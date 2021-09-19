package com.marcherdiego.events.navigator.graph.styles

import com.mxgraph.util.mxConstants
import com.mxgraph.view.mxPerimeter

object GraphStyles {
    private const val DARK_GREY = "#101010"
    private const val LIGHT_GREY = "#202020"
    private const val RED = "#460202"
    private const val GREEN = "#023a05"
    private const val BLUE = "#022a46"

    fun getNodeStyles(): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {
            put(mxConstants.STYLE_FILLCOLOR, LIGHT_GREY)
            put(mxConstants.STYLE_FONTCOLOR, "#FAFAFA")
            put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter)
            put(mxConstants.STYLE_SPACING, 5)
        }
    }

    fun getApplicationNodeStyle(): Map<String, Any> {
        return getNodeStyles().toMutableMap().apply {
            put(mxConstants.STYLE_FILLCOLOR, DARK_GREY)
            put(mxConstants.STYLE_FONTSTYLE, mxConstants.FONT_BOLD)
        }
    }

    fun getModelNodeStyles(): Map<String, Any> {
        return getNodeStyles().toMutableMap().apply {
            put(mxConstants.STYLE_FILLCOLOR, RED)
        }
    }

    fun getViewNodeStyles(): Map<String, Any> {
        return getNodeStyles().toMutableMap().apply {
            put(mxConstants.STYLE_FILLCOLOR, GREEN)
        }
    }

    fun getPresenterNodeStyles(): Map<String, Any> {
        return getNodeStyles().toMutableMap().apply {
            put(mxConstants.STYLE_FILLCOLOR, BLUE)
            put(mxConstants.STYLE_FONTSTYLE, mxConstants.FONT_BOLD)
        }
    }

    fun getEdgeStyle(): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {
            put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_CONNECTOR)
            put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC)
            put(mxConstants.STYLE_STARTARROW, mxConstants.SHAPE_LINE)
            put(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_MIDDLE)
            put(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_CENTER)
            put(mxConstants.STYLE_STROKECOLOR, "#888888")
        }
    }

    fun getReversedEdgeStyle(): Map<String, Any> {
        return getEdgeStyle().toMutableMap().apply {
            put(mxConstants.STYLE_DASHED, true)
            put(mxConstants.STYLE_ENDARROW, mxConstants.SHAPE_LINE)
            put(mxConstants.STYLE_STARTARROW, mxConstants.ARROW_CLASSIC)
        }
    }
}
