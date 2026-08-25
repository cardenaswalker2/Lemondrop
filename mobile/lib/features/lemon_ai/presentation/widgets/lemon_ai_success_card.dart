import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../../core/theme/app_theme.dart';

class LemonAiSuccessCard extends StatelessWidget {
  final String orderCode;
  final String? whatsAppUrl;
  final VoidCallback? onTrackPressed;

  const LemonAiSuccessCard({
    super.key,
    required this.orderCode,
    this.whatsAppUrl,
    this.onTrackPressed,
  });

  Future<void> _openWhatsApp(BuildContext context) async {
    if (whatsAppUrl == null || whatsAppUrl!.isEmpty) return;
    try {
      final uri = Uri.parse(whatsAppUrl!);
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      }
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('No se pudo abrir WhatsApp automáticamente.')),
        );
      }
    }
  }

  void _copyOrderCode(BuildContext context) {
    Clipboard.setData(ClipboardData(text: orderCode));
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Código $orderCode copiado al portapapeles ✓'),
        duration: const Duration(seconds: 2),
        backgroundColor: AppTheme.darkGreen,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: 8, bottom: 4),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppTheme.darkGreen, width: 2),
        boxShadow: [
          BoxShadow(
            color: AppTheme.darkGreen.withOpacity(0.15),
            blurRadius: 16,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Header Badge
          const Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text('🎉', style: TextStyle(fontSize: 22)),
              SizedBox(width: 8),
              Text(
                'PEDIDO RECIBIDO',
                style: TextStyle(
                  fontWeight: FontWeight.w900,
                  fontSize: 18,
                  color: AppTheme.darkGreen,
                  letterSpacing: 0.5,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Order Code Pill (Clickable to copy)
          GestureDetector(
            onTap: () => _copyOrderCode(context),
            child: Container(
              padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 16),
              decoration: BoxDecoration(
                color: AppTheme.primaryLemon.withOpacity(0.3),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppTheme.primaryLemon, width: 2),
              ),
              child: Column(
                children: [
                  Text(
                    orderCode,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontFamily: 'monospace',
                      fontWeight: FontWeight.w900,
                      fontSize: 20,
                      color: AppTheme.darkBg,
                      letterSpacing: 2,
                    ),
                  ),
                  const SizedBox(height: 2),
                  const Text(
                    'Toca para copiar código 📋',
                    style: TextStyle(fontSize: 10, color: AppTheme.textGray),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 10),

          // Status Badge
          Container(
            padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 12),
            decoration: BoxDecoration(
              color: AppTheme.softGreen,
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Text(
              '🟢 Estado: Pedido recibido',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontWeight: FontWeight.w800,
                fontSize: 13,
                color: AppTheme.darkGreen,
              ),
            ),
          ),
          const SizedBox(height: 10),

          // Explanation Note
          const Text(
            'Tu pedido fue registrado correctamente. Te avisaremos por WhatsApp cuando esté listo para recoger en el stand.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 12,
              color: AppTheme.textGray,
              height: 1.4,
            ),
          ),
          const SizedBox(height: 14),

          // Action Buttons
          Row(
            children: [
              if (onTrackPressed != null) ...[
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: onTrackPressed,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppTheme.darkGreen,
                      side: const BorderSide(color: AppTheme.darkGreen, width: 1.5),
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    icon: const Text('🔎', style: TextStyle(fontSize: 14)),
                    label: const Text(
                      'Seguimiento',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
              ],
              if (whatsAppUrl != null && whatsAppUrl!.isNotEmpty)
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: () => _openWhatsApp(context),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppTheme.primaryLemon,
                      foregroundColor: AppTheme.darkBg,
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    icon: const Text('📱', style: TextStyle(fontSize: 14)),
                    label: const Text(
                      'Abrir WhatsApp',
                      style: TextStyle(fontWeight: FontWeight.w900, fontSize: 12),
                    ),
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}
