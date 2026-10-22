import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:mobile_app/models/models.dart';
import 'package:mobile_app/providers/api_providers.dart';
import 'package:mobile_app/providers/auth_provider.dart';
import 'package:mobile_app/screens/add_service_record_screen.dart';
import 'package:mobile_app/screens/dashboard_screen.dart';
import 'package:mobile_app/screens/history_screen.dart';
import 'package:mobile_app/services/api_clients.dart';
import 'package:mobile_app/services/api_exception.dart';

class MockPricingApi extends PricingPolicyApi {
  QuoteResponse quoteResponse;
  MockPricingApi(this.quoteResponse);

  @override
  Future<QuoteResponse> quote(String vehicleId) async => quoteResponse;
}

class MockAdvisoryApi extends NotificationAdvisoryApi {
  AdvisoryResponse advisoryResponse;
  MockAdvisoryApi(this.advisoryResponse);

  @override
  Future<AdvisoryResponse> getAdvisories(String vehicleId) async => advisoryResponse;
}

class MockMaintenanceApi extends MaintenanceApi {
  ServiceRecordResponse? postResponse;
  ApiException? postError;
  MaintenanceTimeline timelineResponse;
  bool? lastIncludeFlagged;

  MockMaintenanceApi({
    this.postResponse,
    this.postError,
    MaintenanceTimeline? timeline,
  }) : timelineResponse = timeline ?? MaintenanceTimeline(vehicleId: 'test-v1');

  @override
  Future<ServiceRecordResponse> postServiceRecord(ServiceRecordRequest request) async {
    if (postError != null) throw postError!;
    return postResponse ??
        ServiceRecordResponse(
          stored: true,
          verification: ServiceRecordVerification(
            status: 'accepted',
            checks: [
              VerificationCheck(code: 'date_not_future', passed: true, message: 'Date is valid'),
            ],
          ),
        );
  }

  @override
  Future<MaintenanceTimeline> getTimeline(String vehicleId, {bool includeFlagged = false}) async {
    lastIncludeFlagged = includeFlagged;
    return timelineResponse;
  }
}

class MockTelematicsApi extends TelematicsApi {
  @override
  Future<List<TripAggregate>> getAggregates(String vehicleId) async => [];
}

class FakeAuthNotifier extends AuthNotifier {
  final String? testVehicleId;
  final bool testIsDemo;

  FakeAuthNotifier({this.testVehicleId = 'test-veh-001', this.testIsDemo = false});

  @override
  AuthState build() {
    return AuthState(vehicleId: testVehicleId, policyId: 'test-policy', isLoading: false, isDemo: testIsDemo);
  }
}

QuoteResponse createTestQuote({
  String healthStatus = 'scored',
  String healthConfidence = 'verified',
  double? maintenanceScore = 90.0,
}) {
  return QuoteResponse(
    finalPremium: 24960.0,
    basePremium: 24960.0,
    dynamicMultiplier: 1.0,
    scoreDetail: ScoreDetail(
      usageScore: 85.0,
      maintenanceScore: maintenanceScore,
      compositeScore: 87.0,
      vehicleHealthScore: maintenanceScore,
      healthStatus: healthStatus,
      healthConfidence: healthConfidence,
      hasOpenRecall: false,
      dataFlags: [],
      renewalRecommendation: 'Standard renewal',
      contributingFactors: [],
    ),
  );
}

