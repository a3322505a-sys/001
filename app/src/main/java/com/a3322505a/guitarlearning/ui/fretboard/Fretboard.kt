package com.a3322505a.guitarlearning.ui.fretboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.ui.theme.FretMetal
import com.a3322505a.guitarlearning.ui.theme.FretboardWood
import com.a3322505a.guitarlearning.ui.theme.FretboardWoodDark
import com.a3322505a.guitarlearning.ui.theme.FretboardWoodLight
import com.a3322505a.guitarlearning.ui.theme.Inlay
import com.a3322505a.guitarlearning.ui.theme.NutIvory
import com.a3322505a.guitarlearning.ui.theme.PixelGlow
import com.a3322505a.guitarlearning.ui.theme.PixelGold
import com.a3322505a.guitarlearning.ui.theme.PixelError
import com.a3322505a.guitarlearning.ui.theme.PixelSuccess
import com.a3322505a.guitarlearning.ui.theme.StringMetal
import kotlin.math.pow

const val FIRST_FRET = 0
const val LAST_FRET = 12
private const val STRING_LABEL_WIDTH_DP = 42
private const val FRET_HEADER_HEIGHT_DP = 22
private const val OPEN_STRING_WIDTH_UNITS = 0.55f
internal const val FEEDBACK_MARKER_SIZE_FRACTION = 0.88f
internal const val FEEDBACK_MARKER_RING_FRACTION = 0.78f
internal const val FEEDBACK_MARKER_STROKE_DP = 4f
internal const val FEEDBACK_MARKER_CORE_DP = 8f

data class FretboardCell(
    val string: Int,
    val fret: Int,
)

enum class FretboardMarkerRole {
    TARGET,
    ANCHOR,
    CORRECT,
    INCORRECT,
    CONFIRMED,
}

data class FretboardMarker(
    val position: FretPosition,
    val role: FretboardMarkerRole,
)

enum class FretboardInteractionMode {
    Disabled,
    Enabled,
    CorrectionOnly,
}

/** One source of truth for drawing coordinates and pointer hit testing. */
object FretboardGeometry {
    fun visibleWidthUnits(
        firstFret: Int = FIRST_FRET,
        lastFret: Int = LAST_FRET,
    ): Double {
        requireValidRange(firstFret, lastFret)
        return (firstFret..lastFret).sumOf {
            fretWidthWeight(it).toDouble()
        }
    }

    fun fretLeftFraction(
        fret: Int,
        lastFret: Int = LAST_FRET,
        firstFret: Int = FIRST_FRET,
    ): Float {
        requireValidFret(fret, firstFret, lastFret)
        return fretBoundaryFraction(fret, firstFret, lastFret)
    }

    fun fretRightFraction(
        fret: Int,
        lastFret: Int = LAST_FRET,
        firstFret: Int = FIRST_FRET,
    ): Float {
        requireValidFret(fret, firstFret, lastFret)
        return fretBoundaryFraction(fret + 1, firstFret, lastFret)
    }

    fun fretCenterFraction(
        fret: Int,
        lastFret: Int = LAST_FRET,
        firstFret: Int = FIRST_FRET,
    ): Float = (
        fretLeftFraction(fret, lastFret, firstFret) +
            fretRightFraction(fret, lastFret, firstFret)
        ) / 2f

    fun stringCenterFraction(string: Int): Float =
        (rowIndexForString(string) + 0.5f) / 6f

    fun positionAt(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        lastFret: Int = LAST_FRET,
        firstFret: Int = FIRST_FRET,
    ): FretPosition? {
        requireValidRange(firstFret, lastFret)
        if (width <= 0f || height <= 0f || x < 0f || x > width || y < 0f || y > height) {
            return null
        }
        val xFraction = x / width
        val fret = (firstFret..lastFret).firstOrNull {
            xFraction < fretRightFraction(it, lastFret, firstFret)
        } ?: lastFret
        val string = ((y / height) * 6f).toInt().coerceIn(0, 5) + 1
        return GuitarCore.getFretPosition(string, fret)
    }

    private fun requireValidFret(fret: Int, firstFret: Int, lastFret: Int) {
        requireValidRange(firstFret, lastFret)
        require(fret in firstFret..lastFret) {
            "fret must be between $firstFret and $lastFret"
        }
    }

    private fun requireValidRange(firstFret: Int, lastFret: Int) {
        require(firstFret in FIRST_FRET..LAST_FRET) { "firstFret must be between 0 and 12" }
        require(lastFret in firstFret..LAST_FRET) {
            "lastFret must be between firstFret and 12"
        }
    }

