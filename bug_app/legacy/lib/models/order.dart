import 'package:cloud_firestore/cloud_firestore.dart';

class OrderItem {
  final String productId;
  final String title;
  final int qty;
  final double unitPrice;

  OrderItem({
    required this.productId,
    required this.title,
    required this.qty,
    required this.unitPrice,
  });

  Map<String, dynamic> toMap() {
    return {
      'productId': productId,
      'title': title,
      'qty': qty,
      'unitPrice': unitPrice,
    };
  }
}

class OrderModel {
  final String? orderId;
  final String userId;
  final List<OrderItem> items;
  final double total;
  final String deliveryAddress;
  final String status;
  final DateTime timestamp;

  OrderModel({
    this.orderId,
    required this.userId,
    required this.items,
    required this.total,
    required this.deliveryAddress,
    this.status = 'pending',
    required this.timestamp,
  });

  Map<String, dynamic> toMap() {
    return {
      'userId': userId,
      'items': items.map((i) => i.toMap()).toList(),
      'total': total,
      'deliveryAddress': deliveryAddress,
      'status': status,
      'timestamp': FieldValue.serverTimestamp(),
    };
  }
}
