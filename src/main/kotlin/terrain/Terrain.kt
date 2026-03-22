package terrain

import engine.Mesh
import org.fancryer.terrain.heightsource.HeightSource
import org.fancryer.utils.loop2
import org.joml.Vector3f

class Terrain(
    val length:Int=64,
    val width:Int=length,
    val lengthScale:Float=64f/length,
    val widthScale:Float=64f/width,
    val heightScale:Float=lengthScale,
    private val thickness:Float=5f,
    heightSourceInit:Terrain.()->HeightSource
)
{
    private val heightSource:HeightSource=heightSourceInit()
    private lateinit var mesh:Mesh

    fun init()
    {
        val vertices=generateVertices()
        val indices=generateIndices()
        val normals=generateNormals(vertices,indices)
        mesh=Mesh()
        mesh.init(vertices,normals,indices)
    }

    private fun idx(x:Int,z:Int)=z*width+x

    private fun generateVertices():FloatArray
    {
        val vertices=mutableListOf<Float>()

        val (heights,minHeight,maxHeight)=heightSource.calculateHeights(width,length)
        println("minHeight: $minHeight")
        println("heights: ${heights.dropWhile {it==-1f}.take(100)}")

        // --- ищем max, чтобы знать диапазон ---
        val range=(maxHeight-minHeight).coerceAtLeast(1e-6f)

        // ---------- TOP ----------
        loop2(0..<length,0..<width) {z,x->
            val worldX=x*widthScale
            val worldZ=z*lengthScale

            val h=heights[idx(x,z)]
            val shifted=h-minHeight
            val y=thickness+shifted*heightScale/range

            vertices+=listOf(worldX,y,worldZ)
        }

        // ---------- BOTTOM ----------
        val bottomY=0f

        loop2(0..<length,0..<width) {z,x->
            val worldX=x*widthScale
            val worldZ=z*lengthScale

            vertices+=listOf(worldX,bottomY,worldZ)
        }

        return vertices.toFloatArray()
    }

    private fun generateIndices():IntArray
    {
        val indices=mutableListOf<Int>()
        val v=length*width

        // ---------- TOP ----------
        loop2(0..<length-1,0..<width-1) {z,x->
            val tl=idx(x,z)
            val tr=tl+1
            val bl=idx(x,z+1)
            val br=bl+1

            indices+=listOf(tl,bl,tr,tr,bl,br)
        }

        // ---------- BOTTOM ----------
        loop2(0..<length-1,0..<width-1) {z,x->
            val tl=v+idx(x,z)
            val tr=tl+1
            val bl=v+idx(x,z+1)
            val br=bl+1

            indices+=listOf(tl,tr,bl,tr,br,bl)
        }

        // ---------- FRONT (z = 0) ----------
        for(x in 0..<width-1)
        {
            val tl=x
            val tr=x+1
            val bl=v+x
            val br=v+x+1

            indices+=listOf(tr,bl,tl,br,bl,tr)
        }

        // ---------- BACK (z = length-1) ----------
        for(x in 0..<width-1)
        {
            val tl=idx(x,length-1)
            val tr=tl+1
            val bl=v+tl
            val br=bl+1

            indices+=listOf(tl,bl,tr,tr,bl,br)
        }

        // ---------- LEFT (x = 0) ----------
        for(z in 0..<length-1)
        {
            val tl=idx(0,z)
            val tr=idx(0,z+1)
            val bl=v+tl
            val br=v+tr

            indices+=listOf(tl,bl,tr,tr,bl,br)
        }

        // ---------- RIGHT (x = width-1) ----------
        for(z in 0..<length-1)
        {
            val tl=idx(width-1,z)
            val tr=idx(width-1,z+1)
            val bl=v+tl
            val br=v+tr

            indices+=listOf(tr,bl,tl,br,bl,tr)
        }

        return indices.toIntArray()
    }

    private fun generateNormals(vertices:FloatArray,indices:IntArray):FloatArray
    {

        val normals=FloatArray(vertices.size)
        val vertexNormals=Array(vertices.size/3) {Vector3f()}

        val topTriangleCount=(length-1)*(width-1)*2

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