    private fun fretBoundaryFraction(
        boundary: Int,
        firstFret: Int,
        lastFret: Int,
    ): Float {
        require(boundary in firstFret..(lastFret + 1)) {
            "boundary must be between $firstFret and ${lastFret + 1}"
        }
        val fretboardWidthUnits = visibleWidthUnits(firstFret, lastFret)
        val precedingWidthUnits = (firstFret until boundary)
            .sumOf { fretWidthWeight(it).toDouble() }
        return (precedingWidthUnits / fretboardWidthUnits).toFloat()
    }
}

fun rowIndexForString(string: Int): Int {
    require(string in 1..6) { "string must be between 1 and 6" }
    return string - 1
}

fun isHighlighted(
    selectedPosition: FretPosition?,
    string: Int,
    fret: Int,
): Boolean = selectedPosition?.string == string && selectedPosition.fret == fret

/** Cells highlighted for a target. The renderer uses this same list as the regression tests. */
fun highlightedCells(selectedPosition: FretPosition?): List<FretboardCell> =
    (1..6).flatMap { string ->
        (FIRST_FRET..LAST_FRET).mapNotNull { fret ->
            if (isHighlighted(selectedPosition, string, fret)) {
                FretboardCell(string = string, fret = fret)
            } else {
                null
            }
        }
    }

/** Relative width used by both the header and the board renderer. */
fun fretWidthWeight(fret: Int): Float {
    require(fret in FIRST_FRET..LAST_FRET) { "fret must be between 0 and 12" }
    if (fret == FIRST_FRET) return OPEN_STRING_WIDTH_UNITS
    val previousBoundary = 2.0.pow(-(fret - 1) / 12.0)
    val currentBoundary = 2.0.pow(-fret / 12.0)
    val firstFretWidth = 1.0 - 2.0.pow(-1.0 / 12.0)
    return ((previousBoundary - currentBoundary) / firstFretWidth).toFloat()
}

/** The horizontal start of an open-string region or fretted cell. */
fun fretLeftFraction(
    fret: Int,
    lastFret: Int = LAST_FRET,
    firstFret: Int = FIRST_FRET,
): Float = FretboardGeometry.fretLeftFraction(fret, lastFret, firstFret)

/** The horizontal end of an open-string region or fretted cell. */
fun fretRightFraction(
    fret: Int,
    lastFret: Int = LAST_FRET,
    firstFret: Int = FIRST_FRET,
): Float = FretboardGeometry.fretRightFraction(fret, lastFret, firstFret)

/** Horizontal center for the open-string target or a numbered fret. */
fun fretCenterFraction(
    fret: Int,
    lastFret: Int = LAST_FRET,
    firstFret: Int = FIRST_FRET,
): Float = FretboardGeometry.fretCenterFraction(fret, lastFret, firstFret)

/** The vertical center of a string row, expressed as a fraction of board height. */
fun stringCenterFraction(string: Int): Float = FretboardGeometry.stringCenterFraction(string)

/** Number of inlay dots rendered for a fret. */
fun markerCountForFret(fret: Int): Int = when (fret) {
    3, 5, 7, 9 -> 1
    12 -> 2
    else -> 0
}

