import 'package:flutter/material.dart';
import '../../theme.dart';
import 'delivery_preferences_screen.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          ListTile(
            leading: const Icon(Icons.notifications_outlined, color: QuickCartTheme.primaryGreen),
            title: const Text('Notifications'),
            subtitle: const Text('Order updates & promo alerts'),
            trailing: Switch(value: true, onChanged: (_) {}),
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.local_shipping_outlined, color: QuickCartTheme.primaryGreen),
            title: const Text('Delivery Preferences'),
            subtitle: const Text('Time slot, leave at door, instructions'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const DeliveryPreferencesScreen()),
              );
            },
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.lock_outline, color: QuickCartTheme.primaryGreen),
            title: const Text('Privacy & Security'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {},
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.help_outline, color: QuickCartTheme.primaryGreen),
            title: const Text('Help & Support'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {},
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.info_outline, color: QuickCartTheme.primaryGreen),
            title: const Text('About QuickCart'),
            subtitle: const Text('Version 1.0.0'),
          ),
        ],
      ),
    );
  }
}
