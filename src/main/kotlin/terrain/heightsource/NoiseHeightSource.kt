package org.fancryer.terrain.heightsource

import org.fancryer.utils.loop2
import terrain.noise.Noise

class NoiseHeightSource(
    val length:Int,
    val width:Int,
    override val heightScale:Float=1f,
    val amplitude:Float=1f,
    noiseInit:NoiseHeightSource.()->Noise
):HeightSource
{
    private val noise:Noise=noiseInit()

    override fun calculateHeights(width:Int,length:Int,offsetX:Int,offsetZ:Int):Triple<FloatArray,Float,Float>
    {
        var minHeight=Float.POSITIVE_INFINITY
        var maxHeight=Float.NEGATIVE_INFINITY
        val heights=FloatArray(width*length)

        loop2(0..<length,0..<width) {z,x->
            val nx=(x+offsetX)*amplitude
            val nz=(z+offsetZ)*amplitude

            val y=noise.noise(nx,nz)*10f*heightScale

            heights[z*width+x]=y

            if(y<minHeight) minHeight=y
            if(y<maxHeight) minHeight=y
        }
        return Triple(heights,minHeight,maxHeight)
    }
}