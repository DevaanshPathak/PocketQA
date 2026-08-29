import 'dart:async';
import 'package:flutter/material.dart';
import '../repositories/product_repository.dart';
import '../models/category_model.dart';
import '../models/product_model.dart';
import '../tool/seed_firestore.dart';

/// ProductProvider manages the product catalogue for the QuickCart app.
///
/// Strategy:
/// 1. Always populate from hardcoded data immediately (UI never blocks).
/// 2. Once auth is confirmed, attempt to load Firestore data in the background.
/// 3. If Firestore has data → use it; if empty → seed it silently.
/// 4. If Firestore errors → stay on hardcoded data, no crash, no spinner.
class ProductProvider extends ChangeNotifier {
  final ProductRepository _productRepository = ProductRepository();

  List<CategoryModel> _categories = [];
  List<ProductModel> _allProducts = [];
  String? _selectedCategoryId;
  String _searchQuery = '';
  bool _isLoading = false; // Always false — fallback loads synchronously.

  StreamSubscription<List<CategoryModel>>? _categorySub;
  StreamSubscription<List<ProductModel>>? _productSub;

  bool _seeded = false;
  String? _userId;

  ProductProvider() {
    _loadHardcodedCatalogue();
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  List<CategoryModel> get categories => _categories;
  String? get selectedCategoryId => _selectedCategoryId;
  String get searchQuery => _searchQuery;
  bool get isLoading => _isLoading;

  List<ProductModel> get filteredProducts {
    List<ProductModel> list = _allProducts;
    if (_selectedCategoryId != null && _selectedCategoryId!.isNotEmpty) {
      list = list.where((p) => p.categoryId == _selectedCategoryId).toList();
    }
    if (_searchQuery.isNotEmpty) {
      list = _productRepository.searchProducts(list, _searchQuery);
    }
    return list;
  }

  void selectCategory(String? categoryId) {
    _selectedCategoryId = categoryId;
    notifyListeners();
  }

  void setSearchQuery(String query) {
    _searchQuery = query;
    notifyListeners();
  }

  /// Called by ChangeNotifierProxyProvider whenever the auth UID changes.
  /// Starts Firestore real-time sync only when authenticated.
  void updateUserId(String? uid) {
    if (_userId == uid) return;
    _userId = uid;
    _cancelStreams();
    if (uid != null && uid.isNotEmpty) {
      _startFirestoreSync();
    }
  }

  @override
  void dispose() {
    _cancelStreams();
    super.dispose();
  }

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  void _cancelStreams() {
    _categorySub?.cancel();
    _productSub?.cancel();
    _categorySub = null;
    _productSub = null;
  }

  /// Attempts to start Firestore streams. All failures are silently caught —
  /// the UI already has hardcoded data and will never show an empty state
  /// due to Firestore issues.
  void _startFirestoreSync() {
    // Product stream — simple query, no composite index needed.
    _productSub = _productRepository.streamProducts().listen(
      (prodList) {
        if (prodList.isNotEmpty) {
          _allProducts = prodList;
          notifyListeners();
        } else if (!_seeded) {
          // Firestore products collection is empty — seed it once.
          _seeded = true;
          _seedFirestoreQuietly();
        }
        // If empty AND already seeded, fallback data is already displayed.
      },
      onError: (e) {
        // Permission error or missing index — fallback data is already shown.
        debugPrint('[ProductProvider] Firestore product stream error: $e');
      },
    );
  }

  /// Seeds Firestore in the background. Failures are swallowed so the UI
  /// is never affected.
  Future<void> _seedFirestoreQuietly() async {
    try {
      await seedQuickCartData();
      debugPrint('[ProductProvider] Firestore seeding complete.');
    } catch (e) {
      debugPrint('[ProductProvider] Firestore seeding skipped (will use local data): $e');
      // Reset so seeding is retried on the next app launch.
      _seeded = false;
    }
  }

  // ---------------------------------------------------------------------------
  // Hardcoded catalogue — always available, no network dependency
  // ---------------------------------------------------------------------------

  void _loadHardcodedCatalogue() {
    _categories = [
      CategoryModel(id: 'cat_fruits_veg', name: 'Fruits & Vegetables', description: 'Fresh organic fruits and vegetables', imageUrl: 'https://images.unsplash.com/photo-1610832958506-aa56368176cf?w=300', iconName: 'local_grocery_store', sortOrder: 1, isActive: true),
      CategoryModel(id: 'cat_dairy_eggs', name: 'Dairy & Eggs', description: 'Fresh milk, cheese, butter and farm eggs', imageUrl: 'https://images.unsplash.com/photo-1528498033373-3c6c08e93d79?w=300', iconName: 'egg', sortOrder: 2, isActive: true),
      CategoryModel(id: 'cat_bakery', name: 'Bakery & Bread', description: 'Freshly baked breads, buns and cakes', imageUrl: 'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=300', iconName: 'bakery_dining', sortOrder: 3, isActive: true),
      CategoryModel(id: 'cat_snacks', name: 'Snacks & Munchies', description: 'Chips, biscuits, nuts and savory snacks', imageUrl: 'https://images.unsplash.com/photo-1599490659213-e2b9527bd087?w=300', iconName: 'fastfood', sortOrder: 4, isActive: true),
      CategoryModel(id: 'cat_beverages', name: 'Beverages & Juices', description: 'Refreshing drinks, fruit juices and sodas', imageUrl: 'https://images.unsplash.com/photo-1622483767028-3f66f32aef97?w=300', iconName: 'local_cafe', sortOrder: 5, isActive: true),
      CategoryModel(id: 'cat_staples', name: 'Foodgrains & Staples', description: 'Rice, wheat flour, pulses and cooking oils', imageUrl: 'https://images.unsplash.com/photo-1586201375761-83865001e31c?w=300', iconName: 'grain', sortOrder: 6, isActive: true),
      CategoryModel(id: 'cat_personal_care', name: 'Personal Care', description: 'Soaps, shampoos and skincare essentials', imageUrl: 'https://images.unsplash.com/photo-1556228720-195a672e8a03?w=300', iconName: 'sanitizer', sortOrder: 7, isActive: true),
      CategoryModel(id: 'cat_household', name: 'Household Essentials', description: 'Cleaners, detergents and paper towels', imageUrl: 'https://images.unsplash.com/photo-1584622650111-993a426fbf0a?w=300', iconName: 'cleaning_services', sortOrder: 8, isActive: true),
    ];

    _allProducts = [
      // Fruits & Vegetables
      ProductModel(id: 'prod_bananas', name: 'Organic Fresh Bananas', description: 'Sweet, ripe organic bananas sourced from local farms.', categoryId: 'cat_fruits_veg', brand: 'FreshDay', quantityLabel: '1 kg (approx 6-7 pcs)', price: 2.49, originalPrice: 2.99, discountPercentage: 16, imageUrl: 'https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=300', isAvailable: true, stockQuantity: 100),
      ProductModel(id: 'prod_apples', name: 'Crisp Red Apples', description: 'Juicy and crunchy red apples packed with vitamins.', categoryId: 'cat_fruits_veg', brand: 'FarmPure', quantityLabel: '4 pcs', price: 3.99, originalPrice: 4.49, discountPercentage: 11, imageUrl: 'https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?w=300', isAvailable: true, stockQuantity: 80),
      ProductModel(id: 'prod_tomatoes', name: 'Vine-Ripe Tomatoes', description: 'Fresh red tomatoes perfect for salads and cooking.', categoryId: 'cat_fruits_veg', brand: 'FarmFresh', quantityLabel: '500g', price: 1.99, originalPrice: 2.49, discountPercentage: 20, imageUrl: 'https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=300', isAvailable: true, stockQuantity: 60),
      ProductModel(id: 'prod_spinach', name: 'Fresh Baby Spinach', description: 'Tender, washed baby spinach leaves rich in iron.', categoryId: 'cat_fruits_veg', brand: 'FreshDay', quantityLabel: '250g pack', price: 2.29, originalPrice: 2.79, discountPercentage: 18, imageUrl: 'https://images.unsplash.com/photo-1576045057995-568f588f82fb?w=300', isAvailable: true, stockQuantity: 45),
      // Dairy & Eggs
      ProductModel(id: 'prod_milk', name: 'Whole Farm Milk', description: 'Pasteurized 100% pure cow milk.', categoryId: 'cat_dairy_eggs', brand: 'DailyDrop', quantityLabel: '1 Gallon', price: 3.49, originalPrice: 3.99, discountPercentage: 12, imageUrl: 'https://images.unsplash.com/photo-1563636619-e9143da7973b?w=300', isAvailable: true, stockQuantity: 120),
      ProductModel(id: 'prod_eggs', name: 'Large Free-Range Eggs', description: 'Grade A large eggs from free-range hens.', categoryId: 'cat_dairy_eggs', brand: 'FarmPure', quantityLabel: '12 pcs carton', price: 4.29, originalPrice: 4.99, discountPercentage: 14, imageUrl: 'https://images.unsplash.com/photo-1582722872445-44dc5f7e3c8f?w=300', isAvailable: true, stockQuantity: 90),
      ProductModel(id: 'prod_butter', name: 'Salted Creamery Butter', description: 'Rich and creamy butter made from pure milk cream.', categoryId: 'cat_dairy_eggs', brand: 'DailyDrop', quantityLabel: '250g block', price: 2.99, originalPrice: 3.49, discountPercentage: 14, imageUrl: 'https://images.unsplash.com/photo-1589985270826-4b7bb135bc9d?w=300', isAvailable: true, stockQuantity: 50),
      // Bakery
      ProductModel(id: 'prod_bread', name: 'Whole Wheat Sliced Bread', description: 'Soft and wholesome 100% whole grain wheat bread.', categoryId: 'cat_bakery', brand: 'DailyBake', quantityLabel: '400g loaf', price: 2.79, originalPrice: 3.19, discountPercentage: 12, imageUrl: 'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=300', isAvailable: true, stockQuantity: 75),
      ProductModel(id: 'prod_croissant', name: 'Butter Croissants', description: 'Flaky, buttery French-style baked croissants.', categoryId: 'cat_bakery', brand: 'DailyBake', quantityLabel: '4 pcs pack', price: 4.49, originalPrice: 5.29, discountPercentage: 15, imageUrl: 'https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=300', isAvailable: true, stockQuantity: 40),
      // Snacks
      ProductModel(id: 'prod_chips', name: 'Classic Salted Potato Chips', description: 'Crispy potato chips seasoned with sea salt.', categoryId: 'cat_snacks', brand: 'CrunchMaster', quantityLabel: '150g bag', price: 1.89, originalPrice: 2.19, discountPercentage: 13, imageUrl: 'https://images.unsplash.com/photo-1566478989037-eec170784d0b?w=300', isAvailable: true, stockQuantity: 110),
      // Beverages
      ProductModel(id: 'prod_orange_juice', name: '100% Pure Orange Juice', description: 'Freshly squeezed pulp-free orange juice.', categoryId: 'cat_beverages', brand: 'JuiceBliss', quantityLabel: '1 Liter bottle', price: 3.99, originalPrice: 4.49, discountPercentage: 11, imageUrl: 'https://images.unsplash.com/photo-1621506289937-a8e4df240d0b?w=300', isAvailable: true, stockQuantity: 65),
      // Staples
      ProductModel(id: 'prod_rice', name: 'Premium Basmati Rice', description: 'Aromatic long-grain basmati rice.', categoryId: 'cat_staples', brand: 'GrainGold', quantityLabel: '5 kg bag', price: 12.99, originalPrice: 14.99, discountPercentage: 13, imageUrl: 'https://images.unsplash.com/photo-1586201375761-83865001e31c?w=300', isAvailable: true, stockQuantity: 50),
      // Personal Care
      ProductModel(id: 'prod_shampoo', name: 'Nourishing Herbal Shampoo', description: 'Gentle hair care with natural botanicals.', categoryId: 'cat_personal_care', brand: 'PureGlow', quantityLabel: '400 ml', price: 5.99, originalPrice: 6.99, discountPercentage: 14, imageUrl: 'https://images.unsplash.com/photo-1556228720-195a672e8a03?w=300', isAvailable: true, stockQuantity: 40),
      // Household
      ProductModel(id: 'prod_cleaner', name: 'Multi-Surface Cleaner', description: 'Effective citrus disinfectant spray.', categoryId: 'cat_household', brand: 'CleanSpark', quantityLabel: '750 ml bottle', price: 3.29, originalPrice: 3.99, discountPercentage: 17, imageUrl: 'https://images.unsplash.com/photo-1584622650111-993a426fbf0a?w=300', isAvailable: true, stockQuantity: 60),
    ];
    // _isLoading stays false — data is ready immediately.
  }
}
