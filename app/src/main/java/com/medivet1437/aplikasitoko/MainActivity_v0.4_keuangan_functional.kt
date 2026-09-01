package com.medivet1437.aplikasitoko

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medivet1437.aplikasitoko.ui.theme.AppTheme
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import java.text.SimpleDateFormat
import java.util.*

data class Entry(
    val id: Long,
    val date: String,
    val type: String,
    val amount: Long,
    val note: String
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var entries by remember { mutableStateOf(loadEntries(context)) }
    var page by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var exportDialog by remember { mutableStateOf(false) }

    fun save(list: List<Entry>) {
        entries = list
        saveEntries(context, list)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val imported = importEntries(context, uri)
                if (imported.isEmpty()) {
                    Toast.makeText(context, "Tidak ada data transaksi yang terbaca.", Toast.LENGTH_LONG).show()
                } else {
                    save(entries + imported)
                    Toast.makeText(context, "${imported.size} transaksi berhasil diimpor.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Impor gagal: ${e.message ?: "format file tidak didukung"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.ms-excel")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(exportAsExcel(entries).toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "File Excel berhasil disimpan.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ekspor Excel gagal: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                writePdf(context, uri, entries)
                Toast.makeText(context, "PDF berhasil disimpan.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ekspor PDF gagal: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Keuangan Toko", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { page = 0 }) {
                        Icon(Icons.Default.AccountBalanceWallet, "Beranda")
                    }
                }
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
            FloatingActionButton(
                onClick = { editing = null; dialog = true },
                containerColor = Color(0xFF159957)
            ) { Icon(Icons.Default.Add, "Tambah", tint = Color.White) }
        }
    ) { padding ->
        when (page) {
            0 -> Home(
                entries,
                padding,
                onAdd = { editing = null; dialog = true },
                onImport = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/plain")) },
                onExport = { exportDialog = true }
            )
            1 -> TransactionPage(entries, padding,
                onEdit = { editing = it; dialog = true },
                onDelete = { e -> save(entries.filterNot { it.id == e.id }) }
            )
            else -> ReportPage(entries, padding)
        }
    }

    if (exportDialog) {
        AlertDialog(
            onDismissRequest = { exportDialog = false },
            title = { Text("Ekspor / Bagikan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportDialog = false; exportLauncher.launch("Keuangan_Toko.xls") }, modifier = Modifier.fillMaxWidth()) { Text("Ekspor Excel") }
                    Button(onClick = { exportDialog = false; pdfLauncher.launch("Laporan_Keuangan_Toko.pdf") }, modifier = Modifier.fillMaxWidth()) { Text("Ekspor PDF") }
                    Button(onClick = {
                        exportDialog = false
                        val text = buildWhatsAppReport(entries)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            setPackage("com.whatsapp")
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }, "Bagikan laporan"))
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Bagikan ke WhatsApp") }
                }
            },
            confirmButton = { TextButton(onClick = { exportDialog = false }) { Text("Batal") } }
        )
    }

    if (dialog) {
        EntryDialog(
            initial = editing,
            onDismiss = { dialog = false },
            onSave = { value ->
                val list = if (editing == null) {
                    entries + value.copy(id = System.currentTimeMillis())
                } else {
                    entries.map { if (it.id == editing!!.id) value.copy(id = it.id) else it }
                }
                save(list)
                dialog = false
            }
        )
    }
}

