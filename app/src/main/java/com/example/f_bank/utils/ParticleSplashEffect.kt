package com.example.f_bank.utils

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.core.animation.addListener
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object ParticleSplashEffect {
    private const val PARTICLE_COUNT = 18
    private const val MAX_RADIUS = 260f
    private const val MIN_RADIUS = 120f
    private const val MAX_SIZE = 18f
    private const val MIN_SIZE = 6f
    private const val ANIMATION_DURATION = 400L

    fun play(target: View, @ColorInt color: Int) {
        val root = target.rootView as? ViewGroup ?: return
        if (target.width == 0 || target.height == 0 || root.width == 0 || root.height == 0) {
            return
        }

        val overlayView = ParticleOverlayView(
            target = target,
            root = root,
            color = color
        )
        root.overlay.add(overlayView)
        overlayView.start {
            root.overlay.remove(overlayView)
        }
    }

    private class ParticleOverlayView(
        private val target: View,
        root: ViewGroup,
        @ColorInt private val color: Int
    ) : View(root.context) {

        private data class Particle(
            val angle: Float,
            val maxRadius: Float,
            val baseSize: Float
        )

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@ParticleOverlayView.color
        }
        private val particles = List(PARTICLE_COUNT) {
            Particle(
                angle = Random.nextFloat() * 360f,
                maxRadius = Random.nextFloat() * (MAX_RADIUS - MIN_RADIUS) + MIN_RADIUS,
                baseSize = Random.nextFloat() * (MAX_SIZE - MIN_SIZE) + MIN_SIZE
            )
        }
        private val targetCenter = calculateTargetCenter(root)
        private var progress: Float = 0f
        private var onAnimationEnd: (() -> Unit)? = null

        init {
            layoutParams = ViewGroup.LayoutParams(root.width, root.height)
        }

        fun start(onEnd: () -> Unit) {
            onAnimationEnd = onEnd
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ANIMATION_DURATION
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                addListener(onEnd = {
                    onAnimationEnd?.invoke()
                })
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (progress == 0f) return
            particles.forEach { particle ->
                val angleRad = Math.toRadians(particle.angle.toDouble())
                val currentRadius = particle.maxRadius * progress
                val currentX = targetCenter.first + (cos(angleRad) * currentRadius).toFloat()
                val currentY = targetCenter.second + (sin(angleRad) * currentRadius).toFloat()
                val currentSize = particle.baseSize * (1f - progress)
                canvas.drawCircle(currentX, currentY, currentSize, paint)
            }
        }

        private fun calculateTargetCenter(root: ViewGroup): Pair<Float, Float> {
            val targetLocation = IntArray(2)
            val rootLocation = IntArray(2)
            target.getLocationOnScreen(targetLocation)
            root.getLocationOnScreen(rootLocation)

            val centerX = targetLocation[0] - rootLocation[0] + target.width / 2f
            val centerY = targetLocation[1] - rootLocation[1] + target.height / 2f
            return centerX to centerY
        }
    }
}

