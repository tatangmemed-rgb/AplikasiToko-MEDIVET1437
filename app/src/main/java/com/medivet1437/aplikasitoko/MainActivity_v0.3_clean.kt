package com.medivet1437.aplikasitoko

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medivet1437.aplikasitoko.ui.theme.AppTheme

private val Background = Color(0xFFF6F7FA)
private val Dark = Color(0xFF18202A)
private val Muted = Color(0xFF687381)
private val Green = Color(0xFF159957)
private val Blue = Color(0xFF1565C0)
private val Orange = Color(0xFFE98A20)
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
private fun StoreApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var drawerOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val drawerState = rememberDrawerState(
        if (drawerOpen) DrawerValue.Open else DrawerValue.Closed
    )

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { _: Uri? -> }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                selectedTab = selectedTab,
                onSelect = {
                    selectedTab = it
                    drawerOpen = false
                }
            )
        }
    ) {
        Scaffold(
            containerColor = Background,
            topBar = {
                AppTopBar(
                    onMenu = { drawerOpen = true }
                )
            },
            bottomBar = {
                AppBottomBar(
                    selected = selectedTab,
                    onSelected = { selectedTab = it }
                )
            },
            floatingActionButton = {
                if (selectedTab == 0 || selectedTab == 1) {
                    FloatingActionButton(
                        onClick = { selectedTab = 1 },
                        containerColor = Green,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah")
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedTab) {
                    0 -> Dashboard(
                        onImport = {
                            importLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                )
                            )
                        },
                        onWhatsApp = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Laporan keuangan toko dari Aplikasi Toko MEDIVET1437"
                                )
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Bagikan laporan")
                            )
                        }
                    )
                    1 -> FinancePage()
                    2 -> ReportsPage()
                    else -> SettingsPage()
                }
            }
        }
    }
}

@Composable
private fun AppTopBar(onMenu: () -> Unit) {
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
                    modifier = Modifier.size(31.dp),
                    tint = Dark
                )
            }

            Image(
                painter = painterResource(R.drawable.medivet1437_logo),
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
                    color = Dark
                )
                Text(
                    "MEDIVET1437",
                    fontSize = 12.sp,
                    color = Muted
                )
            }

            Spacer(Modifier.weight(1f))

            IconButton(onClick = {}) {
                Icon(
                    Icons.Default.NotificationsNone,
                    contentDescription = "Notifikasi",
                    modifier = Modifier.size(27.dp),
                    tint = Dark
                )
            }
        }
    }
}

@Composable
private fun Dashboard(
    onImport: () -> Unit,
    onWhatsApp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Text(
            "Keuangan Toko",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = Dark
        )
        Text(
            "Catat, pantau, dan kendalikan keuangan toko.",
            fontSize = 14.sp,
            color = Muted
        )

        Spacer(Modifier.height(20.dp))

        FinancialSummaryCard()

        Spacer(Modifier.height(22.dp))

        SectionTitle("Input Keuangan")
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FinanceActionCard(
                icon = Icons.Default.ReceiptLong,
                title = "Nota Masuk",
                subtitle = "Catat pembelian",
                onClick = {}
            )
            FinanceActionCard(
                icon = Icons.Default.Schedule,
                title = "Belum Dibayar",
                subtitle = "Pantau kewajiban",
                onClick = {}
            )
            FinanceActionCard(
                icon = Icons.Default.CheckCircle,
                title = "Nota Terbayar",
                subtitle = "Catat pembayaran",
                onClick = {}
            )
            FinanceActionCard(
                icon = Icons.Default.Payments,
                title = "Pengeluaran",
                subtitle = "Catat biaya toko",
                onClick = {}
            )
            FinanceActionCard(
                icon = Icons.Default.AttachMoney,
                title = "Pemasukan",
                subtitle = "Catat pemasukan",
                onClick = {}
            )
        }

        Spacer(Modifier.height(22.dp))

        SectionTitle("Data Pendukung")
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SupportAction(
                icon = Icons.Default.Download,
                title = "Impor iREAP",
                subtitle = "Data penjualan / stok",
                onClick = onImport
            )
            SupportAction(
                icon = Icons.Default.Upload,
                title = "Ekspor",
                subtitle = "CSV • Excel • PDF",
                onClick = {}
            )
            SupportAction(
                icon = Icons.Default.Share,
                title = "WhatsApp",
                subtitle = "Bagikan laporan",
                onClick = onWhatsApp
            )
        }

        Spacer(Modifier.height(22.dp))

        EmptyFinanceCard(
            title = "Belum ada transaksi keuangan",
            text = "Masukkan transaksi secara manual untuk mulai membentuk laporan keuangan toko."
        )

        Spacer(Modifier.height(18.dp))

        EmptyFinanceCard(
            title = "Belum ada data laporan",
            text = "Saldo, laba, hutang, piutang, dan arus kas akan dihitung dari transaksi yang Anda masukkan."
        )

        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun FinancialSummaryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "RINGKASAN KEUANGAN",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Muted
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryItem("Saldo", "Belum ada", Green, Modifier.weight(1f))
                SummaryItem("Pemasukan", "Belum ada", Blue, Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryItem("Pengeluaran", "Belum ada", Red, Modifier.weight(1f))
                SummaryItem("Laba Bersih", "Belum ada", Orange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryItem(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Background
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(5.dp))
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
private fun FinanceActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.width(175.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(15.dp)) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(31.dp),
                tint = Green
            )
            Spacer(Modifier.height(9.dp))
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Dark
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = Muted
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(43.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("BUKA")
            }
        }
    }
}

