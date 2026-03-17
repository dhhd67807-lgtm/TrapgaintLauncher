package com.movtery.zalithlauncher.feature.version.install

enum class Addon(val addonName: String) {
    OPTIFINE("OptiFine"),
    FORGE("Forge"),
    NEOFORGE("NeoForge"),
    FABRIC("Fabric"),
    FABRIC_API("Fabric API"),
    DRAGON_CLIENT("Dragon Client"),
    QUILT("Quilt"),
    QSL("QSL");

    companion object {
        private val compatibleMap = mapOf(
            OPTIFINE to setOf(OPTIFINE, FORGE),
            FORGE to setOf(OPTIFINE, FORGE),
            NEOFORGE to setOf(NEOFORGE),
            FABRIC to setOf(FABRIC, FABRIC_API, DRAGON_CLIENT),
            FABRIC_API to setOf(FABRIC, FABRIC_API, DRAGON_CLIENT),
            DRAGON_CLIENT to setOf(FABRIC, FABRIC_API, DRAGON_CLIENT),
            QUILT to setOf(QUILT, QSL),
            QSL to setOf(QUILT, QSL)
        )

        fun getCompatibles(addon: Addon) = compatibleMap[addon]
    }
}
