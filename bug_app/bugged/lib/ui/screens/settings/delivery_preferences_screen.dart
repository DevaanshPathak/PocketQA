import 'package:flutter/material.dart';
import '../../theme.dart';

class DeliveryPreferencesScreen extends StatefulWidget {
  const DeliveryPreferencesScreen({super.key});

  @override
  State<DeliveryPreferencesScreen> createState() => _DeliveryPreferencesScreenState();
}

class _DeliveryPreferencesScreenState extends State<DeliveryPreferencesScreen> with WidgetsBindingObserver {
  String _selectedSlot = 'Morning (8 AM - 11 AM)';
  bool _leaveAtDoor = true;
  bool _contactless = false;
  final TextEditingController _instructionsController = TextEditingController();

  // Bug 2 state tracker: Stateful controller invalidated during lifecycle state changes
  bool _isLifecycleInvalidated = false;
  late _DisposedStateController _stateController;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _stateController = _DisposedStateController();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    // When app is backgrounded/paused or returned, state becomes invalidated
    if (state == AppLifecycleState.paused || state == AppLifecycleState.inactive || state == AppLifecycleState.resumed) {
      _isLifecycleInvalidated = true;
      _stateController.dispose(); // State controller disposed on lifecycle transition
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _instructionsController.dispose();
    super.dispose();
  }

  void _savePreferences() {
    // BUG 2: Lifecycle / State Crash Trigger
    // Accessing _stateController after app lifecycle state transition/disposal throws StateError
    if (_isLifecycleInvalidated || _stateController.isDisposed) {
      // Stable line crash: Accessing disposed state controller after lifecycle change
      _stateController.updateState('SAVED');
    }

    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Delivery preferences saved')),
    );
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Delivery Preferences'),
      ),
      body: SingleChildScrollView(
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
                        // Modifying preference state flags lifecycle state check
                        _isLifecycleInvalidated = true;
                        _stateController.dispose();
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
                        _isLifecycleInvalidated = true;
                        _stateController.dispose();
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
                        _isLifecycleInvalidated = true;
                        _stateController.dispose();
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
                hintText: 'e.g. Please do not ring doorbell, leave package near door',
              ),
            ),

            const SizedBox(height: 32),

            ElevatedButton(
              onPressed: _savePreferences,
              child: const Text('Save Preferences'),
            ),
          ],
        ),
      ),
    );
  }
}

class _DisposedStateController {
  bool isDisposed = false;

  void dispose() {
    isDisposed = true;
  }

  void updateState(String value) {
    if (isDisposed) {
      throw StateError('Cannot access disposed state controller after lifecycle state change.');
    }
  }
}
