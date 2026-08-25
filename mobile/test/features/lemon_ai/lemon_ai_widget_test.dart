import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lemondrop_mobile/features/lemon_ai/data/models/ai_models.dart';
import 'package:lemondrop_mobile/features/lemon_ai/presentation/widgets/lemon_ai_fab.dart';
import 'package:lemondrop_mobile/features/lemon_ai/presentation/widgets/lemon_ai_order_card.dart';
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
