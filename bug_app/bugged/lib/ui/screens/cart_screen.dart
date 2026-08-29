import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../state/cart_provider.dart';
import '../widgets/cart_item_tile.dart';
import '../../config/app_config.dart';

class CartScreen extends StatefulWidget {
  const CartScreen({super.key});

  @override
  State<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends State<CartScreen> {
  final TextEditingController _promoController = TextEditingController();

  void _applyPromo(bool injectBugs) {
    final code = _promoController.text.trim();
    if (injectBugs && (code == "FREEZE" || code == "FAIL")) {
      // ANR / UI Freeze Bug
      while (true) {
        // Infinite loop on main isolate
      }
    } else if (code == "FAIL") {
       ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Invalid promo code')),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Promo code applied!')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final cart = Provider.of<CartProvider>(context);
    final config = AppConfig.of(context)!;

    return Scaffold(
      appBar: AppBar(title: const Text('Your Cart')),
      body: Column(
        children: [
          Expanded(
            child: ListView.builder(
              itemCount: cart.items.length,
              itemBuilder: (ctx, i) {
                final entry = cart.items.entries.toList()[i];
                return CartItemTile(productId: entry.key, cartItem: entry.value);
              },
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(15.0),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _promoController,
                    decoration: const InputDecoration(labelText: 'Promo Code'),
                  ),
                ),
                TextButton(
                  onPressed: () => _applyPromo(config.injectBugs),
                  child: const Text('APPLY'),
                ),
              ],
            ),
          ),
          Card(
            margin: const EdgeInsets.all(15),
            child: Padding(
              padding: const EdgeInsets.all(8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Total', style: TextStyle(fontSize: 20)),
                  const Spacer(),
                  Chip(
                    label: Text(
                      '\$${cart.totalAmount.toStringAsFixed(2)}',
                      style: const TextStyle(color: Colors.white),
                    ),
                    backgroundColor: Theme.of(context).primaryColor,
                  ),
                  TextButton(
                    onPressed: cart.totalAmount <= 0 && !config.injectBugs
                        ? null
                        : () => Navigator.pushNamed(context, '/checkout'),
                    child: const Text('ORDER NOW'),
                  )
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
