package com.phodal.shire.marketplace

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.phodal.shire.ShireMainBundle
import com.phodal.shirecore.ShirelangNotifications
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*
import javax.swing.table.TableCellRenderer


class MarketplaceToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        project.getService(MarketPlaceView::class.java).initToolWindow(toolWindow)
    }

}

@State(name = "MarketPlaceView", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class MarketPlaceView(val project: Project) : Disposable {
    private var myToolWindowPanel: JPanel? = null

    private val shirePackageTableComponent = ShirePackageTableComponent()

    init {
        myToolWindowPanel = panel {
            row {
                cell(shirePackageTableComponent.mainPanel).align(Align.FILL)
            }.resizableRow()
        }
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


class ShirePackageTableComponent {
    val mainPanel: JPanel = JPanel(BorderLayout())
    private val columns = arrayOf(
        object : ColumnInfo<ShirePackage, String>(ShireMainBundle.message("marketplace.column.name")) {
            override fun valueOf(data: ShirePackage): String = data.name
        },
        object : ColumnInfo<ShirePackage, String>(ShireMainBundle.message("marketplace.column.description")) {
            override fun valueOf(data: ShirePackage): String = data.description
        },
        object : ColumnInfo<ShirePackage, String>(ShireMainBundle.message("marketplace.column.version")) {
            override fun valueOf(data: ShirePackage): String = data.version
        },
        object : ColumnInfo<ShirePackage, String>(ShireMainBundle.message("marketplace.column.author")) {
            override fun valueOf(data: ShirePackage): String = data.author
        },
        object : ColumnInfo<ShirePackage, JButton>(ShireMainBundle.message("marketplace.column.action")) {
            override fun getRenderer(item: ShirePackage): TableCellRenderer = ButtonRenderer()
            override fun valueOf(data: ShirePackage): JButton {
                val installButton = JButton(ShireMainBundle.message("marketplace.action.install"))
                installButton.addActionListener {
                    JOptionPane.showMessageDialog(mainPanel, "Button clicked")
                }

                return installButton
            }
        }
    )

    // Create a list to store the row data
    val dataList = listOf(
        ShirePackage("Plugin 1", "A useful plugin", "1.0", "Author A"),
        ShirePackage("Plugin 2", "Another great plugin", "2.1", "Author B"),
        ShirePackage("Plugin 3", "Yet another plugin", "3.0", "Author C")
    )

    init {
        val model = ListTableModel(columns, dataList)
        val tableView = TableView(model)
        val scrollPane = JBScrollPane(tableView)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
    }
}

data class ShirePackage(val name: String, val description: String, val version: String, val author: String)

class ButtonRenderer : JButton(), TableCellRenderer {
    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean,
        hasFocus: Boolean, row: Int, column: Int,
    ): Component = panel {
        row {
            cell(JButton("Install")).apply {
                addActionListener {
                    val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return@addActionListener
                    ShirelangNotifications.info(project, "Button clicked")
                }
            }
        }
    }
}

