plugins {
    alias(libs.plugins.musicmania.jvm.library)
    alias(libs.plugins.musicmania.hilt)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
}
