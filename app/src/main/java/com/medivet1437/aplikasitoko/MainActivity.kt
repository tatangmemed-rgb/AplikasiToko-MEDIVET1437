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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

// v0.5: bukti transaksi + backup/restore. Data contoh tidak dibuat oleh aplikasi.
data class Entry(
    val id: Long,
    val date: String,
    val type: String,
    val amount: Long,
    val note: String,
    val proofPath: String = ""
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
    var page by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var exportDialog by remember { mutableStateOf(false) }
    var shareReportDialog by remember { mutableStateOf(false) }
    var backupDialog by remember { mutableStateOf(false) }
    var pendingProofPath by remember { mutableStateOf("") }
    var pendingProofToken by remember { mutableStateOf("") }

    fun save(list: List<Entry>) {
        entries = list
        saveEntries(context, list)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val imported = importEntries(context, uri)
                if (imported.isEmpty()) Toast.makeText(context, "Tidak ada data transaksi yang terbaca.", Toast.LENGTH_LONG).show()
                else {
                    save(entries + imported)
                    Toast.makeText(context, "${imported.size} transaksi berhasil diimpor.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Impor gagal: ${e.message ?: "format file tidak didukung"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.ms-excel")) { uri ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(exportAsExcel(entries).toByteArray(Charsets.UTF_8)) }
            Toast.makeText(context, "File Excel berhasil disimpan.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "Ekspor Excel gagal: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) try {
            writePdf(context, uri, entries)
            Toast.makeText(context, "PDF berhasil disimpan.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "Ekspor PDF gagal: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) try {
            writeBackup(context, uri, entries)
            Toast.makeText(context, "Backup berhasil disimpan.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "Backup gagal: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val restored = readBackup(context, uri)
            if (restored.isEmpty() && !backupContainsEntries(context, uri)) {
                Toast.makeText(context, "File backup tidak berisi data yang valid.", Toast.LENGTH_LONG).show()
            } else {
                save(restored)
                Toast.makeText(context, "Restore berhasil: ${restored.size} transaksi.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) { Toast.makeText(context, "Restore gagal: ${e.message ?: "file tidak valid"}", Toast.LENGTH_LONG).show() }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                pendingProofPath = copyUriToProofs(context, uri, pendingProofToken)
                Toast.makeText(context, "Bukti dipilih dari galeri.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(context, "Gagal mengambil bukti: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val bitmap = result.data?.extras?.get("data") as? Bitmap
                if (bitmap == null) throw IllegalStateException("Kamera tidak mengembalikan gambar")
                pendingProofPath = saveBitmapProof(context, bitmap, pendingProofToken)
                Toast.makeText(context, "Bukti dari kamera tersimpan.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(context, "Gagal menyimpan foto: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Keuangan Toko", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { page = 0 }) { Icon(Icons.Default.AccountBalanceWallet, "Beranda") } }
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
            FloatingActionButton(onClick = {
                editing = null
                pendingProofPath = ""
                pendingProofToken = UUID.randomUUID().toString()
                dialog = true
            }, containerColor = Color(0xFF159957)) { Icon(Icons.Default.Add, "Tambah", tint = Color.White) }
        }
    ) { padding ->
        when (page) {
            0 -> Home(entries, padding,
                onAdd = {
                    editing = null; pendingProofPath = ""; pendingProofToken = UUID.randomUUID().toString(); dialog = true
                },
                onImport = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/plain")) },
                onExport = { exportDialog = true },
                onBackupRestore = { backupDialog = true }
            )
            1 -> TransactionPage(entries, padding,
                onEdit = {
                    editing = it
                    pendingProofPath = it.proofPath
                    pendingProofToken = UUID.randomUUID().toString()
                    dialog = true
                },
                onDelete = { e -> save(entries.filterNot { it.id == e.id }) },
                onViewProof = { path -> openProof(context, path) }
            )
            else -> ReportPage(entries, padding)
        }
    }

    if (exportDialog) {
        AlertDialog(onDismissRequest = { exportDialog = false }, title = { Text("Ekspor Laporan Keuangan") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportDialog = false; exportLauncher.launch("Keuangan_Toko.xls") }, Modifier.fillMaxWidth()) { Text("Ekspor Excel") }
                Button(onClick = { exportDialog = false; pdfLauncher.launch("Laporan_Keuangan_Toko.pdf") }, Modifier.fillMaxWidth()) { Text("Ekspor PDF") }
                Button(
                    onClick = { exportDialog = false; shareReportDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Bagikan Laporan ke WhatsApp") }
            }
        }, confirmButton = { TextButton(onClick = { exportDialog = false }) { Text("Batal") } })
    }

    if (shareReportDialog) {
        AlertDialog(
            onDismissRequest = { shareReportDialog = false },
            title = { Text("Bagikan Laporan Keuangan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Pilih format laporan yang akan dibagikan melalui WhatsApp.",
                        color = Color.Gray
                    )
                    Button(
                        onClick = {
                            shareReportDialog = false
                            val text = buildWhatsAppReport(entries)
                            val whatsapp = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                                setPackage("com.whatsapp")
                            }
                            try {
                                context.startActivity(whatsapp)
                            } catch (_: Exception) {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        },
                                        "Bagikan laporan"
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Text WhatsApp") }
                    OutlinedButton(
                        onClick = {
                            shareReportDialog = false
                            pdfLauncher.launch("Laporan_Keuangan_Toko.pdf")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("PDF") }
                }
            },
            confirmButton = {
                TextButton(onClick = { shareReportDialog = false }) { Text("Batal") }
            }
        )
    }

    if (backupDialog) {
        AlertDialog(onDismissRequest = { backupDialog = false }, title = { Text("Backup & Restore") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup mencakup transaksi dan foto bukti yang tersimpan di aplikasi.", color = Color.Gray)
                Button(onClick = { backupDialog = false; backupLauncher.launch("Backup_Keuangan_Toko.zip") }, Modifier.fillMaxWidth()) { Text("Backup Data") }
                OutlinedButton(onClick = { backupDialog = false; restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, Modifier.fillMaxWidth()) { Text("Restore Data") }
            }
        }, confirmButton = { TextButton(onClick = { backupDialog = false }) { Text("Batal") } })
    }

    if (dialog) {
        EntryDialog(
            initial = editing,
            proofPath = pendingProofPath,
            onChooseGallery = { galleryLauncher.launch(arrayOf("image/*")) },
            onTakeCamera = { cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) },
            onRemoveProof = { pendingProofPath = "" },
            onDismiss = {
                if (editing == null) deleteProofIfExists(context, pendingProofPath)
                dialog = false
            },
            onSave = { value ->
                val list = if (editing == null) {
                    entries + value.copy(id = System.currentTimeMillis(), proofPath = pendingProofPath)
                } else {
                    entries.map { if (it.id == editing!!.id) value.copy(id = it.id, proofPath = pendingProofPath) else it }
                }
                save(list)
                dialog = false
            }
        )
    }
}

@Composable
private fun Home(entries: List<Entry>, padding: PaddingValues, onAdd: () -> Unit, onImport: () -> Unit, onExport: () -> Unit, onBackupRestore: () -> Unit) {
    val income = entries.filter { it.type == "Pemasukan" }.sumOf { it.amount }
    val expense = entries.filter { it.type == "Pengeluaran" }.sumOf { it.amount }
    val unpaid = entries.filter { it.type == "Nota Masuk" }.sumOf { it.amount }
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Ringkasan Keuangan", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Semua angka berasal dari data yang Anda masukkan.", color = Color.Gray) }
        item { SummaryCard("Pemasukan", income, Color(0xFF159957)) }
        item { SummaryCard("Pengeluaran", expense, Color(0xFFD64545)) }
        item { SummaryCard("Selisih", income - expense, Color(0xFF1565C0)) }
        item { SummaryCard("Nota Belum Dibayar", unpaid, Color(0xFFE98A20)) }
        item { Button(onClick = onAdd, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Tambah Transaksi") } }
        item { OutlinedButton(onClick = onImport, Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Impor Excel / CSV / iREAP") } }
        item { OutlinedButton(onClick = onExport, Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Ekspor / WhatsApp") } }
        item { OutlinedButton(onClick = onBackupRestore, Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Backup & Restore") } }
        if (entries.isEmpty()) item { Card(Modifier.fillMaxWidth()) { Text("Belum ada data transaksi.", Modifier.padding(24.dp), color = Color.Gray) } }
    }
}

@Composable private fun SummaryCard(title: String, amount: Long, color: Color) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text(title, color = Color.Gray); Text(formatMoney(amount), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color) } } }

@Composable
private fun TransactionPage(entries: List<Entry>, padding: PaddingValues, onEdit: (Entry) -> Unit, onDelete: (Entry) -> Unit, onViewProof: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Transaksi", fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("Koreksi, lihat bukti, atau hapus data.", color = Color.Gray); Spacer(Modifier.height(10.dp)) }
        items(entries.reversed(), key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text(entry.type, fontWeight = FontWeight.Bold); Text(entry.date, fontSize = 12.sp, color = Color.Gray); Text(entry.note, color = Color.Gray); Text(formatMoney(entry.amount), fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = { onEdit(entry) }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(4.dp)); Text("Koreksi") }
                    if (entry.proofPath.isNotBlank() && File(entry.proofPath).exists()) TextButton(onClick = { onViewProof(entry.proofPath) }) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(4.dp)); Text("Lihat Bukti") }
                    TextButton(onClick = { onDelete(entry) }) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Hapus") }
                }
            } }
        }
    }
}

