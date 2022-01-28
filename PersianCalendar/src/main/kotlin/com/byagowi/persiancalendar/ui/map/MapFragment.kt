package com.byagowi.persiancalendar.ui.map

import android.animation.LayoutTransition
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.BitmapCompat
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.set
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.byagowi.persiancalendar.PREF_LATITUDE
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.databinding.FragmentMapBinding
import com.byagowi.persiancalendar.global.coordinates
import com.byagowi.persiancalendar.ui.preferences.locationathan.location.showCoordinatesDialog
import com.byagowi.persiancalendar.ui.preferences.locationathan.location.showGPSLocationDialog
import com.byagowi.persiancalendar.ui.shared.ArrowView
import com.byagowi.persiancalendar.ui.shared.SolarDraw
import com.byagowi.persiancalendar.ui.utils.dp
import com.byagowi.persiancalendar.ui.utils.getCompatDrawable
import com.byagowi.persiancalendar.ui.utils.navigateSafe
import com.byagowi.persiancalendar.ui.utils.onClick
import com.byagowi.persiancalendar.ui.utils.setupUpNavigation
import com.byagowi.persiancalendar.utils.HALF_SECOND_IN_MILLIS
import com.byagowi.persiancalendar.utils.appPrefs
import com.byagowi.persiancalendar.utils.formatDateAndTime
import com.cepmuvakkit.times.posAlgo.EarthPosition
import com.cepmuvakkit.times.posAlgo.SunMoonPositionForMap
import com.google.android.material.animation.ArgbEvaluatorCompat
import io.github.persiancalendar.praytimes.Coordinates
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.*
import java.util.zip.GZIPInputStream
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

class MapFragment : Fragment() {

    private val date = GregorianCalendar()
    private val dateMinutesOffset
        get() =
            ((date.timeInMillis - GregorianCalendar().timeInMillis) / 1000 / 60).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(MINUTES_OFFSET_KEY, dateMinutesOffset)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentMapBinding.inflate(inflater)
        binding.appBar.toolbar.let {
            it.setTitle(R.string.map)
            it.setupUpNavigation()
        }

        solarDraw = SolarDraw(layoutInflater.context)
        pinBitmap = inflater.context.getCompatDrawable(R.drawable.ic_pin).toBitmap(120, 110)

        val args by navArgs<MapFragmentArgs>()
        val minutesOffset = savedInstanceState?.getInt(MINUTES_OFFSET_KEY) ?: args.minutesOffset
        date.add(Calendar.MINUTE, minutesOffset)

        update(binding, date)

        binding.startArrow.rotateTo(ArrowView.Direction.START)
        binding.startArrow.setOnClickListener {
            binding.startArrow.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            date.add(Calendar.HOUR, -1)
            update(binding, date)
        }
        binding.startArrow.setOnLongClickListener {
            date.add(Calendar.DATE, -1)
            update(binding, date)
            true
        }
        binding.endArrow.rotateTo(ArrowView.Direction.END)
        binding.endArrow.setOnClickListener {
            binding.endArrow.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            date.add(Calendar.HOUR, 1)
            update(binding, date)
        }
        binding.endArrow.setOnLongClickListener {
            date.add(Calendar.DATE, 1)
            update(binding, date)
            true
        }

        fun bringGps() {
            showGPSLocationDialog(activity ?: return, viewLifecycleOwner)
        }

