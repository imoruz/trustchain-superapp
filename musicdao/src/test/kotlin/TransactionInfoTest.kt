import nl.tudelft.trustchain.musicdao.core.sharedwallet.TransactionInfo
import org.bitcoinj.core.Sha256Hash
import org.junit.Test
import org.junit.Assert.assertEquals


class TransactionInfoTest {
    @Test
    fun testSerializeDeserializeTransactionInfo() {
        val txid = Sha256Hash.wrap(ByteArray(32) { 0x0A })
        val valueSatoshi = 12345678L
        val timestamp = 1650001234L
        val tx = TransactionInfo(txid, valueSatoshi, timestamp)

        val serialized = tx.serialize()
        val (deserialized, _) = TransactionInfo.deserialize(serialized, 0)

        assertEquals(txid, deserialized.txid)
        assertEquals(valueSatoshi, deserialized.valueSatoshi)
        assertEquals(timestamp, deserialized.timestamp)
    }
}
