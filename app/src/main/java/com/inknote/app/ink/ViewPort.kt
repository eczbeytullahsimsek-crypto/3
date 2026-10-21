package com.inknote.app.ink

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max

/**
 * ViewPort state manager that handles the canvas transformable state (zoom & pan)
 * completely decoupled from the stroke coordinate system.
 *
 * Stroke Coordinate System:
 * - Fixed Document space: width = [DOC_WIDTH] (1000f), height = [docHeight] (1000f / aspect).
 * - All vector strokes ([androidx.ink.strokes.Stroke]) and stroke points are stored and built
 *   once in Document space.
 *
 * Screen Coordinate System:
 * - Local view pixels: [viewWidth] x [viewHeight].
 *
 * Matrices:
 * - [strokeToViewMatrix]: Document -> View pixels. Used by [androidx.ink.rendering.android.canvas.CanvasStrokeRenderer]
 *   and hardware layers during draw passes.
 * - [viewToStrokeMatrix]: View pixels -> Document. Used by [androidx.ink.authoring.InProgressStrokesView]
 *   as motionEventToWorldTransform so stylus inputs are mapped to stroke coordinates with zero offset.
 */
class ViewPort(
    val docWidth: Float = DOC_WIDTH,
    var aspect: Float = 1f / 1.4142f
) {
    val docHeight: Float
        get() = docWidth / aspect.coerceAtLeast(0.1f)

    var viewWidth: Float = 0f
        private set
    var viewHeight: Float = 0f
        private set

    // Observable Compose state for reactive UI if needed
    var zoomScale: Float by mutableFloatStateOf(1.0f)
        private set
    var panX: Float by mutableFloatStateOf(0f)
        private set
    var panY: Float by mutableFloatStateOf(0f)
        private set

    // Dedicated hardware transformation matrices
    val strokeToViewMatrix: Matrix = Matrix()
    val viewToStrokeMatrix: Matrix = Matrix()

    private val tempPts = FloatArray(2)

    var onTransformChanged: (() -> Unit)? = null

    /**
     * Updates viewport dimensions on layout or container size changes.
     */
    fun setViewSize(width: Float, height: Float, newAspect: Float = aspect): Boolean {
        if (width <= 0f || height <= 0f) return false
        var changed = false
        if (viewWidth != width || viewHeight != height) {
            viewWidth = width
            viewHeight = height
            changed = true
        }
        if (aspect != newAspect) {
            aspect = newAspect
            changed = true
        }
        if (changed) {
            updateMatrices()
            clampPan()
            updateMatrices()
            onTransformChanged?.invoke()
        }
        return changed
    }

    /**
     * Sets zoom and pan offsets directly.
     */
    fun setZoomAndPan(newZoom: Float, newPanX: Float, newPanY: Float) {
        val clampedZoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        zoomScale = clampedZoom
        if (clampedZoom <= 1.02f) {
            panX = 0f
            panY = 0f
        } else {
            panX = newPanX
            panY = newPanY
            clampPan()
        }
        updateMatrices()
        onTransformChanged?.invoke()
    }

    /**
     * Applies incremental pinch-to-zoom and two-finger pan relative to a focus point on screen.
     */
    fun applyTransformDelta(
        zoomFactor: Float,
        focusViewX: Float,
        focusViewY: Float,
        panDeltaX: Float,
        panDeltaY: Float
    ) {
        val oldZoom = zoomScale
        val targetZoom = (oldZoom * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val actualFactor = targetZoom / oldZoom

        zoomScale = targetZoom
        if (targetZoom <= 1.02f) {
            panX = 0f
            panY = 0f
        } else {
            // Anchor zoom around focal point
            panX = focusViewX - (focusViewX - panX) * actualFactor + panDeltaX
            panY = focusViewY - (focusViewY - panY) * actualFactor + panDeltaY
            clampPan()
        }
        updateMatrices()
        onTransformChanged?.invoke()
    }

    /**
     * Resets zoom and pan back to 100% fit.
     */
    fun reset() {
        zoomScale = 1.0f
        panX = 0f
        panY = 0f
        updateMatrices()
        onTransformChanged?.invoke()
    }

    private fun clampPan() {
        if (viewWidth <= 0f || viewHeight <= 0f) return
        val baseScale = viewWidth / docWidth
        val currentScale = baseScale * zoomScale
        val renderedW = docWidth * currentScale
        val renderedH = docHeight * currentScale

        val maxPanX = max(0f, (renderedW - viewWidth) / 2f + viewWidth * 0.35f)
        val maxPanY = max(0f, (renderedH - viewHeight) / 2f + viewHeight * 0.35f)
        panX = panX.coerceIn(-maxPanX, maxPanX)
        panY = panY.coerceIn(-maxPanY, maxPanY)
    }

    private fun updateMatrices() {
        if (viewWidth <= 0f || docWidth <= 0f) {
            strokeToViewMatrix.reset()
            viewToStrokeMatrix.reset()
            return
        }
        val baseScale = viewWidth / docWidth
        val totalScale = baseScale * zoomScale

        strokeToViewMatrix.reset()
        strokeToViewMatrix.setScale(totalScale, totalScale)
        strokeToViewMatrix.postTranslate(panX, panY)

        if (!strokeToViewMatrix.invert(viewToStrokeMatrix)) {
            viewToStrokeMatrix.reset()
        }
    }

    // Coordinate conversions between View pixels and Document space
    fun viewToDoc(viewX: Float, viewY: Float): PointF {
        tempPts[0] = viewX
        tempPts[1] = viewY
        viewToStrokeMatrix.mapPoints(tempPts)
        return PointF(tempPts[0], tempPts[1])
    }

    fun docToView(docX: Float, docY: Float): PointF {
        tempPts[0] = docX
        tempPts[1] = docY
        strokeToViewMatrix.mapPoints(tempPts)
        return PointF(tempPts[0], tempPts[1])
    }

    fun mapRectToView(docRect: RectF, out: RectF) {
        strokeToViewMatrix.mapRect(out, docRect)
    }

    fun mapRectToDoc(viewRect: RectF, out: RectF) {
        viewToStrokeMatrix.mapRect(out, viewRect)
    }

    companion object {
        const val DOC_WIDTH = 1000f
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 4.0f
    }
}
