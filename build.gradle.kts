
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
        vendor.set(JvmVendorSpec.AZUL)
    }
}
