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

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy { emptyMap() }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libng_gl4es.so"
    
    // NGGL4ES works with all MC versions (no limits)
}