@Composable private fun ReportPage(entries: List<Entry>, padding: PaddingValues) {
    val income = entries.filter { it.type == "Pemasukan" }.sumOf { it.amount }
    val expense = entries.filter { it.type == "Pengeluaran" }.sumOf { it.amount }
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Text("Laporan", fontSize = 25.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp))
        SummaryCard("Total Pemasukan", income, Color(0xFF159957)); Spacer(Modifier.height(8.dp)); SummaryCard("Total Pengeluaran", expense, Color(0xFFD64545)); Spacer(Modifier.height(8.dp)); SummaryCard("Selisih", income - expense, Color(0xFF1565C0)); Spacer(Modifier.height(12.dp)); Text("Jumlah transaksi: ${entries.size}", color = Color.Gray)
    }
}

@Composable
private fun EntryDialog(initial: Entry?, proofPath: String, onChooseGallery: () -> Unit, onTakeCamera: () -> Unit, onRemoveProof: () -> Unit, onDismiss: () -> Unit, onSave: (Entry) -> Unit) {
    var type by remember(initial) { mutableStateOf(initial?.type ?: "Pemasukan") }
    var date by remember(initial) { mutableStateOf(initial?.date ?: SimpleDateFormat("dd/MM/yyyy", Locale("id")).format(Date())) }
    var amount by remember(initial) { mutableStateOf(initial?.amount?.toString() ?: "") }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    val types = listOf("Pemasukan", "Pengeluaran", "Nota Masuk", "Nota Terbayar")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Tambah Transaksi" else "Koreksi Data") }, text = {
        Column {
            Text("Jenis transaksi", fontSize = 12.sp, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { types.take(2).forEach { FilterChip(type == it, { type = it }, label = { Text(it) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { types.drop(2).forEach { FilterChip(type == it, { type = it }, label = { Text(it) }) } }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = date, onValueChange = { date = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tanggal") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = amount, onValueChange = { amount = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Nominal") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Keterangan") }, minLines = 2)
            Spacer(Modifier.height(10.dp))
            Text("Bukti transaksi", fontWeight = FontWeight.Bold)
            Text("Nota masuk/keluar, transfer, kuitansi, atau bukti lainnya.", fontSize = 12.sp, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onTakeCamera) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(4.dp)); Text("Kamera") }
                OutlinedButton(onClick = onChooseGallery) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(4.dp)); Text("Galeri") }
            }
            if (proofPath.isNotBlank() && File(proofPath).exists()) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("✓ Bukti sudah dipilih", color = Color(0xFF159957))
                    TextButton(onClick = onRemoveProof) { Text("Hapus bukti") }
                }
            }
        }
    }, confirmButton = {
        Button(onClick = { val n = amount.toLongOrNull(); if (n != null && n > 0 && note.isNotBlank()) onSave(Entry(initial?.id ?: 0, date, type, n, note.trim(), proofPath)) }) { Text("Simpan") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

private fun importEntries(context: Context, uri: Uri): List<Entry> {
    val name = (uri.lastPathSegment ?: "").lowercase(Locale.ROOT)
    if (name.endsWith(".xlsx")) return convertRowsToEntries(parseXlsx(context, uri))
    val text = context.contentResolver.openInputStream(uri)?.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText() } ?: return emptyList()
    val cleaned = text.removePrefix("\uFEFF")
    val rows = if (cleaned.contains("<table", ignoreCase = true)) parseHtmlTable(cleaned) else cleaned.lineSequence().filter { it.isNotBlank() }.map { parseCsvLine(it) }.toList()
    return if (rows.isEmpty()) emptyList() else convertRowsToEntries(rows)
}

private fun parseXlsx(context: Context, uri: Uri): List<List<String>> {
    val entries = mutableMapOf<String, ByteArray>()
    context.contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(input).use { zip -> var e = zip.nextEntry; while (e != null) { if (!e.isDirectory && (e.name == "xl/sharedStrings.xml" || e.name == "xl/worksheets/sheet1.xml")) entries[e.name] = zip.readBytes(); zip.closeEntry(); e = zip.nextEntry } } }
    val shared = entries["xl/sharedStrings.xml"]?.toString(Charsets.UTF_8)?.let { xml -> Regex("(?is)<si[^>]*>(.*?)</si>").findAll(xml).map { m -> Regex("(?is)<t[^>]*>(.*?)</t>").findAll(m.groupValues[1]).joinToString("") { it.groupValues[1] }.xmlUnescape() }.toList() } ?: emptyList()
    val sheet = entries["xl/worksheets/sheet1.xml"]?.toString(Charsets.UTF_8) ?: return emptyList()
    return Regex("(?is)<row[^>]*>(.*?)</row>").findAll(sheet).map { rowMatch -> Regex("(?is)<c\\b([^>]*)>(.*?)</c>").findAll(rowMatch.groupValues[1]).map { cell -> val attrs = cell.groupValues[1]; val inner = cell.groupValues[2]; val value = Regex("(?is)<v[^>]*>(.*?)</v>").find(inner)?.groupValues?.get(1)?.trim() ?: ""; if (Regex("""\bt\s*=\s*"s""" ).containsMatchIn(attrs)) shared.getOrNull(value.toIntOrNull() ?: -1) ?: value else Regex("(?is)<is[^>]*>.*?<t[^>]*>(.*?)</t>.*?</is>").find(inner)?.groupValues?.get(1)?.xmlUnescape() ?: value.xmlUnescape() }.toList() }.filter { it.isNotEmpty() }.toList()
}

