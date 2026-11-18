import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';

/// One engine-local Android exporter. Exports continue while the engine lives;
/// this is not a background service and does not modify the original video.
class WordDailyVideoExporter {
  static const _channel = MethodChannel('worddaily_video_exporter');
  static final _tasks = <String, VideoExportTask>{};
  static int _sequence = 0;
  static bool _listening = false;

  /// Inputs must be accessible local files, not asset names/content URIs/URLs.
  /// Copy a bundled raster logo to an app-private file before calling.
  /// [outputPath] must be an unused .mp4 path with an existing writable parent.
  /// Only one export per Flutter engine is supported at a time.
  static VideoExportTask exportMp4({
    required File inputVideo,
    required String creatorUsername,
    required File logo,
    required String outputPath,
    void Function(VideoExportProgress)? onProgress,
    void Function(File)? onCompleted,
    void Function(VideoExportException)? onError,
    void Function()? onCancelled,
  }) {
    if (creatorUsername.trim().isEmpty) {
      throw ArgumentError.value(creatorUsername, 'creatorUsername', 'Required');
    }
    if (outputPath.trim().isEmpty) {
      throw ArgumentError.value(outputPath, 'outputPath', 'Required');
    }
    if (_tasks.isNotEmpty) {
      throw StateError('An export is already running.');
    }
    if (!_listening) {
      _channel.setMethodCallHandler(_handleEvent);
      _listening = true;
    }
    final id = '${DateTime.now().microsecondsSinceEpoch}_${_sequence++}';
    final task = VideoExportTask._(
      id,
      onProgress,
      onCompleted,
      onError,
      onCancelled,
    );
    _tasks[id] = task;
    unawaited(_start(task, {
      'id': id,
      'inputPath': inputVideo.absolute.path,
      'creatorUsername': creatorUsername.trim(),
      'logoPath': logo.absolute.path,
      'outputPath': File(outputPath).absolute.path,
    }));
    return task;
  }

  static Future<void> _start(
      VideoExportTask task, Map<String, Object> args) async {
    try {
      await _channel.invokeMethod<void>('export', args);
    } on PlatformException catch (error) {
      _fail(task,
          VideoExportException(error.code, error.message ?? 'Export failed'));
    } catch (error) {
      _fail(task, VideoExportException('unavailable', error.toString()));
    }
  }

  static void _fail(VideoExportTask task, VideoExportException error) {
    if (_tasks.remove(task.id) == null) return;
    task._result.completeError(error);
    task._onError?.call(error);
  }

  static Future<void> _handleEvent(MethodCall call) async {
    if (call.method != 'event') return;
    final event = Map<Object?, Object?>.from(call.arguments as Map);
    final task = _tasks[event['id']];
    if (task == null) return; // Late callbacks from an old/cancelled export.
    switch (event['type']) {
      case 'progress':
        task._onProgress?.call(VideoExportProgress(
          fraction: (event['progress'] as num?)?.toDouble(),
        ));
      case 'completed':
        final output = File(event['outputPath'] as String);
        _tasks.remove(task.id);
        task._result.complete(output);
        task._onCompleted?.call(output);
      case 'cancelled':
        _tasks.remove(task.id);
        task._result.completeError(
            const VideoExportException('cancelled', 'Export cancelled'));
        task._onCancelled?.call();
      case 'error':
        _fail(
            task,
            VideoExportException(
              event['code'] as String? ?? 'export_failed',
              event['message'] as String? ?? 'Export failed',
            ));
    }
  }
}

class VideoExportTask {
  VideoExportTask._(this.id, this._onProgress, this._onCompleted, this._onError,
      this._onCancelled);
  final String id;
  final _result = Completer<File>();
  final void Function(VideoExportProgress)? _onProgress;
  final void Function(File)? _onCompleted;
  final void Function(VideoExportException)? _onError;
  final void Function()? _onCancelled;
  Future<void>? _cancelling;

  /// Resolves only after the completed MP4 is at the requested output path.
  /// Cancellation throws [VideoExportException] with code `cancelled`.
  Future<File> get result => _result.future;

  /// Idempotent. Completion can win a race with a late cancellation request.
  Future<void> cancel() {
    if (_result.isCompleted) return Future.value();
    return _cancelling ??= WordDailyVideoExporter._channel
        .invokeMethod<void>('cancel', {'id': id});
  }
}

class VideoExportProgress {
  const VideoExportProgress({required this.fraction});

  /// 0..1, or null while metadata/encoder progress is not yet available.
  final double? fraction;
}

class VideoExportException implements Exception {
  const VideoExportException(this.code, this.message);
  final String code;
  final String message;
  @override
  String toString() => 'VideoExportException($code): $message';
}
