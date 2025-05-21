package nl.tudelft.trustchain.musicdao.ui.screens.donate

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.musicdao.ui.SnackbarHandler
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import androidx.compose.ui.Modifier
import androidx.compose.material.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.material.OutlinedTextField
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import nl.tudelft.trustchain.musicdao.ui.screens.profileMenu.CustomMenuItem
import androidx.compose.runtime.getValue


@Composable
fun SharedDonateScreen(
    bitcoinWalletViewModel: BitcoinWalletViewModel,
    navController: NavController
) {
    val amount = rememberSaveable { mutableStateOf("0.1") }
    val coroutine = rememberCoroutineScope()

    // Start advertising as shared wallet
    val publicKey by bitcoinWalletViewModel.publicKey.collectAsState()
    LaunchedEffect(publicKey) {
        if (!publicKey.isNullOrBlank()) {
            bitcoinWalletViewModel.sharedWalletNsdManager.startAdvertisingSharedWallet(
                walletAddress = publicKey!!,
                deviceName = "MyPhoneSharedWallet"
            )
            Log.d("SharedWallet", "Started advertising shared wallet: $publicKey")
        }
    }

    fun send() {
        val confirmedBalance = bitcoinWalletViewModel.confirmedBalance.value
        if (confirmedBalance == null || confirmedBalance.isZero || confirmedBalance.isNegative) {
            SnackbarHandler.displaySnackbar("You don't have enough funds to donate")
            return
        }

        coroutine.launch {
            val sharedWalletAddress = bitcoinWalletViewModel.sharedWalletAddress.value
            if (sharedWalletAddress == null) {
                SnackbarHandler.displaySnackbar("No shared wallet found on local network")
                return@launch
            }

            val result = bitcoinWalletViewModel.donateToAddress(sharedWalletAddress, amount.value)
            if (result) {
                SnackbarHandler.displaySnackbar("Donation sent to shared wallet")
                navController.popBackStack()
            } else {
                SnackbarHandler.displaySnackbar("Donation failed")
            }
        }
    }

    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            text = "Your balance is ${bitcoinWalletViewModel.confirmedBalance.value?.toFriendlyString() ?: "0.00 BTC"}",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Text(
            text = "Amount",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 5.dp)
        )
        OutlinedTextField(
            value = amount.value,
            onValueChange = { amount.value = it },
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Row {
            listOf("0.001", "0.01", "0.1").forEach { value ->
                Button(
                    onClick = { amount.value = value },
                    modifier = Modifier.padding(end = 10.dp)
                ) { Text(value) }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        CustomMenuItem(text = "Confirm Send", onClick = { send() })
    }
}

