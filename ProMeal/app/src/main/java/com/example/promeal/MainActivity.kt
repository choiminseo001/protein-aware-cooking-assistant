package com.example.promeal

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.promeal.databinding.ActivityMainBinding
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private lateinit var foodDetector: FoodDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        foodDetector = FoodDetector(this)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        binding.btnCapture.setOnClickListener {
            takePhotoAndProcess()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (_: Exception) {
                Toast.makeText(this, "Camera start failure", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhotoAndProcess() {
        val imageCapture = imageCapture ?: return

        binding.btnCapture.isEnabled = false

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    var bitmap = imageProxyToBitmap(imageProxy)

                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    if (rotationDegrees != 0) {
                        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    }

                    binding.ivCapturedResult.setImageBitmap(bitmap)

                    imageProxy.close()

                    runRealModelInference(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    binding.btnCapture.isEnabled = true
                    Toast.makeText(this@MainActivity, "Capture failure: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun runRealModelInference(bitmap: Bitmap) {
        // 커스텀 박스 뷰의 실제 화면 픽셀 가로세로 크기 확보
        val viewWidth = binding.boundingBoxView.width
        val viewHeight = binding.boundingBoxView.height

        // TFLite 모델 추론 수행 (검출된 모든 박스 리스트 리턴됨)
        val results = foodDetector.detect(bitmap, viewWidth, viewHeight)

        // 화면에 빨간 디텍션 박스 그리기
        binding.boundingBoxView.setResults(results)

        // ⭐️ [핵심 해결] 단백질 정산 연산 처리
        if (results.isNotEmpty()) {

            var totalProteinAmount = 0
            val detectedFoodNames = mutableListOf<String>()

            // 1. 검출된 모든 박스를 하나씩 순회하며 단백질을 누적 합산합니다.
            for (detectedItem in results) {
                val foodName = detectedItem.className

                // 맵에 등록된 단백질 양을 가져와서 더해줍니다 (예: 계란 4개면 6g * 4 = 24g)
                val proteinAmount = ProteinData.getProtein(foodName)
                totalProteinAmount += proteinAmount

                // 화면 표시용 이름 리스트 저장
                detectedFoodNames.add(foodName)
            }

            // 2. 텍스트 화면에 보여줄 음식 정보 가공 (예: "egg, egg, egg, egg")
            // 만약 중복 이름을 깔끔하게 합치고 싶다면 detectedFoodNames.distinct().joinToString() 사용 가능
            val foodListText = detectedFoodNames.joinToString(", ")

            // 3. 목표치 계산 (에디트 텍스트에서 숫자를 읽어옵니다)
            val target = binding.etProteinTarget.text.toString().toIntOrNull() ?: 25
            val remaining = (target - totalProteinAmount).coerceAtLeast(0)

            // 4. UI 텍스트 컴포넌트 갱신
            binding.tvRecognizedFood.text = foodListText
            binding.tvProteinAmount.text = "$totalProteinAmount g"
            binding.tvRemainingProtein.text = "$remaining g"
            binding.tvRecommendation.text = getRecommendationMeal(remaining, detectedFoodNames)

        } else {
            binding.tvRecognizedFood.text = "No Food Detected"
            binding.tvProteinAmount.text = "0 g"
            binding.boundingBoxView.clear()
        }

        // 버튼 잠금 해제 (다음 촬영 대기)
        binding.btnCapture.isEnabled = true
    }

    /**
     * Recommends the best meal combinations tailored to the remaining protein target,
     * while intelligently excluding already detected ingredients.
     */
    private fun getRecommendationMeal(remaining: Int, detectedFoods: List<String>): String {
        if (remaining <= 0) return "🎉 Target Achieved! Great Job!"

        var availableFoods = ProteinData.proteinMap.entries.filter { !detectedFoods.contains(it.key) }
        if (availableFoods.isEmpty()) {
            availableFoods = ProteinData.proteinMap.entries.toList()
        }

        val recommendations = mutableListOf<String>()

        // 1. Single Item Recommendation (Smallest item that fulfills the remaining target)
        val singleMatch = availableFoods
            .filter { it.value >= remaining }
            .minByOrNull { it.value }

        if (singleMatch != null) {
            recommendations.add("- Add 1 serving of ${singleMatch.key.uppercase()} (+${singleMatch.value}g)")
        }

        // 2. Combo Recommendation (Combination of 2 different items)
        var bestTwoCombo: Pair<Map.Entry<String, Int>, Map.Entry<String, Int>>? = null
        var closestDiff = Int.MAX_VALUE

        for (i in availableFoods.indices) {
            for (j in i + 1 until availableFoods.size) {
                val comboSum = availableFoods[i].value + availableFoods[j].value
                val diff = comboSum - remaining

                if (diff >= 0 && diff < closestDiff) {
                    closestDiff = diff
                    bestTwoCombo = Pair(availableFoods[i], availableFoods[j])
                }
            }
        }

        if (bestTwoCombo != null) {
            val food1 = bestTwoCombo.first
            val food2 = bestTwoCombo.second
            recommendations.add("- ${food1.key.uppercase()} (+${food1.value}g) + ${food2.key.uppercase()} (+${food2.value}g)")
        }

        // 3. High-Deficit Fallback (If remaining gap is too large, recommend multiple servings of the highest source)
        if (recommendations.isEmpty()) {
            val highestFood = availableFoods.maxByOrNull { it.value }
            if (highestFood != null) {
                val count = (remaining / highestFood.value) + 1
                recommendations.add("- Needs approx. $count servings of ${highestFood.key.uppercase()}")
            }
        }

        return recommendations.joinToString("\n")
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}