package engine

import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL15C.*
import org.lwjgl.opengl.GL20C.*
import org.lwjgl.opengl.GL30C.*

class Mesh
{
    var vaoId=0; private set
    var vboPosId=0; private set
    var vboNormalId=0; private set
    var eboId=0; private set
    var vertexCount=0; private set
    var indexCount=0; private set

    fun init(vertices:FloatArray,normals:FloatArray,indices:IntArray)
    {
        require(vertices.size==normals.size) {"Vertices and normals must have same size"}

        vertexCount=vertices.size/3
        indexCount=indices.size

        vaoId=glGenVertexArrays()
        glBindVertexArray(vaoId)

        // VBO для позиций (location = 0)
        vboPosId=glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER,vboPosId)
        glBufferData(GL_ARRAY_BUFFER,vertices,GL_STATIC_DRAW)
        glVertexAttribPointer(0,3,GL_FLOAT,false,3*4,0)
        glEnableVertexAttribArray(0)

        // VBO для нормалей (location = 1)
        vboNormalId=glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER,vboNormalId)
        glBufferData(GL_ARRAY_BUFFER,normals,GL_STATIC_DRAW)
        glVertexAttribPointer(1,3,GL_FLOAT,false,3*4,0)
        glEnableVertexAttribArray(1)

        // EBO
        eboId=glGenBuffers()
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,eboId)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER,indices,GL_STATIC_DRAW)

        glBindBuffer(GL_ARRAY_BUFFER,0)
        glBindVertexArray(0)
    }

    fun render()
    {
        glBindVertexArray(vaoId)
        glDrawElements(GL_TRIANGLES,indexCount,GL_UNSIGNED_INT,0)
        glBindVertexArray(0)
    }

    fun cleanup()
    {
        glDeleteVertexArrays(vaoId)
        glDeleteBuffers(vboPosId)
        glDeleteBuffers(vboNormalId)
        glDeleteBuffers(eboId)
    }
}