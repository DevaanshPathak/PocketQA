import 'package:cloud_firestore/cloud_firestore.dart';

class OrderItemSnapshot {
  final String productId;
  final String name;
  final int quantity;
  final double unitPrice;
  final String quantityLabel;
  final String imageUrl;

  OrderItemSnapshot({
    required this.productId,
    required this.name,
    required this.quantity,
    required this.unitPrice,
    required this.quantityLabel,
    required this.imageUrl,
  });

  double get totalPrice => quantity * unitPrice;

  factory OrderItemSnapshot.fromMap(Map<String, dynamic> map) {
    return OrderItemSnapshot(
      productId: map['productId'] as String? ?? '',
      name: map['name'] as String? ?? map['title'] as String? ?? map['productName'] as String? ?? 'Item',
      quantity: (map['quantity'] as num?)?.toInt() ?? (map['qty'] as num?)?.toInt() ?? 1,
      unitPrice: (map['unitPrice'] as num?)?.toDouble() ?? 0.0,
      quantityLabel: map['quantityLabel'] as String? ?? '',
      imageUrl: map['imageUrl'] as String? ?? '',
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'productId': productId,
      'name': name,
      'quantity': quantity,
      'unitPrice': unitPrice,
      'quantityLabel': quantityLabel,
      'imageUrl': imageUrl,
    };
  }
}

class OrderModel {
  final String id;
  final String userId;
  final String status;
  final List<OrderItemSnapshot> items;
  final double subtotal;
  final double deliveryFee;
  final double total;
  final String deliveryAddress;
  final String paymentMethod;
  final String estimatedDelivery;
  final DateTime createdAt;
  final DateTime updatedAt;

  OrderModel({
    required this.id,
    required this.userId,
    this.status = 'placed',
    required this.items,
    required this.subtotal,
    this.deliveryFee = 2.99,
    required this.total,
    required this.deliveryAddress,
    this.paymentMethod = 'UPI',
    this.estimatedDelivery = '15-20 minutes',
    DateTime? createdAt,
    DateTime? updatedAt,
  })  : createdAt = createdAt ?? DateTime.now(),
        updatedAt = updatedAt ?? DateTime.now();

  double get totalAmount => total;

  factory OrderModel.fromMap(Map<String, dynamic> map, String id) {
    final rawItems = map['items'] as List<dynamic>? ?? [];
    return OrderModel(
      id: id,
      userId: map['userId'] as String? ?? '',
      status: map['status'] as String? ?? 'placed',
      items: rawItems
          .map((itemMap) => OrderItemSnapshot.fromMap(itemMap as Map<String, dynamic>))
          .toList(),
      subtotal: (map['subtotal'] as num?)?.toDouble() ?? (map['total'] as num?)?.toDouble() ?? 0.0,
      deliveryFee: (map['deliveryFee'] as num?)?.toDouble() ?? 2.99,
      total: (map['total'] as num?)?.toDouble() ?? (map['totalAmount'] as num?)?.toDouble() ?? 0.0,
      deliveryAddress: map['deliveryAddress'] as String? ?? '',
      paymentMethod: map['paymentMethod'] as String? ?? 'UPI',
      estimatedDelivery: map['estimatedDelivery'] as String? ?? '15-20 minutes',
      createdAt: (map['createdAt'] as Timestamp?)?.toDate() ?? DateTime.now(),
      updatedAt: (map['updatedAt'] as Timestamp?)?.toDate() ?? DateTime.now(),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'userId': userId,
      'status': status,
      'items': items.map((i) => i.toMap()).toList(),
      'subtotal': subtotal,
      'deliveryFee': deliveryFee,
      'total': total,
      'deliveryAddress': deliveryAddress,
      'paymentMethod': paymentMethod,
      'estimatedDelivery': estimatedDelivery,
      'createdAt': Timestamp.fromDate(createdAt),
      'updatedAt': Timestamp.fromDate(updatedAt),
    };
  }
}
