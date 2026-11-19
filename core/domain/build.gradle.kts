plugins {
    alias(libs.plugins.musicmania.jvm.library)
    alias(libs.plugins.musicmania.hilt)
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(libs.androidx.paging.common)

    testImplementation(projects.core.testing)
}
