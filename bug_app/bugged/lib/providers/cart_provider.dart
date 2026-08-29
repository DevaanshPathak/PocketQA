import 'dart:async';
import 'package:flutter/material.dart';
import '../repositories/cart_repository.dart';
import '../models/cart_item_model.dart';
import '../models/product_model.dart';

class CartProvider extends ChangeNotifier {
  final CartRepository _cartRepository = CartRepository();

  List<CartItemModel> _items = [];
  String? _userId;
  StreamSubscription<List<CartItemModel>>? _cartSub;

  List<CartItemModel> get items => _items;

  int get itemCount => _items.fold(0, (sum, item) => sum + item.quantity);

  int get totalItemCount => itemCount;

  double get subtotal => _items.fold(0.0, (sum, item) => sum + item.totalPrice);

  double get deliveryFee => _items.isEmpty ? 0.0 : 2.99;

  double get totalAmount => subtotal + deliveryFee;

  void updateUserId(String? uid) {
    if (_userId == uid) return;
    _userId = uid;
    _cartSub?.cancel();
    if (uid != null && uid.isNotEmpty) {
      _cartSub = _cartRepository.streamCart(uid).listen((cartItems) {
        _items = cartItems;
        notifyListeners();
      });
    }
  }

  Future<void> addToCart(ProductModel product, {int quantity = 1}) async {
    final existingIndex = _items.indexWhere((i) => i.productId == product.id);
    if (existingIndex != -1) {
      final existing = _items[existingIndex];
      _items[existingIndex] = existing.copyWith(quantity: existing.quantity + quantity);
    } else {
      _items.add(product.toCartItem(quantity: quantity));
    }
    notifyListeners();

    if (_userId != null && _userId!.isNotEmpty) {
      try {
        await _cartRepository.addToCart(
          uid: _userId!,
          product: product,
          quantity: quantity,
        );
      } catch (e) {
        debugPrint('Firestore cart sync fallback: $e');
      }
    }
  }

  Future<void> addItem(ProductModel product) async {
    await addToCart(product);
  }

  Future<void> incrementItem(String productId) async {
    final existingIndex = _items.indexWhere((i) => i.productId == productId);
    if (existingIndex != -1) {
      await updateQuantity(productId, _items[existingIndex].quantity + 1);
    }
  }

  Future<void> decrementItem(String productId) async {
    final existingIndex = _items.indexWhere((i) => i.productId == productId);
    if (existingIndex != -1) {
      final currentQty = _items[existingIndex].quantity;
      if (currentQty > 1) {
        await updateQuantity(productId, currentQty - 1);
      } else {
        await removeFromCart(productId);
      }
    }
  }

  Future<void> updateQuantity(String productId, int newQuantity) async {
    final existingIndex = _items.indexWhere((i) => i.productId == productId);
    if (existingIndex != -1) {
      if (newQuantity <= 0) {
        _items.removeAt(existingIndex);
      } else {
        _items[existingIndex] = _items[existingIndex].copyWith(quantity: newQuantity);
      }
      notifyListeners();
    }

    if (_userId != null && _userId!.isNotEmpty) {
      try {
        await _cartRepository.updateQuantity(
          uid: _userId!,
          productId: productId,
          quantity: newQuantity,
        );
      } catch (e) {
        debugPrint('Firestore cart sync fallback: $e');
      }
    }
  }

  Future<void> removeFromCart(String productId) async {
    _items.removeWhere((i) => i.productId == productId);
    notifyListeners();

    if (_userId != null && _userId!.isNotEmpty) {
      try {
        await _cartRepository.removeFromCart(
          uid: _userId!,
          productId: productId,
        );
      } catch (e) {
        debugPrint('Firestore cart sync fallback: $e');
      }
    }
  }

  Future<void> clearCart() async {
    _items.clear();
    notifyListeners();

    if (_userId != null && _userId!.isNotEmpty) {
      try {
        await _cartRepository.clearCart(_userId!);
      } catch (e) {
        debugPrint('Firestore cart sync fallback: $e');
      }
    }
  }

  @override
  void dispose() {
    _cartSub?.cancel();
    super.dispose();
  }
}
