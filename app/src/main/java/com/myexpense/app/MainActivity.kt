package com.myexpense.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "transactions")
data class MoneyTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val type: String,
    val category: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface MoneyDao {
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun all(): kotlinx.coroutines.flow.Flow<List<MoneyTransaction>>
    @Insert suspend fun insert(item: MoneyTransaction)
    @Query("SELECT COALESCE(SUM(CASE WHEN type='INCOME' THEN amount ELSE -amount END),0) FROM transactions")
    fun balance(): kotlinx.coroutines.flow.Flow<Double>
    @Query("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='INCOME'")
    fun income(): kotlinx.coroutines.flow.Flow<Double>
    @Query("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='EXPENSE'")
    fun expense(): kotlinx.coroutines.flow.Flow<Double>
}

@Database(entities = [MoneyTransaction::class], version = 1, exportSchema = true)
abstract class MoneyDb : RoomDatabase() {
    abstract fun dao(): MoneyDao
    companion object {
        @Volatile private var instance: MoneyDb? = null
        fun get(context: android.content.Context): MoneyDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MoneyDb::class.java, "myexpense.db")
                .build().also { instance = it }
        }
    }
}

class MoneyModel(private val dao: MoneyDao) {
    val rows = dao.all().stateIn(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main), SharingStarted.WhileSubscribed(5000), emptyList())
    val balance = dao.balance().stateIn(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main), SharingStarted.WhileSubscribed(5000), 0.0)
    val income = dao.income().stateIn(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main), SharingStarted.WhileSubscribed(5000), 0.0)
    val expense = dao.expense().stateIn(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main), SharingStarted.WhileSubscribed(5000), 0.0)
    suspend fun add(title: String, amount: Double, type: String, category: String) {
        if (title.isNotBlank() && amount > 0 && amount.isFinite()) dao.insert(MoneyTransaction(title=title.trim(), amount=amount, type=type, category=category))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ExpenseHome(MoneyDb.get(applicationContext).dao()) }
    }
}

private val Green = Color(0xFF1D7554)
private val Mint = Color(0xFFB9F5D5)

@Composable
fun ExpenseHome(dao: MoneyDao) {
    val model = remember(dao) { MoneyModel(dao) }
    val rows by model.rows.collectAsState()
    val balance by model.balance.collectAsState()
    val income by model.income.collectAsState()
    val expense by model.expense.collectAsState()
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Your Name") }
    var tab by remember { mutableIntStateOf(0) }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary=Mint) else lightColorScheme(primary=Green)) {
        Scaffold(
            floatingActionButton = { FloatingActionButton(onClick={showAdd=true}, containerColor=Green, contentColor=Color.White) { Icon(Icons.Default.Add, "Add") } },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab==0,{tab=0},{Icon(Icons.Default.Home,null)},label={Text("Home")})
                    NavigationBarItem(tab==1,{tab=1},{Icon(Icons.Default.PieChart,null)},label={Text("Reports")})
                    NavigationBarItem(tab==2,{tab=2},{Icon(Icons.Default.CalendarMonth,null)},label={Text("History")})
                    NavigationBarItem(tab==3,{tab=3},{Icon(Icons.Default.MoreHoriz,null)},label={Text("More")})
                }
            }
        ) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding=PaddingValues(20.dp), verticalArrangement=Arrangement.spacedBy(17.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                        Column {
                            Text("Good day,", color=MaterialTheme.colorScheme.onBackground.copy(alpha=.6f))
                            Text(name, fontSize=24.sp, fontWeight=FontWeight.Bold)
                        }
                        Text(if(dark) "☀" else "☾", Modifier.clip(CircleShape).clickable { dark=!dark }.background(MaterialTheme.colorScheme.surface).padding(12.dp), fontSize=20.sp)
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().height(188.dp).clip(RoundedCornerShape(27.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF103C2B),Color(0xFF23815C),Color(0xFF75C99A))))
                        .padding(22.dp), verticalArrangement=Arrangement.SpaceBetween) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("TOTAL BALANCE", color=Color.White.copy(alpha=.72f), fontSize=11.sp, letterSpacing=1.4.sp)
                            Text("◈", color=Mint, fontSize=25.sp)
                        }
                        Text("৳ "+fmt(balance), color=Color.White, fontSize=32.sp, fontWeight=FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.Bottom) {
                            Column {
                                Text("CARD HOLDER", color=Color.White.copy(alpha=.65f), fontSize=9.sp, letterSpacing=1.sp)
                                Text(name, color=Color.White, fontWeight=FontWeight.SemiBold)
                            }
                            Text("••••  ••••  ••••", color=Color.White.copy(alpha=.85f))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Summary("Income",income,true,Modifier.weight(1f))
                        Summary("Expense",expense,false,Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                        Text(if(tab==0) "Recent transactions" else listOf("Recent transactions","Reports overview","Transaction history","Settings")[tab], fontSize=18.sp, fontWeight=FontWeight.Bold)
                        Text("See all", color=Green, modifier=Modifier.clickable { tab=2 })
                    }
                }
                if(rows.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(25.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("🌱", fontSize=32.sp)
                        Text("Your money journey starts here", fontWeight=FontWeight.SemiBold)
                        Text("Add your first income or expense.", color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f), fontSize=13.sp)
                    }
                } else items(rows, key={it.id}) { row -> TransactionItem(row) }
            }
        }
        if(showAdd) AddDialog({showAdd=false}) { title,amount,type,category ->
            scope.launch { model.add(title,amount,type,category) }
            showAdd=false
        }
    }
}

