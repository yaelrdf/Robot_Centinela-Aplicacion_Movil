package com.robotcentinela.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.robotcentinela.app.R
import com.robotcentinela.app.homemapping.domain.FloorPlan
import com.robotcentinela.app.ui.model.Formats
import com.robotcentinela.app.ui.model.MapLayers
import com.robotcentinela.app.ui.model.RobotMarker
import com.robotcentinela.app.ui.model.TrajectoryPoint
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Poppins
import com.robotcentinela.app.ui.theme.Spacing
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private class MapTransform(
    val size: Size,
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
    zoom: Float,
    val pan: Offset,
) {
    private val cx = (minX + maxX) / 2
    private val cy = (minY + maxY) / 2
    val pxPerMeter: Float = (min(size.width / (maxX - minX), size.height / (maxY - minY)) * 0.9 * zoom).toFloat()

    fun x(xm: Double) = size.width / 2 + ((xm - cx) * pxPerMeter).toFloat() + pan.x
    fun y(ym: Double) = size.height / 2 - ((ym - cy) * pxPerMeter).toFloat() + pan.y
    fun point(xm: Double, ym: Double) = Offset(x(xm), y(ym))

    /** Desplazamiento que deja el punto (xm, ym) en el centro de la vista. */
    fun panToCenter(xm: Double, ym: Double) = Offset(-((xm - cx) * pxPerMeter).toFloat(), ((ym - cy) * pxPerMeter).toFloat())
}

/**
 * Mapa asistido 2D (guía §12.5, HU05, HU06).
 *
 * Sin plano (contrato de mapas pendiente o versión sin archivo), dibuja la trayectoria sobre una
 * cuadrícula métrica: nunca inventa habitaciones ni asigna lecturas a una (RB11).
 *
 * @param signalLabel nombre de la escala de señal relativa, p. ej. "Señal relativa MQ6 (ADC, sin calibrar)".
 */