private fun String.xmlUnescape(): String = replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")

private fun convertRowsToEntries(rows: List<List<String>>): List<Entry> {
    if (rows.isEmpty()) return emptyList()
    val normalized = rows.map { row -> row.map { it.trim().lowercase(Locale.ROOT) } }
    val headerIndex = normalized.indexOfFirst { row -> row.any { it.contains("tanggal") || it == "date" } && row.any { it.contains("nominal") || it.contains("amount") || it.contains("harga") || it.contains("total") || it.contains("jumlah") } }
    val start = if (headerIndex >= 0) headerIndex + 1 else 0
    val header = if (headerIndex >= 0) normalized[headerIndex] else emptyList()
    fun indexOfAny(vararg keys: String): Int = keys.firstNotNullOfOrNull { key -> header.indexOfFirst { it.contains(key) }.takeIf { it >= 0 } } ?: -1
    val dateIdx = indexOfAny("tanggal", "date"); val typeIdx = indexOfAny("jenis", "type", "tipe", "kategori", "category"); val amountIdx = indexOfAny("nominal", "amount", "jumlah", "total", "harga"); val noteIdx = indexOfAny("keterangan", "catatan", "note", "deskripsi", "description", "nama")
    val result = mutableListOf<Entry>()
    rows.drop(start).forEachIndexed { idx, row ->
        if (row.isEmpty()) return@forEachIndexed
        val amount = if (amountIdx >= 0 && amountIdx < row.size) parseMoney(row[amountIdx]) else row.mapNotNull { parseMoneyOrNull(it) }.maxOrNull() ?: 0L
        if (amount <= 0L) return@forEachIndexed
        val date = if (dateIdx >= 0 && dateIdx < row.size && row[dateIdx].isNotBlank()) row[dateIdx] else SimpleDateFormat("dd/MM/yyyy", Locale("id")).format(Date())
        val typeRaw = if (typeIdx >= 0 && typeIdx < row.size) row[typeIdx].lowercase(Locale.ROOT) else "pemasukan"
        val type = when { typeRaw.contains("pengeluaran") || typeRaw.contains("expense") || typeRaw.contains("keluar") -> "Pengeluaran"; typeRaw.contains("nota") && (typeRaw.contains("bayar") || typeRaw.contains("paid")) -> "Nota Terbayar"; typeRaw.contains("nota") -> "Nota Masuk"; else -> "Pemasukan" }
        val note = if (noteIdx >= 0 && noteIdx < row.size && row[noteIdx].isNotBlank()) row[noteIdx] else "Impor data"
        result += Entry(System.currentTimeMillis() + idx, date, type, amount, note)
    }
    return result
}

