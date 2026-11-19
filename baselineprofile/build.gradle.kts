import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.musicmania.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.github.tb18003.musicmania.baselineprofile"
    targetProjectPath = ":app"

    // Full AOSP image (not ATD): profile collection needs the complete runtime.
    testOptions.managedDevices.allDevices.create("pixel6Api34Aosp", ManagedVirtualDevice::class.java) {
        device = "Pixel 6"
        apiLevel = 34
        systemImageSource = "aosp"
    }
}

// Default: the managed device. `-Pmusicmania.baselineprofile.connected` uses a running emulator/phone instead.
val useConnectedDevice = providers.gradleProperty("musicmania.baselineprofile.connected").isPresent

baselineProfile {
    useConnectedDevices = useConnectedDevice
    if (!useConnectedDevice) managedDevices += "pixel6Api34Aosp"
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
}
