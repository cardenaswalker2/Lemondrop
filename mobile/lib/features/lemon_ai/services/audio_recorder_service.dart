import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

/// Servicio de grabación de audio con gestión de permisos y limpieza de temporales
class AudioRecorderService {
  final AudioRecorder _audioRecorder = AudioRecorder();
  String? _currentRecordingPath;

  Future<bool> hasPermission() async {
    try {
      return await _audioRecorder.hasPermission();
    } catch (_) {
      return false;
    }
  }

  Future<bool> isRecording() async {
    try {
      return await _audioRecorder.isRecording();
    } catch (_) {
      return false;
    }
  }

  Future<String?> startRecording() async {
    final hasPerm = await hasPermission();
    if (!hasPerm) return null;

    try {
      final tempDir = await getTemporaryDirectory();
      final filePath = '${tempDir.path}/lemon_ai_voice_${DateTime.now().millisecondsSinceEpoch}.m4a';
      _currentRecordingPath = filePath;

      await _audioRecorder.start(
        const RecordConfig(
          encoder: AudioEncoder.aacLc,
          bitRate: 128000,
          sampleRate: 44100,
        ),
        path: filePath,
      );

      return filePath;
    } catch (e) {
      _currentRecordingPath = null;
      return null;
    }
  }

  Future<String?> stopRecording() async {
    try {
      final path = await _audioRecorder.stop();
      final recordedPath = path ?? _currentRecordingPath;
      _currentRecordingPath = null;
      return recordedPath;
    } catch (e) {
      _currentRecordingPath = null;
      return null;
    }
  }

  Future<void> cancelRecording() async {
    try {
      await _audioRecorder.cancel();
      if (_currentRecordingPath != null) {
        final file = File(_currentRecordingPath!);
        if (await file.exists()) {
          await file.delete();
        }
      }
    } catch (_) {
      // Ignored
    } finally {
      _currentRecordingPath = null;
    }
  }

  Future<void> cleanupFile(String? filePath) async {
    if (filePath == null) return;
    try {
      final file = File(filePath);
      if (await file.exists()) {
        await file.delete();
      }
    } catch (_) {
      // Ignored
    }
  }

  Future<void> dispose() async {
    try {
      await _audioRecorder.dispose();
    } catch (_) {
      // Ignored
    }
  }
}
