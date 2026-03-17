package com.movtery.zalithlauncher.feature.mod.modloader

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.kdt.pojavlaunch.utils.DownloadUtils

object DragonClientManifestResolver {
    private const val MANIFEST_URL =
        "https://github.com/dhhd67807-lgtm/dragon-client-mod/releases/latest/download/versions.json"

    data class DragonClientAsset(
        val minecraftVersion: String,
        val clientVersion: String,
        val downloadUrl: String,
        val sha256: String,
        val fileName: String
    )

    @JvmStatic
    @Throws(Exception::class)
    fun resolveLatestAsset(minecraftVersion: String): DragonClientAsset? {
        val rawJson = DownloadUtils.downloadString(MANIFEST_URL) ?: return null
        val root = JsonParser.parseString(rawJson).asJsonObject

        val clientVersion = root.safeString("version")
        val versions = root.getAsJsonObject("minecraft_versions") ?: return null
        val target = versions.getAsJsonObject(minecraftVersion) ?: return null

        val url = target.safeString("url")
        val sha256 = target.safeString("sha256")
        if (url.isBlank()) return null

        return DragonClientAsset(
            minecraftVersion = minecraftVersion,
            clientVersion = clientVersion,
            downloadUrl = url,
            sha256 = sha256,
            fileName = url.substringAfterLast('/')
        )
    }

    private fun JsonObject.safeString(key: String): String {
        val value = get(key) ?: return ""
        return if (value.isJsonNull) "" else value.asString.orEmpty()
    }
}
