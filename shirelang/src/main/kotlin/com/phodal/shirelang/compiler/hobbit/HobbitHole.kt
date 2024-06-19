package com.phodal.shirelang.compiler.hobbit

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.phodal.shirecore.action.ShireActionLocation
import com.phodal.shirecore.agent.InteractionType
import com.phodal.shirecore.middleware.PostCodeHandleContext
import com.phodal.shirecore.middleware.PostProcessor
import com.phodal.shirecore.middleware.select.SelectElementStrategy
import com.phodal.shirelang.compiler.FrontmatterParser
import com.phodal.shirelang.compiler.hobbit._base.Smials
import com.phodal.shirelang.compiler.hobbit.ast.FrontMatterType
import com.phodal.shirelang.compiler.hobbit.ast.PatternAction
import com.phodal.shirelang.compiler.hobbit.ast.RuleBasedPatternAction
import com.phodal.shirelang.compiler.patternaction.PatternActionTransform
import com.phodal.shirelang.psi.ShireFile

/**
 * - Normal: the action is a normal action
 * - Flow: each action can be a task in a flow, which will build a DAG
 */
open class HobbitHole(
    /**
     * Display name of the action.
     */
    val name: String,
    /**
     * Tips for the action.
     */
    val description: String,
    /**
     * The output of the action can be a file, a string, etc.
     */
    val interaction: InteractionType,
    /**
     * The location of the action, can [ShireActionLocation]
     */
    val actionLocation: ShireActionLocation,
    /**
     * The strategy to select the element to apply the action.
     * If not selected text, will according the element position to select the element block.
     * For example, if cursor in a function, select the function block.
     */
    val selectionStrategy: SelectElementStrategy = SelectElementStrategy.Blocked,

    /**
     * The list of rule files to apply for the action.
     */
    val fileContentFilters: List<String> = emptyList(),

    /**
     * The list of variables to apply for the action.
     */
    val variables: MutableMap<String, PatternActionTransform> = mutableMapOf(),

    /**
     * The rest of the data.
     */
    val restData: Map<String, FrontMatterType> = mutableMapOf(),

    /**
     * This code snippet declares a variable 'when_' of type List<VariableCondition> and initializes it with an empty list.
     * 'when_' is a list that stores VariableCondition objects.
     *
     * Which is used for: [IntentionAction.isAvailable], [DumbAwareAction.update] to check is show menu.
     */
    val when_: FrontMatterType.Expression? = null,

    /**
     * The list of rule files to apply for the action.
     */
    val preFilter: List<RuleBasedPatternAction> = emptyList(),
    /**
     * Post middleware actions, like
     * Logging, Metrics, CodeVerify, RunCode, ParseCode etc.
     *
     */
    val postProcess: List<PostProcessor> = emptyList(),
    /**
     * The list of actions that this action depends on.
     * We use it for Directed Acyclic Graph (DAG) to represent dependencies between actions.
     */
    val finalize: FrontMatterType.Expression? = null,
) : Smials {
    fun pickupElement() {
        this.selectionStrategy.select()
    }

    fun setupProcessor(project: Project, editor: Editor?, file: PsiFile?) {
        val language = file?.language?.id
        val context = PostCodeHandleContext(null, language, file)
        postProcess.forEach {
            it.setup(context)
        }
    }

    companion object {
        const val NAME = "name"
        const val ACTION_LOCATION = "actionLocation"
        const val INTERACTION = "interaction"
        const val STRATEGY_SELECTION = "selectionStrategy"
        const val POST_PROCESSOR = "postProcessors"
        private const val DESCRIPTION = "description"
        private const val FILENAME_RULES = "filenameRules"
        private const val VARIABLES = "variables"
        const val WHEN = "when"

        fun from(file: ShireFile): HobbitHole? {
            return FrontmatterParser.parse(file)
        }

        /**
         * For Code completion ,
         * todo: modify to map with description
         */
        fun keys(): Map<String, String> {
            return mapOf(
                NAME to "The display name of the action",
                DESCRIPTION to "The tips for the action",
                INTERACTION to "The output of the action can be a file, a string, etc.",
                ACTION_LOCATION to "The location of the action, can [ShireActionLocation]",
                STRATEGY_SELECTION to "The strategy to select the element to apply the action",
                POST_PROCESSOR to "The list of post processors",
            )
        }

        fun from(frontMatterMap: MutableMap<String, FrontMatterType>): HobbitHole {
            val name = frontMatterMap[NAME]?.value as? String ?: ""
            val description = frontMatterMap[DESCRIPTION]?.value as? String ?: ""
            val interaction = frontMatterMap[INTERACTION]?.value as? String ?: ""
            val actionLocation = frontMatterMap[ACTION_LOCATION]?.value as? String ?: ShireActionLocation.default()

            val data = mutableMapOf<String, FrontMatterType>()
            frontMatterMap.forEach { (key, value) ->
                if (key !in listOf(NAME, DESCRIPTION, INTERACTION, ACTION_LOCATION)) {
                    data[key] = value
                }
            }

            val selectionStrategy = frontMatterMap[STRATEGY_SELECTION]?.value as? String ?: ""

            val postProcessors: List<PostProcessor> = emptyList()
            frontMatterMap[POST_PROCESSOR]?.value?.let {
                PostProcessor.handler(it as String)
            }

            val filenameRules: MutableList<RuleBasedPatternAction> = mutableListOf()
            val filenamesMap = frontMatterMap[FILENAME_RULES] as? FrontMatterType.OBJECT
            filenamesMap?.let {
                (filenamesMap.value as? Map<String, FrontMatterType>)?.forEach { (key, value) ->
                    val text = key.removeSurrounding("\"")
                    PatternAction.from(value)?.let {
                        filenameRules.add(RuleBasedPatternAction(text, it.patternFuncs))
                    }
                }
            }

            val variables: MutableMap<String, PatternActionTransform> = mutableMapOf()
            val variablesMap = frontMatterMap[VARIABLES] as? FrontMatterType.OBJECT
            variablesMap?.let {
                (variablesMap.value as? Map<String, FrontMatterType>)?.forEach { (key, value) ->
                    val variable = key.removeSurrounding("\"")
                    // remove pattern sus
                    PatternAction.from(value)?.let {
                        val pattern = it.pattern.removeSurrounding("/")
                        variables[variable] = PatternActionTransform(variable, pattern, it.patternFuncs)
                    }
                }
            }

            val whenCondition = frontMatterMap[WHEN] as? FrontMatterType.Expression

            return HobbitHole(
                name,
                description,
                InteractionType.from(interaction),
                ShireActionLocation.from(actionLocation),
                preFilter = filenameRules,
                restData = data,
                selectionStrategy = SelectElementStrategy.fromString(selectionStrategy),
                postProcess = postProcessors,
                variables = variables,
                when_ = whenCondition
            )
        }
    }
}
