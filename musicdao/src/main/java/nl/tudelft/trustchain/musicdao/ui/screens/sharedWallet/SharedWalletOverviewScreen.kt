package nl.tudelft.trustchain.musicdao.ui.screens.sharedWallet

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.tudelft.trustchain.musicdao.ui.components.EmptyState
import nl.tudelft.trustchain.musicdao.ui.components.EmptyStateNotScrollable
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.TransactionInfoItem
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.TransactionItem
import org.bitcoinj.core.Coin

@Composable
fun SharedWalletOverviewScreen(bitcoinWalletViewModel: BitcoinWalletViewModel) {
    val sharedWalletBalance by bitcoinWalletViewModel.sharedWalletBalance.collectAsState()
    val sharedWalletTransactions by bitcoinWalletViewModel.sharedWalletTransactions.collectAsState()
    val isStarted by bitcoinWalletViewModel.isStarted.collectAsState()
    val sharedWalletAddress by bitcoinWalletViewModel.sharedWalletAddress.collectAsState()

    LaunchedEffect(sharedWalletBalance, sharedWalletTransactions) {
        Log.d("SharedWalletScreen", "sharedWalletBalance: ${sharedWalletBalance?.toFriendlyString()}")
        Log.d("SharedWalletScreen", "sharedWalletTransactions count: ${sharedWalletTransactions.size}")
        Log.d("SharedWalletScreen", "isStarted: $isStarted")
    }

    if (!isStarted) {
        EmptyState(
            firstLine = "Wallet not started",
            secondLine = "Please wait for the shared wallet to initialize.",
            loadingIcon = true
        )
        return
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
    ) {
        Text(
            text = "Shared wallet found: ${sharedWalletAddress ?: "Searching..."}",
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Text("Shared Wallet Balance", style = MaterialTheme.typography.h6)
        Text(
            text = sharedWalletBalance?.toFriendlyString() ?: "0.00 BTC",
            modifier = Modifier.padding(bottom = 20.dp),
            style = MaterialTheme.typography.body1
        )

        Divider()

        Text("Shared Wallet Transactions", style = MaterialTheme.typography.h6, modifier = Modifier.padding(vertical = 10.dp))

        if (sharedWalletTransactions.isEmpty()) {
            EmptyStateNotScrollable(
                firstLine = "No Transactions",
                secondLine = "No transactions found for the shared wallet.",
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            sharedWalletTransactions.forEach {
                TransactionInfoItem(transactionInfo = it)
            }
        }
    }
}