        val directPathButton = binding.appBar.toolbar.menu.add("Direct Path")
        directPathButton.also {
            it.icon = binding.appBar.toolbar.context.getCompatDrawable(R.drawable.ic_distance_icon)
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }.onClick {
            if (coordinates == null) bringGps()
            else {
                isDirectPathMode = !isDirectPathMode
                directPathButton.icon.alpha = if (isDirectPathMode) 127 else 255
                if (!isDirectPathMode) toCoordinates = null
            }
            update(binding, date)
        }
        binding.appBar.toolbar.menu.add("Grid").also {
            it.icon = binding.appBar.toolbar.context.getCompatDrawable(R.drawable.ic_grid_3x3)
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }.onClick {
            displayGrid = !displayGrid
            update(binding, date)
        }
        binding.appBar.toolbar.menu.add("GPS").also {
            it.icon = binding.appBar.toolbar.context.getCompatDrawable(R.drawable.ic_my_location)
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }.onClick { bringGps() }
        binding.appBar.toolbar.menu.add("Location").also {
            it.icon = binding.appBar.toolbar.context.getCompatDrawable(R.drawable.ic_location_on)
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }.onClick {
            if (coordinates == null) bringGps()
            displayLocation = !displayLocation; update(binding, date)
        }
        binding.appBar.toolbar.menu.add("Night Mask").also {
            it.icon = binding.appBar.toolbar.context.getCompatDrawable(R.drawable.ic_nightlight)
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }.onClick {
            displayNightMask = !displayNightMask
            binding.timeBar.isVisible = displayNightMask
            update(binding, date)
        }
        binding.root.layoutTransition = LayoutTransition().also {
            it.enableTransitionType(LayoutTransition.APPEARING)
            it.setAnimateParentHierarchy(false)
        }
        inflater.context.appPrefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == PREF_LATITUDE) {
                displayLocation = true
                binding.root.postDelayed({ update(binding, date) }, HALF_SECOND_IN_MILLIS)
            }
        }

        binding.map.onClick = fun(x: Float, y: Float) {
            val latitude = 90 - y / mapScaleFactor + 1
            val longitude = x / mapScaleFactor - 180
            if (abs(latitude) > 90 || abs(longitude) > 180) return
            if (latitude.absoluteValue < 2 && longitude.absoluteValue < 2 && displayGrid) {
                findNavController().navigateSafe(
                    MapFragmentDirections.actionMapToPanoRendo(dateMinutesOffset)
                )
                return
            }

            activity?.also {
                val coordinates = Coordinates(latitude.toDouble(), longitude.toDouble(), 0.0)
                if (isDirectPathMode) {
                    toCoordinates = coordinates
                    update(binding, date)
                } else {
                    showCoordinatesDialog(it, viewLifecycleOwner, coordinates)
                }
            }
        }

        render(binding)

        return binding.root
    }

    private var displayNightMask = true
    private var displayLocation = true
    private var displayGrid = false
    private var isDirectPathMode = false
    private var toCoordinates: Coordinates? = null

    private fun update(binding: FragmentMapBinding, date: GregorianCalendar) {
        binding.map.setImageBitmap(createMap(date))
        binding.date.text = date.formatDateAndTime()
    }

    private val scaleDownFactor = 4
    private val mapScaleFactor = 16 / scaleDownFactor
    private val sinkWidth = 360 * mapScaleFactor
    private val sinkHeight = 180 * mapScaleFactor
    private val sinkBitmap = Bitmap.createBitmap(sinkWidth, sinkHeight, Bitmap.Config.ARGB_8888)
    private var referenceBuffer: ByteBuffer? = null
    private fun createReferenceBuffer(): ByteBuffer {
        val zippedMapPath = resources.openRawResource(R.raw.worldmap).use { it.readBytes() }
        val mapPathBytes = GZIPInputStream(ByteArrayInputStream(zippedMapPath)).readBytes()
        val mapPath = PathParser.createPathFromPathData(mapPathBytes.decodeToString())
        // We assume creating reference map will be first use of sink also.
        Canvas(sinkBitmap).also {
            it.drawColor(0xFF809DB5.toInt())
            it.withScale(1f / scaleDownFactor, 1f / scaleDownFactor) {
                it.drawPath(mapPath, Paint().apply { color = 0xFFFBF8E5.toInt() })
            }
        }
        val buffer = ByteBuffer.allocate(BitmapCompat.getAllocationByteCount(sinkBitmap))
        sinkBitmap.copyPixelsToBuffer(buffer)
        return buffer
    }

    private fun getSinkBitmap(): Bitmap {
        val src = referenceBuffer ?: createReferenceBuffer().also { referenceBuffer = it }
        sinkBitmap.copyPixelsFromBuffer(src.also { it.rewind() })
        return sinkBitmap
    }

    private var solarDraw: SolarDraw? = null

    private val nightMaskScale = 2
    private val nightMask = Bitmap.createBitmap(
        360 / nightMaskScale, 180 / nightMaskScale, Bitmap.Config.ARGB_8888
    )

    private fun createMap(date: GregorianCalendar): Bitmap {
        val sink = getSinkBitmap()
        nightMask.eraseColor(Color.TRANSPARENT)
        val sunPosition = SunMoonPositionForMap(date)
        var sunX = .0f
        var sunY = .0f
        var sunAlt = .0
        var moonX = .0f
        var moonY = .0f
        var moonAlt = .0
        (0 until nightMask.width).forEach { x ->
            if (!displayNightMask) return@forEach
            (0 until nightMask.height).forEach { y ->
                val latitude = ((nightMask.height / 2 - y) * nightMaskScale).toDouble()
                val longitude = ((x - nightMask.width / 2) * nightMaskScale).toDouble()
                val sunAltitude = sunPosition.sunAltitude(latitude, longitude)
                if (sunAltitude < 0) nightMask[x, y] =
                    (-sunAltitude.toInt()).coerceAtMost(17) * 7 shl 24
                if (sunAltitude > sunAlt) { // find y/x of a point with maximum sun altitude
                    sunAlt = sunAltitude; sunX = x.toFloat(); sunY = y.toFloat()
                }
                val moonAltitude = sunPosition.moonAltitude(latitude, longitude)
                if (moonAltitude > moonAlt) { // this time for moon
                    moonAlt = moonAltitude; moonX = x.toFloat(); moonY = y.toFloat()
                }
            }
        }
        val coordinates = coordinates
        Canvas(sink).also {
            it.drawBitmap(nightMask, null, Rect(0, 0, sinkWidth, sinkHeight), null)
            val scale = sink.width / nightMask.width
            if (displayGrid) {
                (0 until sinkWidth step sinkWidth / 24).forEachIndexed { i, x ->
                    if (i == 0 || i == 12) return@forEachIndexed
                    it.drawLine(x.toFloat(), 0f, x.toFloat(), sink.height.toFloat(), gridPaint)
                }
                (0 until sinkHeight step sinkHeight / 12).forEachIndexed { i, y ->
                    if (i == 0 || i == 6) return@forEachIndexed
                    it.drawLine(0f, y.toFloat(), sink.width.toFloat(), y.toFloat(), gridPaint)
                }
                it.drawLine(sinkWidth / 2f, 0f, sinkWidth / 2f, sinkHeight / 1f, gridHalfPaint)
                it.drawLine(0f, sinkHeight / 2f, sinkWidth / 1f, sinkHeight / 2f, gridHalfPaint)
                parallelsLatitudes.forEach { y ->
                    it.drawLine(0f, y, sink.width.toFloat(), y, parallelsPaint)
                }
            }
            val solarDraw = solarDraw ?: return@also
            if (displayNightMask) {
                solarDraw.simpleMoon(it, moonX * scale, moonY * scale, sinkWidth * .02f)
                solarDraw.sun(it, sunX * scale, sunY * scale, sinkWidth * .025f)
            }
            if (coordinates != null && displayLocation) {
                val userX = (coordinates.longitude.toFloat() + 180) * mapScaleFactor
                val userY = (90 - coordinates.latitude.toFloat()) * mapScaleFactor
                pinRect.set(
                    userX - pinBitmap.width / 2f / pinScaleDown,
                    userY - pinBitmap.height / pinScaleDown,
                    userX + pinBitmap.width / 2f / pinScaleDown,
                    userY
                )
                it.drawBitmap(pinBitmap, null, pinRect, null)
            }
            val toPath = toCoordinates
            if (coordinates != null && toPath != null) {
                val from = EarthPosition(coordinates.latitude, coordinates.longitude)
                val to = EarthPosition(toPath.latitude, toPath.longitude)
                val points = from.intermediatePoints(to, 24).map { point ->
                    val userX = (point.longitude.toFloat() + 180) * mapScaleFactor
                    val userY = (90 - point.latitude.toFloat()) * mapScaleFactor
                    userX to userY
                }
                points.forEachIndexed { i, (x1, y1) ->
                    if (i >= points.size - 1) return@forEachIndexed
                    val (x2, y2) = points[i + 1]
                    if (hypot(x2 - x1, y2 - y1) > 90 * mapScaleFactor) return@forEachIndexed
                    pathPaint.color = ArgbEvaluatorCompat.getInstance().evaluate(
                        i.toFloat() / points.size, Color.BLACK, Color.RED
                    )
                    it.drawLine(x1, y1, x2, y2, pathPaint)
                }
                val heading = from.toEarthHeading(to)
                val center = points[points.size / 2]
                val centerPlus1 = points[points.size / 2 + 1]
                val distance =
                    "%,d km".format(Locale.ENGLISH, (heading.metres / 1000f).roundToInt())
                val textDegree = Math.toDegrees(
                    atan2(centerPlus1.second - center.second, centerPlus1.first - center.first)
                        .toDouble()
                ).toFloat() + if (centerPlus1.first < center.first) 180 else 0
                it.withRotation(textDegree, center.first, center.second) {
                    it.drawText(distance, center.first, center.second - 2.dp, textPaint)
                }
            }
        }
        return sink
    }

    private val gridLinesWidth = sinkWidth * .001f
    private val gridPaint = Paint().also {
        it.strokeWidth = gridLinesWidth
        it.color = 0x80FFFFFF.toInt()
    }
    private val gridHalfPaint = Paint().also {
        it.strokeWidth = gridLinesWidth
        it.color = 0x80808080.toInt()
    }
    private val pathPaint = Paint().also {
        it.strokeWidth = gridLinesWidth * 2
        it.style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.FAKE_BOLD_TEXT_FLAG).also {
        it.color = Color.BLACK
        it.textSize = gridLinesWidth * 10
        it.textAlign = Paint.Align.CENTER
    }

    private val parallelsLatitudes = listOf(
        // Circles of latitude are often called parallels
        23.436806, // https://en.wikipedia.org/wiki/Tropic_of_Cancer
        -23.436806, // https://en.wikipedia.org/wiki/Tropic_of_Capricorn
        66.566667, // https://en.wikipedia.org/wiki/Arctic_Circle
        -66.566667, // https://en.wikipedia.org/wiki/Antarctic_Circle
    ).map { (90 - it.toFloat()) * mapScaleFactor }
    private val parallelsPaint = Paint().also {
        it.strokeWidth = gridLinesWidth
        it.color = 0x80800000.toInt()
        it.pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
    }

    private val pinScaleDown = 2
    private val pinRect = RectF()
    private var pinBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    companion object {
        private const val MINUTES_OFFSET_KEY = "offset"
    }

    private fun render(binding: FragmentMapBinding) {
        binding.glSurface.setEGLContextClientVersion(2)
        binding.glSurface.setRenderer(object : GLSurfaceView.Renderer {

            private lateinit var mTriangle: Triangle
            private lateinit var mSquare: Square

            override fun onSurfaceCreated(
                gl: GL10?, config: javax.microedition.khronos.egl.EGLConfig?
            ) {
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                // initialize a triangle
                mTriangle = Triangle()
                // initialize a square
                mSquare = Square()
            }

            private val vPMatrix = FloatArray(16)
            private val projectionMatrix = FloatArray(16)
            private val viewMatrix = FloatArray(16)

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                GLES20.glViewport(0, 0, width, height)

                val ratio: Float = width.toFloat() / height.toFloat()

                // this projection matrix is applied to object coordinates
                // in the onDrawFrame() method
                Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)
            }

            override fun onDrawFrame(gl: GL10?) {
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
//                mTriangle.draw()
                // Set the camera position (View matrix)
                Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)

                // Calculate the projection and view transformation
                Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

                // Draw shape
                mTriangle.draw(vPMatrix)
//                mTriangle.draw()
            }
        })
        binding.glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }
}


