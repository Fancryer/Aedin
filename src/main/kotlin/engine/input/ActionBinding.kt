package engine.input

typealias ActionCallback=()->Unit

class ActionBinding(
    val name:String,
    val key:Int,                    // GLFW_KEY_*
    val type:InputType=InputType.Keyboard,
    val onPressed:ActionCallback?=null,
    val onReleased:ActionCallback?=null,
    val onHeld:ActionCallback?=null
)