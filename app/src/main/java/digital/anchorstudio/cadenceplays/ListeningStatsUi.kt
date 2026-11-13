package digital.anchorstudio.soundprint

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class StatsRange(val label:String, val days:Int?) {
    WEEK("7 days",7), MONTH("30 days",30), ALL("All time",null)
}
private data class StatsDay(val key:String,val label:String,val listenedMs:Long)
private data class TrackRank(val track:MusicTrack,val listenedMs:Long,val plays:Int)
private data class ArtistRank(val name:String,val listenedMs:Long,val plays:Int)
private data class StatsSnapshot(
    val totalMs:Long,val plays:Int,val streak:Int,val days:List<StatsDay>,
    val topTracks:List<TrackRank>,val topArtists:List<ArtistRank>
)

@Composable
fun ListeningStatsScreen(libraryManager:MusicLibraryManager,onBack:()->Unit) {
    var range by remember { mutableStateOf(StatsRange.WEEK) }
    var refresh by remember { mutableIntStateOf(0) }
    var resetDialog by remember { mutableStateOf(false) }
    val tracks=remember(refresh) { libraryManager.allSongs() }
    val events=remember(refresh) { libraryManager.listeningEvents(10000) }
    val snapshot=remember(range,tracks,events,refresh) { buildStatsSnapshot(range,tracks,events) }
    LazyColumn(
        modifier=Modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=18.dp,end=18.dp,top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+14.dp,bottom=28.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR LISTENING",fontSize=10.sp,letterSpacing=1.8.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
                    Text("Music statistics",fontSize=27.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold,letterSpacing=(-0.8).sp,color=MaterialTheme.colorScheme.onBackground)
                    Text("Your music, your story.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).border(1.dp,MaterialTheme.colorScheme.outline,CircleShape).clickable { resetDialog=true },contentAlignment=Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Default.Refresh,contentDescription="Reset listening statistics",tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                StatsRange.values().forEach { item ->
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if(range==item) MaterialTheme.colorScheme.primary.copy(alpha=.14f) else Color.Transparent).clickable { range=item }.padding(vertical=11.dp),contentAlignment=Alignment.Center) {
                        Text(item.label,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=if(range==item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primary).padding(20.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.Headphones,null,tint=MaterialTheme.colorScheme.onPrimary.copy(alpha=.85f))
                    Spacer(Modifier.width(8.dp))
                    Text("LISTENING TIME",fontSize=11.sp,letterSpacing=1.4.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.82f))
                }
                Text(formatListeningTime(snapshot.totalMs),modifier=Modifier.padding(top=10.dp),fontSize=34.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1.1).sp,color=MaterialTheme.colorScheme.onPrimary)
                Text(if(range==StatsRange.ALL) "Across your listening history" else "Across the last ${range.days} days",modifier=Modifier.padding(top=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.78f))
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                StatMetricCard("Plays counted",snapshot.plays.toString(),Icons.Default.MusicNote,Modifier.weight(1f))
                StatMetricCard("Current streak","${snapshot.streak} ${if(snapshot.streak==1) "day" else "days"}",Icons.Default.Waves,Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            StatMetricCard("Songs in library",tracks.size.toString(),Icons.Default.Schedule,Modifier.fillMaxWidth())
        }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.BarChart,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Listening activity",fontSize=16.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    if(snapshot.days.any { it.listenedMs>0L }) {
                        val max=snapshot.days.maxOf { it.listenedMs }.coerceAtLeast(1L)
                        Row(Modifier.fillMaxWidth().height(142.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.Bottom) {
                            snapshot.days.forEach { day ->
                                Column(Modifier.weight(1f).fillMaxHeight(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
                                    Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.BottomCenter) {
                                        Box(Modifier.fillMaxWidth(.72f).height((if(day.listenedMs==0L) 2f else (day.listenedMs.toFloat()/max*100f).coerceAtLeast(5f)).dp).clip(RoundedCornerShape(topStart=5.dp,topEnd=5.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha=if(day.listenedMs==0L) .12f else .88f)))
                                    }
                                    Text(day.label,fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,softWrap=false)
                                }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            Text("Your listening story starts here",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
                            Text("Play music and your activity will appear here.",modifier=Modifier.padding(top=5.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            StatsRankSection("Top songs","Ranked by listening time",snapshot.topTracks.mapIndexed { index,row -> Triple(index+1,row.track.title,row.track.artist to "${formatListeningTime(row.listenedMs)} · ${row.plays} plays") })
        }
        item {
            StatsRankSection("Top artists","Ranked by listening time",snapshot.topArtists.mapIndexed { index,row -> Triple(index+1,row.name,"${row.plays} plays" to formatListeningTime(row.listenedMs)) })
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Text("Your listening stays yours",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
                Text("Statistics are calculated from playback recorded on this device. No account or cloud connection is required.",modifier=Modifier.padding(top=5.dp),fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if(resetDialog) AlertDialog(
        onDismissRequest={resetDialog=false},
        title={Text("Reset listening statistics?")},
        text={Text("This clears listening time, play counts, activity and streaks on this device. Your music files, library, playlists and favourites will remain untouched.")},
        confirmButton={TextButton(onClick={ runCatching { libraryManager.clearListeningHistory() }.onSuccess { refresh++ }; resetDialog=false }) { Text("Reset statistics",color=MaterialTheme.colorScheme.error) }},
        dismissButton={TextButton(onClick={resetDialog=false}) { Text("Cancel") }}
    )
}

@Composable
private fun StatMetricCard(label:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier=Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(15.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha=.11f)),contentAlignment=Alignment.Center) {
            androidx.compose.material3.Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))
        }
        Text(value,modifier=Modifier.padding(top=12.dp),fontSize=21.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface,maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis)
        Text(label,modifier=Modifier.padding(top=3.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatsRankSection(title:String,subtitle:String,rows:List<Triple<Int,String,Pair<String,String>>>) {
    Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Column {
            Text(title,fontSize=16.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
            Text(subtitle,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),contentAlignment=Alignment.Center) {
                Text("Your ${title.lowercase()} will appear here after you listen.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal=12.dp)) {
            rows.forEachIndexed { index,row ->
                Row(Modifier.fillMaxWidth().padding(vertical=11.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(row.first.toString().padStart(2,'0'),modifier=Modifier.width(28.dp),fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha=.10f)),contentAlignment=Alignment.Center) {
                        androidx.compose.material3.Icon(Icons.Default.MusicNote,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f).padding(start=11.dp)) {
                        Text(row.second,fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(row.third.first,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    Text(row.third.second,fontSize=11.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(index!=rows.lastIndex) androidx.compose.material3.HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.45f))
            }
        }
    }
}

private fun buildStatsSnapshot(range:StatsRange,tracks:List<MusicTrack>,events:List<LibraryHistoryEvent>):StatsSnapshot {
    val now=Calendar.getInstance()
    val today=dayKey(now.timeInMillis)
    val cutoff=range.days?.let { days -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0);add(Calendar.DAY_OF_YEAR,-(days-1)) }.timeInMillis }
    fun included(event:LibraryHistoryEvent)=cutoff==null || event.timestamp>=cutoff
    val selected=events.filter(::included)
    val total=selected.filter { it.type==HistoryEventType.LISTEN }.sumOf { it.positionMs }
    val plays=selected.count { it.type==HistoryEventType.PLAY }
    val byTrack=selected.groupBy { it.trackId }
    val trackMap=tracks.associateBy { it.id }
    val topTracks=byTrack.mapNotNull { (id,list) -> trackMap[id]?.let { track -> TrackRank(track,list.filter { it.type==HistoryEventType.LISTEN }.sumOf { it.positionMs },list.count { it.type==HistoryEventType.PLAY }) } }
        .filter { it.listenedMs>0L }.sortedByDescending { it.listenedMs }.take(5)
    val topArtists=topTracksAll(events,trackMap,cutoff)
    val daily=selected.filter { it.type==HistoryEventType.LISTEN }.groupBy { dayKey(it.timestamp) }.mapValues { (_,list)->list.sumOf { it.positionMs } }
    val chartCount=if(range==StatsRange.WEEK) 7 else if(range==StatsRange.MONTH) 30 else 14
    val days=(chartCount-1 downTo 0).map { offset ->
        val date=Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0);add(Calendar.DAY_OF_YEAR,-offset) }
        val key=dayKey(date.timeInMillis)
        StatsDay(key,if(chartCount==7) SimpleDateFormat("EE",Locale.getDefault()).format(date.time) else SimpleDateFormat("d",Locale.getDefault()).format(date.time),daily[key]?:0L)
    }
    val allDaily=events.filter { it.type==HistoryEventType.LISTEN }.groupBy { dayKey(it.timestamp) }.mapValues { (_,list)->list.sumOf { it.positionMs } }
    val streakDays=allDaily.filterValues { it>0 }.keys.toSet()
    var cursor=Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0) }
    if(today !in streakDays) cursor.add(Calendar.DAY_OF_YEAR,-1)
    var streak=0
    while(dayKey(cursor.timeInMillis) in streakDays && streak<3650) { streak++;cursor.add(Calendar.DAY_OF_YEAR,-1) }
    return StatsSnapshot(total,plays,streak,days,topTracks,topArtists)
}
private fun topTracksAll(events:List<LibraryHistoryEvent>,tracks:Map<String,MusicTrack>,cutoff:Long?):List<ArtistRank> {
    val selected=events.filter { (cutoff==null || it.timestamp>=cutoff) && it.type==HistoryEventType.LISTEN }
    val totals=linkedMapOf<String,Long>()
    val plays=events.filter { (cutoff==null || it.timestamp>=cutoff) && it.type==HistoryEventType.PLAY }.groupBy { it.trackId }.mapValues { it.value.size }
    selected.forEach { event ->
        val artist=tracks[event.trackId]?.artist?.trim()?.takeIf { it.isNotBlank() && !it.equals("Unknown artist",true) } ?: return@forEach
        totals[artist]=(totals[artist]?:0L)+event.positionMs
    }
    return totals.map { (name,ms) -> ArtistRank(name,ms,selected.filter { tracks[it.trackId]?.artist?.equals(name,true)==true }.map { it.trackId }.distinct().sumOf { plays[it]?:0 }) }
        .sortedByDescending { it.listenedMs }.take(5)
}
private fun dayKey(timestamp:Long):String=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(timestamp))
private fun formatListeningTime(ms:Long):String {
    val minutes=(ms.coerceAtLeast(0L)/60000L).toInt()
    val hours=minutes/60
    val remainder=minutes%60
    return if(hours==0) "${minutes} min" else if(remainder==0) "${hours} hr" else "${hours} hr ${remainder} min"
}
