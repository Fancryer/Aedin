package engine

import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11C.glViewport
import org.lwjgl.system.MemoryUtil.NULL

class Window(var width:Int,var height:Int,val title:String)
{
    var handle=0L
        private set

    fun init(enableVsync:Boolean)
    {
        if(!glfwInit()) throw IllegalStateException("Unable to initialize GLFW")

        // Window settings
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE,GLFW_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3)
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT,GLFW_TRUE)

        handle=glfwCreateWindow(width,height,title,NULL,NULL)
        if(handle==0L)
        {
            glfwTerminate()
            throw IllegalStateException("Failed to create window")
        }


        // Making this context current
        glfwMakeContextCurrent(handle)

        // Enabling V-sync
        glfwSwapInterval(if(enableVsync) 1 else 0)
        glfwShowWindow(handle)

        // Initializing OpenGL (important: only after making a context!)
        GL.createCapabilities()

        glViewport(0,0,width,height)

        glfwSetFramebufferSizeCallback(handle) {_,w,h->
            width=w
            height=h
            glViewport(0,0,width,height)
        }
    }

    val cursorIsVisible get()=glfwGetInputMode(handle,GLFW_CURSOR)==GLFW_CURSOR_NORMAL

    fun toggleCursorVisibility()=setCursorVisibility(!cursorIsVisible)

    fun setCursorVisibility(visible:Boolean)=glfwSetInputMode(
        handle,
        GLFW_CURSOR,
        if(visible) GLFW_CURSOR_NORMAL else GLFW_CURSOR_DISABLED
    )

    fun shouldClose()=glfwWindowShouldClose(handle)
    fun close()=glfwSetWindowShouldClose(handle,true)

    fun update()
    {
        glfwSwapBuffers(handle)
        glfwPollEvents()
    }

    fun cleanup()
    {
        glfwDestroyWindow(handle)
        glfwTerminate()
    }
}