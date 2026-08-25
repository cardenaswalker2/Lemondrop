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

  group('LemonAiRepository Network Tests', () {
    test('sendMessage converts connection error into friendly message', () async {
      dio.httpClientAdapter = HttpClientAdapter();
      // An invalid port will trigger connection error
      dio.options.baseUrl = 'http://127.0.0.1:59999';
      dio.options.connectTimeout = const Duration(milliseconds: 100);

      const request = AIChatRequest(
        message: 'Hola Lemon Drop',
      );

      final response = await repository.sendMessage(request);

      expect(response.success, isFalse);
      expect(response.error, isNotNull);
      expect(response.error!.isNotEmpty, isTrue);
    });
  });
}