@Composable
fun FloorPlanView(
    plan: FloorPlan?,
    trajectory: List<TrajectoryPoint>,
    robot: RobotMarker?,
    layers: MapLayers,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    signalLabel: String? = null,
    accessibilitySummary: String = "",
) {
    val colors = CentinelaTheme.colors
    val textMeasurer = rememberTextMeasurer()
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // Reutilización de paths para optimizar memoria en recomposiciones continuas
    val reusableRoutePath = remember { Path() }
    val reusableDiamondPath = remember { Path() }

    // Límites del dibujo: el plano o, sin plano, la trayectoria con margen.
    val bounds = remember(plan, trajectory.size, robot == null) {
        if (plan != null) {
            doubleArrayOf(0.0, 0.0, plan.widthM, plan.heightM)
        } else {
            val xs = trajectory.map { it.xM } + listOfNotNull(robot?.xM)
            val ys = trajectory.map { it.yM } + listOfNotNull(robot?.yM)
            if (xs.isEmpty()) {
                doubleArrayOf(-2.0, -2.0, 2.0, 2.0)
            } else {
                val minX = xs.min() - 1
                val maxX = max(xs.max() + 1, minX + 4)
                val minY = ys.min() - 1
                val maxY = max(ys.max() + 1, minY + 4)
                doubleArrayOf(minX, minY, maxX, maxY)
            }
        }
    }
    val signals = remember(trajectory) { trajectory.mapNotNull { it.signal } }
    val signalMin = remember(signals) { signals.minOrNull() }
    val signalMax = remember(signals) { signals.maxOrNull() }
    val samplePoints = remember(trajectory, layers.transit) {
        trajectory.filter { it.signal != null && (it.sampling || layers.transit) }
    }

    Box(
        modifier
            .clip(CentinelaShapes.control)
            .background(colors.surface)
            .border(CentinelaShapes.borderWidth, colors.border, CentinelaShapes.control),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it.toSize() }
                .semantics { contentDescription = accessibilitySummary }
                .then(
                    if (interactive) {
                        Modifier.pointerInput(Unit) {
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                zoom = (zoom * zoomChange).coerceIn(0.6f, 6f)
                                pan += panChange
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val t = MapTransform(size, bounds[0], bounds[1], bounds[2], bounds[3], zoom, pan)
            val labelStyle = TextStyle(fontFamily = Poppins, fontSize = 12.sp, color = colors.textPrimary)
            val smallStyle = TextStyle(fontFamily = Poppins, fontSize = 11.sp, color = colors.textSecondary)

            if (plan == null) drawMetricGrid(t, colors.border)

            plan?.let { p ->
                // Zonas sin explorar: trama neutra (§12.5).
                p.unexplored.forEach { r ->
                    val left = t.x(r.left)
                    val right = t.x(r.right)
                    val top = t.y(r.top)
                    val bottom = t.y(r.bottom)
                    clipRect(left, top, right, bottom) {
                        var x = left - (bottom - top)
                        while (x < right) {
                            drawLine(colors.unexploredHatch, Offset(x, bottom), Offset(x + (bottom - top), top), 1.5f)
                            x += 10f
                        }
                    }
                }
                p.walls.forEach { w ->
                    drawLine(colors.textPrimary, t.point(w.from.xM, w.from.yM), t.point(w.to.xM, w.to.yM), 3.dp.toPx(), StrokeCap.Round)
                }
                p.rooms.forEach { room ->
                    drawText(textMeasurer, room.name, Offset(t.x(room.bounds.left) + 6.dp.toPx(), t.y(room.bounds.top) + 4.dp.toPx()), labelStyle)
                }
                // Estaciones: cuadros redondeados con contorno (distintos de muestras y de la posición).
                p.stations.forEach { s ->
                    val c = t.point(s.position.xM, s.position.yM)
                    val half = 6.dp.toPx()
                    drawRoundRect(
                        colors.primary,
                        Offset(c.x - half, c.y - half),
                        Size(half * 2, half * 2),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        style = Stroke(2.dp.toPx(), join = StrokeJoin.Round),
                    )
                    drawText(textMeasurer, s.id.value, Offset(c.x + half + 2, c.y - half - 2), smallStyle)
                }
            }

            // Ruta recorrida con uniones y vértices suavizados.
            if (layers.route && trajectory.size > 1) {
                reusableRoutePath.reset()
                trajectory.forEachIndexed { i, pt ->
                    val o = t.point(pt.xM, pt.yM)
                    if (i == 0) reusableRoutePath.moveTo(o.x, o.y) else reusableRoutePath.lineTo(o.x, o.y)
                }
                drawPath(
                    reusableRoutePath,
                    colors.textSecondary,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.cornerPathEffect(4.dp.toPx()),
                    ),
                )
            }

            // Muestras de señal relativa: rombos (no se confunden con el marcador de posición, §12.5).
            if (layers.samples && signalMin != null && signalMax != null) {
                samplePoints.forEach { pt ->
                    val f = if (signalMax > signalMin) ((pt.signal!! - signalMin) / (signalMax - signalMin)).toFloat() else 0.5f
                    drawDiamond(t.point(pt.xM, pt.yM), 5.dp.toPx(), colors.secondary.copy(alpha = 0.25f + 0.75f * f), colors.textPrimary, reusableDiamondPath)
                }
            }

            // Robot: círculo de incertidumbre (discontinuo, en metros) distinto de la huella (fija, sólida).
            robot?.let { r ->
                val c = t.point(r.xM, r.yM)
                r.sigmaM?.let { sigma ->
                    val radius = max((sigma * t.pxPerMeter).toFloat(), 2.dp.toPx())
                    drawCircle(colors.secondary.copy(alpha = 0.18f), radius, c)
                    drawCircle(
                        colors.secondary,
                        radius,
                        c,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                    )
                }
                val body = 7.dp.toPx()
                drawCircle(colors.secondary, body, c)
                drawCircle(colors.surface, body, c, style = Stroke(2.dp.toPx()))
                val tip = Offset(c.x + (cos(r.thetaRad) * body * 1.9).toFloat(), c.y - (sin(r.thetaRad) * body * 1.9).toFloat())
                drawLine(colors.textPrimary, c, tip, 2.dp.toPx(), StrokeCap.Round)
            }

            drawScaleBar(t, textMeasurer, smallStyle, colors.textPrimary)
            drawOrientation(textMeasurer, smallStyle, colors.textPrimary)
        }

        // Rótulo de tipo de plano (§12.5) o aviso de cuadrícula sin plano.
        Text(
            text = when {
                plan == null -> "Sin plano: trayectoria sobre cuadrícula métrica"
                plan.semantic -> "Plano semántico"
                else -> "Plano de ocupación"
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.onStatusNeutral,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.s)
                .clip(CentinelaShapes.pill)
                .background(colors.statusNeutral)
                .padding(horizontal = Spacing.s, vertical = 2.dp),
        )

        if (interactive) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                MapButton(R.drawable.ic_add, "Acercar") { zoom = (zoom * 1.4f).coerceAtMost(6f) }
                MapButton(R.drawable.ic_remove, "Alejar") { zoom = (zoom / 1.4f).coerceAtLeast(0.6f) }
                MapButton(R.drawable.ic_my_location, "Centrar en el robot") {
                    robot?.let { r ->
                        pan = MapTransform(canvasSize, bounds[0], bounds[1], bounds[2], bounds[3], zoom, Offset.Zero).panToCenter(r.xM, r.yM)
                    }
                }
            }
        }
    }

    if (signalLabel != null && signalMin != null && signalMax != null && layers.samples) {
        SignalScale(signalLabel, signalMin, signalMax, Modifier.padding(top = Spacing.s))
    }
}

@Composable
private fun MapButton(icon: Int, description: String, onClick: () -> Unit) {
    val colors = CentinelaTheme.colors
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(CentinelaShapes.minTouchTarget),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.chrome, contentColor = colors.onChrome),
    ) {
        AppIcon(icon, contentDescription = description, tint = colors.onChrome)
    }
}

