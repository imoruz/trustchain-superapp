package nl.tudelft.trustchain.musicdao.core.sharedwallet

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.musicdao.core.sharedwallet.messages.SharedWalletMessage

import java.util.*

class SharedWalletCommunity(
    private val myWalletId: String,
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler(),
) : TrustChainCommunity(settings, database, crawler) {

    private val seenMessages = LinkedHashSet<String>()
    private val MAX_SEEN_MESSAGES = 500
    private val _currentPropagatedWalletId = MutableStateFlow<String?>(null)


    override val serviceId = "aa6f5273ef7b8c9d0e1f2a3b4c5d6f7e8d9c0efa"
    private var lastReceivedWalletTimestamp: Long = 0

    private val _sharedWalletInfoState = MutableStateFlow<SharedWalletInfoMessage?>(null)
    val sharedWalletInfoState: StateFlow<SharedWalletInfoMessage?> get() = _sharedWalletInfoState

    // Flag indicating if this device is a shared wallet
    var isSharedWallet: Boolean = false
    val safeMyPeer: Peer
        get() = requireNotNull(myPeer) { "myPeer is not initialized yet." }

    private val _discoveredWalletAddress = MutableStateFlow<String?>(null)
    val discoveredWalletAddress: StateFlow<String?> get() = _discoveredWalletAddress


    class Factory(
        private val myWalletId: String,
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<SharedWalletCommunity>(SharedWalletCommunity::class.java) {
        override fun create(): SharedWalletCommunity {
            return SharedWalletCommunity(myWalletId, settings, database, crawler)
        }
    }

    init {
        messageHandlers[MessageId.SHARED_WALLET_MESSAGE] = ::onSharedWalletMessage
        messageHandlers[MessageId.SHARED_WALLET_INFO_MESSAGE] = ::onSharedWalletInfoMessage
    }

    fun becomeSharedWallet() {
        isSharedWallet = true
        _currentPropagatedWalletId.value = myWalletId
        lastReceivedWalletTimestamp = System.currentTimeMillis()
        _discoveredWalletAddress.value = myWalletId  // Ensure local discovery
        broadcastSharedWalletMessage()
        Log.i("WalletJoin", "This device became the shared wallet and set itself as discovered.")
    }

    fun broadcastSharedWalletMessage(ttl: UInt = 2u): Int {

        val originKey = safeMyPeer.publicKey.keyToBin()
        val walletToBroadcast = _currentPropagatedWalletId.value ?: myWalletId
        val packet = serializePacket(
            MessageId.SHARED_WALLET_MESSAGE,
            SharedWalletMessage(originKey, ttl, walletToBroadcast, isSharedWallet, System.currentTimeMillis())
        )

        var count = 0
        val peers = getPeers()
        Log.d("WalletSend", "Broadcasting wallet message to peers: ${peers.map { it.key }.joinToString(", ")}")
        for ((index, peer) in peers.withIndex()) {
            if (index >= MAX_BROADCAST_PEERS) break
            send(peer, packet)
            Log.d("WalletSend", "Wallet message sent.")
            count++
        }
        return count
    }

    fun broadcastSharedWalletInfo(
        walletId: String,
        balanceSatoshi: Long,
        transactions: List<TransactionInfo>,
        ttl: UInt = 2u
    ): Int {
        val originKey = safeMyPeer.publicKey.keyToBin()
        val infoMessage = SharedWalletInfoMessage(originKey, ttl, walletId, balanceSatoshi, transactions)
        val packet = serializePacket(MessageId.SHARED_WALLET_INFO_MESSAGE, infoMessage)

        var count = 0
        val peers = getPeers()
        Log.d("WalletInfoSend", "Broadcasting wallet info to peers: ${peers.map { it.key }.joinToString(", ")}")
        for ((index, peer) in peers.withIndex()) {
            if (index >= MAX_BROADCAST_PEERS) break
            send(peer, packet)
            Log.d("WalletInfoSend", "Wallet info message sent to peer ${peer.mid}")
            count++
        }
        return count
    }


    private fun isDuplicateMessage(messageId: String): Boolean {
        synchronized(seenMessages) {
            if (seenMessages.contains(messageId)) {
                return true
            }
            if (seenMessages.size >= MAX_SEEN_MESSAGES) {
                val iterator = seenMessages.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            seenMessages.add(messageId)
            return false
        }
    }



    private fun onSharedWalletMessage(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(SharedWalletMessage)
        val messageId = payload.walletId + ":" + payload.originPublicKey.toHex()

        if (isDuplicateMessage(messageId)) {
            Log.i("WalletDiscovery", "Duplicate message ignored: $messageId")
            return
        }

        if (!payload.isSharedWallet) {
            Log.i("WalletDiscovery", "Ignored wallet message from non-shared-wallet peer: ${peer.mid}")
            return
        }

        val walletId = payload.walletId
        Log.i("WalletDiscovery", "Received wallet ID: $walletId from shared wallet ${peer.mid}")

        if (payload.timestamp > lastReceivedWalletTimestamp) {
            lastReceivedWalletTimestamp = payload.timestamp
            _discoveredWalletAddress.value = walletId
            _currentPropagatedWalletId.value = walletId
            Log.i("WalletDiscovery", "Accepted newer wallet broadcast with timestamp=${payload.timestamp}")

            if (!hasLocalWallet(walletId)) {
                joinWallet(walletId)
            }

            if (payload.checkTTL()) {
                broadcastSharedWalletMessage(payload.ttl)
            }
        } else {
            Log.i("WalletDiscovery", "Ignored older wallet broadcast with timestamp=${payload.timestamp}")
        }
    }

    private fun onSharedWalletInfoMessage(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(SharedWalletInfoMessage)

        val messageId = payload.walletId + ":" + payload.originPublicKey.toHex()
        if (isDuplicateMessage(messageId)) {
            Log.i("WalletInfo", "Duplicate info message ignored: $messageId")
            return
        }

        // Notify listeners or update state flow here for new balance and transactions
        _sharedWalletInfoState.value = payload

        Log.i("WalletInfo", "Received wallet info for walletId=${payload.walletId} from peer=${peer.mid}")
    }


    fun broadcastToRandomPeer() {
        Log.d("WalletDiscovery", "Trying to bc to random peer")
        val peer = pickRandomPeer() ?: return
        Log.d("WalletDiscovery", "Random peer is: $peer")
        Log.d("WalletDiscovery", "I am peer: $myPeer")
        val walletToBroadcast = _currentPropagatedWalletId.value ?: myWalletId
        val packet = serializePacket(
            MessageId.SHARED_WALLET_MESSAGE,
            SharedWalletMessage(safeMyPeer.publicKey.keyToBin(), 1u, walletToBroadcast, isSharedWallet)
        )
        send(peer, packet)
        Log.d("WalletDiscovery", "Sent wallet announcement to random peer ${peer.mid}")
    }

    private fun pickRandomPeer(): Peer? {
        val peers = getPeers()
        if (peers.isEmpty()) return null
        return peers.random()
    }

    private fun hasLocalWallet(walletId: String): Boolean {
        return discoveredWalletAddress.value == walletId
    }

    private fun joinWallet(walletId: String) {
        Log.i("WalletJoin", "Joining shared wallet: $walletId")
    }

    fun publicKeyHex(): String {
        return safeMyPeer.publicKey.keyToBin().toHex()
    }

    fun publicKeyFromHex(hex: String): PublicKey {
        return defaultCryptoProvider.keyFromPublicBin(hex.hexToBytes())
    }

    fun publicKeyToBytes(pubKey: PublicKey): ByteArray {
        return pubKey.keyToBin()
    }

    companion object {
        private const val MAX_BROADCAST_PEERS = 20
    }

    object MessageId {
        const val SHARED_WALLET_MESSAGE = 20
        const val SHARED_WALLET_INFO_MESSAGE = 21

    }
}

