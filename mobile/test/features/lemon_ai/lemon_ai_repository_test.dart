import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lemondrop_mobile/core/network/api_client.dart';
import 'package:lemondrop_mobile/features/lemon_ai/data/models/ai_models.dart';
import 'package:lemondrop_mobile/features/lemon_ai/data/repositories/lemon_ai_repository.dart';

class MockApiClient extends ApiClient {
  final Dio mockDio;

  MockApiClient(this.mockDio);

  @override
  Dio get dio => mockDio;
}

void main() {
  late Dio dio;
  late LemonAiRepository repository;

  setUp(() {
    dio = Dio();
    repository = LemonAiRepository(MockApiClient(dio));
  });

  group('LemonAiRepository Network & Error Classification Tests', () {
    test('sendMessage handles timeout error and tags AI_TIMEOUT', () async {
      dio.options.baseUrl = 'http://127.0.0.1:59999';
      dio.options.connectTimeout = const Duration(milliseconds: 1);

      const request = AIChatRequest(message: 'hola?');
      final response = await repository.sendMessage(request);

      expect(response.success, isFalse);
      expect(response.error, isNotNull);
      expect(response.error!.contains('AI_'), isTrue);
    });

    test('AIChatResponse correctly deserializes valid 200 payload with products', () {
      final jsonPayload = {
        'conversationId': 'conv-12345',
        'clientToken': 'tok-abc',
        'message': '¡Hola! 🍋 ¿Qué se te antoja hoy?',
        'state': 'IDLE',
        'intent': 'DISCOVERING',
        'cartUpdated': false,
        'requiresConfirmation': false,
        'orderReadyForConfirmation': false,
        'orderConfirmed': false,
        'products': [
          {
            'id': 'p1',
            'name': 'Granizado de Limón',
            'description': 'Pulpa natural',
            'image': 'https://example.com/lemon.jpg',
            'category': 'Granizados',
            'badge': 'Favorito',
            'priceFrom': 5000.0,
            'prices': {'SMALL': 5000.0, 'MEDIUM': 7000.0},
            'available': true,
          }
        ],
        'suggestions': ['🍓 Algo dulce', '🥭 Recomiéndame algo'],
        'success': true,
      };

      final response = AIChatResponse.fromJson(jsonPayload);
      expect(response.success, isTrue);
      expect(response.message, contains('¡Hola!'));
      expect(response.products.length, equals(1));
      expect(response.products.first.name, equals('Granizado de Limón'));
      expect(response.products.first.badge, equals('Favorito'));
    });
  });
}
