package com.example.clionplugin


import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.application.ApplicationManager
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
        val outputFile = File(projectPath, "main.cpp")
        outputFile.writeText(psiFile.text)

        ProgressManager.getInstance().run(object : Task.Modal(project, "Se trimite fișierul către server...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Așteptăm răspunsul serverului..."
                try {
                    val response = sendFileToApi(outputFile)

                    ApplicationManager.getApplication().invokeLater {
                        handleServerResponse(response, editor, functions, project)
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Eroare: ${ex.message}", "Conexiune eșuată")
                    }
                }
            }
        })
    }

    private fun handleServerResponse(
        response: String,
        editor: Editor,
        functions: Collection<OCFunctionDeclaration>,
        project: Project
    ) {
        val jsonObject = try {
            JSONObject(response)
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Eroare la parsarea JSON-ului: ${e.message}", "JSON Parsing Error")
            return
        }

        val jsonArray = jsonObject.getJSONArray("value")
        val functionOrder = jsonObject.getJSONArray("function_order")

        addInlayHints(editor, functions, jsonArray, functionOrder, project)
    }

    private fun sendFileToApi(file: File): String {
        val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
        val lineEnd = "\r\n"
        val url = URL("http://172.20.10.2:5000/upload_source")

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

private fun addInlayHints(
    editor: Editor,
    functions: Collection<OCFunctionDeclaration>,
    jsonArray: JSONArray,
    functionOrder: JSONArray,
    project: Project
) {
    val document = editor.document
    val inlayModel = editor.inlayModel
    val functionMap = functions.associateBy { it.name }

    for (i in 0 until functionOrder.length()) {
        val funcName = functionOrder.getString(i)
        val funcDecl = functionMap[funcName] ?: continue
        val jsonObject = jsonArray.findObjectWithFunction(funcName) ?: continue

        val cpuTime = jsonObject.optString("cpu_time", "N/A")
        val register = jsonObject.optString("start_address", "N/A")
        val hintExtra = if (jsonObject.has("hint") && !jsonObject.isNull("hint")) {
            val hintValue = jsonObject.getString("hint")
            if (hintValue.isNotBlank()) " | Hint: $hintValue" else ""
        } else ""

        val hintText = "⏱ Functia ${i + 1}: CPU Time: $cpuTime | Register: $register$hintExtra"
        val offset = document.getLineStartOffset(document.getLineNumber(funcDecl.textOffset))

        inlayModel.getBlockElementsInRange(offset, offset + 1).forEach { inlay ->
            if (inlay.renderer is ExecutionTimeRenderer) {
                inlay.dispose()
            }
        }

        inlayModel.addBlockElement(
            offset,
            true,
            true,
            0,
            ExecutionTimeRenderer(hintText, project)
        )
    }
}

private fun JSONArray.findObjectWithFunction(functionName: String): JSONObject? {
    for (i in 0 until length()) {
        val obj = getJSONObject(i)
        if (obj.getString("function") == functionName) return obj
    }
    return null
}
