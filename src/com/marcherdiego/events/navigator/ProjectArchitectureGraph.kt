package com.marcherdiego.events.navigator

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.marcherdiego.events.navigator.extensions.addSingletonEdge
import com.marcherdiego.events.navigator.extensions.addSingletonVertex
import com.marcherdiego.events.navigator.extensions.removeExtension
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout
import com.mxgraph.model.mxCell
import com.mxgraph.swing.mxGraphComponent
import com.mxgraph.swing.view.mxICellEditor
import com.mxgraph.view.mxGraph
import java.util.EventObject
import javax.swing.JFrame
import javax.swing.SwingConstants
import kotlin.math.abs

object ProjectArchitectureGraph {
    private val validSourceExtensions = listOf("kt", "java")

    fun show(project: Project) {
        val graph = mxGraph()
        graph.model.beginUpdate()
        val graphComponent = mxGraphComponent(graph)
        graphComponent.isConnectable = false
        graphComponent.isDragEnabled = false
        graphComponent.cellEditor = object : mxICellEditor {
            override fun getEditingCell() = null

            override fun startEditing(cell: Any?, trigger: EventObject?) {
            }

            override fun stopEditing(cancel: Boolean) {
            }
        }
        try {
            val parent = graph.defaultParent
            populateGraph(project, parent, graph)
            mxHierarchicalLayout(graph, SwingConstants.SOUTH).execute(parent)

            val mostNegativeY = getMostNegativeCoordinateY(parent, graph)
            val children = graph.getChildCells(parent)
            graph.moveCells(children, 0.0, abs(mostNegativeY))
        } finally {
            // Updates the display
            graph.model.endUpdate()
        }
        val frame = JFrame()
        frame.setSize(1000, 800)
        frame.contentPane.add(graphComponent)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }

    private fun getMostNegativeCoordinateY(parent: Any, graph: mxGraph): Double {
        val children = graph.getChildCells(parent)
        var mostNegative = 10000.0
        children.forEach {
            val child = it as mxCell
            if (child.geometry != null) {
                if (child.geometry.y < mostNegative) {
                    mostNegative = child.geometry.y
                }
            }
        }
        return mostNegative
    }

    private fun populateGraph(project: Project, parent: Any, graph: mxGraph) {
        ModuleManager.getInstance(project).modules.forEach { module ->
            module.rootManager.contentRoots.forEach { root ->
                getAllSourceFiles(root).forEach { file ->
                    PsiManager.getInstance(project).findFile(file)?.accept(object : PsiRecursiveElementWalkingVisitor() {
                        override fun visitElement(element: PsiElement) {
                            findMvpComponents(element)?.let { mvpComponents ->
                                val fileName = element.containingFile.name.removeExtension()
                                val fileVertex = graph.addSingletonVertex(parent, fileName)
                                val components = mvpComponents.first
                                val shouldRevert = mvpComponents.second

                                components.forEach { reference ->
                                    val referenceName = reference.containingFile.name.removeExtension()
                                    val referenceVertex = graph.addSingletonVertex(parent, referenceName)
                                    val (from, to) = if (shouldRevert) {
                                        Pair(fileVertex, referenceVertex)
                                    } else {
                                        Pair(referenceVertex, fileVertex)
                                    }
                                    val edgeName = "$from$to"
                                    graph.addSingletonEdge(parent, edgeName, from, to)
                                }
                            }
                            super.visitElement(element)
                        }
                    })
                }
            }
        }
    }

    private fun findMvpComponents(psiElement: PsiElement): Pair<Set<PsiElement>, Boolean>? {
        return when {
            PsiUtils.isSubscriptionMethod(psiElement) -> {
                val subscriberMethod = PsiUtils.findAnnotatedMethod(psiElement) ?: return null
                val usages = PsiUtils.findUsages(subscriberMethod)
                if (usages.isEmpty()) {
                    null
                } else {
                    Pair(usages, false)
                }
            }
            PsiUtils.isEventBusPost(psiElement) -> {
                val constructor = PsiUtils.getClassByName(psiElement.text) ?: return null
                val usages = PsiUtils.findUsages(constructor)
                if (usages.isEmpty()) {
                    null
                } else {
                    Pair(usages, true)
                }
            }
            else -> null
        }
    }

    private fun getAllSourceFiles(file: VirtualFile): List<VirtualFile> {
        val children = mutableListOf<VirtualFile>()
        if (file.isDirectory.not() && file.extension?.toLowerCase() in validSourceExtensions) {
            children.add(file)
        }
        file.children.forEach { child ->
            children.addAll(getAllSourceFiles(child))
        }
        return children
    }
}
