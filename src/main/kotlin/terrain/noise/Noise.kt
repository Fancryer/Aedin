package terrain.noise

interface Noise
{
    val scale:Float
    fun noise(x:Float,z:Float):Float
}