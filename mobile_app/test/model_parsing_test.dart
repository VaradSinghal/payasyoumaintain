import 'dart:convert';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/models/models.dart';
import 'package:mobile_app/services/api_exception.dart';

void main() {
  group('Live Fixture Model Parsing', () {
    final liveDir = Directory('test/fixtures/live');

    Map<String, dynamic> loadJson(String filename) {
      final file = File('${liveDir.path}/$filename');
      return jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
    }

    test('Registration 201 fixture parses correctly', () {
      final json = loadJson('registration_201.json');
      final reg = VehicleRegistration.fromJson(json);

      expect(reg.vehicleId, isNotNull);
      expect(reg.policyId, isNotNull);
      expect(reg.rcNumber, 'KA01MJ9999');
      expect(reg.kycStatus, 'VERIFIED');
    });

    test('Consent GET and PUT 200 fixtures parse correctly', () {
      final getJson = loadJson('consent_get_200.json');
      final getConsent = ConsentRecord.fromJson(getJson);
      expect(getConsent.usageTrackingOptIn, isFalse);
      expect(getConsent.maintenanceTrackingOptIn, isFalse);

      final putJson = loadJson('consent_put_200.json');
      final putConsent = ConsentRecord.fromJson(putJson);
      expect(putConsent.usageTrackingOptIn, isTrue);
      expect(putConsent.maintenanceTrackingOptIn, isTrue);
    });

    test('Demo vehicle quotes parse accurately against live bodies', () {
      final demoFiles = [
        'quote_demo_veh_001.json',
        'quote_demo_veh_002.json',
        'quote_demo_veh_003.json',
        'quote_demo_veh_004.json',
        'quote_demo_veh_005.json',
      ];

      for (final f in demoFiles) {
        final json = loadJson(f);
        final quote = QuoteResponse.fromJson(json);

        expect(quote.finalPremium, greaterThan(0));
        expect(quote.dynamicMultiplier, greaterThan(0));
        expect(quote.scoreDetail, isNotNull);

        final detail = quote.scoreDetail!;
        final rawDetail = json['score_detail'] as Map<String, dynamic>;

        // healthConfidence and dataFlags match exactly
        expect(detail.healthConfidence, rawDetail['health_confidence']);
        expect(detail.dataFlags, rawDetail['data_flags']);

        // numeric vehicle_health_score has non-empty renewalRecommendation
        if (detail.vehicleHealthScore != null) {
          expect(detail.renewalRecommendation, isNotNull);
          expect(detail.renewalRecommendation!.trim(), isNotEmpty);
        }

        // contributing_factors parsed properly
        final rawFactors = rawDetail['contributing_factors'] as List<dynamic>;
        expect(detail.contributingFactors.length, rawFactors.length);
        for (int i = 0; i < rawFactors.length; i++) {
          final rawFactor = rawFactors[i] as Map<String, dynamic>;
          final factor = detail.contributingFactors[i];
          expect(factor.factorName, rawFactor['factor_name']);
          expect(factor.impact, (rawFactor['impact'] as num).toDouble());
          expect(factor.direction, rawFactor['direction']);
          expect(factor.description, rawFactor['description']);
        }
      }
    });

    test('Vehicle with live open recall parses hasOpenRecall true (demo-veh-003)', () {
      final json = loadJson('quote_demo_veh_003.json');
      final quote = QuoteResponse.fromJson(json);

      expect(quote.scoreDetail?.hasOpenRecall, isTrue);
      expect(quote.scoreDetail?.healthStatus, 'scored');
      expect(quote.scoreDetail?.vehicleHealthScore, 55.0);
      expect(quote.scoreDetail?.healthConfidence, 'verified');
    });

    test('Fresh unregistered vehicle parses insufficient_data with null scores', () {
      final json = loadJson('quote_fresh_unregistered.json');
      final quote = QuoteResponse.fromJson(json);
      final detail = quote.scoreDetail;

      expect(detail, isNotNull);
      expect(detail!.healthStatus, 'insufficient_data');
      expect(detail.maintenanceScore, isNull);
      expect(detail.compositeScore, isNull);
      expect(detail.vehicleHealthScore, isNull);
      expect(detail.healthConfidence, isNull);
      expect(detail.hasOpenRecall, isFalse);
      expect(detail.renewalRecommendation, isNotNull);
      expect(detail.renewalRecommendation!.trim(), isNotEmpty);
    });

    test('Derived unverified fixture parses healthConfidence as unverified_self_reported', () {
      final json = loadJson('quote_demo_veh_001_derived_unverified.json');
      final quote = QuoteResponse.fromJson(json);

      expect(quote.scoreDetail?.healthConfidence, 'unverified_self_reported');
    });

    test('Advisories keep the API order and parse completely', () {
      final json003 = loadJson('advisories_demo_veh_003.json');
      final adv003 = AdvisoryResponse.fromJson(json003);

      expect(adv003.advisories, isNotEmpty);
      expect(adv003.advisories.first.advisoryType, 'OPEN_RECALL');
      expect(adv003.advisories.first.priority, 'CRITICAL');
      expect(adv003.advisories.first.title, contains('Recall'));
      expect(adv003.advisories.first.actionUrl, 'https://paym.app/recalls');

      // Empty advisories for demo-veh-001
      final json001 = loadJson('advisories_demo_veh_001.json');
      final adv001 = AdvisoryResponse.fromJson(json001);
      expect(adv001.advisories, isEmpty);
    });

    test('FNOL 201 and Claim GET 200 fixtures parse correctly', () {
      final fnolJson = loadJson('fnol_201.json');
      final fnol = FnolResponse.fromJson(fnolJson);

      expect(fnol.claimId, isNotNull);
      expect(fnol.triageDecision, 'MANUAL_REVIEW');
      expect(fnol.claimedCause, 'mechanical_failure');
      expect(fnol.triageReasons, isNotEmpty);

      final claimJson = loadJson('claim_get_200.json');
      final claim = FnolResponse.fromJson(claimJson);
      expect(claim.claimId, fnol.claimId);
      expect(claim.triageDecision, 'MANUAL_REVIEW');
    });

    test('DTC reading fixtures parse correctly', () {
      final postJson = loadJson('dtc_post_200.json');
      final reading = DtcReading.fromJson(postJson);
      expect(reading.source, 'obd_device');
      expect(reading.deviceId, 'obd-dongle-live-42');
      expect(reading.confirmedCodes, ['P0300', 'P0171']);
      expect(reading.pendingCodes, ['P0420']);

      final file = File('${liveDir.path}/dtc_history_200.json');
      final list = jsonDecode(file.readAsStringSync()) as List<dynamic>;
      final history = list.map((e) => DtcReading.fromJson(e as Map<String, dynamic>)).toList();
      expect(history, hasLength(1));
      expect(history.first.confirmedCodes, ['P0300', 'P0171']);
    });

    test('Standard error body becomes ApiException carrying the server message', () {
      final errJson = loadJson('standard_error_404.json');
      final exception = ApiException.fromJson(404, errJson);

      expect(exception.statusCode, 404);
      expect(exception.message, 'The requested resource could not be found.');
      expect(exception.error, 'NOT_FOUND');
    });
  });
}
