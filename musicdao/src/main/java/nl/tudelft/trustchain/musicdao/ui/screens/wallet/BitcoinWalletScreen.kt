package nl.tudelft.trustchain.musicdao.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.tudelft.trustchain.musicdao.core.wallet.UserWalletTransaction
import nl.tudelft.trustchain.musicdao.ui.components.EmptyState
import nl.tudelft.trustchain.musicdao.ui.components.EmptyStateNotScrollable
import nl.tudelft.trustchain.musicdao.ui.screens.profileMenu.CustomMenuItem
import java.text.SimpleDateFormat
import java.util.*
import org.bitcoinj.script.ScriptOpCodes
import android.util.Log


fun extractOpReturnData(userWalletTransaction: UserWalletTransaction): List<ByteArray> {
    val tx = userWalletTransaction.transaction
    val opReturnData = mutableListOf<ByteArray>()

    for (output in tx.outputs) {
        val script = output.scriptPubKey
        val chunks = script.chunks

        // Check if first opcode is OP_RETURN
        if (chunks.isNotEmpty() && chunks[0].opcode == ScriptOpCodes.OP_RETURN) {
            // OP_RETURN data is usually in the next chunk(s)
            // Gather all data chunks after OP_RETURN
            val dataChunks = chunks.drop(1)
                .filter { it.data != null }
                .map { it.data!! }

            dataChunks.forEach {
                opReturnData.add(it)

            }
        }
    }
    return opReturnData
}

@Composable
fun BitcoinWalletScreen(bitcoinWalletViewModel: BitcoinWalletViewModel) {
    val confirmedBalance = bitcoinWalletViewModel.confirmedBalance.collectAsState()
    val estimatedBalance = bitcoinWalletViewModel.estimatedBalance.collectAsState()
    val syncProgress = bitcoinWalletViewModel.syncProgress.collectAsState()
    val status = bitcoinWalletViewModel.status.collectAsState()
    val faucetInProgress = bitcoinWalletViewModel.faucetInProgress.collectAsState()
    val walletTransactions = bitcoinWalletViewModel.walletTransactions.collectAsState()
    val isStarted = bitcoinWalletViewModel.isStarted.collectAsState()

    var state by remember { mutableStateOf(0) }
    val titles = listOf("ACTIONS", "TRANSACTIONS")

    if (!isStarted.value) {
        EmptyState(
            firstLine = "Your wallet is not started yet.",
            secondLine = "Please, wait for the wallet to be started.",
            loadingIcon = true
        )
        return
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colors.primary)
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .align(
                            Alignment.BottomStart
                        )
            ) {
                Text(
                    text = confirmedBalance.value?.toFriendlyString() ?: "0.00 BTC",
                    style = MaterialTheme.typography.h6
                )
                Text(
                    text = "${estimatedBalance.value ?: "0.00 BTC"} (Estimated)",
                    style = MaterialTheme.typography.subtitle1
                )
            }
            Row(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .align(
                            Alignment.TopEnd
                        ),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Sync Progress",
                    modifier =
                        Modifier
                            .padding(end = 15.dp)
                            .align(Alignment.CenterVertically)
                )
                LinearProgressIndicator(
                    syncProgress.value?.let { (it.toFloat() / 100) }
                        ?: 0f,
                    color = MaterialTheme.colors.onPrimary,
                    modifier =
                        Modifier
                            .align(Alignment.CenterVertically)
                            .fillMaxWidth()
                )
            }
        }

        TabRow(selectedTabIndex = state) {
            titles.forEachIndexed { index, title ->
                Tab(
                    onClick = { state = index },
                    selected = (index == state),
                    text = { Text(title) }
                )
            }
        }

        when (state) {
            0 -> {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    CustomMenuItem(
                        text = "Request from faucet",
                        onClick = {
                            bitcoinWalletViewModel.requestFaucet()
                        },
                        disabled = faucetInProgress.value
                    )
                    CustomMenuItem(
                        text = "Send",
                        onClick = { },
                        enabled = false
                    )
                    CustomMenuItem(
                        text = "Receive",
                        onClick = { },
                        enabled = false
                    )

                    Column(modifier = Modifier.padding(bottom = 20.dp)) {
                        Text(text = "Public Key", fontWeight = FontWeight.Bold)
                        Text(text = bitcoinWalletViewModel.publicKey.value ?: "No Public Key")
                    }

                    Column(modifier = Modifier.padding(bottom = 20.dp)) {
                        Text(text = "Wallet Status", fontWeight = FontWeight.Bold)
                        Text(text = status.value ?: "No Status")
                    }
                }
            }
            1 -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (walletTransactions.value.isEmpty()) {
                        EmptyStateNotScrollable(
                            firstLine = "No Transactions",
                            secondLine = "No transactions have been made.",
                            modifier =
                                Modifier
                                    .align(Alignment.Center)
                                    .padding(vertical = 50.dp)
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            walletTransactions.value.map {
                                TransactionItem(
                                    userWalletTransaction = it
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TransactionItem(userWalletTransaction: UserWalletTransaction) {

    val opReturnData = extractOpReturnData(userWalletTransaction)
    Log.d("TransactionItem", "Extracted ${opReturnData.size} OP_RETURN data chunks")
    val opReturnStrings = opReturnData.mapIndexed { index, bytes ->
        try {
            val str = bytes.toString(Charsets.UTF_8)
            Log.d("TransactionItem", "OP_RETURN chunk #$index: $str")
            str
        } catch (e: Exception) {
            Log.e("TransactionItem", "Failed to decode OP_RETURN chunk #$index", e)
            "<unreadable data>"
        }
    }

    ListItem(
        icon = {
            Icon(
                imageVector =
                    if (userWalletTransaction.value.isPositive) {
                        Icons.Outlined.ArrowForward
                    } else {
                        Icons.Outlined.ArrowBack
                    },
                contentDescription = null
            )
        },
        overlineText = {
            Text(
                text = dateToString(userWalletTransaction.date),
                style = MaterialTheme.typography.caption
            )
        },
        text = {
            val text =
                if (userWalletTransaction.value.isPositive) {
                    "Received"
                } else {
                    "Sent"
                }
            Text(text = text)
        },
        secondaryText = {
            Column {
                Text(text = userWalletTransaction.transaction.txId.toString())
                if (opReturnStrings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "OP_RETURN Data:",
                        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold)
                    )
                    opReturnStrings.forEach { dataStr ->
                        Text(
                            text = dataStr,
                            style = MaterialTheme.typography.body2,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        trailing = {
            Text(
                text = userWalletTransaction.value.toFriendlyString(),
                style =
                    TextStyle(
                        color =
                            if (userWalletTransaction.value.isPositive) {
                                Color.Green
                            } else {
                                Color.Red
                            }
                    )
            )
        }
    )
}

fun dateToString(date: Date): String {
    val formatter = SimpleDateFormat("dd MMMM, yyyy, HH:mm", Locale.US)
    return formatter.format(date)
}
