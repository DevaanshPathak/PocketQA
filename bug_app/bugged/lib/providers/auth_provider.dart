import 'dart:async';
import 'package:flutter/material.dart';
import 'package:firebase_auth/firebase_auth.dart';
import '../repositories/auth_repository.dart';
import '../repositories/user_repository.dart';
import '../models/user_model.dart';

class AuthProvider extends ChangeNotifier {
  final AuthRepository _authRepository = AuthRepository();
  final UserRepository _userRepository = UserRepository();

  User? _firebaseUser;
  UserModel? _userModel;
  bool _isLoading = true;
  String? _errorMessage;

  StreamSubscription<User?>? _authSubscription;
  StreamSubscription<UserModel?>? _userSubscription;

  AuthProvider() {
    _firebaseUser = _authRepository.currentUser;
    if (_firebaseUser != null) {
      _userModel = UserModel(
        uid: _firebaseUser!.uid,
        displayName: _firebaseUser!.displayName ?? (_firebaseUser!.email?.isNotEmpty == true ? _firebaseUser!.email!.split('@').first : 'User'),
        email: _firebaseUser!.email ?? '',
        phone: _firebaseUser!.phoneNumber ?? '',
        photoUrl: _firebaseUser!.photoURL ?? '',
        createdAt: DateTime.now(),
        updatedAt: DateTime.now(),
      );
    }
    // A cached Firebase session must never block the offline/demo catalogue on
    // a Firestore profile round trip. The stream below enriches this local
    // profile when available, but the app can render immediately either way.
    _isLoading = false;
    _initAuthStream();
  }

  User? get firebaseUser => _firebaseUser;
  UserModel? get userModel => _userModel;
  UserModel? get user => _userModel;
  bool get isAuthenticated => _firebaseUser != null;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  String get uid => _firebaseUser?.uid ?? '';

  void _initAuthStream() {
    _authSubscription = _authRepository.authStateChanges.listen((user) {
      _firebaseUser = user;
      _userSubscription?.cancel();
      if (user != null) {
        _userSubscription = _userRepository.streamProfile(user.uid).listen((profile) {
          if (profile != null) {
            _userModel = profile;
          } else {
            final defaultUser = UserModel(
              uid: user.uid,
              displayName: user.displayName ?? (user.email?.isNotEmpty == true ? user.email!.split('@').first : 'User'),
              email: user.email ?? '',
              phone: user.phoneNumber ?? '',
              photoUrl: user.photoURL ?? '',
              createdAt: DateTime.now(),
              updatedAt: DateTime.now(),
            );
            _userModel = defaultUser;
            _userRepository.updateProfile(user.uid, defaultUser.toMap());
          }
          _isLoading = false;
          notifyListeners();
        });
      } else {
        _userModel = null;
        _isLoading = false;
        notifyListeners();
      }
    });
  }

  Future<bool> signIn(String email, String password) async {
    _setLoading(true);
    try {
      await _authRepository.signIn(email: email, password: password);
      _setLoading(false);
      return true;
    } catch (e) {
      _setError(e.toString());
      _setLoading(false);
      return false;
    }
  }

  Future<bool> signInWithEmail({required String email, required String password}) =>
      signIn(email, password);

  Future<bool> signUp(String displayName, String email, String password) async {
    _setLoading(true);
    try {
      await _authRepository.signUp(
        displayName: displayName,
        email: email,
        password: password,
      );
      _setLoading(false);
      return true;
    } catch (e) {
      _setError(e.toString());
      _setLoading(false);
      return false;
    }
  }

  Future<bool> signUpWithEmail({
    required String email,
    required String password,
    required String name,
  }) =>
      signUp(name, email, password);

  Future<bool> updateProfile({
    String? displayName,
    String? email,
    String? phone,
    String? photoUrl,
    bool clearPhoto = false,
  }) async {
    final currentUid = uid;
    final data = <String, dynamic>{};
    if (displayName != null) data['displayName'] = displayName;
    if (email != null) data['email'] = email;
    if (phone != null) data['phone'] = phone;
    // Always persist photoUrl changes — including removal (empty string = no photo).
    if (clearPhoto) {
      data['photoUrl'] = '';
    } else if (photoUrl != null) {
      data['photoUrl'] = photoUrl;
    }

    if (currentUid.isNotEmpty) {
      try {
        await _userRepository.updateProfile(currentUid, data);
      } catch (e) {
        debugPrint('Firestore profile update error: $e');
      }
      // Update Firebase Auth email if changed.
      // verifyBeforeUpdateEmail sends a verification link; the email updates
      // in Firebase Auth only after the user clicks it.
      if (email != null && _firebaseUser != null && email != _firebaseUser!.email) {
        try {
          await _firebaseUser!.verifyBeforeUpdateEmail(email);
          debugPrint('Verification email sent to $email. Auth email updates after confirmation.');
        } catch (e) {
          debugPrint('Firebase Auth email update error: $e');
        }
      }
    }

    _userModel = (_userModel ??
            UserModel(
              uid: currentUid.isNotEmpty ? currentUid : 'user_1',
              displayName: displayName ?? '',
              email: email ?? '',
              phone: phone ?? '',
              photoUrl: clearPhoto ? '' : (photoUrl ?? ''),
              createdAt: DateTime.now(),
              updatedAt: DateTime.now(),
            ))
        .copyWith(
      displayName: displayName,
      email: email,
      phone: phone,
      // Pass null explicitly when clearing, otherwise pass the new URL.
      photoUrl: clearPhoto ? null : photoUrl,
    );
    notifyListeners();
    return true;
  }

  Future<bool> signInAnonymously() async {
    _setLoading(true);
    try {
      await _authRepository.signInAnonymously();
      _setLoading(false);
      return true;
    } catch (e) {
      _setError(e.toString());
      _setLoading(false);
      return false;
    }
  }

  Future<void> signOut() async {
    await _authRepository.signOut();
  }

  void _setLoading(bool value) {
    _isLoading = value;
    notifyListeners();
  }

  void _setError(String? message) {
    _errorMessage = message;
    notifyListeners();
  }

  @override
  void dispose() {
    _authSubscription?.cancel();
    _userSubscription?.cancel();
    super.dispose();
  }
}
