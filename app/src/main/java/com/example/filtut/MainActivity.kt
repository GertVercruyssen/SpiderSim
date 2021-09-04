package com.example.filtut

import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import com.google.android.filament.utils.*
import java.nio.ByteBuffer


class MainActivity : AppCompatActivity() ,android.view.View.OnTouchListener {

    companion object {
        init { Utils.init()}
    }

    private val uiHelper: UiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private lateinit var surfaceView: SurfaceView
    private lateinit var choreographer: Choreographer

    // Engine creates and destroys Filament resources
    // Each engine must be accessed from a single thread of your choosing
    // Resources cannot be shared across engines
    private lateinit var engine: Engine
    // A renderer instance is tied to a single surface (SurfaceView, TextureView, etc.)
    private lateinit var renderer: Renderer
    // A scene holds all the renderable, lights, etc. to be drawn
    private lateinit var scene: Scene
    // A view defines a viewport, a scene and a camera for rendering
    private lateinit var view: View
    // Should be pretty obvious :)
    private lateinit var camera: Camera
    // Animator
    var animator: Animator? = null
        private set
    //swapchain
    private var swapChain: SwapChain? = null
    // Filament entity representing a renderable object
    @Entity private var light = 0

    private lateinit var assetLoader: AssetLoader
    private lateinit var resourceLoader: ResourceLoader
    private val readyRenderables = IntArray(128) // add up to 128 entities at a time
    private lateinit var displayHelper: DisplayHelper

    //camera specific variables
    private val kNearPlane = 0.5
    private val kFarPlane = 10000.0
    private val kFovDegrees = 45.0
    private val kAperture = 16f
    private val kShutterSpeed = 1f / 125f
    private val kSensitivity = 100f

    //Game assets
    private lateinit var spiderMesh: FilamentAsset
    private lateinit var floorMesh: FilamentAsset

    //Game logic
    var spiderLogic = SpiderLogic(0.0f,0.0f,0.0f,0.3f)
    private var moveVector: Mat4 = translation(Float3(6.0f,0.0f,0.0f))
    private var spiderAngle: Float = 0.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        surfaceView = SurfaceView(this).apply { setContentView(this) }
        choreographer = Choreographer.getInstance()
        displayHelper = DisplayHelper(surfaceView.context)
        uiHelper.renderCallback = SurfaceCallback()
        uiHelper.attachTo(surfaceView)

        setupFilament()
        setupScene()

