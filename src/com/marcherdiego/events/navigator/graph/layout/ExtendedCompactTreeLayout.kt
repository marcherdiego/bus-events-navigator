package com.marcherdiego.events.navigator.graph.layout

import com.mxgraph.layout.mxCompactTreeLayout
import com.mxgraph.model.mxICell
import com.mxgraph.view.mxGraph

class ExtendedCompactTreeLayout(graph: mxGraph?) : mxCompactTreeLayout(graph, false) {

    override fun execute(parent: Any?) {
        super.execute(parent)
        val vertexes = graph.getChildVertices(graph.defaultParent)
        for (i in vertexes.indices) {
            val parentCell = vertexes[i] as mxICell
            for (j in 0 until parentCell.edgeCount) {
                val edge = parentCell.getEdgeAt(j)
                if (edge.getTerminal(true) !== parentCell) {
                    continue
                }
                val parentBounds = getVertexBounds(parentCell)
                val edgePoints = edge.geometry.points ?: continue

                val outPort = edgePoints[0]
                val elbowPoint = edgePoints[1]
                if (outPort.x != parentBounds.centerX) {
                    outPort.x = parentBounds.centerX
                    elbowPoint.x = parentBounds.centerX
                }
            }
        }
    }
}
