plugins { alias(libs.plugins.kotlin.jvm); application }
kotlin { jvmToolchain(21) }
dependencies { implementation(project(":license-core")) }
application { mainClass.set("com.smartmanager.license.generator.MainKt") }
