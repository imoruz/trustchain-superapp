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
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import nl.tudelft.trustchain.musicdao.core.sharedwallet.SharedWalletCommunity
import nl.tudelft.trustchain.musicdao.ui.screens.donate.ArtistListen
//import nl.tudelft.trustchain.musicdao.core.util.getArtistListenStats
import nl.tudelft.trustchain.musicdao.core.util.getArtistListenStatsForReceived

const val MIN_FEE_PER_KB = 25_000L // based off the function calculateEstimatedTransactionFee in package nl.tudelft.trustchain.currencyii.coin
const val ESTIMATED_KB_PER_TX = 20L // rough estimate that worked for now, but unsure if correct



// Simple enum in the same file:
private enum class FeePriority { LOW, MEDIUM, HIGH }

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


    fun distributeProportionally() {
        viewModelScope.launch {
            // current balance
            val coin: Coin? = confirmedBalance.value
            if (coin == null || coin.isZero) {
                SnackbarHandler.displaySnackbar("No funds to distribute")
                return@launch
            }
            val totalSat = coin.value

            // artist-listens table
            val table = _artistListenTable.value
            if (table.isEmpty()) {
                SnackbarHandler.displaySnackbar("No artists to distribute to")
                return@launch
            }

            // Estimate per-tx fee
            val feePerKB: Long = MIN_FEE_PER_KB
            val txSizeKB = ESTIMATED_KB_PER_TX
            val calculatedFeePerTx = (feePerKB * txSizeKB)


            // Compute total fee reserve
            val totalFeeSat = calculatedFeePerTx * table.size
            if (totalSat <= totalFeeSat) {
                SnackbarHandler.displaySnackbar("Not enough funds to cover fees $totalFeeSat sats")
                return@launch
            }

            // Distributable sats
            val distributableSat = totalSat - totalFeeSat

            // Split distributable sats by listens
            val totalListens = table.sumOf { it.listens }
            var allocated = 0L
            val payments = table.mapIndexed { idx, artistListen ->
                val rawShare = (distributableSat * artistListen.listens) / totalListens
                allocated += rawShare

                // Give any leftover sats to the last artist
                val finalShare = if (idx == table.lastIndex) {
                    rawShare + (distributableSat - allocated)
                } else rawShare

                artistListen.address to finalShare
            }

            // Send each payment (each will incur ~feePerTxSat sats in addition)
            var allSucceeded = true
            payments.forEach { (addr, shareSat) ->
                val shareCoin = Coin.valueOf(shareSat)
                val shareBtc  = shareCoin.toPlainString()
                val ok = donateToAddress(
                    address  = addr,
                    amount   = shareBtc,
                    metadata = """{"payment-mode":"PRO-RATA"}"""
                )
                if (!ok) {
                    Log.e(TAG, "Failed to send $shareBtc BTC to $addr")
                    allSucceeded = false
                }
            }

            // 8️⃣ Final user feedback
            val distributedBtc = BigDecimal(distributableSat)
                .divide(BigDecimal(100_000_000), 8, RoundingMode.HALF_UP)
                .toPlainString()

            if (allSucceeded) {
                SnackbarHandler.displaySnackbar("Distributed $distributedBtc BTC (fees reserved)")
            } else {
                SnackbarHandler.displaySnackbar("Some payments failed—check logs.")
            }
        }
    }



    companion object {
            const val REFRESH_DELAY = 1000L
            const val TAG = "BitcoinWalletViewModel"
        }
    }
