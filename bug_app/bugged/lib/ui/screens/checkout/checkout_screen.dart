import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/cart_provider.dart';
import '../../../providers/order_provider.dart';
import '../../theme.dart';
import '../order/order_confirmation_screen.dart';

class CheckoutScreen extends StatefulWidget {
  const CheckoutScreen({super.key});

  @override
  State<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends State<CheckoutScreen> {
  String _selectedPaymentMethod = 'UPI';

  // BUG 03 & 13: Cached snapshot of the cart to create a stale state.
  static List<CartItemModel>? _cachedCartSnapshot;
  static double? _cachedTotalSnapshot;

  @override
  void initState() {
    super.initState();
    final cart = context.read<CartProvider>();
    // BUG 03 & 13: If we have a cached snapshot, we reuse it instead of the current cart.
    // If not, we take a snapshot now and keep reusing it forever.
    if (_cachedCartSnapshot == null) {
      _cachedCartSnapshot = List.from(cart.items);
      _cachedTotalSnapshot = cart.totalAmount;
    }
  }

  @override
  Widget build(BuildContext context) {
    // We intentionally watch the provider here so the rest of the app might think we update,
    // but we use the stale _cachedCartSnapshot!
    final cart = context.watch<CartProvider>();
    final orderProvider = context.watch<OrderProvider>();
    
    // BUG 03 & 13: Use stale data!
    final cartItems = _cachedCartSnapshot ?? [];
    final totalPayable = _cachedTotalSnapshot ?? 0.0;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Checkout'),
      ),
      body: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Delivery Address Card
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: QuickCartTheme.surfaceWhite,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: QuickCartTheme.borderLight),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.location_on_outlined, color: QuickCartTheme.primaryGreen, size: 28),
                        const SizedBox(width: 12),
                        const Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Delivering to Home',
                                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                              ),
                              SizedBox(height: 2),
                              Text(
                                'Flat 402, Sunshine Apartments, 100ft Road, Indiranagar, Bengaluru - 560038',
                                style: TextStyle(color: QuickCartTheme.textSecondary, fontSize: 12),
                              ),
                            ],
                          ),
                        ),
                        TextButton(
                          onPressed: () {},
                          child: const Text('Change'),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 16),

                  // Delivery Time Estimate Banner
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: QuickCartTheme.primaryGreen.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.timer_outlined, color: QuickCartTheme.primaryDarkGreen),
                        SizedBox(width: 12),
                        Text(
                          'Estimated Delivery: 15–20 minutes',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            color: QuickCartTheme.primaryDarkGreen,
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 24),

                  // Payment Method Section
                  const Text(
                    'Select Payment Method',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  const SizedBox(height: 12),

                  Container(
                    decoration: BoxDecoration(
                      color: QuickCartTheme.surfaceWhite,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: QuickCartTheme.borderLight),
                    ),
                    child: Column(
                      children: [
                        RadioListTile<String>(
                          value: 'UPI',
                          groupValue: _selectedPaymentMethod,
                          activeColor: QuickCartTheme.primaryGreen,
                          title: const Text('Google Pay / PhonePe / UPI'),
                          secondary: const Icon(Icons.account_balance_wallet_outlined),
                          onChanged: (val) => setState(() => _selectedPaymentMethod = val!),
                        ),
                        const Divider(height: 1),
                        RadioListTile<String>(
                          value: 'COD',
                          groupValue: _selectedPaymentMethod,
                          activeColor: QuickCartTheme.primaryGreen,
                          title: const Text('Cash on Delivery'),
                          secondary: const Icon(Icons.payments_outlined),
                          onChanged: (val) => setState(() => _selectedPaymentMethod = val!),
                        ),
                        const Divider(height: 1),
                        RadioListTile<String>(
                          value: 'CARD',
                          groupValue: _selectedPaymentMethod,
                          activeColor: QuickCartTheme.primaryGreen,
                          title: const Text('Credit / Debit Card'),
                          secondary: const Icon(Icons.credit_card_outlined),
                          onChanged: (val) => setState(() => _selectedPaymentMethod = val!),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 24),

                  // Order Items Summary
                  const Text(
                    'Order Items Summary',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  const SizedBox(height: 12),

                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: QuickCartTheme.surfaceWhite,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: QuickCartTheme.borderLight),
                    ),
                    child: Column(
                      children: [
                        for (final item in cartItems)
                          Padding(
                            padding: const EdgeInsets.symmetric(vertical: 4),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Text('${item.quantity}x ${item.name}', style: const TextStyle(fontSize: 13)),
                                Text('₹${item.totalPrice.toStringAsFixed(0)}', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                              ],
                            ),
                          ),
                        const Divider(height: 20),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text('Grand Total', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                            Text('₹${totalPayable.toStringAsFixed(0)}', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: QuickCartTheme.primaryDarkGreen)),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Place Order Button
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: QuickCartTheme.surfaceWhite,
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.08),
                  blurRadius: 10,
                  offset: const Offset(0, -4),
                ),
              ],
            ),
            child: ElevatedButton(
              // BUG 12: Remove the orderProvider.isPlacingOrder guard.
              // Allows double place-order submission.
              onPressed: () async {
                      final orderId = await orderProvider.createOrder(
                        cartItems: cartItems,
                        totalAmount: totalPayable,
                        deliveryAddress: 'Flat 402, Sunshine Apartments, Indiranagar, Bengaluru',
                        paymentMethod: _selectedPaymentMethod,
                      );

                      cart.clearCart();

                      if (mounted) {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => OrderConfirmationScreen(orderId: orderId),
                          ),
                        );
                      }
                    },
              child: orderProvider.isPlacingOrder
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                    )
                  : Text('Place Order • ₹${totalPayable.toStringAsFixed(0)}'),
            ),
          ),
        ],
      ),
    );
  }
}
