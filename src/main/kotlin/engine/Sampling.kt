package org.fancryer.engine

import kotlin.math.roundToInt

enum class Sampling
{
    Nearest,
    Bilinear,
    Bicubic
}

fun sampleNearest(src:FloatArray,w:Int,h:Int,u:Float,v:Float):Float
{
    val x=((u*w-0.5f).roundToInt()).coerceIn(0,w-1)
    val z=((v*h-0.5f).roundToInt()).coerceIn(0,h-1)
    return src[z*w+x]
}

fun sampleBilinear(src:FloatArray,w:Int,h:Int,u:Float,v:Float):Float
{
    val fx=u*w-0.5f
    val fz=v*h-0.5f

    val x0=fx.toInt()
    val z0=fz.toInt()
    val x1=minOf(x0+1,w-1)
    val z1=minOf(z0+1,h-1)

    val tx=fx-x0
    val tz=fz-z0

    val h00=src[z0*w+x0]
    val h10=src[z0*w+x1]
    val h01=src[z1*w+x0]
    val h11=src[z1*w+x1]

    val h0=h00*(1-tx)+h10*tx
    val h1=h01*(1-tx)+h11*tx

    return h0*(1-tz)+h1*tz
}

// Catmull-Rom
fun cubic(p0:Float,p1:Float,p2:Float,p3:Float,t:Float):Float
{
    val a=-0.5f*p0+1.5f*p1-1.5f*p2+0.5f*p3
    val b=p0-2.5f*p1+2f*p2-0.5f*p3
    val c=-0.5f*p0+0.5f*p2
    val d=p1

    return ((a*t+b)*t+c)*t+d
}

fun sampleBicubic(src:FloatArray,w:Int,h:Int,u:Float,v:Float):Float
{
    val fx=(u*w-0.5f).coerceIn(0f,w-1f)
    val fz=(v*h-0.5f).coerceIn(0f,h-1f)

    val x=fx.toInt()
    val z=fz.toInt()

    val tx=fx-x
    val tz=fz-z

    fun get(ix:Int,iz:Int):Float
    {
        val cx=ix.coerceIn(0,w-1)
        val cz=iz.coerceIn(0,h-1)
        return src[cz*w+cx]
    }

    val arr=FloatArray(4)

    for(i in -1..2)
    {
        val p0=get(x-1,z+i)
        val p1=get(x,z+i)
        val p2=get(x+1,z+i)
        val p3=get(x+2,z+i)

        arr[i+1]=cubic(p0,p1,p2,p3,tx)
    }

    return cubic(arr[0],arr[1],arr[2],arr[3],tz)
}