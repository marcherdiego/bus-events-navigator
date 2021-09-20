package com.marcherdiego.events.navigator.graph.component

import com.marcherdiego.events.navigator.graph.styles.GraphStyles
import com.mxgraph.swing.mxGraphComponent
import com.mxgraph.swing.view.mxICellEditor
import com.mxgraph.view.mxGraph
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.util.EventObject
import kotlin.math.abs

class GraphComponent(graph: mxGraph) : mxGraphComponent(graph), MouseListener, MouseMotionListener {
    private var mouseMovementStart: Point? = null

    init {
        graphControl.addMouseListener(this)
        graphControl.addMouseMotionListener(this)
        isConnectable = false
        viewport.isOpaque = true
        viewport.background = Color.decode(GraphStyles.BACKGROUND_COLOR)
        addMouseWheelListener {
            if (it.wheelRotation < 0) {
                zoomIn()
            } else if (it.wheelRotation > 0) {
                zoomOut()
            }
        }
        cellEditor = object : mxICellEditor {
            override fun getEditingCell() = null

            override fun startEditing(cell: Any?, trigger: EventObject?) {
            }

            override fun stopEditing(cancel: Boolean) {
            }
        }
    }

    override fun mouseClicked(e: MouseEvent?) {
    }

    override fun mousePressed(e: MouseEvent?) {
        if (e?.isConsumed == false && !e.isPopupTrigger) {
            mouseMovementStart = e.point
        }
    }

    override fun mouseReleased(e: MouseEvent?) {
        val start = mouseMovementStart
        if (e?.isConsumed == false && start != null) {
            val dx: Int = abs(start.x - e.x)
            val dy: Int = abs(start.y - e.y)
            if (isSignificant(dx.toDouble(), dy.toDouble())) {
                e.consume()
            }
        }

        mouseMovementStart = null
    }

    override fun mouseEntered(e: MouseEvent?) {
    }

    override fun mouseExited(e: MouseEvent?) {
    }

    override fun mouseDragged(e: MouseEvent?) {
        val start = mouseMovementStart
        if (e?.isConsumed == false && start != null) {
            val dx: Int = e.x - start.x
            val dy: Int = e.y - start.y
            val r = viewport.viewRect
            val right = r.x + (if (dx > 0) 0 else r.width) - dx
            val bottom = r.y + (if (dy > 0) 0 else r.height) - dy
            graphControl.scrollRectToVisible(Rectangle(right, bottom, 1, 1))
            e.consume()
        }
    }

    override fun mouseMoved(e: MouseEvent?) {
    }
}
