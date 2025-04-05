package com.example.clionplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.cidr.lang.psi.OCFunctionDeclaration
class CountCppFunctionsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        // Căutăm toate funcțiile în orice fel de fișier, nu doar OCFile
        val functions = PsiTreeUtil.collectElementsOfType(psiFile, OCFunctionDeclaration::class.java)
        val count = functions.size

        Messages.showMessageDialog(
            project,
            "Found $count function(s) in this C++ file.",
            "C++ Function Counter",
            Messages.getInformationIcon()
        )
    }
}
