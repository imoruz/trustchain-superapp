package nl.tudelft.trustchain.musicdao.core.util

import org.bitcoinj.wallet.Wallet
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Scans all wallet transactions received by [myWalletAddress] since the later of:
 *  • [daysBack] days ago, or
 *  • the last transaction whose OP_RETURN metadata contains {"payment-mode":"PRO-RATA"}.
 *
 * Returns a map of artist bitcoin addresses to total listen counts.
 */

data class ListenStats(
    var totalCount: Int = 0,
    val userCounts: MutableMap<String, Int> = mutableMapOf()
)

fun getArtistListenStatsForReceived(
    wallet: Wallet,
    myWalletAddress: String,
    daysBack: Int = 30
): Map<String, ListenStats> {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -daysBack)
    val cutoff30 = calendar.time

    // Find the date of the most recent PRO-RATA payment
    val lastProRataDate: Date? = wallet.walletTransactions
        .mapNotNull { tx ->
            tx.transaction.updateTime?.takeIf {
                // check each OP_RETURN for payment-mode
                tx.transaction.outputs.any { output ->
                    val script = output.scriptPubKey
                    if (!script.isOpReturn) return@any false
                    val data = script.chunks.getOrNull(1)?.data ?: return@any false
                    try {
                        JSONObject(String(data, Charsets.UTF_8))
                            .optString("payment-mode")
                            .equals("PRO-RATA", ignoreCase = true) //TODO: make it for USER_CENTRIC TOO
                    } catch (e: Exception) {
                        false
                    }
                }
            }
        }
        .maxOrNull()

    // Final cutoff is whichever is later
    val cutoff = listOfNotNull(cutoff30, lastProRataDate).maxOrNull()!!

    val artistStatsMap = mutableMapOf<String, ListenStats>()

    // Scan only received transactions after that cutoff
    wallet.walletTransactions.forEach { tx ->
        val txDate = tx.transaction.updateTime ?: return@forEach
        if (txDate.before(cutoff)) return@forEach

        val receivedByMe = tx.transaction.outputs.any { output ->
            try {
                val script = output.scriptPubKey
                // not an OP_RETURN, and sending to my address
                script.chunks.isNotEmpty()
                    && script.chunks[0].opcode != org.bitcoinj.script.ScriptOpCodes.OP_RETURN
                    && script.getToAddress(wallet.params).toString() == myWalletAddress
            } catch (e: Exception) {
                false
            }
        }
        if (!receivedByMe) return@forEach

        // accumulate any OP_RETURN listen-count metadata
        tx.transaction.outputs.forEach outputLoop@{ output ->
            val script = output.scriptPubKey
            if (!script.isOpReturn) return@outputLoop
            val opData = script.chunks.getOrNull(1)?.data ?: return@outputLoop
            val jsonString = String(opData, Charsets.UTF_8)


            runCatching {
                JSONObject(jsonString).let { json ->
                    if (json.has("a") && json.has("n")) {
                        val artist = json.getString("a")
                        val count  = json.getInt("n")
                        val user = json.getString("u")
                        val userCount = json.getInt("un")
                        val stats = artistStatsMap.getOrPut(artist) { ListenStats() }
                        stats.totalCount += count
                        stats.userCounts[user] = stats.userCounts.getOrDefault(user, 0) + userCount
                    }
                }
            }.onFailure {
                runCatching {
                    JSONArray(jsonString).let { arr ->
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            if (item.has("a") && item.has("n") && item.has("u") && item.has("ue")) {
                                val artist = item.getString("a")
                                val count  = item.getInt("n")
                                val user = item.getString("u")
                                val userCount = item.getInt("un")
                                val stats = artistStatsMap.getOrPut(artist) { ListenStats() }
                                stats.totalCount += count
                                stats.userCounts[user] = stats.userCounts.getOrDefault(user, 0) + userCount

                            }
                        }
                    }
                }
            }
        }
    }

    return artistStatsMap
}