private fun parseCsvLine(line: String): List<String> { val separator = when { line.count { it == ';' } > line.count { it == ',' } -> ';'; line.contains('\t') -> '\t'; else -> ',' }; val out = mutableListOf<String>(); val sb = StringBuilder(); var quoted = false; var i = 0; while (i < line.length) { val c = line[i]; if (c == '"') { if (quoted && i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ } else quoted = !quoted } else if (c == separator && !quoted) { out += sb.toString(); sb.clear() } else sb.append(c); i++ }; out += sb.toString(); return out }
private fun parseHtmlTable(html: String): List<List<String>> = Regex("(?is)<tr[^>]*>(.*?)</tr>").findAll(html).map { m -> Regex("(?is)<(?:td|th)[^>]*>(.*?)</(?:td|th)>").findAll(m.groupValues[1]).map { cell -> cell.groupValues[1].replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").trim() }.toList() }.filter { it.isNotEmpty() }.toList()
private fun parseMoneyOrNull(value: String): Long? = try { parseMoney(value).takeIf { it > 0 } } catch (_: Exception) { null }
private fun parseMoney(value: String): Long { val v = value.trim().replace("Rp", "", true).replace("IDR", "", true).replace(".", "").replace(",00", "").replace(",", ""); return v.filter { it.isDigit() }.toLongOrNull() ?: 0L }

private fun exportAsExcel(entries: List<Entry>): String {
    fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    val body = buildString {
        entries.forEach { e ->
            append("<tr><td>${esc(e.date)}</td><td>${esc(e.type)}</td><td>${e.amount}</td><td>${esc(e.note)}</td></tr>\n")
        }
    }
    return """<html><head><meta charset="UTF-8"></head><body><table border="1"><tr><th>Tanggal</th><th>Jenis</th><th>Nominal</th><th>Keterangan</th></tr>$body</table></body></html>"""
}
private fun buildWhatsAppReport(entries: List<Entry>): String { val income = entries.filter { it.type == "Pemasukan" }.sumOf { it.amount }; val expense = entries.filter { it.type == "Pengeluaran" }.sumOf { it.amount }; val unpaid = entries.filter { it.type == "Nota Masuk" }.sumOf { it.amount }; return buildString { append("LAPORAN KEUANGAN TOKO\n\n"); append("Pemasukan: ${formatMoney(income)}\n"); append("Pengeluaran: ${formatMoney(expense)}\n"); append("Selisih: ${formatMoney(income - expense)}\n"); append("Nota belum dibayar: ${formatMoney(unpaid)}\n"); append("\nJumlah transaksi: ${entries.size}\n") } }

private fun writePdf(context: Context, uri: Uri, entries: List<Entry>) {
    val pdf = android.graphics.pdf.PdfDocument(); val pageWidth = 595; val pageHeight = 842; var pageNumber = 1
    var page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()); var canvas = page.canvas
    val paint = android.graphics.Paint().apply { textSize = 14f; isAntiAlias = true }; var y = 40f
    canvas.drawText("Laporan Keuangan Toko", 32f, y, paint); y += 28f
    entries.forEach { e -> if (y > 800f) { pdf.finishPage(page); pageNumber++; page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()); canvas = page.canvas; y = 40f }; canvas.drawText("${e.date} | ${e.type} | ${formatMoney(e.amount)} | ${e.note}".take(85), 32f, y, paint); y += 20f }
    pdf.finishPage(page); context.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }; pdf.close()
}

