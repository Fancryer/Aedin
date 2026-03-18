package engine.input

data class MouseAxisBinding(
    val name: String,
    val axis: MouseAxis,  // X или Y
    val callback: AxisCallback,
    val sensitivity: Float
)

enum class MouseAxis { X, Y }