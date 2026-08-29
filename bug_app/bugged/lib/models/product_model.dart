import 'package:cloud_firestore/cloud_firestore.dart';
import 'cart_item_model.dart';

class ProductModel {
  final String id;
  final String name;
  final String description;
  final String categoryId;
  final String brand;
  final String quantityLabel;
  final double price;
  final double originalPrice;
  final int discountPercentage;
  final String imageUrl;
  final bool isAvailable;
  final int stockQuantity;
  final int sortOrder;
  final DateTime createdAt;
  final DateTime updatedAt;

  ProductModel({
    required this.id,
    required this.name,
    required this.description,
    required this.categoryId,
    required this.brand,
    required this.quantityLabel,
    required this.price,
    required this.originalPrice,
    required this.discountPercentage,
    required this.imageUrl,
    this.isAvailable = true,
    this.stockQuantity = 100,
    this.sortOrder = 0,
    required this.createdAt,
    required this.updatedAt,
  });

  CartItemModel toCartItem({int quantity = 1}) {
    return CartItemModel(
      productId: id,
      productName: name,
      quantity: quantity,
      unitPrice: price,
      imageUrl: imageUrl,
      quantityLabel: quantityLabel,
      updatedAt: DateTime.now(),
    );
  }

  ProductModel copyWith({
    String? id,
    String? name,
    String? description,
    String? categoryId,
    String? brand,
    String? quantityLabel,
    double? price,
    double? originalPrice,
    int? discountPercentage,
    String? imageUrl,
    bool? isAvailable,
    int? stockQuantity,
    int? sortOrder,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return ProductModel(
      id: id ?? this.id,
      name: name ?? this.name,
      description: description ?? this.description,
      categoryId: categoryId ?? this.categoryId,
      brand: brand ?? this.brand,
      quantityLabel: quantityLabel ?? this.quantityLabel,
      price: price ?? this.price,
      originalPrice: originalPrice ?? this.originalPrice,
      discountPercentage: discountPercentage ?? this.discountPercentage,
      imageUrl: imageUrl ?? this.imageUrl,
      isAvailable: isAvailable ?? this.isAvailable,
      stockQuantity: stockQuantity ?? this.stockQuantity,
      sortOrder: sortOrder ?? this.sortOrder,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  factory ProductModel.fromMap(Map<String, dynamic> map, String id) {
    return ProductModel(
      id: id,
      name: map['name'] as String? ?? '',
      description: map['description'] as String? ?? '',
      categoryId: map['categoryId'] as String? ?? '',
      brand: map['brand'] as String? ?? '',
      quantityLabel: map['quantityLabel'] as String? ?? '1 unit',
      price: (map['price'] as num?)?.toDouble() ?? 0.0,
      originalPrice: (map['originalPrice'] as num?)?.toDouble() ?? 0.0,
      discountPercentage: (map['discountPercentage'] as num?)?.toInt() ?? 0,
      imageUrl: map['imageUrl'] as String? ?? '',
      isAvailable: map['isAvailable'] as bool? ?? true,
      stockQuantity: (map['stockQuantity'] as num?)?.toInt() ?? 0,
      sortOrder: (map['sortOrder'] as num?)?.toInt() ?? 0,
      createdAt: (map['createdAt'] as Timestamp?)?.toDate() ?? DateTime.now(),
      updatedAt: (map['updatedAt'] as Timestamp?)?.toDate() ?? DateTime.now(),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'description': description,
      'categoryId': categoryId,
      'brand': brand,
      'quantityLabel': quantityLabel,
      'price': price,
      'originalPrice': originalPrice,
      'discountPercentage': discountPercentage,
      'imageUrl': imageUrl,
      'isAvailable': isAvailable,
      'stockQuantity': stockQuantity,
      'sortOrder': sortOrder,
      'createdAt': Timestamp.fromDate(createdAt),
      'updatedAt': Timestamp.fromDate(updatedAt),
    };
  }
}
