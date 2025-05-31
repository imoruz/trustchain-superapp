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
    val isSharedWallet: Boolean
) : Serializable {

    override fun serialize(): ByteArray {
        return originPublicKey +
            serializeUInt(ttl) +
            serializeVarLen(walletId.toByteArray(StandardCharsets.US_ASCII)) +
            serializeBool(isSharedWallet)
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
            localOffset += SERIALIZED_BOOL_SIZE

            return Pair(
                SharedWalletMessage(originPublicKey, ttl, walletId, isSharedWallet),
                localOffset
            )
        }
    }
}
