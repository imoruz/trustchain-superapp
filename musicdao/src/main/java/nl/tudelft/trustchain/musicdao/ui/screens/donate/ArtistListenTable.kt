package nl.tudelft.trustchain.musicdao.ui.screens.donate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ArtistListenTable(listens: List<ArtistListen>) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.DarkGray)
                .padding(8.dp)
        ) {
            Text("Artist Address", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("User Address", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Streams", fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
        }
        listens.forEach { listen ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            ) {
                Text(listen.address, modifier = Modifier.weight(1f))
                Text(listen.userAddress, modifier = Modifier.weight(1f))
                Text(listen.listens.toString(), modifier = Modifier.width(60.dp))
                Text(listen.userListens.toString(), modifier = Modifier.width(60.dp))
            }
        }

    }
}
