package terrain

import engine.Mesh
import org.joml.Vector3f
import terrain.noise.Noise
import terrain.noise.PerlinNoise

class Terrain(private val size:Int=64,private val scale:Float=1.0f,private val seed:Int=42)
{
    private lateinit var mesh:Mesh
    private val noise:Noise=generateNoise()

    private fun generateNoise():Noise=PerlinNoise(seed)//.let(::RidgedNoise)

    private val thickness=5f

    fun init()
    {
        val vertices=generateVertices()
        val indices=generateIndices()
        val normals=generateNormals(vertices,indices)
        mesh=Mesh()
        mesh.init(vertices,normals,indices)
    }

    private fun generateVertices():FloatArray
    {
        val vertices=mutableListOf<Float>()
        val noiseScale=0.1f

        var minHeight=Float.POSITIVE_INFINITY

        // --- верхняя поверхность ---
        val heights=FloatArray(size*size)

        for(z in 0..<size)
        {
            for(x in 0..<size)
            {

                val nx=x*noiseScale
                val nz=z*noiseScale

                val y=noise.noise(nx,nz)*10f

                heights[z*size+x]=y

                if(y<minHeight) minHeight=y
            }
        }

        // записываем верх
        for(z in 0 until size)
        {
            for(x in 0 until size)
            {

                val worldX=x*scale
                val worldZ=z*scale
                val y=heights[z*size+x]

                vertices.add(worldX)
                vertices.add(y)
                vertices.add(worldZ)
            }
        }

        // --- плоское дно ---
        val bottomY=minHeight-thickness

        for(z in 0 until size)
        {
            for(x in 0 until size)
            {

                val worldX=x*scale
                val worldZ=z*scale

                vertices.add(worldX)
                vertices.add(bottomY)
                vertices.add(worldZ)
            }
        }

        return vertices.toFloatArray()
    }

    private fun generateIndices():IntArray
    {
        val indices=mutableListOf<Int>()
        val v=size*size

        // ---------- TOP ----------
        for(z in 0 until size-1)
        {
            for(x in 0 until size-1)
            {
                val tl=z*size+x
                val tr=tl+1
                val bl=(z+1)*size+x
                val br=bl+1

                indices.add(tl)
                indices.add(bl)
                indices.add(tr)

                indices.add(tr)
                indices.add(bl)
                indices.add(br)
            }
        }

        // ---------- BOTTOM ----------
        for(z in 0 until size-1)
        {
            for(x in 0 until size-1)
            {
                val tl=v+z*size+x
                val tr=tl+1
                val bl=v+(z+1)*size+x
                val br=bl+1

                indices.add(tl)
                indices.add(tr)
                indices.add(bl)

                indices.add(tr)
                indices.add(br)
                indices.add(bl)
            }
        }

        // ---------- FRONT (z = 0) ----------
        for(x in 0 until size-1)
        {
            val tl=x
            val tr=x+1
            val bl=v+x
            val br=v+x+1

            indices.add(tr)
            indices.add(bl)
            indices.add(tl)

            indices.add(br)
            indices.add(bl)
            indices.add(tr)
        }

        // ---------- BACK (z = size-1) ----------
        for(x in 0 until size-1)
        {
            val tl=(size-1)*size+x
            val tr=tl+1
            val bl=v+(size-1)*size+x
            val br=bl+1

            indices.add(tl)
            indices.add(bl)
            indices.add(tr)

            indices.add(tr)
            indices.add(bl)
            indices.add(br)
        }

        // ---------- LEFT (x = 0) ----------
        for(z in 0 until size-1)
        {
            val tl=z*size
            val tr=(z+1)*size
            val bl=v+z*size
            val br=v+(z+1)*size

            indices.add(tl)
            indices.add(bl)
            indices.add(tr)

            indices.add(tr)
            indices.add(bl)
            indices.add(br)
        }

        // ---------- RIGHT (x = size-1) ----------
        for(z in 0 until size-1)
        {
            val tl=z*size+size-1
            val tr=(z+1)*size+size-1
            val bl=v+z*size+size-1
            val br=v+(z+1)*size+size-1

            indices.add(tr)
            indices.add(bl)
            indices.add(tl)

            indices.add(br)
            indices.add(bl)
            indices.add(tr)
        }

        return indices.toIntArray()
    }

    private fun generateNormals(vertices:FloatArray,indices:IntArray):FloatArray
    {

        val normals=FloatArray(vertices.size)
        val vertexNormals=Array(vertices.size/3) {Vector3f()}

        val topTriangleCount=(size-1)*(size-1)*2

        var triIndex=0

        for(i in indices.indices step 3)
        {

            val i1=indices[i]*3
            val i2=indices[i+1]*3
            val i3=indices[i+2]*3

            val v1=Vector3f(vertices[i1],vertices[i1+1],vertices[i1+2])
            val v2=Vector3f(vertices[i2],vertices[i2+1],vertices[i2+2])
            val v3=Vector3f(vertices[i3],vertices[i3+1],vertices[i3+2])

            val edge1=Vector3f(v2).sub(v1)
            val edge2=Vector3f(v3).sub(v1)

            val normal=Vector3f(edge1).cross(edge2).normalize()

            if(triIndex<topTriangleCount)
            {
                // smooth shading (верх)
                vertexNormals[indices[i]].add(normal)
                vertexNormals[indices[i+1]].add(normal)
                vertexNormals[indices[i+2]].add(normal)
            }
            else
            {
                // flat shading (дно + стенки)
                vertexNormals[indices[i]].set(normal)
                vertexNormals[indices[i+1]].set(normal)
                vertexNormals[indices[i+2]].set(normal)
            }

            triIndex++
        }

        for(i in vertexNormals.indices)
        {
            val n=vertexNormals[i].normalize()
            normals[i*3]=n.x
            normals[i*3+1]=n.y
            normals[i*3+2]=n.z
        }

        return normals
    }

    fun render()=mesh.render()

    fun cleanup()=mesh.cleanup()
}