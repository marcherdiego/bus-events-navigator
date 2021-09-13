package com.marcherdiego.events.navigator.extensions

import org.graphstream.graph.Edge
import org.graphstream.graph.Node
import org.graphstream.graph.implementations.SingleGraph

fun SingleGraph.addSingletonNode(name: String): Node {
    return getNode(name) ?: addNode(name).apply {
        setAttribute("ui.label", name)
    }
}

fun SingleGraph.addSingletonEdge(name: String, from: String, to: String, directed: Boolean): Edge {
    return getEdge(name) ?: addEdge(name, from, to, directed).apply {
        System.err.println("Creating edge: $name from $from to $to")
    }
}
