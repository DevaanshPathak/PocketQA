import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../models/product_model.dart';
import '../../../providers/cart_provider.dart';
import '../../theme.dart';
import '../cart/cart_screen.dart';

class ProductDetailsScreen extends StatefulWidget {
  final ProductModel product;

  const ProductDetailsScreen({
    super.key,
    required this.product,
  });

  @override
  State<ProductDetailsScreen> createState() => _ProductDetailsScreenState();
}

class _ProductDetailsScreenState extends State<ProductDetailsScreen> {
  // BUG 04: Shared state across all instances
  static ProductModel? _currentViewedProduct;

  @override
  void initState() {
    super.initState();
    _currentViewedProduct = widget.product;
    _simulateAsyncLoad();
  }

  void _simulateAsyncLoad() async {
    final savedProduct = widget.product;
    // BUG 04: Un-cancelled async delay
    await Future.delayed(const Duration(milliseconds: 1500));
    // Overwrite the global state with the older product, even if navigation changed!
    if (mounted) {
      setState(() {
        _currentViewedProduct = savedProduct;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    // Render the (potentially stale) global product
    final productToDisplay = _currentViewedProduct ?? widget.product;
    final cart = context.watch<CartProvider>();
    final cartItem = cart.items.firstWhere(
      (item) => item.productId == productToDisplay.id,
      orElse: () => productToDisplay.toCartItem(quantity: 0),
    );
    final currentQty = cartItem.quantity;

    return Scaffold(
      appBar: AppBar(
        title: Text(productToDisplay.brand),
        actions: [
          IconButton(
            icon: const Icon(Icons.share_outlined),
            onPressed: () {},
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // High Quality Product Image Header
                  Container(
                    width: double.infinity,
                    height: 260,
                    color: QuickCartTheme.surfaceContainerLow,
                    child: Image.network(
                      productToDisplay.imageUrl,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => const Center(
                        child: Icon(Icons.shopping_basket_outlined, size: 80, color: QuickCartTheme.textLight),
                      ),
                    ),
                  ),

                  Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Brand & Name
                        Text(
                          productToDisplay.brand,
                          style: const TextStyle(
                            fontSize: 14,
                            color: QuickCartTheme.primaryGreen,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          productToDisplay.name,
                          style: const TextStyle(
                            fontSize: 22,
                            fontWeight: FontWeight.bold,
                            color: QuickCartTheme.textPrimary,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          productToDisplay.quantityLabel,
                          style: const TextStyle(
                            fontSize: 14,
                            color: QuickCartTheme.textSecondary,
                          ),
                        ),
                        const SizedBox(height: 16),

                        // Price Row & Discount Badge
                        Row(
                          children: [
                            Text(
                              '₹${productToDisplay.price.toStringAsFixed(0)}',
                              style: const TextStyle(
                                fontSize: 26,
                                fontWeight: FontWeight.bold,
                                color: QuickCartTheme.textPrimary,
                              ),
                            ),
                            const SizedBox(width: 12),
                            if (productToDisplay.originalPrice > productToDisplay.price) ...[
                              Text(
                                '₹${productToDisplay.originalPrice.toStringAsFixed(0)}',
                                style: const TextStyle(
                                  fontSize: 16,
                                  color: QuickCartTheme.textLight,
                                  decoration: TextDecoration.lineThrough,
                                ),
                              ),
                              const SizedBox(width: 12),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                decoration: BoxDecoration(
                                  color: QuickCartTheme.fastOrange,
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: Text(
                                  '${productToDisplay.discountPercentage}% OFF',
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 12,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                            ],
                          ],
                        ),

                        const Divider(height: 32),

                        // Delivery Promise Card
                        Container(
                          padding: const EdgeInsets.all(14),
                          decoration: BoxDecoration(
                            color: QuickCartTheme.surfaceContainerLow,
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: const Row(
                            children: [
                              Icon(Icons.bolt, color: QuickCartTheme.primaryGreen, size: 24),
                              SizedBox(width: 12),
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Superfast 15-Minute Delivery',
                                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                                  ),
                                  Text(
                                    'Delivering from nearest QuickCart dark store',
                                    style: TextStyle(color: QuickCartTheme.textSecondary, fontSize: 12),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),

                        const SizedBox(height: 24),

                        // Product Description
                        const Text(
                          'Product Details',
                          style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          productToDisplay.description,
                          style: const TextStyle(
                            fontSize: 14,
                            color: QuickCartTheme.textSecondary,
                            height: 1.5,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Bottom Action Bar (Add to Cart / Buy Now)
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
            child: Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () {
                      cart.addItem(productToDisplay);
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('Added ${productToDisplay.name} to cart'),
                          duration: const Duration(seconds: 1),
                        ),
                      );
                    },
                    child: Text(currentQty > 0 ? 'Add More ($currentQty)' : 'Add to Cart'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ElevatedButton(
                    onPressed: () {
                      if (currentQty == 0) {
                        cart.addItem(productToDisplay);
                      }
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const CartScreen()),
                      );
                    },
                    child: const Text('Buy Now'),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
