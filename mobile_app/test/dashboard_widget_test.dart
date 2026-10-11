import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_app/screens/dashboard_screen.dart';
import 'package:mobile_app/screens/onboarding_screen.dart';
import 'package:mobile_app/models/models.dart';
import 'package:mobile_app/providers/api_providers.dart';
import 'package:mobile_app/providers/auth_provider.dart';
import 'package:mobile_app/services/api_clients.dart';

class FakePricingApi extends PricingPolicyApi {
  QuoteResponse response;
  bool throwError = false;
  int callCount = 0;

  FakePricingApi(this.response);

  @override
  Future<QuoteResponse> quote(String vehicleId) async {
    callCount++;
    if (throwError) throw Exception('Network Error');
    return response;
  }
}

class FakeAdvisoryApi extends NotificationAdvisoryApi {
  AdvisoryResponse response;

  FakeAdvisoryApi(this.response);

  @override
  Future<AdvisoryResponse> getAdvisories(String vehicleId) async {
    return response;
  }
}

class FakeIdentityConsentApi extends IdentityConsentApi {
  @override
  Future<VehicleRegistration> registerVehicle(Map<String, dynamic> requestData) async {
    return VehicleRegistration(
      vehicleId: 'test-vehicle',
      policyId: 'test-policy',
      rcNumber: 'MH12AB1234',
      kycStatus: 'VERIFIED',
    );
  }

  @override
  Future<ConsentRecord> updateConsent(String vehicleId, bool usageOptIn, bool maintenanceOptIn) async {
    return ConsentRecord(
      vehicleId: vehicleId,
      usageTrackingOptIn: usageOptIn,
      maintenanceTrackingOptIn: maintenanceOptIn,
    );
  }
}

class FakeAuthNotifier extends AuthNotifier {
  @override
  AuthState build() {
    return AuthState(vehicleId: 'test-vehicle', policyId: 'test-policy', isLoading: false);
  }
}

QuoteResponse loadQuoteFixture(String filename) {
  final file = File('test/fixtures/live/$filename');
  final json = jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
  return QuoteResponse.fromJson(json);
}

AdvisoryResponse loadAdvisoryFixture(String filename) {
  final file = File('test/fixtures/live/$filename');
  final json = jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
  return AdvisoryResponse.fromJson(json);
}