        loadEnvironment("venetian_crossroads_2k")
        surfaceView.setOnTouchListener(this)
    }

    private fun setupFilament() {
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera().apply { setExposure(kAperture, kShutterSpeed, kSensitivity) }
        assetLoader = AssetLoader(engine, MaterialProvider(engine), EntityManager.get())
        resourceLoader = ResourceLoader(engine)
    }

    private fun setupScene() {
        view.camera = camera
        view.scene = scene

        //Point the camera at the origin, slightly raised
        camera.lookAt(-5.0,20.0,5.0,0.0,0.0,0.0,0.0,1.0,0.0)

        // We now need a light, let's create a directional light
        light = EntityManager.get().create()

        // Create a color from a temperature (D65)
        val (r, g, b) = Colors.cct(6_500.0f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(r, g, b)
                // Intensity of the sun in lux on a clear day
                .intensity(110_000.0f)
                // The direction is normalized on our behalf
                .direction(-0.753f, -1.0f, 0.890f)
                .castShadows(true)
                .build(engine, light)

        // Add the entity to the scene to light it
        scene.addEntity(light)

        // Set the exposure on the camera, this exposure follows the sunny f/16 rule
        // Since we've defined a light that has the same intensity as the sun, it
        // guarantees a proper exposure
        camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f)

        // Add renderable entities to the scene as they become ready.
        spiderMesh = loadGlb("spider", true)
        floorMesh = loadGlb("floor", false)

        scene.addEntities(floorMesh.entities)
        scene.addEntities(spiderMesh.entities)
        resourceLoader.loadResources(spiderMesh)
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        private val startTime = System.nanoTime()
        override fun doFrame(currentTime: Long) {
            val currentTime = currentTime*2
            //schedule the next frame
            choreographer.postFrameCallback(this)
            // This check guarantees that we have a swap chain
            if (!uiHelper.isReadyToRender) {
                return
            }

            val seconds = (currentTime - startTime).toDouble() / 1_000_000_000
            var animationtimer = spiderLogic.Step(seconds)
            //moveSpider()
            TransformationSpider()
            animator!!.applyAnimation(spiderLogic.currentAnimationIndex, animationtimer.toFloat())


            //animator!!.applyAnimation(0, seconds.toFloat()*1.5f)
            animator!!.updateBoneMatrices()

            // TODO : reenable Allow the resource loader to finalize textures that have become ready.
            //resourceLoader.asyncUpdateLoad()

            // If beginFrame() returns false you should skip the frame
            // This means you are sending frames too quickly to the GPU
            if (renderer.beginFrame(swapChain!!, currentTime)) {
                renderer.render(view)
                renderer.endFrame()
            }
        }
    }

    private fun moveSpider() {
        val tm = engine.transformManager
        val angle = 0.7f
        val center = spiderMesh.boundingBox.center.let { v-> Float3(v[0], v[1], v[2]) }
        center.z = center.z + 4.0f / 0.3f
        val transform = scale(Float3(0.3f)) * translation(Float3(-center))
        moveVector = rotation(Float3(0.0f,0.3f,0.0f),angle) * translation(moveVector)
        val modelRotation = rotation(Float3(0.0f,1.0f,0.0f),spiderAngle)
        spiderAngle += angle
        tm.setTransform(tm.getInstance(spiderMesh.root), transpose(moveVector*transform*modelRotation).toFloatArray())
    }

    fun TransformationSpider() {
        val tm = engine.transformManager
        var matscale = scale(Float3(spiderLogic.scale,spiderLogic.scale,spiderLogic.scale))
        var matrotation = rotation(Float3(0.0f,1.0f,0.0f),spiderLogic.rotation*-180/Math.PI.toFloat()-90)
        var mattranslation = translation(Float3(spiderLogic.position.x,spiderLogic.position.y,spiderLogic.position.z)) //get Y component from scaling

//        var matscale = scale(Float3(1.0f,1.0f,1.0f))
//        var matrotation = rotation(Float3(0.0f,1.0f,0.0f),90.0f)
//        var mattranslation = translation(Float3(5.0f,0.0f,0.0f)) //get Y component from scaling

        tm.setTransform(tm.getInstance(spiderMesh.root), transpose(matscale.times(mattranslation).times(matrotation)).toFloatArray())
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        choreographer.removeFrameCallback(frameCallback)
        engine.destroyEntity(spiderMesh.root)
        engine.destroyEntity(floorMesh.root)
        engine.destroyRenderer(renderer)
        engine.destroyEntity(light)
        engine.destroyView(view)
        engine.destroyScene(scene)
        resourceLoader.destroy()
        swapChain?.let { engine.destroySwapChain(it) }
        engine.flushAndWait()
        swapChain = null
        uiHelper.detach()
    }

    /**
     * Loads a monolithic binary glTF and populates the Filament scene.
     */
    private fun loadGlb(name: String, anim: Boolean) : FilamentAsset {
        val asset : FilamentAsset?
        val buffer = readAsset("models/${name}.glb")

        asset = assetLoader.createAssetFromBinary(buffer)
        asset?.let { asset ->
            resourceLoader.loadResources(asset)
            if(anim) {
                animator = asset.animator
                spiderLogic.animator = asset.animator
            }
            asset.releaseSourceData()
        }
        return asset!!
    }

    private fun loadEnvironment(ibl: String) {
        // Create the indirect light source and add it to the scene.
        var buffer = readAsset("envs/$ibl/${ibl}_ibl.ktx")
        KtxLoader.createIndirectLight(engine, buffer).apply {
            intensity = 10_000f
            scene.indirectLight = this
        }

        // Create the sky box and add it to the scene.
        buffer = readAsset("envs/$ibl/${ibl}_skybox.ktx")
        KtxLoader.createSkybox(engine, buffer).apply {
            scene.skybox = this
        }
    }

    private fun readAsset(assetName: String): ByteBuffer {
        val input = assets.open(assetName)
        val bytes = ByteArray(input.available())
        input.read(bytes)
        return ByteBuffer.wrap(bytes)
    }
//    private fun loadGltf(name: String) {
//        val buffer = readAsset("models/${name}.gltf")
//        modelViewer.loadModelGltf(buffer) { uri -> readAsset("models/$uri") }
//        modelViewer.transformToUnitCube()
//    }
    /**
     * Handles a [MotionEvent] to enable one-finger orbit, two-finger pan, and pinch-to-zoom.
     */
    @SuppressWarnings("ClickableViewAccessibility")
    override fun onTouch(view: android.view.View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                spiderLogic.Touch(event.getX(0), event.getY(0))
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                spiderLogic.Move(event.getX(0), event.getY(0))
                return true
            }
            MotionEvent.ACTION_UP -> {
                return true
            }
        }
        return super.onTouchEvent(event)

    }

    inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = engine.createSwapChain(surface)
            displayHelper.attach(renderer, surfaceView.display)
        }

        override fun onDetachedFromSurface() {
            displayHelper.detach()
            swapChain?.let {
                engine.destroySwapChain(it)
                engine.flushAndWait()
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            view.viewport = Viewport(0, 0, width, height)
            val aspect = width.toDouble() / height.toDouble()
            camera.setProjection(kFovDegrees, aspect, kNearPlane, kFarPlane, Camera.Fov.VERTICAL)
        }
    }
}