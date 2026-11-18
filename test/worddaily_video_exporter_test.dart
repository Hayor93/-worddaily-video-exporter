import 'dart:async';
import 'dart:io';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:worddaily_video_exporter/worddaily_video_exporter.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('worddaily_video_exporter');
  const codec = StandardMethodCodec();
  final calls = <MethodCall>[];
  Future<void> event(VideoExportTask task, String type,
      [Map<String, Object?> data = const {}]) async {
    final delivered = Completer<void>();
    ServicesBinding.instance.channelBuffers.push(
      channel.name,
      codec.encodeMethodCall(MethodCall('event', {
        'id': task.id,
        'type': type,
        ...data,
      })),
      (_) => delivered.complete(),
    );
    await delivered.future;
  }

  VideoExportTask start(
          {void Function(VideoExportProgress)? onProgress,
          void Function(VideoExportException)? onError,
          void Function()? onCancelled,
          void Function(File)? onCompleted}) =>
      WordDailyVideoExporter.exportMp4(
        inputVideo: File('/private/input.mp4'),
        creatorUsername: 'creator',
        logo: File('/private/logo.png'),
        outputPath: '/private/output.mp4',
        onProgress: onProgress,
        onError: onError,
        onCancelled: onCancelled,
        onCompleted: onCompleted,
      );

  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return null;
    });
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test(
      'passes all inputs and reports unknown/available progress and final path',
      () async {
    final progress = <double?>[];
    File? completed;
    final task = start(
        onProgress: (p) => progress.add(p.fraction),
        onCompleted: (file) => completed = file);
    await Future<void>.delayed(Duration.zero);
    expect(calls.single.method, 'export');
    expect(calls.single.arguments, {
      'id': task.id,
      'inputPath': '/private/input.mp4',
      'creatorUsername': 'creator',
      'logoPath': '/private/logo.png',
      'outputPath': '/private/output.mp4',
    });
    await event(task, 'progress', {'progress': null});
    await event(task, 'progress', {'progress': 0.5});
    await event(task, 'completed', {'outputPath': '/private/output.mp4'});
    expect((await task.result).path, '/private/output.mp4');
    expect(completed?.path, '/private/output.mp4');
    expect(progress, [null, 0.5]);
  });
  test('native start error reaches error callback and result', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
            channel,
            (_) async =>
                throw PlatformException(code: 'busy', message: 'Busy'));
    VideoExportException? error;
    final task = start(onError: (e) => error = e);
    await expectLater(
        task.result,
        throwsA(
            isA<VideoExportException>().having((e) => e.code, 'code', 'busy')));
    expect(error?.code, 'busy');
  });
  test('export failure is terminal and stale completion is ignored', () async {
    var errors = 0;
    final task = start(onError: (_) => errors++);
    final done = expectLater(task.result, throwsA(isA<VideoExportException>()));
    await event(
        task, 'error', {'code': 'media3_4001', 'message': 'Encoder failed'});
    await done;
    await event(task, 'completed', {'outputPath': '/private/output.mp4'});
    expect(errors, 1);
  });
  test('cancel is idempotent, scoped by task id, and reports cancellation',
      () async {
    var cancelled = false;
    final task = start(onCancelled: () => cancelled = true);
    final done = expectLater(
        task.result,
        throwsA(isA<VideoExportException>()
            .having((e) => e.code, 'code', 'cancelled')));
    await task.cancel();
    await task.cancel();
    expect(calls.where((c) => c.method == 'cancel').length, 1);
    expect(calls.last.arguments, {'id': task.id});
    await event(task, 'cancelled');
    await done;
    expect(cancelled, isTrue);
  });
  test('duplicate export is rejected without replacing active task', () async {
    final task = start();
    expect(start, throwsStateError);
    await event(task, 'completed', {'outputPath': '/private/output.mp4'});
    await task.result;
  });
  test('invalid arguments fail before platform IO', () {
    expect(
        () => WordDailyVideoExporter.exportMp4(
              inputVideo: File('/input.mp4'),
              creatorUsername: ' ',
              logo: File('/logo.png'),
              outputPath: '/output.mp4',
            ),
        throwsArgumentError);
    expect(calls, isEmpty);
  });
  test('retry after failure receives a new task id', () async {
    final first = start();
    final failed =
        expectLater(first.result, throwsA(isA<VideoExportException>()));
    await event(
        first, 'error', {'code': 'invalid_input', 'message': 'Missing file'});
    await failed;
    final second = start();
    expect(second.id, isNot(first.id));
    await event(second, 'completed', {'outputPath': '/private/output.mp4'});
    await second.result;
  });
  test('cancel after completion does not send a cancellation', () async {
    final task = start();
    await event(task, 'completed', {'outputPath': '/private/output.mp4'});
    await task.result;
    await task.cancel();
    expect(calls.where((c) => c.method == 'cancel'), isEmpty);
  });
}
