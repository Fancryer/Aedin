package org.fancryer

import engine.Camera
import engine.Renderer
import engine.Window
import engine.input.InputManager
import engine.input.MouseAxis
import org.fancryer.AedinError.Companion.aedinError
import org.fancryer.engine.ShaderProgram
import org.joml.Matrix4f
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL11C.*
import skybox.HDRIEnvironment
import skybox.Skybox
import terrain.Terrain
import utils.Time
import kotlin.random.Random

fun main()
{
    // Инициализация GLFW
    val window=Window(1280,720,"Terrain").apply {init(true)}
    glfwSetInputMode(window.handle,GLFW_CURSOR,GLFW_CURSOR_DISABLED)

    val camera=Camera().apply {
        updateProjection(window.width,window.height)
    }
    camera.position.set(32f,10f,32f)

    val inputManager=InputManager(window)
    setupInputBindings(inputManager,camera)

    val hdri=HDRIEnvironment(faceSize=512).apply {
        val downloads="/home/alice/Загрузки"
        val goegap="$downloads/goegap_1k.hdr"
        val kloofendal="$downloads/kloofendal_48d_partly_cloudy_puresky_2k.hdr"
        val rogland="$downloads/rogland_clear_night_1k.hdr"
        loadFromHDR(kloofendal)
    }

    val skybox=Skybox().apply {init(hdri.envCubemap)}

    val renderer=Renderer().apply {init()}
    val shaderProgram=ShaderProgram().apply {
        init("vertex.vert","fragment.frag")
        setUniform1i("skybox",0)
    }

    val terrain=Terrain(64,1.0f,Random.nextInt()).apply {init()}
    val modelMatrix=Matrix4f()

    // TODO Begin > terrain < End

    var lastTime=System.nanoTime()
    var frames=0

    while(!window.shouldClose())
    {
        Time.update()
        camera.updateProjection(window.width,window.height)

        inputManager.update()

        // FPS счётчик
        frames++
        val currentTime=System.nanoTime()
        if(currentTime-lastTime>=1_000_000_000)
        {
            println("FPS: $frames, Delta: ${Time.deltaTime}, Pos: ${camera.position}")
            frames=0
            lastTime=currentTime
        }

        // Рендер
        renderer.clear()
        shaderProgram.use()

        hdri.bindForSkybox()              // биндим skybox в unit 0
        hdri.bindForLighting(1,2,3)       // биндим IBL текстуры

        shaderProgram.setUniform1i("irradianceMap",1)
        shaderProgram.setUniform1i("prefilterMap",2)
        shaderProgram.setUniform1i("brdfLUT",3)

        shaderProgram.setUniformMatrix4f("projection",camera.projectionMatrix)
        shaderProgram.setUniformMatrix4f("view",camera.getViewMatrix())
        shaderProgram.setUniformMatrix4f("model",modelMatrix.identity())
        shaderProgram.setUniform3f("viewPos",camera.position.x,camera.position.y,camera.position.z)

        shaderProgram.setUniform3f("objectColor",0.2f,0.625f,0.2f)  // зелёный ландшафт
        shaderProgram.setUniform1f("exposure",2f)   // яркие HDR

        shaderProgram.setUniform1f("roughness",0.1f)
        shaderProgram.setUniform1f("metallic",0.04f)

        terrain.render()

        skybox.render(camera.getViewMatrix(),camera.projectionMatrix)
        window.update()
    }

    // Resource cleanup
    terrain.cleanup()
    shaderProgram.cleanup()
    window.cleanup()
    hdri.cleanup()
}

fun setupInputBindings(input:InputManager,camera:Camera)
{
    // Действия (однократные нажатия)
    input.bindAction("Exit",GLFW_KEY_ESCAPE) {
        glfwSetWindowShouldClose(input.window.handle,true)
    }
    input.bindAction("Wireframe",GLFW_KEY_L) {
        glPolygonMode(GL_FRONT_AND_BACK,GL_LINE)
    }
    input.bindAction("Points",GLFW_KEY_P) {
        glPolygonMode(GL_FRONT_AND_BACK,GL_POINT)
    }
    input.bindAction("Fill",GLFW_KEY_F) {
        glPolygonMode(GL_FRONT_AND_BACK,GL_FILL)
    }
    input.bindAction("ToggleCursor",GLFW_KEY_M) {
        input.window.toggleCursorVisibility()
    }

    input.bindAction(
        "CameraSpeed+",
        GLFW_KEY_LEFT_CONTROL,
        {camera.speedMultiplier=1f}
    ) {camera.speedMultiplier=5f}

    // Оси движения (непрерывные значения)
    input.bindAxis("MoveForward",GLFW_KEY_W,GLFW_KEY_S,callback=camera::moveForward)
    input.bindAxis("MoveRight",GLFW_KEY_D,GLFW_KEY_A,callback=camera::moveRight)
    input.bindAxis("MoveUp",GLFW_KEY_SPACE,GLFW_KEY_LEFT_SHIFT,callback=camera::moveUp)

    // Мышиные оси для поворота камеры
    input.bindMouseAxis("LookX",MouseAxis.X,sensitivity=0.2f) {camera.processMouse(it,0f)}
    input.bindMouseAxis("LookY",MouseAxis.Y,sensitivity=0.2f) {camera.processMouse(0f,it)}
}

fun displayError(message:String)
{
    throw message.aedinError
}

class AedinError(message:String):RuntimeException(message)
{
    companion object
    {
        val String.aedinError get()=AedinError(this)
    }
}