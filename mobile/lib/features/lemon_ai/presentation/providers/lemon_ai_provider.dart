import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../../../core/storage/secure_storage.dart';
import '../../../auth/providers/auth_provider.dart';
import '../../data/models/ai_models.dart';
import '../../data/repositories/lemon_ai_repository.dart';
import '../../services/audio_recorder_service.dart';
import '../../services/tts_service.dart';

/// Provider del repositorio Lemon AI
final lemonAiRepositoryProvider = Provider<LemonAiRepository>((ref) {
  final apiClient = ref.watch(apiClientProvider);
  return LemonAiRepository(apiClient);
});

/// Provider del servicio de grabación de audio
final audioRecorderServiceProvider = Provider<AudioRecorderService>((ref) {
  final service = AudioRecorderService();
  ref.onDispose(() => service.dispose());
  return service;
});

/// Provider del servicio Text-to-Speech
final ttsServiceProvider = Provider<TextToSpeechProvider>((ref) {
  final provider = FlutterTtsProvider();
  ref.onDispose(() => provider.dispose());
  return provider;
});

/// Estado inmutable de Lemon AI
class LemonAiState {
  final ConversationSessionStatus sessionStatus;
  final LemonAiUiState uiState;
  final String? conversationId;
  final String? clientToken;
  final String? customerName;
  final String? customerPhone;
  final List<AIMessage> messages;
  final AICartDto? activeCart;
  final String? confirmedOrderCode;
  final String? whatsAppUrl;
  final List<String> suggestions;
  final bool isTtsEnabled;
  final String? errorMessage;
  final bool isRecording;

  const LemonAiState({
    this.sessionStatus = ConversationSessionStatus.noConversation,
    this.uiState = LemonAiUiState.idle,
    this.conversationId,
    this.clientToken,
    this.customerName,
    this.customerPhone,
    this.messages = const [],
    this.activeCart,
    this.confirmedOrderCode,
    this.whatsAppUrl,
    this.suggestions = const [
      '🍓 Algo dulce',
      '🥭 Recomiéndame algo',
      '🔥 Más vendidos',
      '🛒 Ver mi pedido',
    ],
    this.isTtsEnabled = true,
    this.errorMessage,
    this.isRecording = false,
  });

  LemonAiState copyWith({
    ConversationSessionStatus? sessionStatus,
    LemonAiUiState? uiState,
    String? conversationId,
    String? clientToken,
    String? customerName,
    String? customerPhone,
    List<AIMessage>? messages,
    AICartDto? activeCart,
    String? confirmedOrderCode,
    String? whatsAppUrl,
    List<String>? suggestions,
    bool? isTtsEnabled,
    String? errorMessage,
    bool? isRecording,
  }) {
    return LemonAiState(
      sessionStatus: sessionStatus ?? this.sessionStatus,
      uiState: uiState ?? this.uiState,
      conversationId: conversationId ?? this.conversationId,
      clientToken: clientToken ?? this.clientToken,
      customerName: customerName ?? this.customerName,
      customerPhone: customerPhone ?? this.customerPhone,
      messages: messages ?? this.messages,
      activeCart: activeCart ?? this.activeCart,
      confirmedOrderCode: confirmedOrderCode ?? this.confirmedOrderCode,
      whatsAppUrl: whatsAppUrl ?? this.whatsAppUrl,
      suggestions: suggestions ?? this.suggestions,
      isTtsEnabled: isTtsEnabled ?? this.isTtsEnabled,
      errorMessage: errorMessage,
      isRecording: isRecording ?? this.isRecording,
    );
  }
}

/// StateNotifier de Lemon AI
class LemonAiNotifier extends StateNotifier<LemonAiState> {
  final LemonAiRepository _repository;
  final AudioRecorderService _recorderService;
  final TextToSpeechProvider _ttsProvider;

  static const _keyConvId = 'lemon_ai_conv_id';
  static const _keyTtsEnabled = 'lemon_ai_tts_enabled';

  LemonAiNotifier({
    required LemonAiRepository repository,
    required AudioRecorderService recorderService,
    required TextToSpeechProvider ttsProvider,
  })  : _repository = repository,
        _recorderService = recorderService,
        _ttsProvider = ttsProvider,
        super(const LemonAiState()) {
    _initSession();
  }

  Future<void> _initSession() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final convId = prefs.getString(_keyConvId);
      final ttsPref = prefs.getBool(_keyTtsEnabled) ?? true;
      final clientToken = await SecureStorage.getAiClientToken();

