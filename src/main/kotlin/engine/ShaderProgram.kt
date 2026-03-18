package org.fancryer.engine

import org.joml.Matrix4f
import org.lwjgl.opengl.GL20C.*
import org.lwjgl.system.MemoryStack
import kotlin.io.path.Path

class ShaderProgram
{
    private var programId=0

    private enum class ShaderType
    {
        Vertex,
        Fragment
    }


    fun init(vertexPath:String,fragmentPath:String)
    {
        // Loading shader sources
        val vertexSource=loadShaderSource(vertexPath)
        val fragmentSource=loadShaderSource(fragmentPath)

        // Creating and compiling a vertex shader
        initFromStrings(
            vertexSource,
            fragmentSource,
            Path(vertexPath).fileName.toString(),
            Path(fragmentPath).fileName.toString()
        )
    }

    fun initFromStrings(
        vertexCode:String,
        fragmentCode:String,
        vertexName:String="`Unknown vertex shader`",
        fragmentName:String="`Unknown fragment shader`"
    )
    {
        val vertexId=glCreateShader(GL_VERTEX_SHADER)
        glShaderSource(vertexId,vertexCode)
        glCompileShader(vertexId)
        checkShaderCompilation(vertexId,ShaderType.Vertex,vertexName)

        val fragmentId=glCreateShader(GL_FRAGMENT_SHADER)
        glShaderSource(fragmentId,fragmentCode)
        glCompileShader(fragmentId)
        checkShaderCompilation(fragmentId,ShaderType.Fragment,fragmentName)

        programId=glCreateProgram()
        glAttachShader(programId,vertexId)
        glAttachShader(programId,fragmentId)
        glLinkProgram(programId)
        checkProgramLinking(programId)

        glDetachShader(programId,vertexId)
        glDetachShader(programId,fragmentId)
        glDeleteShader(vertexId)
        glDeleteShader(fragmentId)
    }

    fun use()=glUseProgram(programId)

    fun getUniformLocation(name:String):Int=glGetUniformLocation(programId,name)

    fun setUniform1i(name:String,value:Int)=
        glUniform1i(getUniformLocation(name),value)

    fun setUniform1f(name:String,value:Float)=
        glUniform1f(getUniformLocation(name),value)

    fun setUniform3f(name:String,v0:Float,v1:Float,v2:Float)=
        glUniform3f(getUniformLocation(name),v0,v1,v2)

    fun setUniform3d(name:String,v0:Double,v1:Double,v2:Double)=
        setUniform3f(name,v0.toFloat(),v1.toFloat(),v2.toFloat())

    fun setUniform3i(name:String,v0:Int,v1:Int,v2:Int)=
        glUniform3i(getUniformLocation(name),v0,v1,v2)

    fun setUniformMatrix4f(name:String,matrix:FloatArray)=
        MemoryStack.stackPush().use {
            val fb=it.mallocFloat(16)
            fb.put(matrix).flip()
            glUniformMatrix4fv(getUniformLocation(name),false,matrix)
        }

    fun setUniformMatrix4f(name:String,matrix:Matrix4f)=
        MemoryStack.stackPush().use {
            val fb=it.mallocFloat(16)
            matrix.get(fb)
            glUniformMatrix4fv(getUniformLocation(name),false,fb)
        }

    fun cleanup()=glDeleteProgram(programId)

    private fun loadShaderSource(path:String):String=
        javaClass.classLoader
            .getResourceAsStream(path)
            ?.bufferedReader()
            ?.use {it.readText()}
        ?:throw RuntimeException("Shader file not found: $path")

    private fun checkShaderCompilation(shaderId:Int,type:ShaderType,name:String)
    {
        val status=glGetShaderi(shaderId,GL_COMPILE_STATUS)
        if(status!=GL_FALSE) return
        val log=glGetShaderInfoLog(shaderId)
        throw RuntimeException("$type shader '$name' compilation failed:\n$log")
    }

    private fun checkProgramLinking(programId:Int)
    {
        val status=glGetProgrami(programId,GL_LINK_STATUS)
        if(status!=GL_FALSE) return
        val log=glGetProgramInfoLog(programId)
        throw RuntimeException("Program linking failed:\n$log")
    }
}