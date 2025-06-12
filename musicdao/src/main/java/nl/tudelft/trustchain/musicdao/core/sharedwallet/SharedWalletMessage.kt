package nl.tudelft.trustchain.musicdao.core.sharedwallet.messages

import nl.tudelft.ipv8.messaging.*
import java.nio.charset.StandardCharsets

/**
 * A message to announce or request a shared wallet with a given wallet ID.
 */
class SharedWalletMessage(
    val originPublicKey: ByteArray,
    var ttl: UInt,
    val walletId: String,
    val isSharedWallet: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable {

    override fun serialize(): ByteArray {
        return originPublicKey +
            serializeUInt(ttl) +
            serializeVarLen(walletId.toByteArray(StandardCharsets.US_ASCII)) +
            serializeBool(isSharedWallet) +
            serializeULong(timestamp.toULong())
    }

    fun checkTTL(): Boolean {
        ttl -= 1u
        return ttl >= 1u
    }

    companion object Deserializer : Deserializable<SharedWalletMessage> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<SharedWalletMessage, Int> {
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

            val isSharedWallet = deserializeBool(buffer, offset + localOffset)
            localOffset += 1 // SERIALIZED_BOOL_SIZE

            val timestamp = if (buffer.size >= offset + localOffset + SERIALIZED_ULONG_SIZE) {
                deserializeULong(buffer, offset + localOffset).toLong()
            } else {
                0L // default timestamp for older messages
            }
            localOffset += if (buffer.size >= offset + localOffset + SERIALIZED_ULONG_SIZE) SERIALIZED_ULONG_SIZE else 0

            return Pair(
                SharedWalletMessage(originPublicKey, ttl, walletId, isSharedWallet, timestamp),
                localOffset
            )
        }
    }
}
