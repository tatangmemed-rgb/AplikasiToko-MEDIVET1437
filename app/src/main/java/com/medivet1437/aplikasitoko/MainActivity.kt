package com.medivet1437.aplikasitoko

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AppTheme { StoreApp() } } }
}

@Composable fun StoreApp() {
    var tab by remember { mutableIntStateOf(0) }
    var drawer by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> }
    val context = androidx.compose.ui.platform.LocalContext.current
    val share = {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "LAPORAN TOKO MEDIVET1437\n\nRingkasan laporan dapat dilihat pada Aplikasi Toko.") }
        context.startActivity(Intent.createChooser(i, "Bagikan laporan"))
    }
    ModalNavigationDrawer(drawerState = rememberDrawerState(if (drawer) DrawerValue.Open else DrawerValue.Closed), drawerContent = {
        ModalDrawerSheet { Spacer(Modifier.height(24.dp)); Text("Aplikasi Toko", Modifier.padding(24.dp), fontSize=24.sp, fontWeight=FontWeight.Bold); listOf("Dashboard","Transaksi","Penjualan","Nota Masuk","Nota Belum Dibayar","Pengeluaran","Kas & Bank","Hutang","Piutang","Stok & Barang","Laporan","Backup & Restore","Pengaturan").forEachIndexed { i, name -> NavigationDrawerItem(label={Text(name)}, selected=i==tab, onClick={tab=if(i==0)0 else 1; drawer=false}, modifier=Modifier.padding(horizontal=12.dp, vertical=2.dp)) } }
    }) {
        Scaffold(bottomBar={ BottomBar(tab){tab=it} }, floatingActionButton={ FloatingActionButton(onClick={}) { Icon(Icons.Default.Add,"Tambah") } }) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().background(Color(0xFFF7F9FC))) {
                TopBar { drawer=true }
                if (tab==0) Dashboard(onImport={picker.launch(arrayOf("text/csv","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel"))}, onShare=share)
                else Transactions()
            }
        }
    }
}

@Composable fun TopBar(open:()->Unit){ Row(Modifier.fillMaxWidth().padding(horizontal=18.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically){ IconButton(open){Icon(Icons.Default.Menu,"Menu", modifier=Modifier.size(30.dp))}; Image(painterResource(com.medivet1437.aplikasitoko.R.drawable.medivet1437_logo), null, Modifier.size(52.dp), contentScale=ContentScale.Fit); Spacer(Modifier.width(10.dp)); Column{Text("Aplikasi Toko", fontSize=20.sp, fontWeight=FontWeight.Bold); Text("MEDIVET1437", fontSize=12.sp, color=Color.Gray)}; Spacer(Modifier.weight(1f)); IconButton({}){Icon(Icons.Default.NotificationsNone,"Notifikasi")} }
}

@Composable fun Dashboard(onImport:()->Unit,onShare:()->Unit){ val scroll=rememberScrollState(); Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=16.dp)){ Text("Selamat pagi 👋", fontSize=25.sp,fontWeight=FontWeight.Bold); Text("Ringkasan kondisi toko hari ini", color=Color.Gray); Spacer(Modifier.height(16.dp));
 Card(shape=RoundedCornerShape(22.dp)){ Column(Modifier.padding(20.dp)){Text("TOTAL SALDO",fontSize=13.sp,color=Color.Gray); Text("Rp69.430.000",fontSize=30.sp,fontWeight=FontWeight.Bold); Text("Kas + Bank + QRIS",color=Color.Gray)} }
 Spacer(Modifier.height(16.dp)); Text("⚡ AKSI CEPAT",fontSize=18.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(10.dp)); Row(Modifier.horizontalScroll(scroll), horizontalArrangement=Arrangement.spacedBy(10.dp)){ ActionCard("📥","Import iREAP","Import Excel / CSV",onImport); ActionCard("📤","Export","Laporan / data"){}; ActionCard("🟢","WhatsApp","Bagikan laporan",onShare){} }
 Spacer(Modifier.height(18.dp)); Text("Ringkasan Hari Ini",fontSize=18.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(10.dp)); Row(Modifier.horizontalScroll(scroll), horizontalArrangement=Arrangement.spacedBy(10.dp)){ Metric("Penjualan","Rp3.250.000",Color(0xFF159957)); Metric("HPP","Rp2.420.000",Color(0xFFFF7A00)); Metric("Laba Kotor","Rp830.000",Color(0xFF673AB7)); Metric("Pengeluaran","Rp380.000",Color(0xFFE91E63)); Metric("Laba Bersih","Rp450.000",Color(0xFF159957)) }
 Spacer(Modifier.height(18.dp)); Card(shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(18.dp)){Row{Text("Penjualan 7 Hari",fontWeight=FontWeight.Bold,fontSize=18.sp);Spacer(Modifier.weight(1f));Text("Lihat →",color=Color(0xFF1565C0))}; Spacer(Modifier.height(14.dp)); Text("Rp3,25 jt",fontSize=28.sp,fontWeight=FontWeight.Bold); Text("▁▃▂▃▂▃▅",fontSize=32.sp,color=Color(0xFF159957)); Text("26 Agu     28 Agu     30 Agu     01 Sep",fontSize=11.sp,color=Color.Gray)} }
 Spacer(Modifier.height(18.dp)); Text("Informasi Penting",fontSize=18.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(10.dp)); Row(Modifier.horizontalScroll(scroll),horizontalArrangement=Arrangement.spacedBy(10.dp)){ Alert("🔴","18 Nota Belum Dibayar","Rp12.500.000"); Alert("🟠","5 Piutang Jatuh Tempo","Rp4.750.000"); Alert("🟡","7 Stok Menipis","Cek stok") }
 Spacer(Modifier.height(18.dp)); Text("Transaksi Terakhir",fontSize=18.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(8.dp)); repeat(4){ TransactionRow(if(it==2) "Pengeluaran" else "Penjualan", if(it==2) "- Rp150.000" else "Rp650.000", if(it==1) "QRIS" else "Tunai", it==2) }; Spacer(Modifier.height(90.dp)) } }

