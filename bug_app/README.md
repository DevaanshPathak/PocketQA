# PocketQA Testbed: Grocery Delivery App

This application serves as a benchmark testbed for automated QA agents. It features two entry points:
- `lib/main_clean.dart`: A fully functional, stable application.
- `lib/main_buggy.dart`: An application with intentionally injected UI, logic, and runtime bugs.

## Firebase Setup & Configuration Guide

### 1. Create a Firebase Project
1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Click **Add project** and name it `pocketqa-testbed`.
3. (Optional) Enable Google Analytics and click **Create project**.

### 2. Configure Anonymous Authentication
1. In the Firebase Console, navigate to **Build > Authentication**.
2. Click **Get Started**.
3. Under **Sign-in method**, select **Anonymous**.
4. Enable the switch and click **Save**.

### 3. Set Up Cloud Firestore
1. Navigate to **Build > Firestore Database**.
2. Click **Create database**.
3. Select a location and start in **Test mode** (or set rules as below).
4. **Firestore Rules:**
```javascript
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### 4. Add Initial Data (Products)
Manually add these 3 documents to a collection named `products`:

**Document 1:**
- ID: `prod_1`
- Fields:
  - `title`: "Organic Bananas"
  - `price`: 2.99
  - `stock`: 50
  - `imageUrl`: "https://via.placeholder.com/150"

**Document 2:**
- ID: `prod_2`
- Fields:
  - `title`: "Fresh Milk"
  - `price`: 3.49
  - `stock`: 20
  - `imageUrl`: "https://via.placeholder.com/150"

**Document 3:**
- ID: `prod_3`
- Fields:
  - `title`: "Whole Wheat Bread"
  - `price`: 2.50
  - `stock`: 30
  - `imageUrl`: "https://via.placeholder.com/150"

### 5. Generate Firebase Options
Ensure you have the [FlutterFire CLI](https://firebase.flutter.dev/docs/cli/) installed. Run the following in your terminal:
```bash
flutterfire configure
```
This will generate `lib/firebase_options.dart`.

## Running the App
- **Clean Version:** `flutter run -t lib/main_clean.dart`
- **Buggy Version:** `flutter run -t lib/main_buggy.dart`
