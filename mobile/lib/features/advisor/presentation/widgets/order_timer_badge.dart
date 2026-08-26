import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/models/models.dart';
import '../../../../core/theme/app_theme.dart';
import '../../providers/orders_provider.dart';

class OrderTimerBadge extends ConsumerWidget {
  final Order order;
  final bool isCompact;

  const OrderTimerBadge({
    super.key,
    required this.order,
    this.isCompact = false,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Rebuild every second synchronously
    ref.watch(liveSecondsProvider);

    final now = DateTime.now();
    final info = _calculateTimerInfo(order, now);

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: isCompact ? 8 : 10,
        vertical: isCompact ? 3 : 5,
      ),
      decoration: BoxDecoration(
        color: info.bgColor,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: info.borderColor, width: 1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(info.icon, style: TextStyle(fontSize: isCompact ? 11 : 13)),
          const SizedBox(width: 4),
          Text(
            info.text,
            style: TextStyle(
              fontSize: isCompact ? 11 : 12,
              fontWeight: FontWeight.w800,
              color: info.textColor,
            ),
          ),
        ],
      ),
    );
  }

  _TimerInfo _calculateTimerInfo(Order order, DateTime now) {
    DateTime referenceTime;
    String prefix;

    switch (order.status) {
      case OrderStatus.received:
        referenceTime = order.receivedAt ?? order.createdAt;
        prefix = 'Espera: ';
        break;
      case OrderStatus.accepted:
        referenceTime = order.acceptedAt ?? order.updatedAt;
        prefix = 'Aceptado: ';
        break;
      case OrderStatus.preparing:
        referenceTime = order.preparingAt ?? order.updatedAt;
        prefix = 'Prep: ';
        break;
      case OrderStatus.almostReady:
        referenceTime = order.almostReadyAt ?? order.updatedAt;
        prefix = 'Casi listo: ';
        break;
      case OrderStatus.ready:
        referenceTime = order.readyAt ?? order.updatedAt;
        prefix = 'Listo hace: ';
        break;
      case OrderStatus.delivered:
        final duration = order.totalDuration;
        final mins = duration.inMinutes;
        return _TimerInfo(
          icon: '⏱️',
          text: 'Duración: ${mins > 0 ? '$mins min' : '< 1 min'}',
          bgColor: AppTheme.softGreen,
          borderColor: AppTheme.darkGreen.withOpacity(0.3),
          textColor: AppTheme.darkGreen,
        );
      case OrderStatus.cancelled:
        return _TimerInfo(
          icon: '❌',
          text: 'Cancelado',
          bgColor: AppTheme.strawberryRed.withOpacity(0.1),
          borderColor: AppTheme.strawberryRed.withOpacity(0.3),
          textColor: AppTheme.strawberryRed,
        );
    }

    final diff = now.difference(referenceTime);
    final totalSeconds = diff.inSeconds < 0 ? 0 : diff.inSeconds;
    final mins = (totalSeconds ~/ 60).toString().padLeft(2, '0');
    final secs = (totalSeconds % 60).toString().padLeft(2, '0');

    final isLongWait = totalSeconds > 600; // > 10 min
    final isUrgentOrLong = order.isUrgent || isLongWait;

    return _TimerInfo(
      icon: order.status == OrderStatus.preparing ? '👨‍🍳' : '⏱️',
      text: '$prefix$mins:$secs',
      bgColor: isUrgentOrLong ? const Color(0xFFFFEBEE) : const Color(0xFFF1F5F9),
      borderColor: isUrgentOrLong ? const Color(0xFFEF5350) : const Color(0xFFCBD5E1),
      textColor: isUrgentOrLong ? const Color(0xFFC62828) : const Color(0xFF334155),
    );
  }
}

class _TimerInfo {
  final String icon;
  final String text;
  final Color bgColor;
  final Color borderColor;
  final Color textColor;

  _TimerInfo({
    required this.icon,
    required this.text,
    required this.bgColor,
    required this.borderColor,
    required this.textColor,
  });
}
