//package nl.tudelft.trustchain.musicdao.core.sharedwallet
//
//import nl.tudelft.ipv8.Peer
//import nl.tudelft.ipv8.messaging.EndpointAggregator
//import nl.tudelft.ipv8.peerdiscovery.Network
//import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
//import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
//import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
//import nl.tudelft.ipv8.keyvault.PublicKey
//import nl.tudelft.ipv8.messaging.Serializable
//import org.junit.Assert.assertEquals
//import org.junit.Before
//import org.junit.Test
//import org.mockito.Mock
//import org.mockito.kotlin.*
//import org.mockito.MockitoAnnotations
//
//class SharedWalletCommunityTest {
//
//    @Mock
//    private lateinit var settings: TrustChainSettings
//
//    @Mock
//    private lateinit var database: TrustChainStore
//
//    @Mock
//    private lateinit var crawler: TrustChainCrawler
//
//    @Mock
//    private lateinit var peer: Peer
//
//    @Mock
//    private lateinit var endpoint: EndpointAggregator
//
//    @Mock
//    private lateinit var network: Network
//
//    private lateinit var community: SharedWalletCommunity
//
//    @Before
//    fun setup() {
//        MockitoAnnotations.openMocks(this)
//
//        // Construct the community under test
//        community = SharedWalletCommunity(
//            myWalletId = "wallet123",
//            settings = settings,
//            database = database,
//            crawler = crawler
//        )
//
//        // Inject the required dependencies manually (assuming they are public or have setters)
//        community.myPeer = peer
//        community.endpoint = endpoint
//        community.network = network
//    }
//
//    @Test
//    fun testBroadcastSharedWalletMessage() {
//        fun createMockPeer(): Peer {
//            val publicKey = mock(PublicKey::class.java)
//            `when`(publicKey.keyToBin()).thenReturn(byteArrayOf(0x01, 0x02))
//
//            val peer = mock(Peer::class.java)
//            `when`(peer.publicKey).thenReturn(publicKey)
//            return peer
//        }
//
//        val peer1 = createMockPeer()
//        val peer2 = createMockPeer()
//        val peer3 = createMockPeer()
//        val peers = listOf(peer1, peer2, peer3)
//
//        val spyCommunity = spy(community)
//        doReturn(peers).`when`(spyCommunity).getPeers()
//        doReturn(peer1).`when`(spyCommunity).safeMyPeer
//
//        val fakeSerializable = mock(Serializable::class.java) // or create a real instance if possible
//        val fakeByteArray = byteArrayOf(0x00) // just some dummy bytes
//
//        doReturn(byteArrayOf(0x03, 0x04)).`when`(spyCommunity).serializePacket(
//            1,
//            fakeSerializable,
//            true,
//            any<Peer>(),
//            fakeByteArray,
//            false,
//            null,
//            null
//        )
//        val count = spyCommunity.broadcastSharedWalletMessage()
//
//        assertEquals(peers.size, count)
//    }
//}
