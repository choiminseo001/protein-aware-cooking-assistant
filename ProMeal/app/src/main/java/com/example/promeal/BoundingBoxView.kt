package com.example.promeal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class BoundingBoxView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // 테두리 선 Paint
    private val boxPaint = Paint().apply {
        color = "#FF3B30".toColorInt() // 산뜻한 애플 레드 칼라
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    // 텍스트 글자 Paint
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        style = Paint.Style.FILL
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    // 텍스트 뒷배경 네모 상자 Paint
    private val textBgPaint = Paint().apply {
        color = "#FF3B30".toColorInt()
        style = Paint.Style.FILL
    }

    private var boxes = listOf<BoundingBox>()

    data class BoundingBox(val rect: android.graphics.RectF, val className: String, val confidence: Float)

    fun setResults(newBoxes: List<BoundingBox>) {
        boxes = newBoxes
        invalidate()
    }

    fun clear() {
        boxes = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (box in boxes) {
            // 1. 객체 테두리 바운딩 박스 드로잉
            canvas.drawRect(box.rect, boxPaint)

            // 2. 텍스트 라벨 내용 조합
            val textStr = " ${box.className.uppercase()} ${(box.confidence * 100).toInt()}% "

            // 텍스트 길이 측정 후 배경 상자 깔기
            val textWidth = textPaint.measureText(textStr)
            val textHeight = 45f

            canvas.drawRect(
                box.rect.left,
                box.rect.top - textHeight,
                box.rect.left + textWidth,
                box.rect.top,
                textBgPaint
            )

            // 3. 텍스트 글자 쓰기
            canvas.drawText(textStr, box.rect.left, box.rect.top - 10f, textPaint)
        }
    }
}
