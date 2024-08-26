package com.phodal.shirelang.compiler.hobbit.execute

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiManager
import com.phodal.shirecore.provider.http.HttpHandler
import com.phodal.shirecore.provider.http.HttpHandlerType
import com.phodal.shirecore.provider.shire.FileRunService
import com.phodal.shirelang.actions.ShireRunFileAction
import com.phodal.shirelang.utils.lookupFile

object ThreadProcessor {
    fun execute(
        myProject: Project, fileName: String, variables: Array<String>, variableTable: MutableMap<String, Any?>,
    ): String {
        val file = myProject.lookupFile(fileName) ?: return "File not found: $fileName"

        val filename = file.name.lowercase()
        val content = file.readText()

        // todo: waiting for execute
        // if ends with .cURL.sh, try call cURL service
        if (filename.endsWith(".curl.sh")) {
            val execute = HttpHandler.provide(HttpHandlerType.CURL)?.execute(myProject, content)
            if (execute != null) {
                return execute
            }
        }

        if (filename.endsWith(".shire")) {
            val executeResult = ShireRunFileAction.suspendExecuteFile(myProject, file.name, variables, variableTable)
            return executeResult ?: "No run service found"
        }

        val psiFile = PsiManager.getInstance(myProject).findFile(file) ?: return "File not found: $fileName"
        return FileRunService.provider(myProject, file)?.runFile(myProject, file, psiFile) ?: "No run service found"
    }
}
