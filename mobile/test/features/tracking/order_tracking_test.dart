import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lemondrop_mobile/core/models/models.dart';
import 'package:lemondrop_mobile/core/theme/app_theme.dart';
import 'package:lemondrop_mobile/features/tracking/presentation/order_tracking_detail_screen.dart';
import 'package:lemondrop_mobile/features/tracking/presentation/widgets/interactive_order_card.dart';
import 'package:lemondrop_mobile/features/tracking/presentation/widgets/order_tracking_timeline.dart';

void main() {
  group('Order & Tracking Model Tests', () {
    test('Order & OrderItem parse correctly with new and legacy JSON', () {
      final json = {
        'id': 'ord-123',
        'orderCode': 'LD-2026-00031',
        'customerName': 'Juan',
        'customerPhone': '3001234567',
        'status': 'PREPARING',
        'subtotal': 13000,
        'total': 13000,
        'items': [
          {
            'productName': 'Granizado de Limón',
            'flavorName': 'Limón',
            'size': 'SMALL',
            'quantity': 1,
            'unitPrice': 6000,
            'subtotal': 7000,
            'addons': [
              {'addonId': 'add-1', 'addonName': 'Leche condensada', 'unitPrice': 1000, 'quantity': 1}
            ]
          },
          {
            'productName': 'Granizado de Fresa',
            'flavorName': 'Fresa',
            'size': 'MEDIUM',
            'quantity': 1,
            'subtotal': 6000,
          }
        ],
        'createdAt': '2026-08-26T10:30:00'
      };

      final order = Order.fromJson(json);

      expect(order.orderCode, 'LD-2026-00031');
      expect(order.customerName, 'Juan');
      expect(order.status, OrderStatus.preparing);
      expect(order.items.length, 2);
      expect(order.items[0].productName, 'Granizado de Limón');
      expect(order.items[0].addons.length, 1);
      expect(order.items[0].addons[0].addonName, 'Leche condensada');
      expect(order.items[1].addons.length, 0); // safe fallback for legacy payload
      expect(order.items[1].unitPrice, 6000); // fallback calculation (subtotal / qty)
      expect(order.total, 13000);
      expect(order.status.trackingStepIndex, 3);
      expect(order.status.trackingTitle, 'Estamos preparando tu pedido');
    });

    test('All OrderStatus step mappings and messages are deterministic', () {
      expect(OrderStatus.received.trackingStepIndex, 1);
      expect(OrderStatus.accepted.trackingStepIndex, 2);
      expect(OrderStatus.preparing.trackingStepIndex, 3);
      expect(OrderStatus.almostReady.trackingStepIndex, 4);
      expect(OrderStatus.ready.trackingStepIndex, 5);
      expect(OrderStatus.delivered.trackingStepIndex, 6);
      expect(OrderStatus.cancelled.trackingStepIndex, -1);

      expect(OrderStatus.received.trackingTitle, 'Pedido recibido');
      expect(OrderStatus.accepted.trackingTitle, 'Tu pedido fue aceptado');
      expect(OrderStatus.preparing.trackingTitle, 'Estamos preparando tu pedido');
      expect(OrderStatus.almostReady.trackingTitle, 'Tu pedido está casi listo');
      expect(OrderStatus.ready.trackingTitle, 'Tu pedido está listo para recoger');
      expect(OrderStatus.delivered.trackingTitle, 'Pedido entregado');
      expect(OrderStatus.cancelled.trackingTitle, 'Pedido cancelado');
    });
  });

  group('InteractiveOrderCard Widget Tests', () {
    testWidgets('Tapping anywhere on InteractiveOrderCard triggers onTap callback', (tester) async {
      bool tapped = false;

      final testOrder = Order(
        id: '1',
        orderCode: 'LD-2026-00099',
        customerName: 'Maria',
        customerPhone: '3009876543',
        items: [
          OrderItem(
            productId: 'p1',
            productName: 'Granizado de Maracuyá',
            flavorId: 'f1',
            flavorName: 'Maracuyá',
            size: ProductSize.medium,
            quantity: 1,
            addons: [
              OrderItemAddon(addonId: 'a1', addonName: 'Lecherita', unitPrice: 1000, quantity: 1),
            ],
            subtotal: 8000,
            observations: '',
          )
        ],
        subtotal: 8000,
        total: 8000,
        status: OrderStatus.accepted,
        observations: '',
        advisorNotes: '',
        createdAt: DateTime.now(),
        updatedAt: DateTime.now(),
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: Scaffold(
            body: InteractiveOrderCard(
              order: testOrder,
              onTap: () => tapped = true,
            ),
          ),
        ),
      );

      expect(find.text('LD-2026-00099'), findsOneWidget);
      expect(find.text('¡Hola, Maria! 👋'), findsOneWidget);
      expect(find.text('Ver seguimiento'), findsOneWidget);
      expect(find.text('\$8.000'), findsWidgets);

      // Tap on empty space / title
      await tester.tap(find.text('¡Hola, Maria! 👋'));
      await tester.pumpAndSettle();

      expect(tapped, isTrue);
    });
  });

  group('OrderTrackingTimeline Widget Tests', () {
    testWidgets('Renders all 6 steps with preparing active', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: const Scaffold(
            body: SingleChildScrollView(
              child: OrderTrackingTimeline(
                status: OrderStatus.preparing,
              ),
            ),
          ),
        ),
      );

      expect(find.text('Seguimiento en Tiempo Real'), findsOneWidget);
      expect(find.text('Recibido'), findsOneWidget);
      expect(find.text('Aceptado'), findsOneWidget);
      expect(find.text('Preparando'), findsOneWidget);
      expect(find.text('Casi listo'), findsOneWidget);
      expect(find.text('Listo'), findsOneWidget);
      expect(find.text('Entregado'), findsOneWidget);
      expect(find.text('EN PROCESO'), findsOneWidget);
      expect(find.text('Paso 3 de 6'), findsOneWidget);
    });

    testWidgets('Renders cancellation state when order is CANCELLED', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: const Scaffold(
            body: SingleChildScrollView(
              child: OrderTrackingTimeline(
                status: OrderStatus.cancelled,
                cancellationReason: 'Sin stock de fruta',
              ),
            ),
          ),
        ),
      );

      expect(find.text('Pedido Cancelado'), findsOneWidget);
      expect(find.text('Motivo: Sin stock de fruta'), findsOneWidget);
      expect(find.text('Seguimiento en Tiempo Real'), findsNothing);
    });
  });

  group('OrderTrackingDetailScreen Widget Tests', () {
    testWidgets('Renders full detail, products, toppings, total and WhatsApp button', (tester) async {
      final testOrder = Order(
        id: 'ord-300',
        orderCode: 'LD-2026-00045',
        customerName: 'Carlos',
        customerPhone: '3101112233',
        items: [
          OrderItem(
            productId: 'p1',
            productName: 'Granizado de Limón',
            flavorId: 'f1',
            flavorName: 'Limón',
            size: ProductSize.small,
            quantity: 2,
            unitPrice: 5000,
            addons: [
              OrderItemAddon(addonId: 'a1', addonName: 'Leche condensada', unitPrice: 1000, quantity: 1),
              OrderItemAddon(addonId: 'a2', addonName: 'Arequipe', unitPrice: 1000, quantity: 1),
            ],
            subtotal: 12000,
            observations: 'Bien frío por favor',
          )
        ],
        subtotal: 12000,
        total: 12000,
        status: OrderStatus.ready,
        observations: '',
        advisorNotes: '',
        createdAt: DateTime.now(),
        updatedAt: DateTime.now(),
      );

      await tester.pumpWidget(
        ProviderScope(
          child: MaterialApp(
            theme: AppTheme.lightTheme,
            home: OrderTrackingDetailScreen(initialOrder: testOrder),
          ),
        ),
      );

      expect(find.text('Detalle del pedido'), findsOneWidget);
      expect(find.text('LD-2026-00045'), findsOneWidget);
      expect(find.text('Carlos'), findsOneWidget);
      expect(find.text('¡Tu pedido está listo! 🎉\nYa puedes acercarte a recogerlo.'), findsOneWidget);
      expect(find.text('Granizado de Limón (Limón)'), findsOneWidget);
      expect(find.text('Tamaño: Pequeño'), findsOneWidget);
      expect(find.text('Cantidad: 2'), findsOneWidget);
      expect(find.text('Leche condensada, Arequipe'), findsOneWidget);
      expect(find.text('Nota: Bien frío por favor'), findsOneWidget);
      expect(find.text('\$12.000'), findsWidgets);
      expect(find.text('¿Tienes dudas sobre tu pedido? Escríbenos por WhatsApp'), findsOneWidget);
    });
  });
}
