/*
 * Minimal libass public API subset for RyouPlayer JNI bridge.
 * Full header: https://github.com/libass/libass/blob/master/libass/ass.h
 *
 * Prebuilt libass.so for Android (arm64-v8a / armeabi-v7a / x86_64):
 *   → Download from: https://github.com/bMaximus/libass-android/releases
 *     or build via NDK toolchain.
 *   → Place in app/src/main/jniLibs/{abi}/libass.so
 */

#pragma once

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ── Opaque types ─────────────────────────────────────────────────────────── */
typedef struct ass_library  ASS_Library;
typedef struct ass_renderer ASS_Renderer;
typedef struct ass_track    ASS_Track;

/* ── Rendered image linked list ───────────────────────────────────────────── */
typedef struct ass_image {
    int w, h;               /* bitmap width / height */
    int stride;             /* bytes per row          */
    unsigned char *bitmap;  /* 8-bit alpha plane      */
    uint32_t color;         /* RGBA (8-8-8-8)         */
    int dst_x, dst_y;       /* position on screen     */
    struct ass_image *next; /* next in the list       */
    int type;               /* 0 = character, 1 = border, 2 = shadow */
} ASS_Image;

typedef enum {
    ASS_HINTING_NONE        = 0,
    ASS_HINTING_LIGHT       = 1,
    ASS_HINTING_NORMAL      = 2,
    ASS_HINTING_NATIVE      = 3,
} ASS_Hinting;

/* ── Library lifecycle ────────────────────────────────────────────────────── */
ASS_Library  *ass_library_init(void);
void          ass_library_done(ASS_Library *priv);
void          ass_set_message_cb(ASS_Library *priv,
                  void (*msg_cb)(int level, const char *fmt, va_list args, void *data),
                  void *data);
void          ass_set_extract_fonts(ASS_Library *priv, int extract);
void          ass_set_fonts_dir(ASS_Library *priv, const char *fonts_dir);

/* ── Renderer lifecycle ───────────────────────────────────────────────────── */
ASS_Renderer *ass_renderer_init(ASS_Library *priv);
void          ass_renderer_done(ASS_Renderer *priv);
void          ass_set_frame_size(ASS_Renderer *priv, int w, int h);
void          ass_set_hinting(ASS_Renderer *priv, ASS_Hinting ht);
void          ass_set_fonts(ASS_Renderer *priv,
                  const char *default_font, const char *default_family,
                  int dfont_provider, const char *config, int update);

/* ── Track (subtitle file) ───────────────────────────────────────────────── */
ASS_Track    *ass_read_memory(ASS_Library *priv,
                  char *buf, size_t bufsize, char *codepage);
ASS_Track    *ass_new_track(ASS_Library *priv);
void          ass_free_track(ASS_Track *track);

/* ── Rendering ───────────────────────────────────────────────────────────── */
ASS_Image    *ass_render_frame(ASS_Renderer *priv, ASS_Track *track,
                  long long now, int *detect_change);

#ifdef __cplusplus
}
#endif
