import 'dart:io';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter/foundation.dart';

class StorageService {
  final FirebaseStorage _storage = FirebaseStorage.instance;

  Future<String?> uploadProfilePhoto({
    required String uid,
    required File imageFile,
  }) async {
    try {
      final ref = _storage.ref().child('users/$uid/profile/profile.jpg');
      final uploadTask = await ref.putFile(imageFile);
      final downloadUrl = await uploadTask.ref.getDownloadURL();
      return downloadUrl;
    } catch (e) {
      debugPrint('StorageService uploadProfilePhoto error: $e');
      return null;
    }
  }
}
