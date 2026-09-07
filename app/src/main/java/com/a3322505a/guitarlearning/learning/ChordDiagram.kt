package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Unit-space projection only. Rotation never transforms the chord's string/fret facts. */
class ChordDiagramGeometry(firstFret: Int, lastFret: Int, val vertical: Boolean) {
    val first = maxOf(1, firstFret)
    val last = maxOf(first + 3, lastFret).coerceAtMost(15)
    val count = last-first+1
    val columns = if(vertical) 7f else count+2f
    val rows = if(vertical) count+2f else 7f
    fun center(c: Coordinate): Pair<Float,Float> {
        val fret = if(c.fret==0) .3f else c.fret-first+1.5f
        return if(vertical) (7-c.string).toFloat() to fret else fret to c.string.toFloat()
    }
    fun at(x: Float,y: Float): Coordinate? {
        if(!x.isFinite() || !y.isFinite()) return null
        val stringAxis=if(vertical) x else y
        val s=stringAxis.roundToInt()
        if(s !in 1..6 || abs(stringAxis-s)>.48f) return null
        val fretAxis=if(vertical) y else x
        val fret=if(fretAxis in 0f..0.75f) 0 else (fretAxis-.5f).roundToInt()+first-1
        if(fret!=0 && (fret !in first..last || abs(fretAxis-(fret-first+1.5f))>.5f)) return null
        return Coordinate(if(vertical) 7-s else s,fret)
    }
}

@Composable
fun ChordDiagram(state: FretboardUiState, onPosition: (PositionTapped)->Unit, modifier: Modifier=Modifier) {
    val g=remember(state.firstFret,state.lastFret,state.chordVertical) { ChordDiagramGeometry(state.firstFret,state.lastFret,state.chordVertical) }
    val chord=state.chord ?: return
    Canvas(modifier.semantics { contentDescription=state.chordTitle+if(g.vertical) "，竖向，左6弦右1弦" else "，横向，上1弦下6弦" }
        .pointerInput(state,g) { detectTapGestures { tap ->
            val title=24.dp.toPx()
            val cell=minOf(size.width/g.columns,(size.height-title)/g.rows)
            val left=(size.width-cell*g.columns)/2
            val top=title+(size.height-title-cell*g.rows)/2
            if(cell>0) g.at((tap.x-left)/cell,(tap.y-top)/cell)?.takeIf { state.interaction!=BoardInteraction.DISABLED && it in state.interactivePositions }?.let { onPosition(PositionTapped(state.viewId,it)) }
        } }) {
        val title=24.dp.toPx()
        val cell=minOf(size.width/g.columns,(size.height-title)/g.rows)
        if(cell<=0) return@Canvas
        val left=(size.width-cell*g.columns)/2
        val top=title+(size.height-title-cell*g.rows)/2
        fun xy(x:Float,y:Float)=Offset(left+x*cell,top+y*cell)
        fun point(c:Coordinate)=g.center(c).let { xy(it.first,it.second) }
        val ink=Color(0xFF223443)
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign=android.graphics.Paint.Align.CENTER; typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD) }
        fun text(value:String,p:Offset,color:Int=0xFF223443.toInt(),sizeSp:Float=12f) {
            paint.color=color; paint.textSize=sizeSp.sp.toPx()
            drawIntoCanvas { it.nativeCanvas.drawText(value,p.x,p.y-(paint.ascent()+paint.descent())/2,paint) }
        }
        drawRoundRect(Color(0xFFF7F9FB),cornerRadius=androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
        text(state.chordTitle,Offset(size.width/2,12.dp.toPx()),sizeSp=13f)
        for(s in 1..6) {
            val start=if(g.vertical) xy(s.toFloat(),1f) else xy(1f,s.toFloat())
            val end=if(g.vertical) xy(s.toFloat(),g.count+1f) else xy(g.count+1f,s.toFloat())
            val string=if(g.vertical) 7-s else s
            drawLine(ink.copy(alpha=.6f),start,end,(.6f+string*.14f).dp.toPx())
            text(string.toString(),if(g.vertical) xy(s.toFloat(),g.count+1.6f) else xy(g.count+1.6f,s.toFloat()),sizeSp=10f)
        }
        for(f in 0..g.count) {
            val start=if(g.vertical) xy(1f,f+1f) else xy(f+1f,1f)
            val end=if(g.vertical) xy(6f,f+1f) else xy(f+1f,6f)
            drawLine(ink.copy(alpha=.65f),start,end,(if(f==0 && g.first==1) 3.5f else 1f).dp.toPx())
            if(f<g.count) text((g.first+f).toString(),if(g.vertical) xy(.35f,f+1.5f) else xy(f+1.5f,6.65f),sizeSp=10f)
        }
        chord.openMutedLabels.forEach { (s,label) -> text(label,point(Coordinate(s,0))) }
        val radius=minOf(cell*.37f,15.dp.toPx())
        chord.fingers.sortedBy { it.fret }.forEach { finger ->
            val a=point(Coordinate(finger.firstString,finger.fret))
            val b=point(Coordinate(finger.lastString,finger.fret))
            val color=FingerColors[finger.finger-1]
            drawLine(ink.copy(alpha=.13f),a+Offset(0f,2.dp.toPx()),b+Offset(0f,2.dp.toPx()),radius*2+2.dp.toPx(),StrokeCap.Round)
            drawLine(color,a,b,radius*2,StrokeCap.Round)
            drawCircle(color,radius,a);drawCircle(color,radius,b)
            text(finger.finger.toString(),a,sizeSp=12f)
            if(a!=b) text(finger.finger.toString(),b,sizeSp=12f)
        }
        chord.tones.filter { it.label.isNotEmpty() && it.label.toIntOrNull()==null }.forEach { tone ->
            // Optional note names sit beside the persistent numbered finger, never replace it.
            text(tone.label,point(tone.coordinate)+Offset(cell*.42f,0f),sizeSp=9f)
        }
        state.marks.filter { it.role in listOf(MarkRole.CORRECT,MarkRole.WRONG) }.forEach {
            val p=point(it.coordinate)
            drawCircle(if(it.role==MarkRole.CORRECT) Color(0xFF16845B) else Color(0xFFCC315C),radius,p)
            text(if(it.role==MarkRole.CORRECT) "✓" else "×",p,android.graphics.Color.WHITE)
        }
    }
}
