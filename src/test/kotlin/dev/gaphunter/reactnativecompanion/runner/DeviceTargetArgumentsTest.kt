package dev.gaphunter.reactnativecompanion.runner

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTargetArgumentsTest {
    @Test
    fun selectedPhysicalIosDeviceIsPassedByUdid() {
        val target = DeviceTarget("physical-udid", "My iPhone", DeviceKind.IOS_PHYSICAL)

        assertEquals(
            listOf("run-ios", "--udid", "physical-udid"),
            DeviceTargetArguments.forCommand(listOf("run-ios"), target),
        )
    }

    @Test
    fun selectedIosSimulatorIsPassedByUdid() {
        val target = DeviceTarget("simulator-udid", "iPhone 16", DeviceKind.IOS_SIMULATOR)

        assertEquals(
            listOf("run-ios", "--udid", "simulator-udid"),
            DeviceTargetArguments.forCommand(listOf("run-ios"), target),
        )
    }

    @Test
    fun targetForAnotherPlatformIsNotPassed() {
        val target = DeviceTarget("android-serial", "android-serial", DeviceKind.ANDROID)

        assertEquals(listOf("run-ios"), DeviceTargetArguments.forCommand(listOf("run-ios"), target))
    }

    @Test
    fun commandWithTerminalPreservesTerminalAndAppendsUdid() {
        // 验证当包含 --terminal 参数时，能够正确保留该参数并追加目标设备 --udid
        val target = DeviceTarget("simulator-udid", "iPhone 16", DeviceKind.IOS_SIMULATOR)

        assertEquals(
            listOf("run-ios", "--terminal", "Terminal", "--udid", "simulator-udid"),
            DeviceTargetArguments.forCommand(listOf("run-ios", "--terminal", "Terminal"), target),
        )
    }
}
