import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_app/screens/dashboard_screen.dart';
import 'package:mobile_app/screens/onboarding_screen.dart';
import 'package:mobile_app/screens/privacy_screen.dart';
import 'package:mobile_app/screens/history_screen.dart';
import 'package:mobile_app/router.dart';
import 'package:mobile_app/models/models.dart';
import 'package:mobile_app/providers/api_providers.dart';
import 'package:mobile_app/providers/auth_provider.dart';
import 'package:mobile_app/providers/dashboard_provider.dart';
import 'package:mobile_app/services/api_clients.dart';

class FakePricingApi extends PricingPolicyApi {
  QuoteResponse response;
  bool throwError = false;
  int callCount = 0;
  List<VehicleSegment> catalogue = [
    VehicleSegment(id: 'sedan_mid', label: 'Mid-size sedan', exShowroomPriceInr: 1200000),
  ];

  FakePricingApi(this.response);

  @override
  Future<QuoteResponse> quote(String vehicleId) async {
    callCount++;
    if (throwError) throw Exception('Network Error');
    return response;
  }

  @override
  Future<List<VehicleSegment>> getVehicleCatalogue() async {
    return catalogue;
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
  ConsentRecord consentRecord = ConsentRecord(
    vehicleId: 'test-vehicle',
    usageTrackingOptIn: false,
    maintenanceTrackingOptIn: false,
  );
  int updateConsentCallCount = 0;
  bool? lastUpdatedUsage;
  bool? lastUpdatedMaintenance;

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
  Future<ConsentRecord> getConsent(String vehicleId) async {
    return consentRecord;
  }

  @override
  Future<ConsentRecord> updateConsent(String vehicleId, bool usageOptIn, bool maintenanceOptIn) async {
    updateConsentCallCount++;
    lastUpdatedUsage = usageOptIn;
    lastUpdatedMaintenance = maintenanceOptIn;
    consentRecord = ConsentRecord(
      vehicleId: vehicleId,
      usageTrackingOptIn: usageOptIn,
      maintenanceTrackingOptIn: maintenanceOptIn,
    );
    return consentRecord;
  }
}

class FakeMaintenanceApi extends MaintenanceApi {
  MaintenanceTimeline timeline;
  int callCount = 0;

  FakeMaintenanceApi(this.timeline);

  @override
  Future<MaintenanceTimeline> getTimeline(String vehicleId) async {
    callCount++;
    return timeline;
  }
}

class FakeTelematicsApi extends TelematicsApi {
  List<TripAggregate> aggregates;
  int callCount = 0;

  FakeTelematicsApi(this.aggregates);

  @override
  Future<List<TripAggregate>> getAggregates(String vehicleId) async {
    callCount++;
    return aggregates;
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

MaintenanceTimeline loadTimelineFixture(String filename) {
  final file = File('test/fixtures/live/$filename');
  final json = jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
  return MaintenanceTimeline.fromJson(json);
}

List<TripAggregate> loadAggregatesFixture(String filename) {
  final file = File('test/fixtures/live/$filename');
  final list = jsonDecode(file.readAsStringSync()) as List<dynamic>;
  return TripAggregate.fromJsonList(list);
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

      expect(find.text('₹13,977.60'), findsOneWidget);
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

      expect(find.text('₹12,265.34'), findsOneWidget);
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

      expect(find.text('₹21,075.60'), findsOneWidget);
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

      expect(find.text('₹6,563.70'), findsOneWidget);
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

      expect(find.text('₹44,460.00'), findsOneWidget);
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

      expect(find.text("Recall status couldn't be checked"), findsWidgets);
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
      expect(find.text('₹13,977.60'), findsOneWidget);
    });

