package nl.tudelft.trustchain.musicdao.core.sharedwallet

import nl.tudelft.ipv8.messaging.*
import org.bitcoinj.core.Sha256Hash
import java.nio.charset.StandardCharsets

/**
 * Message to broadcast shared wallet balance and transactions.
 * - originPublicKey: sender's public key bytes
 * - ttl: message time-to-live to prevent flooding
 * - walletId: identifier of the shared wallet
 * - balanceSatoshi: balance in satoshi
 * - transactions: list of transactions (txid, value, timestamp)
 */

class SharedWalletInfoMessage(
    val originPublicKey: ByteArray,
    var ttl: UInt,
    val walletId: String,
    val balanceSatoshi: Long,
    val transactions: List<TransactionInfo>
) : Serializable {

    override fun serialize(): ByteArray {
        val walletIdBytes = walletId.toByteArray(StandardCharsets.US_ASCII)
        val serializedTxs = transactions.flatMap { it.serialize().toList() }.toByteArray()
        val serializedTxsCount = serializeUInt(transactions.size.toUInt())

        return originPublicKey +
            serializeUInt(ttl) +
            serializeVarLen(walletIdBytes) +
            serializeLong(balanceSatoshi) +
            serializedTxsCount +
            serializedTxs
    }

    fun checkTTL(): Boolean {
        ttl -= 1u
        return ttl >= 1u
    }

    companion object Deserializer : Deserializable<SharedWalletInfoMessage> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<SharedWalletInfoMessage, Int> {
            var localOffset = 0

            val originPublicKey = buffer.copyOfRange(
                offset + localOffset,
                offset + localOffset + SERIALIZED_PUBLIC_KEY_SIZE
            )
            localOffset += SERIALIZED_PUBLIC_KEY_SIZE

            val ttl = deserializeUInt(buffer, offset + localOffset)
            localOffset += SERIALIZED_UINT_SIZE

            val (walletIdBytes, walletIdSize) = deserializeVarLen(buffer, offset + localOffset)
            val walletId = walletIdBytes.toString(StandardCharsets.US_ASCII)
            localOffset += walletIdSize

            val balanceSatoshi = deserializeLong(buffer, offset + localOffset)
            localOffset += SERIALIZED_LONG_SIZE

            val transactionsCount = deserializeUInt(buffer, offset + localOffset).toInt()
            localOffset += SERIALIZED_UINT_SIZE

            val transactions = mutableListOf<TransactionInfo>()
            for (i in 0 until transactionsCount) {
                val (tx, txSize) = TransactionInfo.deserialize(buffer, offset + localOffset)
                transactions.add(tx)
                localOffset += txSize
            }

            return Pair(
                SharedWalletInfoMessage(originPublicKey, ttl, walletId, balanceSatoshi, transactions),
                localOffset
            )
        }
    }
}

/**
 * Data class for one transaction info in the message.
 * Contains:
 * - txid (fixed 32 bytes)
 * - valueSatoshi (8 bytes)
 * - timestamp (8 bytes)
 */
class TransactionInfo(
    val txid: Sha256Hash,
    val valueSatoshi: Long,
    val timestamp: Long
) : Serializable {

    override fun serialize(): ByteArray {
        return txid.bytes +
            serializeLong(valueSatoshi) +
            serializeLong(timestamp)
    }

    companion object Deserializer : Deserializable<TransactionInfo> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<TransactionInfo, Int> {
            var localOffset = 0

            val txidBytes = buffer.copyOfRange(offset + localOffset, offset + localOffset + 32)
            val txid = Sha256Hash.wrap(txidBytes)
            localOffset += 32

            val valueSatoshi = deserializeLong(buffer, offset + localOffset)
            localOffset += SERIALIZED_LONG_SIZE

            val timestamp = deserializeLong(buffer, offset + localOffset)
            localOffset += SERIALIZED_LONG_SIZE

            return Pair(TransactionInfo(txid, valueSatoshi, timestamp), localOffset)
        }
    }
}
