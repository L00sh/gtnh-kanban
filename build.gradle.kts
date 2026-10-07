
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// The moving download tag must not become a shared mod version for different commits.
if (System.getenv("VERSION") == null && version.toString().startsWith("latest-build")) {
    val gitRevision = providers.exec {
        commandLine("git", "rev-parse", "HEAD")
    }.standardOutput.asText.get().trim()
    val dirtySuffix = if (version.toString().endsWith("-dirty")) "-dirty" else ""
    val buildVersion = "0.0.0-git+$gitRevision$dirtySuffix"
    version = buildVersion
    extra["modVersion"] = buildVersion
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
        vendor.set(JvmVendorSpec.AZUL)
    }
}
