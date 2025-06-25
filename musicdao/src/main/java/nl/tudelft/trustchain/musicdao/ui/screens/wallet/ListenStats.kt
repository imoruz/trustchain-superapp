package nl.tudelft.trustchain.musicdao.ui.screens.wallet

data class ListenStats(
    var totalCount: Int = 0,
    val userCounts: MutableMap<String, Int> = mutableMapOf(),
    val paymentAmounts: MutableMap<String, Long> = mutableMapOf()
)
