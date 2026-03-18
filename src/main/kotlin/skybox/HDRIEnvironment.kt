package skybox

import org.fancryer.engine.ShaderProgram
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13C.GL_TEXTURE_CUBE_MAP
import org.lwjgl.opengl.GL13C.GL_TEXTURE_CUBE_MAP_POSITIVE_X
import org.lwjgl.opengl.GL20C.*
import org.lwjgl.opengl.GL30C.*
import org.lwjgl.opengl.GL42C.glTexStorage2D
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack

class HDRIEnvironment(private val faceSize:Int=512)
{
    var envCubemap=0; private set
    private var irradianceMap=0
    private var prefilterMap=0
    private var brdfLUTTexture=0

    // VAO для куба (для рендера в cubemap)
    private var cubeVaoId=0
    private var cubeVboId=0

    private var quadVao=0
    private var quadVbo=0

    // FBO для захвата
    private var captureFBO=0
    private var captureRBO=0

    //lateinit var shCoeffs:Array<Vector3d>; private set

    fun loadFromHDR(hdrPath:String)
    {
        initCubeVAO()
        initQuadVAO()
        initCaptureFramebuffer()

        // 1. Загружаем HDR и конвертируем в cubemap
        envCubemap=loadHDRCubemap(hdrPath)

        // 2. Генерируем irradiance map
        irradianceMap=generateIrradianceMap()

        // 3. Генерируем prefilter map
        prefilterMap=generatePrefilterMap()

        // 4. Генерируем BRDF LUT
        brdfLUTTexture=generateBRDFLUT()

        // Очистка
        glDeleteFramebuffers(captureFBO)
        glDeleteRenderbuffers(captureRBO)
    }

    private fun initCubeVAO()
    {
        cubeVaoId=glGenVertexArrays()
        glBindVertexArray(cubeVaoId)

        cubeVboId=glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER,cubeVboId)
        glBufferData(GL_ARRAY_BUFFER,vertices,GL_STATIC_DRAW)

        glVertexAttribPointer(0,3,GL_FLOAT,false,3*4,0)
        glEnableVertexAttribArray(0)

