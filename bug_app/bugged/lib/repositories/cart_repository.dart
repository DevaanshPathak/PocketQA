import '../services/firestore_service.dart';
import '../models/cart_item_model.dart';
import '../models/product_model.dart';

class CartRepository {
  final FirestoreService _firestoreService = FirestoreService();

  Stream<List<CartItemModel>> streamCart(String uid) {
    return _firestoreService.streamCart(uid);
  }

  Future<void> addToCart({
    required String uid,
    required ProductModel product,
    int quantity = 1,
  }) async {
    final cartItem = CartItemModel(
      productId: product.id,
      productName: product.name,
      quantity: quantity,
      unitPrice: product.price,
      imageUrl: product.imageUrl,
      quantityLabel: product.quantityLabel,
      updatedAt: DateTime.now(),
    );
    await _firestoreService.addToCart(uid, cartItem);
  }

  Future<void> updateQuantity({
    required String uid,
    required String productId,
    required int quantity,
  }) async {
    await _firestoreService.updateCartQuantity(uid, productId, quantity);
  }

  Future<void> removeFromCart({
    required String uid,
    required String productId,
  }) async {
    await _firestoreService.removeFromCart(uid, productId);
  }

  Future<void> clearCart(String uid) async {
    await _firestoreService.clearCart(uid);
  }
}
