package com.marcherdiego.events.navigator

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.marcherdiego.events.navigator.extensions.getResourceAsString
import org.graphstream.graph.implementations.SingleGraph
import org.graphstream.ui.view.Viewer

object ProjectArchitectureGraph {
    private val validSourceExtensions = listOf("kt", "java")

    fun show(project: Project) {
        System.setProperty("org.graphstream.ui", "swing")

        val graph = SingleGraph("Tutorial 1")
        graph.setAttribute("ui.quality")
        graph.setAttribute("ui.antialias")
        graph.setAttribute("stylesheet", getResourceAsString("/css/graph.css"))

        PsiUtils.init(project)
        ModuleManager.getInstance(project).modules.forEach { module ->
            module.rootManager.contentRoots.forEach { root ->
                getAllSourceFiles(root).forEach { file ->
                    PsiManager.getInstance(project).findFile(file)?.accept(object : PsiRecursiveElementWalkingVisitor() {
                        override fun visitElement(element: PsiElement) {
                            findMvpComponents(element)
                            super.visitElement(element)
                        }
                    })
                }
            }
        }

        val a = graph.addNode("A")
        val b = graph.addNode("B")
        val c = graph.addNode("C")
        graph.addEdge("AB", "A", "B", true)
        graph.addEdge("BC", "B", "C", true)
        graph.addEdge("CA", "C", "A", true)

        a.setAttribute("ui.label", "Node A")
        b.setAttribute("ui.label", "Node B")
        c.setAttribute("ui.label", "Node C")

        val viewer = graph.display()
        viewer.closeFramePolicy = Viewer.CloseFramePolicy.HIDE_ONLY
    }

    private fun findMvpComponents(psiElement: PsiElement) {
        when {
            PsiUtils.isSubscriptionMethod(psiElement) -> {
                System.err.println("Subscription found: ${psiElement.text} in ${psiElement.containingFile.name}")
                val subscriberMethod = PsiUtils.findAnnotatedMethod(psiElement) ?: return
            }
            PsiUtils.isEventBusPost(psiElement) -> {
                System.err.println("Post found: ${psiElement.text} in ${psiElement.containingFile.name}")
            }
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
