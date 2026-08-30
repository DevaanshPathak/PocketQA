package com.indium.pocketqa.controller

data class LocalDiagnosis(
    val sourceKey: String,
    val cause: String,
    val reproduction: String,
    val diff: String,
)

/** Deterministic, offline diagnosis for each deliberate Buggy App issue. */
object KnownBugCatalog {
    fun diagnose(finding: BugFinding): LocalDiagnosis = when {
        finding.title.contains("Rapid cart quantity", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/providers/cart_provider.dart",
            cause = "Concurrent delayed quantity updates commit stale snapshots after a newer mutation.",
            reproduction = "Add an item, burst-tap + and -, leave Cart, then reopen it and compare quantity with the total.",
            diff = """--- a/lib/providers/cart_provider.dart
+++ b/lib/providers/cart_provider.dart
@@
- final currentQty = _items[existingIndex].quantity;
- await Future.delayed(const Duration(milliseconds: 600));
- _items[existingIndex] = existing.copyWith(quantity: currentQty + 1);
+ _items[existingIndex] = _items[existingIndex].copyWith(quantity: _items[existingIndex].quantity + 1);
""".trimIndent(),
        )
        finding.title.contains("Rapid search", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/providers/product_provider.dart",
            cause = "An older asynchronous search completion overwrites results for the current query.",
            reproduction = "Search milk, bread, apple, chips, then milk without waiting between entries.",
            diff = """--- a/lib/providers/product_provider.dart
+++ b/lib/providers/product_provider.dart
@@
+ final requestQuery = query;
  await Future.delayed(...);
- _staleSearchResults = results;
+ if (requestQuery == _searchQuery) _staleSearchResults = results;
""".trimIndent(),
        )
        finding.title.contains("Checkout total stale", ignoreCase = true) ||
            finding.title.contains("Checkout summary stale", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/ui/screens/checkout/checkout_screen.dart",
            cause = "Checkout caches the first cart snapshot and reuses it after the authoritative cart changes.",
            reproduction = "Open Checkout, go back to Cart, change an item quantity, then open Checkout again.",
            diff = """--- a/lib/ui/screens/checkout/checkout_screen.dart
+++ b/lib/ui/screens/checkout/checkout_screen.dart
@@
- final cartItems = _cachedCartSnapshot ?? <CartItemModel>[];
- final totalPayable = _cachedTotalSnapshot ?? 0.0;
+ final cartItems = cart.items;
+ final totalPayable = cart.totalAmount;
""".trimIndent(),
        )
        finding.title.contains("Async product load", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/ui/screens/product/product_details_screen.dart",
            cause = "A delayed detail request can update global product state after its route is no longer active.",
            reproduction = "Open Product A, immediately go back, open Product B, then wait for both requests.",
            diff = """--- a/lib/ui/screens/product/product_details_screen.dart
+++ b/lib/ui/screens/product/product_details_screen.dart
@@
  await Future.delayed(...);
+ if (!mounted || requestId != _activeRequestId) return;
  setState(() => _loadedProduct = product);
""".trimIndent(),
        )
        finding.title.contains("quantity", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = if (finding.evidence.contains("QuickCart", ignoreCase = true)) {
                "lib/providers/cart_provider.dart"
            } else "lib/state/cart_provider.dart",
            cause = "The decrement action accepts zero and decrements it again, allowing a negative quantity.",
            reproduction = "Add an item, open the cart, then tap Decrease quantity twice.",
            diff = """--- a/lib/state/cart_provider.dart
+++ b/lib/state/cart_provider.dart
@@
- quantity -= 1;
+ quantity = (quantity - 1).clamp(0, quantity);
""".trimIndent(),
        )
        finding.title.contains("Final list", ignoreCase = true) || finding.title.contains("off-by-one", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/ui/screens/category/category_screen.dart",
            cause = "The grid advertises one more child than the 24-item extended catalogue contains, so the final builder index is out of range.",
            reproduction = "Open Categories, scroll to the final card, then let the grid request its last child.",
            diff = """--- a/lib/ui/screens/category/category_screen.dart
+++ b/lib/ui/screens/category/category_screen.dart
@@
- itemCount: extendedProducts.length + 1,
+ itemCount: extendedProducts.length,
""".trimIndent(),
        )
        finding.title.contains("double save", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/ui/screens/profile/edit_profile_screen.dart",
            cause = "Save starts a delayed asynchronous mutation without an in-flight guard, allowing two concurrent submissions.",
            reproduction = "Edit Profile, change a field, then tap Save Changes twice before the first delay completes.",
            diff = """--- a/lib/ui/screens/profile/edit_profile_screen.dart
+++ b/lib/ui/screens/profile/edit_profile_screen.dart
@@
+ if (_isSaving) return;
+ setState(() => _isSaving = true);
  await auth.updateProfile(...);
""".trimIndent(),
        )
        finding.title.contains("Filter changes", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/ui/screens/search/search_screen.dart",
            cause = "The list's cached length only grows, so a filtered result set can be indexed past its end.",
            reproduction = "Search a broad term, then narrow the filter until only a few products remain and scroll.",
            diff = """--- a/lib/ui/screens/search/search_screen.dart
+++ b/lib/ui/screens/search/search_screen.dart
@@
- itemCount: _cachedLength,
+ itemCount: products.length,
""".trimIndent(),
        )
        finding.title.contains("Profile edit controller", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/ui/screens/profile/edit_profile_screen.dart",
            cause = "Text controllers are recreated in build, leaking or discarding draft form state across edits.",
            reproduction = "Edit a name, cancel, reopen Edit Profile, alter only the email, and save.",
            diff = """--- a/lib/ui/screens/profile/edit_profile_screen.dart
+++ b/lib/ui/screens/profile/edit_profile_screen.dart
@@
+ @override void initState() {
+   super.initState();
+   _nameController = TextEditingController(text: user.displayName);
+ }
- _nameController = TextEditingController(...);
""".trimIndent(),
        )
        finding.title.contains("Cancelled form", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/ui/screens/settings/delivery_preferences_screen.dart",
            cause = "Delivery preference controls mutate static shared state before Save, so Cancel cannot discard edits.",
            reproduction = "Toggle Leave at Door, tap Cancel, then reopen Delivery Preferences.",
            diff = """--- a/lib/ui/screens/settings/delivery_preferences_screen.dart
+++ b/lib/ui/screens/settings/delivery_preferences_screen.dart
@@
- static bool _leaveAtDoor = true;
+ bool _draftLeaveAtDoor = _savedLeaveAtDoor;
@@
+ if (saved) _savedLeaveAtDoor = _draftLeaveAtDoor;
""".trimIndent(),
        )
        finding.title.contains("Double place-order", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/ui/screens/checkout/checkout_screen.dart",
            cause = "Place Order is still actionable while its first asynchronous order creation is running.",
            reproduction = "Open Checkout and tap Place Order twice before the confirmation view appears.",
            diff = """--- a/lib/ui/screens/checkout/checkout_screen.dart
+++ b/lib/ui/screens/checkout/checkout_screen.dart
@@
- onPressed: () async {
+ onPressed: orderProvider.isPlacingOrder ? null : () async {
""".trimIndent(),
        )
        finding.title.contains("hitbox", ignoreCase = true) || finding.title.contains("visual", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/ui/screens/experimental/low_semantics_screen.dart",
            cause = "A low-semantics product card resolves its visible Add control to the adjacent item's data.",
            reproduction = "Open Profile > Fresh Picks, tap the visible Bananas Add button, and compare the confirmation item.",
            diff = """--- a/lib/ui/screens/experimental/low_semantics_screen.dart
+++ b/lib/ui/screens/experimental/low_semantics_screen.dart
@@
- final added = items[(index + 1) % items.length];
+ final added = items[index];
""".trimIndent(),
        )
        finding.title.contains("Login authentication", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/ui/screens/auth/login_screen.dart",
            cause = "Login permits an empty password past local validation and marks the attempt successful.",
            reproduction = "Enter an email, clear Password, and tap Sign In.",
            diff = """--- a/lib/ui/screens/auth/login_screen.dart
+++ b/lib/ui/screens/auth/login_screen.dart
@@
- if (email.isEmpty) {
+ if (email.isEmpty || password.isEmpty) {
    showValidationError();
    return;
  }
""".trimIndent(),
        )
        finding.title.contains("Third", ignoreCase = true) || finding.title.contains("Catalog products", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/ui/screens/catalog_screen.dart",
            cause = "The catalog render path drops the final product card.",
            reproduction = "Open the catalog and inspect the rendered product cards.",
            diff = """--- a/lib/ui/screens/catalog_screen.dart
+++ b/lib/ui/screens/catalog_screen.dart
@@
- itemCount: products.length - 1,
+ itemCount: products.length,
""".trimIndent(),
        )
        finding.title.contains("validation", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "lib/ui/screens/checkout_screen.dart",
            cause = "Checkout submits before required fields are validated.",
            reproduction = "Open checkout and tap Place Order with every field empty.",
            diff = """--- a/lib/ui/screens/checkout_screen.dart
+++ b/lib/ui/screens/checkout_screen.dart
@@
- onPressed: submitOrder,
+ onPressed: formKey.currentState!.validate() ? submitOrder : null,
""".trimIndent(),
        )
        else -> LocalDiagnosis(
            sourceKey = "lib/ui/screens/cart_screen.dart",
            cause = "PocketQA observed an unexpected UI state during the known test trace.",
            reproduction = finding.evidence,
            diff = "// Inspect the local source excerpt and apply the smallest guard for this state.",
        )
    }
}
