import nl.tudelft.ipv8.messaging.*
import nl.tudelft.trustchain.musicdao.core.sharedwallet.messages.SharedWalletMessage
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals


class SharedWalletMessageTest {
    private lateinit var originPublicKey: ByteArray
    private lateinit var walletId: String
    private var ttl: UInt = 10u
    private var isSharedWallet: Boolean = true
    private var timestamp: Long = 1_650_000_000_000L

    @Before
    fun setUp() {
        originPublicKey = ByteArray(SERIALIZED_PUBLIC_KEY_SIZE) { it.toByte() }
        walletId = "shared-wallet-001"
    }

    @Test
    fun testSerializeDeserializeSharedWalletMessage() {
        val message = SharedWalletMessage(originPublicKey, ttl, walletId, isSharedWallet, timestamp)
        val serialized = message.serialize()
        val (deserialized, _) = SharedWalletMessage.deserialize(serialized, 0)

        assertEquals(originPublicKey.toList(), deserialized.originPublicKey.toList())
        assertEquals(ttl, deserialized.ttl)
        assertEquals(walletId, deserialized.walletId)
        assertEquals(isSharedWallet, deserialized.isSharedWallet)
        assertEquals(timestamp, deserialized.timestamp)
    }

    @Test
    fun testCheckTTL() {
        val message = SharedWalletMessage(originPublicKey, 2u, walletId, isSharedWallet)
        val result = message.checkTTL()
        assertEquals(true, result)
        assertEquals(1u, message.ttl)
    }

    @Test
    fun testCheckTTLExpires() {
        val message = SharedWalletMessage(originPublicKey, 1u, walletId, isSharedWallet)
        val result = message.checkTTL()
        assertEquals(false, result)
        assertEquals(0u, message.ttl)
    }

    @Test
    fun testDeserializeWithoutTimestamp() {
        val message = SharedWalletMessage(originPublicKey, ttl, walletId, isSharedWallet, timestamp)
        val fullSerialized = message.serialize()

        // Remove timestamp bytes to simulate an old-format message
        val truncatedSerialized = fullSerialized.copyOf(fullSerialized.size - SERIALIZED_ULONG_SIZE)
        val (deserialized, _) = SharedWalletMessage.deserialize(truncatedSerialized, 0)

        assertEquals(originPublicKey.toList(), deserialized.originPublicKey.toList())
        assertEquals(ttl, deserialized.ttl)
        assertEquals(walletId, deserialized.walletId)
        assertEquals(isSharedWallet, deserialized.isSharedWallet)
        assertEquals(0L, deserialized.timestamp)
    }
}
