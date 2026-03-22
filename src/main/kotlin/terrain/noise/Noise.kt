package terrain.noise

interface Noise
{
    val amplitude:Float
    fun noise(x:Float,z:Float):Float
}