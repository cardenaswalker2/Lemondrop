import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import '../../../../core/network/api_client.dart';
import '../models/ai_models.dart';

class LemonAiRepository {
  final ApiClient _apiClient;

  LemonAiRepository(this._apiClient);

  Dio get _dio => _apiClient.dio;

  /// Envía un mensaje de texto al agente Lemon Drop AI (POST /api/ai/chat)
  Future<AIChatResponse> sendMessage(AIChatRequest request) async {
    final sw = Stopwatch()..start();
    try {
      if (kDebugMode) {
        final convPreview = request.conversationId != null && request.conversationId!.length >= 8
            ? '${request.conversationId!.substring(0, 8)}...'
            : 'new';
        debugPrint('🍋 [AI REQUEST] POST /api/ai/chat | conv: $convPreview | token: ${request.clientToken != null}');
      }

      final response = await _dio.post(
        '/api/ai/chat',
        data: request.toJson(),
      );

      sw.stop();
      if (kDebugMode) {
        debugPrint('🍋 [AI RESPONSE] status: ${response.statusCode} | time: ${sw.elapsedMilliseconds}ms');
      }

      if (response.statusCode == 200 && response.data != null) {
        final data = response.data is Map<String, dynamic>
            ? response.data as Map<String, dynamic>
            : jsonDecode(response.data.toString()) as Map<String, dynamic>;
        return AIChatResponse.fromJson(data);
      } else {
        return AIChatResponse(
          success: false,
          error: 'AI_HTTP_ERROR: Error en el servidor (${response.statusCode})',
        );
      }
    } on DioException catch (e) {
      sw.stop();
      if (kDebugMode) {
        debugPrint('🍋 [AI DIO ERROR] type: ${e.type} | message: ${e.message} | time: ${sw.elapsedMilliseconds}ms');
      }
      return _handleDioError(e);
    } on FormatException catch (e, stack) {
      sw.stop();
      if (kDebugMode) {
        debugPrint('🍋 [AI PARSE ERROR] FormatException: $e\n$stack');
      }
      return AIChatResponse(
        success: false,
        error: 'AI_PARSE_ERROR: Formato de respuesta no válido.',
      );
    } on TypeError catch (e, stack) {
      sw.stop();
      if (kDebugMode) {
        debugPrint('🍋 [AI TYPE ERROR] TypeError: $e\n$stack');
      }
      return AIChatResponse(
        success: false,
        error: 'AI_PARSE_ERROR: Error al procesar datos del servidor.',
      );
    } catch (e, stack) {
      sw.stop();
      if (kDebugMode) {
        debugPrint('🍋 [AI UNKNOWN ERROR] $e\n$stack');
      }
      return AIChatResponse(
        success: false,
        error: 'AI_UNKNOWN_ERROR: Ocurrió un error inesperado.',
      );
    }
  }

