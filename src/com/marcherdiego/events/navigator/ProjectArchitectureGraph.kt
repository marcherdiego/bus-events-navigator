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
import com.marcherdiego.events.navigator.extensions.hasEdge
import com.marcherdiego.events.navigator.extensions.removeExtension
import com.marcherdiego.events.navigator.graph.layout.ExtendedCompactTreeLayout
import com.marcherdiego.events.navigator.graph.styles.GraphStyles
import com.mxgraph.model.mxCell
import com.mxgraph.swing.mxGraphComponent
import com.mxgraph.swing.view.mxICellEditor
import com.mxgraph.view.mxGraph
import java.util.EventObject
import javax.swing.BorderFactory
import javax.swing.JFrame

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

    private fun populateGraph(project: Project, parent: Any, rootVertex: mxCell, graph: mxGraph) {
        val vertices = mutableSetOf<Pair<mxCell, String>>()
        val activities = mutableSetOf<Pair<mxCell, String>>()
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
                                    val isPost = mvpComponents.second

                                    activities.addAll(findActivityReferences(graph, parent, rootVertex, element))
                                    vertices.addAll(activities)
                                    vertices.add(Pair(fileVertex, file.text))
                                    components.forEach { reference ->
                                        val referenceFile = reference.containingFile
                                        val referenceName = referenceFile.name.removeExtension()
                                        val referenceVertex = graph.addSingletonVertex(parent, referenceName)
                                        val (edgeName, from, to) = if (isPost) {
                                            Triple("$referenceName$fileName", referenceVertex, fileVertex)
                                        } else {
                                            Triple("$referenceName$fileName", fileVertex, referenceVertex)
                                        }
                                        System.err.println("populateGraph: ${from.value} -> ${to.value} (POST = $isPost)")
                                        graph.addSingletonEdge(parent, edgeName, from, to)

                                        // To determine whether of not we should reverse it, we should check the destination vertex
                                        // if it belongs to my MVP hierarchy, then we reverse it, if not, then we shouldn't
                                        if (nodeInHierarchy(graph, parent, to, activities)) {
                                            graph.addSingletonEdge(parent, edgeName.reversed(), to, from)
                                        }

                                        vertices.add(Pair(referenceVertex, referenceFile.text))
                                    }
                                }
                                super.visitElement(element)
                            }
                        })
                }
            }
        }
    }

    private fun nodeInHierarchy(graph: mxGraph, parent: Any,  to: mxCell, activities: MutableSet<Pair<mxCell, String>>): Boolean {
        return activities.any {
            graph.hasEdge(parent, it.first, to)
        }
    }

    private fun findActivityReferences(
        graph: mxGraph,
        parent: Any,
        root: mxCell,
        psiElement: PsiElement
    ): MutableSet<Pair<mxCell, String>> {
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
