import org.bitcoinj.core.Address
import org.bitcoinj.core.Transaction
import org.bitcoinj.script.Script
import org.bitcoinj.script.ScriptChunk
import org.bitcoinj.script.ScriptOpCodes
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.WalletTransaction
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.util.*
import org.junit.Assert.assertEquals
import nl.tudelft.trustchain.musicdao.core.util.getArtistListenStats
import nl.tudelft.trustchain.musicdao.core.util.getArtistListenStatsForReceived
import org.bitcoinj.params.MainNetParams


class ListenStatsUtilTest {

    private lateinit var wallet: Wallet
    private lateinit var transaction: Transaction
    private lateinit var script: Script
    private lateinit var output: org.bitcoinj.core.TransactionOutput
    private lateinit var walletTransaction: WalletTransaction

    private val artistJson = JSONObject().put("a", "artist123").put("n", 3)

    @Before
    fun setUp() {
        wallet = mock(Wallet::class.java)
        transaction = mock(Transaction::class.java)
        script = mock(Script::class.java)
        output = mock(org.bitcoinj.core.TransactionOutput::class.java)
        walletTransaction = mock(WalletTransaction::class.java)

        val chunks = listOf(
            ScriptChunk(ScriptOpCodes.OP_RETURN, null),
            ScriptChunk(ScriptOpCodes.OP_PUSHDATA1, artistJson.toString().toByteArray(Charsets.UTF_8))
        )

        `when`(wallet.walletTransactions).thenReturn(setOf(walletTransaction))
        `when`(walletTransaction.transaction).thenReturn(transaction)
        `when`(transaction.updateTime).thenReturn(Date(System.currentTimeMillis())) // recent date
        `when`(transaction.outputs).thenReturn(listOf(output))
        `when`(output.scriptPubKey).thenReturn(script)
        `when`(script.chunks).thenReturn(chunks)
        `when`(script.isOpReturn).thenReturn(true)
    }

    @Test
    fun testGetArtistListenStatsSingleObject() {
        val result = getArtistListenStats(wallet)
        assertEquals(1, result.size)
        assertEquals(3, result["artist123"])
    }

    @Test
    fun testGetArtistListenStatsJsonArray() {
        val jsonArrayString = """
            [{"a":"artistA","n":2},{"a":"artistB","n":5}]
        """.trimIndent()

        val chunks = listOf(
            ScriptChunk(ScriptOpCodes.OP_RETURN, null),
            ScriptChunk(ScriptOpCodes.OP_PUSHDATA1, jsonArrayString.toByteArray(Charsets.UTF_8))
        )
        `when`(script.chunks).thenReturn(chunks)

        val result = getArtistListenStats(wallet)
        assertEquals(2, result.size)
        assertEquals(2, result["artistA"])
        assertEquals(5, result["artistB"])
    }

    @Test
    fun testGetArtistListenStatsInvalidJson() {
        val invalidJsonString = "not-a-json"
        val chunks = listOf(
            ScriptChunk(ScriptOpCodes.OP_RETURN, null),
            ScriptChunk(ScriptOpCodes.OP_PUSHDATA1, invalidJsonString.toByteArray(Charsets.UTF_8))
        )
        `when`(script.chunks).thenReturn(chunks)

        val result = getArtistListenStats(wallet)
        assertEquals(0, result.size)
    }

    @Test
    fun testGetArtistListenStatsForReceived() {
        val myAddress = "1BoatSLRHtKNngkdXEeobR76b53LETtpyT"

        val np = MainNetParams.get()
        `when`(wallet.params).thenReturn(np)

        val chunksWithAddress = listOf(
            ScriptChunk(0, null)
        )
        val chunksOpReturn = listOf(
            ScriptChunk(ScriptOpCodes.OP_RETURN, null),
            ScriptChunk(ScriptOpCodes.OP_PUSHDATA1, artistJson.toString().toByteArray(Charsets.UTF_8))
        )

        val receivingOutput = mock(org.bitcoinj.core.TransactionOutput::class.java)
        val opReturnOutput = mock(org.bitcoinj.core.TransactionOutput::class.java)
        val receivingScript = mock(Script::class.java)
        val opReturnScript = mock(Script::class.java)

        // Receiving output mocks
        `when`(receivingOutput.scriptPubKey).thenReturn(receivingScript)
        `when`(receivingScript.chunks).thenReturn(chunksWithAddress)
        `when`(receivingScript.getToAddress(np)).thenReturn(Address.fromString(np, myAddress))

        // OP_RETURN output mocks
        `when`(opReturnOutput.scriptPubKey).thenReturn(opReturnScript)
        `when`(opReturnScript.chunks).thenReturn(chunksOpReturn)

        // Full transaction output list
        `when`(transaction.outputs).thenReturn(listOf(receivingOutput, opReturnOutput))

        val result = getArtistListenStatsForReceived(wallet, myAddress)
        assertEquals(1, result.size)
        assertEquals(3, result["artist123"])
    }
}
