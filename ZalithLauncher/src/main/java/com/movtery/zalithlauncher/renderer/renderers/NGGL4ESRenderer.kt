package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * Krypton Wrapper (NGGL4ES) - Next Generation GL4ES
 * This renderer works with all Minecraft versions including 1.21.5+
 * Based on FCL's implementation
 */
class NGGL4ESRenderer : RendererInterface {
    override fun getRendererId(): String = "nggl4es"

    override fun getUniqueIdentifier(): String = "e7b90ed6-e518-4d4e-93dc-5c7133cd5b31"

    override fun getRendererName(): String = "Krypton Wrapper (NGGL4ES)"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_USE_MC_COLOR" to "1",
            "LIBGL_GL" to "31",
            "LIBGL_ES" to "3",
            "LIBGL_NORMALIZE" to "1",
            "LIBGL_NOINTOVLHACK" to "1",
            "LIBGL_NOERROR" to "1",
            "POJAV_RENDERER" to "opengles3"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libng_gl4es.so"
    
    // NGGL4ES works with all MC versions (no limits)
}
