package skybox

import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13C.GL_TEXTURE_CUBE_MAP
import org.lwjgl.opengl.GL13C.GL_TEXTURE_CUBE_MAP_POSITIVE_X
import org.lwjgl.opengl.GL14C.GL_TEXTURE_LOD_BIAS
import org.lwjgl.opengl.GL20C.*
import org.lwjgl.opengl.GL30C.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import java.io.File
import java.nio.FloatBuffer

class HDRIEnvironment(private val faceSize: Int = 512) {
    private var envCubemap = 0
    private var irradianceMap = 0
    private var prefilterMap = 0
    private var brdfLUTTexture = 0
    
    // VAO для куба (для рендера в cubemap)
    private var cubeVaoId = 0
    private var cubeVboId = 0
    
    // FBO для захвата
    private var captureFBO = 0
    private var captureRBO = 0
    
    fun loadFromHDR(hdrPath: String) {
        initCubeVAO()
        initCaptureFramebuffer()
        
        // 1. Загружаем HDR и конвертируем в cubemap
        envCubemap = loadHDRCubemap(hdrPath)
        
        // 2. Генерируем irradiance map
        irradianceMap = generateIrradianceMap()
        
        // 3. Генерируем prefilter map
        prefilterMap = generatePrefilterMap()
        
        // 4. Генерируем BRDF LUT
        brdfLUTTexture = generateBRDFLUT()
        
        // Очистка
        glDeleteFramebuffers(captureFBO)
        glDeleteRenderbuffers(captureRBO)
    }
    
