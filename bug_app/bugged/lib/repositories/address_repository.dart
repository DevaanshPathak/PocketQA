import '../services/firestore_service.dart';
import '../models/address_model.dart';

class AddressRepository {
  final FirestoreService _firestoreService = FirestoreService();

  Stream<List<AddressModel>> streamAddresses(String uid) {
    return _firestoreService.streamAddresses(uid);
  }

  Future<void> addAddress(String uid, AddressModel address) async {
    await _firestoreService.addAddress(uid, address);
  }
}
