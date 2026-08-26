enum OrderStatus {
  received,
  accepted,
  preparing,
  almostReady,
  ready,
  delivered,
  cancelled;

  String get nameInSpanish {
    switch (this) {
      case OrderStatus.received:
        return 'Recibido';
      case OrderStatus.accepted:
        return 'Aceptado';
      case OrderStatus.preparing:
        return 'En Preparación';
      case OrderStatus.almostReady:
        return 'Casi Listo';
      case OrderStatus.ready:
        return 'Listo para Recoger';
      case OrderStatus.delivered:
        return 'Entregado';
      case OrderStatus.cancelled:
        return 'Cancelado';
    }
  }

  String get trackingTitle {
    switch (this) {
      case OrderStatus.received:
        return 'Pedido recibido';
      case OrderStatus.accepted:
        return 'Tu pedido fue aceptado';
      case OrderStatus.preparing:
        return 'Estamos preparando tu pedido';
      case OrderStatus.almostReady:
        return 'Tu pedido está casi listo';
      case OrderStatus.ready:
        return 'Tu pedido está listo para recoger';
      case OrderStatus.delivered:
        return 'Pedido entregado';
      case OrderStatus.cancelled:
        return 'Pedido cancelado';
    }
  }

  String get trackingMessage {
    switch (this) {
      case OrderStatus.received:
        return '¡Recibimos tu pedido! 👋\nNuestro equipo ya lo tiene en la lista de preparación.';
      case OrderStatus.accepted:
        return '¡Tu pedido fue aceptado! 💚\nMuy pronto comenzaremos a prepararlo.';
      case OrderStatus.preparing:
        return '¡Estamos preparando tu granizado! 🍋\nEstamos trabajando para que llegue perfecto.';
      case OrderStatus.almostReady:
        return '¡Ya casi! ✨\nTu pedido está tomando los últimos detalles.';
      case OrderStatus.ready:
        return '¡Tu pedido está listo! 🎉\nYa puedes acercarte a recogerlo.';
      case OrderStatus.delivered:
        return '¡Pedido entregado! 💚\nGracias por disfrutar Lemon Drop.';
      case OrderStatus.cancelled:
        return 'Este pedido fue cancelado.';
    }
  }

  /// 1-based step index for the normal pipeline (1..6) or -1 for cancelled
  int get trackingStepIndex {
    switch (this) {
      case OrderStatus.received:
        return 1;
      case OrderStatus.accepted:
        return 2;
      case OrderStatus.preparing:
        return 3;
      case OrderStatus.almostReady:
        return 4;
      case OrderStatus.ready:
        return 5;
      case OrderStatus.delivered:
        return 6;
      case OrderStatus.cancelled:
        return -1;
    }
  }

  String toJson() {
    if (this == OrderStatus.almostReady) {
      return 'ALMOST_READY';
    }
    return name.toUpperCase();
  }

  static OrderStatus fromJson(String value) {
    switch (value.toUpperCase()) {
      case 'RECEIVED':
        return OrderStatus.received;
      case 'ACCEPTED':
        return OrderStatus.accepted;
      case 'PREPARING':
        return OrderStatus.preparing;
      case 'ALMOST_READY':
        return OrderStatus.almostReady;
      case 'READY':
        return OrderStatus.ready;
      case 'DELIVERED':
        return OrderStatus.delivered;
      case 'CANCELLED':
        return OrderStatus.cancelled;
      default:
        return OrderStatus.received;
    }
  }
}

enum ProductSize {
  small,
  medium,
  large;

  String get displayName {
    switch (this) {
      case ProductSize.small:
        return 'Pequeño';
      case ProductSize.medium:
        return 'Mediano';
      case ProductSize.large:
        return 'Grande';
    }
  }

  String toJson() => name.toUpperCase();

  static ProductSize fromJson(String value) {
    switch (value.toUpperCase()) {
      case 'SMALL':
        return ProductSize.small;
      case 'MEDIUM':
        return ProductSize.medium;
      case 'LARGE':
        return ProductSize.large;
      default:
        return ProductSize.medium;
    }
  }
}

class User {
  final String id;
  final String name;
  final String username;
  final String role; // "ADMIN" or "ASESOR"
  final String? phone;

