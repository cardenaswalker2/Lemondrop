import 'package:flutter/material.dart';
import '../../../../core/theme/app_theme.dart';
import '../../data/models/ai_models.dart';

class LemonAiProductCard extends StatelessWidget {
  final AIProductCardDto product;
  final ValueChanged<AIProductCardDto>? onSelect;

  const LemonAiProductCard({
    super.key,
    required this.product,
    this.onSelect,
  });

  String _getEmojiForProduct(String name) {
    final lower = name.toLowerCase();
    if (lower.contains('mango')) return '🥭';
    if (lower.contains('fresa') || lower.contains('frutos rojos') || lower.contains('mora')) return '🍓';
    if (lower.contains('limon') || lower.contains('limón')) return '🍋';
    if (lower.contains('maracuy') || lower.contains('tropical') || lower.contains('piña')) return '🍍';
    if (lower.contains('mandarina') || lower.contains('naranja')) return '🍊';
    return '🍧';
  }

  LinearGradient _getGradientForProduct(String name) {
    final lower = name.toLowerCase();
    if (lower.contains('mango')) {
      return const LinearGradient(
        colors: [Color(0xFFFFE082), Color(0xFFFFB74D)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      );
    }
    if (lower.contains('fresa') || lower.contains('mora')) {
      return const LinearGradient(
        colors: [Color(0xFFFFCDD2), Color(0xFFEF9A9A)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      );
    }
    if (lower.contains('limon') || lower.contains('limón')) {
      return const LinearGradient(
        colors: [Color(0xFFFFF59D), Color(0xFFE6EE9C)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      );
    }
    return const LinearGradient(
      colors: [Color(0xFFE8F5E9), Color(0xFFC8E6C9)],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    );
  }

  @override
  Widget build(BuildContext context) {
    final emoji = _getEmojiForProduct(product.name);
    final gradient = _getGradientForProduct(product.name);
    final hasBadge = product.badge != null && product.badge!.isNotEmpty;
    final price = product.priceFrom > 0
        ? 'Desde \$${product.priceFrom.toStringAsFixed(0)}'
        : (product.prices.isNotEmpty
            ? 'Desde \$${product.prices.values.first.toStringAsFixed(0)}'
            : '\$7.000');

    final hasNetworkImage = product.image != null &&
        product.image!.trim().isNotEmpty &&
        (product.image!.startsWith('http://') || product.image!.startsWith('https://'));

    return Container(
      width: 220,
      margin: const EdgeInsets.only(right: 12, top: 4, bottom: 6),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AppTheme.primaryLemon.withOpacity(0.35), width: 1.2),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.06),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            // Image / Visual Area
            Stack(
              children: [
                Container(
                  height: 95,
                  width: double.infinity,
                  decoration: BoxDecoration(
                    gradient: gradient,
                  ),
                  child: hasNetworkImage
                      ? Image.network(
                          product.image!,
                          fit: BoxFit.cover,
                          loadingBuilder: (context, child, loadingProgress) {
                            if (loadingProgress == null) return child;
                            return Center(
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: AppTheme.darkGreen.withOpacity(0.6),
                              ),
                            );
                          },
                          errorBuilder: (context, error, stackTrace) {
                            return Center(
                              child: Text(emoji, style: const TextStyle(fontSize: 42)),
                            );
                          },
                        )
                      : Center(
                          child: Text(emoji, style: const TextStyle(fontSize: 42)),
                        ),
                ),
                if (hasBadge)
                  Positioned(
                    top: 6,
                    left: 6,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                      decoration: BoxDecoration(
                        color: AppTheme.darkBg.withOpacity(0.85),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text(
                        product.badge!,
                        style: const TextStyle(
                          color: AppTheme.primaryLemon,
                          fontSize: 9,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
              ],
            ),

            // Product Details
            Padding(
              padding: const EdgeInsets.fromLTRB(10, 8, 10, 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    product.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w800,
                      color: AppTheme.textDark,
                    ),
                  ),
                  if (product.description.isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      product.description,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppTheme.textGray,
                        height: 1.15,
                      ),
                    ),
                  ],
                  const SizedBox(height: 6),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Flexible(
                        child: Text(
                          price,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w800,
                            color: AppTheme.darkGreen,
                          ),
                        ),
                      ),
                      const SizedBox(width: 4),
                      InkWell(
                        onTap: () {
                          if (onSelect != null) {
                            onSelect!(product);
                          }
                        },
                        borderRadius: BorderRadius.circular(10),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: AppTheme.primaryLemon,
                            borderRadius: BorderRadius.circular(10),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.06),
                                blurRadius: 4,
                                offset: const Offset(0, 2),
                              ),
                            ],
                          ),
                          child: const Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                'Pedir',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w800,
                                  color: AppTheme.darkBg,
                                ),
                              ),
                              SizedBox(width: 2),
                              Icon(Icons.arrow_forward_ios_rounded, size: 9, color: AppTheme.darkBg),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
