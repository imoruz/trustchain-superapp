package nl.tudelft.trustchain.musicdao.ui.screens.donate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun VisualizeDistributionScreen(navController: NavController) {
    val options = listOf("User Centric", "Pro-Rata")
    val selectedOption = rememberSaveable { mutableStateOf(options[0]) }
    val expanded = rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.padding(20.dp)) {
        Text("Visualize Distribution", fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(20.dp))

        Text("Select Distribution Mode")
        androidx.compose.material.DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            options.forEach { option ->
                androidx.compose.material.DropdownMenuItem(onClick = {
                    selectedOption.value = option
                    expanded.value = false
                }) {
                    Text(option)
                }
            }
        }

        Button(onClick = { expanded.value = true }) {
            Text("Current: ${selectedOption.value}")
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text("Distribution view for: ${selectedOption.value}")
    }
}
