package terrain

interface Noise
{
    val seed:Int
    val scale:Float
    fun noise(x:Float,z:Float):Float
}