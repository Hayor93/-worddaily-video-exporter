# worddaily_video_exporter

Android-only Flutter plugin built with **AndroidX Media3 Transformer 1.8.0**.

The exporter creates **one MP4** from a source video and an approved 2-second branded outro. A permanent WordDaily watermark moves clockwise between the source video's four corners every six seconds, showing the official logo, **WordDaily**, **@creator**, and **worddailyapp.com**. The watermark stays stationary for most of each six-second interval, fades out for 0.2 seconds, changes corners while fully invisible, and fades in for 0.2 seconds. It never slides across the screen. The watermark is not drawn over the outro. The outro's approved audio plays after the source audio. The original video is never modified.

## FlutterFlow Git dependency

Use a **verified commit SHA**, not an unpinned branch:

```yaml
worddaily_video_exporter:
  git:
    url: https://github.com/Hayor93/-worddaily-video-exporter.git
    ref: REPLACE_WITH_VERIFIED_COMMIT_SHA
```

This version changes the Dart API: `outroVideo` is **required**. Existing FlutterFlow custom actions must be updated before switching their dependency.

## Dart API

```dart
import 'dart:io';
import 'package:worddaily_video_exporter/worddaily_video_exporter.dart';

// First inspect the actual local source, including rotation metadata.
final orientation = await WordDailyVideoExporter.getVideoOrientation(sourceVideo);

// Use orientation to select portrait / landscape / square approved URL.
// Download only the selected outro to a readable local .mp4 file.
// Also download/copy the official logo to a local raster image file.

final task = WordDailyVideoExporter.exportMp4(
  inputVideo: sourceVideo,
  creatorUsername: creatorUsername,
  logo: localLogo,
  outroVideo: localOutro,
  outputPath: unusedOutputMp4Path,
  onProgress: (progress) { /* fraction may be null */ },
);
final exportedMp4 = await task.result;
// The app action saves exportedMp4 to Android gallery.
```

The exporter validates that the selected outro has a video track, an audio track, and the same displayed orientation as the source. Near-square sources (within 10% of square) use the square outro. Rotation metadata is honored. Media3 `Composition` concatenates the two `EditedMediaItem` entries sequentially; it does **not** concatenate MP4 bytes. The outro is fitted to the source's displayed dimensions using `Presentation.LAYOUT_SCALE_TO_FIT` to avoid stretching or cropping. Audio is encoded to AAC, video to H.264; silence is generated for source clips without an audio track.

## Important limits

- Requires **local readable** source, logo and outro files, and a **new unused** output MP4 path in an existing writable directory.
- The plugin does **not** download FlutterFlow asset URLs or R2 media, cache assets, save to the gallery, or delete the input files. FlutterFlow's Android custom action owns these steps.
- Only one export per Flutter engine can run at a time. Cancellation and progress apply to the entire composition.
- Android only; web/iOS export is not implemented.
- Android build requires compatible SDK 36, Java 17, Kotlin 2.2.20 and Android Gradle plugin.
- GitHub CI covers Dart tests, Kotlin orientation tests and an isolated Android APK compilation. **Real-device verification** of both clips, audio handoff, rotation, final dimensions, gallery saving, failures and cancellation is still required before enabling production downloads.
