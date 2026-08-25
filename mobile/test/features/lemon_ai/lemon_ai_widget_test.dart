import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lemondrop_mobile/features/lemon_ai/data/models/ai_models.dart';
import 'package:lemondrop_mobile/features/lemon_ai/presentation/widgets/lemon_ai_bubble.dart';
import 'package:lemondrop_mobile/features/lemon_ai/presentation/widgets/lemon_ai_fab.dart';
import 'package:lemondrop_mobile/features/lemon_ai/presentation/widgets/lemon_ai_order_card.dart';
import 'package:lemondrop_mobile/features/lemon_ai/presentation/widgets/lemon_ai_product_card.dart';
import 'package:lemondrop_mobile/features/lemon_ai/presentation/widgets/lemon_ai_product_carousel.dart';
import 'package:lemondrop_mobile/features/lemon_ai/presentation/widgets/lemon_ai_success_card.dart';

void main() {
  group('Lemon AI Widget Tests', () {
    testWidgets('LemonAiOrderCard renders cart items, size, toppings and total price', (WidgetTester tester) async {
      const cart = AICartDto(
        cartId: 'cart-1',
        items: [
          AICartItemDto(
            productName: 'Granizado de Mango',
            flavorName: 'Mango',
            size: 'LARGE',
            quantity: 1,
            addonNames: ['Oreo', 'Gomitas'],
            unitPrice: 9000,
            addonTotal: 2000,
            subtotal: 11000,
          )
        ],
        subtotal: 11000,
        total: 11000,
        status: 'DRAFT',
        totalItems: 1,
      );

      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(
            home: Scaffold(
              body: LemonAiOrderCard(cart: cart),
            ),
          ),
        ),
      );

      expect(find.text('Resumen de tu Pedido'), findsOneWidget);
      expect(find.text('1x Granizado de Mango (Mango)'), findsOneWidget);
      expect(find.text('Tamaño: LARGE'), findsOneWidget);
      expect(find.text('+ Oreo, Gomitas'), findsOneWidget);
      expect(find.text('Confirmar Pedido'), findsOneWidget);
      expect(find.text('Modificar'), findsOneWidget);
    });

    testWidgets('LemonAiProductCard renders product information and triggers selection', (WidgetTester tester) async {
      AIProductCardDto? selected;
      const product = AIProductCardDto(
        id: 'prod-mango',
        name: 'Granizado de Mango',
        description: 'Mango fresco con toque cítrico',
        badge: 'Más vendido',
        priceFrom: 7000,
        prices: {'MEDIUM': 7000},
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: LemonAiProductCard(
              product: product,
              onSelect: (p) => selected = p,
            ),
          ),
        ),
      );

      expect(find.text('Granizado de Mango'), findsOneWidget);
      expect(find.text('Mango fresco con toque cítrico'), findsOneWidget);
      expect(find.text('Más vendido'), findsOneWidget);
      expect(find.text('Desde \$7000'), findsOneWidget);
      expect(find.text('Pedir'), findsOneWidget);

      await tester.tap(find.text('Pedir'));
      expect(selected, isNotNull);
      expect(selected?.id, 'prod-mango');
    });

    testWidgets('LemonAiProductCarousel renders multiple cards in horizontal scroll', (WidgetTester tester) async {
      const products = [
        AIProductCardDto(
          id: 'p1',
          name: 'Granizado de Mango',
          description: 'Mango dulce',
          priceFrom: 7000,
        ),
        AIProductCardDto(
          id: 'p2',
          name: 'Granizado de Fresa',
          description: 'Fresa natural',
          priceFrom: 7000,
        ),
      ];

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: LemonAiProductCarousel(products: products),
          ),
        ),
      );

      expect(find.text('Granizado de Mango'), findsOneWidget);
      expect(find.text('Granizado de Fresa'), findsOneWidget);
      expect(find.byType(ListView), findsOneWidget);
    });

    testWidgets('LemonAiBubble renders user message with clean inline mic icon when isVoice is true', (WidgetTester tester) async {
      final msg = AIMessage(
        id: '1',
        role: 'user',
        content: 'Quiero un granizado de mango',
        timestamp: DateTime.now(),
        isVoice: true,
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: LemonAiBubble(message: msg),
          ),
        ),
      );

      expect(find.text('Quiero un granizado de mango'), findsOneWidget);
      expect(find.byIcon(Icons.mic), findsOneWidget);
      // Ensure no obsolete separate "Audio transcrito" banner appears
      expect(find.text('Audio transcrito'), findsNothing);
    });

    testWidgets('LemonAiSuccessCard displays Pedido recibido and does NOT claim listo para recoger', (WidgetTester tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: LemonAiSuccessCard(
              orderCode: 'LD-2026-00042',
              whatsAppUrl: 'https://wa.me/573001234567?text=Recibimos+tu+pedido',
            ),
          ),
        ),
      );

      expect(find.text('PEDIDO RECIBIDO'), findsOneWidget);
      expect(find.text('LD-2026-00042'), findsOneWidget);
      expect(find.text('🟢 Estado: Pedido recibido'), findsOneWidget);
      expect(find.text('Abrir WhatsApp'), findsOneWidget);

      // Verify it strictly avoids premature ready claims
      expect(find.text('Listo para recoger'), findsNothing);
      expect(find.text('Ya está listo'), findsNothing);
    });

    testWidgets('LemonAiFab renders badge and label', (WidgetTester tester) async {
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(
            home: Scaffold(
              floatingActionButton: LemonAiFab(),
            ),
          ),
        ),
      );

      expect(find.text('Lemon AI'), findsOneWidget);
      expect(find.text('🍋'), findsOneWidget);
    });
  });
}
