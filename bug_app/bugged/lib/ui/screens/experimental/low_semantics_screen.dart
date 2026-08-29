import 'package:flutter/material.dart';
import '../../theme.dart';

class LowSemanticsScreen extends StatelessWidget {
  const LowSemanticsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final items = [
      {'name': 'Fresh Organic Bananas', 'price': '₹49', 'qty': '1 kg', 'color': Colors.amber.shade100},
      {'name': 'Whole Farm Milk', 'price': '₹65', 'qty': '1 L', 'color': Colors.blue.shade100},
      {'name': 'Crisp Red Apples', 'price': '₹120', 'qty': '4 pcs', 'color': Colors.red.shade100},
      {'name': 'Whole Wheat Bread', 'price': '₹45', 'qty': '400g', 'color': Colors.orange.shade100},
      {'name': 'Farm Fresh Eggs', 'price': '₹85', 'qty': '6 pcs', 'color': Colors.yellow.shade100},
      {'name': 'Vine Ripe Tomatoes', 'price': '₹35', 'qty': '500g', 'color': Colors.red.shade200},
    ];

    return Scaffold(
      appBar: AppBar(
        title: const Text('Fresh Picks'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 8),

            // EXCLUDE SEMANTICS BLOCK: Intentionally hides accessibility nodes from standard inspection tree
            ExcludeSemantics(
              child: GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2,
                  childAspectRatio: 0.72,
                  crossAxisSpacing: 12,
                  mainAxisSpacing: 12,
                ),
                itemCount: items.length,
                itemBuilder: (context, index) {
                  final item = items[index];

                  return Container(
                    decoration: BoxDecoration(
                      color: QuickCartTheme.surfaceWhite,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: QuickCartTheme.borderLight),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Container(
                            color: item['color'] as Color,
                            child: Center(
                              child: Icon(
                                Icons.shopping_bag,
                                size: 48,
                                color: Colors.black.withOpacity(0.3),
                              ),
                            ),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                item['name'] as String,
                                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                                maxLines: 1,
                              ),
                              Text(
                                item['qty'] as String,
                                style: const TextStyle(fontSize: 11, color: QuickCartTheme.textSecondary),
                              ),
                              const SizedBox(height: 8),
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Text(
                                    item['price'] as String,
                                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                                  ),
                                  ElevatedButton(
                                    style: ElevatedButton.styleFrom(
                                      minimumSize: const Size(60, 32),
                                      padding: const EdgeInsets.symmetric(horizontal: 12),
                                    ),
                                    onPressed: () {
                                      // BUG 15: the visual card and the action target diverge.
                                      // The control looks attached to this card, but resolves
                                      // the following item from the underlying hit region.
                                      final added = items[(index + 1) % items.length];
                                      ScaffoldMessenger.of(context).showSnackBar(
                                        SnackBar(content: Text('Added ${added['name']} to cart')),
                                      );
                                    },
                                    child: const Text('ADD', style: TextStyle(fontSize: 12)),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
