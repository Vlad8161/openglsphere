package com.example.openglsphere

/**
 * Created by applexis on 25.10.18.
 */

fun conv(src: List<Float>, kernel: List<Float>): List<Float> {
    val res = Array(src.count(), { 0f })
    for (i in 0 until src.count()) {
        var sum = 0f
        var weightSum = 0f
        for (j in 0 until kernel.count()) {
            val srcIdx = i - (j / 2)
            val srcVal = if (srcIdx >= 0 && srcIdx < src.count()) src[srcIdx] else 0f
            sum += srcVal * kernel[j]
            weightSum += kernel[j]
        }
        res[i] = sum / weightSum
    }
    return res.toList()
}
