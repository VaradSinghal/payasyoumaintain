import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:dio/dio.dart';
import 'package:integration_test/integration_test.dart';
import 'package:mobile_app/main.dart';

void dumpScreenText() {
  debugPrint('=== SCREEN TEXT DUMP START ===');
  for (final element in find.byType(Text).evaluate()) {
    final widget = element.widget as Text;
    if (widget.data != null && widget.data!.trim().isNotEmpty) {
      debugPrint('  Text: "${widget.data}"');
    }
  }
  debugPrint('=== SCREEN TEXT DUMP END ===');
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Live Stack E2E: scenarios a through k', (tester) async {
    final failures = <String>[];

    // Clean session setup
    debugPrint('[SETUP] Clearing secure storage...');
    const storage = FlutterSecureStorage();
    await storage.deleteAll();

    runApp(const ProviderScope(child: PayMuApp()));
    await tester.pumpAndSettle(const Duration(seconds: 3));

    // ----------------------------------------------------------------------
    // Scenario a: "Explore demo vehicles" -> "The Model Citizen": ₹13,977.60
    // ----------------------------------------------------------------------
    debugPrint('[SCENARIO A] Explore demo vehicles -> The Model Citizen');
    try {
      expect(find.text('Explore demo vehicles'), findsOneWidget);
      expect(find.text('The Model Citizen'), findsOneWidget);

      await tester.tap(find.text('The Model Citizen'));
      await tester.pumpAndSettle(const Duration(seconds: 5));

      expect(find.text('Your Premium Quote'), findsOneWidget);
      expect(find.text('₹13,977.60'), findsWidgets);
      debugPrint('[SCENARIO A] PASS: ₹13,977.60 displayed on dashboard');
    } catch (e) {
      debugPrint('[SCENARIO A] FAIL: $e');
      dumpScreenText();
      failures.add('Scenario a: $e');
    }

    // ----------------------------------------------------------------------
    // Scenario b: Privacy & tracking toggles OFF -> "Tracking is off" & ₹19,968.00
    // ----------------------------------------------------------------------
    debugPrint('[SCENARIO B] Turn toggles OFF in Privacy & tracking');
    try {
      // Find Privacy & tracking from "About this demo vehicle" card or overflow menu
      final privacyFinder = find.text('Privacy & tracking');
      if (privacyFinder.evaluate().isNotEmpty) {
        await tester.tap(privacyFinder.first);
      } else {
        await tester.tap(find.byType(PopupMenuButton<String>));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Privacy & tracking'));
      }
      await tester.pumpAndSettle(const Duration(seconds: 4));

      expect(find.text('Your Tracking Preferences'), findsOneWidget);

      // Turn both toggles OFF
      await tester.tap(find.byKey(const Key('usage_tracking_switch')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('maintenance_tracking_switch')));
      await tester.pumpAndSettle();

      // Save preferences
      await tester.tap(find.byKey(const Key('save_preferences_button')));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      // Go back to dashboard
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      // Scroll to tracking_off_card if needed (dashboard is a scrollable ListView)
      final dashScrollable = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.byKey(const Key('tracking_off_card')), 50.0, scrollable: dashScrollable);
      await tester.drag(dashScrollable, const Offset(0, -100));
      await tester.pumpAndSettle();

      // Dashboard checks
      expect(find.byKey(const Key('tracking_off_card')), findsOneWidget);
      expect(find.text('Tracking is off — standard pricing'), findsOneWidget);
      expect(find.text('₹19,968.00'), findsWidgets);
      expect(find.text('Usage Score'), findsNothing);
      expect(find.text('Maintenance Score'), findsNothing);
      debugPrint('[SCENARIO B] PASS: Tracking is off card and ₹19,968.00 shown; score bars absent');
    } catch (e) {
      debugPrint('[SCENARIO B] FAIL: $e');
      dumpScreenText();
      failures.add('Scenario b: $e');
    }

    // ----------------------------------------------------------------------
    // Scenario c: Turn both ON, save: ₹13,977.60 again
    // ----------------------------------------------------------------------
    debugPrint('[SCENARIO C] Turn toggles ON in Privacy & tracking');
    try {
      final dashScrollable = find.byType(Scrollable).first;
      final privacyBtn = find.byKey(const Key('privacy_tracking_button'));
      await tester.scrollUntilVisible(privacyBtn, 50.0, scrollable: dashScrollable);
      // Drag upwards to ensure button is above BottomNavigationBar
      await tester.drag(dashScrollable, const Offset(0, -150));
      await tester.pumpAndSettle();

      await tester.tap(privacyBtn);
      await tester.pumpAndSettle(const Duration(seconds: 4));

      expect(find.text('Your Tracking Preferences'), findsOneWidget);

      // Turn both toggles ON
      await tester.tap(find.byKey(const Key('usage_tracking_switch')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('maintenance_tracking_switch')));
      await tester.pumpAndSettle();

      // Save preferences
      await tester.tap(find.byKey(const Key('save_preferences_button')));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      // Go back to dashboard
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      // Dashboard checks
      expect(find.text('₹13,977.60'), findsWidgets);
      expect(find.text('Usage Score'), findsOneWidget);
      expect(find.text('Maintenance Score'), findsOneWidget);
      debugPrint('[SCENARIO C] PASS: ₹13,977.60 restored and score bars returned');
    } catch (e) {
      debugPrint('[SCENARIO C] FAIL: $e');
      dumpScreenText();
      failures.add('Scenario c: $e');
    }

    // ----------------------------------------------------------------------
    // Scenario d: "Why this score?": service record with OEM badge & trip
    // ----------------------------------------------------------------------
    debugPrint('[SCENARIO D] Open Why this score?');
    try {
      final dashScrollable = find.byType(Scrollable).first;
      final whyBtn = find.byKey(const Key('why_this_score_button'));
      await tester.scrollUntilVisible(whyBtn, 50.0, scrollable: dashScrollable);
      await tester.drag(dashScrollable, const Offset(0, -150));
      await tester.pumpAndSettle();

      await tester.tap(whyBtn);
      await tester.pumpAndSettle(const Duration(seconds: 4));

      expect(find.text('Why this score?'), findsOneWidget);
      expect(find.text('Service History'), findsOneWidget);
      expect(find.text('Recent Trips'), findsOneWidget);

      // Check trip aggregate present
      final tripFinder = find.textContaining('harsh braking');
      final historyScrollable = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(tripFinder.first, 50.0, scrollable: historyScrollable);
      expect(tripFinder, findsWidgets);

      // Scroll back up to check service record & OEM badge
      await tester.drag(historyScrollable, const Offset(0, 400));
      await tester.pumpAndSettle();

      // Check OEM badge
      final oemBadgeFinder = find.text('OEM');
      expect(oemBadgeFinder, findsWidgets, reason: 'Expected at least one service record with an OEM badge');
      debugPrint('[SCENARIO D] PASS: Service record with OEM badge and trips listed');
    } catch (e) {
      debugPrint('[SCENARIO D] FAIL: $e');
      dumpScreenText();
      failures.add('Scenario d: $e');
    } finally {
      // Return to dashboard
      final backButton = find.byType(BackButton);
      if (backButton.evaluate().isNotEmpty) {
        await tester.tap(backButton);
        await tester.pumpAndSettle(const Duration(seconds: 3));
      }
    }

    // ----------------------------------------------------------------------
    // Scenario e: Switch to "The Overdue Hatchback": recall banner & ₹12,265.34
    // ----------------------------------------------------------------------
    debugPrint('[SCENARIO E] Switch to The Overdue Hatchback');
    try {
      await tester.tap(find.byType(PopupMenuButton<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Switch vehicle'));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      // On onboarding screen, scroll to The Overdue Hatchback
      final scrollable = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.text('The Overdue Hatchback'), 50.0, scrollable: scrollable);
      await tester.tap(find.text('The Overdue Hatchback'));
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // Check recall banner and premium
      expect(find.text('URGENT: Your vehicle has an open safety recall.'), findsOneWidget);
      expect(find.text('₹12,265.34'), findsWidgets);
      debugPrint('[SCENARIO E] PASS: Recall banner and ₹12,265.34 displayed');
    } catch (e) {
      debugPrint('[SCENARIO E] FAIL: $e');
      dumpScreenText();
      failures.add('Scenario e: $e');
    }

    // ----------------------------------------------------------------------
    // Scenario f: Register new vehicle with demo details, toggles OFF -> ₹24,960.00
    //             Then toggles ON -> tracking-off card disappears
    // ----------------------------------------------------------------------
    debugPrint('[SCENARIO F] Register new vehicle through onboarding');
    try {
      // Reset session to clean onboarding
      await tester.tap(find.byType(PopupMenuButton<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Reset session'));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      expect(find.text('Register my vehicle'), findsOneWidget);
      await tester.tap(find.text('Register my vehicle'));
      await tester.pumpAndSettle(const Duration(seconds: 3));

      // Fill demo details
      expect(find.text('Fill demo details'), findsOneWidget);
      await tester.tap(find.text('Fill demo details'));
      await tester.pumpAndSettle();

      // Submit registration
      final formScroll = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.text('Register Vehicle'), 50.0, scrollable: formScroll);
      await tester.tap(find.text('Register Vehicle'));
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // Consent view: verify toggles default to OFF
      expect(find.text('Data Sharing Preferences'), findsOneWidget);
      final switches = tester.widgetList<SwitchListTile>(find.byType(SwitchListTile)).toList();
      expect(switches.length, 2);
      expect(switches[0].value, isFalse, reason: 'Usage tracking starts OFF');
      expect(switches[1].value, isFalse, reason: 'Maintenance tracking starts OFF');

      // Continue to baseline quote with toggles OFF
      await tester.tap(find.text('Continue to Quote'));
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // Baseline screen checks
      expect(find.text('Your Baseline Quote'), findsOneWidget);
      expect(find.textContaining('₹24,960.00'), findsOneWidget);
      expect(find.byKey(const Key('tracking_off_card')), findsOneWidget);
      expect(find.text('How we priced your vehicle'), findsOneWidget);
      expect(find.textContaining('Mid-size sedan'), findsOneWidget);
      expect(find.textContaining('Your registered vehicle'), findsOneWidget);

      // Go to Dashboard
      final quoteScroll = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.text('Go to Dashboard'), 50.0, scrollable: quoteScroll);
      await tester.tap(find.text('Go to Dashboard'));
      await tester.pumpAndSettle(const Duration(seconds: 5));

      expect(find.byKey(const Key('tracking_off_card')), findsOneWidget);

      // Open Privacy & tracking
      final dashScrollable = find.byType(Scrollable).first;
      final privacyBtn = find.byKey(const Key('privacy_tracking_button'));
      await tester.scrollUntilVisible(privacyBtn, 50.0, scrollable: dashScrollable);
      await tester.drag(dashScrollable, const Offset(0, -150));
      await tester.pumpAndSettle();

      await tester.tap(privacyBtn);
      await tester.pumpAndSettle(const Duration(seconds: 4));

      // Turn both toggles ON
      await tester.tap(find.byKey(const Key('usage_tracking_switch')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('maintenance_tracking_switch')));
      await tester.pumpAndSettle();

      // Save preferences
      await tester.tap(find.byKey(const Key('save_preferences_button')));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      // Go back to Dashboard
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // Verify tracking-off card is NO LONGER shown
      expect(find.byKey(const Key('tracking_off_card')), findsNothing);

      // Verify the pricing section still names "Mid-size sedan"
      final currentDashScrollable = find.byType(Scrollable).first;
      await tester.drag(currentDashScrollable, const Offset(0, 400));
      await tester.pumpAndSettle();
      final explainFinder = find.text('Explain my premium');
      if (explainFinder.evaluate().isNotEmpty) {
        await tester.scrollUntilVisible(explainFinder, 50.0, scrollable: currentDashScrollable);
        await tester.pumpAndSettle();
        await tester.tap(explainFinder, warnIfMissed: false);
        await tester.pumpAndSettle(const Duration(seconds: 2));
      }
      expect(find.textContaining('Mid-size sedan'), findsWidgets);
      debugPrint('[SCENARIO F] PASS: Baseline ₹24,960.00 with How we priced your vehicle and Mid-size sedan, tracking-off card gone after toggles ON');
    } catch (e) {
      debugPrint('[SCENARIO F] FAIL: $e');
      dumpScreenText();
      failures.add('Scenario f: $e');
    }

    // ----------------------------------------------------------------------
    // Scenario g: Switch to "The Overdue Hatchback", file mechanical_failure claim
    //             Assert manual-review triage outcome, open-recall reason, claim id
    // ----------------------------------------------------------------------
    debugPrint('[SCENARIO G] Switch to The Overdue Hatchback and file claim');
    try {
      // Open overflow menu and switch vehicle
      await tester.tap(find.byType(PopupMenuButton<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Switch vehicle'));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      final scrollable = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.text('The Overdue Hatchback'), 50.0, scrollable: scrollable);
      await tester.tap(find.text('The Overdue Hatchback'));
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // Switch to Claims tab
      await tester.tap(find.text('Claims'));
      await tester.pumpAndSettle(const Duration(seconds: 3));

      expect(find.text('File a New Claim (FNOL)'), findsOneWidget);

      // Select mechanical_failure cause
      await tester.tap(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('mechanical_failure').last);
      await tester.pumpAndSettle();

      // Enter description
      await tester.enterText(find.byKey(const Key('fnol_description_field')), 'Engine stalled on highway');
      await tester.pumpAndSettle();

      // Submit claim
      final claimsScrollable = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.text('Submit Claim'), 50.0, scrollable: claimsScrollable);
      await tester.drag(claimsScrollable, const Offset(0, -150));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit Claim'));
      await tester.pumpAndSettle(const Duration(seconds: 6));

      // Assert result screen displays:
      // 1. Manual-review triage outcome
      expect(find.text('Requires Manual Review'), findsOneWidget);
      // 2. Open-recall triage reason
      expect(find.textContaining('open_recall_on_file'), findsOneWidget);
      // 3. Claim ID is displayed
      expect(find.text('Claim ID'), findsOneWidget);
      expect(find.text('Status'), findsOneWidget);

      debugPrint('[SCENARIO G] PASS: Manual review outcome, open recall reason, and claim id shown');
    } catch (e) {
      debugPrint('[SCENARIO G] FAIL: $e');
      dumpScreenText();
      failures.add('Scenario g: $e');
    }

    // ----------------------------------------------------------------------
    // Scenario h: Diagnostics manual reading with confirmed code P0301,
    //             navigate away and back to confirm, then submit reading
    //             with check engine light off (no codes) -> "checked, no faults"
    // ----------------------------------------------------------------------
    debugPrint('[SCENARIO H] Diagnostics manual reading and clear light');
    try {
      // Go back to Dashboard tab
      await tester.tap(find.text('Dashboard'));
      await tester.pumpAndSettle(const Duration(seconds: 3));

      // Scroll to Vehicle Diagnostics shortcut and tap
      final dashScrollable = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.text('Vehicle Diagnostics'), 50.0, scrollable: dashScrollable);
      await tester.drag(dashScrollable, const Offset(0, -150));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Vehicle Diagnostics'));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      expect(find.text('Vehicle Diagnostics'), findsOneWidget);

      // Open Enter Manually dialog
      final diagScrollable = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.text('Enter Manually'), 50.0, scrollable: diagScrollable);
      await tester.drag(diagScrollable, const Offset(0, -100));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Enter Manually'));
      await tester.pumpAndSettle(const Duration(seconds: 2));

      // In manual form, select "Yes" for check engine light on
      expect(find.text('Is your check engine or warning light on?'), findsOneWidget);
      await tester.tap(find.text('Yes'));
      await tester.pumpAndSettle();

      // Enter code P0301 into text field
      final codeField = find.byType(TextField);
      await tester.enterText(codeField, 'P0301');
      await tester.pumpAndSettle();

      // Submit reading
      await tester.tap(find.text('Submit Reading'));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      // Navigate away: tap BackButton to Dashboard
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle(const Duration(seconds: 3));

      // Navigate back: tap Vehicle Diagnostics shortcut
      await tester.scrollUntilVisible(find.text('Vehicle Diagnostics'), 50.0, scrollable: dashScrollable);
      await tester.drag(dashScrollable, const Offset(0, -150));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Vehicle Diagnostics'));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      // Verify confirmed code P0301 appears
      final diagScrollable2 = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.text('P0301'), 50.0, scrollable: diagScrollable2);
      expect(find.text('P0301'), findsOneWidget);
      debugPrint('[SCENARIO H] Confirmed code P0301 verified');

      // Now submit a manual reading with light off (no codes)
      await tester.scrollUntilVisible(find.text('Enter Manually'), 50.0, scrollable: diagScrollable2);
      await tester.drag(diagScrollable2, const Offset(0, -100));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Enter Manually'));
      await tester.pumpAndSettle(const Duration(seconds: 2));

      // Select "No" (light off)
      await tester.tap(find.text('No'));
      await tester.pumpAndSettle();

      // Submit reading
      await tester.tap(find.text('Submit Reading'));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      // Navigate away and back
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle(const Duration(seconds: 3));

      await tester.scrollUntilVisible(find.text('Vehicle Diagnostics'), 50.0, scrollable: dashScrollable);
      await tester.drag(dashScrollable, const Offset(0, -150));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Vehicle Diagnostics'));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      // Confirm screen returns to "checked, no faults"
      final diagScrollable3 = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.text('checked, no faults').first, 50.0, scrollable: diagScrollable3);
      expect(find.text('checked, no faults'), findsWidgets);
      debugPrint('[SCENARIO H] PASS: checked, no faults confirmed');
    } catch (e) {
      debugPrint('[SCENARIO H] FAIL: $e');
      dumpScreenText();
      failures.add('Scenario h: $e');
    } finally {
      // Return to dashboard if still on diagnostics
      final backButton = find.byType(BackButton);
      if (backButton.evaluate().isNotEmpty) {
        await tester.tap(backButton);
        await tester.pumpAndSettle(const Duration(seconds: 3));
      }
    }

    // ----------------------------------------------------------------------
    // Scenario i: Register fresh vehicle through "Fill demo details" with both
    //             consent toggles ON; add service record (oil_change, 30 days ago,
    //             15000 km) -> Accepted with every check passing; dashboard shows
    //             maintenance 85 with "Includes self-reported records", usage
    //             "Not enough data yet", and premium unchanged at ₹24,960.00
    // ----------------------------------------------------------------------
    debugPrint('[SCENARIO I] Fresh vehicle registration + Accepted service record');
    try {
      // Reset session to clean onboarding
      await tester.tap(find.byType(PopupMenuButton<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Reset session'));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      expect(find.text('Register my vehicle'), findsOneWidget);
      await tester.tap(find.text('Register my vehicle'));
      await tester.pumpAndSettle(const Duration(seconds: 3));

      // Fill demo details
      expect(find.text('Fill demo details'), findsOneWidget);
      await tester.tap(find.text('Fill demo details'));
      await tester.pumpAndSettle();

      // Submit registration
      final formScroll = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.text('Register Vehicle'), 50.0, scrollable: formScroll);
      await tester.tap(find.text('Register Vehicle'));
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // Consent view: Turn both switches ON
      expect(find.text('Data Sharing Preferences'), findsOneWidget);
      await tester.tap(find.text('Usage Tracking'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Maintenance Tracking'));
      await tester.pumpAndSettle();

      // Continue to Quote
      await tester.tap(find.text('Continue to Quote'));
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // Go to Dashboard
      final quoteScroll = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.text('Go to Dashboard'), 50.0, scrollable: quoteScroll);
      await tester.tap(find.text('Go to Dashboard'));
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // Scroll to "Add a service record" card
      final dashScrollable = find.byType(Scrollable).first;
      final addRecordCard = find.byKey(const Key('add_service_record_card'));
      await tester.scrollUntilVisible(addRecordCard, 50.0, scrollable: dashScrollable);
      await tester.drag(dashScrollable, const Offset(0, -100));
      await tester.pumpAndSettle();

      await tester.tap(addRecordCard);
      await tester.pumpAndSettle(const Duration(seconds: 4));

      // On Add Service Record screen
      expect(find.text('Add Service Record'), findsOneWidget);

      final now = DateTime.now();
      final date30DaysAgo = now.subtract(const Duration(days: 30));
      final date30Str = '${date30DaysAgo.year.toString().padLeft(4, '0')}-${date30DaysAgo.month.toString().padLeft(2, '0')}-${date30DaysAgo.day.toString().padLeft(2, '0')}';

      // Set date: 30 days ago
      await tester.enterText(find.byKey(const Key('service_date_field')), date30Str);
      await tester.pumpAndSettle();

      // Set odometer: 15000
      await tester.enterText(find.byKey(const Key('odometer_field')), '15000');
      await tester.pumpAndSettle();

      // Submit form
      final addFormScroll = find.byType(Scrollable).first;
      final submitBtn = find.byKey(const Key('submit_service_record_button'));
      await tester.scrollUntilVisible(submitBtn, 50.0, scrollable: addFormScroll);
      await tester.tap(submitBtn);
      await tester.pumpAndSettle(const Duration(seconds: 6));

      // Verify Result View: Accepted with all checks passing
      expect(find.text('Accepted'), findsOneWidget);
      expect(find.textContaining('Self-reported records can raise your health score up to 85'), findsOneWidget);
      expect(find.byIcon(Icons.check_circle), findsWidgets);
      expect(find.byIcon(Icons.cancel), findsNothing);

      // Tap Done
      final resultScroll = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.byKey(const Key('done_button')), 50.0, scrollable: resultScroll);
      await tester.drag(resultScroll, const Offset(0, -100));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('done_button')));
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // Dashboard checks
      final dashScrollable2 = find.byType(Scrollable).first;
      await tester.drag(dashScrollable2, const Offset(0, 300));
      await tester.pumpAndSettle();

      expect(find.text('85 / 100'), findsOneWidget);
      expect(find.text('Includes self-reported records'), findsOneWidget);
      expect(find.text('Not enough data yet'), findsOneWidget);
      expect(find.textContaining('₹24,960.00'), findsWidgets);
      debugPrint('[SCENARIO I] PASS: 85 score, Includes self-reported records, Not enough data yet, ₹24,960.00');
    } catch (e) {
      debugPrint('[SCENARIO I] FAIL: $e');
      dumpScreenText();
      failures.add('Scenario i: $e');
    }

    // ----------------------------------------------------------------------
    // Scenario j: Add a second record at 12000 km dated 10 days ago: Flagged
    //             with the odometer check failing; History shows "1 flagged
    //             record is not used in your score" and, with the switch on,
    //             the flagged row.
    // ----------------------------------------------------------------------
    debugPrint('[SCENARIO J] Add second record (12000 km, 10 days ago) -> Flagged & History switch');
    try {
      final dashScrollable = find.byType(Scrollable).first;
      final addRecordCard = find.byKey(const Key('add_service_record_card'));
      await tester.scrollUntilVisible(addRecordCard, 50.0, scrollable: dashScrollable);
      await tester.drag(dashScrollable, const Offset(0, -100));
      await tester.pumpAndSettle();

      await tester.tap(addRecordCard);
      await tester.pumpAndSettle(const Duration(seconds: 4));

      final now = DateTime.now();
      final date10DaysAgo = now.subtract(const Duration(days: 10));
      final date10Str = '${date10DaysAgo.year.toString().padLeft(4, '0')}-${date10DaysAgo.month.toString().padLeft(2, '0')}-${date10DaysAgo.day.toString().padLeft(2, '0')}';

      // Select tyre_rotation
      await tester.tap(find.byKey(const Key('service_type_dropdown')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Tyre Rotation').last);
      await tester.pumpAndSettle();

      // Set date: 10 days ago
      await tester.enterText(find.byKey(const Key('service_date_field')), date10Str);
      await tester.pumpAndSettle();

      // Set odometer: 12000 (lower than previous 15000 km -> odometer_monotonic check fails)
      await tester.enterText(find.byKey(const Key('odometer_field')), '12000');
      await tester.pumpAndSettle();

      // Submit form
      final addFormScroll = find.byType(Scrollable).first;
      final submitBtn = find.byKey(const Key('submit_service_record_button'));
      await tester.scrollUntilVisible(submitBtn, 50.0, scrollable: addFormScroll);
      await tester.tap(submitBtn);
      await tester.pumpAndSettle(const Duration(seconds: 6));

      // Verify Result View: Flagged for review
      expect(find.text('Flagged for review'), findsOneWidget);
      expect(find.text("Saved, but not used in your score until it's reviewed."), findsOneWidget);
      expect(find.byKey(const Key('check_odometer_monotonic')), findsOneWidget);
      expect(find.byIcon(Icons.cancel), findsOneWidget);

      // Tap Done -> returns to dashboard
      final resultScrollJ = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.byKey(const Key('done_button')), 50.0, scrollable: resultScrollJ);
      await tester.drag(resultScrollJ, const Offset(0, -100));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('done_button')));
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // Open History via "Why this score?"
      final dashScrollable2 = find.byType(Scrollable).first;
      final whyBtn = find.byKey(const Key('why_this_score_button'));
      await tester.scrollUntilVisible(whyBtn, 50.0, scrollable: dashScrollable2);
      await tester.drag(dashScrollable2, const Offset(0, -150));
      await tester.pumpAndSettle();

      await tester.tap(whyBtn);
      await tester.pumpAndSettle(const Duration(seconds: 4));

      expect(find.text('Why this score?'), findsOneWidget);

      // Verify notice and Show flagged switch
      expect(find.text('1 flagged record is not used in your score'), findsOneWidget);
      expect(find.byKey(const Key('show_flagged_switch')), findsOneWidget);

      // Initially flagged row is hidden
      expect(find.text('Flagged — not used in scoring'), findsNothing);

      // Toggle switch to show flagged records
      await tester.tap(find.byKey(const Key('show_flagged_switch')));
      await tester.pumpAndSettle(const Duration(seconds: 4));

      // Verify flagged row is now visible
      expect(find.text('Flagged — not used in scoring'), findsOneWidget);
      debugPrint('[SCENARIO J] PASS: Flagged record, notice, and Show flagged toggle verified');
    } catch (e) {
      debugPrint('[SCENARIO J] FAIL: $e');
      dumpScreenText();
      failures.add('Scenario j: $e');
    } finally {
      final backButton = find.byType(BackButton);
      if (backButton.evaluate().isNotEmpty) {
        await tester.tap(backButton);
        await tester.pumpAndSettle(const Duration(seconds: 3));
      }
    }

    // ----------------------------------------------------------------------
    // Scenario k: Cover Rejected (a date before the registration date)
    // ----------------------------------------------------------------------
    debugPrint('[SCENARIO K] Add rejected record (date before registration date)');
    try {
      final dashScrollable = find.byType(Scrollable).first;
      final addRecordCard = find.byKey(const Key('add_service_record_card'));
      await tester.scrollUntilVisible(addRecordCard, 50.0, scrollable: dashScrollable);
      await tester.drag(dashScrollable, const Offset(0, -100));
      await tester.pumpAndSettle();

      await tester.tap(addRecordCard);
      await tester.pumpAndSettle(const Duration(seconds: 4));

      final now = DateTime.now();
      // Registration date in demo details was 2 years ago; choose 3 years ago
      final date3YearsAgo = now.subtract(const Duration(days: 365 * 3));
      final datePreRegStr = '${date3YearsAgo.year.toString().padLeft(4, '0')}-${date3YearsAgo.month.toString().padLeft(2, '0')}-${date3YearsAgo.day.toString().padLeft(2, '0')}';

      // Set date: 3 years ago
      await tester.enterText(find.byKey(const Key('service_date_field')), datePreRegStr);
      await tester.pumpAndSettle();

      // Set odometer: 5000
      await tester.enterText(find.byKey(const Key('odometer_field')), '5000');
      await tester.pumpAndSettle();

      // Submit form
      final addFormScroll = find.byType(Scrollable).first;
      final submitBtn = find.byKey(const Key('submit_service_record_button'));
      await tester.scrollUntilVisible(submitBtn, 50.0, scrollable: addFormScroll);
      await tester.tap(submitBtn);
      await tester.pumpAndSettle(const Duration(seconds: 6));

      // Verify Result View: Rejected
      expect(find.text('Rejected'), findsOneWidget);
      expect(find.textContaining('Not saved.'), findsOneWidget);
      expect(find.byKey(const Key('check_date_after_registration')), findsOneWidget);
      expect(find.byIcon(Icons.cancel), findsWidgets);

      // Tap Done
      final resultScrollK = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.byKey(const Key('done_button')), 50.0, scrollable: resultScrollK);
      await tester.drag(resultScrollK, const Offset(0, -100));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('done_button')));
      await tester.pumpAndSettle(const Duration(seconds: 4));
      debugPrint('[SCENARIO K] PASS: Rejected record (pre-registration date) displays rejected chip and failed checks');
    } catch (e) {
      debugPrint('[SCENARIO K] FAIL: $e');
      dumpScreenText();
      failures.add('Scenario k: $e');
    }

    // ----------------------------------------------------------------------
    // Always restore a1b2 consent so subsequent runs see clean baseline
    // ----------------------------------------------------------------------
    try {
      const baseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://localhost:8080');
      final dio = Dio();
      final res = await dio.put(
        '$baseUrl/identity/api/v1/vehicles/a1b2c3d4-e5f6-7890-abcd-ef1234567890/consent',
        data: {
          'usage_tracking_opt_in': true,
          'maintenance_tracking_opt_in': true,
        },
      );
      debugPrint('[RESTORE] Restored a1b2 consent (status: ${res.statusCode})');
    } catch (e) {
      debugPrint('[RESTORE WARNING] Could not restore a1b2 consent: $e');
    }

    if (failures.isNotEmpty) {
      fail('Live stack E2E test encountered ${failures.length} failure(s):\n${failures.join('\n')}');
    }
  });
}