@Composable
private fun SupportAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.width(175.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(15.dp)) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(31.dp),
                tint = Blue
            )
            Spacer(Modifier.height(9.dp))
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Dark
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = Muted
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(43.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("BUKA")
            }
        }
    }
}

@Composable
private fun EmptyFinanceCard(title: String, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = Muted
            )
            Spacer(Modifier.height(9.dp))
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Dark
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text,
                fontSize = 13.sp,
                color = Muted
            )
        }
    }
}

@Composable
private fun FinancePage() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text(
            "Keuangan",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = Dark
        )
        Text(
            "Semua pencatatan keuangan toko ada di sini.",
            fontSize = 14.sp,
            color = Muted
        )

        Spacer(Modifier.height(20.dp))

        FinanceMenuButton("Nota Masuk", "Catat nota pembelian", Icons.Default.ReceiptLong)
        FinanceMenuButton("Nota Belum Dibayar", "Hutang kepada pemasok", Icons.Default.Schedule)
        FinanceMenuButton("Nota Terbayar", "Pembayaran nota", Icons.Default.CheckCircle)
        FinanceMenuButton("Pemasukan", "Pemasukan lain di luar penjualan", Icons.Default.AddCircle)
        FinanceMenuButton("Pengeluaran", "Biaya operasional toko", Icons.Default.Payments)
        FinanceMenuButton("Kas & Bank", "Mutasi kas dan rekening", Icons.Default.AccountBalance)
        FinanceMenuButton("Hutang & Piutang", "Pantau kewajiban dan tagihan", Icons.Default.RequestQuote)

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun FinanceMenuButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = Green
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Dark)
                Text(subtitle, fontSize = 12.sp, color = Muted)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun ReportsPage() {
    SimplePage(
        "Laporan",
        "Laporan keuangan akan dihitung dari transaksi yang benar-benar dimasukkan."
    )
}

@Composable
private fun SettingsPage() {
    SimplePage(
        "Pengaturan",
        "Atur toko, format laporan, data, dan preferensi aplikasi."
    )
}

@Composable
private fun SimplePage(title: String, subtitle: String) {
    Column(Modifier.padding(20.dp)) {
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Dark)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, fontSize = 14.sp, color = Muted)
    }
}

@Composable
private fun AppDrawer(
    selectedTab: Int,
    onSelect: (Int) -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 330.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            "MEDIVET1437",
            modifier = Modifier.padding(horizontal = 24.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Dark
        )
        Text(
            "Keuangan & administrasi toko",
            modifier = Modifier.padding(horizontal = 24.dp),
            fontSize = 13.sp,
            color = Muted
        )

        Spacer(Modifier.height(18.dp))

        DrawerItem("Beranda", Icons.Default.Home, selectedTab == 0) { onSelect(0) }
        DrawerItem("Keuangan", Icons.Default.AccountBalanceWallet, selectedTab == 1) { onSelect(1) }
        DrawerItem("Laporan", Icons.Default.Assessment, selectedTab == 2) { onSelect(2) }
        DrawerItem("Pengaturan", Icons.Default.Settings, selectedTab == 3) { onSelect(3) }

        HorizontalDivider(Modifier.padding(vertical = 10.dp))

        DrawerItem("Impor iREAP", Icons.Default.Download, false) {}
        DrawerItem("Ekspor Data", Icons.Default.Upload, false) {}
        DrawerItem("Backup & Restore", Icons.Default.Backup, false) {}
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
        icon = { Icon(icon, null) },
        label = { Text(title, fontSize = 15.sp) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

@Composable
private fun AppBottomBar(
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
            icon = { Icon(Icons.Default.AccountBalanceWallet, null) },
            label = { Text("Keuangan") }
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
