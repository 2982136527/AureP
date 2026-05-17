package com.qiuhu.embyflow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun PixelCatPlaceholder(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFF1E4D0),
                        Color(0xFFE6D2B4),
                    ),
                ),
            ),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            val backgroundPixel = Color(0x14FFFFFF)
            val outline = Color(0xFF6C5544)
            val fur = Color(0xFFE6B98D)
            val ear = Color(0xFFF3C5B7)
            val eye = Color(0xFF2C221C)
            val nose = Color(0xFFB86E6A)
            val paw = Color(0xFFF4D4C5)
            val stripe = Color(0xFFC58F62)

            val spriteSize = min(size.width, size.height) * 0.72f
            val cell = spriteSize / 16f
            val startX = (size.width - cell * 16f) / 2f
            val startY = (size.height - cell * 16f) / 2f

            drawBackdropPattern(
                color = backgroundPixel,
                cell = cell,
                startX = startX,
                startY = startY,
            )

            drawCatShadow(cell = cell, startX = startX, startY = startY)
            drawCatOutline(color = outline, cell = cell, startX = startX, startY = startY)
            drawCatFill(color = fur, cell = cell, startX = startX, startY = startY)
            drawCatInnerEars(color = ear, cell = cell, startX = startX, startY = startY)
            drawCatStripes(color = stripe, cell = cell, startX = startX, startY = startY)
            drawCatEyes(color = eye, cell = cell, startX = startX, startY = startY)
            drawCatNose(color = nose, cell = cell, startX = startX, startY = startY)
            drawCatPaws(color = paw, cell = cell, startX = startX, startY = startY)
        }
    }
}

private fun DrawScope.drawBackdropPattern(
    color: Color,
    cell: Float,
    startX: Float,
    startY: Float,
) {
    for (row in 0 until 16) {
        for (column in 0 until 16) {
            if ((row + column) % 2 == 0) {
                drawCell(
                    x = column,
                    y = row,
                    color = color,
                    cell = cell,
                    startX = startX,
                    startY = startY,
                )
            }
        }
    }
}

private fun DrawScope.drawCatShadow(
    cell: Float,
    startX: Float,
    startY: Float,
) {
    val shadow = Color(0x18000000)
    val offset = cell * 0.55f

    drawCatRows(
        rows = outlineRows(),
        color = shadow,
        cell = cell,
        startX = startX + offset,
        startY = startY + offset,
    )
}

private fun DrawScope.drawCatOutline(
    color: Color,
    cell: Float,
    startX: Float,
    startY: Float,
) {
    drawCatRows(
        rows = outlineRows(),
        color = color,
        cell = cell,
        startX = startX,
        startY = startY,
    )
}

private fun DrawScope.drawCatFill(
    color: Color,
    cell: Float,
    startX: Float,
    startY: Float,
) {
    drawCatRows(
        rows = fillRows(),
        color = color,
        cell = cell,
        startX = startX,
        startY = startY,
    )
}

private fun DrawScope.drawCatInnerEars(
    color: Color,
    cell: Float,
    startX: Float,
    startY: Float,
) {
    drawCells(
        cells = listOf(
            5 to 3,
            10 to 3,
            5 to 4,
            6 to 4,
            9 to 4,
            10 to 4,
        ),
        color = color,
        cell = cell,
        startX = startX,
        startY = startY,
    )
}

private fun DrawScope.drawCatStripes(
    color: Color,
    cell: Float,
    startX: Float,
    startY: Float,
) {
    drawCells(
        cells = listOf(
            6 to 5,
            8 to 5,
            10 to 5,
            7 to 6,
            9 to 6,
        ),
        color = color,
        cell = cell,
        startX = startX,
        startY = startY,
    )
}

private fun DrawScope.drawCatEyes(
    color: Color,
    cell: Float,
    startX: Float,
    startY: Float,
) {
    drawCells(
        cells = listOf(
            5 to 8,
            6 to 8,
            9 to 8,
            10 to 8,
        ),
        color = color,
        cell = cell,
        startX = startX,
        startY = startY,
    )
}

private fun DrawScope.drawCatNose(
    color: Color,
    cell: Float,
    startX: Float,
    startY: Float,
) {
    drawCells(
        cells = listOf(
            7 to 10,
            8 to 10,
            7 to 11,
            8 to 11,
        ),
        color = color,
        cell = cell,
        startX = startX,
        startY = startY,
    )
}

private fun DrawScope.drawCatPaws(
    color: Color,
    cell: Float,
    startX: Float,
    startY: Float,
) {
    drawCells(
        cells = listOf(
            5 to 13,
            6 to 13,
            9 to 13,
            10 to 13,
        ),
        color = color,
        cell = cell,
        startX = startX,
        startY = startY,
    )
}

private fun outlineRows(): List<Pair<Int, IntRange>> = buildList {
    add(1 to (5..6))
    add(1 to (10..11))
    add(2 to (4..7))
    add(2 to (9..12))
    add(3 to (3..7))
    add(3 to (9..13))
    add(4 to (2..13))
    add(5 to (1..14))
    add(6 to (1..14))
    add(7 to (1..14))
    add(8 to (1..14))
    add(9 to (1..14))
    add(10 to (1..14))
    add(11 to (2..13))
    add(12 to (3..12))
    add(13 to (4..11))
}

private fun fillRows(): List<Pair<Int, IntRange>> = buildList {
    add(2 to (5..6))
    add(2 to (10..11))
    add(3 to (4..7))
    add(3 to (9..12))
    add(4 to (3..12))
    add(5 to (2..13))
    add(6 to (2..13))
    add(7 to (2..13))
    add(8 to (2..13))
    add(9 to (2..13))
    add(10 to (2..13))
    add(11 to (3..12))
    add(12 to (4..11))
    add(13 to (5..10))
}

private fun DrawScope.drawCatRows(
    rows: List<Pair<Int, IntRange>>,
    color: Color,
    cell: Float,
    startX: Float,
    startY: Float,
) {
    rows.forEach { (y, range) ->
        range.forEach { x ->
            drawCell(
                x = x,
                y = y,
                color = color,
                cell = cell,
                startX = startX,
                startY = startY,
            )
        }
    }
}

private fun DrawScope.drawCells(
    cells: List<Pair<Int, Int>>,
    color: Color,
    cell: Float,
    startX: Float,
    startY: Float,
) {
    cells.forEach { (x, y) ->
        drawCell(
            x = x,
            y = y,
            color = color,
            cell = cell,
            startX = startX,
            startY = startY,
        )
    }
}

private fun DrawScope.drawCell(
    x: Int,
    y: Int,
    color: Color,
    cell: Float,
    startX: Float,
    startY: Float,
) {
    drawRect(
        color = color,
        topLeft = Offset(
            x = startX + (x * cell),
            y = startY + (y * cell),
        ),
        size = Size(cell, cell),
    )
}
