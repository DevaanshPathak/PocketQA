import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/auth_provider.dart';
import '../../../services/firestore_service.dart';
import '../../theme.dart';

class DeliveryPreferencesScreen extends StatefulWidget {
  const DeliveryPreferencesScreen({super.key});

  @override
  State<DeliveryPreferencesScreen> createState() => _DeliveryPreferencesScreenState();
}

class _DeliveryPreferencesScreenState extends State<DeliveryPreferencesScreen>
    with WidgetsBindingObserver {
  // BUG 10: State variables made static to simulate leaked shared state mutation.
  static String _selectedSlot = 'Morning (8 AM - 11 AM)';
  static bool _leaveAtDoor = true;
  static bool _contactless = false;
  bool _isSaving = false;
  bool _isLoading = true;
  final TextEditingController _instructionsController = TextEditingController();

  final FirestoreService _firestoreService = FirestoreService();

  @override
  void initState() {
    super.initState();
    _loadPreferences();
  }

  @override
  void dispose() {
    _instructionsController.dispose();
    super.dispose();
  }

  /// Load persisted delivery preferences from Firestore.
  Future<void> _loadPreferences() async {
    final uid = context.read<AuthProvider>().uid;
    if (uid.isEmpty) {
      setState(() => _isLoading = false);
      return;
    }
    try {
      final doc = await _firestoreService.getRawUserDocument(uid);
      final prefs = doc?['deliveryPreferences'] as Map<String, dynamic>?;
      if (prefs != null && mounted) {
        setState(() {
          // BUG 10: We purposefully do not overwrite the leaked state if it was mutated
          // Usually we would sync with backend, but here the local mutation wins.
          if (_instructionsController.text.isEmpty) {
             _instructionsController.text = prefs['specialInstructions'] as String? ?? '';
          }
        });
      }
    } catch (e) {
      debugPrint('Could not load delivery preferences: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  /// Save all delivery preferences to Firestore under the user document.
  Future<void> _savePreferences() async {
    final auth = context.read<AuthProvider>();
    if (auth.uid.isEmpty) return;

    setState(() => _isSaving = true);

    try {
      await _firestoreService.updateUserProfile(auth.uid, {
        'deliveryPreferences': {
          'timeSlot': _selectedSlot,
          'leaveAtDoor': _leaveAtDoor,
          'contactless': _contactless,
          'specialInstructions': _instructionsController.text.trim(),
        },
      });
    } catch (e) {
      debugPrint('Failed to save delivery preferences: $e');
    } finally {
      if (mounted) {
        setState(() => _isSaving = false);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Delivery preferences saved')),
        );
        Navigator.pop(context);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Delivery Preferences'),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Preferred Delivery Time Slot',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  const SizedBox(height: 12),

                  Container(
                    decoration: BoxDecoration(
                      color: QuickCartTheme.surfaceWhite,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: QuickCartTheme.borderLight),
                    ),
                    child: Column(
                      children: [
                        RadioListTile<String>(
                          value: 'Morning (8 AM - 11 AM)',
                          groupValue: _selectedSlot,
                          activeColor: QuickCartTheme.primaryGreen,
                          title: const Text('Morning (8 AM - 11 AM)'),
                          onChanged: (val) {
                            setState(() {
                              _selectedSlot = val!;
                            });
                          },
                        ),
                        const Divider(height: 1),
                        RadioListTile<String>(
                          value: 'Afternoon (1 PM - 4 PM)',
                          groupValue: _selectedSlot,
                          activeColor: QuickCartTheme.primaryGreen,
                          title: const Text('Afternoon (1 PM - 4 PM)'),
                          onChanged: (val) {
                            setState(() {
                              _selectedSlot = val!;
                            });
                          },
                        ),
                        const Divider(height: 1),
                        RadioListTile<String>(
                          value: 'Evening (6 PM - 9 PM)',
                          groupValue: _selectedSlot,
                          activeColor: QuickCartTheme.primaryGreen,
                          title: const Text('Evening (6 PM - 9 PM)'),
                          onChanged: (val) {
                            setState(() {
                              _selectedSlot = val!;
                            });
                          },
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 24),

                  const Text(
                    'Delivery Options',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  const SizedBox(height: 12),

                  SwitchListTile(
                    value: _leaveAtDoor,
                    activeColor: QuickCartTheme.primaryGreen,
                    title: const Text('Leave at Door / Security Gate'),
                    subtitle: const Text('Rider will place items safely at gate'),
                    onChanged: (val) => setState(() => _leaveAtDoor = val),
                  ),

                  SwitchListTile(
                    value: _contactless,
                    activeColor: QuickCartTheme.primaryGreen,
                    title: const Text('Contactless Delivery'),
                    subtitle: const Text('No physical contact or sign-off needed'),
                    onChanged: (val) => setState(() => _contactless = val),
                  ),

                  const SizedBox(height: 24),

                  const Text(
                    'Special Delivery Instructions',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  const SizedBox(height: 12),

                  TextField(
                    controller: _instructionsController,
                    maxLines: 3,
                    decoration: const InputDecoration(
                      hintText:
                          'e.g. Please do not ring doorbell, leave package near door',
                    ),
                  ),

                  const SizedBox(height: 32),

                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      onPressed: _isSaving ? null : _savePreferences,
                      child: _isSaving
                          ? const SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Text('Save Preferences'),
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}