  User({
    required this.id,
    required this.name,
    required this.username,
    required this.role,
    this.phone,
  });

  bool get isAdmin => role == 'ADMIN';
  bool get isAdvisor => role == 'ASESOR';

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'] ?? '',
      name: json['name'] ?? '',
      username: json['username'] ?? '',
      role: json['role'] ?? '',
      phone: json['phone'] as String?,
    );
  }
}

class Product {
  final String id;
  final String name;
  final String description;
  final Map<ProductSize, double> sizePrices;
  final bool available;

  Product({
    required this.id,
    required this.name,
    required this.description,
    required this.sizePrices,
    required this.available,
  });

  factory Product.fromJson(Map<String, dynamic> json) {
    final rawPrices = json['sizePrices'] as Map<String, dynamic>? ?? {};
    final prices = <ProductSize, double>{};
    rawPrices.forEach((key, value) {
      prices[ProductSize.fromJson(key)] = (value as num).toDouble();
    });

    return Product(
      id: json['id'] ?? '',
      name: json['name'] ?? '',
      description: json['description'] ?? '',
      sizePrices: prices,
      available: json['available'] ?? false,
    );
  }
}

class Flavor {
  final String id;
  final String name;
  final String description;
  final bool available;
  final double additionalPrice;

  Flavor({
    required this.id,
    required this.name,
    required this.description,
    required this.available,
    required this.additionalPrice,
  });

  factory Flavor.fromJson(Map<String, dynamic> json) {
    return Flavor(
      id: json['id'] ?? '',
      name: json['name'] ?? '',
      description: json['description'] ?? '',
      available: json['available'] ?? false,
      additionalPrice: (json['additionalPrice'] as num?)?.toDouble() ?? 0.0,
    );
  }
}

class Addon {
  final String id;
  final String name;
  final bool available;
  final double additionalPrice;

  Addon({
    required this.id,
    required this.name,
    required this.available,
    required this.additionalPrice,
  });

  factory Addon.fromJson(Map<String, dynamic> json) {
    return Addon(
      id: json['id'] ?? '',
      name: json['name'] ?? '',
      available: json['available'] ?? false,
      additionalPrice: (json['additionalPrice'] as num?)?.toDouble() ?? 0.0,
    );
  }
}

class OrderItemAddon {
  final String addonId;
  final String addonName;
  final double unitPrice;
  final int quantity;

  OrderItemAddon({
    required this.addonId,
    required this.addonName,
    required this.unitPrice,
    required this.quantity,
  });

  factory OrderItemAddon.fromJson(Map<String, dynamic> json) {
    return OrderItemAddon(
      addonId: json['addonId'] ?? '',
      addonName: json['addonName'] ?? '',
      unitPrice: (json['unitPrice'] as num?)?.toDouble() ?? 0.0,
      quantity: json['quantity'] ?? 1,
    );
  }
}

class OrderItem {
  final String productId;
  final String productName;
  final String flavorId;
  final String flavorName;
  final ProductSize size;
  final int quantity;
  final double unitPrice;
  final List<OrderItemAddon> addons;
  final double subtotal;
  final String observations;

  OrderItem({
    required this.productId,
    required this.productName,
    required this.flavorId,
    required this.flavorName,
    required this.size,
    required this.quantity,
    this.unitPrice = 0.0,
    required this.addons,
    required this.subtotal,
    required this.observations,
  });

  factory OrderItem.fromJson(Map<String, dynamic> json) {
    final rawAddons = json['addons'] as List? ?? [];
    final qty = json['quantity'] as int? ?? 1;
    final sub = (json['subtotal'] as num?)?.toDouble() ?? 0.0;
    final uPrice = (json['unitPrice'] as num?)?.toDouble() ?? (qty > 0 ? (sub / qty) : 0.0);

    return OrderItem(
      productId: json['productId'] ?? '',
      productName: json['productName'] ?? '',
      flavorId: json['flavorId'] ?? '',
      flavorName: json['flavorName'] ?? '',
      size: ProductSize.fromJson(json['size'] ?? 'MEDIUM'),
      quantity: qty,
      unitPrice: uPrice,
      addons: rawAddons.map((e) => OrderItemAddon.fromJson(e as Map<String, dynamic>)).toList(),
      subtotal: sub,
      observations: json['observations'] ?? '',
    );
  }
}

