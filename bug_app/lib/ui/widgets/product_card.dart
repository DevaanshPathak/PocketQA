import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/product.dart';
import '../../state/cart_provider.dart';

class ProductCard extends StatelessWidget {
  final Product product;
  final bool isBuggy;

  const ProductCard({super.key, required this.product, required this.isBuggy});

  @override
  Widget build(BuildContext context) {
    // UI Render Bug: Intentionally force unhandled null if title contains "BUG"
    // or based on some condition. The prompt specified 3rd item in list.
    // Handling is better done in the screen for index-based bugs,
    // but we can simulate it here if we pass a flag.

    return Card(
      child: Column(
        children: [
          Expanded(
            child: Image.network(
              product.imageUrl,
              errorBuilder: (context, error, stackTrace) =>
                  const Icon(Icons.error),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: Text(
              product.title,
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
          ),
          Text('\$${product.price.toStringAsFixed(2)}'),
          IconButton(
            icon: const Icon(Icons.add_shopping_cart),
            tooltip: 'Add ${product.title} to cart',
            onPressed: () {
              Provider.of<CartProvider>(context, listen: false).addItem(product);
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('${product.title} added to cart'),
                  duration: const Duration(seconds: 1),
                ),
              );
            },
          ),
        ],
      ),
    );
  }
}
