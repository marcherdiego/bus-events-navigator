package com.marcherdiego.events.navigator

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.JavaPsiFacade
import com.intellij.util.Function
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import java.awt.event.MouseEvent

class StateArtLineMarker : LineMarkerProvider {

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        return if (isSubscriptionMethod(psiElement)) {
            LineMarkerInfo<PsiElement>(
                    psiElement,
                    psiElement.textRange,
                    icon,
                    Pass.LINE_MARKERS,
                    Function { "State Art" },
                    GutterIconNavigationHandler<PsiElement> { _: MouseEvent, psiElement: PsiElement ->
                        buildDependenciesGraph(psiElement)
                    },
                    GutterIconRenderer.Alignment.CENTER
            )
        } else {
            null
        }
    }

    private fun buildDependenciesGraph(psiElement: PsiElement) {
        val project = psiElement.project
        val annotationClass = JavaPsiFacade
                                      .getInstance(project)
                                      .findClass("org.greenrobot.eventbus.Subscribe", GlobalSearchScope.allScope(project)) ?: return
        val psiMethods = AnnotatedElementsSearch.searchPsiMethods(
                annotationClass,
                GlobalSearchScope.allScope(project)
        )
        val allMethods: Collection<PsiMethod> = psiMethods.findAll()

        allMethods.forEach {
            System.err.println("----------------------------")
            System.err.println("Name: ${it.name}")
            val parameter = it.parameterList.parameters.first()
            System.err.println("Parameter: ${parameter.type.canonicalText}")
            System.err.println("body {")
            it.body?.statements?.forEach {
                System.err.println(it.text)
            }
            System.err.println("}")
            System.err.println("----------------------------")
        }
    }

    private fun isSubscriptionMethod(psiElement: PsiElement): Boolean {
        val isLeaf = psiElement is LeafPsiElement
        val isSubscribe = psiElement.text == "Subscribe"
        val parentHasNoSiblings = psiElement.parent.prevSibling == null && psiElement.parent.nextSibling == null
        return isLeaf && parentHasNoSiblings && isSubscribe
    }

    override fun collectSlowLineMarkers(elements: MutableList<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
    }

    companion object {
        private val icon = IconLoader.getIcon("/icons/stateart.png")
    }
}
