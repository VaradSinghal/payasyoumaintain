import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_app/router.dart';
import 'package:mobile_app/screens/dashboard_screen.dart';
import 'package:mobile_app/screens/onboarding_screen.dart';
import 'package:mobile_app/models/models.dart';
import 'package:mobile_app/providers/api_providers.dart';
import 'package:mobile_app/providers/auth_provider.dart';
import 'package:mobile_app/services/api_clients.dart';

class MockPricingApi extends PricingPolicyApi {
  QuoteResponse response;
  String? lastVehicleIdRequested;
  int quoteCallCount = 0;
  List<VehicleSegment> catalogue = [
    VehicleSegment(id: 'sedan_mid', label: 'Mid-size sedan', exShowroomPriceInr: 1200000),
  ];

  MockPricingApi(this.response);

  @override
  Future<QuoteResponse> quote(String vehicleId) async {
    quoteCallCount++;
    lastVehicleIdRequested = vehicleId;
    return response;
  }

  @override
  Future<List<VehicleSegment>> getVehicleCatalogue() async {
    return catalogue;
  }
}

class MockAdvisoryApi extends NotificationAdvisoryApi {
  AdvisoryResponse response;

  MockAdvisoryApi(this.response);

  @override
  Future<AdvisoryResponse> getAdvisories(String vehicleId) async {
    return response;
  }
}

class MockIdentityConsentApi extends IdentityConsentApi {
  int registerCallCount = 0;
  int consentCallCount = 0;

  @override
  Future<VehicleRegistration> registerVehicle(Map<String, dynamic> requestData) async {
    registerCallCount++;
    return VehicleRegistration(
      vehicleId: 'custom-reg-id',
      policyId: 'custom-policy-id',
      rcNumber: requestData['rc_number'] ?? 'KA01AB1234',
      kycStatus: 'VERIFIED',
    );
  }

  @override
  Future<ConsentRecord> updateConsent(String vehicleId, bool usageOptIn, bool maintenanceOptIn) async {
    consentCallCount++;
    return ConsentRecord(
      vehicleId: vehicleId,
      usageTrackingOptIn: usageOptIn,
      maintenanceTrackingOptIn: maintenanceOptIn,
    );
  }
}

class TestAuthNotifier extends AuthNotifier {
  final AuthState _state;
  TestAuthNotifier([AuthState? state])
      : _state = state ?? AuthState(isLoading: false);

  @override
  AuthState build() => _state;

  @override
  Future<void> login(String vehicleId, String policyId, {bool isDemo = false}) async {
    state = AuthState(
      isLoading: false,
      vehicleId: vehicleId,
      policyId: policyId,
      isDemo: isDemo,
    );
  }

