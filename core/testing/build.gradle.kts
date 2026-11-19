plugins {
    alias(libs.plugins.musicmania.jvm.library)
}

dependencies {
    api(projects.core.domain)
    api(libs.junit4)
    api(libs.kotlin.test)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
