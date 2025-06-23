package nl.tudelft.trustchain.musicdao.ui.screens.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import nl.tudelft.trustchain.musicdao.core.wallet.UserWalletTransaction
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import nl.tudelft.trustchain.musicdao.ui.SnackbarHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bitcoinj.core.Coin
import org.bitcoinj.wallet.Wallet
import javax.inject.Inject
import nl.tudelft.trustchain.musicdao.core.sharedwallet.SharedWalletCommunity
import nl.tudelft.trustchain.musicdao.core.sharedwallet.TransactionInfo
import nl.tudelft.trustchain.musicdao.ui.screens.donate.ArtistListen
import nl.tudelft.trustchain.musicdao.core.util.getArtistListenStatsForReceived
import nl.tudelft.trustchain.musicdao.core.wallet.toTransactionInfo
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Transaction
import java.util.Date


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
    private val _sharedWalletBalance = MutableStateFlow<Coin?>(null)
    val sharedWalletBalance: StateFlow<Coin?> get() = _sharedWalletBalance

    private val _sharedWalletTransactions = MutableStateFlow<List<TransactionInfo>>(emptyList())
    val sharedWalletTransactions: StateFlow<List<TransactionInfo>> get() = _sharedWalletTransactions



    val faucetInProgress: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isStarted: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val sharedWalletAddress: StateFlow<String?> = sharedWalletCommunity.discoveredWalletAddress

    init {
        viewModelScope.launch {
            while (isActive) {
                try {
                    sharedWalletCommunity.broadcastToRandomPeer()
                    Log.d(TAG, "Broadcasted shared wallet presence")
                } catch (e: Exception) {
                    Log.e(TAG, "Error broadcasting shared wallet presence: ${e.message}")
                }
                delay(1_000)
            }
        }

        viewModelScope.launch {
            sharedWalletCommunity.sharedWalletInfoState.collect { info ->
                if (info != null && !sharedWalletCommunity.isSharedWallet) {
                    // Only update if this device is NOT the shared wallet
                    _sharedWalletBalance.value = Coin.valueOf(info.balanceSatoshi)
                    _sharedWalletTransactions.value = info.transactions
                    Log.d(TAG, "Updated shared wallet balance and transactions from received info ${_sharedWalletBalance}, ${_sharedWalletTransactions}")
                }
            }
        }

        viewModelScope.launch {
            sharedWalletBalance
                .onEach { newBalance ->
                    if (newBalance != null && sharedWalletCommunity.isSharedWallet) {
                        val walletId = sharedWalletCommunity.discoveredWalletAddress.value ?: return@onEach
                        val balanceSatoshi = newBalance.value
                        val txInfos = sharedWalletTransactions.value

                        sharedWalletCommunity.broadcastSharedWalletInfo(walletId, balanceSatoshi, txInfos)
                        Log.d(TAG, "Broadcasted shared wallet balance and transactions")
                    }
                }
        }


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