        glBindBuffer(GL_ARRAY_BUFFER,0)
        glBindVertexArray(0)
    }

    private fun initQuadVAO()
    {
        val quadVertices=floatArrayOf(
            -1f,-1f,0f,0f,
            1f,-1f,1f,0f,
            1f,1f,1f,1f,

            -1f,-1f,0f,0f,
            1f,1f,1f,1f,
            -1f,1f,0f,1f
        )

        quadVao=glGenVertexArrays()
        quadVbo=glGenBuffers()

        glBindVertexArray(quadVao)

        glBindBuffer(GL_ARRAY_BUFFER,quadVbo)
        glBufferData(GL_ARRAY_BUFFER,quadVertices,GL_STATIC_DRAW)

        glVertexAttribPointer(0,2,GL_FLOAT,false,4*4,0)
        glEnableVertexAttribArray(0)

        glVertexAttribPointer(1,2,GL_FLOAT,false,4*4,2*4)
        glEnableVertexAttribArray(1)

        glBindVertexArray(0)
    }

    private fun initCaptureFramebuffer()
    {
        captureFBO=glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER,captureFBO)

        captureRBO=glGenRenderbuffers()
        glBindRenderbuffer(GL_RENDERBUFFER,captureRBO)
        glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,faceSize,faceSize)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,captureRBO)

        glBindFramebuffer(GL_FRAMEBUFFER,0)
    }

    private fun loadHDRCubemap(path:String):Int
    {
        // 1. Загружаем HDR как 2D текстуру
        val stack=MemoryStack.stackPush()
        val w=stack.mallocInt(1)
        val h=stack.mallocInt(1)
        val comp=stack.mallocInt(1)

        println("Loading HDR from: $path")
        stbi_set_flip_vertically_on_load(true)
        val data=stbi_loadf(path,w,h,comp,3)
                 ?:throw RuntimeException("Failed to load HDR: $path")

        val hdrTexture=glGenTextures()
        glBindTexture(GL_TEXTURE_2D,hdrTexture)
        println("HDR loaded successfully, texture ID: $hdrTexture")
        glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGB16F,
            w.get(),
            h.get(),
            0,
            GL_RGB,
            GL_FLOAT,
            data
        )
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)

        stbi_image_free(data)

        // 2. Создаём пустую кубическую карту
        val cubemap=glGenTextures()
        glBindTexture(GL_TEXTURE_CUBE_MAP,cubemap)

        for(i in 0 until 6)
        {
            glTexImage2D(
                GL_TEXTURE_CUBE_MAP_POSITIVE_X+i,
                0,
                GL_RGB16F,
                faceSize,
                faceSize,
                0,
                GL_RGB,
                GL_FLOAT,
                0
            )
        }

        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_R,GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MIN_FILTER,GL_LINEAR)
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MAG_FILTER,GL_LINEAR)

        // 3. Конвертируем через шейдер
        convertEquirectangularToCubemap(hdrTexture,cubemap)
        glBindTexture(GL_TEXTURE_CUBE_MAP,cubemap)
        glGenerateMipmap(GL_TEXTURE_CUBE_MAP)
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR)

        // 4. Очищаем временную HDR текстуру
        glDeleteTextures(hdrTexture)

        return cubemap
    }

    private fun <T> inModifiedViewPort(f:()->T):T
    {
        val viewport=IntArray(4)
        glGetIntegerv(GL_VIEWPORT,viewport)

        val framebuffer=IntArray(1)
        glGetIntegerv(GL_FRAMEBUFFER_BINDING,framebuffer)

        val t=f()

        glViewport(viewport[0],viewport[1],viewport[2],viewport[3])
        glBindFramebuffer(GL_FRAMEBUFFER,framebuffer[0])
        return t

    }

    private fun convertEquirectangularToCubemap(hdrTexture:Int,cubemap:Int)
    {
        val conversionShader=ShaderProgram()
        conversionShader.init("ibl/convert.vert","ibl/convert.frag")

        glBindFramebuffer(GL_FRAMEBUFFER,captureFBO)
        inModifiedViewPort {
            glViewport(0,0,faceSize,faceSize)

            val projection=perspective(90f,1f,0.1f,10f)

            conversionShader.use()
            conversionShader.setUniform1i("equirectangularMap",0)

            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D,hdrTexture)

            (0..<6).forEach {
                conversionShader.setUniformMatrix4f("view",captureViews[it])
                conversionShader.setUniformMatrix4f("projection",projection)

                glFramebufferTexture2D(
                    GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,
                    GL_TEXTURE_CUBE_MAP_POSITIVE_X+it,cubemap,0
                )

                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

                glBindVertexArray(cubeVaoId)
                glDrawArrays(GL_TRIANGLES,0,36)
            }
        }
        conversionShader.cleanup()
    }

    private fun generateIrradianceMap():Int
    {
        println("Generating irradiance map...")

        val size=128
        val maxMipLevels=5

        val irradianceMap=glGenTextures()
        glBindTexture(GL_TEXTURE_CUBE_MAP,irradianceMap)
        println("Irradiance map ID: $irradianceMap")

        glTexStorage2D(GL_TEXTURE_CUBE_MAP,1,GL_RGB16F,size,size)

        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_R,GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MIN_FILTER,GL_LINEAR)
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MAG_FILTER,GL_LINEAR)

        // Рендерим в irradiance map
        val irradianceShader=ShaderProgram()
        irradianceShader.init("ibl/irradiance.vert","ibl/irradiance.frag")
        println("Irradiance shader initialized successfully")

        inModifiedViewPort {
            glBindFramebuffer(GL_FRAMEBUFFER,captureFBO)

            glViewport(0,0,size,size)
            glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,size,size)

            val projection=perspective(90f,1f,0.1f,10f)

            irradianceShader.use()
            irradianceShader.setUniform1i("environmentMap",0)

            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_CUBE_MAP,envCubemap)

            for(i in 0..<6)
            {
                irradianceShader.setUniformMatrix4f("view",captureViews[i])
                irradianceShader.setUniformMatrix4f("projection",projection)

                glFramebufferTexture2D(
                    GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,
                    GL_TEXTURE_CUBE_MAP_POSITIVE_X+i,irradianceMap,0
                )

                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

                glBindVertexArray(cubeVaoId)
                glDrawArrays(GL_TRIANGLES,0,36)
            }
        }

        irradianceShader.cleanup()
        return irradianceMap
    }

    private fun generatePrefilterMap():Int
    {
        println("Generating prefilter map...")
        val size=128
        val maxMipLevels=5

        val prefilterMap=glGenTextures()
        glBindTexture(GL_TEXTURE_CUBE_MAP,prefilterMap)

        // создаём все mip уровни
        glTexStorage2D(GL_TEXTURE_CUBE_MAP,maxMipLevels,GL_RGB16F,size,size)

        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_R,GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MAG_FILTER,GL_LINEAR)

        val prefilterShader=ShaderProgram()
        prefilterShader.init("ibl/prefilter.vert","ibl/prefilter.frag")

        val projection=perspective(90f,1f,0.1f,10f)

        prefilterShader.use()
        prefilterShader.setUniform1i("environmentMap",0)

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_CUBE_MAP,envCubemap)

        inModifiedViewPort {
            glBindFramebuffer(GL_FRAMEBUFFER,captureFBO)

            for(mip in 0..<maxMipLevels)
            {
                val mipSize=size shr mip
                val roughness=mip.toFloat()/(maxMipLevels-1)
                println("Generating prefilter map for roughness: $roughness, size: $mipSize")

                glViewport(0,0,mipSize,mipSize)

                glBindRenderbuffer(GL_RENDERBUFFER,captureRBO)
                glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,mipSize,mipSize)

                // Проверяем FBO
                if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)
                {
                    println("Framebuffer not complete for mip $mip")
                }

                prefilterShader.setUniform1f("roughness",roughness)
                prefilterShader.setUniform1f("resolution",mipSize.toFloat())

                for(i in 0..<6)
                {
                    prefilterShader.setUniformMatrix4f("view",captureViews[i])
                    prefilterShader.setUniformMatrix4f("projection",projection)

                    glFramebufferTexture2D(
                        GL_FRAMEBUFFER,
                        GL_COLOR_ATTACHMENT0,
                        GL_TEXTURE_CUBE_MAP_POSITIVE_X+i,
                        prefilterMap,
                        mip  // <-- правильно указываем mip level
                    )

                    // Проверяем FBO после привязки
                    if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)
                    {
                        println("Framebuffer not complete after texture attachment for face $i, mip $mip")
                    }

                    glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

                    glBindVertexArray(cubeVaoId)
                    glDrawArrays(GL_TRIANGLES,0,36)

                    val error=glGetError()
                    if(error!=GL_NO_ERROR)
                    {
                        println("OpenGL error after prefilter render: $error")
                    }
                }
            }
        }

        glBindFramebuffer(GL_FRAMEBUFFER,0)
        prefilterShader.cleanup()

        return prefilterMap
    }

    private fun generateBRDFLUT():Int
    {

        val size=512

        val brdfLUT=glGenTextures()
        glBindTexture(GL_TEXTURE_2D,brdfLUT)

        glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RG16F,
            size,
            size,
            0,
            GL_RG,
            GL_FLOAT,
            0
        )

        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)

        val brdfShader=ShaderProgram()
        brdfShader.init("ibl/brdf.vert","ibl/brdf.frag")

        glBindFramebuffer(GL_FRAMEBUFFER,captureFBO)

        inModifiedViewPort {

            glViewport(0,0,size,size)

            glBindRenderbuffer(GL_RENDERBUFFER,captureRBO)
            glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,size,size)

            glFramebufferTexture2D(
                GL_FRAMEBUFFER,
                GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D,
                brdfLUT,
                0
            )

            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

            brdfShader.use()

            glBindVertexArray(quadVao)
            glDrawArrays(GL_TRIANGLES,0,6)
        }

        glBindFramebuffer(GL_FRAMEBUFFER,0)

        brdfShader.cleanup()

        return brdfLUT
    }

