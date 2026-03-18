package engine.input

typealias AxisCallback=(Float)->Unit

class AxisBinding(
    val name:String,
    val positiveKey:Int,        // например, GLFW_KEY_W
    val negativeKey:Int,        // например, GLFW_KEY_S
    val callback:AxisCallback,
    val sensitivity:Float=1.0f
)