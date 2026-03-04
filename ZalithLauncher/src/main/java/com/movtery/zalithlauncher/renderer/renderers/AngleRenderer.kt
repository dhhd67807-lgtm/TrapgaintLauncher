package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

class AngleRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles3_angle"

    override fun getUniqueIdentifier(): String = "f7e8d9c0-1a2b-3c4d-5e6f-7g8h9i0j1k2l"

    override fun getRendererName(): String = "ANGLE (OpenGL ES 3.x)"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_ES" to "3"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libGLESv2_angle.so"
    
    override fun getRendererEGL(): String = "libEGL_angle.so"
    
    // ANGLE works well with MC 1.21.5+ where OSMesa-based renderers fail
    override fun getMinMCVersion(): String = "1.21.5"
}
