import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/product_provider.dart';
import '../../theme.dart';
import '../../widgets/product_card.dart';
import '../search/search_screen.dart';
import '../product/product_details_screen.dart';
import '../experimental/low_semantics_screen.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final productProvider = context.watch<ProductProvider>();
    final categories = productProvider.categories;
    final products = productProvider.filteredProducts;

    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async {},
          child: CustomScrollView(
            slivers: [
              // Top Header (Logo + Location Delivery Banner)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Container(
                                width: 28,
                                height: 28,
                                decoration: const BoxDecoration(
                                  color: QuickCartTheme.primaryGreen,
                                  shape: BoxShape.circle,
                                ),
                                child: const Icon(Icons.flash_on, size: 18, color: Colors.white),
                              ),
                              const SizedBox(width: 8),
                              const Text(
                                'QuickCart',
                                style: TextStyle(
                                  fontSize: 22,
                                  fontWeight: FontWeight.extrabold,
                                  color: QuickCartTheme.primaryDarkGreen,
                                  letterSpacing: -0.5,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          const Row(
                            children: [
                              Icon(Icons.location_on, size: 14, color: QuickCartTheme.primaryGreen),
                              SizedBox(width: 4),
                              Text(
                                'Indiranagar, Bengaluru • 15 mins',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                  color: QuickCartTheme.textSecondary,
                                ),
                              ),
                              Icon(Icons.keyboard_arrow_down, size: 16, color: QuickCartTheme.textSecondary),
                            ],
                          ),
                        ],
                      ),
                      IconButton(
                        onPressed: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(builder: (_) => const LowSemanticsScreen()),
                          );
                        },
                        tooltip: 'VLM Fresh Picks',
                        icon: const Icon(Icons.remove_red_eye_outlined, color: QuickCartTheme.primaryDarkGreen),
                      ),
                    ],
                  ),
                ),
              ),

              // Search Bar Trigger
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: InkWell(
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const SearchScreen()),
                      );
                    },
                    borderRadius: BorderRadius.circular(12),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                      decoration: BoxDecoration(
                        color: QuickCartTheme.surfaceWhite,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: QuickCartTheme.borderLight),
                      ),
                      child: const Row(
                        children: [
                          Icon(Icons.search, color: QuickCartTheme.primaryGreen),
                          SizedBox(width: 12),
                          Text(
                            'Search groceries, fruits, snacks...',
                            style: TextStyle(color: QuickCartTheme.textLight, fontSize: 14),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),

              // Promotional Banner
              SliverToBoxAdapter(
                child: Container(
                  margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [QuickCartTheme.primaryGreen, QuickCartTheme.primaryDarkGreen],
                    ),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Row(
                    children: [
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Fresh Groceries, Delivered Fast ⚡',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            SizedBox(height: 4),
                            Text(
                              'Get up to 20% OFF on daily essentials',
                              style: TextStyle(
                                color: Colors.white70,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.2),
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(Icons.shopping_basket, color: Colors.white, size: 28),
                      ),
                    ],
                  ),
                ),
              ),

              // Categories Header
              const SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.fromLTRB(16, 16, 16, 8),
                  child: Text(
                    'Shop by Category',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: QuickCartTheme.textPrimary,
                    ),
                  ),
                ),
              ),

              // Horizontal Categories List
              SliverToBoxAdapter(
                child: SizedBox(
                  height: 44,
                  child: ListView.builder(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    itemCount: categories.length + 1,
                    itemBuilder: (context, index) {
                      if (index == 0) {
                        final isSelected = productProvider.selectedCategoryId == null;
                        return Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          child: ChoiceChip(
                            label: const Text('All'),
                            selected: isSelected,
                            selectedColor: QuickCartTheme.primaryGreen,
                            labelStyle: TextStyle(
                              color: isSelected ? Colors.white : QuickCartTheme.textPrimary,
                              fontWeight: FontWeight.bold,
                            ),
                            onSelected: (_) {
                              productProvider.selectCategory(null);
                            },
                          ),
                        );
                      }

                      final cat = categories[index - 1];
                      final isSelected = productProvider.selectedCategoryId == cat.id;

                      return Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: ChoiceChip(
                          label: Text(cat.name),
                          selected: isSelected,
                          selectedColor: QuickCartTheme.primaryGreen,
                          labelStyle: TextStyle(
                            color: isSelected ? Colors.white : QuickCartTheme.textPrimary,
                            fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                          ),
                          onSelected: (_) {
                            productProvider.selectCategory(cat.id);
                          },
                        ),
                      );
                    },
                  ),
                ),
              ),

              // Popular Products Header
              const SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.fromLTRB(16, 20, 16, 12),
                  child: Text(
                    'Popular Near You',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: QuickCartTheme.textPrimary,
                    ),
                  ),
                ),
              ),

              // Products Grid
              if (productProvider.isLoading)
                const SliverFillRemaining(
                  child: Center(child: CircularProgressIndicator()),
                )
              else if (products.isEmpty)
                const SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.all(32),
                    child: Center(
                      child: Text('No products available in this category'),
                    ),
                  ),
                )
              else
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 80),
                  sliver: SliverGrid(
                    gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: 2,
                      childAspectRatio: 0.68,
                      crossAxisSpacing: 12,
                      mainAxisSpacing: 12,
                    ),
                    delegate: SliverChildBuilderDelegate(
                      (context, index) {
                        final prod = products[index];
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
                      childCount: products.length,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