@Composable
private fun Home(
    entries: List<Entry>,
    padding: PaddingValues,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    val income = entries.filter { it.type == "Pemasukan" }.sumOf { it.amount }
    val expense = entries.filter { it.type == "Pengeluaran" }.sumOf { it.amount }
    val unpaid = entries.filter { it.type == "Nota Masuk" }.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Ringkasan Keuangan", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Semua angka berasal dari data yang Anda masukkan.", color = Color.Gray)
        }
        item { SummaryCard("Pemasukan", income, Color(0xFF159957)) }
        item { SummaryCard("Pengeluaran", expense, Color(0xFFD64545)) }
        item { SummaryCard("Selisih", income - expense, Color(0xFF1565C0)) }
        item { SummaryCard("Nota Belum Dibayar", unpaid, Color(0xFFE98A20)) }
        item {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Tambah Transaksi")
            }
        }
        item {
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Impor Excel / CSV / iREAP")
            }
        }
        item {
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Ekspor / WhatsApp")
            }
        }
        if (entries.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Belum ada data transaksi.",
                        modifier = Modifier.padding(24.dp),
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, amount: Long, color: Color) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Color.Gray)
            Text(formatMoney(amount), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun TransactionPage(
    entries: List<Entry>,
    padding: PaddingValues,
    onEdit: (Entry) -> Unit,
    onDelete: (Entry) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Transaksi", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Koreksi atau hapus data yang sudah tersimpan.", color = Color.Gray)
            Spacer(Modifier.height(10.dp))
        }
        items(entries.reversed(), key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(entry.type, fontWeight = FontWeight.Bold)
                    Text(entry.date, fontSize = 12.sp, color = Color.Gray)
                    Text(entry.note, color = Color.Gray)
                    Text(formatMoney(entry.amount), fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = { onEdit(entry) }) {
                            Icon(Icons.Default.Edit, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Koreksi")
                        }
                        TextButton(onClick = { onDelete(entry) }) {
                            Icon(Icons.Default.DeleteOutline, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Hapus")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportPage(entries: List<Entry>, padding: PaddingValues) {
    val income = entries.filter { it.type == "Pemasukan" }.sumOf { it.amount }
    val expense = entries.filter { it.type == "Pengeluaran" }.sumOf { it.amount }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(16.dp)
    ) {
        Text("Laporan", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        SummaryCard("Total Pemasukan", income, Color(0xFF159957))
        Spacer(Modifier.height(8.dp))
        SummaryCard("Total Pengeluaran", expense, Color(0xFFD64545))
        Spacer(Modifier.height(8.dp))
        SummaryCard("Selisih", income - expense, Color(0xFF1565C0))
        Spacer(Modifier.height(12.dp))
        Text("Jumlah transaksi: ${entries.size}", color = Color.Gray)
    }
}

@Composable
private fun EntryDialog(initial: Entry?, onDismiss: () -> Unit, onSave: (Entry) -> Unit) {
    var type by remember { mutableStateOf(initial?.type ?: "Pemasukan") }
    var date by remember {
        mutableStateOf(initial?.date ?: SimpleDateFormat("dd/MM/yyyy", Locale("id")).format(Date()))
    }
    var amount by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val types = listOf("Pemasukan", "Pengeluaran", "Nota Masuk", "Nota Terbayar")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tambah Transaksi" else "Koreksi Data") },
        text = {
            Column {
                Text("Jenis transaksi", fontSize = 12.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    types.take(2).forEach {
                        FilterChip(type == it, { type = it }, label = { Text(it) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    types.drop(2).forEach {
                        FilterChip(type == it, { type = it }, label = { Text(it) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(date, { date = it }, label = { Text("Tanggal") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    amount, { amount = it.filter(Char::isDigit) },
                    label = { Text("Nominal") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    note, { note = it },
                    label = { Text("Keterangan") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = amount.toLongOrNull()
                    if (n != null && n > 0 && note.isNotBlank()) {
                        onSave(Entry(initial?.id ?: 0, date, type, n, note.trim()))
                    }
                }
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

private fun importEntries(context: Context, uri: Uri): List<Entry> {
    val name = (uri.lastPathSegment ?: "").lowercase(Locale.ROOT)
    if (name.endsWith(".xlsx")) {
        return convertRowsToEntries(parseXlsx(context, uri))
    }
    val text = context.contentResolver.openInputStream(uri)?.use { input ->
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
    } ?: return emptyList()

    // CSV/TSV and our Excel-compatible HTML .xls export are supported without extra libraries.
    val cleaned = text.removePrefix("\uFEFF")
    val rows = if (cleaned.contains("<table", ignoreCase = true)) {
        parseHtmlTable(cleaned)
    } else {
        cleaned.lineSequence().filter { it.isNotBlank() }.map { parseCsvLine(it) }.toList()
    }
    if (rows.isEmpty()) return emptyList()

    return convertRowsToEntries(rows)
}

private fun parseXlsx(context: Context, uri: Uri): List<List<String>> {
    val entries = mutableMapOf<String, ByteArray>()
    context.contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(input).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && (e.name == "xl/sharedStrings.xml" || e.name == "xl/worksheets/sheet1.xml")) {
                    entries[e.name] = zip.readBytes()
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
    }
    val shared = entries["xl/sharedStrings.xml"]?.toString(Charsets.UTF_8)?.let { xml ->
        Regex("(?is)<si[^>]*>(.*?)</si>").findAll(xml).map { m ->
            Regex("(?is)<t[^>]*>(.*?)</t>").findAll(m.groupValues[1]).joinToString("") { it.groupValues[1] }
                .xmlUnescape()
        }.toList()
    } ?: emptyList()
    val sheet = entries["xl/worksheets/sheet1.xml"]?.toString(Charsets.UTF_8) ?: return emptyList()
    return Regex("(?is)<row[^>]*>(.*?)</row>").findAll(sheet).map { rowMatch ->
        Regex("(?is)<c\\b([^>]*)>(.*?)</c>").findAll(rowMatch.groupValues[1]).map { cell ->
            val attrs = cell.groupValues[1]
            val inner = cell.groupValues[2]
            val value = Regex("(?is)<v[^>]*>(.*?)</v>").find(inner)?.groupValues?.get(1)?.trim() ?: ""
            if (Regex("""\bt\s*=\s*"s""").containsMatchIn(attrs)) {
                shared.getOrNull(value.toIntOrNull() ?: -1) ?: value
            } else {
                Regex("(?is)<is[^>]*>.*?<t[^>]*>(.*?)</t>.*?</is>").find(inner)?.groupValues?.get(1)?.xmlUnescape() ?: value.xmlUnescape()
            }
        }.toList()
    }.filter { it.isNotEmpty() }.toList()
}

private fun String.xmlUnescape(): String =
    replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")

private fun convertRowsToEntries(rows: List<List<String>>): List<Entry> {
    if (rows.isEmpty()) return emptyList()
    val normalized = rows.map { row -> row.map { it.trim().lowercase(Locale.ROOT) } }
    val headerIndex = normalized.indexOfFirst { row ->
        row.any { it.contains("tanggal") || it == "date" } &&
        row.any { it.contains("nominal") || it.contains("amount") || it.contains("harga") || it.contains("total") || it.contains("jumlah") }
    }
    val start = if (headerIndex >= 0) headerIndex + 1 else 0
    val header = if (headerIndex >= 0) normalized[headerIndex] else emptyList()
    fun indexOfAny(vararg keys: String): Int = keys.firstNotNullOfOrNull { key -> header.indexOfFirst { it.contains(key) }.takeIf { it >= 0 } } ?: -1
    val dateIdx = indexOfAny("tanggal", "date")
    val typeIdx = indexOfAny("jenis", "type", "tipe", "kategori", "category")
    val amountIdx = indexOfAny("nominal", "amount", "jumlah", "total", "harga")
    val noteIdx = indexOfAny("keterangan", "catatan", "note", "deskripsi", "description", "nama")
    val result = mutableListOf<Entry>()
    rows.drop(start).forEachIndexed { idx, row ->
        if (row.isEmpty()) return@forEachIndexed
        val amount = if (amountIdx >= 0 && amountIdx < row.size) parseMoney(row[amountIdx]) else row.mapNotNull { parseMoneyOrNull(it) }.maxOrNull() ?: 0L
        if (amount <= 0L) return@forEachIndexed
        val date = if (dateIdx >= 0 && dateIdx < row.size && row[dateIdx].isNotBlank()) row[dateIdx] else SimpleDateFormat("dd/MM/yyyy", Locale("id")).format(Date())
        val typeRaw = if (typeIdx >= 0 && typeIdx < row.size) row[typeIdx].lowercase(Locale.ROOT) else "pemasukan"
        val type = when {
            typeRaw.contains("pengeluaran") || typeRaw.contains("expense") || typeRaw.contains("keluar") -> "Pengeluaran"
            typeRaw.contains("nota") && (typeRaw.contains("bayar") || typeRaw.contains("paid")) -> "Nota Terbayar"
            typeRaw.contains("nota") -> "Nota Masuk"
            else -> "Pemasukan"
        }
        val note = if (noteIdx >= 0 && noteIdx < row.size && row[noteIdx].isNotBlank()) row[noteIdx] else "Impor data"
        result += Entry(System.currentTimeMillis() + idx, date, type, amount, note)
    }
    return result
}

private fun parseCsvLine(line: String): List<String> {
    val separator = when {
        line.count { it == ';' } > line.count { it == ',' } -> ';'
        line.contains('\t') -> '\t'
        else -> ','
    }
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (c == '"') {
            if (quoted && i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ }
            else quoted = !quoted
        } else if (c == separator && !quoted) { out += sb.toString(); sb.clear() }
        else sb.append(c)
        i++
    }
    out += sb.toString()
    return out
}

private fun parseHtmlTable(html: String): List<List<String>> =
    Regex("(?is)<tr[^>]*>(.*?)</tr>").findAll(html).map { m ->
        Regex("(?is)<(?:td|th)[^>]*>(.*?)</(?:td|th)>").findAll(m.groupValues[1]).map { cell ->
            cell.groupValues[1].replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").trim()
        }.toList()
    }.filter { it.isNotEmpty() }.toList()

private fun parseMoneyOrNull(value: String): Long? = try { parseMoney(value).takeIf { it > 0 } } catch (_: Exception) { null }

private fun parseMoney(value: String): Long {
    val v = value.trim().replace("Rp", "", ignoreCase = true).replace("IDR", "", ignoreCase = true).replace(".", "").replace(",00", "").replace(",", "")
    return v.filter { it.isDigit() }.toLongOrNull() ?: 0L
}

private fun exportAsExcel(entries: List<Entry>): String {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    val body = buildString {
        entries.forEach { e ->
            append("<tr><td>${esc(e.date)}</td><td>${esc(e.type)}</td><td>${e.amount}</td><td>${esc(e.note)}</td></tr>\n")
        }
    }
    return """<html><head><meta charset="UTF-8"></head><body><table border="1"><tr><th>Tanggal</th><th>Jenis</th><th>Nominal</th><th>Keterangan</th></tr>$body</table></body></html>"""
}

private fun buildWhatsAppReport(entries: List<Entry>): String {
    val income = entries.filter { it.type == "Pemasukan" }.sumOf { it.amount }
    val expense = entries.filter { it.type == "Pengeluaran" }.sumOf { it.amount }
    val unpaid = entries.filter { it.type == "Nota Masuk" }.sumOf { it.amount }
    return buildString {
        append("LAPORAN KEUANGAN TOKO\n\n")
        append("Pemasukan: ${formatMoney(income)}\n")
        append("Pengeluaran: ${formatMoney(expense)}\n")
        append("Selisih: ${formatMoney(income - expense)}\n")
        append("Nota belum dibayar: ${formatMoney(unpaid)}\n")
        append("\nJumlah transaksi: ${entries.size}\n")
    }
}

private fun writePdf(context: Context, uri: Uri, entries: List<Entry>) {
    val pdf = android.graphics.pdf.PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    var pageNumber = 1
    var page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas = page.canvas
    val paint = android.graphics.Paint().apply { textSize = 14f; isAntiAlias = true }
    var y = 40f
    canvas.drawText("Laporan Keuangan Toko", 32f, y, paint)
    y += 28f
    entries.forEach { e ->
        if (y > 800f) {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = 40f
        }
        val line = "${e.date} | ${e.type} | ${formatMoney(e.amount)} | ${e.note}"
        canvas.drawText(line.take(85), 32f, y, paint)
        y += 20f
    }
    pdf.finishPage(page)
    context.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
    pdf.close()
}

private fun formatMoney(value: Long): String =
    NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        .format(value).replace(",00", "")

private fun loadEntries(context: Context): List<Entry> {
    val raw = context.getSharedPreferences("finance", Context.MODE_PRIVATE)
        .getString("entries", "[]") ?: "[]"
    val a = JSONArray(raw)
    return buildList {
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            add(Entry(o.getLong("id"), o.getString("date"), o.getString("type"),
                o.getLong("amount"), o.getString("note")))
        }
    }
}

private fun saveEntries(context: Context, entries: List<Entry>) {
    val a = JSONArray()
    entries.forEach {
        a.put(JSONObject().apply {
            put("id", it.id)
            put("date", it.date)
            put("type", it.type)
            put("amount", it.amount)
            put("note", it.note)
        })
    }
    context.getSharedPreferences("finance", Context.MODE_PRIVATE)
        .edit().putString("entries", a.toString()).apply()
}
