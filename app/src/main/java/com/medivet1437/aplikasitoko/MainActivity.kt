package com.medivet1437.aplikasitoko

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medivet1437.aplikasitoko.ui.theme.AppTheme
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val CASH = "Kas Toko"
private const val BANK1 = "Saldo Bank 1"
private const val BANK2 = "Saldo Bank 2"
private val ACCOUNTS = listOf(CASH, BANK1, BANK2)

// v0.8: 3 sumber saldo + transfer antar sumber. Data lama otomatis dianggap Kas Toko.
data class Entry(
    val id: Long,
    val date: String,
    val type: String,
    val amount: Long,
    val note: String,
    val proofPath: String = "",
    val account: String = CASH,
    val fromAccount: String = "",
    val toAccount: String = "",
    val linkedEntryId: Long = 0L
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { StoreApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreApp() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(loadEntries(context)) }
    var openingBalances by remember { mutableStateOf(loadOpeningBalances(context)) }
    var page by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    var transferDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var exportDialog by remember { mutableStateOf(false) }
    var shareReportDialog by remember { mutableStateOf(false) }
    var backupDialog by remember { mutableStateOf(false) }
    var openingDialog by remember { mutableStateOf(false) }
    var pendingProofPath by remember { mutableStateOf("") }
    var pendingProofToken by remember { mutableStateOf("") }
    var selectedNote by remember { mutableStateOf<Entry?>(null) }
    var noteListFilter by remember { mutableStateOf("") }

    fun save(list: List<Entry>) {
        entries = list
        saveEntries(context, list)
    }

    fun saveOpening(map: Map<String, Long>) {
        openingBalances = map
        saveOpeningBalances(context, map)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val imported = importEntries(context, uri)
            if (imported.isEmpty()) Toast.makeText(context, "Tidak ada data transaksi yang terbaca.", Toast.LENGTH_LONG).show()
            else {
                save(entries + imported)
                Toast.makeText(context, "${imported.size} transaksi berhasil diimpor.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { Toast.makeText(context, "Impor gagal: ${e.message ?: "format tidak didukung"}", Toast.LENGTH_LONG).show() }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.ms-excel")) { uri ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(exportAsExcel(entries).toByteArray(Charsets.UTF_8)) }
            Toast.makeText(context, "File Excel berhasil disimpan.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "Ekspor Excel gagal: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) try {
            writePdf(context, uri, entries, openingBalances)
            Toast.makeText(context, "PDF berhasil disimpan.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "Ekspor PDF gagal: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) try {
            writeBackup(context, uri, entries, openingBalances)
            Toast.makeText(context, "Backup berhasil disimpan.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "Backup gagal: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val restored = readBackup(context, uri)
            if (restored.entries.isEmpty() && restored.openingBalances.isEmpty() && !backupContainsEntries(context, uri)) {
                Toast.makeText(context, "File backup tidak berisi data yang valid.", Toast.LENGTH_LONG).show()
            } else {
                save(restored.entries)
                saveOpening(restored.openingBalances.ifEmpty { mapOf(CASH to 0L, BANK1 to 0L, BANK2 to 0L) })
                Toast.makeText(context, "Restore berhasil: ${restored.entries.size} transaksi.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) { Toast.makeText(context, "Restore gagal: ${e.message ?: "file tidak valid"}", Toast.LENGTH_LONG).show() }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            pendingProofPath = copyUriToProofs(context, uri, pendingProofToken)
            Toast.makeText(context, "Bukti dipilih dari galeri.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "Gagal mengambil bukti: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) try {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap == null) throw IllegalStateException("Kamera tidak mengembalikan gambar")
            pendingProofPath = saveBitmapProof(context, bitmap, pendingProofToken)
            Toast.makeText(context, "Bukti dari kamera tersimpan.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "Gagal menyimpan foto: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    val balances = calculateBalances(entries, openingBalances)
    val unpaidNotes = getUnpaidNotes(entries)
    val paidNotes = entries.filter { it.type == "Nota Terbayar" }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Column { Text("Keuangan Toko", fontWeight = FontWeight.Bold); Text("MEDIVET1437", fontSize = 12.sp, color = Color.Gray) } },
                navigationIcon = { IconButton(onClick = { page = 0 }) { Icon(Icons.Default.AccountBalanceWallet, "Beranda") } },
                actions = { IconButton(onClick = { openingDialog = true }) { Icon(Icons.Default.Settings, "Saldo awal") } }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(page == 0, { page = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("Beranda") })
                NavigationBarItem(page == 1, { page = 1 }, { Icon(Icons.Default.List, null) }, label = { Text("Transaksi") })
                NavigationBarItem(page == 2, { page = 2 }, { Icon(Icons.Default.Assessment, null) }, label = { Text("Laporan") })
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; pendingProofPath = ""; pendingProofToken = UUID.randomUUID().toString(); dialog = true }, containerColor = Color(0xFF159957)) {
                Icon(Icons.Default.Add, "Tambah", tint = Color.White)
            }
        }
    ) { padding ->
        when (page) {
            0 -> Home(entries, balances, unpaidNotes, paidNotes, padding, onAdd = { editing = null; pendingProofPath = ""; pendingProofToken = UUID.randomUUID().toString(); dialog = true }, onTransfer = { transferDialog = true }, onImport = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/plain")) }, onExport = { exportDialog = true }, onBackupRestore = { backupDialog = true }, onViewNotes = { noteListFilter = it; page = 1 })
            1 -> TransactionPage(entries, padding, initialFilter = noteListFilter, onFilterChanged = { noteListFilter = it }, onEdit = { editing = it; pendingProofPath = it.proofPath; pendingProofToken = UUID.randomUUID().toString(); if (it.type == "Transfer") transferDialog = true else dialog = true }, onDelete = { e -> save(entries.filterNot { it.id == e.id }) }, onViewProof = { openProof(context, it) }, onPayNote = { selectedNote = it; pendingProofPath = ""; pendingProofToken = UUID.randomUUID().toString() })
            else -> ReportPage(entries, balances, padding)
        }
    }

    if (exportDialog) {
        AlertDialog(onDismissRequest = { exportDialog = false }, title = { Text("Ekspor Laporan Keuangan") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportDialog = false; exportLauncher.launch("Keuangan_Toko.xls") }, Modifier.fillMaxWidth()) { Text("Ekspor Excel") }
                Button(onClick = { exportDialog = false; pdfLauncher.launch("Laporan_Keuangan_Toko.pdf") }, Modifier.fillMaxWidth()) { Text("Ekspor PDF") }
                Button(onClick = { exportDialog = false; shareReportDialog = true }, Modifier.fillMaxWidth()) { Text("Bagikan Laporan ke WhatsApp") }
            }
        }, confirmButton = { TextButton(onClick = { exportDialog = false }) { Text("Batal") } })
    }

    if (shareReportDialog) {
        AlertDialog(onDismissRequest = { shareReportDialog = false }, title = { Text("Bagikan Laporan Keuangan") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pilih format laporan keuangan.", color = Color.Gray)
                Button(onClick = {
                    shareReportDialog = false
                    val text = buildWhatsAppReport(entries, balances)
                    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); setPackage("com.whatsapp") }
                    try { context.startActivity(intent) } catch (_: Exception) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Bagikan laporan")) }
                }, Modifier.fillMaxWidth()) { Text("Text WhatsApp") }
                OutlinedButton(onClick = { shareReportDialog = false; pdfLauncher.launch("Laporan_Keuangan_Toko.pdf") }, Modifier.fillMaxWidth()) { Text("PDF") }
            }
        }, confirmButton = { TextButton(onClick = { shareReportDialog = false }) { Text("Batal") } })
    }

    if (backupDialog) {
        AlertDialog(onDismissRequest = { backupDialog = false }, title = { Text("Backup & Restore") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup mencakup transaksi, saldo awal, dan foto bukti.", color = Color.Gray)
                Button(onClick = { backupDialog = false; backupLauncher.launch("Backup_Keuangan_Toko.zip") }, Modifier.fillMaxWidth()) { Text("Backup Data") }
                OutlinedButton(onClick = { backupDialog = false; restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, Modifier.fillMaxWidth()) { Text("Restore Data") }
            }
        }, confirmButton = { TextButton(onClick = { backupDialog = false }) { Text("Batal") } })
    }

    if (openingDialog) {
        OpeningBalanceDialog(openingBalances, onDismiss = { openingDialog = false }, onSave = { saveOpening(it); openingDialog = false })
    }

    if (dialog) {
        EntryDialog(initial = editing, proofPath = pendingProofPath, onChooseGallery = { galleryLauncher.launch(arrayOf("image/*")) }, onTakeCamera = { cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }, onRemoveProof = { pendingProofPath = "" }, onDismiss = { if (editing == null) deleteProofIfExists(context, pendingProofPath); dialog = false }, onSave = { value ->
            val list = if (editing == null) entries + value.copy(id = System.currentTimeMillis(), proofPath = pendingProofPath) else entries.map { if (it.id == editing!!.id) value.copy(id = it.id, proofPath = pendingProofPath) else it }
            save(list); dialog = false
        })
    }

    if (selectedNote != null) {
        PayNoteDialog(
            note = selectedNote!!,
            proofPath = pendingProofPath,
            onChooseGallery = { galleryLauncher.launch(arrayOf("image/*")) },
            onTakeCamera = { cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) },
            onRemoveProof = { pendingProofPath = "" },
            onDismiss = { if (pendingProofPath.isNotBlank()) deleteProofIfExists(context, pendingProofPath); pendingProofPath = ""; selectedNote = null },
            onSave = { account ->
                val paid = Entry(
                    id = System.currentTimeMillis(),
                    date = SimpleDateFormat("dd/MM/yyyy", Locale("id")).format(Date()),
                    type = "Nota Terbayar",
                    amount = selectedNote!!.amount,
                    note = "Pembayaran nota: ${selectedNote!!.note}",
                    proofPath = pendingProofPath,
                    account = account,
                    linkedEntryId = selectedNote!!.id
                )
                save(entries + paid)
                pendingProofPath = ""
                selectedNote = null
                Toast.makeText(context, "Nota ditandai sudah terbayar.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (transferDialog) {
        TransferDialog(initial = editing?.takeIf { it.type == "Transfer" }, proofPath = pendingProofPath, onChooseGallery = { galleryLauncher.launch(arrayOf("image/*")) }, onTakeCamera = { cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }, onRemoveProof = { pendingProofPath = "" }, onDismiss = { if (editing == null) deleteProofIfExists(context, pendingProofPath); transferDialog = false }, onSave = { value ->
            val list = if (editing == null) entries + value.copy(id = System.currentTimeMillis(), proofPath = pendingProofPath) else entries.map { if (it.id == editing!!.id) value.copy(id = it.id, proofPath = pendingProofPath) else it }
            save(list); transferDialog = false
        })
    }
}

@Composable
private fun Home(
    entries: List<Entry>,
    balances: Map<String, Long>,
    unpaidNotes: List<Entry>,
    paidNotes: List<Entry>,
    padding: PaddingValues,
    onAdd: () -> Unit,
    onTransfer: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onBackupRestore: () -> Unit,
    onViewNotes: (String) -> Unit
) {
    val total = balances.values.sum()
    val unpaidTotal = unpaidNotes.sumOf { it.amount }
    val paidTotal = paidNotes.sumOf { it.amount }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Ringkasan Keuangan", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Saldo toko dan status nota.", color = Color.Gray)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17212B)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("TOTAL SALDO", color = Color.White.copy(alpha = .75f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(formatMoney(total), color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text("Kas Toko + Bank 1 + Bank 2", color = Color.White.copy(alpha = .7f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccountCard(CASH, balances[CASH] ?: 0L, Icons.Default.Store, Modifier.weight(1f))
                AccountCard(BANK1, balances[BANK1] ?: 0L, Icons.Default.AccountBalance, Modifier.weight(1f))
            }
        }
        item { AccountCard(BANK2, balances[BANK2] ?: 0L, Icons.Default.AccountBalance, Modifier.fillMaxWidth()) }
        item {
            Text("Status Nota", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("Ketuk kartu untuk melihat detail.", color = Color.Gray, fontSize = 12.sp)
        }
        item {
            NoteSummaryCard("Nota Belum Terbayar", unpaidNotes.size, unpaidTotal, Color(0xFFD64545)) { onViewNotes("Nota Belum Terbayar") }
        }
        item {
            NoteSummaryCard("Nota Terbayar", paidNotes.size, paidTotal, Color(0xFF159957)) { onViewNotes("Nota Terbayar") }
        }
        item { Text("Aksi Cepat", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAdd, Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Transaksi") }
                OutlinedButton(onClick = onTransfer, Modifier.weight(1f)) { Icon(Icons.Default.SwapHoriz, null); Spacer(Modifier.width(6.dp)); Text("Transfer") }
            }
        }
        item { OutlinedButton(onClick = onImport, Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Impor Excel / CSV / iREAP") } }
        item { OutlinedButton(onClick = onExport, Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Ekspor / Laporan WhatsApp") } }
        item { OutlinedButton(onClick = onBackupRestore, Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Backup & Restore") } }
        if (entries.isEmpty()) item { Card(Modifier.fillMaxWidth()) { Text("Belum ada data transaksi. Masukkan saldo awal atau transaksi toko.", Modifier.padding(20.dp), color = Color.Gray) } }
    }
}

@Composable
private fun NoteSummaryCard(title: String, count: Int, amount: Long, color: Color, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text("$count nota", color = Color.Gray, fontSize = 12.sp)
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(formatMoney(amount), fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp)
                Text("Lihat detail", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AccountCard(name: String, amount: Long, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = Color(0xFF159957))
            Spacer(Modifier.height(6.dp))
            Text(name, fontSize = 13.sp, color = Color.Gray)
            Text(formatMoney(amount), fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TransactionPage(
    entries: List<Entry>,
    padding: PaddingValues,
    initialFilter: String,
    onFilterChanged: (String) -> Unit,
    onEdit: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    onViewProof: (String) -> Unit,
    onPayNote: (Entry) -> Unit
) {
    var filter by remember(initialFilter) { mutableStateOf(initialFilter.ifBlank { "Semua" }) }
    val unpaid = getUnpaidNotes(entries)
    val filtered = when (filter) {
        "Nota Belum Terbayar" -> unpaid
        "Nota Terbayar" -> entries.filter { it.type == "Nota Terbayar" }
        CASH -> entries.filter { it.account == CASH || it.fromAccount == CASH || it.toAccount == CASH }
        BANK1 -> entries.filter { it.account == BANK1 || it.fromAccount == BANK1 || it.toAccount == BANK1 }
        BANK2 -> entries.filter { it.account == BANK2 || it.fromAccount == BANK2 || it.toAccount == BANK2 }
        else -> entries
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Text("Transaksi", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Kelola transaksi dan pembayaran nota.", color = Color.Gray)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Semua", "Nota Belum Terbayar", "Nota Terbayar", CASH, BANK1, BANK2).forEach {
                    FilterChip(filter == it, { filter = it; onFilterChanged(if (it == "Semua") "" else it) }, label = { Text(it) })
                }
            }
        }
        if (filtered.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    if (filter == "Nota Belum Terbayar") "Tidak ada nota yang belum terbayar." else if (filter == "Nota Terbayar") "Belum ada nota terbayar." else "Belum ada transaksi.",
                    Modifier.padding(20.dp), color = Color.Gray
                )
            }
        }
        items(filtered.sortedByDescending { it.id }, key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.type, fontWeight = FontWeight.Bold)
                            Text(entry.date, fontSize = 12.sp, color = Color.Gray)
                        }
                        Text(formatMoney(entry.amount), fontWeight = FontWeight.Bold, color = when (entry.type) { "Pemasukan" -> Color(0xFF159957); "Transfer" -> Color(0xFF1565C0); else -> Color(0xFFD64545) })
                    }
                    if (entry.type == "Transfer") Text("${entry.fromAccount}  →  ${entry.toAccount}", color = Color(0xFF1565C0), fontWeight = FontWeight.Medium)
                    else Text(entry.account, fontSize = 12.sp, color = Color.Gray)
                    Text(entry.note, color = Color.Gray)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        if (entry.type == "Nota Masuk" && unpaid.any { it.id == entry.id }) {
                            Button(onClick = { onPayNote(entry) }) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(4.dp)); Text("Bayar Nota") }
                        }
                        TextButton(onClick = { onEdit(entry) }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(4.dp)); Text("Koreksi") }
                        if (entry.proofPath.isNotBlank() && File(entry.proofPath).exists()) TextButton(onClick = { onViewProof(entry.proofPath) }) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(4.dp)); Text("Bukti") }
                        TextButton(onClick = { onDelete(entry) }) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Hapus") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportPage(entries: List<Entry>, balances: Map<String, Long>, padding: PaddingValues) {
    val unpaid = getUnpaidNotes(entries)
    val paid = entries.filter { it.type == "Nota Terbayar" }
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Text("Laporan Keuangan", fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("Isi laporan mengikuti ringkasan Dashboard.", color = Color.Gray) }
        item { SummaryCard("Kas Toko", balances[CASH] ?: 0L, Color(0xFF159957)) }
        item { SummaryCard("Saldo Bank 1", balances[BANK1] ?: 0L, Color(0xFF159957)) }
        item { SummaryCard("Saldo Bank 2", balances[BANK2] ?: 0L, Color(0xFF159957)) }
        item { SummaryCard("Total Saldo", balances.values.sum(), Color(0xFF1565C0)) }
        item { SummaryCard("Nota Belum Terbayar", unpaid.sumOf { it.amount }, Color(0xFFD64545)) }
        item { SummaryCard("Nota Terbayar", paid.sumOf { it.amount }, Color(0xFF159957)) }
    }
}

