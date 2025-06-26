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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bitcoinj.core.Coin
import org.bitcoinj.wallet.Wallet
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import nl.tudelft.trustchain.musicdao.core.sharedwallet.SharedWalletCommunity
import nl.tudelft.trustchain.musicdao.core.sharedwallet.TransactionInfo
import nl.tudelft.trustchain.musicdao.ui.screens.donate.ArtistListen
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.ListenStats
import nl.tudelft.trustchain.musicdao.core.utilcle.getArtistListenStatsForReceived
import nl.tudelft.trustchain.musicdao.core.wallet.toTransactionInfo
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Transaction
import java.util.Date


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

    private val _sharedWalletBalance = MutableStateFlow<Coin?>(null)
    val sharedWalletBalance: StateFlow<Coin?> get() = _sharedWalletBalance

    private val _sharedWalletTransactions = MutableStateFlow<List<TransactionInfo>>(emptyList())
    val sharedWalletTransactions: StateFlow<List<TransactionInfo>> get() = _sharedWalletTransactions

    private val _lastProRataStats = MutableStateFlow<List<DistributionStat>?>(null)
    val lastProRataStats: StateFlow<List<DistributionStat>?> get() = _lastProRataStats

    private val _lastUserCentricStats = MutableStateFlow<List<DistributionStat>?>(null)
    val lastUserCentricStats: StateFlow<List<DistributionStat>?> get() = _lastUserCentricStats

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

    val myWalletAddress: String get() = walletService.protocolAddress().toString()

    private val _artistListenTable = MutableStateFlow<List<ArtistListen>>(emptyList())
    val artistListenTable: StateFlow<List<ArtistListen>> get() = _artistListenTable

    private val dummyArtistNameMap = mapOf(
        "mzrrEk1zyB1Tj9zYnjcgFYsKtqHxN7KJSP" to "User A",
        "n2hKFZgxYCV9s8XEkGkpEVhDUyxtGxgZXc" to "User B",
        "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy" to "User C"
    )


    fun updateArtistListenTable() {

//            val listenMap = getArtistListenStatsForReceived(walletService.wallet(), myWalletAddress)
//
//
//            val artistListenTable = listenMap.mapNotNull { (addr, stats) ->
//                val (user_addr, user_count) = stats.userCounts.maxByOrNull { it.value } ?: return@mapNotNull null
//                ArtistListen(addr, stats.totalCount, user_addr, user_count)
//            }


            //_artistListenTable.value = artistListenTable
            // Dummy data for testing
            _artistListenTable.value = listOf(
                ArtistListen("mzrrEk1zyB1Tj9zYnjcgFYsKtqHxN7KJSP", 500, "userA", 50),
                ArtistListen("n2hKFZgxYCV9s8XEkGkpEVhDUyxtGxgZXc",  80, "userA", 30),
                ArtistListen("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy", 40, "userC", 20)
            )
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
        updateArtistListenTable()
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
            /*if (totalSat <= totalFeeSat) {
                SnackbarHandler.displaySnackbar("Not enough funds to cover fees $totalFeeSat sats")
                return@launch
            }*/

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
                /*val ok = donateToAddress(
                    address  = addr,
                    amount   = shareBtc,
                    metadata = """{"payment-mode":"PRO-RATA"}"""
                )*/
                val ok = true
                if (!ok) {
                    Log.e(TAG, "Failed to send $shareBtc BTC to $addr")
                    allSucceeded = false
                }
            }

            val distributedBtc = BigDecimal(distributableSat)
                .divide(BigDecimal(100_000_000), 8, RoundingMode.HALF_UP)
                .toPlainString()

            val stats = payments.map { (address, amount) ->
                val percentage = amount.toFloat() / distributableSat.toFloat()
                val artistName = dummyArtistNameMap[address] ?: (address.take(6) + "…")
                DistributionStat(artistName, amount, percentage)
            }
            _lastProRataStats.value = stats
            Log.i("BWVM-PRO" ,"Stats: ${lastProRataStats.value}")

            /*if (allSucceeded) {
                SnackbarHandler.displaySnackbar("Distributed $distributedBtc BTC (fees reserved)")
            } else {
                SnackbarHandler.displaySnackbar("Some payments failed—check logs.")
            }*/
        }
    }


    fun distributeProportionallyUserCentric() {
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

            // Get actual payment data from listen stats
            val myWalletAddress = walletService.protocolAddress().toString()
            //val statsMap = getArtistListenStatsForReceived(walletService.wallet(), myWalletAddress)

            val statsMap: Map<String, ListenStats> = mapOf(
                // Artist 1
                "mzrrEk1zyB1Tj9zYnjcgFYsKtqHxN7KJSP" to ListenStats(
                    totalCount = 120,
                    userCounts = mutableMapOf(
                        "userA" to 50,
                        "userB" to 70
                    ),
                    paymentAmounts = mutableMapOf(
                        "userA" to 300_000L, // 0.003 BTC
                        "userB" to 500_000L  // 0.005 BTC
                    )
                ),

                // Artist 2
                "n2hKFZgxYCV9s8XEkGkpEVhDUyxtGxgZXc" to ListenStats(
                    totalCount = 80,
                    userCounts = mutableMapOf(
                        "userA" to 30,
                        "userD" to 50
                    ),
                    paymentAmounts = mutableMapOf(
                        "userA" to 200_000L,
                        "userD" to 400_000L
                    )
                ),

                // Artist 3
                "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy" to ListenStats(
                    totalCount = 40,
                    userCounts = mutableMapOf(
                        "userC" to 20,
                        "userE" to 20
                    ),
                    paymentAmounts = mutableMapOf(
                        "userC" to 100_000L,
                        "userE" to 100_000L
                    )
                )
            )


            // Estimate per-tx fee
            val feePerKB: Long = MIN_FEE_PER_KB
            val txSizeKB = ESTIMATED_KB_PER_TX
            val calculatedFeePerTx = (feePerKB * txSizeKB)


            // Compute total fee reserve
            val totalFeeSat = calculatedFeePerTx * table.size
           /* if (totalSat <= totalFeeSat) {
                SnackbarHandler.displaySnackbar("Not enough funds to cover fees $totalFeeSat sats")
                return@launch
            }*/

            // Distributable sats
            val distributableSat = totalSat - totalFeeSat
            Log.d(TAG, "totalSat: ${totalSat}")
            Log.d(TAG, "distributableSet: ${distributableSat}")


            // For each user, total listens and total amount sent
            val totalListensPerUser = mutableMapOf<String, Long>()
            val totalSentPerUser = mutableMapOf<String, Long>()

            statsMap.forEach { (_, stats) ->
                stats.userCounts.forEach { (user, listens) ->
                    totalListensPerUser[user] = totalListensPerUser.getOrDefault(user, 0L) + listens
                }
                stats.paymentAmounts.forEach { (user, amount) ->
                    totalSentPerUser[user] = totalSentPerUser.getOrDefault(user, 0L) + amount
                }
            }

            // Compute weighted contribution for each artist
            val artistWeightedShare = mutableMapOf<String, Double>()

            statsMap.forEach { (artist, stats) ->
                stats.userCounts.forEach { (user, userListensToArtist) ->
                    val totalListens = totalListensPerUser[user] ?: return@forEach
                    val totalSent = totalSentPerUser[user] ?: return@forEach
                    if (totalListens == 0L || totalSent == 0L) return@forEach

                    val userContributionToArtist =
                        (userListensToArtist.toDouble() / totalListens.toDouble()) * totalSent.toDouble()

                    artistWeightedShare[artist] = artistWeightedShare.getOrDefault(artist, 0.0) + userContributionToArtist
                }
            }

            artistWeightedShare.forEach { (artist, weightedShare) ->
                Log.d(TAG, "Artist: $artist has weighted share: $weightedShare")
            }



            val totalWeighted = artistWeightedShare.values.sum()
            if (totalWeighted == 0.0) {
                SnackbarHandler.displaySnackbar("Total weighted contribution is zero")
                return@launch
            }

            val artistList = artistWeightedShare.entries.toList()
            var allocated = 0L

            Log.d(TAG, "🔄 Starting proportional payment calculation:")
            Log.d(TAG, "→ Total weighted value: $totalWeighted")
            Log.d(TAG, "→ Total distributable satoshis: $distributableSat")

            val payments = artistList.mapIndexed { idx, (artist, weight) ->
                val rawShare = ((weight / totalWeighted) * distributableSat).toLong()
                allocated += rawShare
                val finalShare = if (idx == artistList.lastIndex) {
                    rawShare + (distributableSat - allocated)
                } else rawShare

                Log.d(TAG, "Artist: $artist")
                Log.d(TAG, "Weight: $weight")
                Log.d(TAG, "Raw Share: $rawShare sat")
                Log.d(TAG, "Final Share (with leftovers): $finalShare sat")

                artist to finalShare
            }



            // Send each payment (each will incur ~feePerTxSat sats in addition)
            var allSucceeded = true
            payments.forEach { (addr, shareSat) ->
                val shareCoin = Coin.valueOf(shareSat)
                val shareBtc  = shareCoin.toPlainString()

                Log.d(TAG, "Preparing to send payment:")
                Log.d(TAG, "→ Recipient Address: $addr")
                Log.d(TAG, "→ Amount in Satoshis: $shareSat")
                Log.d(TAG, "→ Amount in BTC: $shareBtc")

                /*val ok = donateToAddress(
                    address  = addr,
                    amount   = shareBtc,
                    metadata = """{"payment-mode":"USER-CENTRIC"}"""
                )*/
                val ok = true
                if (!ok) {
                    Log.e(TAG, "Failed to send $shareBtc BTC to $addr")
                    allSucceeded = false
                }
            }

            // 8️⃣ Final user feedback
            val distributedBtc = BigDecimal(distributableSat)
                .divide(BigDecimal(100_000_000), 8, RoundingMode.HALF_UP)
                .toPlainString()

            val stats = payments.map { (address, amount) ->
                val percentage = amount.toFloat() / distributableSat.toFloat()
                val artistName = dummyArtistNameMap[address] ?: (address.take(6) + "…")
                DistributionStat(artistName, amount, percentage)
            }
            _lastUserCentricStats.value = stats


            /*if (allSucceeded) {
                SnackbarHandler.displaySnackbar("Distributed $distributedBtc BTC (fees reserved)")
            } else {
                SnackbarHandler.displaySnackbar("Some payments failed—check logs.")
            }*/
        }
    }




    companion object {
        const val REFRESH_DELAY = 1000L
        const val TAG = "BitcoinWalletViewModel"
    }
}

data class DistributionStat(
    val artistName: String,
    val amountSat: Long,
    val percentage: Float
)
