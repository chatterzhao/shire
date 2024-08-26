package com.phodal.shirelang.actions

import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.phodal.shirelang.ShireActionStartupActivity
import com.phodal.shirelang.actions.base.DynamicShireActionConfig
import com.phodal.shirelang.psi.ShireFile
import com.phodal.shirelang.run.*
import org.jetbrains.annotations.NonNls
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities.invokeAndWait

class ShireRunFileAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        e.presentation.isEnabledAndVisible = file is ShireFile

        if (e.presentation.text.isNullOrBlank()) {
            e.presentation.text = "Run Shire file: ${file.name}"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE) as? ShireFile ?: return
        val project = file.project
        val config = DynamicShireActionConfig.from(file)

        val existingConfiguration = createRunConfig(e)
        executeShireFile(project, config, existingConfiguration)
    }

    companion object {
        const val ID: @NonNls String = "runShireFileAction"
        fun createRunConfig(e: AnActionEvent): RunnerAndConfigurationSettings? {
            val context = ConfigurationContext.getFromContext(e.dataContext, e.place)
            val configProducer = RunConfigurationProducer.getInstance(ShireRunConfigurationProducer::class.java)

            val existingConfiguration = configProducer.findExistingConfiguration(context)
            return existingConfiguration
        }

        fun executeShireFile(
            project: Project,
            config: DynamicShireActionConfig,
            runSettings: RunnerAndConfigurationSettings?,
            variables: Map<String, String> = mapOf(),
        ) {
            val settings = try {
                runSettings ?: RunManager.getInstance(project)
                    .createConfiguration(config.name, ShireConfigurationType::class.java)
            } catch (e: Exception) {
                logger<ShireRunFileAction>().error("Failed to create configuration", e)
                return
            }

            val runConfiguration = settings.configuration as ShireConfiguration
            runConfiguration.setScriptPath(config.shireFile.virtualFile.path)
            if (variables.isNotEmpty()) {
                runConfiguration.setVariables(variables)
            }

            val executorInstance = DefaultRunExecutor.getRunExecutorInstance()
            val executionEnvironment = ExecutionEnvironmentBuilder
                .createOrNull(executorInstance, runConfiguration)
                ?.build()

            if (executionEnvironment == null) {
                logger<ShireRunFileAction>().error("Failed to create execution environment")
                return
            }

            ExecutionManager.getInstance(project).restartRunProfile(executionEnvironment)
        }

        fun executeFile(
            myProject: Project,
            fileName: String,
            variableNames: Array<String>,
            variableTable: MutableMap<String, Any?>,
        ): Any {
            val variables: MutableMap<String, String> = mutableMapOf()
            for (i in variableNames.indices) {
                variables[variableNames[i]] = variableTable[variableNames[i]].toString() ?: ""
            }

            val file = runReadAction {
                ShireActionStartupActivity.obtainShireFiles(myProject).find {
                    it.name == fileName
                }
            }

            if (file == null) {
                throw RuntimeException("File $fileName not found")
            }

            ApplicationManager.getApplication().invokeLater({
                val config = DynamicShireActionConfig.from(file)
                executeShireFile(myProject, config, null, variables)
            }, ModalityState.NON_MODAL)

            return ""
        }

        fun suspendExecuteFile(
            project: Project,
            fileName: String,
            variableNames: Array<String>,
            variableTable: MutableMap<String, Any?>,
        ): String? {
            val variables: MutableMap<String, String> = mutableMapOf()
            for (i in variableNames.indices) {
                variables[variableNames[i]] = variableTable[variableNames[i]].toString() ?: ""
            }

            val file = runReadAction {
                ShireActionStartupActivity.obtainShireFiles(project).find {
                    it.name == fileName
                }
            }

            if (file == null) {
                throw RuntimeException("File $fileName not found")
            }

            val config = DynamicShireActionConfig.from(file)

            val settings = try {
                RunManager.getInstance(project)
                    .createConfiguration(config.name, ShireConfigurationType::class.java)
            } catch (e: Exception) {
                logger<ShireRunFileAction>().error("Failed to create configuration", e)
                return null
            }

            val runConfiguration = settings.configuration as ShireConfiguration
            runConfiguration.setScriptPath(config.shireFile.virtualFile.path)
            if (variables.isNotEmpty()) {
                runConfiguration.setVariables(variables)
            }

            val executorInstance = DefaultRunExecutor.getRunExecutorInstance()
            val executionEnvironment = ExecutionEnvironmentBuilder
                .createOrNull(executorInstance, runConfiguration)
                ?.build()

            if (executionEnvironment == null) {
                logger<ShireRunFileAction>().error("Failed to create execution environment")
                return null
            }

            val future = CompletableFuture<String>()
            val sb = StringBuilder()

            val processHandler = object: ProcessHandler() {
                override fun destroyProcessImpl() {
                    future.complete(sb.toString())
                    try {
                        notifyProcessDetached()
                    } finally {
                        notifyProcessTerminated(0)
                    }
                }

                override fun detachProcessImpl() {
                    future.complete(sb.toString())
                    try {
                        notifyProcessDetached()
                    } finally {
                        notifyProcessTerminated(0)
                    }
                }

                override fun detachIsDefault(): Boolean {
                    return false
                }

                override fun getProcessInput(): OutputStream? {
                    return null
                }
            }

            ProcessTerminatedListener.attach(processHandler)
            processHandler.addProcessListener(ShireProcessAdapter(sb, runConfiguration))

            var console: ShireConsoleView? = null
            invokeAndWait {
                val executionConsole = ConsoleViewImpl(project, true)
                console = ShireConsoleView(executionConsole)
            }

            console!!.addMessageFilter { line, _ ->
                sb.append(line)
                null
            }

            console!!.attachToProcess(processHandler)

            ExecutionManager.getInstance(project).restartRunProfile(
                project,
                executorInstance,
                executionEnvironment.executionTarget,
                settings,
                processHandler
            )

            return future.get()
        }
    }
}
