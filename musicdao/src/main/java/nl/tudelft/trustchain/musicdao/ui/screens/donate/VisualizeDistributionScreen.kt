package nl.tudelft.trustchain.musicdao.ui.screens.donate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.DistributionStat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import java.util.Locale


@Composable
fun VisualizeDistributionScreen(bitcoinWalletViewModel: BitcoinWalletViewModel) {
    val options = listOf("User Centric", "Pro-Rata")
    val selectedOption = rememberSaveable { mutableStateOf(options[0]) }
    val expanded = rememberSaveable { mutableStateOf(false) }
    val proRataDist by bitcoinWalletViewModel.lastProRataStats.collectAsState()
    val userCentricDist by bitcoinWalletViewModel.lastUserCentricStats.collectAsState()

    LaunchedEffect(Unit) {
        bitcoinWalletViewModel.distributeProportionally()
        bitcoinWalletViewModel.distributeProportionallyUserCentric()
    }

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

        val activeStats = when (selectedOption.value) {
            "Pro-Rata" -> proRataDist
            "User Centric" -> userCentricDist
            else -> null
        }

        activeStats?.let { stats ->
            Spacer(modifier = Modifier.height(20.dp))
            DistributionBarChart(data = stats)
        } ?: Text("No distribution data available.")

    }
}

@Composable
fun DistributionBarChart(data: List<DistributionStat>) {
    val maxValue = data.maxOfOrNull { it.amountSat }?.toFloat() ?: 1f
    val barWidth = 36.dp
    val spacing = 30.dp
    val chartHeight = 140.dp
    val yAxisLabelWidth = 60.dp
    val yAxisLabels = 5

    val barColors = listOf(
        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800),
        Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFFFFEB3B)
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(12.dp)
    ) {
        val density = LocalDensity.current

        Column {
            Spacer(modifier = Modifier.height(32.dp))
            // Chart Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            ) {
                val barSpacingPx = with(density) { (barWidth + spacing).toPx() }
                val barWidthPx = with(density) { barWidth.toPx() }
                val yBase = size.height

                val labelOffsetPx = with(density) { yAxisLabelWidth.toPx() }


                // Y-axis grid and labels
                for (i in 0..yAxisLabels) {
                    val y = size.height - (i / yAxisLabels.toFloat()) * size.height
                    drawLine(
                        color = Color.Black,
                        start = Offset(60f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )

                    val labelSats = ((i / yAxisLabels.toFloat()) * maxValue).toLong()
                    val labelBtc = labelSats / 100_000_000.0
                    val labelText = if (labelBtc >= 0.01) {
                        String.format(Locale.ENGLISH,"%.2f BTC", labelBtc)
                    } else {
                        String.format(Locale.ENGLISH,"%.4f BTC", labelBtc)
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        labelText,
                        0f,
                        y,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 10.sp.toPx()
                        }
                    )
                }

                // Bars and percentages
                data.forEachIndexed { index, stat ->
                    val extraStartPaddingPx = with(density) { 12.dp.toPx() }
                    val x = labelOffsetPx + extraStartPaddingPx + index * barSpacingPx
                    val barHeight = (stat.amountSat / maxValue) * size.height
                    val barTop = yBase - barHeight
                    var color = barColors[index % barColors.size]

                    drawRect(
                        color = color,
                        topLeft = Offset(x, barTop),
                        size = Size(barWidthPx, barHeight)
                    )
                    // Percentage label
                    drawContext.canvas.nativeCanvas.drawText(
                        "${(stat.percentage * 100).toInt()}%",
                        x + barWidthPx / 2,
                        barTop - 6.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = Color.White
                            textSize = 12.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }

            // X-axis artist labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Spacer(modifier = Modifier.width(yAxisLabelWidth))

                data.forEachIndexed { index, stat ->
                    Box(
                        modifier = Modifier
                            .width(barWidth + spacing)
                            .padding(horizontal = 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stat.artistName.take(7),
                            color = Color.Black,
                            fontSize = 12.sp,
                            maxLines = 2,
                            softWrap = false,
                            textAlign = TextAlign.Left
                        )
                    }
                }
            }
        }
    }
}