void main() {
  DateTime fixedClock() => DateTime(2026, 10, 21);

  Widget createSubject({
    required Widget child,
    MockMaintenanceApi? maintenanceApi,
    QuoteResponse? quote,
    String? vehicleId = 'test-veh-001',
  }) {
    final mApi = maintenanceApi ?? MockMaintenanceApi();
    final q = quote ?? createTestQuote();

    return ProviderScope(
      overrides: [
        clockProvider.overrideWithValue(fixedClock),
        maintenanceApiProvider.overrideWithValue(mApi),
        telematicsApiProvider.overrideWithValue(MockTelematicsApi()),
        pricingApiProvider.overrideWithValue(MockPricingApi(q)),
        advisoryApiProvider.overrideWithValue(MockAdvisoryApi(AdvisoryResponse(advisories: []))),
        authProvider.overrideWith(() => FakeAuthNotifier(testVehicleId: vehicleId)),
      ],
      child: MaterialApp(
        home: child,
      ),
    );
  }

  group('Add Service Record Form Validation', () {
    testWidgets('Validates required fields, odometer bounds, and date bounds', (tester) async {
      await tester.pumpWidget(createSubject(child: const AddServiceRecordScreen()));
      await tester.pumpAndSettle();

      expect(find.byType(AddServiceRecordScreen), findsOneWidget);

      // 1. Empty submit -> required fields fail
      final submitBtn = find.byKey(const Key('submit_service_record_button'));
      await tester.tap(submitBtn);
      await tester.pumpAndSettle();

      expect(find.text('Odometer reading is required'), findsOneWidget);

      // 2. Odometer > 1500000 -> bound failure
      final odoField = find.byKey(const Key('odometer_field'));
      await tester.enterText(odoField, '2000000');
      await tester.tap(submitBtn);
      await tester.pumpAndSettle();

      expect(find.text('Odometer must be between 0 and 1,500,000 km'), findsOneWidget);

      // 3. Future date -> bound failure
      final dateField = find.byKey(const Key('service_date_field'));
      await tester.enterText(dateField, '2026-11-01');
      await tester.tap(submitBtn);
      await tester.pumpAndSettle();

      expect(find.text('Service date cannot be in the future'), findsOneWidget);

      // 4. Date older than 5 years -> bound failure
      await tester.enterText(dateField, '2020-01-01');
      await tester.tap(submitBtn);
      await tester.pumpAndSettle();

      expect(find.text('Service date cannot be older than 5 years'), findsOneWidget);

      // 5. Valid inputs pass validation
      await tester.enterText(dateField, '2026-09-21');
      await tester.enterText(odoField, '15000');
      await tester.tap(submitBtn);
      await tester.pumpAndSettle();

      expect(find.text('Record Submitted'), findsOneWidget);
    });
  });

  group('Result Views: Accepted, Flagged, Rejected', () {
    testWidgets('Accepted record displays status chip, copy, and check list', (tester) async {
      final mApi = MockMaintenanceApi(
        postResponse: ServiceRecordResponse(
          stored: true,
          verification: ServiceRecordVerification(
            status: 'accepted',
            checks: [
              VerificationCheck(code: 'date_not_future', passed: true, message: 'Date is valid'),
              VerificationCheck(code: 'odometer_monotonic', passed: true, message: 'Odometer is monotonic'),
            ],
          ),
        ),
      );

      await tester.pumpWidget(createSubject(
        child: const AddServiceRecordScreen(),
        maintenanceApi: mApi,
      ));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('service_date_field')), '2026-09-21');
      await tester.enterText(find.byKey(const Key('odometer_field')), '15000');
      await tester.tap(find.byKey(const Key('submit_service_record_button')));
      await tester.pumpAndSettle();

      expect(find.text('Accepted'), findsOneWidget);
      expect(find.text('Saved. Self-reported records can raise your health score up to 85 until a verified source confirms them.'), findsOneWidget);
      expect(find.text('Date is valid'), findsOneWidget);
      expect(find.text('Odometer is monotonic'), findsOneWidget);
    });

    testWidgets('Flagged record displays status chip and review copy', (tester) async {
      final mApi = MockMaintenanceApi(
        postResponse: ServiceRecordResponse(
          stored: true,
          verification: ServiceRecordVerification(
            status: 'flagged',
            checks: [
              VerificationCheck(code: 'odometer_monotonic', passed: false, message: 'Odometer 12000 violates monotonicity'),
            ],
          ),
        ),
      );

      await tester.pumpWidget(createSubject(
        child: const AddServiceRecordScreen(),
        maintenanceApi: mApi,
      ));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('service_date_field')), '2026-10-11');
      await tester.enterText(find.byKey(const Key('odometer_field')), '12000');
      await tester.tap(find.byKey(const Key('submit_service_record_button')));
      await tester.pumpAndSettle();

      expect(find.text('Flagged for review'), findsOneWidget);
      expect(find.text("Saved, but not used in your score until it's reviewed."), findsOneWidget);
      expect(find.text('Odometer 12000 violates monotonicity'), findsOneWidget);
    });

    testWidgets('Rejected record displays Rejected chip and failed hard checks', (tester) async {
      final mApi = MockMaintenanceApi(
        postResponse: ServiceRecordResponse(
          stored: false,
          verification: ServiceRecordVerification(
            status: 'rejected',
            checks: [
              VerificationCheck(code: 'date_after_registration', passed: false, message: 'Service date is before registration date'),
            ],
          ),
        ),
      );

      await tester.pumpWidget(createSubject(
        child: const AddServiceRecordScreen(),
        maintenanceApi: mApi,
      ));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('service_date_field')), '2024-08-12');
      await tester.enterText(find.byKey(const Key('odometer_field')), '5000');
      await tester.tap(find.byKey(const Key('submit_service_record_button')));
      await tester.pumpAndSettle();

      expect(find.text('Rejected'), findsOneWidget);
      expect(find.textContaining('Not saved.'), findsOneWidget);
      expect(find.textContaining('Service date is before registration date'), findsWidgets);
    });

    testWidgets('Unknown status and unknown check code fall back gracefully without throwing', (tester) async {
      final mApi = MockMaintenanceApi(
        postResponse: ServiceRecordResponse(
          stored: false,
          verification: ServiceRecordVerification(
            status: 'under_investigation',
            checks: [
              VerificationCheck(code: 'future_quantum_check_rule', passed: false, message: 'Quantum check failed'),
            ],
          ),
        ),
      );

      await tester.pumpWidget(createSubject(
        child: const AddServiceRecordScreen(),
        maintenanceApi: mApi,
      ));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('service_date_field')), '2026-09-21');
      await tester.enterText(find.byKey(const Key('odometer_field')), '15000');
      await tester.tap(find.byKey(const Key('submit_service_record_button')));
      await tester.pumpAndSettle();

      expect(find.text('under_investigation'), findsOneWidget);
      expect(find.text('future_quantum_check_rule'), findsOneWidget);
      expect(find.text('Quantum check failed'), findsOneWidget);
    });
  });

  group('Consent Required & Error Mappings', () {
    testWidgets('Shows consent required card when health_status is not_evaluated', (tester) async {
      final notEvalQuote = createTestQuote(healthStatus: 'not_evaluated');

      await tester.pumpWidget(createSubject(
        child: const AddServiceRecordScreen(),
        quote: notEvalQuote,
      ));
      await tester.pumpAndSettle();

      expect(find.text('Turn on maintenance tracking to add records'), findsOneWidget);
      expect(find.text('Privacy & tracking'), findsOneWidget);
      expect(find.byKey(const Key('odometer_field')), findsNothing);
    });

    testWidgets('Shows consent required card when API returns 403 CONSENT_REQUIRED', (tester) async {
      final mApi = MockMaintenanceApi(
        postError: ApiException.fromJson(403, {
          'error': 'CONSENT_REQUIRED',
          'message': 'Maintenance tracking consent is required to submit service records.',
        }),
      );

      await tester.pumpWidget(createSubject(
        child: const AddServiceRecordScreen(),
        maintenanceApi: mApi,
      ));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('service_date_field')), '2026-09-21');
      await tester.enterText(find.byKey(const Key('odometer_field')), '15000');
      await tester.tap(find.byKey(const Key('submit_service_record_button')));
      await tester.pumpAndSettle();

      expect(find.text('Turn on maintenance tracking to add records'), findsOneWidget);
      expect(find.text('Privacy & tracking'), findsOneWidget);
    });

    testWidgets('Maps VEHICLE_NOT_REGISTERED to expected message', (tester) async {
      final mApi = MockMaintenanceApi(
        postError: ApiException.fromJson(404, {
          'error': 'VEHICLE_NOT_REGISTERED',
          'message': 'Vehicle is not registered in system',
        }),
      );

      await tester.pumpWidget(createSubject(
        child: const AddServiceRecordScreen(),
        maintenanceApi: mApi,
      ));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('service_date_field')), '2026-09-21');
      await tester.enterText(find.byKey(const Key('odometer_field')), '15000');
      await tester.tap(find.byKey(const Key('submit_service_record_button')));
      await tester.pumpAndSettle();

      expect(find.text("This vehicle isn't registered."), findsOneWidget);
    });

    testWidgets('Maps 503 SERVICE_UNAVAILABLE to expected message', (tester) async {
      final mApi = MockMaintenanceApi(
        postError: ApiException.fromJson(503, {
          'error': 'SERVICE_UNAVAILABLE',
          'message': 'Maintenance service down',
        }),
      );

      await tester.pumpWidget(createSubject(
        child: const AddServiceRecordScreen(),
        maintenanceApi: mApi,
      ));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('service_date_field')), '2026-09-21');
      await tester.enterText(find.byKey(const Key('odometer_field')), '15000');
      await tester.tap(find.byKey(const Key('submit_service_record_button')));
      await tester.pumpAndSettle();

      expect(find.text('Service temporarily unavailable. Please try again.'), findsOneWidget);
    });

    testWidgets('Maps INVALID_REQUEST to server message', (tester) async {
      final mApi = MockMaintenanceApi(
        postError: ApiException.fromJson(400, {
          'error': 'INVALID_REQUEST',
          'message': 'Request failed contract schema validation: \$.odometer_km is missing',
        }),
      );

      await tester.pumpWidget(createSubject(
        child: const AddServiceRecordScreen(),
        maintenanceApi: mApi,
      ));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('service_date_field')), '2026-09-21');
      await tester.enterText(find.byKey(const Key('odometer_field')), '15000');
      await tester.tap(find.byKey(const Key('submit_service_record_button')));
      await tester.pumpAndSettle();

      expect(find.text('Request failed contract schema validation: \$.odometer_km is missing'), findsOneWidget);
    });
  });

  group('History Screen Flagged Switch and Notice', () {
    testWidgets('Displays notice, Show flagged switch, and labels flagged row', (tester) async {
      final timeline = MaintenanceTimeline(
        vehicleId: 'test-v1',
        totalServices: 2,
        flaggedCount: 1,
        events: [
          TimelineEvent(
            eventId: 'ev-1',
            serviceDate: '2026-09-21',
            serviceType: 'oil_change',
            odometerKm: 15000,
            source: 'self_upload',
            verificationStatus: 'accepted',
          ),
          TimelineEvent(
            eventId: 'ev-2',
            serviceDate: '2026-10-11',
            serviceType: 'tyre_rotation',
            odometerKm: 12000,
            source: 'self_upload',
            verificationStatus: 'flagged',
          ),
        ],
      );

      final mApi = MockMaintenanceApi(timeline: timeline);

      await tester.pumpWidget(createSubject(
        child: const HistoryScreen(),
        maintenanceApi: mApi,
      ));
      await tester.pumpAndSettle();

      // Notice should show "1 flagged record is not used in your score"
      expect(find.text('1 flagged record is not used in your score'), findsOneWidget);
      expect(find.text('Show flagged'), findsOneWidget);

      // Flagged row initially hidden
      expect(find.text('Flagged — not used in scoring'), findsNothing);
      expect(find.text('Accepted'), findsOneWidget);

      // Toggle "Show flagged" switch
      final switchFinder = find.byKey(const Key('show_flagged_switch'));
      await tester.tap(switchFinder);
      await tester.pumpAndSettle();

      // Now flagged row is visible and labeled
      expect(mApi.lastIncludeFlagged, isTrue);
      expect(find.text('Flagged — not used in scoring'), findsOneWidget);
      expect(find.text('Accepted'), findsOneWidget);
    });
  });

  group('Dashboard Badge Text & Cap Explanation', () {
    testWidgets('unverified_self_reported badge reads "Includes self-reported records" and explains 85 cap on tap', (tester) async {
      final selfReportedQuote = createTestQuote(
        healthConfidence: 'unverified_self_reported',
        maintenanceScore: 85.0,
      );

      await tester.pumpWidget(createSubject(
        child: const DashboardScreen(),
        quote: selfReportedQuote,
      ));
      await tester.pumpAndSettle();

      // Verify badge text
      expect(find.text('Includes self-reported records'), findsOneWidget);

      // Tap badge to see 85 cap explanation
      await tester.tap(find.text('Includes self-reported records'));
      await tester.pumpAndSettle();

      expect(find.text('Self-reported records can raise your health score up to 85 until a verified source confirms them.'), findsOneWidget);
    });
  });
}
