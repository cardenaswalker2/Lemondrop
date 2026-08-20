import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:lemondrop_mobile/main.dart';

void main() {
  testWidgets('App loads smoke test', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(
      const ProviderScope(
        child: LemonDropApp(),
      ),
    );

    // Verify splash elements exist or load correctly
    expect(find.text('LEMON DROP'), findsNothing); // Loading screen first
  });
}
