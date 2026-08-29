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
        finding.title.contains("quantity", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = if (finding.evidence.contains("QuickCart", ignoreCase = true)) {
                "bug_app/bugged/lib/providers/cart_provider.dart"
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
            sourceKey = "bug_app/bugged/lib/ui/screens/category/category_screen.dart",
            cause = "The grid advertises one more child than the 24-item extended catalogue contains, so the final builder index is out of range.",
            reproduction = "Open Categories, scroll to the final card, then let the grid request its last child.",
            diff = """--- a/bug_app/bugged/lib/ui/screens/category/category_screen.dart
+++ b/bug_app/bugged/lib/ui/screens/category/category_screen.dart
@@
- itemCount: extendedProducts.length + 1,
+ itemCount: extendedProducts.length,
""".trimIndent(),
        )
        finding.title.contains("double save", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "bug_app/bugged/lib/ui/screens/profile/edit_profile_screen.dart",
            cause = "Save starts a delayed asynchronous mutation without an in-flight guard, allowing two concurrent submissions.",
            reproduction = "Edit Profile, change a field, then tap Save Changes twice before the first delay completes.",
            diff = """--- a/bug_app/bugged/lib/ui/screens/profile/edit_profile_screen.dart
+++ b/bug_app/bugged/lib/ui/screens/profile/edit_profile_screen.dart
@@
+ if (_isSaving) return;
+ setState(() => _isSaving = true);
  await auth.updateProfile(...);
""".trimIndent(),
        )
        finding.title.contains("hitbox", ignoreCase = true) || finding.title.contains("visual", ignoreCase = true) -> LocalDiagnosis(
            sourceKey = "bug_app/bugged/lib/ui/screens/experimental/low_semantics_screen.dart",
            cause = "A low-semantics product card resolves its visible Add control to the adjacent item's data.",
            reproduction = "Open Profile > Fresh Picks, tap the visible Bananas Add button, and compare the confirmation item.",
            diff = """--- a/bug_app/bugged/lib/ui/screens/experimental/low_semantics_screen.dart
+++ b/bug_app/bugged/lib/ui/screens/experimental/low_semantics_screen.dart
@@
- final added = items[(index + 1) % items.length];
+ final added = items[index];
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