class Order {
  final String id;
  final String orderCode;
  final String customerName;
  final String customerPhone;
  final List<OrderItem> items;
  final double subtotal;
  final double total;
  final OrderStatus status;
  final String observations;
  final String advisorNotes;
  final String? cancellationReason;
  final String priority; // "NORMAL" or "ALTA"
  final String? assignedAdvisor;
  final DateTime? receivedAt;
  final DateTime? acceptedAt;
  final DateTime? preparingAt;
  final DateTime? almostReadyAt;
  final DateTime? readyAt;
  final DateTime? deliveredAt;
  final DateTime? cancelledAt;
  final String? createdBy;
  final String? lastModifiedBy;
  final DateTime createdAt;
  final DateTime updatedAt;

  Order({
    required this.id,
    required this.orderCode,
    required this.customerName,
    required this.customerPhone,
    required this.items,
    required this.subtotal,
    required this.total,
    required this.status,
    required this.observations,
    required this.advisorNotes,
    this.cancellationReason,
    this.priority = 'NORMAL',
    this.assignedAdvisor,
    this.receivedAt,
    this.acceptedAt,
    this.preparingAt,
    this.almostReadyAt,
    this.readyAt,
    this.deliveredAt,
    this.cancelledAt,
    this.createdBy,
    this.lastModifiedBy,
    required this.createdAt,
    required this.updatedAt,
  });

  bool get isUrgent => priority.toUpperCase() == 'ALTA';

  bool get isAssigned =>
      assignedAdvisor != null &&
      assignedAdvisor!.trim().isNotEmpty &&
      !assignedAdvisor!.equalsIgnoreCase('Sin asignar') &&
      !assignedAdvisor!.equalsIgnoreCase('Ninguno');

  Duration get totalDuration {
    final end = deliveredAt ?? cancelledAt ?? updatedAt;
    return end.difference(createdAt);
  }

  factory Order.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'] as List? ?? [];

    DateTime? parseDate(dynamic val) {
      if (val == null) return null;
      return DateTime.tryParse(val.toString());
    }

    return Order(
      id: json['id'] ?? '',
      orderCode: json['orderCode'] ?? '',
      customerName: json['customerName'] ?? '',
      customerPhone: json['customerPhone'] ?? '',
      items: rawItems.map((e) => OrderItem.fromJson(e as Map<String, dynamic>)).toList(),
      subtotal: (json['subtotal'] as num?)?.toDouble() ?? (json['total'] as num?)?.toDouble() ?? 0.0,
      total: (json['total'] as num?)?.toDouble() ?? 0.0,
      status: OrderStatus.fromJson(json['status'] ?? 'RECEIVED'),
      observations: json['observations'] ?? '',
      advisorNotes: json['advisorNotes'] ?? '',
      cancellationReason: json['cancellationReason'] as String?,
      priority: (json['priority'] as String?) ?? 'NORMAL',
      assignedAdvisor: json['assignedAdvisor'] as String?,
      receivedAt: parseDate(json['receivedAt']),
      acceptedAt: parseDate(json['acceptedAt']),
      preparingAt: parseDate(json['preparingAt']),
      almostReadyAt: parseDate(json['almostReadyAt']),
      readyAt: parseDate(json['readyAt']),
      deliveredAt: parseDate(json['deliveredAt']),
      cancelledAt: parseDate(json['cancelledAt']),
      createdBy: json['createdBy'] as String?,
      lastModifiedBy: json['lastModifiedBy'] as String?,
      createdAt: parseDate(json['createdAt']) ?? DateTime.now(),
      updatedAt: parseDate(json['updatedAt']) ?? DateTime.now(),
    );
  }
}

extension StringUtils on String {
  bool equalsIgnoreCase(String other) => toLowerCase() == other.toLowerCase();
}

class OrderStatusHistoryEntry {
  final String id;
  final String orderId;
  final String orderCode;
  final OrderStatus? previousStatus;
  final OrderStatus newStatus;
  final String updatedBy;
  final DateTime updatedAt;
  final String? notes;

