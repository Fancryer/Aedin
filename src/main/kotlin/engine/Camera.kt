package engine

import org.joml.Matrix4f
import org.joml.Vector3f

class Camera {
    val position = Vector3f(0f, 0f, 3f)
    val front = Vector3f(0f, 0f, -1f)
    val up = Vector3f(0f, 1f, 0f)
    
    private val projectionMatrix = Matrix4f()
    private val viewMatrix = Matrix4f()
    
    fun updateProjection(width: Int, height: Int) {
        val aspect = width.toFloat() / height.toFloat()
        projectionMatrix.setPerspective(Math.toRadians(45.0).toFloat(), aspect, 0.1f, 100.0f)
    }
    
    fun getViewMatrix(): Matrix4f {
        return viewMatrix.setLookAt(position, position + front, up)
    }
    
    fun getProjectionMatrix(): Matrix4f = projectionMatrix
}