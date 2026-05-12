/**
 * ass_jni.cpp — JNI bridge between Kotlin AssJniRenderer and libass.
 *
 * Compiled with -DLIBASS_PRESENT=1  → links real libass, full rendering.
 * Compiled with -DLIBASS_PRESENT=0  → stub: all functions are no-ops.
 *   AssJniRenderer.nCreate() returns 0 → isAvailable == false in Kotlin.
 */

#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstdlib>
#include <cstring>
#include <cstdarg>

#define TAG  "AssJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ─────────────────────────────────────────────────────────────────────────────
#if LIBASS_PRESENT

#include "include/ass/ass.h"

struct AssContext {
    ASS_Library  *library  = nullptr;
    ASS_Renderer *renderer = nullptr;
    ASS_Track    *track    = nullptr;
    int           width    = 0;
    int           height   = 0;
};

static void msg_cb(int level, const char *fmt, va_list va, void *) {
    if (level >= 6) return;
    char buf[512];
    vsnprintf(buf, sizeof(buf), fmt, va);
    __android_log_print(level < 2 ? ANDROID_LOG_ERROR : ANDROID_LOG_DEBUG,
                        TAG, "libass: %s", buf);
}

// Porter-Duff "src over" blend of a single ASS_Image onto ARGB_8888 pixels
static void blend_image(uint32_t *pixels, int bw, int bh, ASS_Image *img) {
    const int r = (img->color >> 24) & 0xFF;
    const int g = (img->color >> 16) & 0xFF;
    const int b = (img->color >>  8) & 0xFF;
    const int a = 255 - (img->color  & 0xFF); // libass alpha is inverted

    for (int y = 0; y < img->h; ++y) {
        int row = img->dst_y + y;
        if (row < 0 || row >= bh) continue;
        const uint8_t *al = img->bitmap + y * img->stride;
        uint32_t      *dp = pixels + row * bw;
        for (int x = 0; x < img->w; ++x) {
            int col = img->dst_x + x;
            if (col < 0 || col >= bw) continue;
            int alpha = a * al[x] / 255;
            if (!alpha) continue;
            uint32_t d  = dp[col];
            int dr = (d>>16)&0xFF, dg = (d>>8)&0xFF, db = d&0xFF, da = (d>>24)&0xFF;
            int oa = alpha + da * (255 - alpha) / 255;
            int q  = oa ? oa : 1;
            int or_ = (r * alpha + dr * da * (255 - alpha) / 255) / q;
            int og  = (g * alpha + dg * da * (255 - alpha) / 255) / q;
            int ob  = (b * alpha + db * da * (255 - alpha) / 255) / q;
            dp[col] = ((uint32_t)oa<<24)|((uint32_t)or_<<16)|((uint32_t)og<<8)|(uint32_t)ob;
        }
    }
}

#endif // LIBASS_PRESENT
// ─────────────────────────────────────────────────────────────────────────────

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_ryoustream_player_presentation_player_AssJniRenderer_nCreate(JNIEnv*, jobject) {
#if LIBASS_PRESENT
    auto *ctx = new AssContext();
    ctx->library = ass_library_init();
    if (!ctx->library) { delete ctx; return 0; }
    ass_set_message_cb(ctx->library, msg_cb, nullptr);
    ass_set_extract_fonts(ctx->library, 1);
    ctx->renderer = ass_renderer_init(ctx->library);
    if (!ctx->renderer) { ass_library_done(ctx->library); delete ctx; return 0; }
    ass_set_fonts(ctx->renderer, nullptr, "sans-serif", 1, nullptr, 1);
    ass_set_hinting(ctx->renderer, ASS_HINTING_LIGHT);
    LOGI("AssContext created @ %p", ctx);
    return reinterpret_cast<jlong>(ctx);
#else
    LOGI("libass not compiled in — JNI unavailable (stub)");
    return 0L; // signals Kotlin: isAvailable = false
#endif
}

JNIEXPORT void JNICALL
Java_com_ryoustream_player_presentation_player_AssJniRenderer_nDestroy(JNIEnv*, jobject, jlong handle) {
#if LIBASS_PRESENT
    auto *ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx) return;
    if (ctx->track)    ass_free_track(ctx->track);
    if (ctx->renderer) ass_renderer_done(ctx->renderer);
    if (ctx->library)  ass_library_done(ctx->library);
    delete ctx;
    LOGI("AssContext destroyed");
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_ryoustream_player_presentation_player_AssJniRenderer_nLoadData(
        JNIEnv *env, jobject, jlong handle, jbyteArray data) {
#if LIBASS_PRESENT
    auto *ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx) return JNI_FALSE;
    jsize  len = env->GetArrayLength(data);
    jbyte *buf = env->GetByteArrayElements(data, nullptr);
    if (ctx->track) { ass_free_track(ctx->track); ctx->track = nullptr; }
    ctx->track = ass_read_memory(ctx->library, reinterpret_cast<char*>(buf),
                                 static_cast<size_t>(len), nullptr);
    env->ReleaseByteArrayElements(data, buf, JNI_ABORT);
    if (!ctx->track) { LOGE("ass_read_memory failed"); return JNI_FALSE; }
    LOGI("Loaded %d events", ctx->track->n_events);
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT void JNICALL
Java_com_ryoustream_player_presentation_player_AssJniRenderer_nSetFrameSize(
        JNIEnv*, jobject, jlong handle, jint w, jint h) {
#if LIBASS_PRESENT
    auto *ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx) return;
    ctx->width = w; ctx->height = h;
    ass_set_frame_size(ctx->renderer, w, h);
#endif
}

JNIEXPORT jint JNICALL
Java_com_ryoustream_player_presentation_player_AssJniRenderer_nRenderFrame(
        JNIEnv *env, jobject, jlong handle, jlong posMs, jobject bitmap) {
#if LIBASS_PRESENT
    auto *ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx || !ctx->track) return 0;
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return 0;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return 0;
    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return 0;
    memset(pixels, 0, info.stride * info.height);
    int detect = 0;
    ASS_Image *img = ass_render_frame(ctx->renderer, ctx->track,
                                      static_cast<long long>(posMs), &detect);
    int count = 0;
    for (ASS_Image *i = img; i; i = i->next) {
        blend_image(reinterpret_cast<uint32_t*>(pixels),
                    static_cast<int>(info.width),
                    static_cast<int>(info.height), i);
        ++count;
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return count;
#else
    return 0;
#endif
}

} // extern "C"
