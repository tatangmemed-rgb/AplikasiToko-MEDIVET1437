package com.medivet1437.aplikasitoko

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medivet1437.aplikasitoko.ui.theme.AppTheme
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
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

@Composable
private fun StoreApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var entries by remember { mutableStateOf(loadEntries(context)) }
    var page by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }

    fun save(list: List<Entry>) {
        entries = list
        saveEntries(context, list)
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
            0 -> Home(entries, padding) { editing = null; dialog = true }
            1 -> TransactionPage(entries, padding,
                onEdit = { editing = it; dialog = true },
                onDelete = { e -> save(entries.filterNot { it.id == e.id }) }
            )
            else -> ReportPage(entries, padding)
        }
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
private fun Home(entries: List<Entry>, padding: PaddingValues, onAdd: () -> Unit) {
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
                onClick = {
                    // Impor Excel/CSV akan memakai modul impor khusus setelah fondasi transaksi stabil.
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Impor Excel / CSV / iREAP")
            }
        }
        item {
            OutlinedButton(
                onClick = { },
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
