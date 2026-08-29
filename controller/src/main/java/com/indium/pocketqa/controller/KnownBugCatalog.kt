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
            sourceKey = "lib/state/cart_provider.dart",
            cause = "The decrement action accepts zero and decrements it again, allowing a negative quantity.",
            reproduction = "Add an item, open the cart, then tap Decrease quantity twice.",
            diff = """--- a/lib/state/cart_provider.dart
+++ b/lib/state/cart_provider.dart
@@
- quantity -= 1;
+ quantity = (quantity - 1).clamp(0, quantity);
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
