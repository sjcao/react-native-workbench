package dev.gaphunter.reactnativecompanion.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOutputParserTest {

    @Test
    fun parsesMultipleAdbDevices() {
        val output = """
            List of devices attached
            emulator-5554	device
            R58M12ABCDE	unauthorized
        """.trimIndent()

        val devices = DeviceOutputParser.parseAdbDevices(output)

        assertEquals(2, devices.size)
        assertEquals(AndroidDevice("emulator-5554", "device"), devices[0])
        assertEquals(AndroidDevice("R58M12ABCDE", "unauthorized"), devices[1])
        assertTrue(devices[0].isUsable)
        assertTrue(!devices[1].isUsable)
    }

    @Test
    fun emptyAdbOutputYieldsNoDevices() {
        val devices = DeviceOutputParser.parseAdbDevices("List of devices attached\n")
        assertTrue(devices.isEmpty())
    }

    @Test
    fun adbOutputWithOnlyHeaderYieldsNoDevices() {
        val devices = DeviceOutputParser.parseAdbDevices("List of devices attached")
        assertTrue(devices.isEmpty())
    }

    @Test
    fun parsesMultipleSimctlDevicesAcrossSections() {
        val output = """
            == Devices ==
            -- iOS 17.0 --
                iPhone 15 (12345678-1234-1234-1234-123456789012) (Shutdown)
                iPhone 15 Pro (87654321-4321-4321-4321-210987654321) (Booted)
            -- tvOS 17.0 --
                Apple TV (ABCDEF12-3456-7890-ABCD-EF1234567890) (Shutdown)
        """.trimIndent()

        val devices = DeviceOutputParser.parseSimctlDevices(output)

        assertEquals(2, devices.size)
        assertEquals(IosSimulator("12345678-1234-1234-1234-123456789012", "iPhone 15", "Shutdown"), devices[0])
        assertEquals(IosSimulator("87654321-4321-4321-4321-210987654321", "iPhone 15 Pro", "Booted"), devices[1])
        assertTrue(!devices[0].isBooted)
        assertTrue(devices[1].isBooted)
        assertTrue(devices.none { it.name == "Apple TV" })
    }

    @Test
    fun simctlSectionHeadersAreNotMistakenForDevices() {
        val devices = DeviceOutputParser.parseSimctlDevices("== Devices ==\n-- iOS 17.0 --\n-- tvOS 17.0 --")
        assertTrue(devices.isEmpty())
    }

    @Test
    fun simctlOutputWithNoDevicesYieldsEmptyList() {
        val devices = DeviceOutputParser.parseSimctlDevices("")
        assertTrue(devices.isEmpty())
    }

    @Test
    fun deviceNamesWithSpacesAreParsedCorrectly() {
        val output = "-- iOS 17.0 --\n    iPad Pro (12.9-inch) (5th generation) (12345678-1234-1234-1234-123456789012) (Booted)"
        val devices = DeviceOutputParser.parseSimctlDevices(output)

        assertEquals(1, devices.size)
        assertEquals("iPad Pro (12.9-inch) (5th generation)", devices[0].name)
    }

    @Test
    fun parsesOnlyAvailablePhysicalIosDevicesFromXcdeviceJson() {
        val output = """
            [
              {
                "simulator": false,
                "available": true,
                "platform": "com.apple.platform.iphoneos",
                "identifier": "00008110-001234567890001E",
                "name": "Alice's iPhone",
                "operatingSystemVersion": "18.6.2",
                "interface": "usb"
              },
              {
                "simulator": true,
                "available": true,
                "platform": "com.apple.platform.iphonesimulator",
                "identifier": "12345678-1234-1234-1234-123456789012",
                "name": "iPhone 16"
              },
              {
                "simulator": false,
                "available": false,
                "platform": "com.apple.platform.iphoneos",
                "identifier": "00008120-UNAVAILABLE",
                "name": "Offline iPhone"
              },
              {
                "simulator": false,
                "available": true,
                "platform": "com.apple.platform.watchos",
                "identifier": "WATCH-UDID",
                "name": "Apple Watch"
              }
            ]
        """.trimIndent()

        val devices = DeviceOutputParser.parseXcdevicePhysicalDevices(output)

        assertEquals(
            listOf(IosPhysicalDevice("00008110-001234567890001E", "Alice's iPhone", "18.6.2", "usb")),
            devices,
        )
    }

    @Test
    fun blankXcdeviceOutputYieldsNoDevices() {
        assertTrue(DeviceOutputParser.parseXcdevicePhysicalDevices("").isEmpty())
    }
}
