package com.marcherdiego.events.navigator.styles

import com.mxgraph.util.mxConstants
import com.mxgraph.view.mxPerimeter

object GraphStyles {
    const val NODE = "node"
    const val ACTIVITY = "activity"
    const val MODEL = "model"
    const val VIEW = "view"
    const val PRESENTER = "presenter"

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
}
