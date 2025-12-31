import org.gradle.api.Project

fun Project.booleanProperty(name: String, defaultValue: Boolean = false): Boolean {
    return providers.gradleProperty(name)
        .map { it.toBoolean() }
        .getOrElse(defaultValue)
}

enum class Modules(val propertyName: String) {
    ios("iosEnabled"),
    iosSimulator("iosSimulatorEnabled"),
    web("webEnabled")
}