package com.example.promeal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

class FoodDetector(private val context: Context) {

    private var interpreter: Interpreter? = null

    private val labels = listOf(
        "bay_leaf", "bell_pepper", "broccoli", "cabbage", "carrot", "cauliflower", "chicken", "chickpeas",
        "coriander", "cucumber", "egg", "eggplant", "fish", "garlic", "ginger", "kumquat", "lemon",
        "long_pepper", "mutton", "okra", "onion", "pork", "potato", "pumpkin", "radish", "salt",
        "shrimp", "small_pepper", "spring_onion", "tofu", "tomato", "turmeric"
    )

    private val modelInputWidth = 640
    private val modelInputHeight = 640

    init {
        val model: MappedByteBuffer = FileUtil.loadMappedFile(context, "best_int8.tflite")
        val options = Interpreter.Options().apply { setNumThreads(4) }
        interpreter = Interpreter(model, options)
    }

    fun detect(bitmap: Bitmap, viewWidth: Int, viewHeight: Int): List<BoundingBoxView.BoundingBox> {
        val interpreter = interpreter ?: return emptyList()

        // 1. 이미지 전처리 및 정규화
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(modelInputHeight, modelInputWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(org.tensorflow.lite.support.common.ops.NormalizeOp(0.0f, 255.0f))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        // 2. 아웃풋 규격 확인 (보통 [1, 36, 8400] 또는 [1, 8400, 36] 구조)
        val outputTensor = interpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape()

        val dim1 = outputShape[1] // 첫 번째 실질 차원 (36 또는 8400)
        val dim2 = outputShape[2] // 두 번째 실질 차원 (8400 또는 36)

        val rows = if (dim1 < dim2) dim1 else dim2       // 36 (행)
        val columns = if (dim1 < dim2) dim2 else dim1    // 8400 (열)

        // 3. Float 전용 출력 버퍼 준비 및 추론
        val bufferSize = rows * columns * 4
        val outputByteBuffer = ByteBuffer.allocateDirect(bufferSize)
        outputByteBuffer.order(ByteOrder.nativeOrder())

        interpreter.run(tensorImage.buffer, outputByteBuffer)
        outputByteBuffer.rewind()

        val flatOutput = FloatArray(rows * columns)
        outputByteBuffer.asFloatBuffer().get(flatOutput)

        val rawBoxes = mutableListOf<BoundingBoxView.BoundingBox>()

        // ⭐️ 중요: 임계값을 0.65(65%)로 대폭 상향하여 확실한 물체만 필터링합니다.
        // 박스가 너무 안 나온다면 0.50 정도로 미세 조절하세요.
        val confidenceThreshold = 0.65f

        // 4. 모델 출력 포맷 배치 구조 판단 (dim1이 36이면 [36][8400], dim1이 8400이면 [8400][36])
        val isRowsFirst = (dim1 == rows)

        // 5. 8400개 앵커 순회 분석
        for (i in 0 until columns) {
            var maxClassScore = 0f
            var detectedClassId = -1

            // 클래스 개수는 하드코딩 대신 (전체 행 - 4)로 계산하여 유연성 확보
            val classCount = rows - 4

            for (c in 0 until classCount) {
                // 모델 축 구조에 맞게 인덱스 오프셋 분기 처리
                val index = if (isRowsFirst) {
                    (4 + c) * columns + i  // [36][8400] 구조일 때
                } else {
                    i * rows + (4 + c)     // [8400][36] 구조일 때
                }

                if (index < flatOutput.size) {
                    val score = flatOutput[index]
                    if (score > maxClassScore) {
                        maxClassScore = score
                        detectedClassId = c
                    }
                }
            }

            // 32개 정의된 클래스 범위를 벗어나지 않고 임계값을 넘긴 경우만 인정
            if (detectedClassId in 0 until labels.size && maxClassScore > confidenceThreshold) {

                // 좌표 인덱스 추출
                val cxIndex = if (isRowsFirst) 0 * columns + i else i * rows + 0
                val cyIndex = if (isRowsFirst) 1 * columns + i else i * rows + 1
                val wIndex  = if (isRowsFirst) 2 * columns + i else i * rows + 2
                val hIndex  = if (isRowsFirst) 3 * columns + i else i * rows + 3

                val cxRaw = flatOutput[cxIndex]
                val cyRaw = flatOutput[cyIndex]
                val wRaw  = flatOutput[wIndex]
                val hRaw  = flatOutput[hIndex]

                // 좌표 복원 가동
                val cx = if (cxRaw <= 1.0f) cxRaw * modelInputWidth else cxRaw
                val cy = if (cyRaw <= 1.0f) cyRaw * modelInputHeight else cyRaw
                val w = if (wRaw <= 1.0f) wRaw * modelInputWidth else wRaw
                val h = if (hRaw <= 1.0f) hRaw * modelInputHeight else hRaw

                val xScale = viewWidth.toFloat() / modelInputWidth
                val yScale = viewHeight.toFloat() / modelInputHeight

                val left = (cx - w / 2f) * xScale
                val top = (cy - h / 2f) * yScale
                val right = (cx + w / 2f) * xScale
                val bottom = (cy + h / 2f) * yScale

                val rect = RectF(
                    left.coerceAtLeast(0f),
                    top.coerceAtLeast(0f),
                    right.coerceAtMost(viewWidth.toFloat()),
                    bottom.coerceAtMost(viewHeight.toFloat())
                )

                rawBoxes.add(
                    BoundingBoxView.BoundingBox(
                        rect = rect,
                        className = labels[detectedClassId],
                        confidence = maxClassScore
                    )
                )
            }
        }

        // 6. NMS 중복 제거 기준을 더 까다롭게 적용 (0.3f로 낮춰서 근접 박스 대거 탈락시킴)
        val finalBoxes = nms(rawBoxes, iouThreshold = 0.30f)
        Log.d("TFLite_Debug", "최종 결정된 박스 수: ${finalBoxes.size}")
        return finalBoxes
    }

    private fun nms(boxes: List<BoundingBoxView.BoundingBox>, iouThreshold: Float): List<BoundingBoxView.BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.confidence }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBoxView.BoundingBox>()

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.removeAt(0)
            selectedBoxes.add(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (calculateIoU(first.rect, next.rect) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return selectedBoxes
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = maxOf(box1.left, box2.left)
        val intersectionTop = maxOf(box1.top, box2.top)
        val intersectionRight = minOf(box1.right, box2.right)
        val intersectionBottom = minOf(box1.bottom, box2.bottom)

        val intersectionArea = maxOf(0f, intersectionRight - intersectionLeft) * maxOf(0f, intersectionBottom - intersectionTop)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)

        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }
}
