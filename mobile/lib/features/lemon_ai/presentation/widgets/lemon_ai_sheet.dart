import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_theme.dart';
import '../../data/models/ai_models.dart';
import '../providers/lemon_ai_provider.dart';
import 'lemon_ai_bubble.dart';
import 'lemon_ai_voice_overlay.dart';

class LemonAiSheet extends ConsumerStatefulWidget {
  final VoidCallback? onNavigateToTracking;

  const LemonAiSheet({
    super.key,
    this.onNavigateToTracking,
  });

  static Future<void> show(BuildContext context, {VoidCallback? onNavigateToTracking}) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.transparent,
      builder: (context) => LemonAiSheet(
        onNavigateToTracking: onNavigateToTracking,
      ),
    );
  }

  @override
  ConsumerState<LemonAiSheet> createState() => _LemonAiSheetState();
}

class _LemonAiSheetState extends ConsumerState<LemonAiSheet> {
  final TextEditingController _textController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final FocusNode _focusNode = FocusNode();

  @override
  void dispose() {
    _textController.dispose();
    _scrollController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  void _handleSend() {
    final text = _textController.text.trim();
    if (text.isEmpty) return;

    _textController.clear();
    ref.read(lemonAiProvider.notifier).sendMessage(text);
    _scrollToBottom();
  }

  void _handleProductSelected(AIProductCardDto product) {
    ref.read(lemonAiProvider.notifier).sendMessage('Quiero un ${product.name}');
    _scrollToBottom();
  }

  @override
  Widget build(BuildContext context) {
    final aiState = ref.watch(lemonAiProvider);
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;
    final isKeyboardOpen = bottomInset > 0;
    final screenHeight = MediaQuery.of(context).size.height;
    final sheetHeight = isKeyboardOpen ? screenHeight * 0.94 : screenHeight * 0.88;

    // Auto-scroll when messages update or state changes
    ref.listen(lemonAiProvider, (previous, next) {
      if (previous?.messages.length != next.messages.length ||
          previous?.uiState != next.uiState) {
        _scrollToBottom();
      }
    });

    return Padding(
      padding: EdgeInsets.only(bottom: bottomInset),
      child: Container(
        height: sheetHeight,
        decoration: const BoxDecoration(
          color: AppTheme.creamBg,
          borderRadius: BorderRadius.only(
            topLeft: Radius.circular(28),
            topRight: Radius.circular(28),
          ),
        ),
        child: SafeArea(
          top: false,
          child: Stack(
            children: [
              Column(
                children: [
                  // Top Drag Indicator
                  Center(
                    child: Container(
                      margin: const EdgeInsets.only(top: 8, bottom: 4),
                      width: 36,
                      height: 4,
                      decoration: BoxDecoration(
                        color: Colors.black26,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),

                  // Header
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                    decoration: const BoxDecoration(
                      color: AppTheme.darkBg,
                      borderRadius: BorderRadius.only(
                        topLeft: Radius.circular(24),
                        topRight: Radius.circular(24),
                      ),
                    ),
                    child: Row(
                      children: [
                        Container(
                          width: 34,
                          height: 34,
                          decoration: BoxDecoration(
                            color: AppTheme.primaryLemon.withOpacity(0.25),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: AppTheme.primaryLemon, width: 1.5),
                          ),
                          child: const Center(
                            child: Text('🍋', style: TextStyle(fontSize: 16)),
                          ),
                        ),
                        const SizedBox(width: 10),
                        const Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                'Lemon Drop AI ✨',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontWeight: FontWeight.w900,
                                  fontSize: 15,
                                ),
                              ),
                              Row(
                                children: [
                                  Icon(Icons.circle, color: Color(0xFF10B981), size: 7),
                                  SizedBox(width: 4),
                                  Text(
                                    'Asistente en línea',
                                    style: TextStyle(
                                      color: AppTheme.mintGreen,
                                      fontSize: 11,
                                      fontWeight: FontWeight.w500,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                        // TTS Toggle
                        IconButton(
                          visualDensity: VisualDensity.compact,
                          padding: EdgeInsets.zero,
                          constraints: const BoxConstraints(),
                          onPressed: () {
                            ref.read(lemonAiProvider.notifier).toggleTts();
                          },
                          icon: Icon(
                            aiState.isTtsEnabled ? Icons.volume_up_rounded : Icons.volume_off_rounded,
                            color: aiState.isTtsEnabled ? AppTheme.primaryLemon : Colors.white54,
                            size: 20,
                          ),
                          tooltip: aiState.isTtsEnabled ? 'Desactivar Voz' : 'Activar Voz',
                        ),
                        const SizedBox(width: 8),
                        // Reset Chat
                        IconButton(
                          visualDensity: VisualDensity.compact,
                          padding: EdgeInsets.zero,
                          constraints: const BoxConstraints(),
                          onPressed: () {
                            ref.read(lemonAiProvider.notifier).startNewConversation();
                          },
                          icon: const Icon(Icons.refresh_rounded, color: Colors.white70, size: 20),
                          tooltip: 'Nueva Conversación',
                        ),
                        const SizedBox(width: 8),
                        // Close
                        IconButton(
                          visualDensity: VisualDensity.compact,
                          padding: EdgeInsets.zero,
                          constraints: const BoxConstraints(),
                          onPressed: () => Navigator.pop(context),
                          icon: const Icon(Icons.close_rounded, color: Colors.white, size: 22),
                        ),
                      ],
                    ),
                  ),

                  // Quick Suggestion Chips Bar
                  if (aiState.suggestions.isNotEmpty)
                    Container(
                      height: 40,
                      color: AppTheme.softGreen.withOpacity(0.45),
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                        itemCount: aiState.suggestions.length,
                        separatorBuilder: (context, index) => const SizedBox(width: 6),
                        itemBuilder: (context, index) {
                          final suggestion = aiState.suggestions[index];
                          return InkWell(
                            onTap: () {
                              ref.read(lemonAiProvider.notifier).sendMessage(suggestion);
                            },
                            borderRadius: BorderRadius.circular(14),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                              decoration: BoxDecoration(
                                color: Colors.white,
                                borderRadius: BorderRadius.circular(14),
                                border: Border.all(color: AppTheme.darkGreen.withOpacity(0.25)),
                              ),
                              child: Center(
                                child: Text(
                                  suggestion,
                                  style: const TextStyle(
                                    fontSize: 11,
                                    fontWeight: FontWeight.bold,
                                    color: AppTheme.darkBg,
                                  ),
                                ),
                              ),
                            ),
                          );
                        },
                      ),
                    ),

                  // Messages List View
                  Expanded(
                    child: ListView.builder(
                      controller: _scrollController,
                      padding: const EdgeInsets.only(top: 6, bottom: 6),
                      itemCount: aiState.messages.length,
                      itemBuilder: (context, index) {
                        final msg = aiState.messages[index];
                        return LemonAiBubble(
                          message: msg,
                          onProductSelected: _handleProductSelected,
                          onModifyPressed: () {
                            _focusNode.requestFocus();
                          },
                          onTrackPressed: () {
                            Navigator.pop(context);
                            if (widget.onNavigateToTracking != null) {
                              widget.onNavigateToTracking!();
                            }
                          },
                        );
                      },
                    ),
                  ),

                  // Thinking / Transcribing Indicator
                  if (aiState.uiState == LemonAiUiState.thinking ||
                      aiState.uiState == LemonAiUiState.transcribing)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                      alignment: Alignment.centerLeft,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: AppTheme.mintGreen),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const SizedBox(
                              width: 12,
                              height: 12,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: AppTheme.darkGreen,
                              ),
                            ),
                            const SizedBox(width: 6),
                            Text(
                              aiState.uiState == LemonAiUiState.transcribing
                                  ? '🎙️ Entendiendo tu voz...'
                                  : '🍋 Estoy armando tu respuesta...',
                              style: const TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                color: AppTheme.darkGreen,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),

                  // Error Message Notice
                  if (aiState.errorMessage != null)
                    Container(
                      margin: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppTheme.strawberryRed.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: AppTheme.strawberryRed),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.info_outline, color: AppTheme.strawberryRed, size: 16),
                          const SizedBox(width: 6),
                          Expanded(
                            child: Text(
                              aiState.errorMessage!,
                              style: const TextStyle(fontSize: 11, color: AppTheme.strawberryRed),
                            ),
                          ),
                        ],
                      ),
                    ),

                  // Bottom Chat Input Bar
                  Container(
                    padding: const EdgeInsets.fromLTRB(10, 8, 10, 10),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      border: Border(top: BorderSide(color: Colors.black.withOpacity(0.08))),
                    ),
                    child: Row(
                      children: [
                        // Voice Mic Button
                        IconButton(
                          onPressed: () {
                            ref.read(lemonAiProvider.notifier).startVoiceRecording();
                          },
                          style: IconButton.styleFrom(
                            backgroundColor: AppTheme.creamBg,
                            side: const BorderSide(color: AppTheme.mintGreen, width: 1.5),
                            padding: const EdgeInsets.all(8),
                          ),
                          icon: const Icon(Icons.mic, color: AppTheme.darkGreen, size: 20),
                          tooltip: 'Hablar por micrófono',
                        ),
                        const SizedBox(width: 6),

                        // Text Field
                        Expanded(
                          child: TextField(
                            controller: _textController,
                            focusNode: _focusNode,
                            textInputAction: TextInputAction.send,
                            onSubmitted: (_) => _handleSend(),
                            style: const TextStyle(fontSize: 14, color: AppTheme.textDark),
                            decoration: InputDecoration(
                              hintText: 'Pide sabores, tamaños, toppings...',
                              hintStyle: TextStyle(
                                color: AppTheme.textGray.withOpacity(0.7),
                                fontSize: 13,
                              ),
                              contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                              filled: true,
                              fillColor: AppTheme.softGreen.withOpacity(0.35),
                              border: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(20),
                                borderSide: BorderSide.none,
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 6),

                        // Send Button
                        IconButton(
                          onPressed: _handleSend,
                          style: IconButton.styleFrom(
                            backgroundColor: AppTheme.primaryLemon,
                            foregroundColor: AppTheme.darkBg,
                            padding: const EdgeInsets.all(8),
                          ),
                          icon: const Icon(Icons.send_rounded, size: 20),
                          tooltip: 'Enviar mensaje',
                        ),
                      ],
                    ),
                  ),
                ],
              ),

              // Voice Recording Animated Overlay
              if (aiState.isRecording)
                LemonAiVoiceOverlay(
                  onStopAndSend: () {
                    ref.read(lemonAiProvider.notifier).stopAndSendVoiceRecording();
                  },
                  onCancel: () {
                    ref.read(lemonAiProvider.notifier).cancelVoiceRecording();
                  },
                ),
            ],
          ),
        ),
      ),
    );
  }
}
