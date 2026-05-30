# ProMeal: Protein-Aware Smart Cooking Assistant

## Overview

ProMeal is an Android application that helps users estimate the protein content of cooking ingredients and receive protein-based ingredient recommendations.

The app uses a YOLOv8 object detection model to recognize ingredients from a captured image. Detected ingredients are mapped to a protein lookup table, and the total estimated protein amount is compared with the user's target protein intake.

If the target is not met, the app recommends additional ingredients to help users reach their protein goal.

---

## Features

* Camera capture and image upload
* Real-time ingredient detection using YOLOv8
* Bounding box visualization of detected ingredients
* Protein estimation using a predefined lookup table
* Additional ingredient recommendation based on protein deficit
* Quantized TensorFlow Lite model deployment on Android

---

## Supported Ingredient Classes

The object detection model was trained on 32 ingredient classes, including:

* Chicken
* Egg
* Fish
* Shrimp
* Tofu
* Pork
* Onion
* Tomato
* Potato
* Garlic
* Ginger
* Broccoli
* Cabbage
* Carrot

and other cooking ingredients.

---

## Recommendation Logic

1. Detect ingredients from an image.
2. Sum the protein values of detected ingredients using a protein lookup table.
3. Compare the estimated protein amount with the user-defined target.
4. Recommend:

   * A single ingredient option, or
   * A two-ingredient combination

to satisfy the remaining protein requirement.

The lookup table contains more ingredients than the 32 detection classes to provide finer-grained recommendations.

---

## Model Training and Optimization

### Object Detection Model

* Architecture: YOLOv8n
* Dataset: Recipe Ingredients Dataset (32 classes)

### Validation Results

* mAP@50: 94.1%
* mAP@50-95: 69.9%
* Precision: 90.3%
* Recall: 91.5%

Detailed training and evaluation procedures can be found in:

`model_optimization/yolov8_training_and_optimization.ipynb`

### Quantization

The trained YOLOv8 model was converted to TensorFlow Lite and quantized to INT8.

| Model       | Size     |
| ----------- | -------- |
| FP32 TFLite | 11.74 MB |
| INT8 TFLite | 3.20 MB  |

Model size reduction: 72.7%

The Android application uses the INT8 model:

`best_int8.tflite`

---

## Notes

Detection performance depends on the visual appearance of ingredients in the dataset.

For example:

* Tofu may not be detected reliably in all forms.
* Boiled eggs are detected more reliably than eggs with shells.
* Shrimp detection is less robust due to limited training samples.
