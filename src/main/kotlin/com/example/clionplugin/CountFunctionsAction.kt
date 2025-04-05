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
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Font
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
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
        val outputFile = File(projectPath, "client.cpp")
        outputFile.writeText(psiFile.text)

        val response = sendFileToApi(outputFile)

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

        val jsonArray: JSONArray
        try {
            val jsonObject = JSONObject(serverResponse)
            jsonArray = jsonObject.getJSONArray("value")
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Eroare la parsarea JSON-ului: ${e.message}", "JSON Parsing Error")
            return
        }

        addInlayHints(editor, functions, jsonArray)
    }

    private fun sendFileToApi(file: File): String {
        val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
        val lineEnd = "\r\n"
        val url = URL("http://172.20.10.2:5000/run_source_file")

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.doInput = true
        connection.doOutput = true
        connection.useCaches = false

        DataOutputStream(connection.outputStream).use { outputStream ->
            outputStream.writeBytes("--$boundary$lineEnd")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"$lineEnd")
            outputStream.writeBytes("Content-Type: text/plain$lineEnd$lineEnd")
            file.inputStream().use { it.copyTo(outputStream) }
            outputStream.writeBytes(lineEnd)
            outputStream.writeBytes("--$boundary--$lineEnd")
        }

        val responseCode = connection.responseCode
        val responseText = connection.inputStream.bufferedReader().use { it.readText() }

        connection.disconnect()

        if (responseCode != 200) {
            throw IOException("Eroare la server: $responseCode")
        }

        return responseText
    }

    private fun getRequestTestConnection(): String {
//        val url = URL("https://api.chucknorris.io/jokes/random")
        val url = URL("http://172.20.10.2:5000/run_source_file")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connect()

        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()
        return response
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

private fun addInlayHints(editor: Editor, functions: Collection<OCFunctionDeclaration>, jsonArray: JSONArray) {
    val inlayModel = editor.inlayModel
    val document = editor.document



    if (jsonArray.length() != functions.size) return

    functions.forEachIndexed { index, func ->
        val jsonObject = jsonArray.getJSONObject(index)
        val cpuTime = jsonObject.optString("cpu_time", "N/A")
        val register = jsonObject.optString("start_address", "N/A")
        val hintText = "⏱ Functia ${index + 1}: CPU Time: $cpuTime | Register: $register"

        val line = document.getLineNumber(func.textOffset)
        val offset = document.getLineStartOffset(line)

        inlayModel.addBlockElement(
            offset,
            true,  // relatesToPrecedingText
            true,  // showAbove
            0,     // priority
            ExecutionTimeRenderer(hintText)
        )
    }
}