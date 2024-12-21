package com.phodal.shire.marketplace.ui

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.phodal.shire.ShireMainBundle
import com.phodal.shirecore.provider.shire.FileRunService
import com.phodal.shirecore.ui.ShireInputListener
import com.phodal.shirecore.ui.ShireInputSection
import com.phodal.shirecore.ui.ShireInputTrigger
import com.phodal.shirelang.actions.intention.ShireIntentionAction
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

@State(name = "MarketPlaceView", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class MarketplaceView(val project: Project) : Disposable {
    private var myToolWindowPanel: JPanel? = null

    private val shirePackageTableComponent = ShireMarketplaceTableView(project)

    init {
        val inputSection = ShireInputSection(project, this)
        inputSection.addListener(object : ShireInputListener {
            override fun onStop(component: ShireInputSection) {
                inputSection.showSendButton()
            }

            override fun onSubmit(component: ShireInputSection, trigger: ShireInputTrigger) {
                val prompt = component.text
                component.text = ""


                if (prompt.isEmpty() || prompt.isBlank()) {
                    component.showTooltip(ShireMainBundle.message("chat.input.tips"))
                    return
                }

                val createScratchFile = createShireFile(prompt)

                FileRunService.provider(project, createScratchFile!!)
                    ?.runFile(project, createScratchFile, null)
            }
        })
        val borderPanel = JPanel(BorderLayout())
        borderPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        borderPanel.add(inputSection, BorderLayout.CENTER)

        myToolWindowPanel = panel {
            row {
                cell(shirePackageTableComponent.mainPanel).align(Align.FILL)
            }.resizableRow()
            row {
                cell(borderPanel).align(Align.FILL)
            }
        }
    }

    private fun createShireFile(prompt: String): VirtualFile? {
        // wrapper Shire HobbitHole header
        val header = """
            |---
            |name: "shire-temp"
            |description: "Shire Temp File"
            |interaction: RightPanel
            |---
            |
        """.trimMargin()

        val content = header + prompt

        return ScratchRootType.getInstance().createScratchFile(
            project,
            "shire-temp.shire",
            Language.findLanguageByID("Shire"),
            content
        )
    }

    fun initToolWindow(toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(myToolWindowPanel, "Shire Marketplace", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun dispose() {
        // TODO("Not yet implemented")
    }
}