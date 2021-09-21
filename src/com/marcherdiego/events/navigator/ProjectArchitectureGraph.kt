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
import com.marcherdiego.events.navigator.extensions.readAction
import com.marcherdiego.events.navigator.extensions.removeExtension
import com.marcherdiego.events.navigator.graph.layout.ExtendedCompactTreeLayout
import com.marcherdiego.events.navigator.graph.styles.GraphStyles
import com.mxgraph.model.mxCell
import com.mxgraph.swing.mxGraphComponent
import com.mxgraph.swing.view.mxICellEditor
import com.mxgraph.view.mxGraph
import java.awt.Color
import java.awt.Toolkit
import java.util.EventObject
import javax.swing.JFrame

class ProjectArchitectureGraph(private var statusListener: StatusListener? = null) {
    private val validSourceExtensions = listOf("kt", "java")

    fun show(project: Project) {
        val graph = mxGraph()
        graph.model.beginUpdate()
        graph.isAutoSizeCells = true
        graph.stylesheet.putCellStyle(Constants.APPLICATION, GraphStyles.getApplicationNodeStyle())
        graph.stylesheet.putCellStyle(Constants.NODE, GraphStyles.getNodeStyles())
        graph.stylesheet.putCellStyle(Constants.MODEL, GraphStyles.getModelNodeStyles())
        graph.stylesheet.putCellStyle(Constants.VIEW, GraphStyles.getViewNodeStyles())
        graph.stylesheet.putCellStyle(Constants.PRESENTER, GraphStyles.getPresenterNodeStyles())

        graph.stylesheet.putCellStyle(Constants.EDGE, GraphStyles.getEdgeStyle())
        graph.stylesheet.putCellStyle(Constants.REVERSED_EDGE, GraphStyles.getReversedEdgeStyle())
        graph.stylesheet.putCellStyle(Constants.APPLICATION_EDGE, GraphStyles.getApplicationEdgeStyle())

        try {
            val parent = graph.defaultParent
            val appVertex = graph.addSingletonVertex(parent, "Application")
            appVertex.style = Constants.APPLICATION
            populateGraph(project, parent, appVertex, graph)
            ExtendedCompactTreeLayout(graph).execute(parent)
        } finally {
            graph.model.endUpdate()
        }
        val frame = JFrame()
        val size = Toolkit.getDefaultToolkit().screenSize
        frame.setSize(size.width, size.height)
        val graphComponent = mxGraphComponent(graph)
        graphComponent.viewport.isOpaque = true
        graphComponent.viewport.background = Color.decode(GraphStyles.BACKGROUND_COLOR)
        graphComponent.isConnectable = false
        graphComponent.isDragEnabled = false
        graphComponent.cellEditor = object : mxICellEditor {
            override fun getEditingCell() = null

            override fun startEditing(cell: Any?, trigger: EventObject?) {
            }

            override fun stopEditing(cancel: Boolean) {
            }
        }
        graphComponent.addMouseWheelListener {
            if (it.wheelRotation < 0) {
                graphComponent.zoomIn()
            } else if (it.wheelRotation > 0) {
                graphComponent.zoomOut()
            }
        }
        frame.contentPane.add(graphComponent)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }

    private fun populateGraph(project: Project, parent: Any, rootVertex: mxCell, graph: mxGraph) {
        try {
            val modules = ModuleManager.getInstance(project).modules
            modules.forEachIndexed { moduleIndex, module ->
                module.rootManager.contentRoots.forEach { root ->
                    val allSourceFiles = getAllSourceFiles(root)
                    val totalFiles = allSourceFiles.size
                    allSourceFiles.forEachIndexed { fileIndex, sourceFile ->
                        val psiFile = readAction {
                            PsiManager.getInstance(project).findFile(sourceFile)
                        }
                        psiFile?.accept(object : PsiRecursiveElementWalkingVisitor() {
                            override fun visitElement(element: PsiElement) {
                                statusListener?.notifyStatusUpdate(
                                    module.name,
                                    moduleIndex,
                                    modules.size,
                                    fileIndex.toFloat() / totalFiles.toFloat()
                                )
                                readAction {
                                    buildGraphTreeForComponent(parent, rootVertex, graph, element)
                                    super.visitElement(element)
                                }
                            }
                        })
                    }
                }
            }

            cleanUpUnusedNodes(parent, graph)
            statusListener?.onCompleted()
        } catch (e: Exception) {
            statusListener?.onFailed()
        }
    }

    private fun cleanUpUnusedNodes(parent: Any, graph: mxGraph) {
        val unusedNodes = graph
            .getChildVertices(parent)
            .filter {
                (it as mxCell).edgeCount == 0
            }
        graph.removeCells(unusedNodes.toTypedArray())
    }

    private fun buildGraphTreeForComponent(parent: Any, rootVertex: mxCell, graph: mxGraph, element: PsiElement) {
        findMvpComponents(element)?.let { mvpComponents ->
            val file = element.containingFile
            val fileName = file.name.removeExtension()
            val fileVertex = graph.addSingletonVertex(parent, fileName)
            val components = mvpComponents.first
            val isPost = mvpComponents.second

            findActivityReferences(graph, parent, rootVertex, element)
            components.forEach { reference ->
                val referenceFile = reference.containingFile
                val referenceName = referenceFile.name.removeExtension()
                val referenceVertex = graph.addSingletonVertex(parent, referenceName)
                val (from, to) = if (isPost) {
                    Pair(referenceVertex, fileVertex)
                } else {
                    Pair(fileVertex, referenceVertex)
                }
                graph.addSingletonEdge(parent, "${from.value}${to.value}", from, to, true)
            }
        }
    }

    private fun findActivityReferences(graph: mxGraph, parent: Any, root: mxCell, element: PsiElement) {
        val fileName = element.containingFile.name.removeExtension()
        if (fileName.contains(Constants.PRESENTER, true).not()) {
            return
        }
        val constructor = PsiUtils.getClassByName(fileName) ?: return
        val activityElements = PsiUtils.findUsages(constructor)
        if (activityElements.isNotEmpty()) {
            val fileVertex = graph.addSingletonVertex(parent, fileName)
            activityElements.forEach { activityElement ->
                val activityFile = activityElement.containingFile
                val activityName = activityFile.name.removeExtension()
                val activityVertex = graph.addSingletonVertex(parent, activityName)
                graph.addSingletonEdge(parent, "$activityName$fileName", activityVertex, fileVertex)

                graph.addSingletonEdge(parent, "root-$activityName", root, activityVertex).apply {
                    style = Constants.APPLICATION_EDGE
                }
            }
        }
    }

    private fun findMvpComponents(psiElement: PsiElement): Pair<Set<PsiElement>, Boolean>? {
        return when {
            PsiUtils.isSubscriptionMethod(psiElement) -> {
                val subscriberMethod = PsiUtils.findAnnotatedMethod(psiElement) ?: return null
                val usages = PsiUtils.findMethodParameterUsages(subscriberMethod)
                Pair(usages, false)
            }
            PsiUtils.isEventBusPost(psiElement) -> {
                val constructor = PsiUtils.getClassByName(psiElement.text) ?: return null
                val usages = PsiUtils.findUsages(constructor)
                Pair(usages, true)
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

    interface StatusListener {
        fun notifyStatusUpdate(module: String, moduleIndex: Int, modulesCount: Int, completed: Float)
        fun onCompleted()
        fun onFailed()
    }
}
