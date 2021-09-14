package com.marcherdiego.events.navigator.extensions

import com.mxgraph.model.mxCell
import com.mxgraph.view.mxGraph

fun mxGraph.addSingletonVertex(parent: Any, name: String): Any {
    return getChildVertices(parent).find { (it as mxCell).value == name } ?: insertVertex(parent, null, name, 20.0, 20.0, 80.0, 30.0)
}

fun mxGraph.addSingletonEdge(parent: Any, name: String, from: Any, to: Any): Any {
    return getChildEdges(parent).find { (it as mxCell).id == name } ?: insertEdge(parent, name, null, from, to)
}
