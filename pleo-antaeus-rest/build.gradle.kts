plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    implementation(project(":pleo-antaeus-core"))
    implementation(project(":pleo-antaeus-models"))

    implementation("io.javalin:javalin:5.4.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")

}
