import 'dart:async';
import 'package:flutter/material.dart';
import '../repositories/category_repository.dart';
import '../repositories/product_repository.dart';
import '../models/category_model.dart';
import '../models/product_model.dart';
import '../tool/seed_firestore.dart';

class ProductProvider extends ChangeNotifier {
  final CategoryRepository _categoryRepository = CategoryRepository();
  final ProductRepository _productRepository = ProductRepository();

  List<CategoryModel> _categories = [];
  List<ProductModel> _allProducts = [];
  String? _selectedCategoryId;
  String _searchQuery = '';
  bool _isLoading = true;

  StreamSubscription<List<CategoryModel>>? _categorySub;
  StreamSubscription<List<ProductModel>>? _productSub;

  ProductProvider() {
    _initStreams();
  }

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

  void _initStreams() {
    _categorySub = _categoryRepository.streamCategories().listen((catList) async {
      _categories = catList;
      if (_categories.isEmpty) {
        debugPrint('Categories empty in Firestore, auto-seeding...');
        await seedQuickCartData();
      }
      notifyListeners();
    });

    _productSub = _productRepository.streamProducts().listen((prodList) {
      _allProducts = prodList;
      _isLoading = false;
      notifyListeners();
    });
  }

  void selectCategory(String? categoryId) {
    _selectedCategoryId = categoryId;
    notifyListeners();
  }

  void setSearchQuery(String query) {
    _searchQuery = query;
    notifyListeners();
  }

  @override
  void dispose() {
    _categorySub?.cancel();
    _productSub?.cancel();
    super.dispose();
  }
}
