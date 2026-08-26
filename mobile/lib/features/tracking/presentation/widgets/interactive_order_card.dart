import 'package:flutter/material.dart';
import '../../../../core/models/models.dart';
import '../../../../core/theme/app_theme.dart';
import '../../../../core/utils/app_formatters.dart';
import 'order_status_badge.dart';

class InteractiveOrderCard extends StatelessWidget {
  final Order order;
  final VoidCallback onTap;

  const InteractiveOrderCard({
    super.key,
    required this.order,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: order.status == OrderStatus.cancelled
              ? AppTheme.strawberryRed.withOpacity(0.4)
              : AppTheme.darkGreen.withOpacity(0.12),
          width: 1.5,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.04),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(22),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(22),
          splashColor: AppTheme.primaryLemon.withOpacity(0.2),
          highlightColor: AppTheme.softGreen.withOpacity(0.3),
          child: Padding(
            padding: const EdgeInsets.all(18.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Top Row: Code and Status
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(6),
                          decoration: BoxDecoration(
                            color: AppTheme.softGreen,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Text('📋', style: TextStyle(fontSize: 14)),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          order.orderCode,
                          style: const TextStyle(
                            fontFamily: 'monospace',
                            fontWeight: FontWeight.w900,
                            fontSize: 15,
                            color: AppTheme.darkBg,
                            letterSpacing: 0.8,
                          ),
                        ),
                      ],
                    ),
                    OrderStatusBadge(status: order.status, isCompact: true),
                  ],
                ),
                const SizedBox(height: 12),

                // Greeting & Customer name
                if (order.customerName.isNotEmpty) ...[
                  Text(
                    '¡Hola, ${order.customerName}! 👋',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: AppTheme.darkBg,
                    ),
                  ),
                  const SizedBox(height: 4),
                ],

                // Dynamic short status note
                Text(
                  order.status == OrderStatus.cancelled
                      ? 'Este pedido fue cancelado.'
                      : (order.status == OrderStatus.delivered
                          ? 'Pedido entregado con éxito.'
                          : 'Tu pedido está en seguimiento en tiempo real.'),
                  style: TextStyle(
                    fontSize: 12,
                    color: order.status == OrderStatus.cancelled
                        ? AppTheme.strawberryRed
                        : AppTheme.textGray,
                  ),
                ),
                const SizedBox(height: 12),
                const Divider(height: 1),
                const SizedBox(height: 10),

                // Items summary (up to 2 items previewed)
                ...order.items.take(2).map((item) {
                  final toppingsText = item.addons.isNotEmpty
                      ? ' (+${item.addons.map((a) => a.addonName).join(', ')})'
                      : '';
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 2.5),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: Text(
                            '${item.quantity}x ${item.productName} (${item.flavorName})$toppingsText',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: AppTheme.darkBg,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          AppFormatters.formatCurrency(item.subtotal),
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.bold,
                            color: AppTheme.darkGreen,
                          ),
                        ),
                      ],
                    ),
                  );
                }),
                if (order.items.length > 2) ...[
                  Padding(
                    padding: const EdgeInsets.only(top: 2.0),
                    child: Text(
                      '+ ${order.items.length - 2} producto(s) más...',
                      style: const TextStyle(
                        fontSize: 11,
                        fontStyle: FontStyle.italic,
                        color: AppTheme.textGray,
                      ),
                    ),
                  ),
                ],

                const SizedBox(height: 12),
                const Divider(height: 1),
                const SizedBox(height: 12),

                // Bottom Row: Total and "Ver seguimiento →"
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Total:',
                          style: TextStyle(fontSize: 11, color: AppTheme.textGray, fontWeight: FontWeight.w600),
                        ),
                        Text(
                          AppFormatters.formatCurrency(order.total),
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w900,
                            color: AppTheme.darkGreen,
                          ),
                        ),
                      ],
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                      decoration: BoxDecoration(
                        color: AppTheme.primaryLemon,
                        borderRadius: BorderRadius.circular(14),
                        boxShadow: [
                          BoxShadow(
                            color: AppTheme.primaryLemon.withOpacity(0.4),
                            blurRadius: 6,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            'Ver seguimiento',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w900,
                              color: AppTheme.darkBg,
                            ),
                          ),
                          SizedBox(width: 6),
                          Icon(
                            Icons.arrow_forward_rounded,
                            size: 16,
                            color: AppTheme.darkBg,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
