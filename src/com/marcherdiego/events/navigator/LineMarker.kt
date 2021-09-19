package com.marcherdiego.events.navigator

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent
import javax.swing.Icon

class LineMarker : LineMarkerProvider {

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        PsiUtils.init(psiElement.project)
        return when {
            PsiUtils.isSubscriptionMethod(psiElement) -> {
                val subscriberMethod = PsiUtils.findAnnotatedMethod(psiElement)
                val methodParameter = subscriberMethod?.parameterList?.parameters?.firstOrNull() ?: return null
                val eventName = (methodParameter.type as? PsiClassReferenceType)?.name
                val tooltip = "Subscribes to $eventName event.\nClick here to navigate to the poster(s)"
                buildLineMarkerInfo(psiElement, subscribersIcon, tooltip) { ev, _ ->
                    val constructor = PsiUtils.getConstructor(subscriberMethod) ?: return@buildLineMarkerInfo
                    val filter = FileFilter(subscriberMethod.containingFile.virtualFile)
                    ShowUsagesAction(filter).startFindUsages(
                        constructor,
                        RelativePoint(ev),
                        PsiEditorUtil.findEditor(subscriberMethod),
                        Constants.MAX_USAGES
                    )
                }
            }
            PsiUtils.isEventBusPost(psiElement) -> {
                val eventName = psiElement.text
                val tooltip = "Posts $eventName event.\nClick here to navigate to its subscriber(s)"
                buildLineMarkerInfo(psiElement, postersIcon, tooltip) { ev, element ->
                    val elementName = element.text
                    val constructor = PsiUtils.getClassByName(elementName) ?: return@buildLineMarkerInfo
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

    companion object {
        @JvmField
        val postersIcon = IconLoader.getIcon("/icons/posters.svg", LineMarker::class.java)
        @JvmField
        val subscribersIcon = IconLoader.getIcon("/icons/subscribers.svg", LineMarker::class.java)
    }
}
