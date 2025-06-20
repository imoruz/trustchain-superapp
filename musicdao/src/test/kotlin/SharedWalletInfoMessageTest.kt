import nl.tudelft.ipv8.messaging.*
import nl.tudelft.trustchain.musicdao.core.sharedwallet.SharedWalletInfoMessage
import nl.tudelft.trustchain.musicdao.core.sharedwallet.TransactionInfo
import org.bitcoinj.core.Sha256Hash
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals


class SharedWalletInfoMessageTest {

    private lateinit var originPublicKey: ByteArray
    private lateinit var walletId: String
    private lateinit var transactions: List<TransactionInfo>
    private var balanceSatoshi: Long = 10000L
    private var ttl: UInt = 5u

    @Before
    fun setUp() {
        originPublicKey = ByteArray(SERIALIZED_PUBLIC_KEY_SIZE) { it.toByte() }
        walletId = "wallet123"
        transactions = listOf(
            TransactionInfo(
                Sha256Hash.wrap(ByteArray(32) { 0x01 }),
                5000L,
                1650000000L
            ),
            TransactionInfo(
                Sha256Hash.wrap(ByteArray(32) { 0x02 }),
                5000L,
                1650000100L
            )
        )
    }

    @Test
    fun testSerializeDeserializeSharedWalletInfoMessage() {
        val message = SharedWalletInfoMessage(originPublicKey, ttl, walletId, balanceSatoshi, transactions)
        val serialized = message.serialize()
        val (deserialized, _) = SharedWalletInfoMessage.deserialize(serialized, 0)

        assertEquals(originPublicKey.toList(), deserialized.originPublicKey.toList())
        assertEquals(ttl, deserialized.ttl)
        assertEquals(walletId, deserialized.walletId)
        assertEquals(balanceSatoshi, deserialized.balanceSatoshi)
        assertEquals(transactions.size, deserialized.transactions.size)

        for (i in transactions.indices) {
            assertEquals(transactions[i].txid, deserialized.transactions[i].txid)
            assertEquals(transactions[i].valueSatoshi, deserialized.transactions[i].valueSatoshi)
            assertEquals(transactions[i].timestamp, deserialized.transactions[i].timestamp)
        }
    }

    @Test
    fun testCheckTTL() {
        val message = SharedWalletInfoMessage(originPublicKey, 3u, walletId, balanceSatoshi, transactions)
        val result = message.checkTTL()
        assertEquals(true, result)
        assertEquals(2u, message.ttl)
    }

    @Test
    fun testCheckTTLExpires() {
        val message = SharedWalletInfoMessage(originPublicKey, 1u, walletId, balanceSatoshi, transactions)
        val result = message.checkTTL()
        assertEquals(false, result)
        assertEquals(0u, message.ttl)
    }
}
