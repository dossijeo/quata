import java.security.MessageDigest

// Restore the common UI's accessibility tree after temporary Compose Web layers.
// Keep Android/iOS and all other Compose components on their official versions.
val backportVersion = "1.10.0-quata-owner.1"
val artifactDirectory = rootProject.file(
    "third_party/compose-ui-web/maven/org/jetbrains/compose/ui/ui/$backportVersion",
)
val pinnedFiles = mapOf(
    "ui-$backportVersion.klib" to "f79203757b133aacd3ffad129333c9b0daa79d5afe04d24b360ee4cc1ab43911",
    "ui-$backportVersion.module" to "708e28fbed6f6809e045a6d61967d4dabc12578d803dda618c3afabed7f4a970",
)

allprojects {
    configurations.configureEach {
        if (name.contains("wasm", ignoreCase = true)) {
            incoming.beforeResolve {
                pinnedFiles.forEach { (name, expected) ->
                    val file = artifactDirectory.resolve(name)
                    check(file.isFile) { "Missing Compose Web backport: $name" }
                    val actual = MessageDigest.getInstance("SHA-256")
                        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                    check(actual == expected) { "Compose Web backport checksum mismatch: $name" }
                }
            }
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.compose.ui" &&
                    requested.name == "ui" && requested.version == "1.10.0"
                ) {
                    useVersion(backportVersion)
                    because("Restore Compose Web semantics after removing a temporary owner; see third_party/compose-ui-web")
                }
            }
        }
    }
}
