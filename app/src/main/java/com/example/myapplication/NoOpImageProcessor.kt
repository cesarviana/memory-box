package com.example.myapplication

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage

class NoOpImageProcessor : ImageProcessor {
    override fun process(imageProxy: ImageProxy, image: InputImage) {
        imageProxy.close()
    }
}
