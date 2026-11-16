package com.myexpense.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Entity(tableName="transactions")
data class MoneyTransaction(@PrimaryKey(autoGenerate=true) val id:Long=0,val title:String,val amount:Double,val type:String,val category:String,val note:String="",val createdAt:Long=System.currentTimeMillis())
@Entity(tableName="app_settings")
data class AppSetting(@PrimaryKey val settingKey:String,val value:String)

@Dao interface MoneyDao {
 @Query("SELECT * FROM transactions ORDER BY createdAt DESC") fun all():Flow<List<MoneyTransaction>>
 @Insert suspend fun insert(item:MoneyTransaction)
 @Update suspend fun update(item:MoneyTransaction)
 @Delete suspend fun delete(item:MoneyTransaction)
 @Query("SELECT COALESCE(SUM(CASE WHEN type='INCOME' THEN amount ELSE -amount END),0) FROM transactions") fun balance():Flow<Double>
 @Query("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='INCOME'") fun income():Flow<Double>
 @Query("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='EXPENSE'") fun expense():Flow<Double>
 @Query("SELECT * FROM app_settings WHERE settingKey=:key LIMIT 1") fun setting(key:String):Flow<AppSetting?>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun setSetting(item:AppSetting)
}
@Database(entities=[MoneyTransaction::class,AppSetting::class],version=1,exportSchema=true)
abstract class MoneyDb:RoomDatabase(){
 abstract fun dao():MoneyDao
 companion object{
  @Volatile private var instance:MoneyDb?=null
  fun get(c:android.content.Context):MoneyDb=instance?: synchronized(this){instance?:Room.databaseBuilder(c.applicationContext,MoneyDb::class.java,"myexpense.db").build().also{instance=it}}
 }
}
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{ExpenseApp(MoneyDb.get(applicationContext).dao())}}
}
private val Green=Color(0xFF176B4B)
private val Coral=Color(0xFFE87869)
private val categories=listOf("Food","Transport","Shopping","Bills","Mobile","Health","Education","Entertainment","Salary","Business","Gift","Other")
private fun taka(v:Double)="৳ "+ "%,.2f".format(Locale.US,v)
private fun dateLabel(v:Long)=SimpleDateFormat("dd MMM yyyy",Locale.getDefault()).format(Date(v))

@Composable fun ExpenseApp(dao:MoneyDao){
 val rows by dao.all().collectAsState(initial=emptyList())
 val balance by dao.balance().collectAsState(initial=0.0)
 val income by dao.income().collectAsState(initial=0.0)
 val expense by dao.expense().collectAsState(initial=0.0)
 val budgetItem by dao.setting("daily_budget").collectAsState(initial=null)
 val nameItem by dao.setting("user_name").collectAsState(initial=null)
 val themeItem by dao.setting("theme").collectAsState(initial=null)
 val darkItem by dao.setting("dark").collectAsState(initial=null)
 val scope= rememberCoroutineScope()
 var page by remember{mutableIntStateOf(0)}
 var showEntry by remember{mutableStateOf(false)}
 var entryType by remember{mutableStateOf("EXPENSE")}
 var editing by remember{mutableStateOf<MoneyTransaction?>(null)}
 var search by remember{mutableStateOf("")}
 var filter by remember{mutableStateOf("All")}
 var monthOffset by remember{mutableIntStateOf(0)}
 var dialog by remember{mutableStateOf("")}
 val month=remember(monthOffset){Calendar.getInstance().apply{add(Calendar.MONTH,monthOffset)}}
 val budget=budgetItem?.value?.toDoubleOrNull()?:0.0
 val dark=darkItem?.value=="true"
 val accent=when(themeItem?.value){"Ocean"->Color(0xFF168B9B);"Violet"->Color(0xFF7355B5);"Rose"->Color(0xFFBC5578);else->Green}
 MaterialTheme(colorScheme=if(dark)darkColorScheme(primary=Color(0xFFBDF3D6))else lightColorScheme(primary=accent,background=Color(0xFFF3F8F5),surface=Color.White)){
  Scaffold(
   floatingActionButton={if(page==0||page==1)FloatingActionButton(onClick={entryType="EXPENSE";showEntry=true},containerColor=accent,contentColor=Color.White){Icon(Icons.Default.Add,null)}},
   bottomBar={NavigationBar{val labels=listOf("Home","History","Reports","Calendar","More");val icons=listOf(Icons.Default.Home,Icons.Default.ReceiptLong,Icons.Default.PieChart,Icons.Default.CalendarMonth,Icons.Default.Menu);labels.forEachIndexed{i,label->NavigationBarItem(selected=page==i,onClick={page=i},icon={Icon(icons[i],null)},label={Text(label)})}}}
  ){padding->
   when(page){
    0->HomePage(rows,balance,income,expense,budget,nameItem?.value?:"Your Name",accent,{type->entryType=type;showEntry=true},{page=2},{page=1})
    1->HistoryPage(rows,search,{search=it},filter,{filter=it},{type->entryType=type;showEntry=true},{editing=it},{item->scope.launch{dao.delete(item)}})
    2->ReportsPage(rows,month,{monthOffset+=it})
    3->CalendarPage(rows,month,{monthOffset+=it})
    else->MorePage(nameItem?.value?:"Your Name",budget,dark,themeItem?.value?:"Forest",{dialog="profile"},{dialog="budget"},{scope.launch{dao.setSetting(AppSetting("dark",(!dark).toString()))}},{value->scope.launch{dao.setSetting(AppSetting("theme",value))}})
   }
  }
 }
 if(showEntry||editing!=null){
  val old=editing
  EntryDialog(old?.type?:entryType,old,{showEntry=false;editing=null}){title,amount,type,category,note->
   scope.launch{if(old==null)dao.insert(MoneyTransaction(title=title,amount=amount,type=type,category=category,note=note))else dao.update(old.copy(title=title,amount=amount,type=type,category=category,note=note))}
   showEntry=false;editing=null
  }
 }
 if(dialog=="budget")TextValueDialog("Daily budget",budget.toString(),"Amount in Tk",{v->v.toDoubleOrNull()?.takeIf{it>=0}?.let{scope.launch{dao.setSetting(AppSetting("daily_budget",it.toString()))}};dialog=""},{dialog=""})
 if(dialog=="profile")TextValueDialog("Your name",nameItem?.value.orEmpty(),"Display name",{v->scope.launch{dao.setSetting(AppSetting("user_name",v.trim()))};dialog=""},{dialog=""})
}

