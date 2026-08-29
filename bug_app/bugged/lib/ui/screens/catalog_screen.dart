import 'package:flutter/material.dart';
import '../../services/firebase_service.dart';
import '../../models/product.dart';
import '../widgets/product_card.dart';
import '../../config/app_config.dart';

class CatalogScreen extends StatelessWidget {
  final FirebaseService _firebaseService = FirebaseService();

  CatalogScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final config = AppConfig.of(context)!;

    return Scaffold(
      appBar: AppBar(
        title: Text(config.appTitle),
        actions: [
          IconButton(
            icon: const Icon(Icons.shopping_cart),
            tooltip: 'Shopping cart',
            onPressed: () => Navigator.pushNamed(context, '/cart'),
          ),
        ],
      ),
      body: StreamBuilder<List<Product>>(
        stream: _firebaseService.getProducts(),
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(child: Text('Error: ${snapshot.error}'));
          }
          final products = snapshot.data ?? [];

          return GridView.builder(
            padding: const EdgeInsets.all(10),
            itemCount: products.length,
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              childAspectRatio: 3 / 4,
              crossAxisSpacing: 10,
              mainAxisSpacing: 10,
            ),
            itemBuilder: (ctx, i) {
              // UI Render Bug: For the third item, force unhandled null
              if (config.injectBugs && i == 2) {
                return Text(null as dynamic); // Trigger Red Screen
              }
              return ProductCard(product: products[i], isBuggy: config.injectBugs);
            },
          );
        },
      ),
    );
  }
}
