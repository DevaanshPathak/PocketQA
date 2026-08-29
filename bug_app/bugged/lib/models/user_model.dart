import 'package:cloud_firestore/cloud_firestore.dart';

class UserModel {
  final String uid;
  final String displayName;
  final String email;
  final String phone;
  final String photoUrl;
  final DateTime createdAt;
  final DateTime updatedAt;
  final String? defaultAddressId;
  final bool isActive;

  UserModel({
    required this.uid,
    required this.displayName,
    required this.email,
    this.phone = '',
    this.photoUrl = '',
    required this.createdAt,
    required this.updatedAt,
    this.defaultAddressId,
    this.isActive = true,
  });

  factory UserModel.fromMap(Map<String, dynamic> map, String id) {
    return UserModel(
      uid: id,
      displayName: map['displayName'] as String? ?? '',
      email: map['email'] as String? ?? '',
      phone: map['phone'] as String? ?? '',
      photoUrl: map['photoUrl'] as String? ?? '',
      createdAt: (map['createdAt'] as Timestamp?)?.toDate() ?? DateTime.now(),
      updatedAt: (map['updatedAt'] as Timestamp?)?.toDate() ?? DateTime.now(),
      defaultAddressId: map['defaultAddressId'] as String?,
      isActive: map['isActive'] as bool? ?? true,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'uid': uid,
      'displayName': displayName,
      'email': email,
      'phone': phone,
      'photoUrl': photoUrl,
      'createdAt': Timestamp.fromDate(createdAt),
      'updatedAt': Timestamp.fromDate(updatedAt),
      'defaultAddressId': defaultAddressId,
      'isActive': isActive,
    };
  }

  UserModel copyWith({
    String? displayName,
    String? email,
    String? phone,
    String? photoUrl,
    String? defaultAddressId,
    bool? isActive,
  }) {
    return UserModel(
      uid: uid,
      displayName: displayName ?? this.displayName,
      email: email ?? this.email,
      phone: phone ?? this.phone,
      photoUrl: photoUrl ?? this.photoUrl,
      createdAt: createdAt,
      updatedAt: DateTime.now(),
      defaultAddressId: defaultAddressId ?? this.defaultAddressId,
      isActive: isActive ?? this.isActive,
    );
  }
}
