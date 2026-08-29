import '../services/firestore_service.dart';
import '../models/order_model.dart';
import '../models/cart_item_model.dart';

class OrderRepository {
  final FirestoreService _firestoreService = FirestoreService();

  Future<String> createOrder({
    required String userId,
    required List<CartItemModel> cartItems,
    required String deliveryAddress,
    String paymentMethod = 'UPI',
    double deliveryFee = 2.99,
  }) async {
    if (cartItems.isEmpty) {
      throw Exception('Cannot create order from an empty cart.');
    }

    final itemSnapshots = cartItems.map((item) {
      return OrderItemSnapshot(
        productId: item.productId,
        name: item.productName,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        quantityLabel: item.quantityLabel,
        imageUrl: item.imageUrl,
      );
    }).toList();

    double calculatedSubtotal = 0.0;
    for (final item in itemSnapshots) {
      calculatedSubtotal += item.totalPrice;
    }

    final total = calculatedSubtotal + deliveryFee;

    final order = OrderModel(
      id: '',
      userId: userId,
      status: 'placed',
      items: itemSnapshots,
      subtotal: calculatedSubtotal,
      deliveryFee: deliveryFee,
      total: total,
      deliveryAddress: deliveryAddress,
      paymentMethod: paymentMethod,
      estimatedDelivery: '15-20 minutes',
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
    );

    return await _firestoreService.createOrder(order);
  }

  Stream<List<OrderModel>> streamUserOrders(String uid) {
    return _firestoreService.streamUserOrders(uid);
  }
}
