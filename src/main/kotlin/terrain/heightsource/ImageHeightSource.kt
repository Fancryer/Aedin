package org.fancryer.terrain.heightsource

import org.fancryer.engine.Sampling
import org.fancryer.engine.Sampling.*
import org.fancryer.engine.sampleBicubic
import org.fancryer.engine.sampleBilinear
import org.fancryer.engine.sampleNearest
import org.fancryer.utils.loop2
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sign

class ImageHeightSource(
    private val path:String,
    override val heightScale:Float=1f,
    private val sampling:Sampling=Bilinear
):HeightSource
{
    private val srcSrcHSrcW by lazy {loadHeightmap(path)}

    override fun calculateHeights(
        width:Int,
        length:Int,
        offsetX:Int,
        offsetZ:Int
    ):Triple<FloatArray,Float,Float>
    {
        val (src,srcH,srcW)=srcSrcHSrcW

        val heights=FloatArray(width*length)
        var minHeight=Float.POSITIVE_INFINITY
        var maxHeight=Float.NEGATIVE_INFINITY


//        fun noise(x:Int,z:Int):Float
//        {
//            var n=x*374761393+z*668265263 // большие простые
//            n=(n xor (n shr 13))*1274126177
//            n=n xor (n shr 16)
//            return (n and 0x7fffffff)/2.1474836E9f // [0..1]
//        }

        loop2(0..<length,0..<width) {z,x->
            // --- нормализованные координаты ---
            val u=if(width>1) x.toFloat()/(width-1) else 0f
            val v=if(length>1) z.toFloat()/(length-1) else 0f


            //val noise=noise(x,z)
            val h=sample(src,srcW,srcH,u,v)//+noise*0.005f

            val i=z*width+x

            val scaled=h*heightScale
            heights[i]=scaled

            if(scaled<minHeight) minHeight=scaled
            if(scaled>maxHeight) maxHeight=scaled
        }

        return Triple(heights,minHeight,maxHeight)
    }

    private fun sample(src:FloatArray,w:Int,h:Int,u:Float,v:Float):Float=when(sampling)
    {
        Nearest ->::sampleNearest
        Bilinear->::sampleBilinear
        Bicubic ->::sampleBicubic
    }(src,w,h,u,v)

    private fun loadHeightmap(path:String):Triple<FloatArray,Int,Int>
    {
        MemoryStack.stackPush().use {stack->
            val w=stack.mallocInt(1)
            val h=stack.mallocInt(1)
            val channels=stack.mallocInt(1)

            stbi_set_flip_vertically_on_load(true)

            val data=stbi_loadf(path,w,h,channels,0)
                     ?:error("Failed to load image: ${stbi_failure_reason()}")

            val width=w[0]
            val height=h[0]

            val heights=FloatArray(width*height)

            loop2(0..<height,0..<width) {z,x->
                val i=z*width+x
                val ch = channels[0]

                val value = when (ch) {
                    1 -> data.get(i)
                    3 -> {
                        val base = i * 3
                        (data.get(base) + data.get(base + 1) + data.get(base + 2)) / 3f
                    }
                    4 -> {
                        val base = i * 4
                        (data.get(base) + data.get(base + 1) + data.get(base + 2)) / 3f
                    }
                    else -> error("Unsupported channels: $ch")
                }
                val h=value*2f-1f//data.get(i)*2f-1f
                val shaped=h.sign*h.absoluteValue.pow(1.5f)
                heights[i]=shaped
            }

            stbi_image_free(data)

            return Triple(heights,height,width)
        }
    }
}