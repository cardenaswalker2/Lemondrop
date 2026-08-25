import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:lemondrop_mobile/features/lemon_ai/data/models/ai_models.dart';
import 'package:lemondrop_mobile/features/lemon_ai/data/repositories/lemon_ai_repository.dart';
import 'package:lemondrop_mobile/features/lemon_ai/presentation/providers/lemon_ai_provider.dart';
import 'package:lemondrop_mobile/features/lemon_ai/services/audio_recorder_service.dart';
import 'package:lemondrop_mobile/features/lemon_ai/services/tts_service.dart';
import 'package:shared_preferences/shared_preferences.dart';

class MockLemonAiRepository implements LemonAiRepository {
  AIChatResponse? nextResponse;
  AIVoiceResponse? nextVoiceResponse;
  AIChatRequest? lastRequest;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);

  @override
  Future<AIChatResponse> sendMessage(AIChatRequest request) async {
    lastRequest = request;
    return nextResponse ?? const AIChatResponse(success: true, message: 'Hola');
  }

  @override
  Future<AIVoiceResponse> sendVoice({
    required String audioFilePath,
    String? conversationId,
    String? clientToken,
    String? customerName,
    String? customerPhone,
  }) async {
    return nextVoiceResponse ??
        const AIVoiceResponse(
          transcription: 'Quiero un granizado de fresa',
          chatResponse: AIChatResponse(
            success: true,
            message: '¡Listo! Te agregué el Granizado de Fresa.',
          ),
        );
  }
}

class MockAudioRecorderService implements AudioRecorderService {
  bool recordingStarted = false;
  bool recordingCancelled = false;
  bool recordingStopped = false;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);

  @override
  Future<bool> hasPermission() async => true;

  @override
  Future<bool> isRecording() async => recordingStarted;

  @override
  Future<String?> startRecording() async {
    recordingStarted = true;
    return '/tmp/test_voice.m4a';
  }

  @override
  Future<String?> stopRecording() async {
    recordingStarted = false;
    recordingStopped = true;
    return '/tmp/test_voice.m4a';
  }

  @override
  Future<void> cancelRecording() async {
    recordingStarted = false;
    recordingCancelled = true;
  }

  @override
  Future<void> cleanupFile(String? filePath) async {}

  @override
  Future<void> dispose() async {}
}