private fun formatMoney(value: Long): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")

private fun proofDir(context: Context): File = File(context.filesDir, "proofs").apply { if (!exists()) mkdirs() }
private fun copyUriToProofs(context: Context, uri: Uri, token: String): String {
    val file = File(proofDir(context), "proof_${token}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } } ?: throw IllegalStateException("File tidak dapat dibaca")
    return file.absolutePath
}
private fun saveBitmapProof(context: Context, bitmap: Bitmap, token: String): String {
    val file = File(proofDir(context), "proof_${token}.jpg")
    file.outputStream().use { out -> if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) throw IllegalStateException("Gagal menyimpan foto") }
    return file.absolutePath
}
private fun deleteProofIfExists(context: Context, path: String) { if (path.isNotBlank()) runCatching { File(path).delete() } }

private fun openProof(context: Context, path: String) {
    val file = File(path); if (!file.exists()) { Toast.makeText(context, "Bukti tidak ditemukan.", Toast.LENGTH_SHORT).show(); return }
    // FileProvider tidak diperlukan: abre galeri com base no media URI criado temporariamente.
    val values = android.content.ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, file.name); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Medivet1437") }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (uri == null) { Toast.makeText(context, "Tidak dapat membuka bukti.", Toast.LENGTH_SHORT).show(); return }
    try { context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }; context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "image/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }
    catch (e: Exception) { context.contentResolver.delete(uri, null, null); Toast.makeText(context, "Gagal membuka bukti: ${e.message}", Toast.LENGTH_LONG).show() }
}

