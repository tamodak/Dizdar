package org.tamodak.killit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

/**
 * The icon set, drawn by hand rather than taken from `material-icons`.
 *
 * Material's icons are filled glyphs with rounded terminals. Every other surface in this app is a
 * square, thick, unfilled outline, and mixing the two reads as two different applications sharing a
 * screen. These are stroked outlines with [StrokeCap.Square] and [StrokeJoin.Miter] — no rounding
 * anywhere — so they sit in the same visual language as the borders around them.
 *
 * It is also the cheaper option: `material-icons-core` is on the classpath for a handful of glyphs,
 * and each of these is a few path commands.
 *
 * ### Drawing conventions
 *
 * All are 24x24, matching the viewport every Material icon uses, so they can be sized with the
 * same `Modifier.size(...)` values without surprises. Stroke width is 2f by default; 2.5f for the
 * navigation chevrons, which need to hold up at 22dp, and 1.5f where a shape would otherwise fill
 * in at small sizes. Colour is [Color.Black] throughout and never appears on screen — `Icon`
 * replaces it with a tint, and these are only ever drawn through `Icon`.
 */
private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

private fun ImageVector.Builder.stroke(width: Float = 2f, block: PathBuilder.() -> Unit) = addPath(
    pathData = PathData(block),
    fill = null,
    stroke = SolidColor(Color.Black),
    strokeLineWidth = width,
    strokeLineCap = StrokeCap.Square,
    strokeLineJoin = StrokeJoin.Miter,
    strokeLineMiter = 4f,
)

private fun ImageVector.Builder.solid(block: PathBuilder.() -> Unit) = addPath(
    pathData = PathData(block),
    fill = SolidColor(Color.Black),
)

private fun PathBuilder.rect(x: Float, y: Float, w: Float, h: Float) {
    moveTo(x, y)
    lineTo(x + w, y)
    lineTo(x + w, y + h)
    lineTo(x, y + h)
    close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    close()
}

private fun PathBuilder.segment(x1: Float, y1: Float, x2: Float, y2: Float) {
    moveTo(x1, y1)
    lineTo(x2, y2)
}

object KillitIcons {

    val Shield: ImageVector = icon("Shield") {
        stroke {
            moveTo(12f, 2f)
            lineTo(20f, 5f)
            verticalLineTo(11f)
            curveTo(20f, 17f, 16.5f, 20.5f, 12f, 22f)
            curveTo(7.5f, 20.5f, 4f, 17f, 4f, 11f)
            verticalLineTo(5f)
            close()
        }
    }

    val Apps: ImageVector = icon("Apps") {
        stroke {
            rect(3f, 3f, 8f, 8f)
            rect(13f, 3f, 8f, 8f)
            rect(3f, 13f, 8f, 8f)
            rect(13f, 13f, 8f, 8f)
        }
        solid { rect(9f, 9f, 6f, 6f) }
    }

    val ArrowLeft: ImageVector = icon("ArrowLeft") {
        stroke(2.5f) {
            segment(19f, 12f, 5f, 12f)
            moveTo(11f, 5f)
            lineTo(5f, 12f)
            lineTo(11f, 19f)
        }
    }

    val ChevronRight: ImageVector = icon("ChevronRight") {
        stroke(2.5f) {
            moveTo(9f, 5f)
            lineTo(16f, 12f)
            lineTo(9f, 19f)
        }
    }

    val Computer: ImageVector = icon("Computer") {
        stroke {
            rect(2f, 4f, 20f, 16f)
            moveTo(6f, 9f)
            lineTo(10f, 12f)
            lineTo(6f, 15f)
            segment(12f, 16f, 16f, 16f)
        }
    }

    val Phone: ImageVector = icon("Phone") {
        stroke {
            rect(7f, 2f, 10f, 20f)
            segment(7f, 17f, 17f, 17f)
        }
    }

    val QrCode: ImageVector = icon("QrCode") {
        stroke {
            rect(3f, 3f, 7f, 7f)
            rect(14f, 3f, 7f, 7f)
            rect(3f, 14f, 7f, 7f)
            rect(14f, 14f, 3f, 3f)
            rect(18f, 18f, 3f, 3f)
            rect(14f, 18f, 3f, 1f)
            rect(18f, 14f, 1f, 3f)
        }
    }

