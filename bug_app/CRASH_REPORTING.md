# PocketQA crash-report contract

The Buggy App exposes its latest crash report at
`content://com.pocketqa.pocketqa.crashes/latest`. PocketQA reads it with the
signature-protected permission
`com.pocketqa.pocketqa.permission.READ_CRASH_REPORT`.

From Dart, report an uncaught error through the native storage bridge:

```dart
const _channel = MethodChannel('com.pocketqa.pocketqa/crash_reports');

await _channel.invokeMethod<void>('reportCrash', {
  'reportJson': jsonEncode(report),
});
```

The report must use schema version 1 and the JSON structure documented in
`../PLAN.md`. The first frame's `sourceKey` must start with `lib/` and must be
one of the source keys that PocketQA bundles in its local corpus.

## Current demo issue IDs

| `bugId` | Source key | Trigger |
| --- | --- | --- |
| `catalog-third-item-null` | `lib/ui/screens/catalog_screen.dart` | Open Catalog and render the third product |
| `cart-negative-quantity` | `lib/state/cart_provider.dart` | Add one item; decrease it twice |
| `checkout-missing-validation` | `lib/ui/screens/checkout_screen.dart` | Submit an empty checkout form |
| `checkout-submit-stays-enabled` | `lib/ui/screens/checkout_screen.dart` | Submit a completed checkout form |
| `promo-freeze` | `lib/ui/screens/cart_screen.dart` | Enter `FREEZE`, then tap APPLY |

`bugId` will be included in the crash JSON once a crash is deliberately wired
to one of these scenarios. Non-crash findings use this same catalog through
PocketQA's semantics and timeout detectors.
