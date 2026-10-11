import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:integration_test/integration_test.dart';
import 'package:mobile_app/main.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Walk register -> consent -> dashboard -> file a claim -> diagnostics', (tester) async {
    runApp(const ProviderScope(child: PayMuApp()));
    await tester.pumpAndSettle();

    // ----------------------------------------------------
    // Step 1: Register
    // ----------------------------------------------------
    debugPrint('[STEP 1] Testing Register Screen...');
    expect(find.text('Register Vehicle'), findsOneWidget);
    await tester.tap(find.text('Register Vehicle'));
    await tester.pumpAndSettle(const Duration(seconds: 4));

    // ----------------------------------------------------
    // Step 2: Consent
    // ----------------------------------------------------
    debugPrint('[STEP 2] Testing Consent Screen...');
    expect(find.text('Data Sharing Preferences'), findsOneWidget);

    // Verify toggles start OFF
    final switches = tester.widgetList<Switch>(find.byType(Switch)).toList();
    expect(switches.length, 2);
    expect(switches[0].value, isFalse, reason: 'Usage tracking starts OFF');
    expect(switches[1].value, isFalse, reason: 'Maintenance tracking starts OFF');

    // Turn both toggles ON
    await tester.tap(find.byType(Switch).first);
    await tester.pumpAndSettle();
    await tester.tap(find.byType(Switch).last);
    await tester.pumpAndSettle();

    // Submit consent
    await tester.tap(find.text('Continue to Quote'));
    await tester.pumpAndSettle(const Duration(seconds: 4));

    // Should arrive at Baseline Quote step
    expect(find.text('Your Baseline Quote'), findsOneWidget);
    await tester.tap(find.text('Go to Dashboard'));
    await tester.pumpAndSettle(const Duration(seconds: 4));

    // ----------------------------------------------------
    // Step 3: Dashboard
    // ----------------------------------------------------
    debugPrint('[STEP 3] Testing Dashboard Screen...');
    expect(find.text('Your Premium Quote'), findsOneWidget);
    expect(find.text('Usage Score'), findsOneWidget);
    expect(find.text('Maintenance Score'), findsOneWidget);

    // ----------------------------------------------------
    // Step 4: File a Claim (FNOL)
    // ----------------------------------------------------
    debugPrint('[STEP 4] Testing File a Claim...');
    // Tap Claims tab in navigation bar
    await tester.tap(find.byIcon(Icons.assignment));
    await tester.pumpAndSettle(const Duration(seconds: 2));

    expect(find.text('File a New Claim (FNOL)'), findsOneWidget);

    // Fill FNOL form:
    // Select cause dropdown
    await tester.tap(find.byType(DropdownButtonFormField<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('mechanical_failure').last);
    await tester.pumpAndSettle();

    // Select date
    await tester.tap(find.text('Select Date'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('OK'));
    await tester.pumpAndSettle();

    // Enter description
    await tester.enterText(
      find.widgetWithText(TextField, ''),
      'Engine overheating on highway and transmission slipped.',
    );
    await tester.pumpAndSettle();

    // Add photo
    await tester.tap(find.text('Add Photo Reference'));
    await tester.pumpAndSettle();

    // Submit claim
    final scrollable = find.byType(Scrollable).first;
    await tester.scrollUntilVisible(find.text('Submit Claim'), 50.0, scrollable: scrollable);
    await tester.tap(find.text('Submit Claim'));
    await tester.pumpAndSettle(const Duration(seconds: 5));

    // Should show success snackbar
    expect(find.textContaining('Claim submitted successfully'), findsOneWidget);

    // ----------------------------------------------------
    // Step 5: Diagnostics
    // ----------------------------------------------------
    debugPrint('[STEP 5] Testing Diagnostics Screen...');
    // Go back to dashboard
    await tester.tap(find.byIcon(Icons.dashboard));
    await tester.pumpAndSettle(const Duration(seconds: 2));

    // Scroll down to Diagnostics shortcut
    final dashScrollable = find.byType(Scrollable).first;
    await tester.scrollUntilVisible(find.text('Vehicle Diagnostics'), 50.0, scrollable: dashScrollable);
    await tester.tap(find.text('Vehicle Diagnostics'));
    await tester.pumpAndSettle(const Duration(seconds: 2));

    expect(find.text('Vehicle Diagnostics'), findsOneWidget);
    expect(find.text('Enter Manually'), findsOneWidget);

    // Test Manual Entry
    await tester.tap(find.text('Enter Manually'));
    await tester.pumpAndSettle();

    expect(find.text('Enter Diagnostics Manually'), findsOneWidget);
    // Select "No" (no warning light)
    await tester.tap(find.text('No'));
    await tester.pumpAndSettle();

    // Submit reading
    await tester.tap(find.text('Submit Reading'));
    await tester.pumpAndSettle(const Duration(seconds: 4));

    expect(find.text('Manual entry submitted successfully'), findsOneWidget);
    debugPrint('[SUCCESS] Walk completed with 0 red-screen errors!');
  });
}