    val QrCodeSolid: ImageVector = icon("QrCodeSolid") {
        solid {
            rect(2f, 2f, 6f, 6f)
            rect(16f, 2f, 6f, 6f)
            rect(2f, 16f, 6f, 6f)
            rect(10f, 2f, 2f, 2f)
            rect(14f, 10f, 2f, 2f)
            rect(10f, 14f, 2f, 2f)
            rect(18f, 14f, 2f, 2f)
            rect(10f, 18f, 2f, 2f)
            rect(18f, 18f, 2f, 2f)
            rect(14f, 18f, 2f, 2f)
        }
    }

    val Lock: ImageVector = icon("Lock") {
        stroke {
            rect(5f, 11f, 14f, 10f)
            moveTo(8f, 11f)
            verticalLineTo(7f)
            arcTo(4f, 4f, 0f, false, true, 16f, 7f)
        }
    }

    val Key: ImageVector = icon("Key") {
        stroke {
            circle(7f, 12f, 4.5f)
            segment(11.5f, 12f, 21f, 12f)
            segment(16.5f, 12f, 16.5f, 16.5f)
            segment(20f, 12f, 20f, 15f)
        }
    }

    val Keypad: ImageVector = icon("Keypad") {
        stroke { rect(4f, 2f, 16f, 20f) }
        solid {
            rect(7f, 5f, 2f, 2f)
            rect(11f, 5f, 2f, 2f)
            rect(15f, 5f, 2f, 2f)
            rect(7f, 9f, 2f, 2f)
            rect(11f, 9f, 2f, 2f)
            rect(15f, 9f, 2f, 2f)
            rect(7f, 13f, 2f, 2f)
            rect(11f, 13f, 2f, 2f)
            rect(15f, 13f, 2f, 2f)
            rect(11f, 17f, 2f, 2f)
        }
    }

    val Pattern: ImageVector = icon("Pattern") {
        stroke(1.5f) {
            moveTo(5f, 5f)
            lineTo(5f, 12f)
            lineTo(12f, 12f)
            lineTo(19f, 19f)
        }
        solid {
            rect(3.5f, 3.5f, 3f, 3f)
            rect(10.5f, 3.5f, 3f, 3f)
            rect(17.5f, 3.5f, 3f, 3f)
            rect(3.5f, 10.5f, 3f, 3f)
            rect(10.5f, 10.5f, 3f, 3f)
            rect(17.5f, 10.5f, 3f, 3f)
            rect(3.5f, 17.5f, 3f, 3f)
            rect(10.5f, 17.5f, 3f, 3f)
            rect(17.5f, 17.5f, 3f, 3f)
        }
    }

    val Search: ImageVector = icon("Search") {
        stroke {
            circle(10f, 10f, 7f)
            segment(21f, 21f, 15.5f, 15.5f)
        }
    }

    val Package: ImageVector = icon("Package") {
        stroke(1.5f) { rect(4f, 4f, 16f, 16f) }
    }

    val Check: ImageVector = icon("Check") {
        stroke(3f) {
            moveTo(4f, 12f)
            lineTo(9f, 18f)
            lineTo(20f, 6f)
        }
    }

    val Warning: ImageVector = icon("Warning") {
        stroke {
            moveTo(12f, 3f)
            lineTo(22f, 20f)
            lineTo(2f, 20f)
            close()
            segment(12f, 9f, 12f, 14f)
        }
        solid { rect(11f, 16f, 2f, 2f) }
    }

    val Devices: ImageVector = icon("Devices") {
        stroke {
            rect(2f, 3f, 9f, 18f)
            segment(2f, 17f, 11f, 17f)
            rect(14f, 8f, 8f, 13f)
            segment(14f, 17f, 22f, 17f)
        }
    }

    val Camera: ImageVector = icon("Camera") {
        stroke {
            rect(2f, 6f, 20f, 14f)
            rect(8f, 3f, 8f, 3f)
            circle(12f, 13f, 4f)
        }
    }
}