  /// Envía audio grabado por voz para transcripción Whisper y ejecución de IA (POST /api/ai/voice)
  Future<AIVoiceResponse> sendVoice({
    required String audioFilePath,
    String? conversationId,
    String? clientToken,
    String? customerName,
    String? customerPhone,
  }) async {
    final sw = Stopwatch()..start();
    try {
      if (kDebugMode) {
        debugPrint('🍋 [AI VOICE REQUEST] POST /api/ai/voice | file: $audioFilePath');
      }

      final formDataMap = <String, dynamic>{
        'audio': await MultipartFile.fromFile(
          audioFilePath,
          filename: 'voice_input.m4a',
        ),
      };

      if (conversationId != null && conversationId.isNotEmpty) {
        formDataMap['conversationId'] = conversationId;
      }
      if (clientToken != null && clientToken.isNotEmpty) {
        formDataMap['clientToken'] = clientToken;
      }
      if (customerName != null && customerName.isNotEmpty) {
        formDataMap['customerName'] = customerName;
      }
      if (customerPhone != null && customerPhone.isNotEmpty) {
        formDataMap['customerPhone'] = customerPhone;
      }

      final formData = FormData.fromMap(formDataMap);

      final response = await _dio.post(
        '/api/ai/voice',
        data: formData,
        options: Options(
          contentType: 'multipart/form-data',
        ),
      );

      sw.stop();
      if (kDebugMode) {
        debugPrint('🍋 [AI VOICE RESPONSE] status: ${response.statusCode} | time: ${sw.elapsedMilliseconds}ms');
      }

      if (response.statusCode == 200 && response.data != null) {
        final data = response.data is Map<String, dynamic>
            ? response.data as Map<String, dynamic>
            : jsonDecode(response.data.toString()) as Map<String, dynamic>;
        return AIVoiceResponse.fromJson(data);
      } else {
        return AIVoiceResponse(
          success: false,
          error: 'AI_HTTP_ERROR: No se pudo procesar el audio (${response.statusCode})',
        );
      }
    } on DioException catch (e) {
      sw.stop();
      final chatError = _handleDioError(e);
      return AIVoiceResponse(
        success: false,
        error: chatError.error ?? 'AI_NETWORK_ERROR: Error de red al procesar el audio.',
      );
    } on FormatException {
      sw.stop();
      return const AIVoiceResponse(
        success: false,
        error: 'AI_PARSE_ERROR: Formato de audio inválido.',
      );
    } catch (e) {
      sw.stop();
      return const AIVoiceResponse(
        success: false,
        error: 'AI_UNKNOWN_ERROR: Error al procesar audio.',
      );
    }
  }

  /// Consulta el estado actual de la conversación (GET /api/ai/conversations/{id})
  Future<AIChatResponse> getConversation(String conversationId) async {
    try {
      final response = await _dio.get('/api/ai/conversations/$conversationId');
      if (response.statusCode == 200 && response.data != null) {
        final data = response.data is Map<String, dynamic>
            ? response.data as Map<String, dynamic>
            : jsonDecode(response.data.toString()) as Map<String, dynamic>;
        return AIChatResponse.fromJson(data);
      }
      return const AIChatResponse(success: false, error: 'AI_NOT_FOUND: Conversación no encontrada');
    } on DioException catch (e) {
      return _handleDioError(e);
    } catch (e) {
      return const AIChatResponse(success: false, error: 'AI_UNKNOWN_ERROR: Error al consultar conversación');
    }
  }

  /// Preparado para Server-Sent Events o Streaming si está disponible (POST /api/ai/chat/stream)
  Stream<String> streamMessage(AIChatRequest request) async* {
    try {
      final response = await _dio.post<ResponseBody>(
        '/api/ai/chat/stream',
        data: request.toJson(),
        options: Options(responseType: ResponseType.stream),
      );

      final stream = response.data?.stream;
      if (stream != null) {
        await for (final chunk in stream) {
          final text = utf8.decode(chunk);
          yield text;
        }
      }
    } catch (e) {
      // Fallback
    }
  }

  AIChatResponse _handleDioError(DioException e) {
    if (e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout ||
        e.type == DioExceptionType.sendTimeout) {
      return const AIChatResponse(
        success: false,
        error: 'AI_TIMEOUT: El servidor tardó en responder. Por favor intenta de nuevo.',
      );
    } else if (e.type == DioExceptionType.connectionError) {
      return const AIChatResponse(
        success: false,
        error: 'AI_NETWORK_ERROR: Sin conexión al servidor Lemon Drop. Revisa tu internet.',
      );
    } else if (e.response?.statusCode == 401 || e.response?.statusCode == 403) {
      return const AIChatResponse(
        success: false,
        error: 'AI_AUTH_ERROR: Sesión no autorizada.',
      );
    } else if (e.response?.statusCode == 500) {
      return const AIChatResponse(
        success: false,
        error: 'AI_BACKEND_ERROR: Error interno en el servidor Lemon Drop.',
      );
    }

    final message = e.response?.data is Map
        ? (e.response?.data['message'] ?? e.response?.data['error'] ?? 'Error en el servidor')
        : 'AI_HTTP_ERROR: No se pudo completar la solicitud.';

    return AIChatResponse(
      success: false,
      error: message.toString(),
    );
  }
}
