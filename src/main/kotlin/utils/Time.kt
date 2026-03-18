package utils

import kotlin.time.Duration.Companion.seconds

object Time
{
    var time=0.0.seconds; private set
    private var lastFrameTime=System.nanoTime()

    var deltaTime=0.0; private set

    fun update()
    {
        val currentTime=System.nanoTime()
        deltaTime=(currentTime-lastFrameTime)/1_000_000_000.0
        time+=deltaTime.seconds
        lastFrameTime=currentTime
    }
}