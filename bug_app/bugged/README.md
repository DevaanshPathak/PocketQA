# QuickCart — Buggy Grocery Application (PocketQA Test Target)

QuickCart is a full-featured, Indian quick-commerce grocery delivery application built using Flutter, Dart, and Firebase. It serves as the primary test target application for **PocketQA**, an autonomous QA and debugging agent.

## Package & Application ID
- **Package Name**: `com.quickcart.buggyapp`
- **Flutter SDK Constraint**: `>=3.13.1`
- **Target Platform**: Android

## Features & Architecture
- **Design System**: Material 3 implementation following the *Vibrant Velocity* design system (QuickCart Green `#22C55E`, Fast Orange `#F97316`).
- **Firebase Backend Integration**:
  - `FirebaseAuthService`: Email/password & Anonymous authentication.
  - `FirestoreService`: Real-time Firestore streams for categories, products, user cart, addresses, and orders.
  - `StorageService`: Profile photo upload support.
- **Provider State Management**: `AuthProvider`, `ProductProvider`, `CartProvider`, `OrderProvider`.
- **Automatic Data Seeding**: Automatically seeds categories and products to live Firestore on initial launch.

## Navigation & Screens
- **Welcome**: Splash screen with location header ("Indiranagar, Bengaluru") & CTA.
- **Auth**: Email/Password Sign in & Account creation + Guest login.
- **Home**: Banner, category chips, search trigger, popular product grid with instant cart togglers, sticky cart bar.
- **Search**: Live local search filtering products by title, category, and brand.
- **Categories**: Long scrollable product grid (20+ items) supporting PocketQA scroll testing.
- **Product Details**: High-res image, discount calculation, weight label, delivery promise, Add to Cart & Buy Now.
- **Cart**: Cart items list, quantity controls, bill breakdown (subtotal, delivery charge, handling fee, total).
- **Checkout**: Address selector, payment methods (UPI, Cash on Delivery, Card), order summary, place order.
- **Order Confirmation**: Animated checkmark, Order ID (`QC-XXXXX`), delivery timer.
- **Profile & Edit Profile**: User profile management.
- **Settings & Delivery Preferences**: Notification & delivery time slot settings.
- **Orders**: Historical order list.
- **Fresh Picks (Low-Semantics VLM Screen)**: Visual grid wrapped in `ExcludeSemantics` for PocketQA VLM fallback testing.

## PocketQA Integration & Planted Bugs
QuickCart contains three deterministic planted bug fixtures for PocketQA automated diagnosis testing:
1. **Bug 1 (Null Safety)**: Profile → Edit Profile → Change Photo → Cancel → Save.
2. **Bug 2 (Lifecycle/State)**: Settings → Delivery Preferences → Modify → Background/Lifecycle pause → Save.
3. **Bug 3 (List Index Range)**: Categories → Scroll to end of 20+ list → Tap boundary item.
4. **Low-Semantics VLM Screen**: `lib/ui/screens/experimental/low_semantics_screen.dart`.

For complete bug reproduction steps and expected stack traces, see [`BUG_FIXTURES.md`](BUG_FIXTURES.md).

## Running the Application
```bash
cd bug_app/bugged
flutter pub get
flutter run -t lib/main.dart
```