private fun entryToJson(e: Entry): JSONObject = JSONObject().apply { put("id", e.id); put("date", e.date); put("type", e.type); put("amount", e.amount); put("note", e.note); put("proofName", if (e.proofPath.isNotBlank()) File(e.proofPath).name else "") }

private fun writeBackup(context: Context, uri: Uri, entries: List<Entry>) {
    context.contentResolver.openOutputStream(uri)?.use { output -> ZipOutputStream(output).use { zip ->
        val json = JSONArray(); entries.forEach { json.put(entryToJson(it)) }
        zip.putNextEntry(ZipEntry("entries.json")); zip.write(json.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
        entries.forEach { e -> if (e.proofPath.isNotBlank()) { val file = File(e.proofPath); if (file.exists()) { zip.putNextEntry(ZipEntry("proofs/${file.name}")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() } } }
        zip.putNextEntry(ZipEntry("backup_info.txt")); zip.write("MEDIVET1437 KEUANGAN TOKO BACKUP v1\n".toByteArray(Charsets.UTF_8)); zip.closeEntry()
    } } ?: throw IllegalStateException("Penyimpanan backup gagal")
}

private fun readBackup(context: Context, uri: Uri): List<Entry> {
    val temp = mutableMapOf<String, ByteArray>(); var entriesJson = "[]"
    context.contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(input).use { zip ->
        var e = zip.nextEntry
        while (e != null) {
            if (!e.isDirectory) {
                val safe = e.name.replace('\\', '/')
                if (safe == "entries.json") entriesJson = zip.readBytes().toString(Charsets.UTF_8)
                else if (safe.startsWith("proofs/") && safe.substringAfterLast('/').isNotBlank() && !safe.contains("..")) temp[safe.substringAfterLast('/')] = zip.readBytes()
            }
            zip.closeEntry(); e = zip.nextEntry
        }
    } } ?: throw IllegalStateException("File backup tidak dapat dibaca")
    val a = JSONArray(entriesJson); val restored = mutableListOf<Entry>(); proofDir(context).mkdirs()
    for (i in 0 until a.length()) {
        val o = a.getJSONObject(i); val proofName = o.optString("proofName", ""); var path = ""
        if (proofName.isNotBlank() && temp.containsKey(proofName)) { val f = File(proofDir(context), "restored_${UUID.randomUUID()}_${sanitizeFileName(proofName)}"); f.writeBytes(temp[proofName]!!); path = f.absolutePath }
        restored += Entry(o.getLong("id"), o.getString("date"), o.getString("type"), o.getLong("amount"), o.getString("note"), path)
    }
    return restored
}

// Dipakai hanya untuk memberi pesan pada backup kosong tanpa perlu membaca file ZIP dua kali di UI.
private fun backupContainsEntries(context: Context, uri: Uri): Boolean = runCatching {
    context.contentResolver.openInputStream(uri)?.use { ZipInputStream(it).use { zip -> var e = zip.nextEntry; while (e != null) { if (!e.isDirectory && e.name == "entries.json") return@use true; zip.closeEntry(); e = zip.nextEntry }; false } } ?: false
}.getOrDefault(false)
private fun sanitizeFileName(name: String): String = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")

private fun loadEntries(context: Context): List<Entry> {
    val raw = context.getSharedPreferences("finance", Context.MODE_PRIVATE).getString("entries", "[]") ?: "[]"
    val a = JSONArray(raw)
    return buildList { for (i in 0 until a.length()) { val o = a.getJSONObject(i); add(Entry(o.getLong("id"), o.getString("date"), o.getString("type"), o.getLong("amount"), o.getString("note"), o.optString("proofPath", ""))) } }
}
private fun saveEntries(context: Context, entries: List<Entry>) {
    val a = JSONArray(); entries.forEach { a.put(JSONObject().apply { put("id", it.id); put("date", it.date); put("type", it.type); put("amount", it.amount); put("note", it.note); put("proofPath", it.proofPath) }) }
    context.getSharedPreferences("finance", Context.MODE_PRIVATE).edit().putString("entries", a.toString()).apply()
}
