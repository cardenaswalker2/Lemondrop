import 'package:flutter/material.dart';
import '../../../../core/models/models.dart';
import '../../../../core/theme/app_theme.dart';

class OrderStatusBadge extends StatelessWidget {
  final OrderStatus status;
  final bool isCompact;

  const OrderStatusBadge({
    super.key,
    required this.status,
    this.isCompact = false,
  });

  @override
  Widget build(BuildContext context) {
    final config = _getStatusConfig(status);

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: isCompact ? 10 : 14,
        vertical: isCompact ? 5 : 7,
      ),
      decoration: BoxDecoration(
        color: config.backgroundColor,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: config.borderColor, width: 1.2),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(config.emoji, style: TextStyle(fontSize: isCompact ? 12 : 14)),
          const SizedBox(width: 6),
          Text(
            config.label.toUpperCase(),
            style: TextStyle(
              fontSize: isCompact ? 11 : 12,
              fontWeight: FontWeight.w900,
              color: config.textColor,
              letterSpacing: 0.4,
            ),
          ),
        ],
      ),
    );
  }

  static _StatusConfig _getStatusConfig(OrderStatus status) {
    switch (status) {
      case OrderStatus.received:
        return _StatusConfig(
          label: 'Recibido',
          emoji: '🟡',
          backgroundColor: AppTheme.primaryLemon.withOpacity(0.2),
          borderColor: AppTheme.primaryLemon,
          textColor: AppTheme.darkBg,
        );
      case OrderStatus.accepted:
        return _StatusConfig(
          label: 'Aceptado',
          emoji: '🟢',
          backgroundColor: AppTheme.softGreen,
          borderColor: AppTheme.darkGreen.withOpacity(0.4),
          textColor: AppTheme.darkGreen,
        );
      case OrderStatus.preparing:
        return _StatusConfig(
          label: 'Preparando',
          emoji: '🟠',
          backgroundColor: const Color(0xFFFFF3E0),
          borderColor: const Color(0xFFFF9800),
          textColor: const Color(0xFFE65100),
        );
      case OrderStatus.almostReady:
        return _StatusConfig(
          label: 'Casi listo',
          emoji: '✨',
          backgroundColor: const Color(0xFFF1F8E9),
          borderColor: const Color(0xFF8BC34A),
          textColor: const Color(0xFF33691E),
        );
      case OrderStatus.ready:
        return _StatusConfig(
          label: 'Listo para recoger',
          emoji: '🎉',
          backgroundColor: AppTheme.mintGreen.withOpacity(0.4),
          borderColor: AppTheme.darkGreen,
          textColor: AppTheme.darkGreen,
        );
      case OrderStatus.delivered:
        return _StatusConfig(
          label: 'Entregado',
          emoji: '✅',
          backgroundColor: AppTheme.softGreen,
          borderColor: AppTheme.darkGreen,
          textColor: AppTheme.darkGreen,
        );
      case OrderStatus.cancelled:
        return _StatusConfig(
          label: 'Cancelado',
          emoji: '❌',
          backgroundColor: const Color(0xFFFFEBEE),
          borderColor: AppTheme.strawberryRed,
          textColor: AppTheme.strawberryRed,
        );
    }
  }
}

class _StatusConfig {
  final String label;
  final String emoji;
  final Color backgroundColor;
  final Color borderColor;
  final Color textColor;

  _StatusConfig({
    required this.label,
    required this.emoji,
    required this.backgroundColor,
    required this.borderColor,
    required this.textColor,
  });
}
