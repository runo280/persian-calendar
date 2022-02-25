plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("dependencies") {
            id = "io.github.persiancalendar.appbuildplugin"
            implementationClass = "io.github.persiancalendar.gradle.AppBuildPlugin"
        }
    }
}
