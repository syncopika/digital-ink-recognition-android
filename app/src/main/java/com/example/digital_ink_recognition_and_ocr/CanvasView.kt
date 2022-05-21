package com.example.digital_ink_recognition_and_ocr

// https://developers.google.com/ml-kit/vision/digital-ink-recognition/android
// https://developer.android.com/codelabs/advanced-android-kotlin-training-canvas#2
// https://stackoverflow.com/questions/10410616/how-to-add-custom-view-to-the-layout
// https://stackoverflow.com/questions/20670828/how-to-create-constructor-of-custom-view-with-kotlin
// https://www.raywenderlich.com/142-android-custom-view-tutorial

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.res.ResourcesCompat
import com.google.mlkit.vision.digitalink.Ink
import java.lang.System

private const val STROKE_WIDTH = 12f

class CanvasView: View {
    private lateinit var extraCanvas: Canvas
    private lateinit var extraBitmap: Bitmap
    private val backgroundColor = ResourcesCompat.getColor(resources, R.color.white, null)
    private val drawColor = ResourcesCompat.getColor(resources, R.color.black, null)

    private val paint = Paint().apply {
        color = drawColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = STROKE_WIDTH
    }

    private var path = Path()

    private var motionTouchEventX = 0f
    private var motionTouchEventY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var currTime = 0L
    private val touchTolerance = ViewConfiguration.get(context).scaledTouchSlop

    private var inkBuilder = Ink.Builder()
    lateinit var strokeBuilder: Ink.Stroke.Builder

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if(::extraBitmap.isInitialized) extraBitmap.recycle()

        extraBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        extraCanvas = Canvas(extraBitmap)
        extraCanvas.drawColor(backgroundColor)
    }

    override fun onDraw(canvas: Canvas){
        super.onDraw(canvas)
        canvas.drawBitmap(extraBitmap, 0f, 0f, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        motionTouchEventX = event.x
        motionTouchEventY = event.y
        currTime = System.currentTimeMillis()

        when(event.action){
            MotionEvent.ACTION_DOWN -> touchStart()
            MotionEvent.ACTION_MOVE -> touchMove()
            MotionEvent.ACTION_UP -> touchUp()
        }

        return true
    }

    private fun touchStart(){
        path.reset()
        path.moveTo(motionTouchEventX, motionTouchEventY)
        currentX = motionTouchEventX
        currentY = motionTouchEventY

        strokeBuilder = Ink.Stroke.builder()
        strokeBuilder.addPoint(Ink.Point.create(motionTouchEventX, motionTouchEventY, currTime))
    }

    private fun touchMove(){
        val dx = Math.abs(motionTouchEventX - currentX)
        val dy = Math.abs(motionTouchEventY - currentY)
        if(dx >= touchTolerance || dy >= touchTolerance){
            path.quadTo(currentX, currentY, (motionTouchEventX + currentX) / 2, (motionTouchEventY + currentY) / 2)
            currentX = motionTouchEventX
            currentY = motionTouchEventY
            extraCanvas.drawPath(path, paint)

            strokeBuilder.addPoint(Ink.Point.create(motionTouchEventX, motionTouchEventY, currTime))
        }
        invalidate() // force screen redraw
    }

    private fun touchUp(){
        path.reset()

        strokeBuilder.addPoint(Ink.Point.create(motionTouchEventX, motionTouchEventY, currTime))
        inkBuilder.addStroke(strokeBuilder.build())
    }

    public fun clear(){
        // https://stackoverflow.com/questions/30485073/clear-bitmap-in-android
        extraBitmap.eraseColor(Color.TRANSPARENT)
        inkBuilder = Ink.Builder()
        invalidate()
    }

    public fun getInkData(): Ink {
        return inkBuilder.build()
    }
}