      if (convId != null && convId.isNotEmpty) {
        state = state.copyWith(
          conversationId: convId,
          clientToken: clientToken,
          isTtsEnabled: ttsPref,
          sessionStatus: ConversationSessionStatus.active,
        );
        // Add welcome back message if empty
        if (state.messages.isEmpty) {
          _addWelcomeMessage();
        }
      } else {
        state = state.copyWith(
          isTtsEnabled: ttsPref,
          sessionStatus: ConversationSessionStatus.noConversation,
        );
        _addWelcomeMessage();
      }
    } catch (_) {
      _addWelcomeMessage();
    }
  }

  void _addWelcomeMessage() {
    final welcome = AIMessage(
      id: 'msg-welcome',
      role: 'assistant',
      content: '¡Hola! 🍋 Bienvenido a Lemon Drop. ¿Qué granizado se te antoja hoy? Puedes pedirme un sabor, armar tu vaso o pedir una recomendación. 😄',
      timestamp: DateTime.now(),
    );
    state = state.copyWith(
      messages: [welcome],
      sessionStatus: ConversationSessionStatus.active,
    );
  }

  /// Inicia una nueva conversación limpia
  Future<void> startNewConversation() async {
    await _ttsProvider.stop();
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_keyConvId);
    await SecureStorage.clearAiClientToken();

    state = const LemonAiState(
      sessionStatus: ConversationSessionStatus.active,
      uiState: LemonAiUiState.idle,
    );
    _addWelcomeMessage();
  }

  /// Envía un mensaje de texto al agente Lemon AI
  Future<void> sendMessage(String text, {String? action}) async {
    final cleanText = text.trim();
    if (cleanText.isEmpty && (action == null || action.isEmpty)) return;

    // Interrupt any ongoing TTS
    await _ttsProvider.stop();

    // 1. Add user message to UI immediately
    if (action == null || action.isEmpty) {
      final userMessage = AIMessage(
        id: 'msg-u-${DateTime.now().millisecondsSinceEpoch}',
        role: 'user',
        content: cleanText,
        timestamp: DateTime.now(),
      );
      state = state.copyWith(
        messages: [...state.messages, userMessage],
        uiState: LemonAiUiState.thinking,
        errorMessage: null,
      );
    } else {
      state = state.copyWith(
        uiState: LemonAiUiState.thinking,
        errorMessage: null,
      );
    }

    // 2. Build Request
    final request = AIChatRequest(
      conversationId: state.conversationId,
      clientToken: state.clientToken,
      message: cleanText,
      customerName: state.customerName,
      customerPhone: state.customerPhone,
      action: action,
    );

    // 3. Call backend
    final response = await _repository.sendMessage(request);
    await _processAiResponse(response);
  }

  /// Inicia la grabación de voz por micrófono
  Future<void> startVoiceRecording() async {
    await _ttsProvider.stop();

    final path = await _recorderService.startRecording();
    if (path != null) {
      state = state.copyWith(
        uiState: LemonAiUiState.listening,
        isRecording: true,
      );
    } else {
      state = state.copyWith(
        errorMessage: 'Por favor autoriza el permiso del micrófono para ordenar por voz.',
      );
    }
  }

  /// Detiene la grabación y envía el audio a /api/ai/voice
  Future<void> stopAndSendVoiceRecording() async {
    if (!state.isRecording) return;

    state = state.copyWith(
      isRecording: false,
      uiState: LemonAiUiState.transcribing,
    );

    final audioPath = await _recorderService.stopRecording();
    if (audioPath == null) {
      state = state.copyWith(uiState: LemonAiUiState.idle);
      return;
    }

    try {
      final voiceResponse = await _repository.sendVoice(
        audioFilePath: audioPath,
        conversationId: state.conversationId,
        clientToken: state.clientToken,
        customerName: state.customerName,
        customerPhone: state.customerPhone,
      );

      await _recorderService.cleanupFile(audioPath);

      if (!voiceResponse.success) {
        state = state.copyWith(
          uiState: LemonAiUiState.idle,
          errorMessage: voiceResponse.error ?? 'No se pudo procesar el mensaje de voz.',
        );
        return;
      }

      // 1. Agregar la transcripción como mensaje normal del usuario
      if (voiceResponse.transcription != null && voiceResponse.transcription!.trim().isNotEmpty) {
        final userMsg = AIMessage(
          id: 'msg-u-${DateTime.now().millisecondsSinceEpoch}',
          role: 'user',
          content: voiceResponse.transcription!.trim(),
          timestamp: DateTime.now(),
          isVoice: true,
        );
        state = state.copyWith(
          messages: [...state.messages, userMsg],
          uiState: LemonAiUiState.thinking,
        );
      }

      // 2. Procesar la respuesta del asistente
      if (voiceResponse.chatResponse != null) {
        await _processAiResponse(voiceResponse.chatResponse!);
      } else {
        state = state.copyWith(uiState: LemonAiUiState.idle);
      }
    } catch (e) {
      await _recorderService.cleanupFile(audioPath);
      state = state.copyWith(
        uiState: LemonAiUiState.idle,
        errorMessage: 'No se pudo procesar el mensaje de voz. Intenta nuevamente.',
      );
    }
  }

  /// Cancela la grabación sin enviar nada
  Future<void> cancelVoiceRecording() async {
    await _recorderService.cancelRecording();
    state = state.copyWith(
      isRecording: false,
      uiState: state.activeCart != null && state.uiState == LemonAiUiState.waitingConfirmation
          ? LemonAiUiState.waitingConfirmation
          : LemonAiUiState.idle,
    );
  }

  /// Confirmación explícita de pedido (CONFIRM_ORDER)
  Future<void> confirmOrder() async {
    await sendMessage('', action: 'CONFIRM_ORDER');
  }

  /// Alterna reproducción por voz TTS
  Future<void> toggleTts() async {
    final newTts = !state.isTtsEnabled;
    if (!newTts) {
      await _ttsProvider.stop();
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyTtsEnabled, newTts);
    state = state.copyWith(isTtsEnabled: newTts);
  }

  Future<void> _processAiResponse(AIChatResponse response, {bool isVoice = false}) async {
    if (!response.success) {
      state = state.copyWith(
        uiState: LemonAiUiState.idle,
        errorMessage: response.error ?? 'Hubo un inconveniente al comunicarse con Lemon AI.',
      );
      return;
    }

    // Persist Session Identifiers securely
    if (response.conversationId != null && response.conversationId!.isNotEmpty) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_keyConvId, response.conversationId!);
    }
    if (response.clientToken != null && response.clientToken!.isNotEmpty) {
      await SecureStorage.saveAiClientToken(response.clientToken!);
    }

    // Determine state
    LemonAiUiState nextUiState = LemonAiUiState.idle;
    if (response.orderConfirmed) {
      nextUiState = LemonAiUiState.orderConfirmed;
    } else if (response.requiresConfirmation || response.orderReadyForConfirmation) {
      nextUiState = LemonAiUiState.waitingConfirmation;
    }

    final assistantMsg = AIMessage(
      id: 'msg-a-${DateTime.now().millisecondsSinceEpoch}',
      role: 'assistant',
      content: response.message ?? '',
      timestamp: DateTime.now(),
      isVoice: isVoice,
      cartSnapshot: response.cart,
      products: response.products,
      orderCode: response.orderCode,
      whatsAppUrl: response.whatsAppUrl,
      requiresConfirmation: response.requiresConfirmation || response.orderReadyForConfirmation,
      isOrderConfirmed: response.orderConfirmed,
    );

    state = state.copyWith(
      conversationId: response.conversationId ?? state.conversationId,
      clientToken: response.clientToken ?? state.clientToken,
      messages: [...state.messages, assistantMsg],
      activeCart: response.cart ?? state.activeCart,
      confirmedOrderCode: response.orderCode ?? state.confirmedOrderCode,
      whatsAppUrl: response.whatsAppUrl ?? state.whatsAppUrl,
      suggestions: response.suggestions.isNotEmpty ? response.suggestions : state.suggestions,
      uiState: nextUiState,
      sessionStatus: ConversationSessionStatus.active,
      errorMessage: null,
    );

    // Speak via TTS if enabled
    if (state.isTtsEnabled && response.message != null && response.message!.isNotEmpty) {
      await _ttsProvider.speak(response.message!);
    }
  }
}

/// Provider global de Lemon AI (compartido entre todas las pantallas de la app)
final lemonAiProvider = StateNotifierProvider<LemonAiNotifier, LemonAiState>((ref) {
  final repo = ref.watch(lemonAiRepositoryProvider);
  final recorder = ref.watch(audioRecorderServiceProvider);
  final tts = ref.watch(ttsServiceProvider);

  return LemonAiNotifier(
    repository: repo,
    recorderService: recorder,
    ttsProvider: tts,
  );
});