    private fun initCubeVAO() {
        val vertices = floatArrayOf(
            -1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f,  1.0f, -1.0f,
             1.0f,  1.0f, -1.0f, -1.0f,  1.0f, -1.0f, -1.0f, -1.0f, -1.0f,
            
            -1.0f, -1.0f,  1.0f, 1.0f, -1.0f,  1.0f, 1.0f,  1.0f,  1.0f,
             1.0f,  1.0f,  1.0f, -1.0f,  1.0f,  1.0f, -1.0f, -1.0f,  1.0f,
            
            -1.0f,  1.0f,  1.0f, -1.0f,  1.0f, -1.0f, -1.0f, -1.0f, -1.0f,
            -1.0f, -1.0f, -1.0f, -1.0f, -1.0f,  1.0f, -1.0f,  1.0f,  1.0f,
            
             1.0f,  1.0f,  1.0f,  1.0f,  1.0f, -1.0f,  1.0f, -1.0f, -1.0f,
             1.0f, -1.0f, -1.0f,  1.0f, -1.0f,  1.0f,  1.0f,  1.0f,  1.0f,
            
            -1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f,  1.0f,
             1.0f, -1.0f,  1.0f, -1.0f, -1.0f,  1.0f, -1.0f, -1.0f, -1.0f,
            
            -1.0f,  1.0f, -1.0f, 1.0f,  1.0f, -1.0f, 1.0f,  1.0f,  1.0f,
             1.0f,  1.0f,  1.0f, -1.0f,  1.0f,  1.0f, -1.0f,  1.0f, -1.0f
        )
        
        cubeVaoId = glGenVertexArrays()
        glBindVertexArray(cubeVaoId)
        
        cubeVboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, cubeVboId)
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0)
        glEnableVertexAttribArray(0)
        
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)
    }
    
    private fun initCaptureFramebuffer() {
        captureFBO = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, captureFBO)
        
        captureRBO = glGenRenderbuffers()
        glBindRenderbuffer(GL_RENDERBUFFER, captureRBO)
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, faceSize, faceSize)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, captureRBO)
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }
    
    private fun loadHDRCubemap(path: String): Int {
        // 1. Загружаем HDR как 2D текстуру
        val stack = MemoryStack.stackPush()
        val w = stack.mallocInt(1)
        val h = stack.mallocInt(1)
        val comp = stack.mallocInt(1)
        
        println("Loading HDR from: $path")
        stbi_set_flip_vertically_on_load(true)
        val data = stbi_loadf(path, w, h, comp, 3)
            ?: throw RuntimeException("Failed to load HDR: $path")
        
        val hdrTexture = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, hdrTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, w.get(), h.get(), 0, GL_RGB, GL_FLOAT, data)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        
        stbi_image_free(data)
        
        // 2. Создаём пустую кубическую карту
        val cubemap = glGenTextures()
        glBindTexture(GL_TEXTURE_CUBE_MAP, cubemap)
        
        for (i in 0 until 6) {
            glTexImage2D(
                GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
                0, GL_RGB16F, faceSize, faceSize, 0, GL_RGB, GL_FLOAT, 0
            )
        }
        
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        
        // 3. Конвертируем через шейдер
        convertEquirectangularToCubemap(hdrTexture, cubemap)
        
        // 4. Очищаем временную HDR текстуру
        glDeleteTextures(hdrTexture)
        
        return cubemap
    }
    
    private fun convertEquirectangularToCubemap(hdrTexture: Int, cubemap: Int) {
        val conversionShader = ShaderProgram()
        conversionShader.init("ibl/convert.vert", "ibl/convert.frag")
        
        glBindFramebuffer(GL_FRAMEBUFFER, captureFBO)
        glViewport(0, 0, faceSize, faceSize)
        
        val captureViews = getCaptureViews()
        val projection = perspective(90f, 1f, 0.1f, 10f)
        
        conversionShader.use()
        conversionShader.setUniform("equirectangularMap", 0)
        
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, hdrTexture)
        
        for (i in 0 until 6) {
            conversionShader.setUniform("view", captureViews[i])
            conversionShader.setUniform("projection", projection)
            
            glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, cubemap, 0
            )
            
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            
            glBindVertexArray(cubeVaoId)
            glDrawArrays(GL_TRIANGLES, 0, 36)
        }
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        conversionShader.cleanup()
    }
    
    private fun generateIrradianceMap(): Int {
        val irradianceMap = glGenTextures()
        glBindTexture(GL_TEXTURE_CUBE_MAP, irradianceMap)
        
        for (i in 0 until 6) {
            glTexImage2D(
                GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
                0, GL_RGB16F, 32, 32, 0, GL_RGB, GL_FLOAT, 0
            )
        }
        
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        
        // Рендерим в irradiance map
        val irradianceShader = ShaderProgram()
        irradianceShader.init("ibl/irradiance.vert", "ibl/irradiance.frag")
        
        glBindFramebuffer(GL_FRAMEBUFFER, captureFBO)
        glViewport(0, 0, 32, 32)
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, 32, 32)
        
        val captureViews = getCaptureViews()
        val projection = perspective(90f, 1f, 0.1f, 10f)
        
        irradianceShader.use()
        irradianceShader.setUniform("environmentMap", 0)
        
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_CUBE_MAP, envCubemap)
        
        for (i in 0 until 6) {
            irradianceShader.setUniform("view", captureViews[i])
            irradianceShader.setUniform("projection", projection)
            
            glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, irradianceMap, 0
            )
            
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            
            glBindVertexArray(cubeVaoId)
            glDrawArrays(GL_TRIANGLES, 0, 36)
        }
        
        irradianceShader.cleanup()
        return irradianceMap
    }
    
    private fun generatePrefilterMap(): Int {
        val prefilterMap = glGenTextures()
        glBindTexture(GL_TEXTURE_CUBE_MAP, prefilterMap)
        
        for (i in 0 until 6) {
            glTexImage2D(
                GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
                0, GL_RGB16F, 128, 128, 0, GL_RGB, GL_FLOAT, 0
            )
        }
        
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        
        glGenerateMipmap(GL_TEXTURE_CUBE_MAP)
        
        val prefilterShader = ShaderProgram()
        prefilterShader.init("ibl/prefilter.vert", "ibl/prefilter.frag")
        
        glBindFramebuffer(GL_FRAMEBUFFER, captureFBO)
        
        val captureViews = getCaptureViews()
        val projection = perspective(90f, 1f, 0.1f, 10f)
        
        prefilterShader.use()
        prefilterShader.setUniform("environmentMap", 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_CUBE_MAP, envCubemap)
        
        var maxMipLevels = 5
        for (mip in 0 until maxMipLevels) {
            val mipSize = 128 / (1 shl mip)
            glViewport(0, 0, mipSize, mipSize)
            
            val roughness = mip.toFloat() / (maxMipLevels - 1).toFloat()
            prefilterShader.setUniform("roughness", roughness)
            
            glBindRenderbuffer(GL_RENDERBUFFER, captureRBO)
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, mipSize, mipSize)
            
            for (i in 0 until 6) {
                prefilterShader.setUniform("view", captureViews[i])
                prefilterShader.setUniform("projection", projection)
                
                glFramebufferTexture2D(
                    GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                    GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, prefilterMap, mip
                )
                
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                
                glBindVertexArray(cubeVaoId)
                glDrawArrays(GL_TRIANGLES, 0, 36)
            }
        }
        
        prefilterShader.cleanup()
        return prefilterMap
    }
    
    private fun generateBRDFLUT(): Int {
        val brdfShader = ShaderProgram()
        brdfShader.init("ibl/brdf.vert", "ibl/brdf.frag")
        
        val brdfTexture = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, brdfTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RG16F, 512, 512, 0, GL_RG, GL_FLOAT, 0)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        
        glBindFramebuffer(GL_FRAMEBUFFER, captureFBO)
        glViewport(0, 0, 512, 512)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, brdfTexture, 0)
        
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        
        brdfShader.use()
        
        glBindVertexArray(cubeVaoId)  // используем тот же VAO
        glDrawArrays(GL_TRIANGLES, 0, 36)
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        brdfShader.cleanup()
        
        return brdfTexture
    }
    
    private fun getCaptureViews(): Array<FloatArray> {
        return arrayOf(
            lookAt(0f, 0f, 0f, 1f, 0f, 0f, 0f, -1f, 0f),  // right
            lookAt(0f, 0f, 0f, -1f, 0f, 0f, 0f, -1f, 0f), // left
            lookAt(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),   // top
            lookAt(0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, -1f), // bottom
            lookAt(0f, 0f, 0f, 0f, 0f, 1f, 0f, -1f, 0f),  // front
            lookAt(0f, 0f, 0f, 0f, 0f, -1f, 0f, -1f, 0f)   // back
        )
    }
    
    private fun lookAt(
        eyeX: Float, eyeY: Float, eyeZ: Float,
        centerX: Float, centerY: Float, centerZ: Float,
        upX: Float, upY: Float, upZ: Float
    ): FloatArray = Matrix4f().setLookAt(eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ)
        .get(FloatArray(16))
    
    private fun perspective(fov: Float, aspect: Float, near: Float, far: Float): FloatArray =
        Matrix4f().setPerspective(fov * (Math.PI.toFloat() / 180f), aspect, near, far)
            .get(FloatArray(16))
    
    fun bindForSkybox(unit: Int = 0) {
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_CUBE_MAP, envCubemap)
    }
    
    fun bindForLighting(irradianceUnit: Int = 0, prefilterUnit: Int = 1, brdfUnit: Int = 2) {
        glActiveTexture(GL_TEXTURE0 + irradianceUnit)
        glBindTexture(GL_TEXTURE_CUBE_MAP, irradianceMap)
        
        glActiveTexture(GL_TEXTURE0 + prefilterUnit)
        glBindTexture(GL_TEXTURE_CUBE_MAP, prefilterMap)
        
        glActiveTexture(GL_TEXTURE0 + brdfUnit)
        glBindTexture(GL_TEXTURE_2D, brdfLUTTexture)
    }
    
    fun cleanup() {
        glDeleteTextures(envCubemap)
        glDeleteTextures(irradianceMap)
        glDeleteTextures(prefilterMap)
        glDeleteTextures(brdfLUTTexture)
        glDeleteVertexArrays(cubeVaoId)
        glDeleteBuffers(cubeVboId)
    }
}