const val COORDS_PER_VERTEX = 3
var triangleCoords = floatArrayOf(     // in counterclockwise order:
    0.0f, 0.622008459f, 0.0f,      // top
    -0.5f, -0.311004243f, 0.0f,    // bottom left
    0.5f, -0.311004243f, 0.0f      // bottom right
)

class Triangle {
    private val vertexShaderCode =
    // This matrix member variable provides a hook to manipulate
        // the coordinates of the objects that use this vertex shader
        "uniform mat4 uMVPMatrix;" +
                "attribute vec4 vPosition;" +
                "void main() {" +
                // the matrix must be included as a modifier of gl_Position
                // Note that the uMVPMatrix factor *must be first* in order
                // for the matrix multiplication product to be correct.
                "  gl_Position = uMVPMatrix * vPosition;" +
                "}"

    // Use to access and set the view transformation
    private var vPMatrixHandle: Int = 0

    private val fragmentShaderCode =
        "precision mediump float;" +
                "uniform vec4 vColor;" +
                "void main() {" +
                "  gl_FragColor = vColor;" +
                "}"

    // Set color with red, green, blue and alpha (opacity) values
    val color = floatArrayOf(0.63671875f, 0.76953125f, 0.22265625f, 1.0f)

    private var vertexBuffer: FloatBuffer =
        // (number of coordinate values * 4 bytes per float)
        ByteBuffer.allocateDirect(triangleCoords.size * 4).run {
            // use the device hardware's native byte order
            order(ByteOrder.nativeOrder())

            // create a floating point buffer from the ByteBuffer
            asFloatBuffer().apply {
                // add the coordinates to the FloatBuffer
                put(triangleCoords)
                // set the buffer to read the first coordinate
                position(0)
            }
        }

