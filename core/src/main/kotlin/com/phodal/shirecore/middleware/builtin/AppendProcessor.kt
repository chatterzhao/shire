package com.phodal.shirecore.middleware.builtin

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.phodal.shirecore.middleware.post.PostProcessorType
import com.phodal.shirecore.middleware.post.PostProcessorContext
import com.phodal.shirecore.middleware.post.PostProcessor

class AppendProcessor : PostProcessor {
    override val processorName: String = PostProcessorType.Append.handleName
    override val description: String = "`append` will append the text to the generated text"

    override fun isApplicable(context: PostProcessorContext): Boolean = true

    override fun execute(
        project: Project,
        context: PostProcessorContext,
        console: ConsoleView?,
        args: List<Any>,
    ): Any {

        context.genText += args.map {
            if (it.toString().startsWith("$")) {
                context.compiledVariables[it.toString().substring(1)] ?: ""
            } else {
                it
            }
        }.joinToString(" ")

        return context.genText ?: ""
    }
}
