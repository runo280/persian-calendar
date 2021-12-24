package com.byagowi.persiancalendar.ui.compass

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import com.byagowi.persiancalendar.global.coordinates
import com.byagowi.persiancalendar.ui.shared.SolarDraw
import com.byagowi.persiancalendar.utils.calculateSunMoonPosition
import com.cepmuvakkit.times.posAlgo.SunMoonPosition
import java.util.*
import kotlin.math.min

class SolarView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var currentTime = 0L
    private var sunMoonPosition: SunMoonPosition? = null
    private var animator: ValueAnimator? = null

    fun setTime(time: GregorianCalendar, immediate: Boolean, update: (SunMoonPosition) -> Unit) {
        animator?.removeAllUpdateListeners()
        if (immediate) {
            currentTime = time.timeInMillis
            sunMoonPosition = time.calculateSunMoonPosition(coordinates).also(update)
            postInvalidate()
            return
        }
        ValueAnimator.ofFloat(currentTime.toFloat(), time.timeInMillis.toFloat()).also {
            animator = it
            it.duration = resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
            it.interpolator = AccelerateDecelerateInterpolator()
            val date = GregorianCalendar()
            it.addUpdateListener { _ ->
                currentTime = ((it.animatedValue as? Float) ?: 0f).toLong()
                date.timeInMillis = currentTime
                sunMoonPosition = date.calculateSunMoonPosition(coordinates).also(update)
                postInvalidate()
            }
        }.start()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas ?: return)
        val sunMoonPosition = sunMoonPosition ?: return
        val radius = min(width, height) / 2f
        canvas.withScale(x = scaleFactor, y = scaleFactor, pivotX = radius, pivotY = radius) {
            val cr = radius / 8f
            solarDraw.earth(canvas, radius, radius, cr / 1.5f)
            val sunDegree = sunMoonPosition.sunEcliptic.λ.toFloat()
            canvas.withRotation(pivotX = radius, pivotY = radius, degrees = -sunDegree) {
                solarDraw.sun(this, radius, radius / 6, cr)
            }
            val moonDegree = sunMoonPosition.moonEcliptic.λ.toFloat()
            canvas.withRotation(pivotX = radius, pivotY = radius, degrees = -moonDegree) {
                solarDraw.moon(this, sunMoonPosition, radius, radius / 1.7f, cr / 1.9f)
            }
        }
    }

    private var scaleFactor = 1f
    private val scaleGestureDetector = ScaleGestureDetector(
        context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector?): Boolean {
                scaleFactor = (scaleFactor + (detector?.scaleFactor ?: 1f)).coerceIn(.9f, 1.1f)
                postInvalidate()
                return true
            }
        }
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return true
    }

    private val solarDraw = SolarDraw(context)
}
