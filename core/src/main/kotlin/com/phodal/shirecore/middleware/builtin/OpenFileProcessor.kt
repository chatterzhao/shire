package com.phodal.shirecore.middleware.builtin

import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.phodal.shirecore.findFile
import com.phodal.shirecore.middleware.post.PostProcessorType
import com.phodal.shirecore.middleware.post.PostProcessorContext
import com.phodal.shirecore.middleware.post.PostProcessor


class OpenFileProcessor : PostProcessor {
    override val processorName: String = PostProcessorType.OpenFile.handleName
    override val description: String = "`openFile` will open the file in the editor"

    override fun isApplicable(context: PostProcessorContext): Boolean = true

    override fun execute(project: Project, context: PostProcessorContext, console: ConsoleView?, args: List<Any>): String {
        val firstArg = args.firstOrNull()
        val file = firstArg ?: context.pipeData["output"] ?: context.genText
        if (file !is VirtualFile) {
            if (file is String) {
                // check has multiple files
                val files = file.split("\n")
                runInEdt {
                    val findFiles = files.mapNotNull { project.findFile(it) }
                    findFiles.map {
                        console?.printHyperlink("$it", OpenFileHyperlinkInfo(project, it, -1, -1))
                        // new line
                        console?.print("\n", com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT)
                    }

                    findFiles.mapIndexed { index, it ->
                        val isFocus = index == findFiles.size - 1
                        FileEditorManager.getInstance(project).openFile(it, isFocus)
                    }
                }

                return ""
            } else {
                console?.print("No file to open\n", com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT)
            }

            return ""
        }

        runInEdt {
            FileEditorManager.getInstance(project).openFile(file, true)
        }

        return ""
    }
}

