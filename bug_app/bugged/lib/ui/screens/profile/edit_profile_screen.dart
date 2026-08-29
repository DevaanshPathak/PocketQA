import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/auth_provider.dart';
import '../../../config/bug_fixtures.dart';
import '../../theme.dart';

class EditProfileScreen extends StatefulWidget {
  const EditProfileScreen({super.key});

  @override
  State<EditProfileScreen> createState() => _EditProfileScreenState();
}

class _EditProfileScreenState extends State<EditProfileScreen> {
  late TextEditingController _nameController;
  late TextEditingController _emailController;
  late TextEditingController _phoneController;

  // Bug 1 state variables: Nullable profile photo state
  String? _selectedPhotoPath = 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300';
  bool _photoSelectionCancelled = false;

  @override
  void initState() {
    super.initState();
    final user = context.read<AuthProvider>().user;
    _nameController = TextEditingController(text: user?.displayName.isNotEmpty == true ? user!.displayName : 'Sagar Mehta');
    _emailController = TextEditingController(text: user?.email.isNotEmpty == true ? user!.email : 'sagar@example.com');
    _phoneController = TextEditingController(text: user?.phoneNumber.isNotEmpty == true ? user!.phoneNumber : '+91 9876543210');
  }

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  // Opens Mock Photo Picker dialog
  void _openPhotoPicker() async {
    final result = await showModalBottomSheet<String?>(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (context) {
        return Container(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Select Profile Photo',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 16),
              ListTile(
                leading: const Icon(Icons.photo_library, color: QuickCartTheme.primaryGreen),
                title: const Text('Choose from Gallery'),
                onTap: () => Navigator.pop(context, 'gallery_photo.jpg'),
              ),
              ListTile(
                leading: const Icon(Icons.camera_alt, color: QuickCartTheme.primaryGreen),
                title: const Text('Take a Photo'),
                onTap: () => Navigator.pop(context, 'camera_photo.jpg'),
              ),
              const Divider(),
              ListTile(
                leading: const Icon(Icons.cancel, color: Colors.red),
                title: const Text('Cancel Selection'),
                onTap: () => Navigator.pop(context, null), // Returns null on Cancel
              ),
            ],
          ),
        );
      },
    );

    if (result == null) {
      // User tapped Cancel in mock photo picker
      setState(() {
        _selectedPhotoPath = null; // Photo selection leaves state null
        _photoSelectionCancelled = true;
        BugFixtures.photoCancelled = true;
        BugFixtures.photoUrlState = null;
      });
    } else {
      setState(() {
        _selectedPhotoPath = result;
        _photoSelectionCancelled = false;
        BugFixtures.photoCancelled = false;
      });
    }
  }

  void _saveChanges() {
    // BUG 1: Null-Safety Crash Trigger
    // When photo selection was cancelled, _selectedPhotoPath is null.
    // Explicitly dereferencing _selectedPhotoPath! without null check throws Null Check Operator Exception
    if (_photoSelectionCancelled || _selectedPhotoPath == null) {
      // Trigger deterministic Null Safety Exception for PocketQA
      BugFixtures.triggerBug1NullPhotoSave();
      
      // Stable line crash: Forced null check operator dereference
      final String photoPath = _selectedPhotoPath!; 
      debugPrint('Saved photo path: $photoPath');
    }

    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Profile updated successfully')),
    );
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Edit Profile'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            // Profile Photo with Change/Remove buttons
            Center(
              child: Column(
                children: [
                  CircleAvatar(
                    radius: 50,
                    backgroundColor: QuickCartTheme.primaryGreen.withOpacity(0.15),
                    backgroundImage: _selectedPhotoPath != null ? NetworkImage(_selectedPhotoPath!) : null,
                    child: _selectedPhotoPath == null
                        ? const Icon(Icons.person_off_outlined, size: 48, color: Colors.grey)
                        : null,
                  ),
                  const SizedBox(height: 12),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      TextButton.icon(
                        onPressed: _openPhotoPicker,
                        icon: const Icon(Icons.camera_alt_outlined, size: 18),
                        label: const Text('Change Photo'),
                      ),
                      if (_selectedPhotoPath != null)
                        TextButton.icon(
                          onPressed: () {
                            setState(() {
                              _selectedPhotoPath = null;
                              _photoSelectionCancelled = true;
                              BugFixtures.photoCancelled = true;
                              BugFixtures.photoUrlState = null;
                            });
                          },
                          icon: const Icon(Icons.delete_outline, size: 18, color: Colors.red),
                          label: const Text('Remove', style: TextStyle(color: Colors.red)),
                        ),
                    ],
                  ),
                ],
              ),
            ),

            const SizedBox(height: 24),

            // Profile Fields
            TextField(
              controller: _nameController,
              decoration: const InputDecoration(
                labelText: 'Full Name',
                prefixIcon: Icon(Icons.person_outline),
              ),
            ),
            const SizedBox(height: 16),

            TextField(
              controller: _emailController,
              keyboardType: TextInputType.emailAddress,
              decoration: const InputDecoration(
                labelText: 'Email Address',
                prefixIcon: Icon(Icons.email_outlined),
              ),
            ),
            const SizedBox(height: 16),

            TextField(
              controller: _phoneController,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(
                labelText: 'Phone Number',
                prefixIcon: Icon(Icons.phone_outlined),
              ),
            ),

            const SizedBox(height: 32),

            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text('Cancel'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ElevatedButton(
                    onPressed: _saveChanges,
                    child: const Text('Save Changes'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
