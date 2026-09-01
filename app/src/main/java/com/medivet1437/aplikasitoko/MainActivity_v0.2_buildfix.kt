package com.medivet1437.aplikasitoko

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medivet1437.aplikasitoko.ui.theme.AppTheme

private val AppBackground = Color(0xFFF6F8FB)
private val TextDark = Color(0xFF18202A)
private val TextMuted = Color(0xFF687381)
private val Green = Color(0xFF159957)
private val Blue = Color(0xFF1565C0)
private val Orange = Color(0xFFF28C28)
private val Red = Color(0xFFD64545)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                StoreApp()
            }
        }
    }
}

@Composable
fun StoreApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var drawerOpen by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { _: Uri? -> }

    fun shareWhatsApp() {
        val text = """
            LAPORAN TOKO MEDIVET1437

            Ringkasan laporan dapat dilihat pada Aplikasi Toko.
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan laporan"))
    }

    val drawerState = rememberDrawerState(
        if (drawerOpen) DrawerValue.Open else DrawerValue.Closed
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = 330.dp)
            ) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "MEDIVET1437",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    "Aplikasi Keuangan Toko",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    fontSize = 13.sp,
                    color = TextMuted
                )
                Spacer(Modifier.height(18.dp))

                DrawerItem("Dashboard", Icons.Default.Home, selectedTab == 0) {
                    selectedTab = 0
                    drawerOpen = false
                }
                DrawerItem("Transaksi", Icons.Default.SwapVert, selectedTab == 1) {
                    selectedTab = 1
                    drawerOpen = false
                }
                DrawerItem("Nota Masuk", Icons.Default.ReceiptLong, false) {
                    selectedTab = 1
                    drawerOpen = false
                }
                DrawerItem("Nota Belum Dibayar", Icons.Default.Schedule, false) {
                    selectedTab = 1
                    drawerOpen = false
                }
                DrawerItem("Pengeluaran", Icons.Default.Payments, false) {
                    selectedTab = 1
                    drawerOpen = false
                }
                DrawerItem("Kas & Bank", Icons.Default.AccountBalance, false) {
                    selectedTab = 1
                    drawerOpen = false
                }
                DrawerItem("Hutang", Icons.Default.RequestQuote, false) {
                    selectedTab = 1
                    drawerOpen = false
                }
                DrawerItem("Piutang", Icons.Default.Person, false) {
                    selectedTab = 1
                    drawerOpen = false
                }
                DrawerItem("Stok & Barang", Icons.Default.Inventory2, false) {
                    selectedTab = 1
                    drawerOpen = false
                }
                DrawerItem("Laporan", Icons.Default.Assessment, selectedTab == 2) {
                    selectedTab = 2
                    drawerOpen = false
                }

                HorizontalDivider(Modifier.padding(vertical = 10.dp))

                DrawerItem("Backup & Restore", Icons.Default.Backup, false) {}
                DrawerItem("Pengaturan", Icons.Default.Settings, selectedTab == 3) {
                    selectedTab = 3
                    drawerOpen = false
                }
            }
        }
    ) {
        Scaffold(
            containerColor = AppBackground,
            topBar = {
                TopBar(
                    onMenu = { drawerOpen = true }
                )
            },
            bottomBar = {
                BottomBar(selectedTab) { selectedTab = it }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { },
                    containerColor = Green,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Add, "Tambah")
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedTab) {
                    0 -> Dashboard(
                        onImport = {
                            picker.launch(
                                arrayOf(
                                    "text/csv",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                )
                            )
                        },
                        onShare = { shareWhatsApp() }
                    )
                    1 -> Transactions()
                    2 -> SimplePage("Laporan", "Laporan keuangan dan penjualan")
                    else -> SimplePage("Pengaturan", "Pengaturan aplikasi")
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(title, fontSize = 15.sp) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

@Composable
private fun TopBar(onMenu: () -> Unit) {
    Surface(
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMenu,
                modifier = Modifier.size(50.dp)
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Menu",
                    modifier = Modifier.size(30.dp),
                    tint = TextDark
                )
            }

            Image(
                painter = painterResource(com.medivet1437.aplikasitoko.R.drawable.medivet1437_logo),
                contentDescription = "Logo MEDIVET1437",
                modifier = Modifier.size(50.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.width(10.dp))

            Column {
                Text(
                    "Aplikasi Toko",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    "MEDIVET1437",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Spacer(Modifier.weight(1f))

            IconButton(onClick = {}) {
                Icon(
                    Icons.Default.NotificationsNone,
                    contentDescription = "Notifikasi",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun Dashboard(
    onImport: () -> Unit,
    onShare: () -> Unit
) {
    val horizontal = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Text(
            "Selamat datang 👋",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )
        Text(
            "Ringkasan kondisi toko",
            fontSize = 14.sp,
            color = TextMuted
        )

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    "TOTAL SALDO",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Rp69.430.000",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Kas + Bank + QRIS",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        SectionTitle("Aksi Cepat")

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.horizontalScroll(horizontal),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAction(
                icon = Icons.Default.FileDownload,
                title = "Import iREAP",
                subtitle = "Excel / CSV",
                buttonText = "IMPORT",
                onClick = onImport
            )
            QuickAction(
                icon = Icons.Default.FileUpload,
                title = "Export",
                subtitle = "Data / laporan",
                buttonText = "EXPORT",
                onClick = {}
            )
            QuickAction(
                icon = Icons.Default.Share,
                title = "WhatsApp",
                subtitle = "Bagikan laporan",
                buttonText = "KIRIM",
                onClick = onShare
            )
        }

        Spacer(Modifier.height(22.dp))

        SectionTitle("Ringkasan Hari Ini")

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.horizontalScroll(horizontal),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard("Penjualan", "Rp3,25 jt", Green)
            MetricCard("HPP", "Rp2,42 jt", Orange)
            MetricCard("Laba Kotor", "Rp830 rb", Blue)
            MetricCard("Pengeluaran", "Rp380 rb", Red)
            MetricCard("Laba Bersih", "Rp450 rb", Green)
        }

        Spacer(Modifier.height(22.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Penjualan 7 Hari",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            "Performa penjualan",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    Text(
                        "Lihat →",
                        color = Blue,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    "Rp3,25 jt",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "▁▃▂▃▂▃▅",
                    fontSize = 34.sp,
                    color = Green
                )

                Text(
                    "26 Agu     28 Agu     30 Agu     01 Sep",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        SectionTitle("Perlu Perhatian")

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.horizontalScroll(horizontal),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AlertCard("18", "Nota belum dibayar", "Rp12.500.000", Red)
            AlertCard("5", "Piutang jatuh tempo", "Rp4.750.000", Orange)
            AlertCard("7", "Stok menipis", "Periksa stok", Color(0xFFD5A600))
        }

        Spacer(Modifier.height(22.dp))

        SectionTitle("Transaksi Terakhir")

        Spacer(Modifier.height(8.dp))

        repeat(4) { index ->
            TransactionRow(
                title = if (index == 2) "Pengeluaran" else "Penjualan",
                number = "S2026090100${5 - index}",
                amount = if (index == 2) "- Rp150.000" else "Rp650.000",
                payment = if (index == 1) "QRIS" else "Tunai",
                negative = index == 2
            )
        }

        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextDark
    )
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.width(185.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = Blue
            )
            Spacer(Modifier.height(9.dp))
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = TextMuted
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(13.dp)
            ) {
                Text(
                    buttonText,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    valueColor: Color
) {
    Card(
        modifier = Modifier.width(155.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Spacer(Modifier.height(3.dp))
            Text("hari ini", fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
private fun AlertCard(
    number: String,
    title: String,
    value: String,
    accent: Color
) {
    Card(
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                number,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
            )
            Text(value, fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            Text("Lihat detail →", fontSize = 12.sp, color = Blue)
        }
    }
}

@Composable
private fun TransactionRow(
    title: String,
    number: String,
    amount: String,
    payment: String,
    negative: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(13.dp),
                color = if (negative) Color(0xFFFFE9E9) else Color(0xFFE8F7EF)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (negative) Icons.Default.RemoveCircleOutline
                        else Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = if (negative) Red else Green
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    number,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    amount,
                    fontWeight = FontWeight.Bold,
                    color = if (negative) Red else Green
                )
                Text(
                    payment,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun Transactions() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text(
            "Transaksi",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )
        Text(
            "Lihat dan kelola transaksi toko",
            fontSize = 14.sp,
            color = TextMuted
        )

        Spacer(Modifier.height(18.dp))

        OutlinedButton(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.DateRange, null)
            Spacer(Modifier.width(8.dp))
            Text("01/09/2026 — 30/09/2026")
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.ViewColumn, null)
            Spacer(Modifier.width(8.dp))
            Text("Pengaturan Kolom")
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Cari transaksi…") },
            leadingIcon = {
                Icon(Icons.Default.Search, null)
            },
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(18.dp))

        Text(
            "Tampilkan 25 baris",
            fontSize = 13.sp,
            color = TextMuted
        )

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = {}, shape = RoundedCornerShape(12.dp)) {
                Text("CSV")
            }
            OutlinedButton(onClick = {}, shape = RoundedCornerShape(12.dp)) {
                Text("Excel")
            }
            OutlinedButton(onClick = {}, shape = RoundedCornerShape(12.dp)) {
                Text("PDF")
            }
            Button(onClick = {}, shape = RoundedCornerShape(12.dp)) {
                Text("WhatsApp")
            }
        }

        Spacer(Modifier.height(18.dp))

        repeat(5) { index ->
            TransactionRow(
                title = "Penjualan",
                number = "S2026090100${index + 1}",
                amount = "Rp650.000",
                payment = if (index % 2 == 0) "Tunai" else "QRIS",
                negative = false
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SimplePage(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )
        Text(
            subtitle,
            fontSize = 14.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun BottomBar(
    selected: Int,
    onSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 4.dp
    ) {
        NavigationBarItem(
            selected = selected == 0,
            onClick = { onSelected(0) },
            icon = { Icon(Icons.Default.Home, null) },
            label = { Text("Beranda") }
        )
        NavigationBarItem(
            selected = selected == 1,
            onClick = { onSelected(1) },
            icon = { Icon(Icons.Default.SwapVert, null) },
            label = { Text("Transaksi") }
        )
        NavigationBarItem(
            selected = selected == 2,
            onClick = { onSelected(2) },
            icon = { Icon(Icons.Default.Assessment, null) },
            label = { Text("Laporan") }
        )
        NavigationBarItem(
            selected = selected == 3,
            onClick = { onSelected(3) },
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text("Pengaturan") }
        )
    }
}