//    fun computeSH(cubemap:Array<Array<Vector3d>>):Array<Vector3d>
//    {
//        val sh=Array(9) {Vector3d(0.0,0.0,0.0)}
//
//        var totalWeight=0.0
//
//        val size=sqrt(cubemap[0].size.toDouble()).toInt()
//
//        for(face in 0..<6)
//        {
//            for(y in 0..<size)
//            {
//                for(x in 0..<size)
//                {
//                    val u=2.0*(x+0.5)/size-1.0
//                    val v=2.0*(y+0.5)/size-1.0
//
//                    val dir=directionFromCubemap(face,u,v)
//                    val color=cubemap[face][y*size+x]
//
//                    if(!color.isFinite) continue
//
//                    val maxVal=1000.0   // или 100.0 — зависит от сцены
//                    val rClamped=min(color.x,maxVal)
//                    val gClamped=min(color.y,maxVal)
//                    val bClamped=min(color.z,maxVal)
//                    val safeColor=Vector3d(rClamped,gClamped,bClamped)
//
//                    val weight=solidAngle(u,v,size)
//                    if(!weight.isFinite())
//                    {
//                        println("Non-finite weight at face: $face, y: $y, x: $x:")
//                        println("uv: ($u, $v)")
//                        println("dir: $dir")
//                        println("color: $color")
//                        continue
//                    }
//
//                    val basis=shBasis(dir)
//                    for(i in 0..<9)
//                    {
//                        val term=safeColor*(basis[i]*weight)
//                        if(!term.isFinite()) println("Non-finite term at face $face, x=$x, y=$y: term=$term")
//                        sh[i]+=term
//                    }
//
//                    totalWeight+=weight
//                }
//            }
//        }
//
//        println("totalWeight = $totalWeight")
//
//        for(i in 0..<9)
//            sh[i]*=4.0f*Math.PI.toFloat()/totalWeight
//
//        return sh
//    }

