package com.phodal.shirecore.provider.streaming

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

interface StreamingServiceProvider : Disposable {
    var name: String

    fun onStart(project: Project, userPrompt: String) {
        /// do nothing
    }

    /**
     * Receive streaming data
     */
    fun onStreaming(project: Project, flow: String, args: List<Any>)

    fun onDone(project: Project) {
        /// do nothing
    }

    companion object {
        val EP_NAME =
            com.intellij.openapi.extensions.ExtensionPointName.create<StreamingServiceProvider>("com.phodal.shireStreamingService")

        fun getStreamingService(name: String): StreamingServiceProvider? {
            return EP_NAME.extensions.firstOrNull { it.name == name }
        }

        fun all(): List<StreamingServiceProvider> {
            return EP_NAME.extensions.toList()
        }
    }
}