// Modified by senjucao for React Native Workbench 1.0.0.
package dev.gaphunter.reactnativecompanion.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.*
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import dev.gaphunter.reactnativecompanion.runner.*
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.*

class ReactNativeConsolePanel internal constructor(
    private val project: Project,
    private val createProbe: (com.intellij.execution.configurations.GeneralCommandLine) -> CapturingProcessHandler,
) : JPanel(BorderLayout()), Disposable {
    constructor(project: Project) : this(project, ::CapturingProcessHandler)
    @Volatile private var disposed = false
    private val preferences = PropertiesComponent.getInstance(project)
    private val language = "auto"
    private fun msg(key: String, vararg args: Any): String = WorkbenchBundle.message(language, key, *args)
    private val directoryKey = "dev.senjucao.workbench.app"
    private var app: NativeApp? = null
    private var scanning = true
    private var refreshing = false
    @Volatile private var generation = 0
    @Volatile private var deviceRefreshGeneration = 0
    private var profiles = emptyList<String>()
    private var env: String? = null
    private var probeDetails = ""
    private var updating = false
    private val activeProbeHandlers = ConcurrentHashMap.newKeySet<CapturingProcessHandler>()
    private val devices = JComboBox<DeviceTarget>()
    private val apps = JComboBox<NativeApp>()
    private val tabs = JTabbedPane().apply { tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT }
    private val emptyText = JLabel(msg("app.scanning"), SwingConstants.CENTER)
    private val choose = JButton(msg("app.choose.directory")).apply { addActionListener { chooseDirectory() } }
    private val empty = JPanel(BorderLayout()).apply {
        add(emptyText, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.CENTER)).apply { add(choose) }, BorderLayout.SOUTH)
    }
    private val body = JPanel(CardLayout()).apply { add(empty, "empty"); add(tabs, "sessions") }
    private val status = JLabel(msg("project.scanning"))
    private val envBadge = JLabel().apply { preferredSize = JBUI.size(150, 24) }
    private val details = iconButton(AllIcons.General.Information, msg("details.show")) {
        Messages.showInfoMessage(project, probeDetails, msg("details.title"))
    }
    private val sessions = mutableMapOf<String, Session>()
    private val toolbar = JPanel(BorderLayout(4, 0))
    private val actions = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
    private val selectors = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
    private val android = iconButton(AllIcons.Actions.Execute, msg("run.android")) { launch("Android", listOf("run-android")) }
    private val ios = iconButton(AllIcons.Toolbar.Xcode, msg("run.ios")) {
        launch("iOS", listOf("run-ios", "--terminal", ReactNativeCommandRunner.iosTerminal()))
    }
    private val metro = iconButton(AllIcons.Nodes.Console, msg("metro.start")) { launch("Metro", listOf("start")) }
    private val more = iconButton(AllIcons.Actions.MoreHorizontal, msg("more")) { showMoreMenu() }
    private val refreshButton = iconButton(AllIcons.Actions.Refresh, msg("device.refresh")) { refreshDevices() }
    private val resetCache = iconButton(AllIcons.Actions.Restart, msg("metro.reset")) { launch("Metro", listOf("start", "--reset-cache")) }
    private val diagnostics = iconButton(AllIcons.General.Information, msg("diagnostics")) { launch(msg("diagnostics"), listOf("info")) }
    private val buildAndroid = iconButton(AllIcons.Actions.Compile, msg("build.android")) { launch("Android Release", listOf("build-android", "--mode=release")) }
    private val buildIos = iconButton(AllIcons.Actions.Compile, msg("build.ios")) { launch("iOS Release", listOf("build-ios", "--mode=Release")) }
    private val clearSession = iconButton(AllIcons.Actions.GC, msg("session.clear")) { clearCurrentSession() }
    private val stopAll = iconButton(AllIcons.Actions.Suspend, msg("session.stop.all")) { stopAllTasks() }
    private val rediscover = iconButton(AllIcons.Actions.Refresh, msg("project.refresh")) { discoverApps(true) }
    private val settings = iconButton(AllIcons.General.Settings, msg("settings")) { showSettings() }

    private fun iconButton(sourceIcon: Icon, label: String, action: () -> Unit) = JButton().apply {
        icon = IconUtil.resizeSquared(sourceIcon, JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonIconSize())
        toolTipText = label
        accessibleContext.accessibleName = label
        isFocusable = true
        isContentAreaFilled = false
        border = JBUI.Borders.empty(JBUI.CurrentTheme.Toolbar.mainToolbarButtonInsets())
        preferredSize = JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonSize()
        addActionListener { action() }
    }

    init {
        border = JBUI.Borders.empty(4)
        minimumSize = JBUI.size(100, 100)
        actions.add(android)
        if (SystemInfo.isMac) actions.add(ios)
        actions.add(metro)
        actions.add(more)
        val selectorHeight = JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonSize().height
        listOf(apps, devices).forEach {
            it.preferredSize = JBUI.size(180, selectorHeight)
            it.minimumSize = JBUI.size(180, selectorHeight)
            it.maximumSize = JBUI.size(180, selectorHeight)
        }
        devices.addItem(DeviceTarget("", msg("device.auto"), DeviceKind.ANDROID))
        devices.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): Component {
                val target = value as? DeviceTarget
                return super.getListCellRendererComponent(list, if (target?.id == "") msg("device.auto") else value, index, selected, focus)
            }
        }
        apps.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): Component {
                val candidate = value as? NativeApp
                return super.getListCellRendererComponent(list, candidate?.let { "${it.name} · ${File(it.directory).relativeToOrSelf(File(project.basePath.orEmpty()))}" } ?: msg("app.select.placeholder"), index, selected, focus)
            }
        }
        apps.accessibleContext.accessibleName = msg("app.label")
        devices.accessibleContext.accessibleName = msg("device.label")
        devices.addActionListener { devices.toolTipText = devices.selectedItem?.toString() }
        apps.addActionListener {
            if (!updating) (apps.selectedItem as? NativeApp)?.let { selectApp(it) }
        }
        selectors.add(apps)
        selectors.add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(devices)
            add(refreshButton)
        })
        toolbar.add(actions, BorderLayout.WEST)
        toolbar.add(selectors, BorderLayout.CENTER)
        toolbar.preferredSize = JBUI.size(300, JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonSize().height)
        toolbar.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) { adaptToolbar() }
        })
        add(toolbar, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)
        add(JPanel(BorderLayout(6, 0)).apply {
            add(status, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { add(envBadge); add(details) }, BorderLayout.EAST)
        }, BorderLayout.SOUTH)
        envBadge.isVisible = false; details.isVisible = false
        discoverApps()
        refreshDevices()
    }

    private fun adaptToolbar() {
        val runActions = listOf(android, ios, metro).filter { it.parent != null }
        val actionWidth = runActions.sumOf { it.preferredSize.width } + more.preferredSize.width
        val gap = JBUI.scale(8)
        val showRunActions = toolbar.width >= actionWidth + selectors.preferredSize.width + gap
        runActions.forEach { it.isVisible = showRunActions }
        more.isVisible = true
        val visibleActionWidth = if (showRunActions) actionWidth else more.preferredSize.width
        selectors.isVisible = toolbar.width >= visibleActionWidth + selectors.preferredSize.width + gap
        toolbar.revalidate()
    }

    private fun showMoreMenu() {
        val menu = JPopupMenu()

        fun addAction(button: JButton) {
            menu.add(JMenuItem(button.accessibleContext.accessibleName).apply {
                isEnabled = button.isEnabled
                addActionListener { button.doClick() }
            })
        }

        if (SystemInfo.isMac) addAction(ios)
        addAction(android)
        addAction(metro)
        menu.addSeparator()
        addAction(resetCache)
        addAction(buildAndroid)
        if (SystemInfo.isMac) addAction(buildIos)
        addAction(diagnostics)
        menu.addSeparator()
        addAction(refreshButton)
        addAction(rediscover)
        addAction(settings)
        menu.addSeparator()
        addAction(clearSession)
        addAction(stopAll)
        menu.show(more, 0, more.height)
    }

    private fun updateState() {
        val ready = !scanning && app != null
        listOf(android, ios, metro, resetCache, diagnostics, buildAndroid, buildIos, clearSession, stopAll, rediscover, settings).forEach {
            it.isEnabled = ready
            val label = it.accessibleContext.accessibleName
            it.toolTipText = if (ready) label else msg("action.disabled", label, if (scanning) msg("project.scanning.short") else msg("app.required"))
        }
        apps.isEnabled = !scanning
        refreshButton.isEnabled = !refreshing
        refreshButton.toolTipText = if (refreshing) msg("device.scanning") else msg("device.refresh")
        envBadge.text = env?.let { msg("environment.badge", it) }.orEmpty()
        envBadge.toolTipText = envBadge.text
        envBadge.isVisible = env != null
        choose.isVisible = !scanning && app == null
        emptyText.text = when {
            scanning -> msg("app.scanning")
            app != null -> msg("empty.ready")
            apps.itemCount > 1 -> msg("empty.multiple")
            else -> msg("empty.missing")
        }
        (body.layout as CardLayout).show(body, if (sessions.isEmpty()) "empty" else "sessions")
        status.text = when {
            scanning -> msg("project.scanning")
            app == null -> msg("app.select")
            else -> "${app!!.name} · ${if (refreshing) msg("device.scanning") else msg("device.count", devices.itemCount - 1)}"
        }
        status.toolTipText = app?.directory
        adaptToolbar()
    }

    private fun discoverApps(ignoreSaved: Boolean = false) {
        val request = ++generation
        scanning = true; app = null; env = null
        updateState()
        if (ignoreSaved) preferences.unsetValue(directoryKey)
        val saved = preferences.getValue(directoryKey)
        ApplicationManager.getApplication().executeOnPooledThread {
            val found = AppDiscovery.resolve(File(project.basePath.orEmpty()), saved) {
                disposed || request != generation
            }
            ui {
                if (request != generation) return@ui
                updating = true
                apps.removeAllItems(); found.forEach(apps::addItem)
                apps.selectedIndex = -1
                apps.isVisible = found.size > 1
                updating = false
                val restored = found.firstOrNull { it.directory == saved } ?: found.singleOrNull()
                if (restored != null) {
                    updating = true; apps.selectedItem = restored; updating = false
                    selectApp(restored)
                } else {
                    scanning = false
                    preferences.unsetValue(directoryKey)
                    updateState()
                }
            }
        }
    }

    private fun selectApp(candidate: NativeApp) {
        val request = ++generation
        app = candidate; env = null; profiles = emptyList(); scanning = true
        preferences.setValue(directoryKey, candidate.directory)
        updateState()
        ApplicationManager.getApplication().executeOnPooledThread {
            val available = EnvProfileDiscovery.discover(candidate.directory)
            val key = AppEnvironment.key(candidate.directory)
            ui {
                if (request != generation) return@ui
                profiles = available
                env = AppEnvironment.restore(preferences.getValue(key), available)
                if (env == null) preferences.unsetValue(key)
                scanning = false; updateState()
            }
        }
    }

    private fun chooseDirectory() {
        val chooser = JFileChooser(app?.directory ?: project.basePath).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = msg("directory.title")
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val directory = chooser.selectedFile
        val request = ++generation
        scanning = true; updateState()
        ApplicationManager.getApplication().executeOnPooledThread {
            val candidate = AppDiscovery.inspect(directory)
            ui {
                if (request != generation) return@ui
                if (candidate == null) {
                    scanning = false; updateState()
                    Messages.showWarningDialog(project, msg("app.invalid"), msg("app.invalid.title"))
                } else {
                    updating = true; apps.removeAllItems(); apps.addItem(candidate); apps.isVisible = false; updating = false
                    selectApp(candidate)
                }
            }
        }
    }

    private fun showSettings() {
        val dialog = JDialog(SwingUtilities.getWindowAncestor(this), msg("settings"), Dialog.ModalityType.APPLICATION_MODAL)
        val content = JPanel(BorderLayout(8, 8)).apply { border = JBUI.Borders.empty(12) }
        val directory = JTextField(app?.directory.orEmpty(), 32).apply { isEditable = false }
        val browse = JButton(msg("directory.browse")).apply { addActionListener { dialog.dispose(); chooseDirectory() } }
        content.add(JPanel(BorderLayout(8, 0)).apply { add(directory); add(browse, BorderLayout.EAST) }, BorderLayout.NORTH)
        val choices = JComboBox((listOf(msg("environment.default")) + profiles).toTypedArray())
        choices.selectedItem = env ?: msg("environment.default")
        choices.isEnabled = app != null && !scanning
        choices.accessibleContext.accessibleName = msg("environment.label")
        val center = JPanel(GridLayout(0, 1, 0, 8))
        center.add(JPanel(BorderLayout(8, 0)).apply { add(JLabel(msg("environment.label")), BorderLayout.WEST); add(choices) })
        content.add(center, BorderLayout.CENTER)
        content.add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(JButton(msg("cancel")).apply { addActionListener { dialog.dispose() } })
            add(JButton(msg("save")).apply { addActionListener {
                app?.let { selected ->
                    env = (choices.selectedItem as? String)?.takeIf { it in profiles }
                    val key = AppEnvironment.key(selected.directory)
                    if (env == null) preferences.unsetValue(key) else preferences.setValue(key, env)
                }
                updateState(); dialog.dispose()
            } })
        }, BorderLayout.SOUTH)
        dialog.contentPane = content; dialog.pack(); dialog.setLocationRelativeTo(this); dialog.isVisible = true
    }

    private fun clearCurrentSession() {
        sessions.values.firstOrNull { it.panel == tabs.selectedComponent }?.console?.clear()
    }

    private fun stopAllTasks() {
        sessions.values.filterNot { it.finished }.forEach {
            it.cancelled = true
            it.state.text = msg("session.stopping")
            it.stop.isEnabled = false
            it.stopTask()
        }
    }

    private inner class Session(val key: String, val title: String) : Disposable {
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        val panel = JPanel(BorderLayout())
        val state = JLabel(msg("session.starting"))
        @Volatile var handler: OSProcessHandler? = null
        @Volatile var cancelled = false
        @Volatile var finished = false
        val stop = iconButton(AllIcons.Actions.Suspend, msg("session.stop")) {
            cancelled = true; state.text = msg("session.stopping"); stopTask()
        }
        val close = iconButton(AllIcons.Actions.Close, msg("session.close.disabled")) {
            if (finished) {
                tabs.remove(panel); sessions.remove(key, this); Disposer.dispose(this); updateState()
            }
        }.apply { isEnabled = false }
        init {
            Disposer.register(this@ReactNativeConsolePanel, this)
            Disposer.register(this, console)
            panel.add(JPanel(BorderLayout()).apply {
                add(state, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                    add(stop)
                    add(iconButton(AllIcons.Actions.GC, msg("session.clear")) { console.clear() })
                    add(close)
                }, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            panel.add(console.component, BorderLayout.CENTER)
        }
        fun stopTask() { ApplicationManager.getApplication().executeOnPooledThread { handler?.destroyProcess() } }
        fun complete(label: String) {
            finished = true
            ui {
                state.text = label
                stop.isEnabled = false
                close.isEnabled = true
                close.icon = IconUtil.resizeSquared(
                    AllIcons.Actions.CloseHovered,
                    JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonIconSize()
                )
                close.toolTipText = msg("session.close")
                close.revalidate()
                close.repaint()
            }
        }
        override fun dispose() { cancelled = true; stopTask() }
    }

    private fun launch(title: String, args: List<String>) {
        val selected = app?.takeUnless { scanning } ?: return
        val target = (devices.selectedItem as? DeviceTarget)?.takeIf { it.id.isNotEmpty() }
        if (args.first() == "run-android" && target != null && target.kind != DeviceKind.ANDROID ||
            args.first() == "run-ios" && target?.kind == DeviceKind.ANDROID) {
            Messages.showWarningDialog(project, msg("device.mismatch"), msg("device.mismatch.title")); return
        }
        val key = "${selected.directory}:${args.first()}"
        sessions[key]?.let {
            if (!it.finished) { tabs.selectedComponent = it.panel; status.text = msg("session.duplicate"); return }
            tabs.remove(it.panel); Disposer.dispose(it); sessions.remove(key)
        }
        val session = Session(key, title)
        sessions[key] = session
        tabs.addTab("${selected.name} · $title", session.panel); tabs.selectedComponent = session.panel
        updateState()
        val environment = env
        val command = DeviceTargetArguments.forCommand(args, target)
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                require(AppDiscovery.inspect(File(selected.directory)) != null) { msg("directory.stale") }
                require(environment == null || File(selected.directory, environment).isFile) { msg("environment.stale") }
                if (disposed || session.cancelled) { session.complete(msg("session.cancelled")); return@executeOnPooledThread }
                val handler = ReactNativeCommandRunner.create(selected.directory, command, environment)
                session.handler = handler
                handler.addProcessListener(object : ProcessListener {
                    override fun processTerminated(event: ProcessEvent) { session.complete(msg("session.exited", event.exitCode)) }
                })
                if (disposed || session.cancelled) {
                    handler.startNotify(); handler.destroyProcess(); return@executeOnPooledThread
                }
                session.console.print(msg("console.header", selected.directory, command.joinToString(" "), environment ?: msg("environment.inherited")), ConsoleViewContentType.SYSTEM_OUTPUT)
                session.console.attachToProcess(handler)
                ui { if (!session.finished) session.state.text = msg("session.running", title) }
                handler.startNotify()
                if (disposed || session.cancelled) handler.destroyProcess()
            } catch (e: Exception) {
                if (!disposed) session.console.print(msg("console.failed", e.message.orEmpty()), ConsoleViewContentType.ERROR_OUTPUT)
                session.complete(msg("session.failed"))
            }
        }
    }

    private fun refreshDevices() {
        if (refreshing) return
        refreshing = true
        val request = ++deviceRefreshGeneration
        updateState()

        data class ProbeSpec(
            val name: String,
            val command: com.intellij.execution.configurations.GeneralCommandLine,
            val parse: (String) -> List<DeviceTarget>,
            val diagnostics: (String) -> List<String> = { emptyList() },
        )
        data class ProbeResult(val devices: List<DeviceTarget>, val problems: List<String>)

        val specs = buildList {
            add(ProbeSpec(
                name = "Android / adb",
                command = ReactNativeCommandRunner.runAdbDevices(),
                parse = { text ->
                    val parsed = DeviceOutputParser.parseAdbDevices(text)
                    parsed.filter { it.isUsable }.map { DeviceTarget(it.serial, it.serial, DeviceKind.ANDROID) }
                },
                diagnostics = { text ->
                    DeviceOutputParser.parseAdbDevices(text)
                        .filterNot { it.isUsable }
                        .map { msg("device.unusable", it.serial, it.state) }
                },
            ))
            if (SystemInfo.isMac) {
                add(ProbeSpec(
                    name = msg("device.ios.simulator"),
                    command = ReactNativeCommandRunner.runSimctlListDevices(),
                    parse = { text ->
                        DeviceOutputParser.parseSimctlDevices(text).map { DeviceTarget(it.udid, it.name, DeviceKind.IOS_SIMULATOR, it.state) }
                    },
                ))
                add(ProbeSpec(
                    name = msg("device.ios.physical"),
                    command = ReactNativeCommandRunner.runXcdeviceList(),
                    parse = { text ->
                        DeviceOutputParser.parseXcdevicePhysicalDevices(text).map { DeviceTarget(it.udid, it.name, DeviceKind.IOS_PHYSICAL) }
                    },
                ))
            }
        }

        fun probe(spec: ProbeSpec): ProbeResult {
            if (disposed) return ProbeResult(emptyList(), emptyList())
            var handler: CapturingProcessHandler? = null
            try {
                handler = createProbe(spec.command)
                activeProbeHandlers.add(handler)
                if (disposed) {
                    handler.destroyProcess()
                    return ProbeResult(emptyList(), emptyList())
                }
                val result = handler.runProcess(10_000)
                if (result.isTimeout || result.exitCode != 0) {
                    return ProbeResult(
                        emptyList(),
                        listOf(msg("probe.error", spec.name, if (result.isTimeout) msg("probe.timeout") else result.stderr.take(1500))),
                    )
                }
                val parsed = spec.parse(result.stdout)
                return ProbeResult(parsed, spec.diagnostics(result.stdout))
            } catch (e: Exception) {
                return ProbeResult(emptyList(), listOf(msg("probe.error", spec.name, e.message.orEmpty())))
            } finally {
                handler?.let { activeProbeHandlers.remove(it) }
            }
        }

        val results = arrayOfNulls<ProbeResult>(specs.size)
        val lock = Any()
        val remaining = AtomicInteger(specs.size)
        specs.forEachIndexed { index, spec ->
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val result = probe(spec)
                    synchronized(lock) { results[index] = result }
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        ui {
                            if (request != deviceRefreshGeneration) return@ui
                            val completed = synchronized(lock) { results.filterNotNull() }
                            val found = completed.flatMap { it.devices }
                            val problems = completed.flatMap { it.problems }
                            val previousId = (devices.selectedItem as? DeviceTarget)?.id
                            devices.removeAllItems(); devices.addItem(DeviceTarget("", msg("device.auto"), DeviceKind.ANDROID))
                            found.forEach(devices::addItem)
                            found.firstOrNull { it.id == previousId }?.let { devices.selectedItem = it }
                            probeDetails = problems.joinToString("\n\n")
                            details.isVisible = problems.isNotEmpty()
                            refreshing = false
                            updateState()
                        }
                    }
                }
            }
        }
    }

    private fun ui(action: () -> Unit) = ApplicationManager.getApplication().invokeLater {
        if (!disposed && !project.isDisposed) action()
    }
    override fun dispose() {
        disposed = true
        generation++
        deviceRefreshGeneration++
        activeProbeHandlers.forEach { it.destroyProcess() }
        activeProbeHandlers.clear()
    }
}
