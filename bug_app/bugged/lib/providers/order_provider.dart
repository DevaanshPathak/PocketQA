import 'dart:async';
import 'package:flutter/material.dart';
import '../repositories/order_repository.dart';
import '../models/order_model.dart';
import '../models/cart_item_model.dart';


class OrderProvider extends ChangeNotifier {
  final OrderRepository _orderRepository = OrderRepository();

  List<OrderModel> _orders = [];
  bool _isPlacingOrder = false;
  String? _userId;
  StreamSubscription<List<OrderModel>>? _orderSub;

  List<OrderModel> get orders => _orders;
  bool get isPlacingOrder => _isPlacingOrder;
  bool get isLoading => _isPlacingOrder;

  void updateUserId(String? uid) {
    if (_userId == uid) return;
    _userId = uid;
    _orderSub?.cancel();
    if (uid != null && uid.isNotEmpty) {
      _orderSub = _orderRepository.streamUserOrders(uid).listen((orderList) {
        _orders = orderList;
        notifyListeners();
      });
    } else {
      _orders = [];
      notifyListeners();
    }
  }

  Future<String?> placeOrder({
    required List<CartItemModel> cartItems,
    required String deliveryAddress,
    String paymentMethod = 'UPI',
  }) async {
    _isPlacingOrder = true;
    notifyListeners();

    final orderId = 'order_${DateTime.now().millisecondsSinceEpoch}';
    final subtotal = cartItems.fold(0.0, (sum, i) => sum + i.totalPrice);
    final total = subtotal + 2.99;

    final localOrder = OrderModel(
      id: orderId,
      userId: _userId ?? 'guest',
      items: cartItems.map((i) => OrderItemSnapshot(
        productId: i.productId,
        name: i.name,
        quantity: i.quantity,
        unitPrice: i.unitPrice,
        quantityLabel: i.quantityLabel,
        imageUrl: i.imageUrl,
      )).toList(),
      subtotal: subtotal,
      deliveryFee: 2.99,
      total: total,
      deliveryAddress: deliveryAddress,
      paymentMethod: paymentMethod,
      status: 'placed',
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
    );

    _orders.insert(0, localOrder);

    if (_userId != null && _userId!.isNotEmpty) {
      try {
        await _orderRepository.createOrder(
          userId: _userId!,
          cartItems: cartItems,
          deliveryAddress: deliveryAddress,
          paymentMethod: paymentMethod,
        );
      } catch (e) {
        debugPrint('Firestore order placement fallback: $e');
      }
    }

    _isPlacingOrder = false;
    notifyListeners();
    return orderId;
  }

  Future<String> createOrder({
    required List<CartItemModel> cartItems,
    required double totalAmount,
    required String deliveryAddress,
    String paymentMethod = 'UPI',
  }) async {
    final orderId = await placeOrder(
      cartItems: cartItems,
      deliveryAddress: deliveryAddress,
      paymentMethod: paymentMethod,
    );
    return orderId ?? '';
  }

  @override
  void dispose() {
    _orderSub?.cancel();
    super.dispose();
  }
}