@Composable
fun Summary(label:String, amount:Double, positive:Boolean, modifier:Modifier=Modifier) {
    Column(modifier.clip(RoundedCornerShape(21.dp)).background(MaterialTheme.colorScheme.surface).padding(15.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(if(positive) Color(0xFFE0F6E9) else Color(0xFFFFE9E5)), contentAlignment=Alignment.Center) {
                Icon(if(positive) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,null,tint=if(positive) Green else Color(0xFFC45A49),modifier=Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp)); Text(label,fontSize=13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("৳ "+fmt(amount),fontSize=18.sp,fontWeight=FontWeight.Bold)
    }
}

@Composable
fun TransactionItem(row:MoneyTransaction) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(if(row.type=="INCOME") Color(0xFFE0F6E9) else Color(0xFFFFE9E5)),contentAlignment=Alignment.Center) {
            Icon(if(row.type=="INCOME") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,null,tint=if(row.type=="INCOME") Green else Color(0xFFC45A49))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title,fontWeight=FontWeight.SemiBold,maxLines=1)
            Text(row.category+" • "+SimpleDateFormat("dd MMM",Locale.getDefault()).format(Date(row.createdAt)),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f))
        }
        Text((if(row.type=="INCOME") "+" else "−")+"৳"+fmt(row.amount),fontWeight=FontWeight.Bold)
    }
}

@Composable
fun AddDialog(onDismiss:()->Unit,onSave:(String,Double,String,String)->Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var category by remember { mutableStateOf("General") }
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("New transaction",fontWeight=FontWeight.Bold)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(type=="EXPENSE",{type="EXPENSE"},label={Text("Expense")})
                    FilterChip(type=="INCOME",{type="INCOME"},label={Text("Income")})
                }
                OutlinedTextField(title,{title=it},label={Text("Title")},singleLine=true)
                OutlinedTextField(amount,{amount=it.filter { c -> c.isDigit() || c=='.' }},label={Text("Amount (৳)")},singleLine=true)
                OutlinedTextField(category,{category=it},label={Text("Category")},singleLine=true)
            }
        },
        confirmButton={TextButton(onClick={ val v=amount.toDoubleOrNull(); if(v!=null && v>0 && title.isNotBlank()) onSave(title,v,type,category.ifBlank{"General"}) }) {Text("Save")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}}
    )
}

fun fmt(value:Double):String = "%,.2f".format(Locale.US,value)
