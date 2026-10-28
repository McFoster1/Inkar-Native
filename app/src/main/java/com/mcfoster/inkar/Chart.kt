package com.mcfoster.inkar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private class Pt(val date: LocalDate, val v: Map<String, Int>, val total: Int)

private fun niceScale(v: Double): Pair<Double, Double> {
    val p = 10.0.pow(floor(log10(v / 4.0)))
    for (f in listOf(1.0, 2.0, 2.5, 5.0, 10.0)) {
        val st = f * p
        if (ceil(v / st - 1e-9) <= 5) return st to ceil(v / st - 1e-9) * st
    }
    return 1.0 to v
}

private fun nfmt(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r % 1.0 == 0.0) fmt(r.toInt()) else java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(r)
}

private fun hex(c: String): Color = try {
    Color(android.graphics.Color.parseColor(c))
} catch (e: Exception) {
    InkDim
}

private fun smooth(P: List<Offset>): Path {
    val path = Path()
    val n = P.size
    if (n < 2) return path
    val dx = FloatArray(n - 1)
    val m = FloatArray(n - 1)
    val t = FloatArray(n)
    for (i in 0 until n - 1) {
        dx[i] = P[i + 1].x - P[i].x
        m[i] = (P[i + 1].y - P[i].y) / dx[i]
    }
    t[0] = m[0]
    t[n - 1] = m[n - 2]
    for (i in 1 until n - 1) t[i] = if (m[i - 1] * m[i] <= 0f) 0f else (m[i - 1] + m[i]) / 2f
    for (i in 0 until n - 1) {
        if (m[i] == 0f) {
            t[i] = 0f
            t[i + 1] = 0f
        } else {
            val a = t[i] / m[i]
            val b = t[i + 1] / m[i]
            val q = a * a + b * b
            if (q > 9f) {
                val z = 3f / sqrt(q)
                t[i] = z * a * m[i]
                t[i + 1] = z * b * m[i]
            }
        }
    }
    path.moveTo(P[0].x, P[0].y)
    for (i in 0 until n - 1) {
        val h = dx[i] / 3f
        path.cubicTo(P[i].x + h, P[i].y + t[i] * h, P[i + 1].x - h, P[i + 1].y - t[i + 1] * h, P[i + 1].x, P[i + 1].y)
    }
    return path
}

@Composable
private fun Seg(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(InkEl).padding(2.dp)) {
        for ((key, label) in options) {
            val on = key == selected
            Box(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (on) InkBlue else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) { Txt(label, 13f, if (on) Color.White else InkMuted, lh = 1.3f) }
        }
    }
}

