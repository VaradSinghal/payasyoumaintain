import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_app/models/models.dart';
import 'package:mobile_app/providers/api_providers.dart';
import 'package:mobile_app/providers/auth_provider.dart';
import 'package:mobile_app/screens/claims_screen.dart';
import 'package:mobile_app/services/api_clients.dart';
import 'package:mobile_app/services/api_exception.dart';

class FakeClaimsApi extends ClaimsVerificationApi {
  ApiException? errorToThrow;
  FnolResponse? fnolResponseToReturn;
  Map<String, dynamic>? claimDetailsToReturn;

  @override
  Future<FnolResponse> submitFnol(FnolRequest request) async {
    if (errorToThrow != null) {
      throw errorToThrow!;
    }
    return fnolResponseToReturn ?? FnolResponse(claimId: 'test-claim-123');
  }

  @override
  Future<Map<String, dynamic>> getClaim(String claimId) async {
    if (errorToThrow != null) {
      throw errorToThrow!;
    }
    return claimDetailsToReturn ?? {
      'claim_id': claimId,
      'status': 'OPEN',
      'triage_decision': 'MANUAL_REVIEW',
      'triage_reasons': ['open_recall_on_file: vehicle has 1 open manufacturer recall(s)'],
    };
  }
}

class FakeAuthNotifier extends AuthNotifier {
  @override
  AuthState build() => AuthState(
        isLoading: false,
        vehicleId: 'test-veh-id',
        policyId: 'test-pol-id',
        isDemo: true,
      );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Widget createTestWidget(FakeClaimsApi api) {
    return ProviderScope(
      overrides: [
        claimsApiProvider.overrideWithValue(api),
        authProvider.overrideWith(() => FakeAuthNotifier()),
      ],
      child: const MaterialApp(
        home: ClaimsScreen(),
      ),
    );
  }

  group('ClaimsScreen error mapping tests', () {
    setUp(() {
      final binding = TestWidgetsFlutterBinding.ensureInitialized();
      binding.platformDispatcher.views.first.physicalSize = const Size(800, 1200);
      binding.platformDispatcher.views.first.devicePixelRatio = 1.0;
    });

    tearDown(() {
      final binding = TestWidgetsFlutterBinding.ensureInitialized();
      binding.platformDispatcher.views.first.resetPhysicalSize();
      binding.platformDispatcher.views.first.resetDevicePixelRatio();
    });

    testWidgets('POLICY_VEHICLE_MISMATCH maps to plain message', (tester) async {
      final api = FakeClaimsApi()
        ..errorToThrow = ApiException(400, 'Policy mismatch', error: 'POLICY_VEHICLE_MISMATCH');

      await tester.pumpWidget(createTestWidget(api));
      await tester.pumpAndSettle();

      // Fill in claimed cause
      await tester.tap(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('mechanical_failure').last);
      await tester.pumpAndSettle();

      // Enter description
      await tester.enterText(find.byKey(const Key('fnol_description_field')), 'Engine failure');
      await tester.pumpAndSettle();

      // Submit
      await tester.ensureVisible(find.text('Submit Claim'));
      await tester.tap(find.text('Submit Claim'));
      await tester.pumpAndSettle();

      expect(find.text("This policy doesn't match the selected vehicle."), findsOneWidget);
    });

    testWidgets('VEHICLE_NOT_REGISTERED maps to plain message', (tester) async {
      final api = FakeClaimsApi()
        ..errorToThrow = ApiException(404, 'Vehicle not registered', error: 'VEHICLE_NOT_REGISTERED');

      await tester.pumpWidget(createTestWidget(api));
      await tester.pumpAndSettle();

      await tester.tap(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('collision').last);
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('fnol_description_field')), 'Fender bender');
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Submit Claim'));
      await tester.tap(find.text('Submit Claim'));
      await tester.pumpAndSettle();

      expect(find.text("This vehicle isn't registered."), findsOneWidget);
    });

    testWidgets('INVALID_INCIDENT_DATE maps to plain message', (tester) async {
      final api = FakeClaimsApi()
        ..errorToThrow = ApiException(400, 'Invalid date', error: 'INVALID_INCIDENT_DATE');

      await tester.pumpWidget(createTestWidget(api));
      await tester.pumpAndSettle();

      await tester.tap(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('theft').last);
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('fnol_description_field')), 'Car stolen');
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Submit Claim'));
      await tester.tap(find.text('Submit Claim'));
      await tester.pumpAndSettle();

      expect(find.text("The incident date must be within the last year and not in the future."), findsOneWidget);
    });

    testWidgets('Unknown server error keeps showing the server message', (tester) async {
      final api = FakeClaimsApi()
        ..errorToThrow = ApiException(500, 'Database connection timeout', error: 'DATABASE_TIMEOUT');

      await tester.pumpWidget(createTestWidget(api));
      await tester.pumpAndSettle();

      await tester.tap(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('vandalism').last);
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('fnol_description_field')), 'Broken glass');
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Submit Claim'));
      await tester.tap(find.text('Submit Claim'));
      await tester.pumpAndSettle();

      expect(find.text('Database connection timeout'), findsOneWidget);
    });

    testWidgets('Incident date picker enforces bounds (today and 365 days ago)', (tester) async {
      final api = FakeClaimsApi();
      await tester.pumpWidget(createTestWidget(api));
      await tester.pumpAndSettle();

      // Tap date picker decorator
      await tester.tap(find.text('Select Date'));
      await tester.pumpAndSettle();

      // Dialog is open
      expect(find.byType(DatePickerDialog), findsOneWidget);

      final datePicker = tester.widget<DatePickerDialog>(find.byType(DatePickerDialog));
      final now = DateTime.now();
      final today = DateTime(now.year, now.month, now.day);
      final expectedFirst = today.subtract(const Duration(days: 365));

      expect(datePicker.lastDate.year, today.year);
      expect(datePicker.lastDate.month, today.month);
      expect(datePicker.lastDate.day, today.day);

      expect(datePicker.firstDate.year, expectedFirst.year);
      expect(datePicker.firstDate.month, expectedFirst.month);
      expect(datePicker.firstDate.day, expectedFirst.day);

      // Dismiss dialog
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
    });

    testWidgets('Submitting valid claim transitions to claim status with manual review and triage reasons', (tester) async {
      final api = FakeClaimsApi()
        ..fnolResponseToReturn = FnolResponse(claimId: 'claim-xyz-999')
        ..claimDetailsToReturn = {
          'claim_id': 'claim-xyz-999',
          'status': 'OPEN',
          'triage_decision': 'MANUAL_REVIEW',
          'triage_reasons': [
            "Claimed cause is 'mechanical_failure'.",
            "open_recall_on_file: vehicle has 1 open manufacturer recall(s) — a pending recall increases mechanical failure plausibility and requires investigator review.",
          ],
        };

      await tester.pumpWidget(createTestWidget(api));
      await tester.pumpAndSettle();

      await tester.tap(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('mechanical_failure').last);
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('fnol_description_field')), 'Brakes failed');
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Submit Claim'));
      await tester.tap(find.text('Submit Claim'));
      await tester.pumpAndSettle();

      // Check transition to result screen
      expect(find.text('claim-xyz-999'), findsOneWidget);
      expect(find.text('Requires Manual Review'), findsOneWidget);
      expect(find.textContaining('open_recall_on_file'), findsOneWidget);
    });
  });
}
