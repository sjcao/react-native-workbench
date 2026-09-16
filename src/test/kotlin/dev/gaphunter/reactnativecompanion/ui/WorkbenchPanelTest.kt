package dev.gaphunter.reactnativecompanion.ui

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Container
import java.awt.event.ComponentEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Exercise actual Swing components, including overflow and accessible icon controls. */
class WorkbenchPanelTest : BasePlatformTestCase() {
    fun testMissingProbeExecutableFinishesRefreshAndAllowsRetry() {
        val check = Runnable {
            val missing = java.io.File(project.basePath, "missing-probe-" + java.util.UUID.randomUUID()).absolutePath
            val panel = ReactNativeConsolePanel(project) {
                com.intellij.execution.process.CapturingProcessHandler(
                    com.intellij.execution.configurations.GeneralCommandLine(missing)
                )
            }
            try {
                fun descendants(component: Component): List<Component> = listOf(component) +
                    if (component is Container) component.components.flatMap { descendants(it) } else emptyList()
                val buttons = descendants(panel).filterIsInstance<JButton>()
                val refresh = buttons.single {
                    it.accessibleContext.accessibleName == WorkbenchBundle.message("auto", "device.refresh")
                }
                val details = buttons.single {
                    it.accessibleContext.accessibleName == WorkbenchBundle.message("auto", "details.show")
                }
                fun awaitRefresh() {
                    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
                    while (!refresh.isEnabled && System.nanoTime() < deadline) {
                        com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
                        Thread.sleep(10)
                    }
                    assertTrue("A missing executable must not leave discovery running", refresh.isEnabled)
                    assertTrue("Probe errors must remain visible", details.isVisible)
                }
                awaitRefresh()
                refresh.doClick()
                awaitRefresh()
            } finally { Disposer.dispose(panel) }
        }
        if (SwingUtilities.isEventDispatchThread()) check.run() else SwingUtilities.invokeAndWait(check)
    }

    fun testToolbarAdaptsAndIconsHaveAccessibleNames() {
        val check = Runnable {
            val panel = ReactNativeConsolePanel(project)
            try {
                fun descendants(component: Component): List<Component> = listOf(component) +
                    if (component is Container) component.components.flatMap { descendants(it) } else emptyList()
                val all = descendants(panel)
                val run = all.filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == WorkbenchBundle.message("auto", "run.android") }
                val more = all.filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == WorkbenchBundle.message("auto", "more") }
                val actionBar = run.parent as JPanel
                val toolbar = actionBar.parent as JPanel
                val standardButtonSize = JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonSize()
                val standardIconSize = JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonIconSize()
                assertEquals(standardButtonSize.width, run.preferredSize.width)
                assertEquals(standardButtonSize.height, run.preferredSize.height)
                assertEquals(standardIconSize, run.icon.iconWidth)
                assertEquals(standardIconSize, run.icon.iconHeight)
                assertTrue(more.isVisible)
                assertEquals(0, (actionBar.layout as java.awt.FlowLayout).hgap)
                toolbar.setSize(800, standardButtonSize.height)
                toolbar.dispatchEvent(ComponentEvent(toolbar, ComponentEvent.COMPONENT_RESIZED))
                toolbar.doLayout()
                actionBar.doLayout()
                assertTrue(run.parent.isVisible)
                val refresh = all.filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == WorkbenchBundle.message("auto", "device.refresh") }
                val deviceGroup = refresh.parent as JPanel
                val device = all.filterIsInstance<javax.swing.JComboBox<*>>().single {
                    it.accessibleContext.accessibleName == WorkbenchBundle.message("auto", "device.label")
                }
                val appSelector = all.filterIsInstance<javax.swing.JComboBox<*>>().single {
                    it.accessibleContext.accessibleName == WorkbenchBundle.message("auto", "app.label")
                }
                assertTrue(deviceGroup.layout is java.awt.FlowLayout)
                assertSame(deviceGroup, device.parent)
                assertSame(deviceGroup, refresh.parent)
                assertEquals(appSelector.preferredSize.width, device.preferredSize.width)
                assertTrue(device.preferredSize.width <= JBUI.scale(200))
                toolbar.setSize(180, standardButtonSize.height)
                toolbar.dispatchEvent(ComponentEvent(toolbar, ComponentEvent.COMPONENT_RESIZED))
                toolbar.doLayout()
                actionBar.doLayout()
                assertTrue(actionBar.isVisible)
                assertFalse(run.isVisible)
                assertTrue(more.isVisible)
                assertFalse(device.parent.parent.isVisible)
                assertFalse(run.isEnabled) // Discovery is asynchronous; never run an unresolved directory.
            } finally { Disposer.dispose(panel) }
        }
        if (SwingUtilities.isEventDispatchThread()) check.run() else SwingUtilities.invokeAndWait(check)
    }
}
