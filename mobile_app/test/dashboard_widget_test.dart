import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_app/screens/dashboard_screen.dart';
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

class FakeAuthNotifier extends AuthNotifier {
  @override
  AuthState build() {
    return AuthState(vehicleId: 'test-vehicle', policyId: 'test-policy', isLoading: false);
  }
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

  group('Dashboard Widget Tests', () {
    testWidgets('Normal vehicle', (tester) async {
      final pricingApi = FakePricingApi(QuoteResponse(
        finalPremium: 85.00,
        dynamicMultiplier: 0.85,
        discountBreakdown: [],
        scoreDetail: ScoreDetail(
          usageScore: 90.0,
          maintenanceScore: 85.0,
          compositeScore: 88.0,
          vehicleHealthScore: 88.0,
          healthStatus: 'scored',
          healthConfidence: 'verified',
          hasOpenRecall: false,
          dataFlags: [],
          renewalRecommendation: 'RENEW',
          contributingFactors: [],
        ),
      ));
      final advisoryApi = FakeAdvisoryApi(AdvisoryResponse(advisories: [
        AdvisoryMessage(advisoryType: 'test', priority: 'LOW', title: 'Test', message: 'Test message')
      ]));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      final listFinder = find.byType(Scrollable);
      await tester.scrollUntilVisible(find.text('Test message'), 50.0, scrollable: listFinder);

      expect(find.text('\$85.00'), findsOneWidget);
      expect(find.text('RENEW'), findsOneWidget);
      expect(find.text('Usage Score'), findsOneWidget);
      expect(find.text('90 / 100'), findsOneWidget);
      expect(find.text('Maintenance Score'), findsOneWidget);
      expect(find.text('85 / 100'), findsOneWidget);
      expect(find.text('URGENT: Your vehicle has an open safety recall.'), findsNothing);
      expect(find.text('Test message'), findsOneWidget);
    });

    testWidgets('Insufficient data', (tester) async {
      final pricingApi = FakePricingApi(QuoteResponse(
        finalPremium: 100.00,
        dynamicMultiplier: 1.0,
        discountBreakdown: [],
        scoreDetail: ScoreDetail(
          usageScore: 90.0,
          maintenanceScore: null,
          compositeScore: null,
          vehicleHealthScore: null,
          healthStatus: 'insufficient_data',
          healthConfidence: 'verified',
          hasOpenRecall: false,
          dataFlags: [],
          renewalRecommendation: 'RENEW',
          contributingFactors: [],
        ),
      ));
      final advisoryApi = FakeAdvisoryApi(AdvisoryResponse(advisories: []));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      expect(find.text('Insufficient Data'), findsOneWidget);
      expect(find.text('Not enough data yet'), findsWidgets);
    });

    testWidgets('Unverified badge', (tester) async {
      final pricingApi = FakePricingApi(QuoteResponse(
        finalPremium: 100.00,
        dynamicMultiplier: 1.0,
        discountBreakdown: [],
        scoreDetail: ScoreDetail(
          usageScore: 90.0,
          maintenanceScore: 80.0,
          compositeScore: 85.0,
          vehicleHealthScore: 85.0,
          healthStatus: 'scored',
          healthConfidence: 'unverified_self_reported',
          hasOpenRecall: false,
          dataFlags: [],
          renewalRecommendation: 'RENEW',
          contributingFactors: [],
        ),
      ));
      final advisoryApi = FakeAdvisoryApi(AdvisoryResponse(advisories: []));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      expect(find.text('Unverified — self-reported only'), findsOneWidget);
    });

    testWidgets('Recall true', (tester) async {
      final pricingApi = FakePricingApi(QuoteResponse(
        finalPremium: 100.00,
        dynamicMultiplier: 1.0,
        discountBreakdown: [],
        scoreDetail: ScoreDetail(
          usageScore: 90.0,
          maintenanceScore: 80.0,
          compositeScore: 85.0,
          vehicleHealthScore: 85.0,
          healthStatus: 'scored',
          healthConfidence: 'verified',
          hasOpenRecall: true,
          dataFlags: [],
          renewalRecommendation: 'RENEW',
          contributingFactors: [],
        ),
      ));
      final advisoryApi = FakeAdvisoryApi(AdvisoryResponse(advisories: []));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      expect(find.text('URGENT: Your vehicle has an open safety recall.'), findsOneWidget);
    });

    testWidgets('Recall null (amber notice)', (tester) async {
      final pricingApi = FakePricingApi(QuoteResponse(
        finalPremium: 100.00,
        dynamicMultiplier: 1.0,
        discountBreakdown: [],
        scoreDetail: ScoreDetail(
          usageScore: 90.0,
          maintenanceScore: 80.0,
          compositeScore: 85.0,
          vehicleHealthScore: 85.0,
          healthStatus: 'scored',
          healthConfidence: 'verified',
          hasOpenRecall: null,
          dataFlags: [],
          renewalRecommendation: 'RENEW',
          contributingFactors: [],
        ),
      ));
      final advisoryApi = FakeAdvisoryApi(AdvisoryResponse(advisories: []));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      expect(find.text("Recall status couldn't be checked"), findsOneWidget);
    });

    testWidgets('Empty advisories', (tester) async {
      final pricingApi = FakePricingApi(QuoteResponse(
        finalPremium: 100.00,
        dynamicMultiplier: 1.0,
        discountBreakdown: [],
        scoreDetail: ScoreDetail(
          dataFlags: [],
          contributingFactors: [],
        ),
      ));
      final advisoryApi = FakeAdvisoryApi(AdvisoryResponse(advisories: []));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      final listFinder = find.byType(Scrollable);
      await tester.scrollUntilVisible(find.text('All caught up'), 50.0, scrollable: listFinder);

      expect(find.text('All caught up'), findsOneWidget);
    });

    testWidgets('Error state whose retry button refetches', (tester) async {
      final pricingApi = FakePricingApi(QuoteResponse(
        finalPremium: 100.00,
        dynamicMultiplier: 1.0,
        discountBreakdown: [],
      ));
      pricingApi.throwError = true;
      final advisoryApi = FakeAdvisoryApi(AdvisoryResponse(advisories: []));

      await tester.pumpWidget(createTestApp(pricingApi, advisoryApi));
      await tester.pumpAndSettle();

      // Should be in error state
      expect(find.text('Retry'), findsOneWidget);

      // Now disable error and retry
      pricingApi.throwError = false;
      await tester.tap(find.text('Retry'));
      await tester.pumpAndSettle();

      // Should successfully load
      expect(find.text('\$100.00'), findsOneWidget);
    });
  });
}
