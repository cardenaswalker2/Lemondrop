import 'package:flutter_test/flutter_test.dart';
import 'package:lemondrop_mobile/features/lemon_ai/data/models/ai_models.dart';

void main() {
  group('Lemon AI Models Test', () {
    test('AIChatRequest serializes properly to JSON', () {
      const req = AIChatRequest(
        conversationId: 'conv-123',
        clientToken: 'token-abc',
        message: 'Quiero un granizado de mango grande con oreo',
        customerName: 'Mateo',
        customerPhone: '3001234567',
        action: 'CONFIRM_ORDER',
      );

      final json = req.toJson();
      expect(json['conversationId'], 'conv-123');
      expect(json['clientToken'], 'token-abc');
      expect(json['message'], 'Quiero un granizado de mango grande con oreo');
      expect(json['customerName'], 'Mateo');
      expect(json['customerPhone'], '3001234567');
      expect(json['action'], 'CONFIRM_ORDER');
    });

    test('AIChatResponse deserializes full structured payload with cart and confirmation', () {
      final json = {
        'conversationId': 'conv-456',
        'clientToken': 'token-xyz',
        'message': 'Acabo de armar tu pedido. ¿Confirmas?',
        'state': 'WAITING_CONFIRMATION',
        'intent': 'ORDER_CREATION',
        'cartUpdated': true,
        'requiresConfirmation': true,
        'orderReadyForConfirmation': true,
        'orderConfirmed': false,
        'orderCode': null,
        'whatsAppUrl': null,
        'cart': {
          'cartId': 'cart-789',
          'items': [
            {
              'id': 'item-1',
              'productId': 'p-mango',
              'productName': 'Granizado de Mango',
              'flavorId': 'f-mango',
              'flavorName': 'Mango',
              'size': 'LARGE',
              'quantity': 1,
              'addonNames': ['Oreo', 'Gomitas'],
              'unitPrice': 9000,
              'addonTotal': 2000,
              'subtotal': 11000,
              'observations': 'Bien frío'
            }
          ],
          'subtotal': 11000,
          'total': 11000,
          'status': 'DRAFT',
          'totalItems': 1
        },
        'suggestions': ['Confirmar', 'Cambiar tamaño'],
        'executionTimeMs': 120,
        'success': true,
      };

      final response = AIChatResponse.fromJson(json);

      expect(response.conversationId, 'conv-456');
      expect(response.clientToken, 'token-xyz');
      expect(response.requiresConfirmation, isTrue);
      expect(response.orderConfirmed, isFalse);
      expect(response.cart, isNotNull);
      expect(response.cart!.total, 11000);
      expect(response.cart!.items.length, 1);
      expect(response.cart!.items.first.productName, 'Granizado de Mango');
      expect(response.cart!.items.first.size, 'LARGE');
      expect(response.cart!.items.first.addonNames, ['Oreo', 'Gomitas']);
      expect(response.suggestions, ['Confirmar', 'Cambiar tamaño']);
    });

    test('AIChatResponse deserializes products list and visual metadata', () {
      final json = {
        'conversationId': 'conv-456',
        'message': 'Aquí tienes los granizados más populares:',
        'products': [
          {
            'id': 'prod-mango',
            'name': 'Granizado de Mango',
            'description': 'Mango tropical con sal y limón opcional',
            'image': 'https://example.com/mango.jpg',
            'category': 'Granizados',
            'badge': 'Más vendido',
            'priceFrom': 7000,
            'prices': {'SMALL': 5000, 'MEDIUM': 7000, 'LARGE': 9000},
            'available': true,
          }
        ],
        'success': true,
      };

      final response = AIChatResponse.fromJson(json);

      expect(response.products.length, 1);
      final prod = response.products.first;
      expect(prod.id, 'prod-mango');
      expect(prod.name, 'Granizado de Mango');
      expect(prod.badge, 'Más vendido');
      expect(prod.priceFrom, 7000);
      expect(prod.prices['MEDIUM'], 7000);
      expect(prod.available, isTrue);
    });

    test('AIVoiceResponse deserializes transcription and embedded chatResponse', () {
      final json = {
        'transcription': 'Quiero un granizado de maracuyá',
        'chatResponse': {
          'conversationId': 'conv-voice-1',
          'message': '¡Listo! Te agregué Granizado de Maracuyá.',
          'success': true,
        },
        'sttDurationMs': 450,
        'success': true,
      };

      final voiceResp = AIVoiceResponse.fromJson(json);

      expect(voiceResp.transcription, 'Quiero un granizado de maracuyá');
      expect(voiceResp.sttDurationMs, 450);
      expect(voiceResp.success, isTrue);
      expect(voiceResp.chatResponse, isNotNull);
      expect(voiceResp.chatResponse!.message, contains('Maracuyá'));
    });

    test('AIChatResponse deserializes confirmed order payload', () {
      final json = {
        'conversationId': 'conv-456',
        'clientToken': 'token-xyz',
        'message': '¡Tu pedido fue recibido!',
        'state': 'ORDER_CONFIRMED',
        'intent': 'ORDER_CONFIRMATION',
        'cartUpdated': false,
        'requiresConfirmation': false,
        'orderReadyForConfirmation': false,
        'orderConfirmed': true,
        'orderCode': 'LD-2026-00042',
        'whatsAppUrl': 'https://wa.me/573001234567?text=Recibimos+tu+pedido',
        'cart': {
          'cartId': 'cart-789',
          'items': [],
          'subtotal': 11000,
          'total': 11000,
          'status': 'CONFIRMED',
          'totalItems': 1
        },
        'suggestions': [],
        'executionTimeMs': 150,
        'success': true,
      };

      final response = AIChatResponse.fromJson(json);

      expect(response.orderConfirmed, isTrue);
      expect(response.orderCode, 'LD-2026-00042');
      expect(response.whatsAppUrl, contains('https://wa.me/573001234567'));
    });
  });
}
