package com.inknote.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.inknote.app.data.PageTemplate
import kotlin.math.max

/** Kağıt şablonunu (nokta, çizgi, kareli, Cornell, ajanda, porte) sayfa genişliğine oranlı çizer. */
@Composable
fun PaperCanvas(template: PageTemplate, dark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val u = w * 5f / 210f                      // 5 mm
        val ink = if (dark) Color.White else Color(0xFF1C1B20)
        val faint = ink.copy(alpha = 0.14f)
        val hair = max(1f, w * 0.0011f)
        when (template) {
            PageTemplate.BLANK -> Unit
            PageTemplate.DOT -> {
                val pts = ArrayList<Offset>()
                var y = u * 2
                while (y < h - u) {
                    var x = u * 2
                    while (x < w - u) { pts.add(Offset(x, y)); x += u }
                    y += u
                }
                drawPoints(pts, PointMode.Points, ink.copy(alpha = 0.30f), strokeWidth = max(2f, w * 0.0030f), cap = StrokeCap.Round)
            }
            PageTemplate.RULED -> {
                val s = w * 6.5f / 210f
                var y = s * 3
                while (y < h) { drawLine(faint, Offset(0f, y), Offset(w, y), hair); y += s }
                drawLine(Color(0xFFBA1A1A).copy(alpha = .25f), Offset(u * 4, 0f), Offset(u * 4, h), hair)
            }
            PageTemplate.GRID -> {
                var x = 0f
                while (x <= w) { drawLine(faint, Offset(x, 0f), Offset(x, h), hair * 0.8f); x += u }
                var y = 0f
                while (y <= h) { drawLine(faint, Offset(0f, y), Offset(w, y), hair * 0.8f); y += u }
            }
            PageTemplate.CORNELL -> {
                val vx = w * 0.30f
                val hy = h * 0.82f
                drawLine(ink.copy(alpha = .35f), Offset(vx, u * 3), Offset(vx, hy), hair * 1.6f)
                drawLine(ink.copy(alpha = .35f), Offset(0f, hy), Offset(w, hy), hair * 1.6f)
                drawLine(ink.copy(alpha = .35f), Offset(0f, u * 3), Offset(w, u * 3), hair * 1.6f)
                val s = w * 7f / 210f
                var y = u * 3 + s
                while (y < hy) { drawLine(faint, Offset(vx, y), Offset(w, y), hair * 0.8f); y += s }
            }
            PageTemplate.WEEKLY -> {
                val m = w * 0.05f
                val gap = w * 0.025f
                val cw = (w - 2 * m - gap) / 2
                val ch = (h - 2 * m - 3 * gap) / 4
                for (r in 0 until 4) for (c in 0 until 2) {
                    drawRoundRect(ink.copy(alpha = .22f), Offset(m + c * (cw + gap), m + r * (ch + gap)), Size(cw, ch), CornerRadius(w * 0.012f), Stroke(hair * 1.4f))
                }
            }
            PageTemplate.STAFF -> {
                val sp = w * 2f / 210f * 1.2f
                val groupH = sp * 4
                var y = u * 4
                while (y + groupH < h - u * 2) {
                    for (i in 0 until 5) drawLine(ink.copy(alpha = .4f), Offset(u * 2, y + i * sp), Offset(w - u * 2, y + i * sp), hair)
                    y += groupH + sp * 5
                }
            }
        }
    }
}
