import 'package:flutter_test/flutter_test.dart';
import 'package:pocketqa/main.dart';

void main() {
  test('demo mode opens the QuickCart shell without an auth session', () {
    expect(
      shouldShowMainNavigation(demoMode: true, authenticated: false),
      isTrue,
    );
  });

  test('normal mode still requires an authenticated session', () {
    expect(
      shouldShowMainNavigation(demoMode: false, authenticated: false),
      isFalse,
    );
  });
}
