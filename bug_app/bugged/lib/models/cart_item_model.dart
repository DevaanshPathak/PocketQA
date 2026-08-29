import 'package:cloud_firestore/cloud_firestore.dart';

class CartItemModel {
  final String productId;
  final String productName;
  final int quantity;
  final double unitPrice;
  final String imageUrl;
  final String quantityLabel;
  final DateTime updatedAt;

  CartItemModel({
    required this.productId,
    required this.productName,
    required this.quantity,
    required this.unitPrice,
    required this.imageUrl,
    required this.quantityLabel,
    required this.updatedAt,
  });

  double get totalPrice => quantity * unitPrice;

  factory CartItemModel.fromMap(Map<String, dynamic> map, String id) {
    return CartItemModel(
      productId: map['productId'] as String? ?? id,
      productName: map['productName'] as String? ?? '',
      quantity: (map['quantity'] as num?)?.toInt() ?? 1,
      unitPrice: (map['unitPrice'] as num?)?.toDouble() ?? 0.0,
      imageUrl: map['imageUrl'] as String? ?? '',
      quantityLabel: map['quantityLabel'] as String? ?? '',
      updatedAt: (map['updatedAt'] as Timestamp?)?.toDate() ?? DateTime.now(),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'productId': productId,
      'productName': productName,
      'quantity': quantity,
      'unitPrice': unitPrice,
      'imageUrl': imageUrl,
      'quantityLabel': quantityLabel,
      'updatedAt': Timestamp.fromDate(updatedAt),
    };
  }

  CartItemModel copyWith({
    int? quantity,
    DateTime? updatedAt,
  }) {
    return CartItemModel(
      productId: productId,
      productName: productName,
      quantity: quantity ?? this.quantity,
      unitPrice: unitPrice,
      imageUrl: imageUrl,
      quantityLabel: quantityLabel,
      updatedAt: updatedAt ?? DateTime.now(),
    );
  }
}