@Composable fun ActionCard(icon:String,title:String,sub:String,onClick:()->Unit={}){ Card(onClick=onClick, shape=RoundedCornerShape(20.dp), modifier=Modifier.width(190.dp)){Column(Modifier.padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(icon,fontSize=32.sp);Text(title,fontWeight=FontWeight.Bold,fontSize=16.sp);Text(sub,fontSize=12.sp,color=Color.Gray);Spacer(Modifier.height(10.dp));Button(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Text(if(title=="Import iREAP")"IMPORT SEKARANG" else if(title=="WhatsApp")"KIRIM SEKARANG" else "BUKA")}}} }
@Composable fun Metric(t:String,v:String,c:Color){Card(shape=RoundedCornerShape(18.dp),modifier=Modifier.width(155.dp)){Column(Modifier.padding(16.dp)){Text(t,color=Color.Gray);Spacer(Modifier.height(6.dp));Text(v,fontSize=18.sp,fontWeight=FontWeight.Bold,color=c);Text("hari ini",fontSize=12.sp,color=Color.Gray)}}}
@Composable fun Alert(i:String,t:String,v:String){Card(shape=RoundedCornerShape(18.dp),modifier=Modifier.width(210.dp)){Column(Modifier.padding(14.dp)){Text(i+" "+t,fontWeight=FontWeight.Bold);Text(v,color=Color.Gray);Text("Lihat detail →",color=Color(0xFF1565C0),fontSize=13.sp)}}}
@Composable fun TransactionRow(name:String,value:String,pay:String,negative:Boolean=false){Card(shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(negative)Icons.Default.RemoveCircleOutline else Icons.Default.ShoppingCart,null,tint=if(negative)Color.Red else Color(0xFF159957));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(name,fontWeight=FontWeight.Bold);Text("S20260901005",fontSize=12.sp,color=Color.Gray)};Column(horizontalAlignment=Alignment.End){Text(value,fontWeight=FontWeight.Bold,color=if(negative)Color.Red else Color(0xFF159957));Text(pay,fontSize=11.sp,color=Color.Gray)}}}}
@Composable fun Transactions(){Column(Modifier.fillMaxSize().padding(18.dp)){Text("Transaksi",fontSize=26.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(16.dp));Text("Periode tanggal",fontWeight=FontWeight.Bold);OutlinedButton(onClick={},modifier=Modifier.fillMaxWidth()){Text("01/09/2026 — 30/09/2026")};Spacer(Modifier.height(10.dp));OutlinedButton(onClick={},modifier=Modifier.fillMaxWidth()){Text("⚙ Column Settings")};Spacer(Modifier.height(10.dp));Text("Search transaksi…",color=Color.Gray);Spacer(Modifier.height(20.dp));Text("CSV   Excel   PDF   🟢 WhatsApp",fontWeight=FontWeight.Bold,color=Color(0xFF1565C0))}}
@Composable fun BottomBar(tab:Int,onTab:(Int)->Unit){NavigationBar{NavigationBarItem(tab==0,{onTab(0)},{Icon(Icons.Default.Home,null);Text("Dashboard")});NavigationBarItem(tab==1,{onTab(1)},{Icon(Icons.Default.SwapVert,null);Text("Transaksi")});Spacer(Modifier.width(52.dp));NavigationBarItem(false,{onTab(2)},{Icon(Icons.Default.Description,null);Text("Laporan")});NavigationBarItem(false,{onTab(3)},{Icon(Icons.Default.Person,null);Text("Akun")})}}
