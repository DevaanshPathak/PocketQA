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
          _userModel = profile;
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
