import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lemondrop_mobile/core/models/models.dart';
import 'package:lemondrop_mobile/features/advisor/providers/orders_provider.dart';
import 'package:lemondrop_mobile/features/advisor/providers/checklist_service.dart';
import 'package:lemondrop_mobile/features/advisor/presentation/widgets/advisor_badges.dart';
import 'package:lemondrop_mobile/features/advisor/presentation/widgets/order_timer_badge.dart';
import 'package:lemondrop_mobile/features/advisor/presentation/order_detail_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Advisor Model & Utilities Tests', () {
    test('Order model parses priority, assignedAdvisor, timestamps and duration', () {
      final json = {
        'id': 'ord-test-01',
        'orderCode': 'ORD-9999',
        'customerName': 'Juan Perez',
        'customerPhone': '3001234567',
        'items': [
          {
            'productId': 'prod-1',
            'productName': 'Granizado de Limón',
            'flavorId': 'flav-1',
            'flavorName': 'Maracuyá',
            'size': 'MEDIUM',
            'quantity': 2,
            'unitPrice': 6000.0,
            'subtotal': 12000.0,
            'addons': [],
            'observations': 'Sin pitillo',
          }
        ],
        'subtotal': 12000.0,
        'total': 12000.0,
        'status': 'PREPARING',
        'observations': 'Nota general',
        'advisorNotes': 'Nota asesor',
        'priority': 'ALTA',
        'assignedAdvisor': 'Carlos Asesor',
        'createdAt': '2026-08-26T10:00:00',
        'updatedAt': '2026-08-26T10:05:00',
        'receivedAt': '2026-08-26T10:00:00',
        'acceptedAt': '2026-08-26T10:02:00',
        'preparingAt': '2026-08-26T10:05:00',
      };

      final order = Order.fromJson(json);

      expect(order.id, 'ord-test-01');
      expect(order.orderCode, 'ORD-9999');
      expect(order.isUrgent, isTrue);
      expect(order.isAssigned, isTrue);
      expect(order.assignedAdvisor, 'Carlos Asesor');
      expect(order.status, OrderStatus.preparing);
      expect(order.items.length, 1);
      expect(order.items.first.observations, 'Sin pitillo');
    });

    test('ChecklistService loads default checklist and persists changes', () async {
      SharedPreferences.setMockInitialValues({});

      final initialChecklist = await ChecklistService.getChecklist('ord-check-01');
      expect(initialChecklist.length, 5);
      expect(initialChecklist.values.every((v) => v == false), isTrue);

      initialChecklist['Sabor seleccionado'] = true;
      initialChecklist['Hielo molido / granizado'] = true;
      await ChecklistService.saveChecklist('ord-check-01', initialChecklist);

      final loadedChecklist = await ChecklistService.getChecklist('ord-check-01');
      expect(loadedChecklist['Sabor seleccionado'], isTrue);
      expect(loadedChecklist['Hielo molido / granizado'], isTrue);
      expect(loadedChecklist['Tamaño correcto'], isFalse);
    });
  });

  group('Advisor Widgets Tests', () {
    testWidgets('PriorityBadge renders when isUrgent is true and hides when false', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: Column(
              children: [
                PriorityBadge(isUrgent: true),
                PriorityBadge(isUrgent: false),
              ],
            ),
          ),
        ),
      );

      expect(find.text('ALTA'), findsOneWidget);
    });

    testWidgets('AdvisorBadge displays correct assignment label', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: Column(
              children: [
                AdvisorBadge(assignedAdvisor: null),
                AdvisorBadge(assignedAdvisor: 'carlos', currentUsername: 'carlos'),
                AdvisorBadge(assignedAdvisor: 'maria', currentUsername: 'carlos'),
              ],
            ),
          ),
        ),
      );

      expect(find.text('Sin asignar'), findsOneWidget);
      expect(find.text('Por ti'), findsOneWidget);
      expect(find.text('maria'), findsOneWidget);
    });

    testWidgets('OrderDetailScreen renders complete order in read-only / history mode', (tester) async {
      final testOrder = Order(
        id: 'ord-hist-99',
        orderCode: 'ORD-HIST-99',
        customerName: 'Cliente Histórico',
        customerPhone: '3119998877',
        items: [
          OrderItem(
            productId: 'p1',
            productName: 'Limonada de Coco',
            flavorId: 'f1',
            flavorName: 'Coco',
            size: ProductSize.large,
            quantity: 1,
            unitPrice: 8000,
            subtotal: 8000,
            addons: [],
            observations: 'Bien fría',
          ),
        ],
        subtotal: 8000,
        total: 8000,
        status: OrderStatus.delivered,
        observations: '',
        advisorNotes: '',
        priority: 'NORMAL',
        assignedAdvisor: 'carlos',
        createdAt: DateTime.now().subtract(const Duration(minutes: 20)),
        updatedAt: DateTime.now().subtract(const Duration(minutes: 5)),
        deliveredAt: DateTime.now().subtract(const Duration(minutes: 5)),
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            liveSecondsProvider.overrideWith((ref) => Stream.value(DateTime.now().millisecondsSinceEpoch)),
          ],
          child: MaterialApp(
            home: OrderDetailScreen(
              orderId: testOrder.id,
              preloadedOrder: testOrder,
            ),
          ),
        ),
      );

      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('Comanda ORD-HIST-99'), findsOneWidget);
      expect(find.text('Cliente Histórico'), findsOneWidget);
      expect(find.text('1x Limonada de Coco'), findsOneWidget);
      expect(find.text('\$8000'), findsWidgets);
      expect(find.textContaining('Pedido Entregado con Éxito'), findsOneWidget);
    });
  });
}