@Composable private fun HomePage(rows:List<MoneyTransaction>,balance:Double,income:Double,expense:Double,budget:Double,name:String,accent:Color,add:(String)->Unit,reports:()->Unit,history:()->Unit){
 val startMonth=Calendar.getInstance().apply{set(Calendar.DAY_OF_MONTH,1);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
 val startToday=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
 val monthSpent=rows.filter{it.type=="EXPENSE"&&it.createdAt>=startMonth}.sumOf{it.amount}
 val todaySpent=rows.filter{it.type=="EXPENSE"&&it.createdAt>=startToday}.sumOf{it.amount}
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(17.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
  item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("Welcome back",color=MaterialTheme.colorScheme.onSurfaceVariant);Text(name,fontSize=25.sp,fontWeight=FontWeight.Bold)};Text("MYEXPENSE",color=accent,fontWeight=FontWeight.Black)}}
  item{Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF103C2B),accent,Color(0xFF72C99A)))).padding(22.dp)){Text("TOTAL BALANCE",color=Color.White.copy(.7f),fontSize=11.sp);Text(taka(balance),color=Color.White,fontSize=31.sp,fontWeight=FontWeight.ExtraBold);Spacer(Modifier.height(18.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("INCOME",color=Color.White.copy(.7f));Text(taka(income),color=Color.White,fontWeight=FontWeight.Bold)};Column{Text("EXPENSE",color=Color.White.copy(.7f));Text(taka(expense),color=Color.White,fontWeight=FontWeight.Bold)}}}}
  item{Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){MetricCard("This month",monthSpent,Modifier.weight(1f));MetricCard("Today",todaySpent,Modifier.weight(1f))}}
  item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={add("EXPENSE")},Modifier.weight(1f)){Icon(Icons.Default.Add,null);Text("Expense")};OutlinedButton(onClick={add("INCOME")},Modifier.weight(1f)){Icon(Icons.Default.Add,null);Text("Add money")}}}
  item{Card(shape=RoundedCornerShape(19.dp)){Column(Modifier.padding(15.dp)){Text("Daily budget",fontWeight=FontWeight.Bold);Text("Spent "+taka(todaySpent)+" / "+taka(budget));LinearProgressIndicator(progress={if(budget>0)(todaySpent/budget).toFloat().coerceIn(0f,1f)else 0f},Modifier.fillMaxWidth().padding(vertical=8.dp));Text(if(budget>0)"Remaining "+taka((budget-todaySpent).coerceAtLeast(0.0))else"Set a budget in More",fontSize=12.sp)}}}
  item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Recent activity",fontSize=18.sp,fontWeight=FontWeight.Bold);TextButton(onClick=history){Text("See all")}}}
  if(rows.isEmpty())item{EmptyState("🌱","Your money journey starts here","Add your first income or expense.")}
  else items(rows.take(6),key={it.id}){TransactionCard(it)}
  item{TextButton(onClick=reports){Text("View financial reports →")}}
 }
}
@Composable private fun HistoryPage(rows:List<MoneyTransaction>,search:String,onSearch:(String)->Unit,filter:String,onFilter:(String)->Unit,add:(String)->Unit,edit:(MoneyTransaction)->Unit,delete:(MoneyTransaction)->Unit){
 val shown=rows.filter{(filter=="All"||it.type==filter)&&(search.isBlank()||it.title.contains(search,true)||it.category.contains(search,true)||it.note.contains(search,true))}
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
  item{Text("Transaction history",fontSize=25.sp,fontWeight=FontWeight.Bold);Text(rows.size.toString()+" records",color=MaterialTheme.colorScheme.onSurfaceVariant)}
  item{OutlinedTextField(search,onSearch,Modifier.fillMaxWidth(),label={Text("Search transactions")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true)}
  item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("All","EXPENSE","INCOME").forEach{FilterChip(filter==it,{onFilter(it)},label={Text(if(it=="EXPENSE")"Expenses"else if(it=="INCOME")"Income"else it)})}}}
  item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={add("EXPENSE")},Modifier.weight(1f)){Text("+ Expense")};OutlinedButton(onClick={add("INCOME")},Modifier.weight(1f)){Text("+ Income")}}}
  if(shown.isEmpty())item{EmptyState("🧾","No matching records","Try another search.")}
  else items(shown,key={it.id}){row->Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){TransactionCard(row)};Column{IconButton(onClick={edit(row)}){Icon(Icons.Default.Edit,null)};IconButton(onClick={delete(row)}){Icon(Icons.Default.DeleteOutline,null,tint=Coral)}}}}
 }
}
@Composable private fun ReportsPage(rows:List<MoneyTransaction>,month:Calendar,change:(Int)->Unit){
 val list=rows.filter{val c=Calendar.getInstance().apply{timeInMillis=it.createdAt};c.get(Calendar.YEAR)==month.get(Calendar.YEAR)&&c.get(Calendar.MONTH)==month.get(Calendar.MONTH)}
 val total=list.filter{it.type=="EXPENSE"}.sumOf{it.amount}
 val groups=list.filter{it.type=="EXPENSE"}.groupBy{it.category}.mapValues{entry->entry.value.sumOf{it.amount}}.toList().sortedByDescending{it.second}
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(17.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{Text("Financial reports",fontSize=25.sp,fontWeight=FontWeight.Bold)}
  item{MonthSwitcher(month,change)}
  item{Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){MetricCard("Income",list.filter{it.type=="INCOME"}.sumOf{it.amount},Modifier.weight(1f));MetricCard("Expense",total,Modifier.weight(1f))}}
  item{Text("Spending by category",fontWeight=FontWeight.Bold,fontSize=18.sp)}
  if(groups.isEmpty())item{EmptyState("📊","No spending this month","Category report will appear here.")}
  else items(groups){entry->Card(shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(14.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(entry.first,fontWeight=FontWeight.SemiBold);Text(taka(entry.second),fontWeight=FontWeight.Bold)};LinearProgressIndicator(progress={if(total>0)(entry.second/total).toFloat()else 0f},Modifier.fillMaxWidth().padding(top=8.dp))}}}
 }
}
@Composable private fun CalendarPage(rows:List<MoneyTransaction>,month:Calendar,change:(Int)->Unit){
 val cal=month.clone() as Calendar;cal.set(Calendar.DAY_OF_MONTH,1);val offset=(cal.get(Calendar.DAY_OF_WEEK)+5)%7;val count=cal.getActualMaximum(Calendar.DAY_OF_MONTH)
 var selected by remember(month.timeInMillis){mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH))}
 val daily=rows.filter{val c=Calendar.getInstance().apply{timeInMillis=it.createdAt};c.get(Calendar.YEAR)==month.get(Calendar.YEAR)&&c.get(Calendar.MONTH)==month.get(Calendar.MONTH)&&c.get(Calendar.DAY_OF_MONTH)==selected}
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{Text("Calendar",fontSize=25.sp,fontWeight=FontWeight.Bold)}
  item{MonthSwitcher(month,change)}
  item{Card(shape=RoundedCornerShape(19.dp)){Column(Modifier.padding(10.dp)){Row{listOf("M","T","W","T","F","S","S").forEach{Text(it,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontWeight=FontWeight.Bold)}};for(week in 0..((offset+count-1)/7)){Row{for(col in 0..6){val d=week*7+col-offset+1;Box(Modifier.weight(1f).height(43.dp).padding(2.dp).clip(RoundedCornerShape(9.dp)).background(if(d==selected)MaterialTheme.colorScheme.primaryContainer else Color.Transparent).clickable(enabled=d in 1..count){selected=d},contentAlignment=Alignment.Center){if(d in 1..count)Text(d.toString(),fontWeight=if(d==selected)FontWeight.Bold else FontWeight.Normal)}}}}}}}
  item{Text("Activity on day "+selected,fontWeight=FontWeight.Bold,fontSize=18.sp)}
  if(daily.isEmpty())item{EmptyState("📅","No activity","Choose another date.")}
  else items(daily,key={it.id}){TransactionCard(it)}
 }
}
@Composable private fun MorePage(name:String,budget:Double,dark:Boolean,theme:String,profile:()->Unit,budgetClick:()->Unit,darkClick:()->Unit,themeClick:(String)->Unit){
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(17.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Text("More",fontSize=25.sp,fontWeight=FontWeight.Bold);Text("Personalize your finance tracker",color=MaterialTheme.colorScheme.onSurfaceVariant)}
  item{SettingRow(Icons.Default.Person,"Profile",name,profile)}
  item{SettingRow(Icons.Default.AccountBalanceWallet,"Daily budget",if(budget>0)taka(budget)else"Not set",budgetClick)}
  item{SettingRow(Icons.Default.DarkMode,"Dark mode",if(dark)"On"else"Off",darkClick)}
  item{Text("Theme Store",fontWeight=FontWeight.Bold,fontSize=18.sp)}
  item{Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("Forest","Ocean","Violet","Rose").forEach{FilterChip(theme==it,{themeClick(it)},label={Text(it)})}}}
  item{Text("Offline-first • Your data stays on this device",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
 }
}
@Composable private fun EntryDialog(initial:String,item:MoneyTransaction?,dismiss:()->Unit,save:(String,Double,String,String,String)->Unit){
 var title by remember(item?.id){mutableStateOf(item?.title?:"")};var amount by remember(item?.id){mutableStateOf(item?.amount?.toString()?:"")};var type by remember(item?.id){mutableStateOf(item?.type?:initial)};var category by remember(item?.id){mutableStateOf(item?.category?:"Food")};var note by remember(item?.id){mutableStateOf(item?.note?:"")}
 AlertDialog(onDismissRequest=dismiss,title={Text(if(item==null)"Add transaction"else"Edit transaction")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Row{FilterChip(type=="EXPENSE",{type="EXPENSE"},label={Text("Expense")});Spacer(Modifier.width(6.dp));FilterChip(type=="INCOME",{type="INCOME"},label={Text("Money in")})};OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("Title")});OutlinedTextField(amount,{amount=it.filter{c->c.isDigit()||c=='.'}},Modifier.fillMaxWidth(),label={Text("Amount Tk")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));Text("Category");Row{categories.take(6).forEach{FilterChip(category==it,{category=it},label={Text(it)})}};OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text("Note (optional)")})}},confirmButton={TextButton(onClick={val v=amount.toDoubleOrNull();if(title.isNotBlank()&&v!=null&&v>0&&v.isFinite())save(title.trim(),v,type,category,note.trim())}){Text("Save")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
@Composable private fun TextValueDialog(title:String,initial:String,hint:String,save:(String)->Unit,dismiss:()->Unit){
 var value by remember(title){mutableStateOf(initial)}
 AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={OutlinedTextField(value,{value=it},Modifier.fillMaxWidth(),label={Text(hint)},singleLine=true)},confirmButton={TextButton(onClick={save(value)}){Text("Save")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
@Composable private fun MetricCard(label:String,value:Double,modifier:Modifier){Card(modifier,shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp)){Text(label,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(taka(value),fontWeight=FontWeight.Bold,fontSize=17.sp)}}}
@Composable private fun TransactionCard(row:MoneyTransaction){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(row.type=="INCOME")Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,null,tint=if(row.type=="INCOME")Green else Coral);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(row.title,fontWeight=FontWeight.SemiBold);Text(row.category+" • "+dateLabel(row.createdAt),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);if(row.note.isNotBlank())Text(row.note,fontSize=11.sp)};Text((if(row.type=="INCOME")"+" else "−")+taka(row.amount),fontWeight=FontWeight.Bold,color=if(row.type=="INCOME")Green else Coral,fontSize=13.sp)}}}
@Composable private fun SettingRow(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,subtitle:String,click:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=click),shape=RoundedCornerShape(16.dp)){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold);Text(subtitle,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Default.ChevronRight,null)}}}
@Composable private fun EmptyState(icon:String,title:String,subtitle:String){Card(shape=RoundedCornerShape(19.dp)){Column(Modifier.fillMaxWidth().padding(23.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(icon,fontSize=29.sp);Text(title,fontWeight=FontWeight.Bold);Text(subtitle,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
@Composable private fun MonthSwitcher(month:Calendar,change:(Int)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){IconButton(onClick={change(-1)}){Icon(Icons.Default.ChevronLeft,null)};Text(SimpleDateFormat("MMMM yyyy",Locale.getDefault()).format(month.time),fontWeight=FontWeight.Bold);IconButton(onClick={change(1)}){Icon(Icons.Default.ChevronRight,null)}}}
