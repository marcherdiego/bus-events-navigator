package com.marcherdiego.events.navigator

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.ui.awt.RelativePoint
import com.marcherdiego.events.navigator.domain.Callers
import java.awt.event.MouseEvent
import javax.swing.Icon

class LineMarker : LineMarkerProvider {

    private var initDone = false
    private lateinit var subscribeAnnotationClass: PsiClass
    private lateinit var javaPsiFacade: JavaPsiFacade
    private lateinit var psiShortNamesCache: PsiShortNamesCache
    private lateinit var allScope: GlobalSearchScope

    private fun init(psiElement: PsiElement) {
        if (initDone.not()) {
            initDone = true

            val project = psiElement.project
            javaPsiFacade = JavaPsiFacade.getInstance(project)
            psiShortNamesCache = PsiShortNamesCache.getInstance(project)
            allScope = GlobalSearchScope.allScope(project)
            subscribeAnnotationClass = javaPsiFacade.findClass(SUBSCRIBE_CLASS_NAME, allScope) ?: return
        }
    }

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        init(psiElement)
        PsiUtils.init(psiElement.project)
        return when {
            PsiUtils.isSubscriptionMethod(psiElement) -> {
                val subscriberMethod = findAnnotatedMethod(psiElement) ?: return null
                val methodParameter = subscriberMethod.parameterList.parameters.firstOrNull() ?: return null
                val eventName = (methodParameter.type as? PsiClassReferenceType)?.name
                val tooltip = "Subscribes to $eventName event.\nClick here to navigate to the poster(s)"
                buildLineMarkerInfo(psiElement, subscribersIcon, tooltip) { ev, _ ->
                    val callers = buildDependenciesGraph(subscriberMethod) ?: return@buildLineMarkerInfo
                    val filter = FileFilter(callers.subscriberMethod.containingFile.virtualFile)
                    ShowUsagesAction(filter).startFindUsages(
                        callers.constructorReference,
                        RelativePoint(ev),
                        PsiEditorUtil.findEditor(callers.subscriberMethod),
                        Constants.MAX_USAGES
                    )
                }
            }
            PsiUtils.isEventBusPost(psiElement) -> {
                val eventName = psiElement.text
                val tooltip = "Posts $eventName event.\nClick here to navigate to its subscriber(s)"
                buildLineMarkerInfo(psiElement, postersIcon, tooltip) { ev, element ->
                    val elementName = element.text
                    val constructor = psiShortNamesCache.getClassesByName(elementName, allScope).firstOrNull() ?: return@buildLineMarkerInfo
                    val filter = FileFilter(constructor.containingFile.virtualFile)
                    ShowUsagesAction(filter).startFindUsages(
                        constructor,
                        RelativePoint(ev),
                        PsiEditorUtil.findEditor(constructor),
                        Constants.MAX_USAGES
                    )
                }
            }
            else -> null
        }
    }

    private fun buildLineMarkerInfo(
        psiElement: PsiElement,
        icon: Icon,
        tooltip: String,
        handler: (ev: MouseEvent, element: PsiElement) -> Unit
    ) = LineMarkerInfo(psiElement, psiElement.textRange, icon, { tooltip }, handler, GutterIconRenderer.Alignment.CENTER, { tooltip })

    private fun buildDependenciesGraph(subscriberMethod: PsiMethod): Callers? {
        val parameter = subscriberMethod.parameterList.parameters.first()
        val parameterClass = javaPsiFacade.findClass(parameter.type.canonicalText, allScope) ?: return null
        return Callers(subscriberMethod, parameterClass.constructors.first())
    }

    private fun findAnnotatedMethod(psiElement: PsiElement): PsiMethod? {
        val elementContainingFile = psiElement.containingFile
        val fileScope = GlobalSearchScope.fileScope(elementContainingFile)
        val psiMethods = AnnotatedElementsSearch.searchPsiMethods(subscribeAnnotationClass, fileScope)
        val fileText = elementContainingFile.text
        val elementLine = StringUtil.offsetToLineNumber(fileText, psiElement.textOffset)
        return psiMethods.findAll().firstOrNull {
            val methodLine = StringUtil.offsetToLineNumber(fileText, it.textOffset)
            methodLine in elementLine..elementLine + 1
        }
    }

    companion object {
        @JvmField
        val postersIcon = IconLoader.getIcon("/icons/posters.svg", LineMarker::class.java)
        @JvmField
        val subscribersIcon = IconLoader.getIcon("/icons/subscribers.svg", LineMarker::class.java)

        private const val SUBSCRIBE_CLASS_NAME = "org.greenrobot.eventbus.Subscribe"
    }
}
