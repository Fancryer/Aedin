package org.fancryer.terrain.heightsource

interface HeightSource
{
    /** heights, minHeight */
    fun calculateHeights(width:Int,length:Int,offsetX:Int=0,offsetZ:Int=0):Triple<FloatArray,Float,Float>
    val heightScale:Float
}