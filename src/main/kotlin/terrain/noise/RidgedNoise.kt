package terrain.noise

class RidgedNoise(
    private val base:Noise,
    val octaves:Int=5,
    val lacunarity:Float=2.0f,
    val gain:Float=0.5f,
    override val amplitude:Float=1.0f
):Noise
{

    override fun noise(x:Float,z:Float):Float
    {
        var frequency=1.0f
        var amplitude=0.5f
        var weight=1.0f

        var result=0.0f

        repeat(octaves) {
            var n=base.noise(x*frequency,z*frequency)

            // преобразуем в ridged noise
            n=1.0f-kotlin.math.abs(n*2f-1f)

            // усиливаем гребни
            n*=n

            n*=weight
            weight=(n*gain).coerceIn(0f,1f)

            result+=n*amplitude

            frequency*=lacunarity
            amplitude*=gain
        }

        return result
    }
}