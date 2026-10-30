package com.robotcentinela.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robotcentinela.app.ui.model.ChartPoint
import com.robotcentinela.app.ui.model.Formats
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Poppins
import kotlin.math.abs
import kotlin.math.max

/**
 * Gráfica de línea con ejes graduados y unidades, rango temporal visible, banda mínimo-máximo,
 * selección por toque y huecos conservados (guía §12.5).
 *
 * @param decimals decimales de las etiquetas del eje Y.
 * @param xLabel formato de una marca de tiempo del eje X.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    unit: String?,
    decimals: Int,
    xLabel: (Long) -> String,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    accessibilitySummary: String = "",
    area: Boolean = false,
) {
    val colors = CentinelaTheme.colors
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontFamily = Poppins, fontSize = 11.sp, color = colors.textSecondary)

    // Reutilización de paths para optimización de memoria y menor presión al GC
    val reusableBandPath = remember { Path() }
    val reusableFillPath = remember { Path() }
    val reusableLinePath = remember { Path() }

    val values = points.flatMap { listOfNotNull(it.y, it.low, it.high) }
    val minX = points.minOfOrNull { it.xMs } ?: 0L
    val maxX = max(points.maxOfOrNull { it.xMs } ?: 1L, minX + 1)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = accessibilitySummary }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    if (points.isEmpty()) return@detectTapGestures
                    val left = 52.dp.toPx()
                    val width = size.width - left - 8.dp.toPx()
                    val index = points.indices.minByOrNull { i ->
                        abs(left + width * (points[i].xMs - minX).toFloat() / (maxX - minX) - tap.x)
                    }
                    onSelect(if (index == selectedIndex) null else index)
                }
            },
    ) {
        val left = 52.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 26.dp.toPx()

        // Ejes.
        drawLine(colors.border, Offset(left, top), Offset(left, bottom), 1.dp.toPx())
        drawLine(colors.border, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
        unit?.let { drawText(measurer, it, Offset(0f, 0f), axisStyle) }

        if (values.isEmpty()) {
            drawText(measurer, "Sin muestras válidas en el periodo", Offset(left + 8.dp.toPx(), (top + bottom) / 2), axisStyle)
            return@Canvas
        }

        val rawMin = values.min()
        val rawMax = values.max()
        val pad = if (rawMax > rawMin) (rawMax - rawMin) * 0.08 else max(abs(rawMax) * 0.05, 1.0)
        val minY = rawMin - pad
        val maxY = rawMax + pad

        fun px(x: Long) = left + (right - left) * (x - minX).toFloat() / (maxX - minX)
        fun py(y: Double) = bottom - (bottom - top) * ((y - minY) / (maxY - minY)).toFloat()

        // Marcas del eje Y.
        for (i in 0..3) {
            val v = minY + (maxY - minY) * i / 3
            val y = py(v)
            drawLine(colors.border.copy(alpha = 0.5f), Offset(left, y), Offset(right, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            drawText(measurer, Formats.number(v, decimals), Offset(2.dp.toPx(), y - 8.dp.toPx()), axisStyle)
        }
        // Rango temporal visible: inicio y fin.
        drawText(measurer, xLabel(minX), Offset(left, bottom + 6.dp.toPx()), axisStyle)
        val endLabel = measurer.measure(xLabel(maxX), axisStyle)
        drawText(endLabel, topLeft = Offset(right - endLabel.size.width, bottom + 6.dp.toPx()))

        // Segmentos continuos (los nulos cortan la serie).
        val segments = mutableListOf<MutableList<ChartPoint>>()
        var current = mutableListOf<ChartPoint>()
        points.forEach { p ->
            if (p.y == null) {
                if (current.isNotEmpty()) segments += current
                current = mutableListOf()
            } else {
                current += p
            }
        }
        if (current.isNotEmpty()) segments += current

        segments.forEach { seg ->
            // Banda mínimo-máximo.
            if (seg.all { it.low != null && it.high != null } && seg.size > 1) {
                reusableBandPath.reset()
                seg.forEachIndexed { i, p -> if (i == 0) reusableBandPath.moveTo(px(p.xMs), py(p.high!!)) else reusableBandPath.lineTo(px(p.xMs), py(p.high!!)) }
                seg.asReversed().forEach { p -> reusableBandPath.lineTo(px(p.xMs), py(p.low!!)) }
                reusableBandPath.close()
                drawPath(reusableBandPath, colors.primary.copy(alpha = 0.15f))
            }
            // Área bajo la serie (estilo del mockup). Cada tramo continuo se rellena por separado: los huecos quedan vacíos.
            if (area && seg.size > 1) {
                reusableFillPath.reset()
                reusableFillPath.moveTo(px(seg.first().xMs), bottom)
                seg.forEach { p -> reusableFillPath.lineTo(px(p.xMs), py(p.y!!)) }
                reusableFillPath.lineTo(px(seg.last().xMs), bottom)
                reusableFillPath.close()
                drawPath(reusableFillPath, colors.primary.copy(alpha = 0.35f))
            }
            if (seg.size == 1) {
                drawCircle(colors.primary, 3.dp.toPx(), Offset(px(seg[0].xMs), py(seg[0].y!!)))
            } else {
                reusableLinePath.reset()
                seg.forEachIndexed { i, p -> if (i == 0) reusableLinePath.moveTo(px(p.xMs), py(p.y!!)) else reusableLinePath.lineTo(px(p.xMs), py(p.y!!)) }
                drawPath(reusableLinePath, colors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        selectedIndex?.let { idx ->
            points.getOrNull(idx)?.let { p ->
                val x = px(p.xMs)
                drawLine(colors.secondary, Offset(x, top), Offset(x, bottom), 2.dp.toPx())
                p.y?.let { drawCircle(colors.secondary, 5.dp.toPx(), Offset(x, py(it))) }
            }
        }
    }
}
