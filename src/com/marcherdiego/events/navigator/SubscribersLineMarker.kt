package com.marcherdiego.events.navigator

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.util.Function
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.search.searches.MethodReferencesSearch
import java.awt.event.MouseEvent

class SubscribersLineMarker : LineMarkerProvider {

    private var initDone = false
    private lateinit var subscribeAnnotationClass: PsiClass
    private lateinit var javaPsiFacade: JavaPsiFacade
    private lateinit var allScope: GlobalSearchScope

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        init(psiElement)
        return if (isSubscriptionMethod(psiElement)) {
            LineMarkerInfo<PsiElement>(
                    psiElement,
                    psiElement.textRange,
                    icon,
                    Pass.LINE_MARKERS,
                    Function { "Usage graph" },
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
        val fileScope = GlobalSearchScope.fileScope(psiElement.containingFile)
        val psiMethods = AnnotatedElementsSearch.searchPsiMethods(subscribeAnnotationClass, fileScope)
        val fileText = psiElement.containingFile.text
        val elementLine = StringUtil.offsetToLineNumber(fileText, psiElement.textOffset)
        val subscriberMethod = psiMethods.findAll().firstOrNull {
            val methodLine = StringUtil.offsetToLineNumber(fileText, it.textOffset)
            methodLine in elementLine..elementLine + 1
        } ?: return
        System.err.println("Name: ${subscriberMethod.name}")
        val parameter = subscriberMethod.parameterList.parameters.first()
        val parameterClass = javaPsiFacade.findClass(parameter.type.canonicalText, allScope) ?: return
        parameterClass.constructors.forEach {
            MethodReferencesSearch.search(it, allScope, false).findAll().forEach {
                val element = it.element
                val referencingElement = (element.references.firstOrNull() ?: return).element
                if (psiElement.containingFile != referencingElement.containingFile) {
                    val fileName = referencingElement.containingFile.name
                    val referenceLine = StringUtil.offsetToLineNumber(element.containingFile.text, referencingElement.textOffset) + 1
                    System.err.println("MethodReferencesSearch: $fileName line: $referenceLine")
                }
            }
        }
        System.err.println("----------------------------")
    }

    private fun isSubscriptionMethod(psiElement: PsiElement): Boolean {
        val isLeaf = psiElement is LeafPsiElement
        val isSubscribe = psiElement.text == "Subscribe"
        val parentHasNoSiblings = psiElement.parent.prevSibling == null && psiElement.parent.nextSibling == null
        return isLeaf && parentHasNoSiblings && isSubscribe
    }

    override fun collectSlowLineMarkers(elements: MutableList<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
    }

    private fun init(psiElement: PsiElement) {
        if (initDone.not()) {
            initDone = true

            javaPsiFacade = JavaPsiFacade.getInstance(psiElement.project)
            allScope = GlobalSearchScope.allScope(psiElement.project)
            subscribeAnnotationClass = javaPsiFacade.findClass("org.greenrobot.eventbus.Subscribe", allScope) ?: return
        }
    }

    companion object {
        private val icon = IconLoader.getIcon("/icons/stateart.png")
    }
}
