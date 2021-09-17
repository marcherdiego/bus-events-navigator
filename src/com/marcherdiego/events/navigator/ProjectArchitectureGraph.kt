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
import com.marcherdiego.events.navigator.graph.layout.ExtendedCompactTreeLayout
import com.marcherdiego.events.navigator.graph.styles.GraphStyles
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
        graph.stylesheet.putCellStyle(Constants.NODE, GraphStyles.getNodeStyles())
        graph.stylesheet.putCellStyle(Constants.MODEL, GraphStyles.getModelNodeStyles())
        graph.stylesheet.putCellStyle(Constants.VIEW, GraphStyles.getViewNodeStyles())
        graph.stylesheet.putCellStyle(Constants.PRESENTER, GraphStyles.getPresenterNodeStyles())

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

            val appVertex = graph.addSingletonVertex(parent, "Application")

            populateGraph(project, parent, appVertex, graph)
            ExtendedCompactTreeLayout(graph).execute(parent)
            //mxHierarchicalLayout(graph).execute(parent)

            //val mostNegativeY = getMostNegativeCoordinateY(parent, graph)
            //val children = graph.getChildCells(parent)
            //graph.moveCells(children, 0.0, abs(mostNegativeY))
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

    private fun populateGraph(project: Project, parent: Any, rootVertex: mxCell, graph: mxGraph) {
        val vertices = mutableSetOf<Pair<mxCell, String>>()
        ModuleManager.getInstance(project).modules.forEach { module ->
            module.rootManager.contentRoots.forEach { root ->
                getAllSourceFiles(root).forEach { file ->
                    PsiManager
                        .getInstance(project)
                        .findFile(file)
                        ?.accept(object : PsiRecursiveElementWalkingVisitor() {
                            override fun visitElement(element: PsiElement) {
                                findMvpComponents(element)?.let { mvpComponents ->
                                    val file = element.containingFile
                                    val fileName = file.name.removeExtension()
                                    val fileVertex = graph.addSingletonVertex(parent, fileName)
                                    val components = mvpComponents.first
                                    val shouldRevert = mvpComponents.second

                                    vertices.addAll(findActivityReferences(graph, parent, rootVertex, element))
                                    vertices.add(Pair(fileVertex, file.text))
                                    components.forEach { reference ->
                                        val referenceFile = reference.containingFile
                                        val referenceName = referenceFile.name.removeExtension()
                                        val referenceVertex = graph.addSingletonVertex(parent, referenceName)
                                        val (edgeName, from, to) = if (shouldRevert) {
                                            Triple("$fileName$referenceName", fileVertex, referenceVertex)
                                        } else {
                                            Triple("$referenceName$fileName", referenceVertex, fileVertex)
                                        }
                                        //graph.addSingletonEdge(parent, edgeName, from, to)

                                        vertices.addAll(findActivityReferences(graph, parent, rootVertex, reference))
                                        vertices.add(Pair(referenceVertex, referenceFile.text))
                                    }
                                }
                                super.visitElement(element)
                            }
                        })
                }
            }
        }

        resolveInteractions(graph, parent, vertices)
    }

    private fun findActivityReferences(graph: mxGraph, parent: Any, root: mxCell, psiElement: PsiElement): Set<Pair<mxCell, String>> {
        val result = mutableSetOf<Pair<mxCell, String>>()
        val fileName = psiElement.containingFile.name.removeExtension()
        if (fileName.contains("Presenter").not()) {
            return result
        }
        val constructor = PsiUtils.getClassByName(fileName) ?: return result
        val activityElements = PsiUtils.findUsages(constructor)
        if (activityElements.isNotEmpty()) {
            val fileVertex = graph.addSingletonVertex(parent, fileName)
            activityElements.forEach { activityElement ->
                val activityFile = activityElement.containingFile
                val activityName = activityFile.name.removeExtension()
                val activityVertex = graph.addSingletonVertex(parent, activityName)
                val edgeName = "$fileName$activityName"
                graph.addSingletonEdge(parent, edgeName, activityVertex, fileVertex)
                result.add(Pair(activityVertex, activityFile.text))

                graph.addSingletonEdge(parent, "root-$activityName", root, activityVertex)
            }
        }
        return result
    }

    private fun resolveInteractions(graph: mxGraph, parent: Any, vertices: Set<Pair<mxCell, String>>) {
        vertices.forEach { outer ->
            getInteractionCandidates(outer, vertices)
                .forEach { inner ->
                    val outerVertex = outer.first
                    val outerName = outerVertex.value as String
                    val outerContent = outer.second

                    val innerVertex = inner.first
                    val innerName = innerVertex.value as String
                    if (outerContent.contains(innerName)) {
                        val edgeName = "$innerName$outerName"
                        graph.addSingletonEdge(parent, edgeName, outerVertex, innerVertex)
                    }
                }
        }
    }

    private fun getInteractionCandidates(element: Pair<mxCell, String>, vertices: Set<Pair<mxCell, String>>): List<Pair<mxCell, String>> {
        var result = vertices.filter {
            it != element
        }
        val elementName = (element.first.value as String)
        result = when {
            elementName.contains(Constants.MODEL, true) -> {
                result.filter {
                    (it.first.value as String).contains(Constants.PRESENTER, true)
                }
            }
            elementName.contains(Constants.VIEW, true) -> {
                result.filter {
                    (it.first.value as String).contains(Constants.PRESENTER, true)
                }
            }
            elementName.contains(Constants.PRESENTER, true) -> result
            elementName.contains(Constants.MODEL, true) -> listOf()
            else -> {
                result.filter {
                    (it.first.value as String).contains(Constants.MODEL, true).not() &&
                            (it.first.value as String).contains(Constants.VIEW, true).not() &&
                            (it.first.value as String).contains(Constants.PRESENTER, true).not()
                }
            }
        }
        return result
    }

    private fun findMvpComponents(psiElement: PsiElement): Pair<Set<PsiElement>, Boolean>? {
        return when {
            PsiUtils.isSubscriptionMethod(psiElement) -> {
                val subscriberMethod = PsiUtils.findAnnotatedMethod(psiElement) ?: return null
                val usages = PsiUtils.findMethodParameterUsages(subscriberMethod)
                if (usages.isEmpty()) {
                    null
                } else {
                    Pair(usages, true)
                }
            }
            PsiUtils.isEventBusPost(psiElement) -> {
                val constructor = PsiUtils.getClassByName(psiElement.text) ?: return null
                val usages = PsiUtils.findUsages(constructor)
                if (usages.isEmpty()) {
                    null
                } else {
                    Pair(usages, false)
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
