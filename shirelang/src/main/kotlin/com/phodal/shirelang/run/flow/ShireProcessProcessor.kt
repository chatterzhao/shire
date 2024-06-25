package com.phodal.shirelang.run.flow

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilBase
import com.intellij.openapi.fileEditor.FileEditorManager
import com.phodal.shire.llm.LlmProvider
import com.phodal.shirecore.ShirelangNotifications
import com.phodal.shirecore.middleware.select.SelectElementStrategy
import com.phodal.shirelang.ShireLanguage
import com.phodal.shirelang.compiler.ShireCompiler
import com.phodal.shirelang.psi.ShireFile
import com.phodal.shirelang.psi.ShireVisitor
import com.phodal.shirelang.run.ShireConsoleView
import com.phodal.shirelang.utils.Code
import kotlinx.coroutines.runBlocking

@Service(Service.Level.PROJECT)
class ShireProcessProcessor(val project: Project) {
    private val conversationService = project.getService(ShireConversationService::class.java)

    /**
     * This function takes a ShireFile as input and returns a list of PsiElements that are comments.
     * It iterates through the ShireFile and adds any comments it finds to the list.
     *
     * @param shireFile the ShireFile to search for comments
     * @return a list of PsiElements that are comments
     */
    private fun collectComments(shireFile: ShireFile): List<PsiComment> {
        val comments = mutableListOf<PsiComment>()
        shireFile.accept(object : ShireVisitor() {
            override fun visitComment(comment: PsiComment) {
                comments.add(comment)
            }
        })

        return comments
    }

    /**
     * Process the output of a script based on the exit code and flag comment.
     * If LLM returns a Shire code, execute it.
     * If the exit code is not 0, attempts to fix the script with LLM.
     * If the exit code is 0 and there is a flag comment, process it.
     *
     * Flag comment format:
     * ```shire
     * [flow]:flowable.shire, means next step is flowable.shire
     * ```
     *
     * @param output The output of the script
     * @param event The process event containing the exit code
     * @param scriptPath The path of the script file
     */
    fun process(output: String, event: ProcessEvent, scriptPath: String, consoleView: ShireConsoleView?) {
        conversationService.refreshIdeOutput(scriptPath, output)

        val code = Code.parse(conversationService.getLlmResponse(scriptPath))
        if (code.language == ShireLanguage.INSTANCE) {
            runInEdt {
                executeTask(ShireFile.fromString(project, code.text), consoleView)
            }
        }

        when {
            event.exitCode == 0 -> {
                val shireFile: ShireFile = runReadAction { ShireFile.lookup(project, scriptPath) } ?: return
                val firstComment = collectComments(shireFile).firstOrNull() ?: return
                if (firstComment.textRange.startOffset == 0) {
                    val text = firstComment.text
                    if (text.startsWith(ShireCompiler.FLOW_FALG)) {
                        val nextScript = text.substring(ShireCompiler.FLOW_FALG.length)
                        val newScript = ShireFile.lookup(project, nextScript) ?: return
                        this.executeTask(newScript, consoleView)
                    }
                }
            }

            event.exitCode != 0 -> {
                conversationService.retryScriptExecution(scriptPath, consoleView)
            }
        }
    }

    /**
     * This function is responsible for running a task with a new script.
     * @param newScript The new script to be run.
     */
    private fun executeTask(newScript: ShireFile, consoleView: ShireConsoleView?) {
        val shireCompiler = createCompiler(project, newScript)
        val result = shireCompiler.compile()
        if (result.shireOutput != "") {
            ShirelangNotifications.notify(project, result.shireOutput)
        }

        if (result.hasError) {
            if (consoleView == null) return

            runBlocking {
                LlmProvider.provider(project)?.stream(result.shireOutput, "Shirelang", true)
                    ?.collect {
                        consoleView.print(it, ConsoleViewContentType.NORMAL_OUTPUT)
                    }
            }
        } else {
            if (result.nextJob == null) return

            val nextJob = result.nextJob!!
            val nextResult = createCompiler(project, nextJob).compile()
            if (nextResult.shireOutput != "") {
                ShirelangNotifications.notify(project, nextResult.shireOutput)
            }
        }
    }

    private fun createCompiler(
        project: Project,
        shireFile: ShireFile,
    ): ShireCompiler {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val element: PsiElement? = editor?.caretModel?.currentCaret?.offset?.let {
            val psiFile = PsiUtilBase.getPsiFileInEditor(editor, project) ?: return@let null
            SelectElementStrategy.getElementAtOffset(psiFile, it)
        }

        return ShireCompiler(project, shireFile, editor, element)
    }
}