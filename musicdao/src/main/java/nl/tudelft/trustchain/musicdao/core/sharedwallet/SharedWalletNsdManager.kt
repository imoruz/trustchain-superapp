package nl.tudelft.trustchain.musicdao.core.sharedwallet

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedWalletNsdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Android's NSD manager used for registering and discovering local network services
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    //handles lifecycle events when your service is advertised.
    private var registrationListener: NsdManager.RegistrationListener? = null

    //tracks whether your device is currently advertising.
    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    //tracks whether your device is currently discovering.
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    // Holds the address of the discovered shared wallet
    private val _discoveredSharedWalletAddress = MutableStateFlow<String?>(null)
    val discoveredSharedWalletAddress: StateFlow<String?> =
        _discoveredSharedWalletAddress.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    // Using a map to hold resolve listeners, as each resolve call needs a new listener instance
    // and we might try to resolve multiple services concurrently if they are found quickly.
    private val activeResolveListeners = mutableMapOf<String, NsdManager.ResolveListener>()

    // Tracks the service name of the currently resolved shared wallet.
    // Useful if the service is lost, so we can clear the address.
    private var resolvedServiceName: String? = null

    // This holds constants used for service naming and logging.
    companion object {
        const val SERVICE_TYPE = "_musicdao._tcp."
        const val SERVICE_NAME_PREFIX = "MusicDAOSharedWallet"
        const val WALLET_ADDRESS_KEY = "wallet_address"
        private const val TAG = "SharedWalletNsdManager"
    }

    //Starts advertising the shared wallet on the local network with a wallet address via NSD
    fun startAdvertisingSharedWallet(walletAddress: String, deviceName: String = "UsedSharedWalletPhone") {
        if (_isAdvertising.value) {
            Log.d(TAG, "Already advertising.")
            return
        }
        stopAdvertising() // Ensure any previous registration is cleaned up

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${serviceInfo.serviceName}")
                _isAdvertising.value = true
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Registration failed. Error code: $errorCode")
                _isAdvertising.value = false
                // Handle error (e.g., inform UI)
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered: ${serviceInfo.serviceName}")
                _isAdvertising.value = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregistration failed. Error code: $errorCode")
                // Handle error
            }
        }

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME_PREFIX-$deviceName" // Make it somewhat unique
            serviceType = SERVICE_TYPE
            // You might need to set a port if you plan direct P2P communication later
            // For now, if only using TXT records, a dummy port can be used, but it must be set.
            port = 8080 // Example port, ensure it's available or choose dynamically
            setAttribute(WALLET_ADDRESS_KEY, walletAddress)
        }

        try {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error registering service: ${e.message}", e)
            _isAdvertising.value = false
        }
    }

    // Stops advertising the shared wallet service and cleans up the NSD registration
    fun stopAdvertising() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering service: ${e.message}", e)
            }
            registrationListener = null // Clear the listener
        }
        _isAdvertising.value = false
    }


    fun startDiscovery() {
        if (_isDiscovering.value) {
            Log.d(TAG, "Discovery already active.")
            return
        }
        stopDiscovery() // Clean up previous discovery if any

        // Reset address before starting new discovery.
        // Consider if this is always desired, or only if no service was previously found.
        _discoveredSharedWalletAddress.value = null
        resolvedServiceName = null

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started for: $regType")
                _isDiscovering.value = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.i(TAG, "Service found: $service")
                // Filter for your specific service type and prefix
                if (service.serviceType == SERVICE_TYPE &&
                    service.serviceName.startsWith(SERVICE_NAME_PREFIX)
                ) {
                    // Check if we are already trying to resolve this or have resolved it
                    // and if we haven't found a wallet yet (to avoid multiple resolutions if not needed)
                    if (_discoveredSharedWalletAddress.value == null && !activeResolveListeners.containsKey(
                            service.serviceName
                        )
                    ) {
                        Log.d(TAG, "Attempting to resolve: ${service.serviceName}")
                        resolveService(service)
                    } else {
                        Log.d(
                            TAG,
                            "Skipping resolution for ${service.serviceName}, address already found or resolution in progress."
                        )
                    }
                } else {
                    Log.d(
                        TAG,
                        "Ignoring service not matching type/prefix: ${service.serviceName}, type: ${service.serviceType}"
                    )
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.w(TAG, "Service lost: ${service.serviceName}")
                if (service.serviceName == resolvedServiceName) {
                    Log.i(TAG, "Lost the currently active shared wallet: ${service.serviceName}")
                    _discoveredSharedWalletAddress.value = null
                    resolvedServiceName = null
                    // Optionally, you might want to automatically restart discovery
                    // or clear the UI indication of a found wallet.
                }
                // Clean up any resolve listener associated with the lost service
                activeResolveListeners.remove(service.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Service discovery stopped for: $serviceType")
                _isDiscovering.value = false
                // Clean up all active resolve listeners when discovery stops fully.
                // This is important because ongoing resolve attempts might not complete
                // if discovery is stopped.
                activeResolveListeners.keys.forEach { serviceName ->
                    // NsdManager doesn't have a direct "stopResolve" method.
                    // The listener just won't be called if discovery stops.
                    Log.d(TAG, "Clearing resolve listener for $serviceName due to discovery stop.")
                }
                activeResolveListeners.clear()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed for $serviceType. Error code: $errorCode")
                _isDiscovering.value = false
                // Attempt to stop discovery, though it might have already effectively stopped.
                try {
                    nsdManager.stopServiceDiscovery(this)
                } catch (e: Exception) {
                    Log.e(TAG, "Error trying to stop discovery after start failure: ${e.message}")
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed for $serviceType. Error code: $errorCode")
                // The state might be inconsistent here. Manual intervention or logging is key.
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error starting discovery: ${e.message}", e)
            _isDiscovering.value =
                false // Ensure state is false if discoverServices throws immediately
            discoveryListener = null // Clean up listener if start failed immediately
        }
    }

    private fun resolveService(serviceToResolve: NsdServiceInfo) {
        // A new ResolveListener instance is needed for each resolve request.
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}. Error code: $errorCode")
                activeResolveListeners.remove(serviceInfo.serviceName) // Clean up listener for this service
            }

            override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                Log.i(TAG, "Service resolved: $resolvedServiceInfo")
                activeResolveListeners.remove(resolvedServiceInfo.serviceName) // Clean up listener post-resolution

                // Only update if we haven't found a wallet address yet from another source/service
                if (_discoveredSharedWalletAddress.value == null) {
                    val attributes = resolvedServiceInfo.attributes
                    val walletAddressBytes = attributes[WALLET_ADDRESS_KEY]

                    if (walletAddressBytes != null) {
                        val walletAddress = String(walletAddressBytes, StandardCharsets.UTF_8)
                        Log.i(TAG, "Discovered Shared Wallet Address: $walletAddress for service: ${resolvedServiceInfo.serviceName}")
                        _discoveredSharedWalletAddress.value = walletAddress
                        resolvedServiceName = resolvedServiceInfo.serviceName // Track which service provided the address
                        // Optional: Once an address is found, you might want to stop further discovery/resolution
                        // to save resources, if only one shared wallet is expected.
                        // stopDiscovery() // Uncomment if you want to stop after first successful resolution
                    } else {
                        Log.w(TAG, "Wallet address not found in TXT record for ${resolvedServiceInfo.serviceName}")
                    }
                } else {
                    Log.d(TAG, "A shared wallet address (${_discoveredSharedWalletAddress.value}) is already set. Ignoring resolved service: ${resolvedServiceInfo.serviceName}")
                }
            }
        }

        // Add to active listeners before calling resolve
        activeResolveListeners[serviceToResolve.serviceName] = resolveListener
        try {
            nsdManager.resolveService(serviceToResolve, resolveListener)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error resolving service ${serviceToResolve.serviceName}: ${e.message}", e)
            activeResolveListeners.remove(serviceToResolve.serviceName) // Clean up if resolveService call fails immediately
        }
    }

    fun stopDiscovery() {
        if (discoveryListener != null) {
            try {
                // This call will eventually trigger discoveryListener.onDiscoveryStopped()
                // if successful, or onStopDiscoveryFailed() if it fails.
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) { // Catch potential runtime exceptions
                Log.e(TAG, "Error trying to stop service discovery: ${e.message}", e)
                // Even if stopServiceDiscovery throws, try to clean up state
                _isDiscovering.value = false // Manually set if the call itself fails
                // It's possible the listener callback won't fire if this throws,
                // so manually clearing active resolve listeners here might be considered,
                // though usually onDiscoveryStopped handles it.
                // activeResolveListeners.clear() // Consider if needed on direct exception
            }
            // Do not nullify discoveryListener immediately here.
            // The NsdManager needs it to report onDiscoveryStopped or onStopDiscoveryFailed.
            // It will be effectively inactive once onDiscoveryStopped is called.
            // We set _isDiscovering to false in the onDiscoveryStopped callback.
        } else {
            // If no listener is active, ensure the state reflects that.
            _isDiscovering.value = false
            activeResolveListeners.clear() // No discovery means no active resolves
        }
        // Note: _isDiscovering is primarily set to false within the
        // onDiscoveryStopped callback of the discoveryListener.
        // This function initiates the stop.
    }

}

