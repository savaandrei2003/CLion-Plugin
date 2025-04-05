package com.example.clionplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.cidr.lang.OCLanguage
import com.jetbrains.cidr.lang.psi.OCFunctionDeclaration

class CountCppFunctionsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val functions = PsiTreeUtil.collectElementsOfType(psiFile, OCFunctionDeclaration::class.java)
        if (functions.isEmpty()) {
            Messages.showMessageDialog(project, "Nu s-au gasit functii.", "Info", Messages.getInformationIcon())
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            functions.forEachIndexed { index, func ->
                val commentText = "// Aceasta este functia nr. ${index + 1}\n"
                val dummyFile = PsiFileFactory.getInstance(project).createFileFromText(
                    "dummy.cpp",
                    OCLanguage.getInstance(),
                    commentText
                )
                val comment = dummyFile.firstChild as? PsiComment ?: return@forEachIndexed
                psiFile.addBefore(comment, func)
            }
        }

        Messages.showMessageDialog(
            project,
            "Am adaugat comentarii pentru ${functions.size} functie/functii.",
            "Succes!",
            Messages.getInformationIcon()
        )
    }
}
