#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <net.h>
#include <vector>
#include <string>
#include <cmath>
#include <algorithm>

#define LOG_TAG "HandwritingJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static ncnn::Net g_net;
static bool g_loaded = false;

static void preprocessBitmap(JNIEnv* env, jobject bitmap, ncnn::Mat& out) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("getInfo failed");
        out = ncnn::Mat(64, 64, 1);
        out.fill(0.0f);
        return;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("lockPixels failed");
        out = ncnn::Mat(64, 64, 1);
        out.fill(0.0f);
        return;
    }

    int srcW = info.width;
    int srcH = info.height;
    uint32_t* src = (uint32_t*)pixels;

    int minX = srcW, maxX = 0, minY = srcH, maxY = 0;
    bool hasForeground = false;
    std::vector<uint8_t> gray(srcW * srcH);

    for (int y = 0; y < srcH; y++) {
        for (int x = 0; x < srcW; x++) {
            uint32_t c = src[y * srcW + x];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            int g_val = (r * 30 + g * 59 + b * 11) / 100;
            gray[y * srcW + x] = (uint8_t)g_val;

            if (g_val < 220) {
                hasForeground = true;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    out = ncnn::Mat(64, 64, 1);
    out.fill(0.0f);

    if (!hasForeground) return;

    int cropW = maxX - minX + 1;
    int cropH = maxY - minY + 1;
    int longSide = std::max(cropW, cropH);

    float scale = 56.0f / (float)longSide;
    int newW = std::max(1, (int)(cropW * scale));
    int newH = std::max(1, (int)(cropH * scale));

    int offsetX = (64 - newW) / 2;
    int offsetY = (64 - newH) / 2;

    for (int y = 0; y < newH; y++) {
        float srcYF = minY + (y / scale);
        for (int x = 0; x < newW; x++) {
            float srcXF = minX + (x / scale);

            int sx = (int)(srcXF + 0.5f);
            int sy = (int)(srcYF + 0.5f);
            if (sx >= srcW) sx = srcW - 1;
            if (sy >= srcH) sy = srcH - 1;
            if (sx < 0) sx = 0;
            if (sy < 0) sy = 0;

            uint8_t g = gray[sy * srcW + sx];
            float val = 1.0f - (g / 255.0f);

            int outX = offsetX + x;
            int outY = offsetY + y;
            if (outX >= 0 && outX < 64 && outY >= 0 && outY < 64) {
                out.channel(0).row(outY)[outX] = val;
            }
        }
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_myime_HandwritingRecognizer_nativeInit(
        JNIEnv* env, jobject /*this*/, jobject assetManager) {

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) {
        LOGE("assetManager is null");
        return JNI_FALSE;
    }

    if (g_net.load_param(mgr, "handwritten/model.ncnn.param") != 0) {
        LOGE("load_param failed");
        return JNI_FALSE;
    }
    if (g_net.load_model(mgr, "handwritten/model.ncnn.bin") != 0) {
        LOGE("load_model failed");
        return JNI_FALSE;
    }

    g_loaded = true;
    LOGI("model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_myime_HandwritingRecognizer_nativeRecognize(
        JNIEnv* env, jobject /*this*/, jobject bitmap, jint topK) {

    if (!g_loaded) return nullptr;

    ncnn::Mat input;
    preprocessBitmap(env, bitmap, input);

    ncnn::Extractor ex = g_net.create_extractor();
    ex.input("in0", input);
    ncnn::Mat output;
    ex.extract("out0", output);

    int numClass = output.w;
    if (numClass <= 0) return nullptr;

    std::vector<float> probs(numClass);
    float maxVal = output[0];
    for (int i = 1; i < numClass; i++) {
        if (output[i] > maxVal) maxVal = output[i];
    }
    float sum = 0;
    for (int i = 0; i < numClass; i++) {
        probs[i] = expf(output[i] - maxVal);
        sum += probs[i];
    }
    for (int i = 0; i < numClass; i++) probs[i] /= sum;

    std::vector<int> indices(numClass);
    for (int i = 0; i < numClass; i++) indices[i] = i;
    int k = std::min((int)topK, numClass);
    std::partial_sort(indices.begin(), indices.begin() + k, indices.end(),
        [&](int a, int b) { return probs[a] > probs[b]; });

    jintArray result = env->NewIntArray(k);
    std::vector<jint> buf(k);
    for (int i = 0; i < k; i++) buf[i] = indices[i];
    env->SetIntArrayRegion(result, 0, k, buf.data());

    return result;
}