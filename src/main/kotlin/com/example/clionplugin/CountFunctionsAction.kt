package com.example.clionplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.jetbrains.cidr.lang.psi.OCFile
import com.jetbrains.cidr.lang.psi.OCFunctionDeclaration

class CountCppFunctionsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? OCFile ?: return

        val functions = psiFile.children.filterIsInstance<OCFunctionDeclaration>()
        val count = functions.size

        Messages.showMessageDialog(
            project,
            "Found $count function(s) in this C++ file.",
            "C++ Function Counter",
            Messages.getInformationIcon()
        )
    }
}
