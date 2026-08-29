import '../services/firestore_service.dart';
import '../models/category_model.dart';

class CategoryRepository {
  final FirestoreService _firestoreService = FirestoreService();

  Stream<List<CategoryModel>> streamCategories() {
    return _firestoreService.streamCategories();
  }

  Future<List<CategoryModel>> getCategories() async {
    return await _firestoreService.getCategories();
  }
}
