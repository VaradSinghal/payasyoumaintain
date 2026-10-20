import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/models/models.dart';
import 'package:mobile_app/providers/api_providers.dart';
import 'package:mobile_app/screens/onboarding_screen.dart';
import 'package:mobile_app/services/api_clients.dart';
import 'package:mobile_app/widgets/pricing_basis_widget.dart';

class FakeTestPricingApi extends PricingPolicyApi {
  List<VehicleSegment> catalogue;
  bool throwCatalogueError = false;
  QuoteResponse? quoteResponse;

  FakeTestPricingApi({
    required this.catalogue,
    this.throwCatalogueError = false,
    this.quoteResponse,
  });

  @override
  Future<List<VehicleSegment>> getVehicleCatalogue() async {
    if (throwCatalogueError) {
      throw Exception('Catalogue service unavailable');
    }
    return catalogue;
  }

  @override
  Future<QuoteResponse> quote(String vehicleId) async {
    return quoteResponse ??
        QuoteResponse(
          vehicleId: vehicleId,
          basePremium: 24960.0,
          finalPremium: 24960.0,
          dynamicMultiplier: 1.0,
          currency: 'INR',
          pricingBasis: PricingBasis(
            source: 'registered',
            vehicleSegmentId: 'sedan_mid',
            exShowroomPriceInr: 1200000,
            vehicleAgeYears: 2,
            depreciationFactor: 0.2,
            idv: 960000.0,
            ncbPct: 20.0,
          ),
        );
  }
}

class FakeTestIdentityApi extends IdentityConsentApi {
  Map<String, dynamic>? lastRegisterBody;
  int registerCallCount = 0;

  @override
  Future<VehicleRegistration> registerVehicle(Map<String, dynamic> requestData) async {
    registerCallCount++;
    lastRegisterBody = requestData;
    return VehicleRegistration(
      vehicleId: 'test-reg-id',
      policyId: 'test-policy-id',
      rcNumber: requestData['rc_number'] ?? 'DL01DEMO9999',
      kycStatus: 'VERIFIED',
      vehicleSegmentId: requestData['vehicle_segment_id'],
    );
  }
}

final testCatalogue = [
  VehicleSegment(id: 'hatchback_compact', label: 'Compact hatchback', exShowroomPriceInr: 800000),
  VehicleSegment(id: 'sedan_entry', label: 'Entry sedan', exShowroomPriceInr: 900000),
  VehicleSegment(id: 'suv_compact', label: 'Compact SUV', exShowroomPriceInr: 1000000),
  VehicleSegment(id: 'sedan_mid', label: 'Mid-size sedan', exShowroomPriceInr: 1200000),
  VehicleSegment(id: 'suv_premium', label: 'Premium SUV', exShowroomPriceInr: 1500000),
  VehicleSegment(id: 'suv_luxury', label: 'Luxury SUV', exShowroomPriceInr: 1800000),
];

