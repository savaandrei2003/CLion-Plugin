package com.example.clionplugin

import ExecutionTimeRenderer
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.jetbrains.cidr.lang.OCLanguage
import com.jetbrains.cidr.lang.psi.OCFunctionDeclaration
import org.json.JSONObject
import java.awt.Font
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.swing.JLabel

class CountCppFunctionsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val functions = PsiTreeUtil.collectElementsOfType(psiFile, OCFunctionDeclaration::class.java)

        if (functions.isEmpty()) {
            Messages.showMessageDialog(project, "Nu s-au gasit functii.", "Info", Messages.getInformationIcon())
            return
        }

        val projectPath = project.basePath ?: return
        val outputFile = File(projectPath, "main.txt")
        outputFile.writeText(psiFile.text)

        val serverResponse = getRequestTestConnection()
        if (serverResponse.isEmpty()) {
            Messages.showMessageDialog(
                project,
                "Nu s-a putut conecta la server.",
                "Eroare",
                Messages.getErrorIcon()
            )
            return
        }

        val jsonValue = try {
            JSONObject(serverResponse).getString("value")
        } catch (e: Exception) {
            "/* Nu s-a putut extrage textul din JSON. */"
        }

       // addPerformanceMarkers(project, psiFile, functions, jsonValue)
        addInlayHints(editor, functions, jsonValue)
    }

    private fun sendFileToApi(file: File): String {
        val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
        val lineEnd = "\r\n"
        val url = URL("http://localhost:8080/analyze")

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.doInput = true
        connection.doOutput = true
        connection.useCaches = false

        val outputStream = DataOutputStream(connection.outputStream)
        outputStream.writeBytes("--$boundary$lineEnd")
        outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"$lineEnd")
        outputStream.writeBytes("Content-Type: text/plain$lineEnd$lineEnd")

        file.inputStream().copyTo(outputStream)
        outputStream.writeBytes(lineEnd)
        outputStream.writeBytes("--$boundary--$lineEnd")
        outputStream.flush()
        outputStream.close()

        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()
        return response
    }

    private fun getRequestTestConnection(): String {
        val url = URL("https://api.chucknorris.io/jokes/random")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connect()

        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()
        return response
    }
}

private fun addFunctionMarkers(project: Project, psiFile: PsiFile, functions: Collection<OCFunctionDeclaration>) {
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
}

private fun addPerformanceMarkers(project: Project, psiFile: PsiFile, functions: Collection<OCFunctionDeclaration>, jsonValue: String) {
    WriteCommandAction.runWriteCommandAction(project) {
        functions.forEachIndexed { index, func ->
            val commentText = "// Functia ${index + 1}: $jsonValue\n"
            val dummyFile = PsiFileFactory.getInstance(project).createFileFromText(
                "dummy.cpp",
                OCLanguage.getInstance(),
                commentText
            )
            val comment = dummyFile.firstChild as? PsiComment ?: return@forEachIndexed
            psiFile.addBefore(comment, func)
        }
    }
}

private fun addInlayHints(editor: Editor, functions: Collection<OCFunctionDeclaration>, jsonValue: String) {
    val inlayModel = editor.inlayModel
    val document = editor.document

    functions.forEachIndexed { index, func ->
        val line = document.getLineNumber(func.textOffset)
        val offset = document.getLineStartOffset(line)

        inlayModel.addBlockElement(
            offset,
            true,  // relatesToPrecedingText
            true,  // showAbove
            0,     // priority
            ExecutionTimeRenderer("⏱ Functia ${index + 1}: $jsonValue")
        )
    }
}
