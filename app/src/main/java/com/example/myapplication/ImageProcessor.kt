package com.example.myapplication

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage

interface ImageProcessor {
    fun process(imageProxy: ImageProxy, image: InputImage)
}
