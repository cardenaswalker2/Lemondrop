import 'package:flutter/material.dart';
import '../../../../core/models/models.dart';
import '../../../../core/theme/app_theme.dart';

class OrderTrackingTimeline extends StatelessWidget {
  final OrderStatus status;
  final String? cancellationReason;

  const OrderTrackingTimeline({
    super.key,
    required this.status,
    this.cancellationReason,
  });

  static const List<_TimelineStepData> _steps = [
    _TimelineStepData(
      stepNumber: 1,
      title: 'Recibido',
      subtitle: 'Orden registrada en el sistema',
      status: OrderStatus.received,
      icon: Icons.receipt_long_rounded,
    ),
    _TimelineStepData(
      stepNumber: 2,
      title: 'Aceptado',
      subtitle: 'Confirmado por nuestro equipo',
      status: OrderStatus.accepted,
      icon: Icons.check_circle_outline_rounded,
    ),
    _TimelineStepData(
      stepNumber: 3,
      title: 'Preparando',
      subtitle: 'En cocina preparando tu granizado',
      status: OrderStatus.preparing,
      icon: Icons.blender_rounded,
    ),
    _TimelineStepData(
      stepNumber: 4,
      title: 'Casi listo',
      subtitle: 'Agregando toppings y detalles finales',
      status: OrderStatus.almostReady,
      icon: Icons.auto_awesome_rounded,
    ),
    _TimelineStepData(
      stepNumber: 5,
      title: 'Listo',
      subtitle: '¡Listo para recoger en el stand!',
      status: OrderStatus.ready,
      icon: Icons.storefront_rounded,
    ),
    _TimelineStepData(
      stepNumber: 6,
      title: 'Entregado',
      subtitle: '¡Disfruta tu Lemon Drop! 🍋',
      status: OrderStatus.delivered,
      icon: Icons.task_alt_rounded,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    if (status == OrderStatus.cancelled) {
      return _buildCancelledCard();
    }

    final currentStep = status.trackingStepIndex;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: AppTheme.darkGreen.withOpacity(0.12)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.04),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text('📦', style: TextStyle(fontSize: 20)),
              const SizedBox(width: 8),
              const Expanded(
                child: Text(
                  'Seguimiento en Tiempo Real',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w900,
                    color: AppTheme.darkBg,
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: AppTheme.softGreen,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  'Paso $currentStep de 6',
                  style: const TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                    color: AppTheme.darkGreen,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          ...List.generate(_steps.length, (index) {
            final step = _steps[index];
            final isCompleted = currentStep > step.stepNumber ||
                (currentStep == 6 && step.stepNumber == 6);
            final isActive = currentStep == step.stepNumber && currentStep != 6;
            final isLast = index == _steps.length - 1;

            return _buildTimelineItem(
              step: step,
              isCompleted: isCompleted,
              isActive: isActive,
              isLast: isLast,
            );
          }),
        ],
      ),
    );
  }

  Widget _buildTimelineItem({
    required _TimelineStepData step,
    required bool isCompleted,
    required bool isActive,
    required bool isLast,
  }) {
    Color circleBg;
    Color iconColor;
    Border? circleBorder;
    List<BoxShadow>? shadows;

    if (isCompleted) {
      circleBg = AppTheme.darkGreen;
      iconColor = Colors.white;
    } else if (isActive) {
      circleBg = AppTheme.primaryLemon;
      iconColor = AppTheme.darkBg;
      circleBorder = Border.all(color: AppTheme.darkGreen, width: 2);
      shadows = [
        BoxShadow(
          color: AppTheme.primaryLemon.withOpacity(0.6),
          blurRadius: 10,
          spreadRadius: 2,
        ),
      ];
    } else {
      circleBg = const Color(0xFFE2E8F0);
      iconColor = const Color(0xFF94A3B8);
    }

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Left Column: Dot & Line
        Column(
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: circleBg,
                shape: BoxShape.circle,
                border: circleBorder,
                boxShadow: shadows,
              ),
              child: Center(
                child: isCompleted
                    ? const Icon(Icons.check_rounded, color: Colors.white, size: 20)
                    : (isActive
                        ? const Text('🍋', style: TextStyle(fontSize: 16))
                        : Text(
                            '${step.stepNumber}',
                            style: TextStyle(
                              color: iconColor,
                              fontWeight: FontWeight.bold,
                              fontSize: 13,
                            ),
                          )),
              ),
            ),
            if (!isLast)
              Container(
                width: 3,
                height: 38,
                margin: const EdgeInsets.symmetric(vertical: 3),
                decoration: BoxDecoration(
                  color: isCompleted ? AppTheme.darkGreen : const Color(0xFFE2E8F0),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
          ],
        ),
        const SizedBox(width: 14),

        // Right Column: Title and Subtitle
        Expanded(
          child: Padding(
            padding: const EdgeInsets.only(top: 6, bottom: 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      step.title,
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: (isActive || isCompleted)
                            ? FontWeight.w900
                            : FontWeight.w600,
                        color: isActive
                            ? AppTheme.darkGreen
                            : (isCompleted
                                ? AppTheme.darkBg
                                : const Color(0xFF94A3B8)),
                      ),
                    ),
                    if (isActive) ...[
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: AppTheme.primaryLemon,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: const Text(
                          'EN PROCESO',
                          style: TextStyle(
                            fontSize: 9,
                            fontWeight: FontWeight.w900,
                            color: AppTheme.darkBg,
                          ),
                        ),
                      ),
                    ] else if (isCompleted) ...[
                      const SizedBox(width: 6),
                      const Icon(Icons.check_circle_rounded,
                          size: 16, color: AppTheme.darkGreen),
                    ],
                  ],
                ),
                const SizedBox(height: 2),
                Text(
                  step.subtitle,
                  style: TextStyle(
                    fontSize: 12,
                    color: (isActive || isCompleted)
                        ? AppTheme.textGray
                        : const Color(0xFF94A3B8),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildCancelledCard() {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: const Color(0xFFFFEBEE),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppTheme.strawberryRed, width: 1.5),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: const BoxDecoration(
              color: Colors.white,
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.cancel_rounded, color: AppTheme.strawberryRed, size: 28),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Pedido Cancelado',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w900,
                    color: Color(0xFFB71C1C),
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  (cancellationReason != null && cancellationReason!.trim().isNotEmpty)
                      ? 'Motivo: $cancellationReason'
                      : 'Este pedido fue cancelado por el equipo operativo.',
                  style: const TextStyle(
                    fontSize: 13,
                    color: Color(0xFFC62828),
                    height: 1.3,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _TimelineStepData {
  final int stepNumber;
  final String title;
  final String subtitle;
  final OrderStatus status;
  final IconData icon;

  const _TimelineStepData({
    required this.stepNumber,
    required this.title,
    required this.subtitle,
    required this.status,
    required this.icon,
  });
}
