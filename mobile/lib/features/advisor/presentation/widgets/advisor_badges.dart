import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../../core/models/models.dart';
import '../../../../core/theme/app_theme.dart';

class PriorityBadge extends StatelessWidget {
  final bool isUrgent;
  final bool isCompact;

  const PriorityBadge({
    super.key,
    required this.isUrgent,
    this.isCompact = false,
  });

  @override
  Widget build(BuildContext context) {
    if (!isUrgent) return const SizedBox.shrink();

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: isCompact ? 6 : 8,
        vertical: isCompact ? 2 : 4,
      ),
      decoration: BoxDecoration(
        color: const Color(0xFFFFEBEE),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppTheme.strawberryRed, width: 1.2),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('🔥', style: TextStyle(fontSize: 10)),
          const SizedBox(width: 3),
          Text(
            'ALTA',
            style: TextStyle(
              fontSize: isCompact ? 9 : 10,
              fontWeight: FontWeight.w900,
              color: AppTheme.strawberryRed,
              letterSpacing: 0.5,
            ),
          ),
        ],
      ),
    );
  }
}

class AdvisorBadge extends StatelessWidget {
  final String? assignedAdvisor;
  final String? currentUsername;
  final bool isCompact;

  const AdvisorBadge({
    super.key,
    required this.assignedAdvisor,
    this.currentUsername,
    this.isCompact = false,
  });

  @override
  Widget build(BuildContext context) {
    final isUnassigned = assignedAdvisor == null ||
        assignedAdvisor!.trim().isEmpty ||
        assignedAdvisor!.equalsIgnoreCase('Sin asignar') ||
        assignedAdvisor!.equalsIgnoreCase('Ninguno');

    final isMe = !isUnassigned &&
        currentUsername != null &&
        assignedAdvisor!.equalsIgnoreCase(currentUsername!);

    final bgColor = isMe
        ? AppTheme.softGreen
        : (isUnassigned ? const Color(0xFFF5F5F5) : const Color(0xFFE0F2FE));

    final textColor = isMe
        ? AppTheme.darkGreen
        : (isUnassigned ? AppTheme.textGray : const Color(0xFF0369A1));

    final label = isMe
        ? 'Por ti'
        : (isUnassigned ? 'Sin asignar' : assignedAdvisor!);

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: isCompact ? 6 : 8,
        vertical: isCompact ? 2 : 4,
      ),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            isMe ? Icons.check_circle_outline : (isUnassigned ? Icons.person_outline : Icons.person),
            size: isCompact ? 11 : 13,
            color: textColor,
          ),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: isCompact ? 10 : 11,
              fontWeight: FontWeight.bold,
              color: textColor,
            ),
          ),
        ],
      ),
    );
  }
}

class OrderTimelineWidget extends StatelessWidget {
  final List<OrderStatusHistoryEntry> statusHistory;
  final List<OrderChangeHistoryEntry> changeHistory;

  const OrderTimelineWidget({
    super.key,
    required this.statusHistory,
    required this.changeHistory,
  });

  @override
  Widget build(BuildContext context) {
    if (statusHistory.isEmpty && changeHistory.isEmpty) {
      return const Padding(
        padding: EdgeInsets.all(16.0),
        child: Center(
          child: Text(
            'No hay registros de trazabilidad disponibles.',
            style: TextStyle(color: AppTheme.textGray, fontSize: 13),
          ),
        ),
      );
    }

    final timeFormatter = DateFormat('hh:mm a');

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (statusHistory.isNotEmpty) ...[
          const Text(
            'Historial de Estados',
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppTheme.darkBg),
          ),
          const SizedBox(height: 12),
          ListView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: statusHistory.length,
            itemBuilder: (context, index) {
              final entry = statusHistory[index];
              final isLast = index == statusHistory.length - 1;

              return Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Column(
                    children: [
                      Container(
                        width: 12,
                        height: 12,
                        decoration: BoxDecoration(
                          color: _getStatusColor(entry.newStatus),
                          shape: BoxShape.circle,
                        ),
                      ),
                      if (!isLast)
                        Container(
                          width: 2,
                          height: 38,
                          color: Colors.grey.shade300,
                        ),
                    ],
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.only(bottom: 12.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                entry.newStatus.nameInSpanish,
                                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                              ),
                              Text(
                                timeFormatter.format(entry.updatedAt),
                                style: const TextStyle(fontSize: 11, color: AppTheme.textGray),
                              ),
                            ],
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'Por: ${entry.updatedBy}',
                            style: const TextStyle(fontSize: 11, color: AppTheme.textGray),
                          ),
                          if (entry.notes != null && entry.notes!.trim().isNotEmpty) ...[
                            const SizedBox(height: 2),
                            Text(
                              'Nota: "${entry.notes}"',
                              style: const TextStyle(fontSize: 11, fontStyle: FontStyle.italic, color: AppTheme.darkGreen),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                ],
              );
            },
          ),
        ],
        if (changeHistory.isNotEmpty) ...[
          const SizedBox(height: 16),
          const Text(
            'Modificaciones Realizadas',
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppTheme.strawberryRed),
          ),
          const SizedBox(height: 8),
          ListView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: changeHistory.length,
            itemBuilder: (context, index) {
              final change = changeHistory[index];
              return Card(
                color: const Color(0xFFFFF8F8),
                margin: const EdgeInsets.only(bottom: 8),
                child: Padding(
                  padding: const EdgeInsets.all(12.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            'Cambio en: ${change.propertyName}',
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12, color: AppTheme.darkBg),
                          ),
                          Text(
                            timeFormatter.format(change.updatedAt),
                            style: const TextStyle(fontSize: 10, color: AppTheme.textGray),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      if (change.oldValue != null && change.newValue != null)
                        Text(
                          '${change.oldValue} → ${change.newValue}',
                          style: const TextStyle(fontSize: 12, color: AppTheme.darkGreen, fontWeight: FontWeight.w600),
                        ),
                      const SizedBox(height: 4),
                      Text(
                        'Motivo: ${change.reason} (por ${change.updatedBy})',
                        style: const TextStyle(fontSize: 11, fontStyle: FontStyle.italic, color: AppTheme.textGray),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
        ],
      ],
    );
  }

  Color _getStatusColor(OrderStatus status) {
    switch (status) {
      case OrderStatus.received:
        return AppTheme.primaryLemon;
      case OrderStatus.accepted:
        return AppTheme.mintGreen;
      case OrderStatus.preparing:
        return Colors.orange;
      case OrderStatus.almostReady:
        return AppTheme.softGreen;
      case OrderStatus.ready:
        return AppTheme.darkGreen;
      case OrderStatus.delivered:
        return Colors.blue;
      case OrderStatus.cancelled:
        return AppTheme.strawberryRed;
    }
  }
}
