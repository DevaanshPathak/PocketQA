import 'package:flutter/material.dart';
import '../../theme.dart';
import '../../widgets/app_bottom_navigation.dart';
import '../auth/login_screen.dart';

class WelcomeScreen extends StatelessWidget {
  const WelcomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: QuickCartTheme.surfaceWhite,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          child: Column(
            children: [
              const Spacer(),

              // Logo & Illustration Banner
              Container(
                width: 120,
                height: 120,
                decoration: BoxDecoration(
                  color: QuickCartTheme.primaryGreen.withOpacity(0.12),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.shopping_bag_outlined,
                  size: 64,
                  color: QuickCartTheme.primaryGreen,
                ),
              ),
              const SizedBox(height: 32),

              // Title & Subtitle
              const Text(
                'QuickCart',
                style: TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.bold,
                  color: QuickCartTheme.primaryDarkGreen,
                  letterSpacing: -0.5,
                ),
              ),
              const SizedBox(height: 12),
              const Text(
                'Groceries at your doorstep',
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w600,
                  color: QuickCartTheme.textPrimary,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                'Fresh groceries and daily essentials delivered quickly in 15 minutes.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 14,
                  color: QuickCartTheme.textSecondary,
                  height: 1.4,
                ),
              ),

              const SizedBox(height: 24),

              // Location Chip
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                decoration: BoxDecoration(
                  color: QuickCartTheme.surfaceContainerLow,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.location_on, size: 18, color: QuickCartTheme.primaryGreen),
                    SizedBox(width: 6),
                    Text(
                      'Delivering to Indiranagar, Bengaluru',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: QuickCartTheme.textPrimary,
                      ),
                    ),
                  ],
                ),
              ),

              const Spacer(),

              // Action Buttons
              ElevatedButton(
                onPressed: () {
                  Navigator.pushReplacement(
                    context,
                    MaterialPageRoute(builder: (_) => const MainNavigationShell()),
                  );
                },
                child: const Text('Start Shopping'),
              ),
              const SizedBox(height: 12),
              OutlinedButton(
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const LoginScreen()),
                  );
                },
                child: const Text('Login / Sign Up'),
              ),
              const SizedBox(height: 16),
            ],
          ),
        ),
      ),
    );
  }
}