/** Escala explícita de la señal relativa (§12.5: puntos de señal relativa bajo una escala explícita). */
@Composable
private fun SignalScale(label: String, min: Double, max: Double, modifier: Modifier = Modifier) {
    val colors = CentinelaTheme.colors
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(Formats.number(min, 0), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Canvas(Modifier.size(width = 120.dp, height = 12.dp)) {
                val steps = 12
                val w = size.width / steps
                for (i in 0 until steps) {
                    drawRect(colors.secondary.copy(alpha = 0.25f + 0.75f * i / (steps - 1)), Offset(i * w, 0f), Size(w + 1, size.height))
                }
            }
            Text(Formats.number(max, 0), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }
    }
}

/** Leyenda del mapa (§12.5). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapLegend(showSamples: Boolean, modifier: Modifier = Modifier) {
    val colors = CentinelaTheme.colors
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LegendItem("Robot (posición estimada)") { c -> drawCircle(colors.secondary, size.minDimension / 3, c) }
        LegendItem("Incertidumbre") { c ->
            drawCircle(colors.secondary, size.minDimension / 2.2f, c, style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
        }
        LegendItem("Ruta") { c -> drawLine(colors.textSecondary, Offset(0f, c.y), Offset(size.width, c.y), 5f) }
        LegendItem("Estación") { _ ->
            drawRect(colors.primary, Offset(size.width * 0.2f, size.height * 0.2f), Size(size.width * 0.6f, size.height * 0.6f), style = Stroke(4f))
        }
        if (showSamples) {
            LegendItem("Muestra") { c -> drawDiamond(c, size.minDimension / 2.6f, colors.secondary, colors.textPrimary, null) }
        }
        LegendItem("Sin explorar") { _ ->
            var x = -size.height
            while (x < size.width) {
                drawLine(colors.unexploredHatch, Offset(x, size.height), Offset(x + size.height, 0f), 2f)
                x += 6f
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: DrawScope.(center: Offset) -> Unit) {
    val colors = CentinelaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(Modifier.size(18.dp)) { swatch(center) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    }
}

private fun DrawScope.drawDiamond(center: Offset, half: Float, fill: Color, outline: Color, reusablePath: Path? = null) {
    val path = (reusablePath ?: Path()).apply {
        reset()
        moveTo(center.x, center.y - half)
        lineTo(center.x + half, center.y)
        lineTo(center.x, center.y + half)
        lineTo(center.x - half, center.y)
        close()
    }
    drawPath(path, fill)
    drawPath(path, outline, style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawMetricGrid(t: MapTransform, color: Color) {
    val step = 1.0
    var x = floor(t.minX)
    while (x <= t.maxX) {
        drawLine(color, Offset(t.x(x), 0f), Offset(t.x(x), size.height), 1f)
        x += step
    }
    var y = floor(t.minY)
    while (y <= t.maxY) {
        drawLine(color, Offset(0f, t.y(y)), Offset(size.width, t.y(y)), 1f)
        y += step
    }
}

/** Barra de escala métrica legible: elige 0.5, 1, 2 o 5 m (o sus potencias de 10) para unos 80 dp. */
private fun DrawScope.drawScaleBar(
    t: MapTransform,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    color: Color,
) {
    if (t.pxPerMeter <= 0f) return
    val targetMeters = 80.dp.toPx() / t.pxPerMeter
    val magnitude = 10.0.pow(floor(log10(targetMeters.toDouble())))
    val nice = listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.lastOrNull { it <= targetMeters } ?: magnitude
    val length = (nice * t.pxPerMeter).toFloat()
    val left = 12.dp.toPx()
    val baseline = size.height - 14.dp.toPx()
    drawLine(color, Offset(left, baseline), Offset(left + length, baseline), 2.dp.toPx())
    drawLine(color, Offset(left, baseline - 5.dp.toPx()), Offset(left, baseline + 1), 2.dp.toPx())
    drawLine(color, Offset(left + length, baseline - 5.dp.toPx()), Offset(left + length, baseline + 1), 2.dp.toPx())
    val label = if (nice < 1) "${Formats.number(nice * 100, 0)} cm" else "${Formats.number(nice, if (nice < 10) 1 else 0)} m"
    drawText(measurer, label, Offset(left, baseline + 2.dp.toPx()), style)
}

/** Orientación del plano: la flecha indica el eje +Y del marco del mapa. */
private fun DrawScope.drawOrientation(measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle, color: Color) {
    val x = 16.dp.toPx()
    val top = 10.dp.toPx()
    val bottom = 34.dp.toPx()
    drawLine(color, Offset(x, bottom), Offset(x, top), 2.dp.toPx(), StrokeCap.Round)
    drawLine(color, Offset(x, top), Offset(x - 5.dp.toPx(), top + 6.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
    drawLine(color, Offset(x, top), Offset(x + 5.dp.toPx(), top + 6.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
    drawText(measurer, "+Y", Offset(x + 7.dp.toPx(), top - 2.dp.toPx()), style)
}
