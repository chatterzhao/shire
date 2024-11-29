// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.phodal.shirecore.runner

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.ExecutionManager.Companion.EXECUTION_TOPIC
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.Filter
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.text.nullize
import com.phodal.shirecore.ShireCoreBundle
import com.phodal.shirecore.ShirelangNotifications
import com.phodal.shirecore.provider.shire.FileRunService
import java.io.OutputStream
import java.util.concurrent.CompletableFuture

open class RunServiceTask(
    private val project: Project,
    private val virtualFile: VirtualFile,
    private val testElement: PsiElement?,
    private val fileRunService: FileRunService,
    private val runner: ProgramRunner<*>? = null,
    private val future: CompletableFuture<String>? = null,
) : ConfigurationRunner, Task.Backgroundable(
    project, ShireCoreBundle.message("progress.run.task"), true
) {
    override fun runnerId() = runner?.runnerId ?: DefaultRunExecutor.EXECUTOR_ID

    override fun run(indicator: ProgressIndicator) {
//        if (future != null) {
//            runInBackgroundAndCollectToFuture()
//            return
//        }
        val runAndCollectTestResults = runAndCollectTestResults(indicator)
        future?.complete(runAndCollectTestResults?.status?.name ?: "Failed")
    }

    private fun runInBackgroundAndCollectToFuture() {
        val settings: RunnerAndConfigurationSettings =
            fileRunService.createRunSettings(project, virtualFile, testElement)
                ?: throw IllegalStateException("No run configuration found for file: ${virtualFile.path}")

        val executorInstance = DefaultRunExecutor.getRunExecutorInstance()
        val executionEnvironment = ExecutionEnvironmentBuilder
            .createOrNull(executorInstance, settings.configuration)
            ?.build()

        if (executionEnvironment == null) {
            throw IllegalStateException("Failed to create execution environment")
        }
        val hintDisposable = Disposer.newDisposable()
        val connection = ApplicationManager.getApplication().messageBus.connect(hintDisposable)
        connection.subscribe(EXECUTION_TOPIC, object : ExecutionListener {
            override fun processTerminated(
                executorId: String,
                env: ExecutionEnvironment,
                handler: ProcessHandler,
                exitCode: Int,
            ) {
                if (handler is ShireProcessHandler) {
                    future?.complete(exitCode.toString())
                } else {
                    future?.complete("")
                }

                super.processTerminated(executorId, env, handler, exitCode)
            }
        })

        ExecutionManager.getInstance(project).restartRunProfile(
            project,
            executorInstance,
            executionEnvironment.executionTarget,
            settings,
            null
        )

        return
    }

    /**
     * This function is responsible for executing a run configuration and returning the corresponding check result.
     * It is used within the test framework to run tests and report the results back to the user.
     *
     * @param indicator A progress indicator that is used to track the progress of the execution.
     * @return The check result of the executed run configuration, or `null` if no run configuration could be created.
     */
    private fun runAndCollectTestResults(indicator: ProgressIndicator?): RunnerResult? {
        val settings: RunnerAndConfigurationSettings? =
            fileRunService.createRunSettings(project, virtualFile, testElement)
        if (settings == null) {
            logger<RunServiceTask>().warn("No run configuration found for file: ${virtualFile.path}")
            return null
        }

        settings.isActivateToolWindowBeforeRun = false

        val testRoots = mutableListOf<SMTestProxy.SMRootTestProxy>()
        val testEventsListener = object : SMTRunnerEventsAdapter() {
            override fun onTestingStarted(testsRoot: SMTestProxy.SMRootTestProxy) {
                testRoots += testsRoot
            }
        }

        val runContext = createRunContext()
        executeRunConfigures(project, settings, runContext, testEventsListener, indicator)

        @Suppress("UnstableApiUsage")
        invokeAndWaitIfNeeded { }

        val testResults = testRoots.mapNotNull { it.toCheckResult() }
        if (testResults.isEmpty()) return RunnerResult.noTestsRun

        val firstFailure = testResults.firstOrNull { it.status != RunnerStatus.Solved }
        val result = firstFailure ?: testResults.first()
        return result
    }

    private fun SMTestProxy.SMRootTestProxy.toCheckResult(): RunnerResult? {
        if (finishedSuccessfully()) return RunnerResult(RunnerStatus.Solved, "CONGRATULATIONS")

        val failedChildren = collectChildren(object : Filter<SMTestProxy>() {
            override fun shouldAccept(test: SMTestProxy): Boolean = test.isLeaf && !test.finishedSuccessfully()
        })

        val firstFailedTest = failedChildren.firstOrNull()
        if (firstFailedTest == null) {
            ShirelangNotifications.warn(project, "Testing failed although no failed tests found")
            return null
        }

        val diff = firstFailedTest.diffViewerProvider?.let {
            CheckResultDiff(it.left, it.right, it.diffTitle)
        }
        val message = if (diff != null) getComparisonErrorMessage(firstFailedTest) else getErrorMessage(firstFailedTest)
        val details = firstFailedTest.stacktrace
        return RunnerResult(
            RunnerStatus.Failed,
            removeAttributes(fillWithIncorrect(message)),
            diff = diff,
            details = details
        )
    }

    private fun SMTestProxy.finishedSuccessfully(): Boolean {
        return !hasErrors() && (isPassed || isIgnored)
    }

    /**
     * Some testing frameworks add attributes to be shown in console (ex. Jest - ANSI color codes)
     * which are not supported in Task Description, so they need to be removed
     */
    private fun removeAttributes(text: String): String {
        val buffer = StringBuilder()
        AnsiEscapeDecoder().escapeText(text, ProcessOutputTypes.STDOUT) { chunk, _ ->
            buffer.append(chunk)
        }
        return buffer.toString()
    }

    /**
     * Returns message for test error that will be shown to a user in Check Result panel
     */
    @Suppress("UnstableApiUsage")
    @NlsSafe
    private fun getErrorMessage(node: SMTestProxy): String = node.errorMessage ?: "Execution failed"

    /**
     * Returns message for comparison error that will be shown to a user in Check Result panel
     */
    private fun getComparisonErrorMessage(node: SMTestProxy): String = getErrorMessage(node)

    private fun fillWithIncorrect(message: String): String =
        message.nullize(nullizeSpaces = true) ?: "Incorrect"
}