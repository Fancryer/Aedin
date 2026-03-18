package terrain

import org.fancryer.engine.Mesh

class Terrain(
    private val size:Int=64,
    private val scale:Float=1.0f
)
{
    private lateinit var mesh:Mesh
    private val noise=Noise()
}