@Composable
fun Fretboard(
    selectedPosition: FretPosition? = null,
    markers: List<FretboardMarker> = selectedPosition?.let {
        listOf(FretboardMarker(it, FretboardMarkerRole.TARGET))
    }.orEmpty(),
    interactionMode: FretboardInteractionMode = FretboardInteractionMode.Disabled,
    onPositionClick: ((FretPosition) -> Unit)? = null,
    markerScale: Float = 1f,
    showLabels: Boolean = true,
    firstFret: Int = FIRST_FRET,
    lastFret: Int = LAST_FRET,
    modifier: Modifier = Modifier,
) {
    require(firstFret in FIRST_FRET..LAST_FRET) { "firstFret must be between 0 and 12" }
    require(lastFret in firstFret..LAST_FRET) { "lastFret must be between firstFret and 12" }
    val selectedDescription = selectedPosition?.let {
        "，当前位置：" + it.string + "弦" + it.fret + "品"
    }.orEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .semantics {
                contentDescription = "真实六弦${firstFret}至${lastFret}品指板" + selectedDescription
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showLabels) FretHeader(firstFret, lastFret)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (showLabels) StringLabels()
            FretboardCanvas(
                markers = markers,
                interactionMode = interactionMode,
                onPositionClick = onPositionClick,
                markerScale = markerScale,
                firstFret = firstFret,
                lastFret = lastFret,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun FretHeader(firstFret: Int, lastFret: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(STRING_LABEL_WIDTH_DP.dp))
        (firstFret..lastFret).forEach { fret ->
            Box(
                modifier = Modifier
                    .weight(fretWidthWeight(fret))
                    .height(FRET_HEADER_HEIGHT_DP.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = fret.toString(),
                    fontSize = 11.sp,
                    fontWeight = if (fret == FIRST_FRET) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StringLabels() {
    Column(
        modifier = Modifier
            .width(STRING_LABEL_WIDTH_DP.dp)
            .fillMaxHeight(),
    ) {
        (1..6).forEach { string ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = string.toString() + "弦",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FretboardCanvas(
    markers: List<FretboardMarker>,
    interactionMode: FretboardInteractionMode,
    onPositionClick: ((FretPosition) -> Unit)?,
    markerScale: Float,
    firstFret: Int,
    lastFret: Int,
    modifier: Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clip(CutCornerShape(6.dp))
            .then(
                if (
                    interactionMode != FretboardInteractionMode.Disabled &&
                    onPositionClick != null
                ) {
                    Modifier.pointerInput(interactionMode, onPositionClick, firstFret, lastFret) {
                        detectTapGestures { offset ->
                            FretboardGeometry.positionAt(
                                x = offset.x,
                                y = offset.y,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                lastFret = lastFret,
                                firstFret = firstFret,
                            )?.let(onPositionClick)
                        }
                    }
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = "代码绘制的真实结构指板"
            },
    ) {
        drawRect(color = FretboardWood)
        drawPixelWoodGrain()
        drawOpenStringArea(firstFret, lastFret)
        drawMarkers(markers, markerScale, firstFret, lastFret)
        drawFretLines(firstFret, lastFret)
        drawInlayMarkers(firstFret, lastFret)
        drawStrings()
        drawRect(
            color = FretboardWoodDark,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

private fun DrawScope.drawPixelWoodGrain() {
    for (index in 0 until 12) {
        val y = size.height * (index + 1) / 13f
        val start = size.width * ((index * 17) % 29) / 100f
        val length = size.width * (0.18f + (index % 4) * 0.06f)
        drawRect(
            color = if (index % 2 == 0) {
                FretboardWoodLight.copy(alpha = 0.72f)
            } else {
                FretboardWoodDark.copy(alpha = 0.55f)
            },
            topLeft = androidx.compose.ui.geometry.Offset(start, y),
            size = androidx.compose.ui.geometry.Size(
                width = length.coerceAtMost(size.width - start),
                height = 1.dp.toPx(),
            ),
        )

        val secondStart = size.width * (0.58f + ((index * 7) % 16) / 100f)
        drawRect(
            color = FretboardWoodDark.copy(alpha = 0.38f),
            topLeft = androidx.compose.ui.geometry.Offset(secondStart, y + 3.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(
                width = (size.width * 0.14f).coerceAtMost(size.width - secondStart),
                height = 1.dp.toPx(),
            ),
        )
    }
}

private fun DrawScope.drawOpenStringArea(firstFret: Int, lastFret: Int) {
    if (firstFret != FIRST_FRET) return
    val nutX = size.width * fretRightFraction(0, lastFret, firstFret)
    drawRect(
        color = FretboardWoodDark.copy(alpha = 0.22f),
        size = androidx.compose.ui.geometry.Size(nutX, size.height),
    )
}

private fun DrawScope.drawMarkers(
    markers: List<FretboardMarker>,
    markerScale: Float,
    firstFret: Int,
    lastFret: Int,
) {
    markers.forEach { marker ->
        val cell = FretboardCell(marker.position.string, marker.position.fret)
        require(cell.fret in firstFret..lastFret) { "marker must be inside the visible fret range" }
        val left = size.width * fretLeftFraction(cell.fret, lastFret, firstFret)
        val right = size.width * fretRightFraction(cell.fret, lastFret, firstFret)
        val cellHeight = size.height / 6f
        val center = androidx.compose.ui.geometry.Offset(
            x = size.width * fretCenterFraction(cell.fret, lastFret, firstFret),
            y = cellHeight * (rowIndexForString(cell.string) + 0.5f),
        )
        val outerSize = minOf(right - left, cellHeight) *
            FEEDBACK_MARKER_SIZE_FRACTION * markerScale
        val outerTopLeft = androidx.compose.ui.geometry.Offset(
            center.x - outerSize / 2f,
            center.y - outerSize / 2f,
        )
        val markerColor = when (marker.role) {
            FretboardMarkerRole.TARGET -> PixelGlow
            FretboardMarkerRole.ANCHOR -> PixelGold
            FretboardMarkerRole.CORRECT, FretboardMarkerRole.CONFIRMED -> PixelSuccess
            FretboardMarkerRole.INCORRECT -> PixelError
        }
        drawRect(
            color = markerColor.copy(alpha = 0.22f),
            topLeft = outerTopLeft,
            size = androidx.compose.ui.geometry.Size(outerSize, outerSize),
        )
        val ringSize = outerSize * FEEDBACK_MARKER_RING_FRACTION
        drawRect(
            color = markerColor,
            topLeft = androidx.compose.ui.geometry.Offset(
                center.x - ringSize / 2f,
                center.y - ringSize / 2f,
            ),
            size = androidx.compose.ui.geometry.Size(ringSize, ringSize),
            style = Stroke(width = FEEDBACK_MARKER_STROKE_DP.dp.toPx()),
        )
        val coreSize = FEEDBACK_MARKER_CORE_DP.dp.toPx() * markerScale
        drawRect(
            color = NutIvory,
            topLeft = androidx.compose.ui.geometry.Offset(
                center.x - coreSize / 2f,
                center.y - coreSize / 2f,
            ),
            size = androidx.compose.ui.geometry.Size(coreSize, coreSize),
        )
    }
}

private fun DrawScope.drawFretLines(firstFret: Int, lastFret: Int) {
    val regularWidth = 1.dp.toPx()
    val nutWidth = 5.dp.toPx()
    for (fret in firstFret..lastFret) {
        val x = if (fret == lastFret) {
            size.width - regularWidth / 2f
        } else {
            size.width * fretRightFraction(fret, lastFret, firstFret)
        }
        val strokeWidth = if (firstFret == FIRST_FRET && fret == FIRST_FRET) {
            nutWidth
        } else {
            regularWidth
        }
        drawLine(
            color = FretboardWoodDark,
            start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x, size.height),
            strokeWidth = strokeWidth + 2.dp.toPx(),
        )
        drawLine(
            color = if (firstFret == FIRST_FRET && fret == FIRST_FRET) NutIvory else FretMetal,
            start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x, size.height),
            strokeWidth = strokeWidth,
        )
    }
}

private fun DrawScope.drawInlayMarkers(firstFret: Int, lastFret: Int) {
    for (fret in firstFret..lastFret) {
        val count = markerCountForFret(fret)
        if (count == 0) continue

        val centerX = size.width * fretCenterFraction(fret, lastFret, firstFret)
        val yPositions = if (count == 1) {
            listOf(size.height / 2f)
        } else {
            listOf(size.height * 0.36f, size.height * 0.64f)
        }
        yPositions.forEach { y ->
            val markerSize = 8.dp.toPx()
            drawRect(
                color = FretboardWoodDark,
                topLeft = androidx.compose.ui.geometry.Offset(
                    centerX - markerSize / 2f,
                    y - markerSize / 2f,
                ),
                size = androidx.compose.ui.geometry.Size(markerSize, markerSize),
            )
            val innerSize = 5.dp.toPx()
            drawRect(
                color = Inlay,
                topLeft = androidx.compose.ui.geometry.Offset(
                    centerX - innerSize / 2f,
                    y - innerSize / 2f,
                ),
                size = androidx.compose.ui.geometry.Size(innerSize, innerSize),
            )
        }
    }
}

private fun DrawScope.drawStrings() {
    (1..6).forEach { string ->
        val y = size.height * stringCenterFraction(string)
        val strokeWidth = (1.25f + (string - 1) * 0.65f).dp.toPx()
        drawLine(
            color = FretboardWoodDark.copy(alpha = 0.8f),
            start = androidx.compose.ui.geometry.Offset(0f, y + 1.dp.toPx()),
            end = androidx.compose.ui.geometry.Offset(size.width, y + 1.dp.toPx()),
            strokeWidth = strokeWidth + 1.dp.toPx(),
        )
        drawLine(
            color = StringMetal,
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(size.width, y),
            strokeWidth = strokeWidth,
        )
    }
}
