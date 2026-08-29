import 'dart:io';
import '../services/firestore_service.dart';
import '../services/storage_service.dart';
import '../models/user_model.dart';

class UserRepository {
  final FirestoreService _firestoreService = FirestoreService();
  final StorageService _storageService = StorageService();

  Stream<UserModel?> streamProfile(String uid) {
    return _firestoreService.streamUserProfile(uid);
  }

  Future<UserModel?> getProfile(String uid) async {
    return await _firestoreService.getUserProfile(uid);
  }

  Future<void> updateProfile(String uid, Map<String, dynamic> data) async {
    await _firestoreService.updateUserProfile(uid, data);
  }

  Future<String?> uploadProfilePhoto(String uid, File file) async {
    final photoUrl = await _storageService.uploadProfilePhoto(uid: uid, imageFile: file);
    if (photoUrl != null) {
      await updateProfile(uid, {'photoUrl': photoUrl});
    }
    return photoUrl;
  }
}