  OrderStatusHistoryEntry({
    required this.id,
    required this.orderId,
    required this.orderCode,
    this.previousStatus,
    required this.newStatus,
    required this.updatedBy,
    required this.updatedAt,
    this.notes,
  });

  factory OrderStatusHistoryEntry.fromJson(Map<String, dynamic> json) {
    return OrderStatusHistoryEntry(
      id: json['id'] ?? '',
      orderId: json['orderId'] ?? '',
      orderCode: json['orderCode'] ?? '',
      previousStatus: json['previousStatus'] != null ? OrderStatus.fromJson(json['previousStatus']) : null,
      newStatus: OrderStatus.fromJson(json['newStatus'] ?? 'RECEIVED'),
      updatedBy: json['updatedBy'] ?? 'SISTEMA',
      updatedAt: json['updatedAt'] != null ? DateTime.tryParse(json['updatedAt'].toString()) ?? DateTime.now() : DateTime.now(),
      notes: json['notes'] as String?,
    );
  }
}

class OrderChangeHistoryEntry {
  final String id;
  final String orderId;
  final String orderCode;
  final String propertyName;
  final String? oldValue;
  final String? newValue;
  final String updatedBy;
  final DateTime updatedAt;
  final String reason;

  OrderChangeHistoryEntry({
    required this.id,
    required this.orderId,
    required this.orderCode,
    required this.propertyName,
    this.oldValue,
    this.newValue,
    required this.updatedBy,
    required this.updatedAt,
    required this.reason,
  });

  factory OrderChangeHistoryEntry.fromJson(Map<String, dynamic> json) {
    return OrderChangeHistoryEntry(
      id: json['id'] ?? '',
      orderId: json['orderId'] ?? '',
      orderCode: json['orderCode'] ?? '',
      propertyName: json['propertyName'] ?? '',
      oldValue: json['oldValue'] as String?,
      newValue: json['newValue'] as String?,
      updatedBy: json['updatedBy'] ?? 'SISTEMA',
      updatedAt: json['updatedAt'] != null ? DateTime.tryParse(json['updatedAt'].toString()) ?? DateTime.now() : DateTime.now(),
      reason: json['reason'] ?? '',
    );
  }
}

class Stats {
  final int deliveredCountToday;
  final int readyCount;
  final int preparingCount;
  final int pendingCount;
  final int cancelledCountToday;
  final int urgentCount;
  final int unassignedCount;
  final int myDeliveredCountToday;
  final int myActiveCount;
  final double totalSalesToday;
  final int ordersCreatedToday;
  final String topProductToday;
  final String topFlavorToday;

  Stats({
    required this.deliveredCountToday,
    required this.readyCount,
    required this.preparingCount,
    required this.pendingCount,
    this.cancelledCountToday = 0,
    this.urgentCount = 0,
    this.unassignedCount = 0,
    this.myDeliveredCountToday = 0,
    this.myActiveCount = 0,
    required this.totalSalesToday,
    required this.ordersCreatedToday,
    required this.topProductToday,
    required this.topFlavorToday,
  });

  factory Stats.fromJson(Map<String, dynamic> json) {
    return Stats(
      deliveredCountToday: json['deliveredCountToday'] ?? 0,
      readyCount: json['readyCount'] ?? 0,
      preparingCount: json['preparingCount'] ?? 0,
      pendingCount: json['pendingCount'] ?? 0,
      cancelledCountToday: json['cancelledCountToday'] ?? 0,
      urgentCount: json['urgentCount'] ?? 0,
      unassignedCount: json['unassignedCount'] ?? 0,
      myDeliveredCountToday: json['myDeliveredCountToday'] ?? 0,
      myActiveCount: json['myActiveCount'] ?? 0,
      totalSalesToday: (json['totalSalesToday'] as num?)?.toDouble() ?? 0.0,
      ordersCreatedToday: json['ordersCreatedToday'] ?? 0,
      topProductToday: json['topProductToday'] ?? 'Ninguno',
      topFlavorToday: json['topFlavorToday'] ?? 'Ninguno',
    );
  }
}
