import 'package:flutter/material.dart';
import '../../../../core/theme/app_theme.dart';

class LemonAiVoiceOverlay extends StatefulWidget {
  final VoidCallback onStopAndSend;
  final VoidCallback onCancel;

  const LemonAiVoiceOverlay({
    super.key,
    required this.onStopAndSend,
    required this.onCancel,
  });

  @override
  State<LemonAiVoiceOverlay> createState() => _LemonAiVoiceOverlayState();
}

class _LemonAiVoiceOverlayState extends State<LemonAiVoiceOverlay>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _pulseAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
    )..repeat(reverse: true);

    _pulseAnimation = Tween<double>(begin: 1.0, end: 1.25).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
      decoration: BoxDecoration(
        color: AppTheme.darkBg.withOpacity(0.95),
        borderRadius: const BorderRadius.only(
          topLeft: Radius.circular(24),
          topRight: Radius.circular(24),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.3),
            blurRadius: 20,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Waveform Animation Stage
          AnimatedBuilder(
            animation: _pulseAnimation,
            builder: (context, child) {
              return Transform.scale(
                scale: _pulseAnimation.value,
                child: Container(
                  width: 60,
                  height: 60,
                  decoration: BoxDecoration(
                    color: AppTheme.primaryLemon,
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: AppTheme.primaryLemon.withOpacity(0.5),
                        blurRadius: 16,
                        spreadRadius: 4,
                      ),
                    ],
                  ),
                  child: const Center(
                    child: Icon(
                      Icons.mic,
                      size: 32,
                      color: AppTheme.darkBg,
                    ),
                  ),
                ),
              );
            },
          ),
          const SizedBox(height: 12),
          const Text(
            'Escuchando tu pedido... 🍋',
            style: TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.w800,
              fontSize: 15,
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            'Habla con naturalidad (ej. "Un granizado de mango grande")',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: AppTheme.mintGreen,
              fontSize: 12,
            ),
          ),
          const SizedBox(height: 16),

          // Actions
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              OutlinedButton.icon(
                onPressed: widget.onCancel,
                style: OutlinedButton.styleFrom(
                  foregroundColor: Colors.white70,
                  side: const BorderSide(color: Colors.white30),
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                icon: const Icon(Icons.close, size: 18),
                label: const Text('Cancelar', style: TextStyle(fontSize: 13)),
              ),
              ElevatedButton.icon(
                onPressed: widget.onStopAndSend,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.primaryLemon,
                  foregroundColor: AppTheme.darkBg,
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                icon: const Icon(Icons.send_rounded, size: 18),
                label: const Text(
                  'Enviar Voz',
                  style: TextStyle(fontWeight: FontWeight.w900, fontSize: 13),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
