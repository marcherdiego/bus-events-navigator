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
import com.marcherdiego.events.navigator.styles.GraphStyles
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout
import com.mxgraph.model.mxCell
import com.mxgraph.swing.mxGraphComponent
import com.mxgraph.swing.view.mxICellEditor
import com.mxgraph.view.mxGraph
import java.util.EventObject
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.SwingConstants
import kotlin.math.abs

object ProjectArchitectureGraph {
    private val validSourceExtensions = listOf("kt", "java")

    fun show(project: Project) {
        val graph = mxGraph()
        graph.model.beginUpdate()
        graph.isAutoSizeCells = true
        graph.stylesheet.putCellStyle(GraphStyles.NODE, GraphStyles.getNodeStyles())
        graph.stylesheet.putCellStyle(GraphStyles.MODEL, GraphStyles.getModelNodeStyles())
        graph.stylesheet.putCellStyle(GraphStyles.VIEW, GraphStyles.getViewNodeStyles())
        graph.stylesheet.putCellStyle(GraphStyles.PRESENTER, GraphStyles.getPresenterNodeStyles())

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
        graphComponent.border = BorderFactory.createEmptyBorder(30, 30, 30, 30)

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
        frame.setSize(1500, 1200)
        frame.contentPane.add(graphComponent)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }

    private fun getMostNegativeCoordinateY(parent: Any, graph: mxGraph): Double {
        return graph.getChildCells(parent).minOf {
            (it as mxCell).geometry?.y ?: 10000.0
        }
    }

    private fun populateGraph(project: Project, parent: Any, graph: mxGraph) {
        val activities = mutableSetOf<mxCell>()
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

                                activities.addAll(findActivityReferences(graph, parent, element))
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

                                    activities.addAll(findActivityReferences(graph, parent, reference))
                                }
                            }
                            super.visitElement(element)
                        }
                    })
                }
            }
        }

        resolveActivitiesInteractions(graph, parent, activities)
    }

    private fun findActivityReferences(graph: mxGraph, parent: Any, psiElement: PsiElement): Set<mxCell> {
        val fileName = psiElement.containingFile.name.removeExtension()
        if (fileName.contains("Presenter").not()) {
            return setOf()
        }
        val constructor = PsiUtils.getClassByName(fileName) ?: return setOf()
        val activityElements = PsiUtils.findUsages(constructor)
        val activities = mutableSetOf<mxCell>()
        if (activityElements.isNotEmpty()) {
            val fileVertex = graph.addSingletonVertex(parent, fileName)
            activityElements.forEach { activityElement ->
                val referenceName = activityElement.containingFile.name.removeExtension()
                val referenceVertex = graph.addSingletonVertex(parent, referenceName)
                activities.add(referenceVertex)
                val edgeName = "$fileVertex$referenceVertex"
                graph.addSingletonEdge(parent, edgeName, fileVertex, referenceVertex)
            }
        }
        return activities
    }

    private fun resolveActivitiesInteractions(graph: mxGraph, parent: Any, activities: Set<mxCell>) {
        val v1 = activities.toList()[1]
        val v2 = activities.toList()[2]
        val edgeName1 = "$v1$v2"
        graph.addSingletonEdge(parent, edgeName1, v1, v2)

        val v3 = activities.toList()[1]
        val v4 = activities.toList()[3]
        val edgeName2 = "$v3$v4"
        graph.addSingletonEdge(parent, edgeName2, v3, v4)

        val v5 = activities.toList()[2]
        val v6 = activities.toList()[3]
        val edgeName3 = "$v5$v6"
        graph.addSingletonEdge(parent, edgeName3, v5, v6)

        val v7 = activities.toList()[3]
        val v8 = activities.toList()[4]
        val edgeName4 = "$v7$v8"
        graph.addSingletonEdge(parent, edgeName4, v7, v8)
    }

    private fun findMvpComponents(psiElement: PsiElement): Pair<Set<PsiElement>, Boolean>? {
        return when {
            PsiUtils.isSubscriptionMethod(psiElement) -> {
                val subscriberMethod = PsiUtils.findAnnotatedMethod(psiElement) ?: return null
                val usages = PsiUtils.findMethodParameterUsages(subscriberMethod)
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
