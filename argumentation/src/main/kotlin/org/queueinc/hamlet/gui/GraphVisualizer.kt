package org.queueinc.hamlet.gui

import edu.uci.ics.jung.algorithms.layout.KKLayout
import edu.uci.ics.jung.algorithms.layout.Layout
import edu.uci.ics.jung.graph.Graph
import edu.uci.ics.jung.graph.SparseMultigraph
import edu.uci.ics.jung.graph.util.EdgeType
import edu.uci.ics.jung.visualization.VisualizationViewer
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse
import edu.uci.ics.jung.visualization.control.ModalGraphMouse
import edu.uci.ics.jung.visualization.decorators.ToStringLabeller
import edu.uci.ics.jung.visualization.renderers.Renderer
import it.unibo.tuprolog.argumentation.core.mining.graph
import it.unibo.tuprolog.argumentation.core.model.Attack
import it.unibo.tuprolog.argumentation.core.model.LabelledArgument
import it.unibo.tuprolog.dsl.prolog
import it.unibo.tuprolog.solve.MutableSolver
import it.unibo.tuprolog.solve.classic.classic
import javafx.embed.swing.SwingNode
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

internal class GraphVisualizer {

    private val graphPane: JScrollPane = JScrollPane()
    private val classicTheoryPane: JScrollPane = JScrollPane()
    private val treeTheoryPane: JScrollPane = JScrollPane()
    val splitPane: JSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)

    private val next: JButton = JButton("Next").also { button ->
        button.addActionListener {
            this.selectedContext =
                if (this.selectedContext + 1 >= this.maxContext) this.maxContext else this.selectedContext + 1
            this.update()
        }
    }
    private val back: JButton = JButton("Back").also { button ->
        button.addActionListener {
            this.selectedContext =
                if (this.selectedContext - 1 <= this.minContext) this.minContext else this.selectedContext - 1
            this.update()
        }
    }
    private val context: JLabel = JLabel()

    private val minContext: Int = 0
    private var maxContext: Int = 0
    private var selectedContext: Int = 0

    private var mutableSolver: MutableSolver? = null

    init {

        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        buttonPanel.add(back)
        buttonPanel.add(next)
        buttonPanel.add(context)

        val tabbedPane = JTabbedPane()
        tabbedPane.addTab("Classic", classicTheoryPane)
        tabbedPane.addTab("Tree", treeTheoryPane)

        panel.add(tabbedPane)
        panel.add(buttonPanel)

        splitPane.add(panel)
        splitPane.add(graphPane)

        splitPane.isOneTouchExpandable = true
        splitPane.dividerLocation = 150
    }

    private fun update() {
        SwingUtilities.invokeLater {
            back.isEnabled = this.selectedContext > this.minContext
            next.isEnabled = this.selectedContext < this.maxContext
            context.text = this.selectedContext.toString()
        }
        mutableSolver?.also { solver ->
            try {
                val graph = solver.graph(this.selectedContext)
                SwingUtilities.invokeLater {
                    printGraph(this.graphPane, graph.labellings, graph.attacks)
                    printTheory(this.classicTheoryPane, this.treeTheoryPane, graph.labellings)
                }
            } catch (e: Exception) {
                this.clear()
            }
        } ?: clear()
        revalidate()
    }

    private fun clear() {
        SwingUtilities.invokeLater {
            this.graphPane.viewport.removeAll()
            this.classicTheoryPane.viewport.removeAll()
            this.treeTheoryPane.viewport.removeAll()
        }
    }

    private fun revalidate() {
        SwingUtilities.invokeLater {
            this.splitPane.repaint()
        }
    }
    companion object {

        @JvmStatic
        fun customTab() : GraphNode {
            val frame = GraphVisualizer()
            val swingNode = SwingNode()
            frame.splitPane.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    frame.revalidate()
                }
            })
            swingNode.content = frame.splitPane
            frame.update()
            return GraphNode(swingNode) { model ->
                frame.mutableSolver = MutableSolver.classic(
                    libraries = model.libraries
                )
                frame.selectedContext = prolog {
                    frame.mutableSolver!!.solve("context_active"(X))
                        .map { it.substitution[X]!!.asNumeric()!!.intValue.toInt() }
                        .first()
                }
                frame.maxContext = frame.selectedContext
                frame.update()
                model.graph(frame.selectedContext)
            }
        }

        @JvmStatic
        private fun buildGraph(arguments: List<LabelledArgument>, attacks: List<Attack>): Graph<String, String> {
            val graph: Graph<String, String> = SparseMultigraph()
            arguments.map { it.argument.identifier }
                .forEach(graph::addVertex)
            attacks.forEach { x ->
                graph.addEdge(
                    x.attacker.identifier + x.target.identifier,
                    x.attacker.identifier,
                    x.target.identifier,
                    EdgeType.DIRECTED
                )
            }
            return graph
        }

        @JvmStatic
        private fun printGraph(graphPane: JScrollPane, arguments: List<LabelledArgument>, attacks: List<Attack>) {
            val layout: Layout<String, String> = KKLayout(buildGraph(arguments, attacks))
            layout.size = Dimension(350, 300)
            val vv: VisualizationViewer<String, String> = VisualizationViewer(layout)
            vv.preferredSize = Dimension(350, 300)
            vv.renderContext.setVertexFillPaintTransformer { i ->
                when (arguments.first { x -> x.argument.identifier == i }.label) {
                    "in" -> Color.GREEN
                    "out" -> Color.RED
                    else -> Color.GRAY
                }
            }
            vv.renderContext.vertexLabelTransformer = ToStringLabeller()
            vv.renderer.vertexLabelRenderer.position = Renderer.VertexLabel.Position.AUTO
            val graphMouse: DefaultModalGraphMouse<String, String> = DefaultModalGraphMouse()
            graphMouse.setMode(ModalGraphMouse.Mode.PICKING)
            vv.graphMouse = graphMouse
            graphPane.viewport.view = vv
        }

        @JvmStatic
        private fun printTheory(classicTheoryPane: JScrollPane, treeTheoryPane: JScrollPane, arguments: List<LabelledArgument>) {
            val textArea = JTextArea()
            textArea.isEditable = false
            arguments.sortedBy { it.argument.identifier.drop(1).toInt() }
                .forEach { x -> textArea.append(x.argument.descriptor + "\n") }
            classicTheoryPane.viewport.view = textArea

            val textAreaTree = JTextPane()
            textAreaTree.isEditable = false
            textAreaTree.contentType = "text/html"
            textAreaTree.text = formatResolutionTree(arguments)
            treeTheoryPane.viewport.view = textAreaTree
        }

        @JvmStatic
        private fun formatResolutionTree(arguments: List<LabelledArgument>): String {
            fun tree(arg: LabelledArgument, arguments: List<LabelledArgument>): String =
                "<li>${arg.argument.descriptor} <b>[${arg.label.uppercase()}]</b></li>" +
                        arg.argument.supports.joinToString(separator = "") { sub ->
                            tree(
                                arguments.first { it.argument.identifier == sub.identifier },
                                arguments
                            )
                        }.let { if (it.isNotEmpty()) "<ul>$it</ul>" else it }

            return "<html><ul>" + arguments
                .sortedBy { it.argument.identifier.drop(1).toInt() }
                .joinToString(separator = "") { tree(it, arguments) } + "</ul></html>"
        }
    }
}