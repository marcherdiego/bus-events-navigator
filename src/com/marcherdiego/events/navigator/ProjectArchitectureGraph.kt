package com.marcherdiego.events.navigator

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.marcherdiego.events.navigator.extensions.addSingletonEdge
import com.marcherdiego.events.navigator.extensions.addSingletonNode
import com.marcherdiego.events.navigator.extensions.getResourceAsString
import com.marcherdiego.events.navigator.extensions.removeExtension
import org.graphstream.graph.implementations.DefaultGraph
import org.graphstream.ui.view.Viewer

object ProjectArchitectureGraph {
    private val validSourceExtensions = listOf("kt", "java")

    fun show(project: Project) {
        System.setProperty("org.graphstream.ui", "swing")

        val graph = DefaultGraph("App Architecture")
        graph.setAttribute("ui.quality")
        graph.setAttribute("ui.antialias")
        graph.setAttribute("stylesheet", getResourceAsString("/css/graph.css"))

        PsiUtils.init(project)

        ModuleManager.getInstance(project).modules.forEach { module ->
            module.rootManager.contentRoots.forEach { root ->
                getAllSourceFiles(root).forEach { file ->
                    PsiManager.getInstance(project).findFile(file)?.accept(object : PsiRecursiveElementWalkingVisitor() {
                        override fun visitElement(element: PsiElement) {
                            findMvpComponents(element)?.let { mvpComponents ->
                                val fileName = element.containingFile.name.removeExtension()
                                graph.addSingletonNode(fileName)

                                val components = mvpComponents.first
                                val shouldRevert = mvpComponents.second
                                components.forEach { reference ->
                                    val referenceName = reference.containingFile.name.removeExtension()
                                    graph.addSingletonNode(referenceName)

                                    val (from, to) = if (shouldRevert) {
                                        Pair(fileName, referenceName)
                                    } else {
                                        Pair(referenceName, fileName)
                                    }
                                    val edgeName = "$from$to"
                                    graph.addSingletonEdge(edgeName, from, to, true)
                                }
                            }
                            super.visitElement(element)
                        }
                    })
                }
            }
        }

        val viewer = graph.display()
        viewer.closeFramePolicy = Viewer.CloseFramePolicy.HIDE_ONLY
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
