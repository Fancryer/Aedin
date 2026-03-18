package skybox

import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.GL_TEXTURE_CUBE_MAP
import org.lwjgl.opengl.GL13C.GL_TEXTURE_CUBE_MAP_POSITIVE_X
import org.lwjgl.opengl.GL30C.*
import org.lwjgl.stb.STBImage.*
import java.nio.ByteBuffer
import java.nio.IntBuffer

class Skybox {
    private var vaoId = 0
    private var vboId = 0
    private var textureId = 0
    private lateinit var shader: ShaderProgram
    
    fun init() {
        // Вершины куба
        val vertices = floatArrayOf(
            -1.0f,  1.0f, -1.0f,
            -1.0f, -1.0f, -1.0f,
             1.0f, -1.0f, -1.0f,
             1.0f, -1.0f, -1.0f,
             1.0f,  1.0f, -1.0f,
            -1.0f,  1.0f, -1.0f,
            
            -1.0f, -1.0f,  1.0f,
            -1.0f, -1.0f, -1.0f,
            -1.0f,  1.0f, -1.0f,
            -1.0f,  1.0f, -1.0f,
            -1.0f,  1.0f,  1.0f,
            -1.0f, -1.0f,  1.0f,
            
             1.0f, -1.0f, -1.0f,
             1.0f, -1.0f,  1.0f,
             1.0f,  1.0f,  1.0f,
             1.0f,  1.0f,  1.0f,
             1.0f,  1.0f, -1.0f,
             1.0f, -1.0f, -1.0f,
            
            -1.0f, -1.0f,  1.0f,
            -1.0f,  1.0f,  1.0f,
             1.0f,  1.0f,  1.0f,
             1.0f,  1.0f,  1.0f,
             1.0f, -1.0f,  1.0f,
            -1.0f, -1.0f,  1.0f,
            
            -1.0f,  1.0f, -1.0f,
             1.0f,  1.0f, -1.0f,
             1.0f,  1.0f,  1.0f,
             1.0f,  1.0f,  1.0f,
            -1.0f,  1.0f,  1.0f,
            -1.0f,  1.0f, -1.0f,
            
            -1.0f, -1.0f, -1.0f,
            -1.0f, -1.0f,  1.0f,
             1.0f, -1.0f, -1.0f,
             1.0f, -1.0f, -1.0f,
            -1.0f, -1.0f,  1.0f,
             1.0f, -1.0f,  1.0f
        )
        
        // Настройка VAO/VBO
        vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)
        
        vboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0)
        glEnableVertexAttribArray(0)
        
        // Загрузка текстур куба
        textureId = loadCubeMapTextures()
        
        // Шейдер
        shader = ShaderProgram()
        shader.init("skybox.vert", "skybox.frag")
    }
    
    private fun loadCubeMapTextures(): Int {
        val textureId = glGenTextures()
        glBindTexture(GL_TEXTURE_CUBE_MAP, textureId)
        
        // Файлы: right, left, top, bottom, front, back
        val faces = arrayOf(
            "skybox/right.jpg",
            "skybox/left.jpg",
            "skybox/top.jpg",
            "skybox/bottom.jpg",
            "skybox/front.jpg",
            "skybox/back.jpg"
        )
        
        for (i in faces.indices) {
            val image = loadTextureFromResources(faces[i])
            glTexImage2D(
                GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
                0, GL_RGB, image.width, image.height, 0, GL_RGB, GL_UNSIGNED_BYTE, image.data
            )
        }
        
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)
        
        return textureId
    }
    
    fun render(viewMatrix: Matrix4f, projectionMatrix: Matrix4f) {
        glDepthFunc(GL_LEQUAL)  // меняем depth test для скайбокса
        glDepthMask(false)      // не пишем в depth buffer
        
        shader.use()
        
        // Убираем трансляцию из view matrix
        val view = Matrix4f(viewMatrix)
        view.m30(0f)
        view.m31(0f)
        view.m32(0f)
        
        shader.setUniform("projection", projectionMatrix)
        shader.setUniform("view", view)
        
        glBindVertexArray(vaoId)
        glBindTexture(GL_TEXTURE_CUBE_MAP, textureId)
        glDrawArrays(GL_TRIANGLES, 0, 36)
        
        glDepthMask(true)
        glDepthFunc(GL_LESS)
    }
}