import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/app_theme.dart';
import '../../data/models/ai_models.dart';
import '../providers/lemon_ai_provider.dart';

class LemonAiOrderCard extends ConsumerWidget {
  final AICartDto cart;
  final VoidCallback? onModifyPressed;

  const LemonAiOrderCard({
    super.key,
    required this.cart,
    this.onModifyPressed,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final currencyFormat = NumberFormat.currency(locale: 'es_CO', symbol: '\$', decimalDigits: 0);

    return Container(
      margin: const EdgeInsets.only(top: 8, bottom: 4),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.creamBg,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppTheme.primaryLemon, width: 2),
        boxShadow: [
          BoxShadow(
            color: AppTheme.primaryLemon.withOpacity(0.2),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Row(
                children: [
                  Text('🛒', style: TextStyle(fontSize: 20)),
                  SizedBox(width: 8),
                  Text(
                    'Resumen de tu Pedido',
                    style: TextStyle(
                      fontWeight: FontWeight.w900,
                      fontSize: 16,
                      color: AppTheme.darkBg,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: AppTheme.mintGreen,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  '${cart.totalItems} ${cart.totalItems == 1 ? 'ítem' : 'ítems'}',
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 12,
                    color: AppTheme.darkGreen,
                  ),
                ),
              ),
            ],
          ),
          const Divider(height: 20, color: Color(0x222E6F40)),

          // Items List
          ...cart.items.map((item) {
            final addonsText = item.addonNames.isNotEmpty
                ? '+ ${item.addonNames.join(', ')}'
                : null;

            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 4.0),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '${item.quantity}x ${item.productName ?? 'Granizado'} (${item.flavorName ?? 'Natural'})',
                          style: const TextStyle(
                            fontWeight: FontWeight.w800,
                            fontSize: 14,
                            color: AppTheme.textDark,
                          ),
                        ),
                        Text(
                          'Tamaño: ${item.size}',
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppTheme.textGray,
                          ),
                        ),
                        if (addonsText != null)
                          Text(
                            addonsText,
                            style: const TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                              color: AppTheme.darkGreen,
                            ),
                          ),
                        if (item.observations != null && item.observations!.isNotEmpty)
                          Text(
                            '"${item.observations}"',
                            style: const TextStyle(
                              fontSize: 11,
                              fontStyle: FontStyle.italic,
                              color: AppTheme.textGray,
                            ),
                          ),
                      ],
                    ),
                  ),
                  Text(
                    currencyFormat.format(item.subtotal),
                    style: const TextStyle(
                      fontWeight: FontWeight.w900,
                      fontSize: 14,
                      color: AppTheme.darkGreen,
                    ),
                  ),
                ],
              ),
            );
          }),

          const Divider(height: 20, color: Color(0x222E6F40)),

          // Total Row
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Total a Pagar:',
                style: TextStyle(
                  fontWeight: FontWeight.w800,
                  fontSize: 16,
                  color: AppTheme.darkBg,
                ),
              ),
              Text(
                currencyFormat.format(cart.total),
                style: const TextStyle(
                  fontWeight: FontWeight.w900,
                  fontSize: 20,
                  color: AppTheme.darkGreen,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),

          // Action Buttons
          Row(
            children: [
              Expanded(
                flex: 2,
                child: ElevatedButton.icon(
                  onPressed: () {
                    ref.read(lemonAiProvider.notifier).confirmOrder();
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.primaryLemon,
                    foregroundColor: AppTheme.darkBg,
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  icon: const Text('🍨', style: TextStyle(fontSize: 16)),
                  label: const Text(
                    'Confirmar Pedido',
                    style: TextStyle(fontWeight: FontWeight.w900, fontSize: 13),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                flex: 1,
                child: OutlinedButton(
                  onPressed: onModifyPressed,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppTheme.darkGreen,
                    side: const BorderSide(color: AppTheme.darkGreen, width: 1.5),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  child: const Text(
                    'Modificar',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12),
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
