package nl.tudelft.trustchain.musicdao.core.util

import org.bitcoinj.wallet.Wallet
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Scans all wallet transactions in the last [daysBack] days for OP_RETURN metadata of the form
 * {"a":"bitcoinAddress","n":listenCount} or a JSON array of such objects.
 * Returns a map of artist bitcoin addresses to total listen counts.
 */
fun getArtistListenStats(wallet: Wallet, daysBack: Int = 30): Map<String, Int> {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -daysBack)
    val cutoff = calendar.time

    val artistListenCounts = mutableMapOf<String, Int>()

    wallet.walletTransactions.forEach { tx ->
        val txDate = tx.transaction.updateTime
        if (txDate != null && txDate.after(cutoff)) {
            tx.transaction.outputs.forEach { output ->
                val script = output.scriptPubKey
                if (script.isOpReturn) {
                    val opReturnBytes = script.chunks[1].data
                    if (opReturnBytes != null) {
                        val opReturnString = String(opReturnBytes, Charsets.UTF_8)
                        try {
                            // Try single JSON object
                            val json = JSONObject(opReturnString)
                            if (json.has("a") && json.has("n")) {
                                val artist = json.getString("a")
                                val count = json.getInt("n")
                                artistListenCounts[artist] = artistListenCounts.getOrDefault(artist, 0) + count
                            }
                        } catch (e: Exception) {
                            try {
                                // Try JSON array
                                val jsonArray = JSONArray(opReturnString)
                                for (i in 0 until jsonArray.length()) {
                                    val json = jsonArray.getJSONObject(i)
                                    if (json.has("a") && json.has("n")) {
                                        val artist = json.getString("a")
                                        val count = json.getInt("n")
                                        artistListenCounts[artist] = artistListenCounts.getOrDefault(artist, 0) + count
                                    }
                                }
                            } catch (ex: Exception) {
                                // Not a matching format, ignore
                            }
                        }
                    }
                }
            }
        }
    }
    return artistListenCounts
}

fun getArtistListenStatsForReceived(
    wallet: Wallet,
    myWalletAddress: String,
    daysBack: Int = 30
): Map<String, Int> {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -daysBack)
    val cutoff = calendar.time

    val artistListenCounts = mutableMapOf<String, Int>()

    wallet.walletTransactions.forEach { tx ->
        val txDate = tx.transaction.updateTime
        if (txDate != null && txDate.after(cutoff)) {
            // Only process transactions received by this wallet
            val receivedByMe = tx.transaction.outputs.any { output ->
                // Not OP_RETURN, and address matches this wallet
                try {
                    val script = output.scriptPubKey
                    script.chunks.isNotEmpty() &&
                        script.chunks[0].opcode != org.bitcoinj.script.ScriptOpCodes.OP_RETURN &&
                        script.getToAddress(wallet.params).toString() == myWalletAddress
                } catch (e: Exception) { false }
            }
            if (!receivedByMe) return@forEach // skip if not received by this wallet

            // Now sum up OP_RETURN listens for this transaction
            tx.transaction.outputs.forEach { output ->
                val script = output.scriptPubKey
                if (script.chunks.isNotEmpty() && script.chunks[0].opcode == org.bitcoinj.script.ScriptOpCodes.OP_RETURN) {
                    val opReturnBytes = script.chunks.getOrNull(1)?.data
                    if (opReturnBytes != null) {
                        val opReturnString = String(opReturnBytes, Charsets.UTF_8)
                        try {
                            val json = JSONObject(opReturnString)
                            if (json.has("a") && json.has("n")) {
                                val artist = json.getString("a")
                                val count = json.getInt("n")
                                artistListenCounts[artist] = artistListenCounts.getOrDefault(artist, 0) + count
                            }
                        } catch (e: Exception) {
                            try {
                                val jsonArray = JSONArray(opReturnString)
                                for (i in 0 until jsonArray.length()) {
                                    val json = jsonArray.getJSONObject(i)
                                    if (json.has("a") && json.has("n")) {
                                        val artist = json.getString("a")
                                        val count = json.getInt("n")
                                        artistListenCounts[artist] = artistListenCounts.getOrDefault(artist, 0) + count
                                    }
                                }
                            } catch (ex: Exception) {
                                // Ignore
                            }
                        }
                    }
                }
            }
        }
    }
    return artistListenCounts
}