class MockTextToSpeechProvider implements TextToSpeechProvider {
  String? lastSpoken;
  bool isStopped = false;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);

  @override
  Future<void> init() async {}

  @override
  Future<void> speak(String text) async {
    lastSpoken = text;
    isStopped = false;
  }

  @override
  Future<void> stop() async {
    isStopped = true;
  }

  @override
  Future<void> pause() async {}

  @override
  Future<void> setRate(double rate) async {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late MockLemonAiRepository mockRepo;
  late MockAudioRecorderService mockRecorder;
  late MockTextToSpeechProvider mockTts;
  late LemonAiNotifier notifier;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});

    mockRepo = MockLemonAiRepository();
    mockRecorder = MockAudioRecorderService();
    mockTts = MockTextToSpeechProvider();

    notifier = LemonAiNotifier(
      repository: mockRepo,
      recorderService: mockRecorder,
      ttsProvider: mockTts,
    );
  });

  group('LemonAiNotifier State & Workflow Tests', () {
    test('Initial state contains welcome message and idle state', () async {
      await Future.delayed(const Duration(milliseconds: 20));
      expect(notifier.state.messages.length, 1);
      expect(notifier.state.messages.first.role, 'assistant');
      expect(notifier.state.messages.first.content, contains('Bienvenido a Lemon Drop'));
      expect(notifier.state.uiState, LemonAiUiState.idle);
    });

    test('Sending message adds user message and processes AI response', () async {
      mockRepo.nextResponse = const AIChatResponse(
        conversationId: 'conv-101',
        clientToken: 'token-202',
        message: '¡Listo! Agregué tu Granizado de Mango Grande con Oreo.',
        state: 'WAITING_CONFIRMATION',
        requiresConfirmation: true,
        cart: AICartDto(
          cartId: 'cart-1',
          items: [
            AICartItemDto(
              size: 'LARGE',
              quantity: 1,
              addonNames: ['Oreo'],
              unitPrice: 9000,
              addonTotal: 1000,
              subtotal: 10000,
              productName: 'Granizado de Mango',
            )
          ],
          subtotal: 10000,
          total: 10000,
          status: 'DRAFT',
          totalItems: 1,
        ),
      );

      await notifier.sendMessage('Quiero un granizado de mango grande con oreo');

      expect(notifier.state.messages.length, 3); // Welcome + User + Assistant
      expect(notifier.state.messages[1].role, 'user');
      expect(notifier.state.messages[1].content, 'Quiero un granizado de mango grande con oreo');
      expect(notifier.state.messages[2].role, 'assistant');
      expect(notifier.state.messages[2].requiresConfirmation, isTrue);
      expect(notifier.state.activeCart, isNotNull);
      expect(notifier.state.activeCart!.total, 10000);
      expect(notifier.state.uiState, LemonAiUiState.waitingConfirmation);
      expect(mockTts.lastSpoken, contains('Agregué tu Granizado de Mango'));
    });

    test('Stopping and sending voice creates user message with transcription and assistant reply', () async {
      mockRepo.nextVoiceResponse = const AIVoiceResponse(
        transcription: 'Quiero un granizado de limón mediano',
        chatResponse: AIChatResponse(
          conversationId: 'conv-voice-1',
          clientToken: 'token-voice-1',
          message: '¡Excelente elección! 🍋 Te preparo el Granizado de Limón.',
          success: true,
        ),
      );

      await notifier.startVoiceRecording();
      await notifier.stopAndSendVoiceRecording();

      expect(mockRecorder.recordingStopped, isTrue);
      expect(notifier.state.messages.length, 3); // Welcome + User voice transcript + Assistant
      expect(notifier.state.messages[1].role, 'user');
      expect(notifier.state.messages[1].content, 'Quiero un granizado de limón mediano');
      expect(notifier.state.messages[1].isVoice, isTrue);
      expect(notifier.state.messages[2].role, 'assistant');
      expect(notifier.state.messages[2].content, contains('Granizado de Limón'));
      expect(notifier.state.uiState, LemonAiUiState.idle);
    });

    test('Confirming order sends CONFIRM_ORDER action and updates state to orderConfirmed', () async {
      mockRepo.nextResponse = const AIChatResponse(
        conversationId: 'conv-101',
        clientToken: 'token-202',
        message: '🎉 ¡Pedido confirmado con éxito! Código: LD-2026-00042',
        state: 'ORDER_CONFIRMED',
        orderConfirmed: true,
        orderCode: 'LD-2026-00042',
        whatsAppUrl: 'https://wa.me/573001234567?text=Recibimos+tu+pedido',
      );

      await notifier.confirmOrder();

      expect(mockRepo.lastRequest?.action, 'CONFIRM_ORDER');
      expect(notifier.state.confirmedOrderCode, 'LD-2026-00042');
      expect(notifier.state.whatsAppUrl, contains('https://wa.me/573001234567'));
      expect(notifier.state.uiState, LemonAiUiState.orderConfirmed);
    });

    test('Starting voice recording interrupts TTS and sets listening state', () async {
      await notifier.startVoiceRecording();

      expect(mockTts.isStopped, isTrue);
      expect(notifier.state.isRecording, isTrue);
      expect(notifier.state.uiState, LemonAiUiState.listening);
    });

    test('Canceling voice recording cleans up and restores state without adding empty message', () async {
      await notifier.startVoiceRecording();
      await notifier.cancelVoiceRecording();

      expect(mockRecorder.recordingCancelled, isTrue);
      expect(notifier.state.isRecording, isFalse);
      expect(notifier.state.messages.length, 1); // Only initial welcome message remains
      expect(notifier.state.uiState, LemonAiUiState.idle);
    });

    test('Multi-turn conversation continuity retains conversationId and clientToken', () async {
      // Turn 1
      mockRepo.nextResponse = const AIChatResponse(
        conversationId: 'conv-session-777',
        clientToken: 'token-secret-888',
        message: '¡De una! 🍋 Aquí tienes las opciones de limón.',
        success: true,
      );

      await notifier.sendMessage('Quiero un Granizado de Limón');

      expect(notifier.state.conversationId, 'conv-session-777');
      expect(notifier.state.clientToken, 'token-secret-888');

      // Turn 2
      mockRepo.nextResponse = const AIChatResponse(
        conversationId: 'conv-session-777',
        clientToken: 'token-secret-888',
        message: '¡Listo! ¿Qué tamaño prefieres: pequeño, mediano o grande?',
        success: true,
      );

      await notifier.sendMessage('el de limón porfa');

      expect(mockRepo.lastRequest?.conversationId, 'conv-session-777');
      expect(mockRepo.lastRequest?.clientToken, 'token-secret-888');
      expect(mockRepo.lastRequest?.message, 'el de limón porfa');
      expect(notifier.state.conversationId, 'conv-session-777');
      expect(notifier.state.messages.length, 5); // welcome, user1, assistant1, user2, assistant2
    });
  });
}