  @override
  Future<void> logout() async {
    state = AuthState(
      isLoading: false,
      vehicleId: null,
      policyId: null,
      isDemo: false,
    );
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
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Fixture Parity Tests: demo_scenario.json vs test/fixtures/live/', () {
    test('All five demo vehicles in demo_scenario.json match live quote fixtures', () {
      final scenarioFile = File('assets/demo/demo_scenario.json');
      expect(scenarioFile.existsSync(), isTrue, reason: 'assets/demo/demo_scenario.json must exist');

      final scenariosJson = jsonDecode(scenarioFile.readAsStringSync()) as List<dynamic>;
      expect(scenariosJson.length, 5, reason: 'Must have exactly 5 demo vehicle scenarios');

      final fixtureFiles = [
        'quote_demo_veh_001.json',
        'quote_demo_veh_002.json',
        'quote_demo_veh_003.json',
        'quote_demo_veh_004.json',
        'quote_demo_veh_005.json',
      ];

      for (int i = 0; i < 5; i++) {
        final scenario = DemoScenario.fromJson(scenariosJson[i] as Map<String, dynamic>);
        final fixtureQuote = loadQuoteFixture(fixtureFiles[i]);

        expect(
          scenario.expected.finalPremium,
          closeTo(fixtureQuote.finalPremium, 0.001),
          reason: 'Vehicle ${scenario.vehicleId} expected final_premium must equal fixture final_premium',
        );

        final fixtureFlags = fixtureQuote.scoreDetail?.dataFlags ?? <String>[];
        expect(
          scenario.expected.dataFlags,
          fixtureFlags,
          reason: 'Vehicle ${scenario.vehicleId} expected data_flags must equal fixture data_flags',
        );

        expect(
          scenario.expected.basePremium,
          closeTo(fixtureQuote.basePremium!, 0.001),
          reason: 'Vehicle ${scenario.vehicleId} expected base_premium must equal fixture base_premium',
        );

        expect(
          scenario.expected.dynamicMultiplier,
          closeTo(fixtureQuote.dynamicMultiplier, 0.001),
          reason: 'Vehicle ${scenario.vehicleId} expected dynamic_multiplier must equal fixture dynamic_multiplier',
        );
      }
    });
  });

  group('Demo-Vehicle Picker Widget Tests', () {
    testWidgets('Offers "Register my vehicle" and "Explore demo vehicles" with all 5 cards', (tester) async {
      final pricingApi = MockPricingApi(loadQuoteFixture('quote_demo_veh_001.json'));
      final identityApi = MockIdentityConsentApi();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            pricingApiProvider.overrideWithValue(pricingApi),
            identityApiProvider.overrideWithValue(identityApi),
          ],
          child: const MaterialApp(home: OnboardingScreen()),
        ),
      );
      await tester.pumpAndSettle();

      final listFinder = find.byType(Scrollable);

      // Top options
      expect(find.text('Register my vehicle'), findsOneWidget);
      expect(find.text('Explore demo vehicles'), findsOneWidget);

      // Card 1
      expect(find.text('The Model Citizen'), findsOneWidget);
      expect(find.text('Mid-segment sedan · 2 years'), findsOneWidget);
      expect(find.text('Serviced on time, driven carefully, and it shows.'), findsOneWidget);

      // Card 2
      await tester.scrollUntilVisible(find.text('The Overdue Hatchback'), 50.0, scrollable: listFinder);
      expect(find.text('The Overdue Hatchback'), findsOneWidget);
      expect(find.text('Compact hatchback · 4 years'), findsOneWidget);

      // Card 3
      await tester.scrollUntilVisible(find.text('The Conflicting Records'), 50.0, scrollable: listFinder);
      expect(find.text('The Conflicting Records'), findsOneWidget);
      expect(find.text('Premium SUV · 3 years'), findsOneWidget);

      // Card 4
      await tester.scrollUntilVisible(find.text('The Self-Reporter'), 50.0, scrollable: listFinder);
      expect(find.text('The Self-Reporter'), findsOneWidget);
      expect(find.text('Mid-range sedan · 5 years'), findsOneWidget);

      // Card 5
      await tester.scrollUntilVisible(find.text('Brand New'), 50.0, scrollable: listFinder);
      expect(find.text('Brand New'), findsOneWidget);
      expect(find.text('Luxury SUV · new'), findsOneWidget);
    });

    testWidgets('Selecting a demo vehicle POSTs quote, skips identity-consent, and logs in with isDemo = true', (tester) async {
      final quote001 = loadQuoteFixture('quote_demo_veh_001.json');
      final pricingApi = MockPricingApi(quote001);
      final identityApi = MockIdentityConsentApi();
      final authNotifier = TestAuthNotifier(AuthState(isLoading: false));

      late WidgetRef capturedRef;

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            pricingApiProvider.overrideWithValue(pricingApi),
            identityApiProvider.overrideWithValue(identityApi),
            authProvider.overrideWith(() => authNotifier),
          ],
          child: MaterialApp(
            home: Consumer(
              builder: (context, ref, _) {
                capturedRef = ref;
                final auth = ref.watch(authProvider);
                if (auth.vehicleId != null) {
                  return const Scaffold(body: Text('DASHBOARD_CONTAINER'));
                }
                return const OnboardingScreen();
              },
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Tap "The Model Citizen"
      await tester.tap(find.text('The Model Citizen'));
      await tester.pumpAndSettle();

      // Check pricing API was called with vehicle_id
      expect(pricingApi.quoteCallCount, 1);
      expect(pricingApi.lastVehicleIdRequested, 'a1b2c3d4-e5f6-7890-abcd-ef1234567890');

      // Demo vehicles are not registered in identity-consent, so identityApi must NOT be called
      expect(identityApi.registerCallCount, 0);
      expect(identityApi.consentCallCount, 0);

      // AuthState must reflect the demo vehicle
      final auth = capturedRef.read(authProvider);
      expect(auth.vehicleId, 'a1b2c3d4-e5f6-7890-abcd-ef1234567890');
      expect(auth.policyId, quote001.policyId);
      expect(auth.isDemo, isTrue);

      // Dashboard screen reached
      expect(find.text('DASHBOARD_CONTAINER'), findsOneWidget);
    });
  });

  group('Demo Session Dashboard and Shell Overflow Menu', () {
    testWidgets('Demo session displays collapsible "About this demo vehicle" card', (tester) async {
      final pricingApi = MockPricingApi(loadQuoteFixture('quote_demo_veh_001.json'));
      final advisoryApi = MockAdvisoryApi(loadAdvisoryFixture('advisories_demo_veh_001.json'));

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            pricingApiProvider.overrideWithValue(pricingApi),
            advisoryApiProvider.overrideWithValue(advisoryApi),
            authProvider.overrideWith(() => TestAuthNotifier(
              AuthState(
                vehicleId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
                policyId: 'test-policy',
                isDemo: true,
                isLoading: false,
              ),
            )),
          ],
          child: const MaterialApp(home: DashboardScreen()),
        ),
      );
      await tester.pumpAndSettle();

      // Collapsible card header
      expect(find.textContaining('About this demo vehicle'), findsOneWidget);
      expect(find.text('Serviced on time, driven carefully, and it shows.'), findsOneWidget);
      expect(find.textContaining('Manufacturer-verified service records and calm driving'), findsOneWidget);
    });

    testWidgets('Shell overflow menu displays "Switch vehicle" beside "Reset session"', (tester) async {
      final pricingApi = MockPricingApi(loadQuoteFixture('quote_demo_veh_001.json'));
      final advisoryApi = MockAdvisoryApi(loadAdvisoryFixture('advisories_demo_veh_001.json'));

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            pricingApiProvider.overrideWithValue(pricingApi),
            advisoryApiProvider.overrideWithValue(advisoryApi),
            authProvider.overrideWith(() => TestAuthNotifier(
              AuthState(
                vehicleId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
                policyId: 'test-policy',
                isDemo: true,
                isLoading: false,
              ),
            )),
          ],
          child: Consumer(
            builder: (context, ref, _) {
              final router = ref.watch(routerProvider);
              return MaterialApp.router(
                routerConfig: router,
              );
            },
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Tap overflow menu button (PopupMenuButton)
      await tester.tap(find.byType(PopupMenuButton<String>));
      await tester.pumpAndSettle();

      expect(find.text('Switch vehicle'), findsOneWidget);
      expect(find.text('Reset session'), findsOneWidget);
    });
  });

  group('Dashboard Additions: Premium Explainer & Data Flags', () {
    testWidgets('Explain my premium expands and formats base, discounts, multiplier, and methodology', (tester) async {
      final customQuoteJson = {
        'currency': 'INR',
        'request_id': 'test-req',
        'vehicle_id': 'test-veh',
        'policy_id': 'test-pol',
        'base_premium': 24960.00,
        'dynamic_multiplier': 0.700,
        'final_premium': 13977.60,
        'discount_breakdown': [
          {'reason': 'no_claim_bonus', 'adjustment_pct': -20.0},
          {'reason': 'risk_score_discount', 'adjustment_pct': -30.0},
          {'reason': 'risk_score_surcharge', 'adjustment_pct': 9.2},
          {'reason': 'cold_start_neutral', 'adjustment_pct': 0.0},
          {'reason': 'custom_pilot_rebate', 'adjustment_pct': -5.0},
        ],
        'valid_until': '2026-11-10T08:40:54Z',
        'computed_at': '2026-10-11T08:40:54Z',
        'methodology_note': 'Dynamic Risk Multiplier is illustrative.',
        'score_detail': {
          'usage_score': 100.0,
          'maintenance_score': 100.0,
          'composite_score': 100.0,
          'health_status': 'scored',
          'vehicle_health_score': 100.0,
          'health_confidence': 'verified',
          'has_open_recall': false,
          'data_flags': [],
          'contributing_factors': [],
        }
      };

      final pricingApi = MockPricingApi(QuoteResponse.fromJson(customQuoteJson));
      final advisoryApi = MockAdvisoryApi(loadAdvisoryFixture('advisories_demo_veh_001.json'));

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            pricingApiProvider.overrideWithValue(pricingApi),
            advisoryApiProvider.overrideWithValue(advisoryApi),
            authProvider.overrideWith(() => TestAuthNotifier(
              AuthState(vehicleId: 'test-veh', policyId: 'test-pol', isLoading: false),
            )),
          ],
          child: const MaterialApp(home: DashboardScreen()),
        ),
      );
      await tester.pumpAndSettle();

      final listFinder = find.byType(Scrollable);
      await tester.scrollUntilVisible(find.text('Explain my premium'), 50.0, scrollable: listFinder);
      expect(find.text('Explain my premium'), findsOneWidget);

      // Tap to expand
      await tester.tap(find.text('Explain my premium'));
      await tester.pumpAndSettle();

      // Base premium in ₹ with Indian grouping
      expect(find.text('₹24,960.00'), findsOneWidget);

      // Discount labels mapped cleanly
      expect(find.text('No-claim bonus'), findsOneWidget);
      expect(find.text('Good vehicle and driving record'), findsOneWidget);
      expect(find.text('Risk adjustment'), findsOneWidget);
      expect(find.text('New policy, standard rate'), findsOneWidget);
      expect(find.text('custom_pilot_rebate'), findsOneWidget); // Raw fallback for unknown

      // Multiplier and final premium
      expect(find.text('Multiplier'), findsOneWidget);
      expect(find.text('0.700'), findsOneWidget);
      expect(find.text('₹13,977.60'), findsWidgets);

      // Methodology note
      expect(find.text('Dynamic Risk Multiplier is illustrative.'), findsWidgets);
    });

    testWidgets('Data flags render as plain chips with mapped labels and unknown fallback', (tester) async {
      final flagQuoteJson = {
        'currency': 'INR',
        'request_id': 'test-req',
        'vehicle_id': 'test-veh',
        'policy_id': 'test-pol',
        'base_premium': 27300.00,
        'dynamic_multiplier': 0.772,
        'final_premium': 21075.60,
        'discount_breakdown': [],
        'valid_until': '2026-11-10T08:40:54Z',
        'computed_at': '2026-10-11T08:40:54Z',
        'score_detail': {
          'usage_score': 85.0,
          'maintenance_score': 100.0,
          'composite_score': 91.0,
          'health_status': 'scored',
          'vehicle_health_score': 91.0,
          'health_confidence': 'verified',
          'has_open_recall': false,
          'data_flags': [
            'conflicting_records_detected',
            'usage_data_unavailable',
            'recall_status_unavailable',
            'some_new_future_flag',
          ],
          'contributing_factors': [],
        }
      };

      final pricingApi = MockPricingApi(QuoteResponse.fromJson(flagQuoteJson));
      final advisoryApi = MockAdvisoryApi(loadAdvisoryFixture('advisories_demo_veh_001.json'));

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            pricingApiProvider.overrideWithValue(pricingApi),
            advisoryApiProvider.overrideWithValue(advisoryApi),
            authProvider.overrideWith(() => TestAuthNotifier(
              AuthState(vehicleId: 'test-veh', policyId: 'test-pol', isLoading: false),
            )),
          ],
          child: const MaterialApp(home: DashboardScreen()),
        ),
      );
      await tester.pumpAndSettle();

      // Chips rendered
      expect(
        find.text('Conflicting service records found; the manufacturer-verified record was used'),
        findsOneWidget,
      );
      expect(find.text('No trip data yet'), findsOneWidget);
      expect(find.text("Recall status couldn't be checked"), findsOneWidget);
      expect(find.text('some_new_future_flag'), findsOneWidget);
    });
  });

  group('Honest Consent Copy and Registration Speed', () {
    testWidgets('Displays honest consent copy on data sharing preferences view', (tester) async {
      final identityApi = MockIdentityConsentApi();
      final pricingApi = MockPricingApi(loadQuoteFixture('quote_a1b2_opted_out.json'));

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            identityApiProvider.overrideWithValue(identityApi),
            pricingApiProvider.overrideWithValue(pricingApi),
          ],
          child: const MaterialApp(home: OnboardingScreen()),
        ),
      );
      await tester.pumpAndSettle();

      // Go to registration form
      await tester.tap(find.text('Register my vehicle'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Fill demo details'));
      await tester.pumpAndSettle();

      // Scroll and tap Register Vehicle
      await tester.ensureVisible(find.text('Register Vehicle'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Register Vehicle'));
      await tester.pumpAndSettle();

      // Consent view: verify honest copy
      expect(find.text('Data Sharing Preferences'), findsOneWidget);
      expect(
        find.text('Off: you pay the standard premium. On: your premium can reflect your driving and servicing, and it can go down or up. You can change this any time in Privacy & tracking.'),
        findsOneWidget,
      );
    });

    testWidgets('"Fill demo details" pre-fills fictional data into form', (tester) async {
      final identityApi = MockIdentityConsentApi();
      final pricingApi = MockPricingApi(loadQuoteFixture('quote_a1b2_opted_out.json'));

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            identityApiProvider.overrideWithValue(identityApi),
            pricingApiProvider.overrideWithValue(pricingApi),
            clockProvider.overrideWithValue(() => DateTime(2026, 1, 15)),
          ],
          child: const MaterialApp(home: OnboardingScreen()),
        ),
      );
      await tester.pumpAndSettle();

      // Go to registration form
      await tester.tap(find.text('Register my vehicle'));
      await tester.pumpAndSettle();

      expect(find.text('Fill demo details'), findsOneWidget);
      await tester.tap(find.text('Fill demo details'));
      await tester.pumpAndSettle();

      // Verify fictional fields filled
      expect(find.text('Demo Driver (Fictional)'), findsOneWidget);
      expect(find.text('DL01DEMO9999'), findsOneWidget);
      expect(find.text('2024-01-15'), findsOneWidget);
      expect(find.text('Mid-size sedan (indicative ex-showroom ₹12,00,000)'), findsOneWidget);
      expect(find.text('demo.driver@example.com'), findsOneWidget);
      expect(find.text('9999999999'), findsOneWidget);
      expect(find.text('ABCDE9999Z'), findsOneWidget);
    });
  });
}
