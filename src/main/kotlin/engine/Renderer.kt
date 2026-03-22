package engine

import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL32C.GL_TEXTURE_CUBE_MAP_SEAMLESS

class Renderer
{
    fun init()
    {
        glClearColor(0.2f,0.3f,0.3f,1.0f)
        glEnable(GL_DEPTH_TEST) // important: For 3D
        glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
        glPointSize(5f)
        glLineWidth(20f)
    }

    fun clear()=glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
}