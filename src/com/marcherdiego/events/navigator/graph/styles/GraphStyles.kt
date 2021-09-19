package com.marcherdiego.events.navigator.graph.styles

import com.mxgraph.util.mxConstants
import com.mxgraph.view.mxPerimeter

object GraphStyles {
    fun getNodeStyles(): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {
            put(mxConstants.STYLE_FILLCOLOR, "#222222")
            put(mxConstants.STYLE_FONTCOLOR, "#FFFFFF")
            put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter)
            put(mxConstants.STYLE_SPACING, 5)
        }
    }

    fun getModelNodeStyles(): Map<String, Any> {
        return getNodeStyles().toMutableMap().apply {
            put(mxConstants.STYLE_FILLCOLOR, "#004400")
        }
    }

    fun getViewNodeStyles(): Map<String, Any> {
        return getNodeStyles().toMutableMap().apply {
            put(mxConstants.STYLE_FILLCOLOR, "#000044")
        }
    }

    fun getPresenterNodeStyles(): Map<String, Any> {
        return getNodeStyles().toMutableMap().apply {
            put(mxConstants.STYLE_FILLCOLOR, "#440000")
            put(mxConstants.STYLE_FONTSTYLE, mxConstants.FONT_BOLD)
        }
    }

    fun getActivityNodeStyles(): Map<String, Any> {
        return getNodeStyles().toMutableMap().apply {
            put(mxConstants.STYLE_FILLCOLOR, "#444400")
            put(mxConstants.STYLE_FONTSTYLE, mxConstants.FONT_BOLD)
        }
    }

    fun getReversedArrowEdgeStyle(): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {
            put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_CONNECTOR)
            put(mxConstants.STYLE_DASHED, true)
            put(mxConstants.STYLE_ENDARROW, mxConstants.SHAPE_LINE)
            put(mxConstants.STYLE_STARTARROW, mxConstants.ARROW_CLASSIC)
            put(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_MIDDLE)
            put(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_CENTER)
            put(mxConstants.STYLE_STROKECOLOR, "#6482B9")
            put(mxConstants.STYLE_FONTCOLOR, "#446299")
        }
    }
}
