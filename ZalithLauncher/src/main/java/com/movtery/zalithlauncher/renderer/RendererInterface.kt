package com.movtery.zalithlauncher.renderer

/**
 * 启动器渲染器实现
 */
interface RendererInterface {
    /**
     * 获取渲染器的ID
     */
    fun getRendererId(): String

    /**
     * 获取渲染器的唯一标识ID
     */
    fun getUniqueIdentifier(): String

    /**
     * 获取渲染器的名称
     */
    fun getRendererName(): String

    /**
     * 获取渲染器的环境变量
     */
    fun getRendererEnv(): Lazy<Map<String, String>>

    /**
     * 获取需要dlopen的库
     */
    fun getDlopenLibrary(): Lazy<List<String>>

    /**
     * 获取渲染器的库
     */
    fun getRendererLibrary(): String

    /**
     * 获取EGL名称
     */
    fun getRendererEGL(): String? = null
    
    /**
     * 获取支持的最小Minecraft版本 (例如: "1.8")
     * 空字符串表示无限制
     */
    fun getMinMCVersion(): String = ""
    
    /**
     * 获取支持的最大Minecraft版本 (例如: "1.21.4")
     * 空字符串表示无限制
     */
    fun getMaxMCVersion(): String = ""
    
    /**
     * 检查是否支持指定的Minecraft版本
     */
    fun supportsVersion(mcVersion: String): Boolean {
        if (getMinMCVersion().isEmpty() && getMaxMCVersion().isEmpty()) return true

        // If the version can't be parsed reliably, only unrestricted renderers should be used.
        val version = parseVersion(mcVersion) ?: return false
        val minVersion = if (getMinMCVersion().isNotEmpty()) parseVersion(getMinMCVersion()) else null
        val maxVersion = if (getMaxMCVersion().isNotEmpty()) parseVersion(getMaxMCVersion()) else null
        
        if (minVersion != null && compareVersions(version, minVersion) < 0) return false
        if (maxVersion != null && compareVersions(version, maxVersion) > 0) return false
        
        return true
    }
    
    private fun parseVersion(version: String): List<Int>? {
        val matches = Regex("""(?<!\d)(\d+)\.(\d+)(?:\.(\d+))?(?!\d)""")
            .findAll(version)
            .mapNotNull { match ->
                val major = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val minor = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val patch = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
                listOf(major, minor, patch)
            }
            .toList()

        if (matches.isEmpty()) return null
        return matches.firstOrNull { parsed -> parsed.firstOrNull() == 1 } ?: matches.first()
    }
    
    private fun compareVersions(v1: List<Int>, v2: List<Int>): Int {
        val maxLength = maxOf(v1.size, v2.size)
        for (i in 0 until maxLength) {
            val num1 = v1.getOrNull(i) ?: 0
            val num2 = v2.getOrNull(i) ?: 0
            if (num1 != num2) return num1.compareTo(num2)
        }
        return 0
    }
}
