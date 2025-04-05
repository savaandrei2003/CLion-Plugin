package com.example.clionplugin

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.Messages
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.cidr.lang.psi.OCFunctionDeclaration
import com.jetbrains.cidr.lang.psi.visitors.OCVisitor
import com.jetbrains.cidr.lang.psi.OCStatement
import com.jetbrains.cidr.lang.psi.OCForStatement
import com.jetbrains.cidr.lang.psi.OCWhileStatement

class CyclomaticComplexityAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset) ?: return
        val function = PsiTreeUtil.getParentOfType(element, OCFunctionDeclaration::class.java) ?: return

        var estimatedIterations = 1

        function.accept(object : OCVisitor() {
            var depth = 0
            override fun visitForStatement(stmt: OCForStatement) {
                depth++
                estimatedIterations *= estimateIterations(stmt.text)
                super.visitForStatement(stmt)
                depth--
            }

            override fun visitWhileStatement(stmt: OCWhileStatement) {
                depth++
                estimatedIterations *= 100 // conservative guess
                super.visitWhileStatement(stmt)
                depth--
            }
        })

        Messages.showMessageDialog(
            project,
            "Estimated runtime complexity: ~O($estimatedIterations)",
            "Runtime Estimator",
            Messages.getInformationIcon()
        )
    }

    private fun estimateIterations(forText: String): Int {
        val regex = Regex("""for\s*\(([^;]+);([^;]+);([^)]+)\)""")
        val match = regex.find(forText) ?: return 1

        val condition = match.groupValues[2]
        val step = match.groupValues[3]

        return when {
            "1000" in condition || "i<1000" in condition -> 1000
            "100" in condition -> 100
            "10" in condition -> 10
            else -> 1
        }
    }
}
