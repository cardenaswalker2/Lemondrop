import 'package:flutter_tts/flutter_tts.dart';

/// Abstracción desacoplada para Text-to-Speech según principios arquitectónicos
abstract class TextToSpeechProvider {
  Future<void> init();
  Future<void> speak(String text);
  Future<void> stop();
  Future<void> pause();
  Future<void> setRate(double rate);
  Future<void> setVolume(double volume);
  Future<void> dispose();
}

/// Implementación estándar de TTS en Flutter para español
class FlutterTtsProvider implements TextToSpeechProvider {
  final FlutterTts _flutterTts = FlutterTts();
  bool _isInitialized = false;

  @override
  Future<void> init() async {
    if (_isInitialized) return;
    try {
      await _flutterTts.setLanguage('es-CO');
      await _flutterTts.setSpeechRate(0.5);
      await _flutterTts.setVolume(1.0);
      await _flutterTts.setPitch(1.0);
      _isInitialized = true;
    } catch (_) {
      try {
        await _flutterTts.setLanguage('es-ES');
        _isInitialized = true;
      } catch (_) {
        // Fallback silently if language not available
      }
    }
  }

  @override
  Future<void> speak(String text) async {
    if (!_isInitialized) await init();
    // Strip markdown formatting and emojis for cleaner speech
    final cleanText = text
        .replaceAll(RegExp(r'\*\*|\*|#|`|_'), '')
        .replaceAll(RegExp(r'\|.*?\|'), '')
        .trim();
    if (cleanText.isEmpty) return;

    try {
      await _flutterTts.stop();
      await _flutterTts.speak(cleanText);
    } catch (_) {
      // Ignored
    }
  }

  @override
  Future<void> stop() async {
    try {
      await _flutterTts.stop();
    } catch (_) {
      // Ignored
    }
  }

  @override
  Future<void> pause() async {
    try {
      await _flutterTts.pause();
    } catch (_) {
      // Ignored
    }
  }

  @override
  Future<void> setRate(double rate) async {
    try {
      await _flutterTts.setSpeechRate(rate);
    } catch (_) {
      // Ignored
    }
  }

  @override
  Future<void> setVolume(double volume) async {
    try {
      await _flutterTts.setVolume(volume);
    } catch (_) {
      // Ignored
    }
  }

  @override
  Future<void> dispose() async {
    try {
      await _flutterTts.stop();
    } catch (_) {
      // Ignored
    }
  }
}
