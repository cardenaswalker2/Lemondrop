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

  User({
    required this.id,
    required this.name,
    required this.username,
    required this.role,
  });

  bool get isAdmin => role == 'ADMIN';
  bool get isAdvisor => role == 'ASESOR';

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'] ?? '',
      name: json['name'] ?? '',
      username: json['username'] ?? '',
      role: json['role'] ?? '',
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
    required this.addons,
    required this.subtotal,
    required this.observations,
  });

  factory OrderItem.fromJson(Map<String, dynamic> json) {
    final rawAddons = json['addons'] as List? ?? [];
    return OrderItem(
      productId: json['productId'] ?? '',
      productName: json['productName'] ?? '',
      flavorId: json['flavorId'] ?? '',
      flavorName: json['flavorName'] ?? '',
      size: ProductSize.fromJson(json['size'] ?? 'MEDIUM'),
      quantity: json['quantity'] ?? 1,
      addons: rawAddons.map((e) => OrderItemAddon.fromJson(e)).toList(),
      subtotal: (json['subtotal'] as num?)?.toDouble() ?? 0.0,
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
    required this.createdAt,
    required this.updatedAt,
  });

  factory Order.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'] as List? ?? [];
    return Order(
      id: json['id'] ?? '',
      orderCode: json['orderCode'] ?? '',
      customerName: json['customerName'] ?? '',
      customerPhone: json['customerPhone'] ?? '',
      items: rawItems.map((e) => OrderItem.fromJson(e)).toList(),
      subtotal: (json['subtotal'] as num?)?.toDouble() ?? 0.0,
      total: (json['total'] as num?)?.toDouble() ?? 0.0,
      status: OrderStatus.fromJson(json['status'] ?? 'RECEIVED'),
      observations: json['observations'] ?? '',
      advisorNotes: json['advisorNotes'] ?? '',
      cancellationReason: json['cancellationReason'],
      createdAt: json['createdAt'] != null
          ? DateTime.parse(json['createdAt'])
          : DateTime.now(),
      updatedAt: json['updatedAt'] != null
          ? DateTime.parse(json['updatedAt'])
          : DateTime.now(),
    );
  }
}

class Stats {
  final int deliveredCountToday;
  final int readyCount;
  final int preparingCount;
  final int pendingCount;
  final double totalSalesToday;
  final int ordersCreatedToday;
  final String topProductToday;
  final String topFlavorToday;

  Stats({
    required this.deliveredCountToday,
    required this.readyCount,
    required this.preparingCount,
    required this.pendingCount,
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
      totalSalesToday: (json['totalSalesToday'] as num?)?.toDouble() ?? 0.0,
      ordersCreatedToday: json['ordersCreatedToday'] ?? 0,
      topProductToday: json['topProductToday'] ?? 'Ninguno',
      topFlavorToday: json['topFlavorToday'] ?? 'Ninguno',
    );
  }
}
