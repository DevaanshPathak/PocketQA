import '../services/firestore_service.dart';
import '../models/product_model.dart';

class ProductRepository {
  final FirestoreService _firestoreService = FirestoreService();

  Stream<List<ProductModel>> streamProducts() {
    return _firestoreService.streamProducts();
  }

  Stream<List<ProductModel>> streamProductsByCategory(String categoryId) {
    return _firestoreService.streamProductsByCategory(categoryId);
  }

  Future<ProductModel?> getProduct(String productId) async {
    return await _firestoreService.getProduct(productId);
  }

  List<ProductModel> searchProducts(List<ProductModel> products, String query) {
    final cleanQuery = query.toLowerCase().trim();
    if (cleanQuery.isEmpty) return products;
    return products.where((p) {
      return p.name.toLowerCase().contains(cleanQuery) ||
          p.brand.toLowerCase().contains(cleanQuery) ||
          p.description.toLowerCase().contains(cleanQuery);
    }).toList();
  }
}
