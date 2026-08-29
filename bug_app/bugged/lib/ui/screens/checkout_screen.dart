import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../state/cart_provider.dart';
import '../../services/firebase_service.dart';
import '../../models/order.dart';
import '../../config/app_config.dart';

class CheckoutScreen extends StatefulWidget {
  const CheckoutScreen({super.key});

  @override
  State<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends State<CheckoutScreen> {
  final _formKey = GlobalKey<FormState>();
  final _addressController = TextEditingController();
  final _nameController = TextEditingController();
  final _phoneController = TextEditingController();
  bool _isLoading = false;
  final FirebaseService _firebaseService = FirebaseService();

  Future<void> _placeOrder(bool injectBugs) async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _isLoading = true);

    final cart = Provider.of<CartProvider>(context, listen: false);
    final order = OrderModel(
      userId: _firebaseService.userId ?? 'anonymous',
      items: cart.items.values.map((i) => OrderItem(
        productId: i.product.id,
        title: i.product.title,
        qty: i.quantity,
        unitPrice: i.product.price,
      )).toList(),
      total: cart.totalAmount,
      deliveryAddress: _addressController.text,
      timestamp: DateTime.now(),
    );

    try {
      await _firebaseService.placeOrder(order);
      cart.clear();
      if (mounted) {
        Navigator.of(context).pushReplacementNamed('/order-success');
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error placing order: $e')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final config = AppConfig.of(context)!;

    return Scaffold(
      appBar: AppBar(title: const Text('Checkout')),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Padding(
              padding: const EdgeInsets.all(16.0),
              child: Form(
                key: _formKey,
                child: ListView(
                  children: [
                    TextFormField(
                      controller: _nameController,
                      decoration: const InputDecoration(labelText: 'Full Name'),
                      validator: (value) =>
                          value == null || value.isEmpty ? 'Please enter name' : null,
                    ),
                    TextFormField(
                      controller: _addressController,
                      decoration: const InputDecoration(labelText: 'Delivery Address'),
                      validator: (value) =>
                          value == null || value.isEmpty ? 'Please enter address' : null,
                    ),
                    TextFormField(
                      controller: _phoneController,
                      decoration: const InputDecoration(labelText: 'Phone Number'),
                      keyboardType: TextInputType.phone,
                      validator: (value) =>
                          value == null || value.isEmpty ? 'Please enter phone' : null,
                    ),
                    const SizedBox(height: 20),
                    ElevatedButton(
                      onPressed: (_isLoading && !config.injectBugs)
                          ? null
                          : () => _placeOrder(config.injectBugs),
                      child: const Text('Place Order'),
                    ),
                  ],
                ),
              ),
            ),
    );
  }
}
