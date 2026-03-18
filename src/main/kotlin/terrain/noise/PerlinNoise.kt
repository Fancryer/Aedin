package terrain

import kotlin.math.*
import kotlin.random.Random

class PerlinNoise(override val seed:Int=42,override val scale:Float=1.0f):Noise
{
    private val perm=IntArray(512)

    init
    {
        // Простая инициализация таблицы перестановок
        val p=IntArray(256) {it}
        p.shuffle(Random(seed))

        for(i in 0 until 512) perm[i]=p[i and 255]
    }

    override fun noise(x:Float,z:Float):Float
    {
        // Упрощённый шум Перлина (для начала)
        // Вернёт значение между 0 и 1
        val ix=floor(x).toInt() and 255
        val iz=floor(z).toInt() and 255
        val fx=x-floor(x)
        val fz=z-floor(z)

        val u=fade(fx)
        val v=fade(fz)

        val a=perm[ix]+iz
        val b=perm[ix+1]+iz

        val aa=perm[a]
        val ab=perm[a+1]
        val ba=perm[b]
        val bb=perm[b+1]

        val x1=lerp(grad(aa,fx,fz),grad(ba,fx-1,fz),u)
        val x2=lerp(grad(ab,fx,fz-1),grad(bb,fx-1,fz-1),u)

        return (lerp(x1,x2,v)+1.0f)/2.0f*scale  // Нормализация в [0,1]
    }

    private fun fade(t:Float):Float=t*t*t*(t*(t*6-15)+10)
    private fun lerp(a:Float,b:Float,t:Float):Float=a+t*(b-a)

    private fun grad(hash:Int,x:Float,z:Float):Float
    {
        val h=hash and 15
        val u=if(h<8) x else z
        val v=if(h<4) z else if(h in 12..15) x else 0f
        return (if((h and 1)==0) u else -u)+(if((h and 2)==0) v else -v)
    }
}