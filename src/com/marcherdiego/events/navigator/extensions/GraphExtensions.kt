package com.marcherdiego.events.navigator.extensions

import com.marcherdiego.events.navigator.Constants
import com.mxgraph.model.mxCell
import com.mxgraph.view.mxGraph

fun mxGraph.addSingletonVertex(parent: Any, name: String): mxCell {
    var resultVertex = getChildVertices(parent).find {
        (it as mxCell).value == name
    } as? mxCell
    if (resultVertex == null) {
        resultVertex = insertVertex(parent, null, name, 20.0, 20.0, 80.0, 30.0) as mxCell
    }
    resultVertex.style = getNodeStyle(name)
    updateCellSize(resultVertex)
    return resultVertex
}

fun mxGraph.addSingletonEdge(parent: Any, name: String, from: Any, to: Any, reversed: Boolean = false): mxCell {
    var resultEdge = getChildEdges(parent).find {
        (it as mxCell).id == name
    } as? mxCell
    if (resultEdge == null) {
        resultEdge = insertEdge(parent, name, null, from, to) as mxCell

        resultEdge.style = if (reversed) {
            Constants.REVERSED_EDGE
        } else {
            Constants.EDGE
        }
    }
    return resultEdge
}

private fun getNodeStyle(name: String): String {
    val nodeName = name.toLowerCase()
    return when {
        nodeName.contains(Constants.MODEL) -> Constants.MODEL
        nodeName.contains(Constants.VIEW) || nodeName.contains(Constants.ADAPTER) -> Constants.VIEW
        nodeName.contains(Constants.PRESENTER) -> Constants.PRESENTER
        else -> Constants.NODE
    }
}