    testWidgets('Consent toggles start OFF', (tester) async {
      final identityApi = FakeIdentityConsentApi();
      final pricingApi = FakePricingApi(loadQuoteFixture('quote_a1b2_opted_out.json'));

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            identityApiProvider.overrideWithValue(identityApi),
            pricingApiProvider.overrideWithValue(pricingApi),
          ],
          child: const MaterialApp(
            home: OnboardingScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // On welcome screen, tap "Register my vehicle"
      expect(find.text('Register my vehicle'), findsOneWidget);
      await tester.tap(find.text('Register my vehicle'));
      await tester.pumpAndSettle();

      // On register step, tap "Fill demo details"
      expect(find.text('Fill demo details'), findsOneWidget);
      await tester.tap(find.text('Fill demo details'));
      await tester.pumpAndSettle();

      // Tap "Register Vehicle"
      expect(find.text('Register Vehicle'), findsOneWidget);
      await tester.ensureVisible(find.text('Register Vehicle'));
      await tester.pumpAndSettle();
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

    testWidgets('Tracking-off card replaces score bars and recall area when not_evaluated', (tester) async {
      final pricingApi = FakePricingApi(loadQuoteFixture('quote_a1b2_opted_out.json'));
      final advisoryApi = FakeAdvisoryApi(loadAdvisoryFixture('advisories_a1b2_opted_out.json'));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      // Tracking-off card is rendered
      expect(find.text('Tracking is off — standard pricing'), findsOneWidget);
      expect(find.text('Privacy & tracking'), findsWidgets);

      // Score bars and insufficient data badge are absent
      expect(find.text('Not enough data yet'), findsNothing);
      expect(find.text('Insufficient Data'), findsNothing);
      expect(find.text('Usage Score'), findsNothing);
      expect(find.text('Maintenance Score'), findsNothing);

      // Recall banner is absent
      expect(find.text('URGENT: Your vehicle has an open safety recall.'), findsNothing);
      expect(find.text("Recall status couldn't be checked"), findsNothing);

      // consent_not_given_* flags and vehicle_not_registered do NOT render as chips
      expect(find.byType(Chip), findsNothing);
    });

    testWidgets('Unavailable card with Retry renders when consent_status_unavailable', (tester) async {
      final pricingApi = FakePricingApi(loadQuoteFixture('quote_consent_status_unavailable.json'));
      final advisoryApi = FakeAdvisoryApi(AdvisoryResponse(advisories: []));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      // Card appears
      expect(find.text("We couldn't check your tracking preferences. Standard pricing is shown."), findsOneWidget);
      expect(find.byKey(const Key('retry_consent_button')), findsOneWidget);

      final initialCount = pricingApi.callCount;
      await tester.tap(find.byKey(const Key('retry_consent_button')));
      await tester.pumpAndSettle();

      expect(pricingApi.callCount, greaterThan(initialCount));
    });

    testWidgets('Privacy screen loads GET consent and saves PUT toggles via fake API', (tester) async {
      final identityApi = FakeIdentityConsentApi();
      identityApi.consentRecord = ConsentRecord(
        vehicleId: 'test-vehicle',
        usageTrackingOptIn: false,
        maintenanceTrackingOptIn: false,
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            identityApiProvider.overrideWithValue(identityApi),
            authProvider.overrideWith(() => FakeAuthNotifier()),
          ],
          child: const MaterialApp(
            home: PrivacyScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Privacy & tracking'), findsOneWidget);
      expect(find.text('Usage tracking'), findsOneWidget);
      expect(find.text('Maintenance tracking'), findsOneWidget);

      // Both switches start false
      var switches = tester.widgetList<SwitchListTile>(find.byType(SwitchListTile)).toList();
      expect(switches[0].value, isFalse);
      expect(switches[1].value, isFalse);

      // Toggle both
      await tester.tap(find.byKey(const Key('usage_tracking_switch')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('maintenance_tracking_switch')));
      await tester.pumpAndSettle();

      switches = tester.widgetList<SwitchListTile>(find.byType(SwitchListTile)).toList();
      expect(switches[0].value, isTrue);
      expect(switches[1].value, isTrue);

      // Save preferences
      await tester.tap(find.byKey(const Key('save_preferences_button')));
      await tester.pumpAndSettle();

      expect(identityApi.updateConsentCallCount, 1);
      expect(identityApi.lastUpdatedUsage, isTrue);
      expect(identityApi.lastUpdatedMaintenance, isTrue);
      expect(find.text('Preferences saved'), findsOneWidget);
    });

    testWidgets('TRACKING_DISABLED advisory deep-links to Privacy screen', (tester) async {
      final quote = loadQuoteFixture('quote_a1b2_opted_out.json');
      final advisory = loadAdvisoryFixture('advisories_a1b2_opted_out.json');
      final pricingApi = FakePricingApi(quote);
      final advisoryApi = FakeAdvisoryApi(advisory);
      final identityApi = FakeIdentityConsentApi();

      final container = ProviderContainer(
        overrides: [
          pricingApiProvider.overrideWithValue(pricingApi),
          advisoryApiProvider.overrideWithValue(advisoryApi),
          identityApiProvider.overrideWithValue(identityApi),
          authProvider.overrideWith(() => FakeAuthNotifier()),
        ],
      );

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp.router(
            routerConfig: container.read(routerProvider),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Scroll until advisory is visible
      final advisoryTile = find.text('Personalised insights are off');
      await tester.scrollUntilVisible(advisoryTile, 300);
      expect(advisoryTile, findsOneWidget);

      // Tap advisory
      await tester.tap(advisoryTile);
      await tester.pumpAndSettle();

      // Navigates to Privacy screen
      expect(find.text('Your Tracking Preferences'), findsOneWidget);
      expect(find.byKey(const Key('save_preferences_button')), findsOneWidget);
    });

    testWidgets('Onboarding consent step honest copy and baseline quote tracking-off card', (tester) async {
      final identityApi = FakeIdentityConsentApi();
      final pricingApi = FakePricingApi(loadQuoteFixture('quote_a1b2_opted_out.json'));

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            identityApiProvider.overrideWithValue(identityApi),
            pricingApiProvider.overrideWithValue(pricingApi),
          ],
          child: const MaterialApp(
            home: OnboardingScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Register my vehicle -> fill -> Register
      await tester.tap(find.text('Register my vehicle'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Fill demo details'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Register Vehicle'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Register Vehicle'));
      await tester.pumpAndSettle();

      // Check honest copy on consent view
      expect(find.text('Off: you pay the standard premium. On: your premium can reflect your driving and servicing, and it can go down or up. You can change this any time in Privacy & tracking.'), findsOneWidget);

      // Check toggles default to OFF
      final switches = tester.widgetList<Switch>(find.byType(Switch)).toList();
      expect(switches[0].value, isFalse);
      expect(switches[1].value, isFalse);

      // Continue to Quote with toggles OFF
      await tester.tap(find.text('Continue to Quote'));
      await tester.pumpAndSettle();

      // Baseline quote screen shows tracking-off card
      expect(find.text('Your Baseline Quote'), findsOneWidget);
      expect(find.text('Tracking is off — standard pricing'), findsOneWidget);
      expect(find.text("We'll personalise your premium as data arrives"), findsNothing);
    });

    testWidgets('History screen renders live fixture a1b2: shows OEM badges, no Self-reported, and 10-trip limit toggle', (tester) async {
      final timeline = loadTimelineFixture('timeline_a1b2.json');
      final aggregates = loadAggregatesFixture('aggregates_a1b2.json');
      final maintenanceApi = FakeMaintenanceApi(timeline);
      final telematicsApi = FakeTelematicsApi(aggregates);
      final evaluatedQuote = loadQuoteFixture('quote_demo_veh_001.json');

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            maintenanceApiProvider.overrideWithValue(maintenanceApi),
            telematicsApiProvider.overrideWithValue(telematicsApi),
            authProvider.overrideWith(() => FakeAuthNotifier()),
            dashboardProvider.overrideWith((ref) async => DashboardData(
                  evaluatedQuote,
                  AdvisoryResponse(advisories: []),
                )),
          ],
          child: const MaterialApp(
            home: HistoryScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Why this score?'), findsOneWidget);
      expect(find.text('Service History'), findsOneWidget);
      expect(find.text('Full service'), findsOneWidget);
      expect(find.text('35,000 km'), findsOneWidget);
      expect(find.text('Brake service'), findsOneWidget);
      expect(find.text('28,000 km'), findsOneWidget);

      // Badges: OEM present (2 records), Self-reported absent
      expect(find.text('OEM'), findsNWidgets(2));
      expect(find.text('Self-reported'), findsNothing);

      // Trips section: 12 trips present in fixture, defaults to 10 with toggle
      await tester.scrollUntilVisible(find.text('Recent Trips'), 200);
      expect(find.text('Recent Trips'), findsOneWidget);

      await tester.scrollUntilVisible(find.text('Show all'), 200);
      expect(find.text('Show all'), findsOneWidget);
      await tester.tap(find.text('Show all'));
      await tester.pumpAndSettle();

      await tester.scrollUntilVisible(find.text('Show less'), 200);
      expect(find.text('Show less'), findsOneWidget);
    });

    testWidgets('History screen renders live fixture d4e5: shows Self-reported badges, no OEM', (tester) async {
      final timeline = loadTimelineFixture('timeline_d4e5.json');
      final maintenanceApi = FakeMaintenanceApi(timeline);
      final telematicsApi = FakeTelematicsApi([]);
      final evaluatedQuote = loadQuoteFixture('quote_demo_veh_004.json');

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            maintenanceApiProvider.overrideWithValue(maintenanceApi),
            telematicsApiProvider.overrideWithValue(telematicsApi),
            authProvider.overrideWith(() => FakeAuthNotifier()),
            dashboardProvider.overrideWith((ref) async => DashboardData(
                  evaluatedQuote,
                  AdvisoryResponse(advisories: []),
                )),
          ],
          child: const MaterialApp(
            home: HistoryScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Oil change'), findsOneWidget);
      expect(find.text('30,000 km'), findsOneWidget);
      expect(find.text('Brake service'), findsOneWidget);
      expect(find.text('25,000 km'), findsOneWidget);

      // Badges: Self-reported present (2 records), OEM absent
      expect(find.text('Self-reported'), findsNWidgets(2));
      expect(find.text('OEM'), findsNothing);
    });

    testWidgets('History screen renders live fixture c3d4: shows both OEM and Self-reported badges', (tester) async {
      final timeline = loadTimelineFixture('timeline_c3d4.json');
      final maintenanceApi = FakeMaintenanceApi(timeline);
      final telematicsApi = FakeTelematicsApi([]);
      final evaluatedQuote = loadQuoteFixture('quote_demo_veh_003.json');

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            maintenanceApiProvider.overrideWithValue(maintenanceApi),
            telematicsApiProvider.overrideWithValue(telematicsApi),
            authProvider.overrideWith(() => FakeAuthNotifier()),
            dashboardProvider.overrideWith((ref) async => DashboardData(
                  evaluatedQuote,
                  AdvisoryResponse(advisories: []),
                )),
          ],
          child: const MaterialApp(
            home: HistoryScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Full service'), findsOneWidget);
      expect(find.text('42,000 km'), findsOneWidget);
      expect(find.text('Oil change'), findsOneWidget);
      expect(find.text('38,500 km'), findsOneWidget);

      // Both badges present
      expect(find.text('OEM'), findsOneWidget);
      expect(find.text('Self-reported'), findsOneWidget);
    });

    test('Contract schema maintenance-event source enum values all map to human labels', () {
      var schemaFile = File('../contracts/schemas/maintenance-event.schema.json');
      if (!schemaFile.existsSync()) {
        schemaFile = File('contracts/schemas/maintenance-event.schema.json');
      }
      expect(schemaFile.existsSync(), isTrue, reason: 'maintenance-event.schema.json should exist');

      final schemaJson = jsonDecode(schemaFile.readAsStringSync()) as Map<String, dynamic>;
      final properties = schemaJson['properties'] as Map<String, dynamic>;
      final sourceProp = properties['source'] as Map<String, dynamic>;
      final enumValues = (sourceProp['enum'] as List<dynamic>).cast<String>();

      expect(enumValues, containsAll(['oem_api', 'self_upload', 'inspection']));

      for (final val in enumValues) {
        final label = HistoryScreen.formatSourceLabel(val);
        expect(label, isNotNull, reason: 'Enum value $val should map to a human label');
        expect(label!.isNotEmpty, isTrue);
        expect(label.contains('_'), isFalse, reason: 'Label $label should be humanised, not raw identifier');
      }
    });

    testWidgets('History screen displays empty states when no records or trips exist', (tester) async {
      final timeline = MaintenanceTimeline(vehicleId: 'test-vehicle', totalServices: 0, events: []);
      final aggregates = <TripAggregate>[];
      final maintenanceApi = FakeMaintenanceApi(timeline);
      final telematicsApi = FakeTelematicsApi(aggregates);
      final evaluatedQuote = loadQuoteFixture('quote_demo_veh_001.json');

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            maintenanceApiProvider.overrideWithValue(maintenanceApi),
            telematicsApiProvider.overrideWithValue(telematicsApi),
            authProvider.overrideWith(() => FakeAuthNotifier()),
            dashboardProvider.overrideWith((ref) async => DashboardData(
                  evaluatedQuote,
                  AdvisoryResponse(advisories: []),
                )),
          ],
          child: const MaterialApp(
            home: HistoryScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('No service records yet'), findsOneWidget);
      expect(find.text('No trips recorded yet'), findsOneWidget);
    });

    testWidgets('not_evaluated vehicle in HistoryScreen triggers no timeline or aggregates call', (tester) async {
      final notEvaluatedQuote = loadQuoteFixture('quote_a1b2_opted_out.json');
      final timeline = MaintenanceTimeline(vehicleId: 'test-vehicle', events: []);
      final maintenanceApi = FakeMaintenanceApi(timeline);
      final telematicsApi = FakeTelematicsApi([]);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            maintenanceApiProvider.overrideWithValue(maintenanceApi),
            telematicsApiProvider.overrideWithValue(telematicsApi),
            authProvider.overrideWith(() => FakeAuthNotifier()),
            dashboardProvider.overrideWith((ref) async => DashboardData(
                  notEvaluatedQuote,
                  AdvisoryResponse(advisories: []),
                )),
          ],
          child: const MaterialApp(
            home: HistoryScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Turn on tracking to see your history here'), findsOneWidget);
      expect(maintenanceApi.callCount, 0, reason: 'Must not call maintenance timeline');
      expect(telematicsApi.callCount, 0, reason: 'Must not call telematics aggregates');
    });
  });
}
