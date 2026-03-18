package skybox

import org.fancryer.engine.ShaderProgram
import org.intellij.lang.annotations.Language
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.GL_TEXTURE_CUBE_MAP
import org.lwjgl.opengl.GL13C.GL_TEXTURE_CUBE_MAP_POSITIVE_X
import org.lwjgl.opengl.GL30C.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import utils.Time
import java.io.File
import java.nio.IntBuffer

class Skybox
{
    private var vaoId=0
    private var vboId=0
    private var textureId=0
    private lateinit var shader:ShaderProgram

    fun init(cubemapTexture:Int)
    {
        textureId=cubemapTexture

        // Настройка VAO/VBO
        vaoId=glGenVertexArrays()
        glBindVertexArray(vaoId)

        vboId=glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER,vboId)
        glBufferData(GL_ARRAY_BUFFER,vertices,GL_STATIC_DRAW)

        glVertexAttribPointer(0,3,GL_FLOAT,false,3*4,0)
        glEnableVertexAttribArray(0)

        // Шейдер
        shader=ShaderProgram()
        shader.init("skybox/skybox.vert","skybox/skybox.frag")
    }

    fun render(viewMatrix:Matrix4f,projectionMatrix:Matrix4f,sunColor:Vector3f=Vector3f(1f,1f,1f))
    {
        glDepthFunc(GL_LEQUAL)
        glDepthMask(false)

        // Отключаем отсечение граней
        glCullFace(GL_FRONT)

        shader.use()

        val view=Matrix4f(viewMatrix)
        view.m30(0f)
        view.m31(0f)
        view.m32(0f)

        shader.setUniform3f("sunColor",sunColor.x,sunColor.y,sunColor.z)
        shader.setUniform1f(
            "time",
            (Time.time.inWholeNanoseconds.toDouble()/1_000_000_000.0).toFloat()
        )  // если нужно
        shader.setUniformMatrix4f("projection",projectionMatrix)
        shader.setUniformMatrix4f("view",view)

        glBindVertexArray(vaoId)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_CUBE_MAP,textureId)
        glDrawArrays(GL_TRIANGLES,0,36)

        // Включаем обратно
        glCullFace(GL_BACK)

        glDepthMask(true)
        glDepthFunc(GL_LESS)
    }

    companion object
    {
        private val vertices=floatArrayOf(
            // Правый
            1.0f,-1.0f,-1.0f,
            1.0f,-1.0f,1.0f,
            1.0f,1.0f,1.0f,
            1.0f,1.0f,1.0f,
            1.0f,1.0f,-1.0f,
            1.0f,-1.0f,-1.0f,

            // Левый
            -1.0f,-1.0f,1.0f,
            -1.0f,-1.0f,-1.0f,
            -1.0f,1.0f,-1.0f,
            -1.0f,1.0f,-1.0f,
            -1.0f,1.0f,1.0f,
            -1.0f,-1.0f,1.0f,

            // Верхний
            -1.0f,1.0f,-1.0f,
            1.0f,1.0f,-1.0f,
            1.0f,1.0f,1.0f,
            1.0f,1.0f,1.0f,
            -1.0f,1.0f,1.0f,
            -1.0f,1.0f,-1.0f,

            // Нижний
            -1.0f,-1.0f,-1.0f,
            -1.0f,-1.0f,1.0f,
            1.0f,-1.0f,-1.0f,
            1.0f,-1.0f,-1.0f,
            -1.0f,-1.0f,1.0f,
            1.0f,-1.0f,1.0f,

            // Передний
            -1.0f,-1.0f,1.0f,
            1.0f,-1.0f,1.0f,
            1.0f,1.0f,1.0f,
            1.0f,1.0f,1.0f,
            -1.0f,1.0f,1.0f,
            -1.0f,-1.0f,1.0f,

            // Задний
            1.0f,-1.0f,-1.0f,
            -1.0f,-1.0f,-1.0f,
            -1.0f,1.0f,-1.0f,
            -1.0f,1.0f,-1.0f,
            1.0f,1.0f,-1.0f,
            1.0f,-1.0f,-1.0f
        )
    }
}