void main() {
  group('Vehicle Picker Widget Tests', () {
    testWidgets('Picker loads and shows 6 options from catalogue', (tester) async {
      final pricingApi = FakeTestPricingApi(catalogue: testCatalogue);
      final identityApi = FakeTestIdentityApi();

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

      await tester.tap(find.text('Register my vehicle'));
      await tester.pumpAndSettle();

      // Find the vehicle type dropdown
      expect(find.byKey(const Key('vehicle_type_dropdown')), findsOneWidget);

      // Tap dropdown to expand options
      await tester.tap(find.byKey(const Key('vehicle_type_dropdown')));
      await tester.pumpAndSettle();

      // Verify all 6 options are displayed
      expect(find.text('Compact hatchback (indicative ex-showroom ₹8,00,000)'), findsWidgets);
      expect(find.text('Entry sedan (indicative ex-showroom ₹9,00,000)'), findsWidgets);
      expect(find.text('Compact SUV (indicative ex-showroom ₹10,00,000)'), findsWidgets);
      expect(find.text('Mid-size sedan (indicative ex-showroom ₹12,00,000)'), findsWidgets);
      expect(find.text('Premium SUV (indicative ex-showroom ₹15,00,000)'), findsWidgets);
      expect(find.text('Luxury SUV (indicative ex-showroom ₹18,00,000)'), findsWidgets);
    });

    testWidgets('Requires a selection and displays error when empty', (tester) async {
      final pricingApi = FakeTestPricingApi(catalogue: testCatalogue);
      final identityApi = FakeTestIdentityApi();

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

      await tester.tap(find.text('Register my vehicle'));
      await tester.pumpAndSettle();

      // Tap "Register Vehicle" without selecting a vehicle segment
      await tester.ensureVisible(find.text('Register Vehicle'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Register Vehicle'));
      await tester.pumpAndSettle();

      // Error must be displayed and no API call made
      expect(find.text('Please select a vehicle type'), findsWidgets);
      expect(identityApi.registerCallCount, 0);
    });

    testWidgets('Shows error state with working Retry button after catalogue failure', (tester) async {
      final pricingApi = FakeTestPricingApi(
        catalogue: testCatalogue,
        throwCatalogueError: true,
      );
      final identityApi = FakeTestIdentityApi();

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

      await tester.tap(find.text('Register my vehicle'));
      await tester.pumpAndSettle();

      // Error message and Retry button present
      expect(find.text('Could not load vehicle catalogue.'), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);

      // Fix backend failure and tap Retry
      pricingApi.throwCatalogueError = false;
      await tester.tap(find.text('Retry'));
      await tester.pumpAndSettle();

      // Dropdown is now displayed
      expect(find.byKey(const Key('vehicle_type_dropdown')), findsOneWidget);
      expect(find.text('Could not load vehicle catalogue.'), findsNothing);
    });

    testWidgets('Registration request body includes vehicle_segment_id from selection', (tester) async {
      final pricingApi = FakeTestPricingApi(catalogue: testCatalogue);
      final identityApi = FakeTestIdentityApi();

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

      await tester.tap(find.text('Register my vehicle'));
      await tester.pumpAndSettle();

      // Open dropdown and select 'Compact SUV'
      await tester.tap(find.byKey(const Key('vehicle_type_dropdown')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Compact SUV (indicative ex-showroom ₹10,00,000)').last);
      await tester.pumpAndSettle();

      // Tap Register Vehicle
      await tester.ensureVisible(find.text('Register Vehicle'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Register Vehicle'));
      await tester.pumpAndSettle();

      expect(identityApi.registerCallCount, 1);
      expect(identityApi.lastRegisterBody?['vehicle_segment_id'], 'suv_compact');
    });

    testWidgets('Demo-details date computed from injected clock', (tester) async {
      final pricingApi = FakeTestPricingApi(catalogue: testCatalogue);
      final identityApi = FakeTestIdentityApi();
      DateTime fixedClock() => DateTime(2028, 6, 15);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            pricingApiProvider.overrideWithValue(pricingApi),
            identityApiProvider.overrideWithValue(identityApi),
            clockProvider.overrideWithValue(fixedClock),
          ],
          child: const MaterialApp(home: OnboardingScreen()),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Register my vehicle'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Fill demo details'));
      await tester.pumpAndSettle();

      // Today is 2028-06-15, today minus 2 years is 2026-06-15
      expect(find.text('2026-06-15'), findsOneWidget);
      expect(find.text('Mid-size sedan (indicative ex-showroom ₹12,00,000)'), findsOneWidget);
    });

    testWidgets('Registration date picker bounds (firstDate: 25 years ago, lastDate: today)', (tester) async {
      final pricingApi = FakeTestPricingApi(catalogue: testCatalogue);
      final identityApi = FakeTestIdentityApi();
      DateTime fixedClock() => DateTime(2026, 10, 20);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            pricingApiProvider.overrideWithValue(pricingApi),
            identityApiProvider.overrideWithValue(identityApi),
            clockProvider.overrideWithValue(fixedClock),
          ],
          child: const MaterialApp(home: OnboardingScreen()),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Register my vehicle'));
      await tester.pumpAndSettle();

      // Tap calendar icon to open date picker
      await tester.tap(find.byIcon(Icons.calendar_today));
      await tester.pumpAndSettle();

      expect(find.byType(DatePickerDialog), findsOneWidget);
      final dialog = tester.widget<DatePickerDialog>(find.byType(DatePickerDialog));

      expect(dialog.lastDate, DateTime(2026, 10, 20));
      expect(dialog.firstDate, DateTime(2001, 10, 20));
    });
  });

  group('PricingBasisSection Widget Tests', () {
    testWidgets('Renders registered source with catalogue label and formatting', (tester) async {
      final basis = PricingBasis(
        source: 'registered',
        vehicleSegmentId: 'sedan_mid',
        exShowroomPriceInr: 1200000,
        vehicleAgeYears: 2,
        depreciationFactor: 0.2,
        idv: 960000.0,
        ncbPct: 20.0,
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: PricingBasisSection(
                pricingBasis: basis,
                catalogue: testCatalogue,
              ),
            ),
          ),
        ),
      );

      expect(find.text('How we priced your vehicle'), findsOneWidget);
      expect(find.text('Your registered vehicle'), findsOneWidget);
      expect(find.text('Mid-size sedan'), findsOneWidget);
      expect(find.text('2 years'), findsOneWidget);
      expect(find.text('₹12,00,000'), findsOneWidget);
      expect(find.text('20%'), findsOneWidget);
      expect(find.text('₹9,60,000.00'), findsOneWidget);
      expect(find.text('20.0%'), findsOneWidget);
    });

    testWidgets('Renders demo_profile source', (tester) async {
      final basis = PricingBasis(
        source: 'demo_profile',
        vehicleSegmentId: 'sedan_mid',
        exShowroomPriceInr: 1200000,
        vehicleAgeYears: 2,
        depreciationFactor: 0.2,
        idv: 960000.0,
        ncbPct: 20.0,
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: PricingBasisSection(
              pricingBasis: basis,
              catalogue: testCatalogue,
            ),
          ),
        ),
      );

      expect(find.text('Demo vehicle profile'), findsOneWidget);
      expect(find.text('Mid-size sedan'), findsOneWidget);
    });

    testWidgets('Renders generic_default source and handles null vehicle_segment_id', (tester) async {
      final basis = PricingBasis(
        source: 'generic_default',
        vehicleSegmentId: null,
        exShowroomPriceInr: 750000,
        vehicleAgeYears: 3,
        depreciationFactor: 0.3,
        idv: 525000.0,
        ncbPct: 0.0,
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: PricingBasisSection(
              pricingBasis: basis,
              catalogue: testCatalogue,
            ),
          ),
        ),
      );

      expect(find.text('Standard profile — no vehicle details on file'), findsOneWidget);
      expect(find.text('Vehicle segment'), findsNothing);
      expect(find.text('3 years'), findsOneWidget);
      expect(find.text('0.0%'), findsOneWidget);
    });

    testWidgets('Falls back to raw segment id when not found in catalogue', (tester) async {
      final basis = PricingBasis(
        source: 'demo_profile',
        vehicleSegmentId: 'custom_electric_sedan',
        exShowroomPriceInr: 2500000,
        vehicleAgeYears: 1,
        depreciationFactor: 0.1,
        idv: 2250000.0,
        ncbPct: 0.0,
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: PricingBasisSection(
              pricingBasis: basis,
              catalogue: testCatalogue,
            ),
          ),
        ),
      );

      expect(find.text('custom_electric_sedan'), findsOneWidget);
    });

    testWidgets('Completely hides section when pricing_basis is null', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: PricingBasisSection(
              pricingBasis: null,
              catalogue: null,
            ),
          ),
        ),
      );

      expect(find.text('How we priced your vehicle'), findsNothing);
      expect(find.byType(Card), findsNothing);
    });
  });
}
