package utils

object Time {
    private var lastFrameTime = System.nanoTime()
    var deltaTime = 0.0f
        private set
    
    fun update() {
        val currentTime = System.nanoTime()
        deltaTime = (currentTime - lastFrameTime) / 1_000_000_000.0f
        lastFrameTime = currentTime
    }
}