void main() {
  Widget createTestApp(FakePricingApi pricingApi, FakeAdvisoryApi advisoryApi) {
    return ProviderScope(
      overrides: [
        pricingApiProvider.overrideWithValue(pricingApi),
        advisoryApiProvider.overrideWithValue(advisoryApi),
        authProvider.overrideWith(() => FakeAuthNotifier()),
      ],
      child: const MaterialApp(
        home: DashboardScreen(),
      ),
    );
  }

  group('Dashboard Widget Tests - 5 Demo Stories', () {
    testWidgets('Story 1 (NORMAL): renders quote_demo_veh_001.json without error', (tester) async {
      final pricingApi = FakePricingApi(loadQuoteFixture('quote_demo_veh_001.json'));
      final advisoryApi = FakeAdvisoryApi(loadAdvisoryFixture('advisories_demo_veh_001.json'));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      expect(find.text('\$13977.60'), findsOneWidget);
      expect(find.text('Eligible for standard renewal - vehicle health profile is strong.'), findsOneWidget);
      expect(find.text('Usage Score'), findsOneWidget);
      expect(find.text('100 / 100'), findsWidgets);
      expect(find.text('Maintenance Score'), findsOneWidget);
      expect(find.text('URGENT: Your vehicle has an open safety recall.'), findsNothing);
      expect(find.text("Recall status couldn't be checked"), findsNothing);

      final listFinder = find.byType(Scrollable);
      await tester.scrollUntilVisible(find.text("Great Driving — You're on Track for a Discount"), 50.0, scrollable: listFinder);
      expect(find.text("Great Driving — You're on Track for a Discount"), findsOneWidget);
    });

    testWidgets('Story 2 (POOR): renders quote_demo_veh_002.json with red recall banner', (tester) async {
      final pricingApi = FakePricingApi(loadQuoteFixture('quote_demo_veh_002.json'));
      final advisoryApi = FakeAdvisoryApi(loadAdvisoryFixture('advisories_demo_veh_002.json'));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      expect(find.text('\$12265.34'), findsOneWidget);
      expect(find.text('URGENT: Your vehicle has an open safety recall.'), findsOneWidget);
      expect(find.text('Usage Score'), findsOneWidget);
      expect(find.text('75 / 100'), findsOneWidget);
      expect(find.text('Maintenance Score'), findsOneWidget);
      expect(find.text('15 / 100'), findsOneWidget);

      final listFinder = find.byType(Scrollable);
      await tester.scrollUntilVisible(find.text('Manufacturer Recall — Immediate Action Required'), 50.0, scrollable: listFinder);
      expect(find.text('Manufacturer Recall — Immediate Action Required'), findsOneWidget);
    });

    testWidgets('Story 3 (CONFLICTING): renders quote_demo_veh_003.json without error', (tester) async {
      final pricingApi = FakePricingApi(loadQuoteFixture('quote_demo_veh_003.json'));
      final advisoryApi = FakeAdvisoryApi(loadAdvisoryFixture('advisories_demo_veh_003.json'));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      expect(find.text('\$21075.60'), findsOneWidget);
      expect(find.text('Usage Score'), findsOneWidget);
      expect(find.text('85 / 100'), findsOneWidget);
      expect(find.text('Maintenance Score'), findsOneWidget);
      expect(find.text('100 / 100'), findsOneWidget);
      expect(find.text('URGENT: Your vehicle has an open safety recall.'), findsNothing);
    });

    testWidgets('Story 4 (UNVERIFIED): renders quote_demo_veh_004.json with unverified badge', (tester) async {
      final pricingApi = FakePricingApi(loadQuoteFixture('quote_demo_veh_004.json'));
      final advisoryApi = FakeAdvisoryApi(loadAdvisoryFixture('advisories_demo_veh_004.json'));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      expect(find.text('\$6563.70'), findsOneWidget);
      expect(find.text('Unverified — self-reported only'), findsOneWidget);
      expect(find.text('Usage Score'), findsOneWidget);
      expect(find.text('100 / 100'), findsOneWidget);
      expect(find.text('Maintenance Score'), findsOneWidget);
      expect(find.text('85 / 100'), findsOneWidget);
    });

    testWidgets('Story 5 (INSUFFICIENT): renders quote_demo_veh_005.json and null usage score reads "Not enough data yet"', (tester) async {
      final pricingApi = FakePricingApi(loadQuoteFixture('quote_demo_veh_005.json'));
      final advisoryApi = FakeAdvisoryApi(loadAdvisoryFixture('advisories_demo_veh_005.json'));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      expect(find.text('\$44460.00'), findsOneWidget);
      expect(find.text('Insufficient Data'), findsOneWidget);
      expect(find.text('Not enough data yet'), findsWidgets);

      final listFinder = find.byType(Scrollable);
      await tester.scrollUntilVisible(find.text('No Service Records on File'), 50.0, scrollable: listFinder);
      expect(find.text('No Service Records on File'), findsOneWidget);
    });

    testWidgets('Recall null (amber "couldn\'t check" notice) - rendered from derived live fixture', (tester) async {
      final pricingApi = FakePricingApi(loadQuoteFixture('quote_demo_veh_001_derived_recall_null.json'));
      final advisoryApi = FakeAdvisoryApi(loadAdvisoryFixture('advisories_demo_veh_001.json'));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      expect(find.text("Recall status couldn't be checked"), findsOneWidget);
    });

    testWidgets('error with a working retry', (tester) async {
      final pricingApi = FakePricingApi(loadQuoteFixture('quote_demo_veh_001.json'));
      pricingApi.throwError = true;
      final advisoryApi = FakeAdvisoryApi(loadAdvisoryFixture('advisories_demo_veh_001.json'));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      // Should be in error state
      expect(find.text('Retry'), findsOneWidget);

      // Now disable error and retry
      pricingApi.throwError = false;
      await tester.tap(find.text('Retry'));
      await tester.pumpAndSettle();

      // Should successfully load
      expect(find.text('\$13977.60'), findsOneWidget);
    });

    testWidgets('Consent toggles start OFF', (tester) async {
      final identityApi = FakeIdentityConsentApi();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            identityApiProvider.overrideWithValue(identityApi),
          ],
          child: const MaterialApp(
            home: OnboardingScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // On register step, tap "Register Vehicle"
      expect(find.text('Register Vehicle'), findsOneWidget);
      await tester.tap(find.text('Register Vehicle'));
      await tester.pumpAndSettle();

      // Now on consent step: "Data Sharing Preferences"
      expect(find.text('Data Sharing Preferences'), findsOneWidget);
      expect(find.text('Usage Tracking'), findsOneWidget);
      expect(find.text('Maintenance Tracking'), findsOneWidget);

      // Check both switch widgets start OFF (value == false)
      final switches = tester.widgetList<Switch>(find.byType(Switch)).toList();
      expect(switches.length, 2);
      expect(switches[0].value, isFalse, reason: 'Usage Tracking toggle must start OFF');
      expect(switches[1].value, isFalse, reason: 'Maintenance Tracking toggle must start OFF');
    });
  });
}