    fun loadShader(type: Int, shaderCode: String): Int {

        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        return GLES20.glCreateShader(type).also { shader ->

            // add the source code to the shader and compile it
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    private var mProgram: Int

    init {
        val vertexShader: Int = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader: Int = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        // create empty OpenGL ES Program
        mProgram = GLES20.glCreateProgram().also {

            // add the vertex shader to program
            GLES20.glAttachShader(it, vertexShader)

            // add the fragment shader to program
            GLES20.glAttachShader(it, fragmentShader)

            // creates OpenGL ES program executables
            GLES20.glLinkProgram(it)
        }
    }

    private var positionHandle: Int = 0
    private var mColorHandle: Int = 0

    private val vertexCount: Int = triangleCoords.size / COORDS_PER_VERTEX
    private val vertexStride: Int = COORDS_PER_VERTEX * 4 // 4 bytes per vertex

    fun draw(mvpMatrix: FloatArray) { // pass in the calculated transformation matrix


//        // Draw the triangle
//        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
//
//        // Disable vertex array
//        GLES20.glDisableVertexAttribArray(positionHandle)
//    }
//    fun draw() {
        // Add program to OpenGL ES environment
        GLES20.glUseProgram(mProgram)

        // get handle to vertex shader's vPosition member
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition").also {

            // Enable a handle to the triangle vertices
            GLES20.glEnableVertexAttribArray(it)

            // Prepare the triangle coordinate data
            GLES20.glVertexAttribPointer(
                it,
                COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                vertexStride,
                vertexBuffer
            )

            // get handle to shape's transformation matrix
            vPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")

            // Pass the projection and view transformation to the shader
            GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix, 0)

//            // get handle to fragment shader's vColor member
            mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor").also { colorHandle ->

                // Set color for drawing the triangle
                GLES20.glUniform4fv(colorHandle, 1, color, 0)
            }

            // Draw the triangle
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

            // Disable vertex array
            GLES20.glDisableVertexAttribArray(it)
        }
    }
}

var squareCoords = floatArrayOf(
    -0.5f, 0.5f, 0.0f,      // top left
    -0.5f, -0.5f, 0.0f,      // bottom left
    0.5f, -0.5f, 0.0f,      // bottom right
    0.5f, 0.5f, 0.0f       // top right
)

class Square {

    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3) // order to draw vertices

    // initialize vertex byte buffer for shape coordinates
    private val vertexBuffer: FloatBuffer =
        // (# of coordinate values * 4 bytes per float)
        ByteBuffer.allocateDirect(squareCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(squareCoords)
                position(0)
            }
        }

    // initialize byte buffer for the draw list
    private val drawListBuffer: ShortBuffer =
        // (# of coordinate values * 2 bytes per short)
        ByteBuffer.allocateDirect(drawOrder.size * 2).run {
            order(ByteOrder.nativeOrder())
            asShortBuffer().apply {
                put(drawOrder)
                position(0)
            }
        }
}
