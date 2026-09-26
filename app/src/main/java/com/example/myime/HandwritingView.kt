package com.example.myime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class HandwritingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBBBBB")
        textSize = 44f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private val currentPath = Path()
    private var lastX = 0f
    private var lastY = 0f

    private var strokeCount = 0
    private var lastStrokeTime = 0L
    private var recognizer: HandwritingRecognizer? = null
    private var onResult: ((List<String>) -> Unit)? = null

    fun setRecognizer(r: HandwritingRecognizer) {
        recognizer = r
    }

    fun setOnResultListener(listener: (List<String>) -> Unit) {
        onResult = listener
    }

    fun clear() {
        val cvs = bitmapCanvas ?: return
        cvs.drawColor(Color.WHITE)
        currentPath.reset()
        strokeCount = 0
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmapCanvas = Canvas(bitmap!!)
            bitmapCanvas?.drawColor(Color.WHITE)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, 0f, 0f, null)
        canvas.drawPath(currentPath, strokePaint)

        // 空画布时显示提示文字
        if (strokeCount == 0) {
            canvas.drawText(
                "在此处手写",
                width / 2f,
                height / 2f + 15f,
                hintPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.reset()
                currentPath.moveTo(event.x, event.y)
                lastX = event.x
                lastY = event.y
                strokeCount++
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.quadTo(lastX, lastY, event.x, event.y)
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                bitmapCanvas?.drawPath(currentPath, strokePaint)
                currentPath.reset()
                lastStrokeTime = System.currentTimeMillis()
                postDelayed(recognizeRunnable, 600)
            }
        }
        invalidate()
        return true
    }

    private val recognizeRunnable = Runnable {
        if (System.currentTimeMillis() - lastStrokeTime < 550) return@Runnable
        if (strokeCount == 0) return@Runnable
        val r = recognizer ?: return@Runnable
        val bmp = bitmap ?: return@Runnable
        val candidates = r.recognize(bmp, 5)
        if (candidates.isNotEmpty()) onResult?.invoke(candidates)
    }
}