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
    if (_userId == null) return null;
    _isPlacingOrder = true;
    notifyListeners();

    try {
      final orderId = await _orderRepository.createOrder(
        userId: _userId!,
        cartItems: cartItems,
        deliveryAddress: deliveryAddress,
        paymentMethod: paymentMethod,
      );
      _isPlacingOrder = false;
      notifyListeners();
      return orderId;
    } catch (e) {
      _isPlacingOrder = false;
      notifyListeners();
      rethrow;
    }
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
