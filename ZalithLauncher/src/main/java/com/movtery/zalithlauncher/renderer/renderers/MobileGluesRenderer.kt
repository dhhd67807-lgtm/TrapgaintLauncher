package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

class MobileGluesRenderer : RendererInterface {
    override fun getRendererId(): String = "mobileglues"

    override fun getUniqueIdentifier(): String = "a1b2c3d4-5e6f-7g8h-9i0j-k1l2m3n4o5p6"

    override fun getRendererName(): String = "MobileGlues"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_ES" to "3",
            "LIBGL_MIPMAP" to "3",
            "LIBGL_NORMALIZE" to "1",
            "LIBGL_VSYNC" to "1"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { 
        listOf("libmobileglues_info_getter.so")
    }

    override fun getRendererLibrary(): String = "libmobileglues.so"
}
