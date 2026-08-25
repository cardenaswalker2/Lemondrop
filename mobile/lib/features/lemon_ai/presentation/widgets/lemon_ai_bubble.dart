import 'package:flutter/material.dart';
import '../../../../core/theme/app_theme.dart';
import '../../data/models/ai_models.dart';
import 'lemon_ai_order_card.dart';
import 'lemon_ai_product_carousel.dart';
import 'lemon_ai_success_card.dart';

class LemonAiBubble extends StatelessWidget {
  final AIMessage message;
  final ValueChanged<AIProductCardDto>? onProductSelected;
  final VoidCallback? onModifyPressed;
  final VoidCallback? onTrackPressed;

  const LemonAiBubble({
    super.key,
    required this.message,
    this.onProductSelected,
    this.onModifyPressed,
    this.onTrackPressed,
  });

  @override
  Widget build(BuildContext context) {
    final isUser = message.isUser;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5.0, horizontal: 12.0),
      child: Row(
        mainAxisAlignment: isUser ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (!isUser) ...[
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: AppTheme.primaryLemon.withOpacity(0.35),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: AppTheme.primaryLemon, width: 1.5),
              ),
              child: const Center(
                child: Text('🍋', style: TextStyle(fontSize: 16)),
              ),
            ),
            const SizedBox(width: 8),
          ],
          Flexible(
            child: Column(
              crossAxisAlignment: isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
              children: [
                if (message.content.isNotEmpty)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                    decoration: BoxDecoration(
                      color: isUser ? AppTheme.darkGreen : Colors.white,
                      borderRadius: BorderRadius.only(
                        topLeft: const Radius.circular(18),
                        topRight: const Radius.circular(18),
                        bottomLeft: isUser ? const Radius.circular(18) : const Radius.circular(4),
                        bottomRight: isUser ? const Radius.circular(4) : const Radius.circular(18),
                      ),
                      border: isUser
                          ? null
                          : Border.all(color: AppTheme.mintGreen.withOpacity(0.6), width: 1),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.04),
                          blurRadius: 6,
                          offset: const Offset(0, 2),
                        ),
                      ],
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        if (isUser && message.isVoice) ...[
                          const Icon(
                            Icons.mic,
                            size: 14,
                            color: Colors.white70,
                          ),
                          const SizedBox(width: 4),
                        ],
                        Flexible(
                          child: Text(
                            message.content,
                            style: TextStyle(
                              fontSize: 14,
                              height: 1.35,
                              color: isUser ? Colors.white : AppTheme.textDark,
                              fontWeight: isUser ? FontWeight.w600 : FontWeight.normal,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                // Attached Structured Product Cards Carousel
                if (message.products.isNotEmpty)
                  LemonAiProductCarousel(
                    products: message.products,
                    onProductSelected: onProductSelected,
                  ),

                // Attached Structured Order Card
                if (message.requiresConfirmation && message.cartSnapshot != null)
                  LemonAiOrderCard(
                    cart: message.cartSnapshot!,
                    onModifyPressed: onModifyPressed,
                  ),

                // Attached Confirmed Order Success Card
                if (message.isOrderConfirmed && message.orderCode != null)
                  LemonAiSuccessCard(
                    orderCode: message.orderCode!,
                    whatsAppUrl: message.whatsAppUrl,
                    onTrackPressed: onTrackPressed,
                  ),
              ],
            ),
          ),
          if (isUser) const SizedBox(width: 4),
        ],
      ),
    );
  }
}
