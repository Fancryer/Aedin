package engine

import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.plus
import org.joml.times
import kotlin.math.cos
import kotlin.math.sin

class Camera(var sensitivity:Float=1f,var speedMultiplier:Float=1f)
{
    private val speed=0.2f
    val position=Vector3f(0f,0f,3f)
    val front=Vector3f()
    val right=Vector3f()
    val up=Vector3f()

    private val worldUp=Vector3f(0f,1f,0f)

    var yaw=-90f; private set
    var pitch=-20f; private set

    init
    {
        updateCameraVectors()
    }

    val projectionMatrix=Matrix4f()
    private val viewMatrix=Matrix4f()

    fun updateProjection(width:Int,height:Int)
    {
        val aspect=width.toFloat()/height.toFloat()
        projectionMatrix.setPerspective(Math.toRadians(70.0).toFloat(),aspect,0.1f,1000.0f)
    }

    fun getViewMatrix():Matrix4f=viewMatrix.setLookAt(position,position+front,up)

    fun processMouse(dx:Float,dy:Float)
    {
        yaw+=dx*sensitivity
        pitch+=dy*sensitivity

        // Ограничиваем pitch, чтобы не переворачивать камеру
        pitch=pitch.coerceIn(-89.0f,89.0f)

        updateCameraVectors()
    }

    fun moveForward(amount:Float)
    {
        position.add(Vector3f(front)*amount*speedMultiplier*speed)
    }

    fun moveRight(amount:Float)
    {
        position.add(Vector3f(right)*amount*speedMultiplier*speed)
    }

    fun moveUp(amount:Float)
    {
        position.add(Vector3f(up)*amount*speedMultiplier*speed)
    }

    private fun updateCameraVectors()
    {
        // Считаем новый фронт
        val newFront=Vector3f()
        val yawRads=Math.toRadians(yaw.toDouble())
        val pitchRads=Math.toRadians(pitch.toDouble())
        newFront.x=cos(yawRads).toFloat()*cos(pitchRads).toFloat()
        newFront.y=sin(pitchRads).toFloat()
        newFront.z=sin(yawRads).toFloat()*cos(pitchRads).toFloat()

        front.set(newFront.normalize())

        // Пересчитываем right и up
        right.set(front).cross(worldUp).normalize()
        up.set(right).cross(front).normalize()
    }
}