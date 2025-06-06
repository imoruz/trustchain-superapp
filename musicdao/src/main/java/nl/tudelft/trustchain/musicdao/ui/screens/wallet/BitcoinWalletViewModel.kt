package nl.tudelft.trustchain.musicdao.ui.screens.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import nl.tudelft.trustchain.musicdao.core.wallet.UserWalletTransaction
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import nl.tudelft.trustchain.musicdao.ui.SnackbarHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bitcoinj.core.Coin
import org.bitcoinj.wallet.Wallet
import javax.inject.Inject
import nl.tudelft.trustchain.musicdao.core.sharedwallet.SharedWalletCommunity
import nl.tudelft.trustchain.musicdao.ui.screens.donate.ArtistListen
import nl.tudelft.trustchain.musicdao.core.util.getArtistListenStats
import nl.tudelft.trustchain.musicdao.core.util.getArtistListenStatsForReceived

@HiltViewModel
class BitcoinWalletViewModel
@Inject
constructor(
    val walletService: WalletService,
    val artistRepository: ArtistRepository,
    val sharedWalletCommunity: SharedWalletCommunity
) : ViewModel() {
    val publicKey: MutableStateFlow<String?> = MutableStateFlow(null)
    val confirmedBalance: MutableStateFlow<Coin?> = MutableStateFlow(null)
    val estimatedBalance: MutableStateFlow<String?> = MutableStateFlow(null)
    val status: MutableStateFlow<String?> = MutableStateFlow(null)
    val syncProgress: MutableStateFlow<Int?> = MutableStateFlow(null)
    val walletTransactions: MutableStateFlow<List<UserWalletTransaction>> =
        MutableStateFlow(listOf())
    val sharedWalletBalance: MutableStateFlow<Coin?> = MutableStateFlow(null)
    val sharedWalletTransactions: MutableStateFlow<List<UserWalletTransaction>> =
        MutableStateFlow(listOf())
    private val gson = Gson()


    val faucetInProgress: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isStarted: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val sharedWalletAddress: StateFlow<String?> = sharedWalletCommunity.discoveredWalletAddress

    init {
        viewModelScope.launch {
            while (isActive) {
                try {
                    // network error happens here (see Community class)
                    sharedWalletCommunity.broadcastToRandomPeer()
                    Log.d(TAG, "Broadcasted shared wallet presence")
                } catch (e: Exception) {
                    Log.e(TAG, "Error broadcasting shared wallet presence: ${e.message}")
                }
                delay(1_000)
            }
        }

        /*viewModelScope.launch {
            while (isActive) {
                val sharedAddress = sharedWalletAddress.value
                if (!sharedAddress.isNullOrEmpty()) {
                    fetchSharedWalletBalance()
                    fetchSharedWalletTransactions()
                    Log.d(TAG, "Setting sharedWalletBalance to: ${sharedWalletBalance.value}")
                    Log.d(TAG, "Setting sharedWalletTransactions to: ${sharedWalletTransactions.value.size} items")
                }
                delay(10*REFRESH_DELAY)
            }
        }*/
        viewModelScope.launch {

            while (isActive) {
                syncProgress.value = walletService.percentageSynced()
                status.value = walletService.walletStatus()

                if (walletService.isStarted()) {
                    isStarted.value = true
                    publicKey.value = walletService.protocolAddress().toString()
                    estimatedBalance.value = walletService.estimatedBalance()
                    confirmedBalance.value = walletService.confirmedBalance()
                    walletTransactions.value = walletService.walletTransactions()
                }
                delay(REFRESH_DELAY)
            }
        }
    }

        val myWalletAddress: String
        get() = walletService.protocolAddress().toString()

        private val _artistListenTable = MutableStateFlow<List<ArtistListen>>(emptyList())
        val artistListenTable: StateFlow<List<ArtistListen>> get() = _artistListenTable

        fun updateArtistListenTable() {
            val myWalletAddress = walletService.protocolAddress().toString()
            val listenMap = getArtistListenStatsForReceived(walletService.wallet(), myWalletAddress)
            val artistListenTable = listenMap.map { (addr, count) -> ArtistListen(addr, count) }
            _artistListenTable.value = artistListenTable
//            val table = getArtistListenStats(walletService.wallet())
//                .map { (addr, count) -> ArtistListen(addr, count) }
//            _artistListenTable.value = table
        }
        fun requestFaucet() {
            viewModelScope.launch {
                faucetInProgress.value = true
                val faucetRequestResult = walletService.defaultFaucetRequest()
                if (faucetRequestResult) {
                    SnackbarHandler.displaySnackbar(text = "Successfully requested from faucet")
                } else {
                    SnackbarHandler.displaySnackbar(text = "Something went wrong requesting from faucet")
                }
                faucetInProgress.value = false
            }
        }

        fun wallet(): Wallet {
            return walletService.wallet()
        }

        suspend fun donate(
            publicKey: String,
            amount: String
        ): Boolean {
            val bitcoinPublicKey = artistRepository.getArtist(publicKey)?.bitcoinAddress ?: return false
            return walletService.sendCoins(bitcoinPublicKey, amount)
        }

        suspend fun donateToAddress(
            address: String,
            amount: String,
            metadata: String? = null
        ): Boolean {
            return walletService.sendCoins(address, amount, metadata)
        }


    companion object {
            const val REFRESH_DELAY = 1000L
            const val TAG = "BitcoinWalletViewModel"
        }
    }