//    fun directionFromCubemap(face:Int,u:Double,v:Double):Vector3d=when(face)
//    {
//        0   ->Vector3d(1.0,-v,-u)
//        1   ->Vector3d(-1.0,-v,u)
//        2   ->Vector3d(u,1.0,v)
//        3   ->Vector3d(u,-1.0,-v)
//        4   ->Vector3d(u,-v,1.0)
//        else->Vector3d(-u,-v,-1.0)
//    }.normalize()
//
//    fun solidAngle(u:Double,v:Double,size:Int):Double
//    {
//        val inv=2.0/size
//        val x0=u-inv
//        val y0=v-inv
//        val x1=u+inv
//        val y1=v+inv
//
//        return areaElement(x0,y0)-
//               areaElement(x0,y1)-
//               areaElement(x1,y0)+
//               areaElement(x1,y1)
//    }
//
//    fun shBasis(d:Vector3d):DoubleArray
//    {
//        val x=d.x
//        val y=d.y
//        val z=d.z
//
//        return doubleArrayOf(
//            0.282095,
//            0.488603*y,
//            0.488603*z,
//            0.488603*x,
//            1.092548*x*y,
//            1.092548*y*z,
//            0.315392*(3*z*z-1),
//            1.092548*x*z,
//            0.546274*(x*x-y*y)
//        )
//    }

