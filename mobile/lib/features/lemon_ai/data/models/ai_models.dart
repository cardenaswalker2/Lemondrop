/// Estado de la sesión de conversación según directivas arquitectónicas
enum ConversationSessionStatus {
  noConversation,
  active,
  expired,
  resetting,
}

/// Estado de la UI e interacciones de Lemon AI
enum LemonAiUiState {
  idle,
  thinking,
  listening,
  transcribing,
  waitingConfirmation,
  orderConfirmed,
  error,
}

/// DTO de ítem de carrito estructurado devuelto por el backend
class AICartItemDto {
  final String? id;
  final String? productId;
  final String? productName;
  final String? flavorId;
  final String? flavorName;
  final String size;
  final int quantity;
  final List<String> addonNames;
  final num unitPrice;
  final num addonTotal;
  final num subtotal;
  final String? observations;

  const AICartItemDto({
    this.id,
    this.productId,
    this.productName,
    this.flavorId,
    this.flavorName,
    required this.size,
    required this.quantity,
    required this.addonNames,
    required this.unitPrice,
    required this.addonTotal,
    required this.subtotal,
    this.observations,
  });

  factory AICartItemDto.fromJson(Map<String, dynamic> json) {
    return AICartItemDto(
      id: json['id'] as String?,
      productId: json['productId'] as String?,
      productName: json['productName'] as String?,
      flavorId: json['flavorId'] as String?,
      flavorName: json['flavorName'] as String?,
      size: (json['size'] as String?) ?? 'MEDIUM',
      quantity: (json['quantity'] as num?)?.toInt() ?? 1,
      addonNames: (json['addonNames'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      unitPrice: (json['unitPrice'] as num?) ?? 0,
      addonTotal: (json['addonTotal'] as num?) ?? 0,
      subtotal: (json['subtotal'] as num?) ?? 0,
      observations: json['observations'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'productId': productId,
        'productName': productName,
        'flavorId': flavorId,
        'flavorName': flavorName,
        'size': size,
        'quantity': quantity,
        'addonNames': addonNames,
        'unitPrice': unitPrice,
        'addonTotal': addonTotal,
        'subtotal': subtotal,
        'observations': observations,
      };
}

/// DTO del carrito completo sincronizado con el backend
class AICartDto {
  final String? cartId;
  final List<AICartItemDto> items;
  final num subtotal;
  final num total;
  final String status;
  final int totalItems;

  const AICartDto({
    this.cartId,
    required this.items,
    required this.subtotal,
    required this.total,
    required this.status,
    required this.totalItems,
  });

  factory AICartDto.fromJson(Map<String, dynamic> json) {
    return AICartDto(
      cartId: json['cartId'] as String?,
      items: (json['items'] as List<dynamic>?)
              ?.map((e) => AICartItemDto.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      subtotal: (json['subtotal'] as num?) ?? 0,
      total: (json['total'] as num?) ?? 0,
      status: (json['status'] as String?) ?? 'DRAFT',
      totalItems: (json['totalItems'] as num?)?.toInt() ?? 0,
    );
  }

  Map<String, dynamic> toJson() => {
        'cartId': cartId,
        'items': items.map((e) => e.toJson()).toList(),
        'subtotal': subtotal,
        'total': total,
        'status': status,
        'totalItems': totalItems,
      };
}

/// Mensaje en el historial de chat de Flutter
class AIMessage {
  final String id;
  final String role; // "user", "assistant", "system"
  final String content;
  final DateTime timestamp;
  final bool isVoice;
  final AICartDto? cartSnapshot;
  final String? orderCode;
  final String? whatsAppUrl;
  final bool requiresConfirmation;
  final bool isOrderConfirmed;

  AIMessage({
    required this.id,
    required this.role,
    required this.content,
    required this.timestamp,
    this.isVoice = false,
    this.cartSnapshot,
    this.orderCode,
    this.whatsAppUrl,
    this.requiresConfirmation = false,
    this.isOrderConfirmed = false,
  });

  bool get isUser => role == 'user';
  bool get isAssistant => role == 'assistant';

  factory AIMessage.fromJson(Map<String, dynamic> json) {
    return AIMessage(
      id: json['id'] as String? ?? DateTime.now().millisecondsSinceEpoch.toString(),
      role: json['role'] as String? ?? 'assistant',
      content: json['content'] as String? ?? '',
      timestamp: json['timestamp'] != null
          ? DateTime.tryParse(json['timestamp'] as String) ?? DateTime.now()
          : DateTime.now(),
      isVoice: json['isVoice'] as bool? ?? false,
      cartSnapshot: json['cartSnapshot'] != null
          ? AICartDto.fromJson(json['cartSnapshot'] as Map<String, dynamic>)
          : null,
      orderCode: json['orderCode'] as String?,
      whatsAppUrl: json['whatsAppUrl'] as String?,
      requiresConfirmation: json['requiresConfirmation'] as bool? ?? false,
      isOrderConfirmed: json['isOrderConfirmed'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'role': role,
        'content': content,
        'timestamp': timestamp.toIso8601String(),
        'isVoice': isVoice,
        'cartSnapshot': cartSnapshot?.toJson(),
        'orderCode': orderCode,
        'whatsAppUrl': whatsAppUrl,
        'requiresConfirmation': requiresConfirmation,
        'isOrderConfirmed': isOrderConfirmed,
      };
}

/// Petición enviada al endpoint POST /api/ai/chat
class AIChatRequest {
  final String? conversationId;
  final String? clientToken;
  final String message;
  final String? customerName;
  final String? customerPhone;
  final String? action;

  const AIChatRequest({
    this.conversationId,
    this.clientToken,
    required this.message,
    this.customerName,
    this.customerPhone,
    this.action,
  });

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{
      'message': message,
    };
    if (conversationId != null) map['conversationId'] = conversationId;
    if (clientToken != null) map['clientToken'] = clientToken;
    if (customerName != null && customerName!.isNotEmpty) map['customerName'] = customerName;
    if (customerPhone != null && customerPhone!.isNotEmpty) map['customerPhone'] = customerPhone;
    if (action != null && action!.isNotEmpty) map['action'] = action;
    return map;
  }
}

/// Respuesta estructurada recibida de POST /api/ai/chat o POST /api/ai/voice
class AIChatResponse {
  final String? conversationId;
  final String? clientToken;
  final String? message;
  final String? state;
  final String? intent;
  final bool cartUpdated;
  final bool requiresConfirmation;
  final bool orderReadyForConfirmation;
  final bool orderConfirmed;
  final String? orderCode;
  final String? whatsAppUrl;
  final AICartDto? cart;
  final List<String> suggestions;
  final int executionTimeMs;
  final bool success;
  final String? error;

  const AIChatResponse({
    this.conversationId,
    this.clientToken,
    this.message,
    this.state,
    this.intent,
    this.cartUpdated = false,
    this.requiresConfirmation = false,
    this.orderReadyForConfirmation = false,
    this.orderConfirmed = false,
    this.orderCode,
    this.whatsAppUrl,
    this.cart,
    this.suggestions = const [],
    this.executionTimeMs = 0,
    this.success = true,
    this.error,
  });

  factory AIChatResponse.fromJson(Map<String, dynamic> json) {
    return AIChatResponse(
      conversationId: json['conversationId'] as String?,
      clientToken: json['clientToken'] as String?,
      message: json['message'] as String?,
      state: json['state'] as String?,
      intent: json['intent'] as String?,
      cartUpdated: json['cartUpdated'] as bool? ?? false,
      requiresConfirmation: json['requiresConfirmation'] as bool? ?? false,
      orderReadyForConfirmation: json['orderReadyForConfirmation'] as bool? ?? false,
      orderConfirmed: json['orderConfirmed'] as bool? ?? false,
      orderCode: json['orderCode'] as String?,
      whatsAppUrl: json['whatsAppUrl'] as String?,
      cart: json['cart'] != null ? AICartDto.fromJson(json['cart'] as Map<String, dynamic>) : null,
      suggestions: (json['suggestions'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      executionTimeMs: (json['executionTimeMs'] as num?)?.toInt() ?? 0,
      success: json['success'] as bool? ?? true,
      error: json['error'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
        'conversationId': conversationId,
        'clientToken': clientToken,
        'message': message,
        'state': state,
        'intent': intent,
        'cartUpdated': cartUpdated,
        'requiresConfirmation': requiresConfirmation,
        'orderReadyForConfirmation': orderReadyForConfirmation,
        'orderConfirmed': orderConfirmed,
        'orderCode': orderCode,
        'whatsAppUrl': whatsAppUrl,
        'cart': cart?.toJson(),
        'suggestions': suggestions,
        'executionTimeMs': executionTimeMs,
        'success': success,
        'error': error,
      };
}
