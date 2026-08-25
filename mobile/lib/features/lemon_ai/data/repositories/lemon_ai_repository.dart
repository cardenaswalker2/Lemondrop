import 'dart:convert';
import 'package:dio/dio.dart';
import '../../../../core/network/api_client.dart';
import '../models/ai_models.dart';

class LemonAiRepository {
  final ApiClient _apiClient;

  LemonAiRepository(this._apiClient);

  Dio get _dio => _apiClient.dio;

  /// Envía un mensaje de texto al agente Lemon Drop AI (POST /api/ai/chat)
  Future<AIChatResponse> sendMessage(AIChatRequest request) async {
    try {
      final response = await _dio.post(
        '/api/ai/chat',
        data: request.toJson(),
      );

      if (response.statusCode == 200 && response.data != null) {
        final data = response.data is Map<String, dynamic>
            ? response.data as Map<String, dynamic>
            : jsonDecode(response.data.toString()) as Map<String, dynamic>;
        return AIChatResponse.fromJson(data);
      } else {
        return AIChatResponse(
          success: false,
          error: 'Error en el servidor (${response.statusCode})',
        );
      }
    } on DioException catch (e) {
      return _handleDioError(e);
    } catch (e) {
      return AIChatResponse(
        success: false,
        error: 'Ocurrió un problema de conexión al enviar el mensaje.',
      );
    }
  }

  /// Envía audio grabado por voz para transcripción Whisper y ejecución de IA (POST /api/ai/voice)
  Future<AIChatResponse> sendVoice({
    required String audioFilePath,
    String? conversationId,
    String? clientToken,
    String? customerName,
    String? customerPhone,
  }) async {
    try {
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

      if (response.statusCode == 200 && response.data != null) {
        final data = response.data is Map<String, dynamic>
            ? response.data as Map<String, dynamic>
            : jsonDecode(response.data.toString()) as Map<String, dynamic>;
        return AIChatResponse.fromJson(data);
      } else {
        return AIChatResponse(
          success: false,
          error: 'No se pudo procesar el audio (${response.statusCode})',
        );
      }
    } on DioException catch (e) {
      return _handleDioError(e);
    } catch (e) {
      return AIChatResponse(
        success: false,
        error: 'Error de conexión al enviar el audio de voz.',
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
      return AIChatResponse(success: false, error: 'Conversación no encontrada');
    } on DioException catch (e) {
      return _handleDioError(e);
    } catch (e) {
      return AIChatResponse(success: false, error: 'Error al consultar conversación');
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
        error: 'El servidor tardó en responder. Por favor intenta de nuevo.',
      );
    } else if (e.type == DioExceptionType.connectionError) {
      return const AIChatResponse(
        success: false,
        error: 'Sin conexión al servidor Lemon Drop. Revisa tu internet.',
      );
    }

    final message = e.response?.data is Map
        ? (e.response?.data['message'] ?? e.response?.data['error'] ?? 'Error en el servidor')
        : 'Lo siento, hubo un problema al procesar tu solicitud.';

    return AIChatResponse(
      success: false,
      error: message.toString(),
    );
  }
}