@Composable
private fun SummaryCard(title: String, amount: Long, color: Color) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text(title, color = Color.Gray); Text(formatMoney(amount), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color) } }
}

@Composable
private fun EntryDialog(initial: Entry?, proofPath: String, onChooseGallery: () -> Unit, onTakeCamera: () -> Unit, onRemoveProof: () -> Unit, onDismiss: () -> Unit, onSave: (Entry) -> Unit) {
    var type by remember(initial) { mutableStateOf(initial?.type ?: "Pemasukan") }
    var account by remember(initial) { mutableStateOf(initial?.account ?: CASH) }
    var date by remember(initial) { mutableStateOf(initial?.date ?: SimpleDateFormat("dd/MM/yyyy", Locale("id")).format(Date())) }
    var amount by remember(initial) { mutableStateOf(initial?.amount?.toString() ?: "") }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    val types = listOf("Pemasukan", "Pengeluaran", "Nota Masuk", "Nota Terbayar")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Tambah Transaksi" else "Koreksi Data") }, text = {
        Column {
            Text("Jenis transaksi", fontSize = 12.sp, color = Color.Gray)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { types.forEach { FilterChip(type == it, { type = it }, label = { Text(it) }) } }
            Spacer(Modifier.height(8.dp))
            Text("Masuk/keluar dari", fontSize = 12.sp, color = Color.Gray)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { ACCOUNTS.forEach { FilterChip(account == it, { account = it }, label = { Text(it) }) } }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = date, onValueChange = { date = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tanggal") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = amount, onValueChange = { amount = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Nominal") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Keterangan") }, minLines = 2)
            Spacer(Modifier.height(10.dp))
            Text("Bukti transaksi", fontWeight = FontWeight.Bold)
            Text("Nota masuk/keluar, transfer, kuitansi, atau bukti lainnya.", fontSize = 12.sp, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(onClick = onTakeCamera) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(4.dp)); Text("Kamera") }; OutlinedButton(onClick = onChooseGallery) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(4.dp)); Text("Galeri") } }
            if (proofPath.isNotBlank() && File(proofPath).exists()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("✓ Bukti sudah dipilih", color = Color(0xFF159957)); TextButton(onClick = onRemoveProof) { Text("Hapus bukti") } }
        }
    }, confirmButton = { Button(onClick = { val n = amount.toLongOrNull(); if (n != null && n > 0 && note.isNotBlank()) onSave(Entry(initial?.id ?: 0, date, type, n, note.trim(), proofPath, account = account)) }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun TransferDialog(initial: Entry?, proofPath: String, onChooseGallery: () -> Unit, onTakeCamera: () -> Unit, onRemoveProof: () -> Unit, onDismiss: () -> Unit, onSave: (Entry) -> Unit) {
    var from by remember(initial) { mutableStateOf(initial?.fromAccount ?: CASH) }
    var to by remember(initial) { mutableStateOf(initial?.toAccount ?: BANK1) }
    var date by remember(initial) { mutableStateOf(initial?.date ?: SimpleDateFormat("dd/MM/yyyy", Locale("id")).format(Date())) }
    var amount by remember(initial) { mutableStateOf(initial?.amount?.toString() ?: "") }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    var error by remember(initial) { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Transfer Antar Saldo" else "Koreksi Transfer") }, text = {
        Column {
            Text("Uang hanya dipindahkan, bukan pemasukan/pengeluaran.", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text("Dari", fontSize = 12.sp, color = Color.Gray)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { ACCOUNTS.forEach { FilterChip(from == it, { from = it }, label = { Text(it) }) } }
            Spacer(Modifier.height(8.dp))
            Text("Ke", fontSize = 12.sp, color = Color.Gray)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { ACCOUNTS.forEach { FilterChip(to == it, { to = it }, label = { Text(it) }) } }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = date, onValueChange = { date = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tanggal") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = amount, onValueChange = { amount = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Nominal") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Keterangan") }, minLines = 2)
            Spacer(Modifier.height(10.dp))
            Text("Bukti transfer (opsional)", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(onClick = onTakeCamera) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(4.dp)); Text("Kamera") }; OutlinedButton(onClick = onChooseGallery) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(4.dp)); Text("Galeri") } }
            if (proofPath.isNotBlank() && File(proofPath).exists()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("✓ Bukti tersimpan", color = Color(0xFF159957)); TextButton(onClick = onRemoveProof) { Text("Hapus") } }
            if (error.isNotBlank()) Text(error, color = Color(0xFFD64545), fontSize = 12.sp)
        }
    }, confirmButton = { Button(onClick = { val n = amount.toLongOrNull(); when { from == to -> error = "Sumber dan tujuan harus berbeda."; n == null || n <= 0 -> error = "Nominal harus diisi."; note.isBlank() -> error = "Keterangan harus diisi."; else -> onSave(Entry(initial?.id ?: 0, date, "Transfer", n, note.trim(), proofPath, fromAccount = from, toAccount = to)) } }) { Text("Simpan Transfer") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun PayNoteDialog(
    note: Entry,
    proofPath: String,
    onChooseGallery: () -> Unit,
    onTakeCamera: () -> Unit,
    onRemoveProof: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var account by remember(note.id) { mutableStateOf(CASH) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bayar Nota") },
        text = {
            Column {
                Text(note.note, fontWeight = FontWeight.Bold)
                Text(formatMoney(note.amount), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD64545))
                Spacer(Modifier.height(10.dp))
                Text("Bayar dari", fontSize = 12.sp, color = Color.Gray)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ACCOUNTS.forEach { FilterChip(account == it, { account = it }, label = { Text(it) }) }
                }
                Spacer(Modifier.height(10.dp))
                Text("Bukti pembayaran (opsional)", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onTakeCamera) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(4.dp)); Text("Kamera") }
                    OutlinedButton(onClick = onChooseGallery) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(4.dp)); Text("Galeri") }
                }
                if (proofPath.isNotBlank() && File(proofPath).exists()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("✓ Bukti dipilih", color = Color(0xFF159957))
                    TextButton(onClick = onRemoveProof) { Text("Hapus") }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(account) }) { Text("Bayar & Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun OpeningBalanceDialog(current: Map<String, Long>, onDismiss: () -> Unit, onSave: (Map<String, Long>) -> Unit) {
    var cash by remember { mutableStateOf((current[CASH] ?: 0L).toString()) }
    var bank1 by remember { mutableStateOf((current[BANK1] ?: 0L).toString()) }
    var bank2 by remember { mutableStateOf((current[BANK2] ?: 0L).toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Saldo Awal") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Masukkan saldo awal yang benar-benar dimiliki toko. Tidak ada angka contoh.", color = Color.Gray, fontSize = 12.sp)
            OutlinedTextField(value = cash, onValueChange = { cash = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text(CASH) })
            OutlinedTextField(value = bank1, onValueChange = { bank1 = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text(BANK1) })
            OutlinedTextField(value = bank2, onValueChange = { bank2 = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text(BANK2) })
        }
    }, confirmButton = { Button(onClick = { onSave(mapOf(CASH to (cash.toLongOrNull() ?: 0L), BANK1 to (bank1.toLongOrNull() ?: 0L), BANK2 to (bank2.toLongOrNull() ?: 0L))) }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

private fun getUnpaidNotes(entries: List<Entry>): List<Entry> {
    val paidLinkedIds = entries.filter { it.type == "Nota Terbayar" && it.linkedEntryId != 0L }.map { it.linkedEntryId }.toSet()
    return entries.filter { it.type == "Nota Masuk" && it.id !in paidLinkedIds }
}

private fun calculateBalances(entries: List<Entry>, opening: Map<String, Long>): Map<String, Long> {
    val result = mutableMapOf(CASH to (opening[CASH] ?: 0L), BANK1 to (opening[BANK1] ?: 0L), BANK2 to (opening[BANK2] ?: 0L))
    entries.forEach { e ->
        when (e.type) {
            "Pemasukan" -> result[e.account] = (result[e.account] ?: 0L) + e.amount
            "Pengeluaran", "Nota Terbayar" -> result[e.account] = (result[e.account] ?: 0L) - e.amount
            "Transfer" -> {
                result[e.fromAccount] = (result[e.fromAccount] ?: 0L) - e.amount
                result[e.toAccount] = (result[e.toAccount] ?: 0L) + e.amount
            }
        }
    }
    return result
}

private fun importEntries(context: Context, uri: Uri): List<Entry> {
    val name = (uri.lastPathSegment ?: "").lowercase(Locale.ROOT)
    if (name.endsWith(".xlsx")) return convertRowsToEntries(parseXlsx(context, uri))

    val text = context.contentResolver.openInputStream(uri)?.use { input ->
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
    } ?: return emptyList()

    val cleaned = text.removePrefix("\uFEFF")
    val rows = if (cleaned.contains("<table", ignoreCase = true)) {
        parseHtmlTable(cleaned)
    } else {
        // iREAP POS/Excel CSV dapat memakai koma sebagai pemisah kolom DAN
        // koma/titik sebagai bagian dari nominal. Pemisah kolom harus ditentukan
        // SATU KALI dari header, jangan dihitung ulang untuk setiap baris.
        parseDelimitedText(cleaned)
    }
    return if (rows.isEmpty()) emptyList() else convertRowsToEntries(rows)
}

private fun parseXlsx(context: Context, uri: Uri): List<List<String>> {
    val files = mutableMapOf<String, ByteArray>()
    context.contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory &&
                    (entry.name == "xl/sharedStrings.xml" ||
                     entry.name.startsWith("xl/worksheets/sheet"))) {
                    files[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    val shared = files["xl/sharedStrings.xml"]?.toString(Charsets.UTF_8)?.let { xml ->
        Regex("(?is)<si\\b[^>]*>(.*?)</si>").findAll(xml).map { m ->
            Regex("(?is)<t\\b[^>]*>(.*?)</t>").findAll(m.groupValues[1])
                .joinToString("") { it.groupValues[1] }
                .xmlUnescape()
        }.toList()
    } ?: emptyList()

    val sheetName = files.keys
        .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
        .sorted()
        .firstOrNull() ?: return emptyList()

    val sheet = files[sheetName]?.toString(Charsets.UTF_8) ?: return emptyList()

    return Regex("(?is)<row\\b[^>]*>(.*?)</row>").findAll(sheet).map { rowMatch ->
        val cellsByColumn = mutableMapOf<Int, String>()

        Regex("(?is)<c\\b([^>]*)>(.*?)</c>").findAll(rowMatch.groupValues[1]).forEach { cell ->
            val attrs = cell.groupValues[1]
            val inner = cell.groupValues[2]
            val ref = Regex("""\br\s*=\s*"([A-Z]+)\d+"""").find(attrs)?.groupValues?.get(1)
            val col = ref?.let { excelColumnToIndex(it) } ?: cellsByColumn.size

            val type = Regex("""\bt\s*=\s*"([^"]+)"""").find(attrs)?.groupValues?.get(1)
            val value = when (type) {
                "s" -> {
                    val index = Regex("(?is)<v[^>]*>(.*?)</v>").find(inner)
                        ?.groupValues?.get(1)?.trim()?.toIntOrNull()
                    shared.getOrNull(index ?: -1) ?: ""
                }
                "inlineStr" -> {
                    Regex("(?is)<t\\b[^>]*>(.*?)</t>").find(inner)
                        ?.groupValues?.get(1)?.xmlUnescape() ?: ""
                }
                else -> {
                    Regex("(?is)<v[^>]*>(.*?)</v>").find(inner)
                        ?.groupValues?.get(1)?.trim()?.xmlUnescape() ?: ""
                }
            }
            cellsByColumn[col] = value
        }

        if (cellsByColumn.isEmpty()) emptyList()
        else (0..(cellsByColumn.keys.maxOrNull() ?: 0)).map { cellsByColumn[it] ?: "" }
    }.filter { it.isNotEmpty() }.toList()
}

private fun excelColumnToIndex(column: String): Int {
    var result = 0
    for (ch in column.uppercase(Locale.ROOT)) {
        result = result * 26 + (ch - 'A' + 1)
    }
    return result - 1
}

private fun String.xmlUnescape(): String = replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")

private fun convertRowsToEntries(rows: List<List<String>>): List<Entry> {
    if (rows.isEmpty()) return emptyList()

    val normalized = rows.map { row ->
        row.map { it.trim().lowercase(Locale.ROOT) }
    }

    // Cari header berdasarkan nama kolom yang benar-benar ada di file.
    // iREAP/Excel contoh nyata: Date | Transaction | Qty | Gross IDR | Net IDR
    val headerIndex = normalized.indexOfFirst { row ->
        row.any { it == "date" || it.contains("tanggal") } &&
        row.any {
            it == "net idr" || it == "gross idr" ||
            it.contains("nominal") || it.contains("amount") ||
            it.contains("jumlah") || it.contains("total") ||
            it.contains("harga")
        }
    }

    val start = if (headerIndex >= 0) headerIndex + 1 else 0
    val header = if (headerIndex >= 0) normalized[headerIndex] else emptyList()

    fun exactOrContains(vararg keys: String): Int {
        for (key in keys) {
            val exact = header.indexOf(key)
            if (exact >= 0) return exact
        }
        for (key in keys) {
            val found = header.indexOfFirst { it.contains(key) }
            if (found >= 0) return found
        }
        return -1
    }

    val dateIdx = exactOrContains("tanggal", "date")

    // Untuk laporan iREAP, prioritaskan Net IDR sebagai nilai transaksi akhir.
    // Jika Net IDR tidak ada, baru gunakan Gross IDR/nominal/amount/total.
    val amountIdx = exactOrContains(
        "net idr",
        "net",
        "gross idr",
        "gross",
        "nominal",
        "amount",
        "jumlah",
        "total",
        "harga",
        "nilai",
        "payment"
    )

    val typeIdx = exactOrContains("jenis", "type", "tipe", "kategori", "category")
    val noteIdx = exactOrContains(
        "keterangan", "catatan", "note", "deskripsi",
        "description", "nama", "transaction"
    )
    val accountIdx = exactOrContains("akun", "account", "rekening", "sumber")

    val result = mutableListOf<Entry>()

    rows.drop(start).forEachIndexed { idx, row ->
        if (row.isEmpty()) return@forEachIndexed

        // JANGAN lagi mengambil angka terbesar dari seluruh kolom.
        // Itu yang dapat membuat Qty/Transaction/kolom lain terbaca sebagai nominal.
        if (amountIdx < 0 || amountIdx >= row.size) return@forEachIndexed

        val amount = parseMoney(row[amountIdx])
        if (amount <= 0L) return@forEachIndexed

        val date = if (dateIdx >= 0 && dateIdx < row.size && row[dateIdx].isNotBlank()) {
            row[dateIdx]
        } else {
            SimpleDateFormat("dd/MM/yyyy", Locale("id")).format(Date())
        }

        val typeRaw = if (typeIdx >= 0 && typeIdx < row.size) {
            row[typeIdx].lowercase(Locale.ROOT)
        } else {
            "pemasukan"
        }

        val type = when {
            typeRaw.contains("transfer") -> "Transfer"
            typeRaw.contains("pengeluaran") ||
                typeRaw.contains("expense") ||
                typeRaw.contains("keluar") -> "Pengeluaran"
            typeRaw.contains("nota") &&
                (typeRaw.contains("bayar") || typeRaw.contains("paid")) -> "Nota Terbayar"
            typeRaw.contains("nota") -> "Nota Masuk"
            else -> "Pemasukan"
        }

        val note = if (noteIdx >= 0 && noteIdx < row.size && row[noteIdx].isNotBlank()) {
            row[noteIdx]
        } else {
            "Impor iREAP"
        }

        val accountRaw = if (accountIdx >= 0 && accountIdx < row.size) {
            row[accountIdx].lowercase(Locale.ROOT)
        } else {
            ""
        }

        val account = when {
            accountRaw.contains("bank 2") || accountRaw.contains("bank2") -> BANK2
            accountRaw.contains("bank 1") || accountRaw.contains("bank1") -> BANK1
            else -> CASH
        }

        result += Entry(
            System.currentTimeMillis() + idx,
            date,
            type,
            amount,
            note,
            account = account
        )
    }

    return result
}

private fun parseDelimitedText(text: String): List<List<String>> {
    val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
    if (lines.isEmpty()) return emptyList()

    val separator = detectCsvSeparator(lines.first())
    val rows = lines.map { parseCsvLine(it, separator) }.toMutableList()

    // iREAP/Excel CSV kadang menyimpan angka seperti 762,990.00 tanpa quote.
    // Jika delimiter-nya juga koma, nominal akan terpecah menjadi beberapa kolom.
    // Gabungkan kembali bagian nominal berdasarkan posisi kolom nominal di header.
    if (rows.isNotEmpty()) {
        val header = rows.first().map { it.trim().lowercase(Locale.ROOT) }
        val amountIdx = header.indexOfFirst {
            it.contains("nominal") || it.contains("amount") ||
            it.contains("jumlah") || it.contains("total") || it.contains("harga") ||
            it.contains("nilai") || it.contains("payment")
        }
        if (amountIdx >= 0) {
            for (i in 1 until rows.size) {
                rows[i] = repairAmountOverflow(rows[i], amountIdx, header.size, separator)
            }
        }
    }
    return rows
}

private fun detectCsvSeparator(header: String): Char {
    if (header.contains('\t')) return '\t'
    val semicolon = header.count { it == ';' }
    val comma = header.count { it == ',' }
    return if (semicolon > comma) ';' else ','
}

private fun repairAmountOverflow(row: List<String>, amountIdx: Int, expectedColumns: Int, separator: Char): List<String> {
    if (row.size <= expectedColumns || amountIdx >= row.size) return row

    val overflow = row.size - expectedColumns
    val amountParts = overflow + 1
    val end = (amountIdx + amountParts).coerceAtMost(row.size)
    val mergedAmount = row.subList(amountIdx, end).joinToString(separator.toString())

    val repaired = mutableListOf<String>()
    repaired.addAll(row.take(amountIdx))
    repaired.add(mergedAmount)
    if (end < row.size) repaired.addAll(row.subList(end, row.size))
    return repaired
}

private fun parseCsvLine(line: String, separator: Char = detectCsvSeparator(line)): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (c == '"') {
            if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                sb.append('"')
                i++
            } else {
                quoted = !quoted
            }
        } else if (c == separator && !quoted) {
            out += sb.toString().trim()
            sb.clear()
        } else {
            sb.append(c)
        }
        i++
    }
    out += sb.toString().trim()
    return out
}
private fun parseHtmlTable(html: String): List<List<String>> = Regex("(?is)<tr[^>]*>(.*?)</tr>").findAll(html).map { m -> Regex("(?is)<(?:td|th)[^>]*>(.*?)</(?:td|th)>").findAll(m.groupValues[1]).map { cell -> cell.groupValues[1].replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").trim() }.toList() }.filter { it.isNotEmpty() }.toList()
private fun parseMoneyOrNull(value: String): Long? = try {
    parseMoney(value).takeIf { it > 0L }
} catch (_: Exception) {
    null
}

/**
 * Membaca nominal dari format Indonesia maupun format yang biasa dipakai iREAP/Excel.
 * Contoh yang semuanya dibaca sebagai nominal yang sama:
 *   762,990.00  -> 762990
 *   762.990,00  -> 762990
 *   762.990     -> 762990
 *   762,990     -> 762990
 *   Rp 762.990  -> 762990
 *   76.299.000  -> 76299000
 *   76,299,000  -> 76299000
 *
 * Karena aplikasi menyimpan rupiah sebagai Long (rupiah utuh), bagian desimal
 * yang bernilai .00 diabaikan. Jika ada pecahan selain .00, dibulatkan ke rupiah.
 */
private fun parseMoney(value: String): Long {
    var s = value.trim()
        .replace('\u00A0', ' ')
        .replace(Regex("(?i)\\b(IDR|RP)\\b"), "")
        .replace("Rp", "", ignoreCase = true)
        .trim()

    if (s.isBlank()) return 0L

    val negative = s.startsWith("-") || (s.startsWith("(") && s.endsWith(")"))
    s = s.replace(Regex("[^0-9,.-]"), "")
        .removePrefix("-")
        .removePrefix("(")
        .removeSuffix(")")

    if (s.isBlank()) return 0L

    val lastComma = s.lastIndexOf(',')
    val lastDot = s.lastIndexOf('.')

    val integerDigits: String
    val decimalDigits: String?

    when {
        lastComma >= 0 && lastDot >= 0 -> {
            // Jika dua separator ada, separator paling kanan adalah kandidat desimal.
            val decimalPos = maxOf(lastComma, lastDot)
            val digitsAfter = s.length - decimalPos - 1
            if (digitsAfter in 1..2) {
                integerDigits = s.substring(0, decimalPos).filter(Char::isDigit)
                decimalDigits = s.substring(decimalPos + 1).filter(Char::isDigit)
            } else {
                // Contoh 1.234.567 atau 1,234,567: semuanya pemisah ribuan.
                integerDigits = s.filter(Char::isDigit)
                decimalDigits = null
            }
        }
        lastComma >= 0 -> {
            val digitsAfter = s.length - lastComma - 1
            if (digitsAfter in 1..2) {
                // 762,99 -> desimal; 762,990 -> ribuan.
                integerDigits = s.substring(0, lastComma).filter(Char::isDigit)
                decimalDigits = s.substring(lastComma + 1).filter(Char::isDigit)
            } else {
                integerDigits = s.filter(Char::isDigit)
                decimalDigits = null
            }
        }
        lastDot >= 0 -> {
            val digitsAfter = s.length - lastDot - 1
            if (digitsAfter in 1..2) {
                // 762.99 -> desimal; 762.990 -> pemisah ribuan.
                integerDigits = s.substring(0, lastDot).filter(Char::isDigit)
                decimalDigits = s.substring(lastDot + 1).filter(Char::isDigit)
            } else {
                integerDigits = s.filter(Char::isDigit)
                decimalDigits = null
            }
        }
        else -> {
            integerDigits = s.filter(Char::isDigit)
            decimalDigits = null
        }
    }

    if (integerDigits.isBlank()) return 0L

    val base = integerDigits.toLongOrNull() ?: return 0L
    val cents = decimalDigits?.toLongOrNull() ?: 0L
    val result = if (decimalDigits == null || cents == 0L) {
        base
    } else {
        // Rupiah di aplikasi disimpan sebagai rupiah utuh.
        if (cents >= 50L) base + 1L else base
    }

    return if (negative) -result else result
}

private fun exportAsExcel(entries: List<Entry>): String {
    fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    val body = buildString { entries.forEach { e -> append("<tr><td>${esc(e.date)}</td><td>${esc(e.type)}</td><td>${esc(e.account)}</td><td>${esc(e.fromAccount)}</td><td>${esc(e.toAccount)}</td><td>${e.amount}</td><td>${esc(e.note)}</td></tr>\n") } }
    return """<html><head><meta charset="UTF-8"></head><body><table border="1"><tr><th>Tanggal</th><th>Jenis</th><th>Akun</th><th>Dari</th><th>Ke</th><th>Nominal</th><th>Keterangan</th></tr>$body</table></body></html>"""
}

private fun buildWhatsAppReport(entries: List<Entry>, balances: Map<String, Long>): String {
    val unpaid = getUnpaidNotes(entries)
    val paid = entries.filter { it.type == "Nota Terbayar" }
    val date = SimpleDateFormat("dd/MM/yyyy", Locale("id")).format(Date())
    return buildString {
        append("📊 LAPORAN KEUANGAN TOKO\n")
        append(date).append("\n\n")
        append("💰 SALDO\n")
        append("Kas Toko       : ").append(formatMoney(balances[CASH] ?: 0L)).append("\n")
        append("Saldo Bank 1   : ").append(formatMoney(balances[BANK1] ?: 0L)).append("\n")
        append("Saldo Bank 2   : ").append(formatMoney(balances[BANK2] ?: 0L)).append("\n")
        append("-------------------------\n")
        append("TOTAL SALDO    : ").append(formatMoney(balances.values.sum())).append("\n\n")
        append("🧾 NOTA BELUM TERBAYAR\n")
        append("Jumlah Nota    : ").append(unpaid.size).append("\n")
        append("Total Nominal  : ").append(formatMoney(unpaid.sumOf { it.amount })).append("\n\n")
        append("✅ NOTA TERBAYAR\n")
        append("Jumlah Nota    : ").append(paid.size).append("\n")
        append("Total Nominal  : ").append(formatMoney(paid.sumOf { it.amount })).append("\n")
    }
}

private fun writePdf(context: Context, uri: Uri, entries: List<Entry>, balances: Map<String, Long>) {
    val unpaid = getUnpaidNotes(entries)
    val paid = entries.filter { it.type == "Nota Terbayar" }
    val pdf = android.graphics.pdf.PdfDocument()
    val pageWidth = 595; val pageHeight = 842
    val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
    val canvas = page.canvas
    val paint = android.graphics.Paint().apply { textSize = 14f; isAntiAlias = true }
    var y = 40f
    fun line(text: String) { canvas.drawText(text.take(95), 32f, y, paint); y += 22f }
    line("LAPORAN KEUANGAN TOKO")
    y += 8f
    line("Kas Toko: ${formatMoney(balances[CASH] ?: 0L)}")
    line("Saldo Bank 1: ${formatMoney(balances[BANK1] ?: 0L)}")
    line("Saldo Bank 2: ${formatMoney(balances[BANK2] ?: 0L)}")
    line("TOTAL SALDO: ${formatMoney(balances.values.sum())}")
    y += 8f
    line("NOTA BELUM TERBAYAR: ${formatMoney(unpaid.sumOf { it.amount })}")
    line("NOTA TERBAYAR: ${formatMoney(paid.sumOf { it.amount })}")
    pdf.finishPage(page)
    context.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
    pdf.close()
}

private fun formatMoney(value: Long): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")

private fun proofDir(context: Context): File = File(context.filesDir, "proofs").apply { if (!exists()) mkdirs() }
private fun copyUriToProofs(context: Context, uri: Uri, token: String): String { val file = File(proofDir(context), "proof_${token}.jpg"); context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } } ?: throw IllegalStateException("File tidak dapat dibaca"); return file.absolutePath }
private fun saveBitmapProof(context: Context, bitmap: Bitmap, token: String): String { val file = File(proofDir(context), "proof_${token}.jpg"); file.outputStream().use { out -> if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) throw IllegalStateException("Gagal menyimpan foto") }; return file.absolutePath }
private fun deleteProofIfExists(context: Context, path: String) { if (path.isNotBlank()) runCatching { File(path).delete() } }

private fun openProof(context: Context, path: String) {
    val file = File(path); if (!file.exists()) { Toast.makeText(context, "Bukti tidak ditemukan.", Toast.LENGTH_SHORT).show(); return }
    val values = android.content.ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, file.name); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Medivet1437") }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (uri == null) { Toast.makeText(context, "Tidak dapat membuka bukti.", Toast.LENGTH_SHORT).show(); return }
    try { context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }; context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "image/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }
    catch (e: Exception) { context.contentResolver.delete(uri, null, null); Toast.makeText(context, "Gagal membuka bukti: ${e.message}", Toast.LENGTH_LONG).show() }
}

private fun entryToJson(e: Entry): JSONObject = JSONObject().apply { put("id", e.id); put("date", e.date); put("type", e.type); put("amount", e.amount); put("note", e.note); put("proofName", if (e.proofPath.isNotBlank()) File(e.proofPath).name else ""); put("account", e.account); put("fromAccount", e.fromAccount); put("toAccount", e.toAccount); put("linkedEntryId", e.linkedEntryId) }

private fun writeBackup(context: Context, uri: Uri, entries: List<Entry>, opening: Map<String, Long>) {
    context.contentResolver.openOutputStream(uri)?.use { output -> ZipOutputStream(output).use { zip ->
        val json = JSONArray(); entries.forEach { json.put(entryToJson(it)) }
        zip.putNextEntry(ZipEntry("entries.json")); zip.write(json.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
        val openingJson = JSONObject().apply { opening.forEach { (k, v) -> put(k, v) } }
        zip.putNextEntry(ZipEntry("opening_balances.json")); zip.write(openingJson.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
        entries.forEach { e -> if (e.proofPath.isNotBlank()) { val file = File(e.proofPath); if (file.exists()) { zip.putNextEntry(ZipEntry("proofs/${file.name}")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() } } }
        zip.putNextEntry(ZipEntry("backup_info.txt")); zip.write("MEDIVET1437 KEUANGAN TOKO BACKUP v2\n".toByteArray(Charsets.UTF_8)); zip.closeEntry()
    } } ?: throw IllegalStateException("Penyimpanan backup gagal")
}

data class RestoreData(val entries: List<Entry>, val openingBalances: Map<String, Long>)

private fun readBackup(context: Context, uri: Uri): RestoreData {
    val temp = mutableMapOf<String, ByteArray>(); var entriesJson = "[]"; var openingJson = "{}"
    context.contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(input).use { zip -> var e = zip.nextEntry; while (e != null) { if (!e.isDirectory) { val safe = e.name.replace('\\', '/'); when { safe == "entries.json" -> entriesJson = zip.readBytes().toString(Charsets.UTF_8); safe == "opening_balances.json" -> openingJson = zip.readBytes().toString(Charsets.UTF_8); safe.startsWith("proofs/") && safe.substringAfterLast('/').isNotBlank() && !safe.contains("..") -> temp[safe.substringAfterLast('/')] = zip.readBytes() }; }; zip.closeEntry(); e = zip.nextEntry } } } ?: throw IllegalStateException("File backup tidak dapat dibaca")
    val a = JSONArray(entriesJson); val restored = mutableListOf<Entry>(); proofDir(context).mkdirs()
    for (i in 0 until a.length()) { val o = a.getJSONObject(i); val proofName = o.optString("proofName", ""); var path = ""; if (proofName.isNotBlank() && temp.containsKey(proofName)) { val f = File(proofDir(context), "restored_${UUID.randomUUID()}_${sanitizeFileName(proofName)}"); f.writeBytes(temp[proofName]!!); path = f.absolutePath }; restored += Entry(o.getLong("id"), o.getString("date"), o.getString("type"), o.getLong("amount"), o.getString("note"), path, o.optString("account", CASH), o.optString("fromAccount", ""), o.optString("toAccount", ""), o.optLong("linkedEntryId", 0L)) }
    val oj = JSONObject(openingJson); val opening = mapOf(CASH to oj.optLong(CASH, 0L), BANK1 to oj.optLong(BANK1, 0L), BANK2 to oj.optLong(BANK2, 0L))
    return RestoreData(restored, opening)
}

private fun backupContainsEntries(context: Context, uri: Uri): Boolean = runCatching { context.contentResolver.openInputStream(uri)?.use { ZipInputStream(it).use { zip -> var e = zip.nextEntry; while (e != null) { if (!e.isDirectory && e.name == "entries.json") return@use true; zip.closeEntry(); e = zip.nextEntry }; false } } ?: false }.getOrDefault(false)
private fun sanitizeFileName(name: String): String = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")

private fun loadEntries(context: Context): List<Entry> {
    val raw = context.getSharedPreferences("finance", Context.MODE_PRIVATE).getString("entries", "[]") ?: "[]"; val a = JSONArray(raw)
    return buildList { for (i in 0 until a.length()) { val o = a.getJSONObject(i); add(Entry(o.getLong("id"), o.getString("date"), o.getString("type"), o.getLong("amount"), o.getString("note"), o.optString("proofPath", ""), o.optString("account", CASH), o.optString("fromAccount", ""), o.optString("toAccount", ""), o.optLong("linkedEntryId", 0L))) } }
}
private fun saveEntries(context: Context, entries: List<Entry>) { val a = JSONArray(); entries.forEach { a.put(JSONObject().apply { put("id", it.id); put("date", it.date); put("type", it.type); put("amount", it.amount); put("note", it.note); put("proofPath", it.proofPath); put("account", it.account); put("fromAccount", it.fromAccount); put("toAccount", it.toAccount); put("linkedEntryId", it.linkedEntryId) }) }; context.getSharedPreferences("finance", Context.MODE_PRIVATE).edit().putString("entries", a.toString()).apply() }

private fun loadOpeningBalances(context: Context): Map<String, Long> {
    val p = context.getSharedPreferences("finance", Context.MODE_PRIVATE); return mapOf(CASH to p.getLong("opening_cash", 0L), BANK1 to p.getLong("opening_bank1", 0L), BANK2 to p.getLong("opening_bank2", 0L))
}
private fun saveOpeningBalances(context: Context, map: Map<String, Long>) { context.getSharedPreferences("finance", Context.MODE_PRIVATE).edit().putLong("opening_cash", map[CASH] ?: 0L).putLong("opening_bank1", map[BANK1] ?: 0L).putLong("opening_bank2", map[BANK2] ?: 0L).apply() }
