plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    implementation(project(":pleo-antaeus-data"))
    api(project(":pleo-antaeus-models"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.3.5")
}