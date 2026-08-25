import 'package:flutter/material.dart';
import '../../data/models/ai_models.dart';
import 'lemon_ai_product_card.dart';

class LemonAiProductCarousel extends StatelessWidget {
  final List<AIProductCardDto> products;
  final ValueChanged<AIProductCardDto>? onProductSelected;

  const LemonAiProductCarousel({
    super.key,
    required this.products,
    this.onProductSelected,
  });

  @override
  Widget build(BuildContext context) {
    if (products.isEmpty) return const SizedBox.shrink();

    // Display maximum 5 products in horizontal carousel
    final displayProducts = products.take(5).toList();

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 8),
      height: 205,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        physics: const BouncingScrollPhysics(),
        itemCount: displayProducts.length,
        itemBuilder: (context, index) {
          final product = displayProducts[index];
          return LemonAiProductCard(
            product: product,
            onSelect: onProductSelected,
          );
        },
      ),
    );
  }
}
