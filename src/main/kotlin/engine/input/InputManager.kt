package engine.input

import engine.Window
import org.lwjgl.glfw.GLFW.*

class InputManager(val window:Window)
{
    // Binding storages
    private val actionBindings=mutableMapOf<String,ActionBinding>()
    private val axisBindings=mutableMapOf<String,AxisBinding>()

    // Key states
    private val keyStates=mutableMapOf<Int,Boolean>()
    private val previousKeyStates=mutableMapOf<Int,Boolean>()

    // Axis states
    private val axisPositiveStates=mutableMapOf<Int,Float>()
    private val axisNegativeStates=mutableMapOf<Int,Float>()
    private val previousAxisPositiveStates=mutableMapOf<Int,Float>()
    private val previousAxisNegativeStates=mutableMapOf<Int,Float>()

    // For mouse
    private var lastMouseX=0.0
    private var lastMouseY=0.0
    private var firstMouse=true
    private val mouseAxisBindings=mutableMapOf<String,MouseAxisBinding>()

    // Для хранения дельты мыши
    private var mouseDeltaX=0f
    private var mouseDeltaY=0f

    init
    {
        // Initializing mouse position
        val mx=DoubleArray(1)
        val my=DoubleArray(1)
        glfwGetCursorPos(window.handle,mx,my)
        lastMouseX=mx[0]
        lastMouseY=my[0]
    }

    fun bindAction(
        name:String,
        key:Int,
        onReleased:ActionCallback?=null,
        onHeld:ActionCallback?=null,
        onPressed:ActionCallback?=null
    )
    {
        actionBindings[name]=ActionBinding(name,key,InputType.Keyboard,onPressed,onReleased,onHeld)
    }

    fun bindAxis(
        name:String,
        positiveKey:Int,
        negativeKey:Int,
        sensitivity:Float=1.0f,
        callback:AxisCallback
    )
    {
        axisBindings[name]=AxisBinding(name,positiveKey,negativeKey,callback,sensitivity)
    }

    fun bindMouseAxis(
        name:String,
        axis:MouseAxis,
        sensitivity:Float=0.1f,
        callback:AxisCallback
    )
    {
        mouseAxisBindings[name]=MouseAxisBinding(name,axis,callback,sensitivity)
    }

    // State update (to be called every frame)
    fun update()
    {
        // Обновляем позицию мыши и считаем дельту
        updateMouseDelta()
        updateKeyStates()
        updateAxisStates()
        processActions()
        processAxis()
        processMouseAxis()  // новый метод
    }

    private fun updateMouseDelta()
    {
        val mx=DoubleArray(1)
        val my=DoubleArray(1)
        glfwGetCursorPos(window.handle,mx,my)

        if(firstMouse)
        {
            lastMouseX=mx[0]
            lastMouseY=my[0]
            firstMouse=false
        }

        mouseDeltaX=(mx[0]-lastMouseX).toFloat()
        mouseDeltaY=(lastMouseY-my[0]).toFloat()  // Инвертируем Y

        lastMouseX=mx[0]
        lastMouseY=my[0]
    }

    private fun processMouseAxis()
    {
        mouseAxisBindings.values.forEach {
            val value=(if(it.axis==MouseAxis.X) mouseDeltaX else mouseDeltaY)*it.sensitivity
            if(value!=0f) it.callback(value)
        }
    }

    private fun updateKeyStates()
    {
        // Saving previous states
        keyStates.keys.forEach {previousKeyStates[it]=keyStates[it]?:false}

        // Updating current states
        actionBindings.values.forEach {
            if(it.type==InputType.Keyboard) keyStates[it.key]=isKeyPressed(it.key)
        }
    }

    private fun updateAxisStates()
    {
        // Save previous states
        axisPositiveStates.keys.forEach {previousAxisPositiveStates[it]=axisPositiveStates[it]?:0f}
        axisNegativeStates.keys.forEach {previousAxisNegativeStates[it]=axisNegativeStates[it]?:0f}

        // Update current states (для клавиатуры пока 0 или 1)
        axisBindings.values.forEach {
            axisPositiveStates[it.positiveKey]=if(isKeyPressed(it.positiveKey)) 1f else 0f
            axisNegativeStates[it.negativeKey]=if(isKeyPressed(it.negativeKey)) 1f else 0f
        }
    }

    private fun processActions()=actionBindings.values.forEach {
        val current=keyStates[it.key]?:false
        val previous=previousKeyStates[it.key]?:false

        when
        {
            current&&!previous->it.onPressed?.invoke()  // Pressed
            !current&&previous->it.onReleased?.invoke() // Released
            current&&previous ->it.onHeld?.invoke()     // Held
        }
    }

    private fun processAxis()=axisBindings.values.forEach {
        val positive=axisPositiveStates[it.positiveKey]?:0f
        val negative=axisNegativeStates[it.negativeKey]?:0f
        val value=(positive-negative)*it.sensitivity
        if(value!=0f) it.callback(value)
    }

    fun isKeyPressed(key:Int):Boolean=glfwGetKey(window.handle,key)==GLFW_PRESS
}