//    fun areaElement(x:Double,y:Double):Double
//    {
//        val denom=sqrt(x*x+y*y+1.0)
//        return if(denom.isFinite()) atan2(x*y,denom) else 0.0
//    }

    fun bindForSkybox(unit:Int=0)
    {
        glActiveTexture(GL_TEXTURE0+unit)
        glBindTexture(GL_TEXTURE_CUBE_MAP,envCubemap)
    }

    fun bindForLighting(irradianceUnit:Int=1,prefilterUnit:Int=2,brdfUnit:Int=3)
    {
        glActiveTexture(GL_TEXTURE0+irradianceUnit)
        glBindTexture(GL_TEXTURE_CUBE_MAP,irradianceMap)

        glActiveTexture(GL_TEXTURE0+prefilterUnit)
        glBindTexture(GL_TEXTURE_CUBE_MAP,prefilterMap)

        glActiveTexture(GL_TEXTURE0+brdfUnit)
        glBindTexture(GL_TEXTURE_2D,brdfLUTTexture)
    }

    fun cleanup()
    {
        glDeleteTextures(envCubemap)
        glDeleteTextures(irradianceMap)
        glDeleteTextures(prefilterMap)
        glDeleteTextures(brdfLUTTexture)
        glDeleteVertexArrays(cubeVaoId)
        glDeleteBuffers(cubeVboId)
        glDeleteVertexArrays(quadVao)
        glDeleteBuffers(quadVbo)
    }

    companion object
    {
        private val vertices=floatArrayOf(
            -1.0f,-1.0f,-1.0f,1.0f,-1.0f,-1.0f,1.0f,1.0f,-1.0f,
            1.0f,1.0f,-1.0f,-1.0f,1.0f,-1.0f,-1.0f,-1.0f,-1.0f,

            -1.0f,-1.0f,1.0f,1.0f,-1.0f,1.0f,1.0f,1.0f,1.0f,
            1.0f,1.0f,1.0f,-1.0f,1.0f,1.0f,-1.0f,-1.0f,1.0f,

            -1.0f,1.0f,1.0f,-1.0f,1.0f,-1.0f,-1.0f,-1.0f,-1.0f,
            -1.0f,-1.0f,-1.0f,-1.0f,-1.0f,1.0f,-1.0f,1.0f,1.0f,

            1.0f,1.0f,1.0f,1.0f,1.0f,-1.0f,1.0f,-1.0f,-1.0f,
            1.0f,-1.0f,-1.0f,1.0f,-1.0f,1.0f,1.0f,1.0f,1.0f,

            -1.0f,-1.0f,-1.0f,1.0f,-1.0f,-1.0f,1.0f,-1.0f,1.0f,
            1.0f,-1.0f,1.0f,-1.0f,-1.0f,1.0f,-1.0f,-1.0f,-1.0f,

            -1.0f,1.0f,-1.0f,1.0f,1.0f,-1.0f,1.0f,1.0f,1.0f,
            1.0f,1.0f,1.0f,-1.0f,1.0f,1.0f,-1.0f,1.0f,-1.0f
        )

        private val captureViews=arrayOf(
            lookAt(0f,0f,0f,1f,0f,0f,0f,-1f,0f),  // right
            lookAt(0f,0f,0f,-1f,0f,0f,0f,-1f,0f), // left
            lookAt(0f,0f,0f,0f,1f,0f,0f,0f,1f),   // top
            lookAt(0f,0f,0f,0f,-1f,0f,0f,0f,-1f), // bottom
            lookAt(0f,0f,0f,0f,0f,1f,0f,-1f,0f),  // front
            lookAt(0f,0f,0f,0f,0f,-1f,0f,-1f,0f)   // back
        )

        private fun lookAt(
            eyeX:Float,
            eyeY:Float,
            eyeZ:Float,
            centerX:Float,
            centerY:Float,
            centerZ:Float,
            upX:Float,
            upY:Float,
            upZ:Float
        ):FloatArray=lookAt(
            Vector3f(eyeX,eyeY,eyeZ),
            Vector3f(centerX,centerY,centerZ),
            Vector3f(upX,upY,upZ)
        )

        private fun lookAt(
            eye:Vector3f,
            center:Vector3f,
            up:Vector3f
        ):FloatArray=Matrix4f().setLookAt(eye,center,up).get(FloatArray(16))

        private fun perspective(fov:Float,aspect:Float,near:Float,far:Float):FloatArray=
            Matrix4f().setPerspective(fov*(Math.PI.toFloat()/180f),aspect,near,far)
                .get(FloatArray(16))
    }
}