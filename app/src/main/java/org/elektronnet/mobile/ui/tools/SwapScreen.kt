package org.elektronnet.mobile.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.ChainConfig
import org.elektronnet.mobile.R
import org.elektronnet.mobile.kdf.CoinBalance
import org.elektronnet.mobile.kdf.KdfState
import org.elektronnet.mobile.kdf.MakerOrder
import org.elektronnet.mobile.kdf.Orderbook
import org.elektronnet.mobile.kdf.SwapStatus
import org.elektronnet.mobile.service.NodeHost

private val TESTNET_PAIRS = listOf(
    "tELEK" to "tBTC",
    "tELEK" to "SEPOLIAETH",
    "tBTC" to "SEPOLIAETH",
)

private val MAINNET_PAIRS = listOf(
    "ELEK" to "BTC",
    "ELEK" to "ETH",
    "BTC" to "ETH",
)

@Composable
fun SwapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kdf = NodeHost.kdfSupervisor

    val availablePairs = remember {
        if (ChainConfig.isTestnet) TESTNET_PAIRS else MAINNET_PAIRS
    }
    var selectedPairIndex by remember { mutableIntStateOf(0) }
    val currentPair = availablePairs[selectedPairIndex]
    val base = currentPair.first
    val rel = currentPair.second

    var orderbook by remember { mutableStateOf<Orderbook?>(null) }
    var baseBalance by remember { mutableStateOf<CoinBalance?>(null) }
    var relBalance by remember { mutableStateOf<CoinBalance?>(null) }
    var myOrders by remember { mutableStateOf<List<MakerOrder>>(emptyList()) }
    var recentSwaps by remember { mutableStateOf<List<SwapStatus>>(emptyList()) }

    var actionTab by remember { mutableIntStateOf(0) } // 0: Instant Swap (Taker), 1: Limit Order (Maker)
    var swapVolume by remember { mutableStateOf("") }
    var makerPrice by remember { mutableStateOf("") }
    var makerVolume by remember { mutableStateOf("") }

    var message by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isOperating by remember { mutableStateOf(false) }

    fun refreshData() {
        val client = kdf?.client ?: return
        if (kdf.state != KdfState.Running) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val ob = client.orderbook(base, rel)
                val bb = runCatching { client.myBalance(base) }.getOrNull()
                val rb = runCatching { client.myBalance(rel) }.getOrNull()
                val ords = client.myOrders().makerOrders
                val swaps = client.myRecentSwaps(5)
                withContext(Dispatchers.Main) {
                    orderbook = ob
                    baseBalance = bb
                    relBalance = rb
                    myOrders = ords
                    recentSwaps = swaps
                }
            }
        }
    }

    LaunchedEffect(kdf?.state, selectedPairIndex) {
        if (kdf?.state == KdfState.Running) {
            while (true) {
                refreshData()
                delay(3_000)
            }
        }
    }

    ToolPage(title = stringResource(R.string.swap_title), onBack = onBack) {
        // Status & Lifecycle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dotColor = when (kdf?.state) {
                            is KdfState.Running -> Color(0xFF4CAF50)
                            is KdfState.Starting -> Color(0xFFFF9800)
                            is KdfState.Error -> MaterialTheme.colorScheme.error
                            else -> Color.Gray
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (kdf?.state) {
                                is KdfState.Running -> "KDF P2P Mesh Aktiv"
                                is KdfState.Starting -> "KDF Startet..."
                                is KdfState.Error -> "KDF Fehler"
                                else -> "KDF Gestoppt"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    when (kdf?.state) {
                        is KdfState.Stopped, is KdfState.Error -> {
                            Button(
                                onClick = { kdf.start() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            ) {
                                Text("Starten")
                            }
                        }
                        is KdfState.Running -> {
                            OutlinedButton(onClick = { kdf.stop() }) {
                                Text("Stoppen")
                            }
                        }
                        is KdfState.Starting -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        null -> {}
                    }
                }

                if (kdf?.state is KdfState.Error) {
                    ErrorText((kdf.state as KdfState.Error).message)
                }
                if (kdf?.state == KdfState.Running && kdf.versionInfo.isNotBlank()) {
                    Text(
                        kdf.versionInfo,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (kdf?.state != KdfState.Running) {
            Hint("Starte den KDF Swap Daemon, um dezentral über das Peer-to-Peer Mesh zu handeln.")
            return@ToolPage
        }

        // Trading Pair Selector
        Text("Handelspaar", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availablePairs.forEachIndexed { index, (b, r) ->
                val selected = index == selectedPairIndex
                OutlinedButton(
                    onClick = {
                        selectedPairIndex = index
                        orderbook = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (selected) ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) else ButtonDefaults.outlinedButtonColors(),
                ) {
                    Text("$b / $r", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Balances Row
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(base, style = MaterialTheme.typography.labelSmall)
                    Text(
                        baseBalance?.balance?.toPlainString() ?: "0.00",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(rel, style = MaterialTheme.typography.labelSmall)
                    Text(
                        relBalance?.balance?.toPlainString() ?: "0.00",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Action Tabs: Sofort-Tausch vs Limit Order
        TabRow(selectedTabIndex = actionTab) {
            Tab(selected = actionTab == 0, onClick = { actionTab = 0 }, text = { Text("Sofort-Tausch") })
            Tab(selected = actionTab == 1, onClick = { actionTab = 1 }, text = { Text("Limit-Order") })
        }

        // Form Fields
        if (actionTab == 0) {
            // Instant Swap (Taker)
            val bestAsk = orderbook?.asks?.minByOrNull { it.price }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sofort kaufen ($base gegen $rel)", style = MaterialTheme.typography.titleSmall)
                    if (bestAsk != null) {
                        Text(
                            "Bestes Angebot: ${bestAsk.price.toPlainString()} $rel pro $base (Verfügbar: ${bestAsk.maxVolume.toPlainString()})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Hint("Aktuell kein passendes Verkaufsangebot im Orderbuch.")
                    }

                    OutlinedTextField(
                        value = swapVolume,
                        onValueChange = { swapVolume = it },
                        label = { Text("Menge ($base)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )

                    val estimatedCost = runCatching {
                        val vol = BigDecimal(swapVolume.ifBlank { "0" })
                        val pr = bestAsk?.price ?: BigDecimal.ZERO
                        (vol * pr).toPlainString()
                    }.getOrDefault("0")

                    if (swapVolume.isNotBlank() && bestAsk != null) {
                        Text("Geschätzte Kosten: $estimatedCost $rel", style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            val client = kdf.client
                            val pr = bestAsk?.price?.toPlainString() ?: return@Button
                            val vol = swapVolume.trim()
                            if (vol.isBlank()) return@Button
                            isOperating = true
                            errorMessage = null
                            message = null
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val uuid = client.buy(base, rel, pr, vol)
                                    withContext(Dispatchers.Main) {
                                        message = "Swap gestartet! UUID: ${uuid?.take(8)}..."
                                        swapVolume = ""
                                        isOperating = false
                                        refreshData()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        errorMessage = e.message
                                        isOperating = false
                                    }
                                }
                            }
                        },
                        enabled = !isOperating && swapVolume.isNotBlank() && bestAsk != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isOperating) "Tausche..." else "Jetzt tauschen ($base kaufen)")
                    }
                }
            }
        } else {
            // Limit Order (Maker)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Limit-Order einstellen ($base verkaufen gegen $rel)", style = MaterialTheme.typography.titleSmall)

                    OutlinedTextField(
                        value = makerPrice,
                        onValueChange = { makerPrice = it },
                        label = { Text("Preis ($rel pro $base)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )

                    OutlinedTextField(
                        value = makerVolume,
                        onValueChange = { makerVolume = it },
                        label = { Text("Menge ($base)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )

                    Button(
                        onClick = {
                            val client = kdf.client
                            val pr = makerPrice.trim()
                            val vol = makerVolume.trim()
                            if (pr.isBlank() || vol.isBlank()) return@Button
                            isOperating = true
                            errorMessage = null
                            message = null
                            scope.launch(Dispatchers.IO) {
                                try {
                                    client.setprice(base, rel, pr, vol, cancelPrevious = false)
                                    withContext(Dispatchers.Main) {
                                        message = "Order erfolgreich ins Orderbuch gestellt!"
                                        makerPrice = ""
                                        makerVolume = ""
                                        isOperating = false
                                        refreshData()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        errorMessage = e.message
                                        isOperating = false
                                    }
                                }
                            }
                        },
                        enabled = !isOperating && makerPrice.isNotBlank() && makerVolume.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isOperating) "Stelle Order..." else "Order ins Orderbuch stellen")
                    }
                }
            }
        }

        if (message != null) {
            Text(message.orEmpty(), color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium)
        }
        if (errorMessage != null) {
            ErrorText(errorMessage.orEmpty())
        }

        // Live Orderbook
        Text("Orderbuch ($base / $rel)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Preis ($rel)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Menge ($base)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()

                // Asks (Sells)
                val asks = orderbook?.asks.orEmpty().take(5)
                if (asks.isEmpty()) {
                    Text("Keine Verkaufsangebote", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    asks.forEach { ask ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    actionTab = 0
                                    swapVolume = ask.maxVolume.toPlainString()
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(ask.price.toPlainString(), color = Color(0xFFE53935), fontFamily = FontFamily.Monospace)
                            Text(ask.maxVolume.toPlainString(), fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Bids (Buys)
                val bids = orderbook?.bids.orEmpty().take(5)
                if (bids.isEmpty()) {
                    Text("Keine Kaufangebote", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    bids.forEach { bid ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(bid.price.toPlainString(), color = Color(0xFF43A047), fontFamily = FontFamily.Monospace)
                            Text(bid.maxVolume.toPlainString(), fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Active Maker Orders
        if (myOrders.isNotEmpty()) {
            Text("Meine aktiven Maker-Orders (${myOrders.size})", style = MaterialTheme.typography.titleMedium)
            myOrders.forEach { ord ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("${ord.base} -> ${ord.rel}", fontWeight = FontWeight.SemiBold)
                            Text("Preis: ${ord.price.toPlainString()} | Offen: ${ord.availableAmount.toPlainString()}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (ord.cancellable) {
                            TextButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        kdf.client.cancelOrder(ord.uuid)
                                        refreshData()
                                    }
                                },
                            ) {
                                Text("Stornieren", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Recent Swaps
        if (recentSwaps.isNotEmpty()) {
            Text("Letzte Swaps", style = MaterialTheme.typography.titleMedium)
            recentSwaps.forEach { sw ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${sw.myAmount.toPlainString()} ${sw.myCoin} -> ${sw.otherAmount.toPlainString()} ${sw.otherCoin}",
                                fontWeight = FontWeight.SemiBold,
                            )
                            val stateColor = when (sw.state) {
                                "Finished" -> Color(0xFF2E7D32)
                                "Failed" -> MaterialTheme.colorScheme.error
                                else -> Color(0xFFE65100)
                            }
                            Text(sw.state, color = stateColor, fontWeight = FontWeight.Bold)
                        }
                        Text("UUID: ${sw.uuid.take(12)}...", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        if (sw.events.isNotEmpty()) {
                            Text(
                                "Schritte: " + sw.events.joinToString(" -> ") { it.type },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
