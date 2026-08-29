import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/product_provider.dart';
import '../../../models/product_model.dart';
import '../../theme.dart';
import '../../widgets/product_card.dart';
import '../product/product_details_screen.dart';


class CategoryScreen extends StatelessWidget {
  const CategoryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final productProvider = context.watch<ProductProvider>();
    final categories = productProvider.categories;
    final products = productProvider.filteredProducts;

    // Generate extended list of products to guarantee 20+ items for scrolling & PocketQA boundary testing
    final List<ProductModel> extendedProducts = products.isEmpty
        ? []
        : List.generate(
            24,
            (index) {
              final base = products[index % products.length];
              return base.copyWith(
                id: '${base.id}_ext_$index',
                name: '${base.name} (Pack ${index + 1})',
              );
            },
          );

    return Scaffold(
      appBar: AppBar(
        title: const Text('Categories & Catalogue'),
      ),
      body: Column(
        children: [
          // Category Selector Chips Header
          Container(
            height: 50,
            padding: const EdgeInsets.symmetric(vertical: 4),
            color: QuickCartTheme.surfaceWhite,
            child: ListView.builder(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              itemCount: categories.length + 1,
              itemBuilder: (context, index) {
                if (index == 0) {
                  final isSelected = productProvider.selectedCategoryId == null;
                  return Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: FilterChip(
                      label: const Text('All Categories'),
                      selected: isSelected,
                      selectedColor: QuickCartTheme.primaryGreen.withOpacity(0.2),
                      checkmarkColor: QuickCartTheme.primaryGreen,
                      onSelected: (_) => productProvider.selectCategory(null),
                    ),
                  );
                }

                final cat = categories[index - 1];
                final isSelected = productProvider.selectedCategoryId == cat.id;

                return Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: FilterChip(
                    label: Text(cat.name),
                    selected: isSelected,
                    selectedColor: QuickCartTheme.primaryGreen.withOpacity(0.2),
                    checkmarkColor: QuickCartTheme.primaryGreen,
                    onSelected: (_) => productProvider.selectCategory(cat.id),
                  ),
                );
              },
            ),
          ),

          const Divider(height: 1),

          // Scrollable Grid
          Expanded(
            child: productProvider.isLoading
                ? const Center(child: CircularProgressIndicator())
                : extendedProducts.isEmpty
                    ? const Center(
                        child: Text(
                          'No products available in this category',
                          style: TextStyle(color: QuickCartTheme.textSecondary),
                        ),
                      )
                    : GridView.builder(
                        padding: const EdgeInsets.fromLTRB(16, 16, 16, 80),
                        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          childAspectRatio: 0.68,
                          crossAxisSpacing: 12,
                          mainAxisSpacing: 12,
                        ),
                        // BUG 06: Final list item off-by-one boundary crash
                        itemCount: extendedProducts.length + 1,
                        itemBuilder: (context, index) {
                          // Crash occurs here when index == extendedProducts.length
                          final prod = extendedProducts[index];

                          return ProductCard(
                            product: prod,
                            onTap: () {
                              Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => ProductDetailsScreen(product: prod),
                                ),
                              );
                            },
                          );
                        },
                      ),
          ),
        ],
      ),
    );
  }
}
