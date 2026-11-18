# worddaily_video_exporter

Android-only Flutter plugin using AndroidX Media3 Transformer 1.8.0 to embed a moving WordDaily watermark in exported MP4 videos. No outro or app integration is included.

## FlutterFlow Git dependency

```yaml
worddaily_video_exporter:
  git:
    url: https://github.com/Hayor93/-worddaily-video-exporter.git
```

Requires compatible Android SDK 36, Java 17, Kotlin 2.2.20 and Android Gradle plugin support. Android only; web/iOS export is not implemented.

## Important limits

The plugin requires local readable video/logo files and an unused MP4 output path in a writable existing directory. It does not fetch Cloudflare R2 URLs or save exports into the device gallery. No outro files are included. Device playback/codec behavior has not been verified from this reconstructed copy.
