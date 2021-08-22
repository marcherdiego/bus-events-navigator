package com.marcherdiego.events.navigator;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiIdentifierImpl;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;

public class PsiUtils {

    public static PsiClass getClass(PsiType psiType) {
        if (psiType instanceof PsiClassType) {
            return ((PsiClassType) psiType).resolve();
        }
        return null;
    }

    public static boolean isEventBusReceiver(PsiElement psiElement) {
        if (psiElement instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) psiElement;
            PsiModifierList modifierList = method.getModifierList();
            for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
                if (safeEquals(psiAnnotation.getQualifiedName(), Constants.FUN_ANNOTATION)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isEventBusPost(PsiElement psiElement) {
        if (psiElement instanceof PsiMethodCallExpressionImpl && psiElement.getFirstChild() != null && psiElement.getFirstChild() instanceof PsiReferenceExpressionImpl) {
            PsiReferenceExpressionImpl all = (PsiReferenceExpressionImpl) psiElement.getFirstChild();
            if (all.getFirstChild() instanceof PsiMethodCallExpressionImpl && all.getLastChild() instanceof PsiIdentifierImpl) {
                PsiMethodCallExpressionImpl start = (PsiMethodCallExpressionImpl) all.getFirstChild();
                PsiIdentifierImpl post = (PsiIdentifierImpl) all.getLastChild();
                if ((safeEquals(post.getText(), Constants.FUN_NAME) || safeEquals(post.getText(), Constants.FUN_NAME2)) && safeEquals(start.getText(), Constants.FUN_START)) {
                    return true;
                }
            }
        }

        if (psiElement instanceof PsiCallExpression) {
            PsiCallExpression callExpression = (PsiCallExpression) psiElement;
            PsiMethod method = callExpression.resolveMethod();
            if (method != null) {
                String name = method.getName();
                PsiElement parent = method.getParent();
                if ((safeEquals(Constants.FUN_NAME, name) || safeEquals(Constants.FUN_NAME2, name)) && parent instanceof PsiClass) {
                    PsiClass implClass = (PsiClass) parent;
                    return isEventBusClass(implClass) || isSuperClassEventBus(implClass);
                }
            }
        }

        return false;
    }

    private static boolean isEventBusClass(PsiClass psiClass) {
        return safeEquals(psiClass.getName(), Constants.FUN_EVENT_CLASS_NAME);
    }

    private static boolean isSuperClassEventBus(PsiClass psiClass) {
        PsiClass[] supers = psiClass.getSupers();
        if (supers.length == 0) {
            return false;
        }
        for (PsiClass superClass : supers) {
            if (safeEquals(superClass.getName(), Constants.FUN_EVENT_CLASS_NAME)) {
                return true;
            }
        }
        return false;
    }

    private static boolean safeEquals(String obj, String value) {
        return obj != null && obj.equals(value);
    }
}
