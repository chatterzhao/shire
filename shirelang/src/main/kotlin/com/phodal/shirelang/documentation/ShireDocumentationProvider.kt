package com.phodal.shirelang.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.phodal.shirelang.psi.ShireTypes
import com.phodal.shirecore.agent.CustomAgent
import com.phodal.shirecore.markdown.convertMarkdownToHtml
import com.phodal.shirelang.ShireLanguage
import com.phodal.shirelang.completion.dataprovider.BuiltinCommand
import com.phodal.shirelang.completion.dataprovider.CompositeVariableProvider

class ShireDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val project = element?.project ?: return null
        val markdownDoc = when (element.elementType) {
            ShireTypes.AGENT_ID -> {
                val agentConfigs = CustomAgent.loadFromProject(project).filter {
                    it.name == element.text
                }

                if (agentConfigs.isEmpty()) return null
                agentConfigs.joinToString("\n") { it.description }
            }

            ShireTypes.COMMAND_ID -> {
                val command = BuiltinCommand.all().find { it.commandName == element.text } ?: return null
                val example = BuiltinCommand.example(command)
                val lang = ShireLanguage.INSTANCE.displayName
                "${command.description}\nExample:\n```$lang\n$example\n```\n "
            }

            ShireTypes.VARIABLE_ID -> {
                CompositeVariableProvider.all().find { it.name == element.text }?.description
            }

            else -> {
                element.text
            }
        }

        return convertMarkdownToHtml(markdownDoc ?: "")
    }

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? = contextElement ?: file.findElementAt(targetOffset)
}