@Composable
private fun LegendChip(label: String, color: Color, off: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    var m = Modifier.alpha(if (off) 0.45f else 1f).clip(shape).background(InkEl).border(1.dp, InkBorder, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Txt(
            label, 12f, InkMuted, lh = 1.3f, maxLines = 1, overflow = TextOverflow.Ellipsis,
            decoration = if (off) TextDecoration.LineThrough else null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WritingChartCard(repo: Repo, goal: Int, sel: Int?, onSel: (Int?) -> Unit) {
    var mode by rememberSaveable { mutableStateOf("day") }
    var rng by rememberSaveable { mutableStateOf(7) }
    var hidden by remember { mutableStateOf(setOf<String>()) }
    val cum = mode == "cum"

    val days = repo.chartDays(rng)
    val k = days.size
    val tot = LinkedHashMap<String, Int>()
    for (d in days) for ((id, w) in d.words) tot[id] = (tot[id] ?: 0) + w
    val ids = tot.filter { it.value > 0 }.keys.sortedByDescending { tot[it] ?: 0 }
    val run = HashMap<String, Int>()
    val pts = days.map { d ->
        val v = HashMap<String, Int>()
        var total = 0
        for (id in ids) {
            val w = d.words[id] ?: 0
            val x = if (cum) {
                run[id] = (run[id] ?: 0) + w
                run[id] ?: 0
            } else w
            v[id] = x
            total += x
        }
        Pt(d.date, v, total)
    }
    fun gA(i: Int): Int = goal * (i + 1)
    val vis = ids.filter { it !in hidden }
    var maxV = 10.0
    for ((i, p) in pts.withIndex()) {
        maxV = maxOf(maxV, p.total.toDouble(), if (cum) gA(i).toDouble() else goal.toDouble())
    }
    val (st, top) = niceScale(maxV)
    val selIdx = sel?.takeIf { it in 0 until k }

    HCard(glow = Glow.TopRight) {
        Txt("Writing Activity", 15f, InkText, FontWeight.SemiBold)
        Txt(if (cum) "Running total across the period" else "Words written per day", 12f, InkDim)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seg(listOf("day" to "Daily", "cum" to "Cumulative"), mode) { mode = it; onSel(null) }
            Seg(listOf("7" to "7D", "30" to "30D", "90" to "90D"), rng.toString()) { rng = it.toInt(); onSel(null) }
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendChip("Total", InkBlue)
            for (id in ids) {
                val (title, color) = repo.metaOf(id)
                LegendChip(title, hex(color), off = id in hidden) {
                    hidden = if (id in hidden) hidden - id else hidden + id
                }
            }
            LegendChip(if (cum) "Goal pace" else "Daily goal", InkOk)
        }
        Spacer(Modifier.height(10.dp))

        val tm = rememberTextMeasurer()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxOf(maxWidth.value, 260f)
            val h = if (w < 480f) 200f else 240f
            val pl = 44f
            val pr = 14f
            val pt = 18f
            val pb = 28f
            val iw = w - pl - pr
            val ih = h - pt - pb
            fun xd(i: Int): Float = pl + i.toFloat() / (k - 1) * iw
            fun yd(v: Double): Float = (pt + ih - v / top * ih).toFloat()

            Box(Modifier.fillMaxWidth().height(h.dp)) {
                Canvas(
                    Modifier.fillMaxSize().pointerInput(k, mode, rng) {
                        detectTapGestures { off ->
                            val idx = ((off.x / density - pl) / iw * (k - 1)).roundToInt().coerceIn(0, k - 1)
                            onSel(idx)
                        }
                    },
                ) {
                    val u = density
                    fun label(s: String, x: Float, baseY: Float, anchor: Int, color: Color) {
                        val r = tm.measure(s, TextStyle(fontSize = 11.sp, color = color))
                        val left = when (anchor) {
                            0 -> x
                            1 -> x - r.size.width / 2f
                            else -> x - r.size.width
                        }
                        drawText(r, topLeft = Offset(left, baseY - r.firstBaseline))
                    }
                    val dash35 = PathEffect.dashPathEffect(floatArrayOf(3f * u, 5f * u))

                    var v = 0.0
                    while (v <= top + 1e-9) {
                        val y = yd(v) * u
                        drawLine(InkBorder, Offset(pl * u, y), Offset((w - pr) * u, y), strokeWidth = 1f * u, pathEffect = dash35)
                        label(nfmt(v), (pl - 8f) * u, y + 4f * u, 2, InkDim)
                        v += st
                    }
                    if (k <= 7) {
                        for (i in 0 until k) {
                            val s = if (i == k - 1) "Today" else pts[i].date.format(DateTimeFormatter.ofPattern("EEE"))
                            val a = if (i == 0) 0 else if (i == k - 1) 2 else 1
                            label(s, xd(i) * u, (h - 8f) * u, a, InkDim)
                        }
                    } else {
                        for (j in 0 until 5) {
                            val i = Math.round(j * (k - 1) / 4.0).toInt()
                            val s = if (i == k - 1) "Today" else pts[i].date.format(DateTimeFormatter.ofPattern("MMM d"))
                            val a = if (j == 0) 0 else if (j == 4) 2 else 1
                            label(s, xd(i) * u, (h - 8f) * u, a, InkDim)
                        }
                    }

                    val tp = pts.mapIndexed { i, p -> Offset(xd(i) * u, yd(p.total.toDouble()) * u) }
                    val y0 = yd(0.0) * u
                    val line = smooth(tp)
                    val area = smooth(tp)
                    area.lineTo(tp.last().x, y0)
                    area.lineTo(tp.first().x, y0)
                    area.close()
                    val minY = tp.minOf { it.y }
                    drawPath(area, brush = Brush.verticalGradient(listOf(InkBlue.copy(alpha = 0.4f), InkBlue.copy(alpha = 0f)), startY = minY, endY = y0))

                    if (!cum) {
                        val gy = yd(goal.toDouble()) * u
                        drawLine(InkOk.copy(alpha = 0.9f), Offset(pl * u, gy), Offset((w - pr) * u, gy), strokeWidth = 1f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * u, 5f * u)))
                        label("Goal " + fmt(goal), (w - pr - 4f) * u, gy - 6f * u, 2, InkOk)
                    } else {
                        val ya = yd(gA(0).toDouble()) * u
                        val yb = yd(gA(k - 1).toDouble()) * u
                        drawLine(InkOk.copy(alpha = 0.9f), Offset(xd(0) * u, ya), Offset(xd(k - 1) * u, yb), strokeWidth = 1f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * u, 5f * u)))
                        label("Goal pace", (w - pr - 4f) * u, maxOf(yb - 6f * u, 12f * u), 2, InkOk)
                    }

                    for (id in vis) {
                        val sp = pts.mapIndexed { i, p -> Offset(xd(i) * u, yd((p.v[id] ?: 0).toDouble()) * u) }
                        drawPath(smooth(sp), hex(repo.metaOf(id).second).copy(alpha = 0.9f), style = Stroke(1.75f * u, cap = StrokeCap.Round))
                    }
                    drawPath(line, InkBlue.copy(alpha = 0.25f), style = Stroke(7f * u, cap = StrokeCap.Round))
                    drawPath(line, InkBlue, style = Stroke(2.75f * u, cap = StrokeCap.Round))

                    if (!cum) {
                        for ((i, p) in pts.withIndex()) {
                            if (p.total > 0 && p.total >= goal) {
                                val c = Offset(xd(i) * u, yd(p.total.toDouble()) * u)
                                val r = (if (k > 30) 3f else 4.5f) * u
                                drawCircle(InkOk, r, c)
                                drawCircle(InkCard, r, c, style = Stroke(2f * u))
                            }
                        }
                    }
                    if (selIdx != null) {
                        val sx = xd(selIdx) * u
                        drawLine(InkDim, Offset(sx, pt * u), Offset(sx, (pt + ih) * u), strokeWidth = 1f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * u, 4f * u)))
                        val p = pts[selIdx]
                        for (id in vis) {
                            val c = Offset(sx, yd((p.v[id] ?: 0).toDouble()) * u)
                            drawCircle(hex(repo.metaOf(id).second), 4f * u, c)
                            drawCircle(InkCard, 4f * u, c, style = Stroke(2f * u))
                        }
                        val c = Offset(sx, yd(p.total.toDouble()) * u)
                        drawCircle(InkCard, 6f * u, c)
                        drawCircle(InkBlue, 6f * u, c, style = Stroke(3f * u))
                    }
                }

                if (selIdx != null) {
                    val p = pts[selIdx]
                    val xs = xd(selIdx)
                    val py = yd(p.total.toDouble())
                    Box(
                        Modifier.layout { measurable, constraints ->
                            val maxW = minOf(250.dp.roundToPx(), constraints.maxWidth - 8.dp.roundToPx())
                            val placeable = measurable.measure(Constraints(0, maxOf(0, maxW), 0, constraints.maxHeight))
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                val pw = placeable.width
                                val ph = placeable.height
                                val m4 = 4.dp.toPx()
                                val sx = xs * density
                                val sy = py * density
                                var ty = sy - ph - 16.dp.toPx()
                                if (ty < m4) ty = sy + 16.dp.toPx()
                                val left = (sx - pw / 2f).coerceIn(m4, maxOf(m4, constraints.maxWidth - pw - m4))
                                val topY = maxOf(m4, minOf(ty, constraints.maxHeight - ph - m4))
                                placeable.place(left.roundToInt(), topY.roundToInt())
                            }
                        },
                    ) {
                        ChartPopup(repo, p, ids, goal, cum, selIdx, onClose = { onSel(null) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartPopup(repo: Repo, p: Pt, ids: List<String>, goal: Int, cum: Boolean, idx: Int, onClose: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(IntrinsicSize.Max).clip(shape).background(Color(0xE61A1E25)).border(1.dp, InkBorder, shape)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt(p.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")), 13f, InkMuted, modifier = Modifier.weight(1f, fill = false), lh = 1.3f)
            Txt("×", 20f, InkMuted, modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 4.dp), lh = 1f)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(fmt(p.total), 26f, InkText, FontWeight.Bold, lh = 1.2f)
            Txt(if (cum) " words so far" else " words", 13f, InkDim, FontWeight.Medium, Modifier.padding(bottom = 4.dp), lh = 1.2f)
        }
        val rows = ids.filter { (p.v[it] ?: 0) > 0 }.sortedByDescending { p.v[it] ?: 0 }
        if (rows.isEmpty()) {
            Txt("No words written", 13f, InkDim, modifier = Modifier.padding(top = 6.dp), lh = 1.3f)
        } else {
            for (id in rows) {
                val (title, color) = repo.metaOf(id)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(hex(color)))
                        Spacer(Modifier.width(7.dp))
                        Txt(title, 13f, InkText, lh = 1.3f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Txt(fmt(p.v[id] ?: 0), 13f, InkText, FontWeight.Bold, lh = 1.3f)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(InkBorder))
        Spacer(Modifier.height(8.dp))
        if (!cum) {
            if (p.total >= goal) Txt("✓ Daily goal reached", 13f, InkOk, FontWeight.Medium, lh = 1.3f)
            else Txt(fmt(goal - p.total) + " to reach goal", 13f, InkDim, lh = 1.3f)
        } else {
            val diff = p.total - goal * (idx + 1)
            if (diff >= 0) Txt(fmt(diff) + " ahead of goal pace", 13f, InkOk, FontWeight.Medium, lh = 1.3f)
            else Txt(fmt(-diff) + " behind goal pace", 13f, InkDim, lh = 1.3f)
        }
    }
}
