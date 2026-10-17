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

    test('Vehicle with live open recall parses hasOpenRecall true (b2c3 / demo-veh-002)', () {
      final json = loadJson('quote_demo_veh_002.json');
      final quote = QuoteResponse.fromJson(json);

      expect(quote.scoreDetail?.hasOpenRecall, isTrue);
      expect(quote.scoreDetail?.healthStatus, 'scored');
      expect(quote.scoreDetail?.healthConfidence, 'verified');
    });

    test('Conflicting vehicle carries conflicting_records_detected (c3d4 / demo-veh-003)', () {
      final json = loadJson('quote_demo_veh_003.json');
      final quote = QuoteResponse.fromJson(json);

      expect(quote.scoreDetail?.dataFlags, contains('conflicting_records_detected'));
      expect(quote.scoreDetail?.hasOpenRecall, isFalse);
    });

    test('Unverified vehicle parses healthConfidence as unverified_self_reported (d4e5 / demo-veh-004)', () {
      final json = loadJson('quote_demo_veh_004.json');
      final quote = QuoteResponse.fromJson(json);

      expect(quote.scoreDetail?.healthConfidence, 'unverified_self_reported');
      expect(quote.basePremium, 11700.0);
    });

    test('e5f6 and fresh vehicle parse with null usage, maintenance and composite scores and insufficient_data', () {
      final json5 = loadJson('quote_demo_veh_005.json');
      final quote5 = QuoteResponse.fromJson(json5);
      final detail5 = quote5.scoreDetail;

      expect(detail5, isNotNull);
      expect(detail5!.usageScore, isNull);
      expect(detail5.maintenanceScore, isNull);
      expect(detail5.compositeScore, isNull);
      expect(detail5.healthStatus, 'insufficient_data');
      expect(detail5.dataFlags, contains('usage_data_unavailable'));

      final jsonFresh = loadJson('quote_fresh_unregistered.json');
      final quoteFresh = QuoteResponse.fromJson(jsonFresh);
      final detailFresh = quoteFresh.scoreDetail;

      expect(detailFresh, isNotNull);
      expect(detailFresh!.usageScore, isNull);
      expect(detailFresh.maintenanceScore, isNull);
      expect(detailFresh.compositeScore, isNull);
      expect(detailFresh.healthStatus, 'insufficient_data');
      expect(detailFresh.dataFlags, contains('usage_data_unavailable'));
      expect(detailFresh.renewalRecommendation, isNotNull);
      expect(detailFresh.renewalRecommendation!.trim(), isNotEmpty);
    });

    test('Advisories keep the API order and parse completely (b2c3 / demo-veh-002)', () {
      final json002 = loadJson('advisories_demo_veh_002.json');
      final adv002 = AdvisoryResponse.fromJson(json002);

      expect(adv002.advisories, hasLength(3));
      expect(adv002.advisories[0].advisoryType, 'OPEN_RECALL');
      expect(adv002.advisories[0].priority, 'CRITICAL');
      expect(adv002.advisories[0].title, contains('Recall'));
      expect(adv002.advisories[0].actionUrl, 'https://paym.app/recalls');

      expect(adv002.advisories[1].advisoryType, 'OVERDUE_SERVICE');
      expect(adv002.advisories[2].advisoryType, 'LOW_RISK_SCORE');

      // Commendation advisory for demo-veh-001
      final json001 = loadJson('advisories_demo_veh_001.json');
      final adv001 = AdvisoryResponse.fromJson(json001);
      expect(adv001.advisories, hasLength(1));
      expect(adv001.advisories.first.advisoryType, 'SAFE_DRIVING_COMMENDATION');
    });

    test('Empty timeline and aggregates parse as empty lists for e5f6...', () {
      final timelineJson = loadJson('timeline_empty_200.json');
      final timeline = MaintenanceTimeline.fromJson(timelineJson);

      expect(timeline.totalServices, 0);
      expect(timeline.events, isEmpty);

      final aggregatesFile = File('${liveDir.path}/aggregates_empty_200.json');
      final aggregatesList = jsonDecode(aggregatesFile.readAsStringSync()) as List<dynamic>;
      final aggregates = TripAggregate.fromJsonList(aggregatesList);

      expect(aggregates, isEmpty);
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

    test('Standard error bodies become ApiException carrying server error and message', () {
      final err404Json = loadJson('standard_error_404.json');
      final ex404 = ApiException.fromJson(404, err404Json);
      expect(ex404.statusCode, 404);
      expect(ex404.error, 'NOT_FOUND');
      expect(ex404.message, isNotEmpty);

      final err400Json = loadJson('standard_error_400.json');
      final ex400 = ApiException.fromJson(400, err400Json);
      expect(ex400.statusCode, 400);
      expect(ex400.error, 'INVALID_REQUEST');
      expect(ex400.message, contains('Invalid value'));
    });

    test('Opted out quotes parse accurately with not_evaluated health status', () {
      final qA1b2Off = QuoteResponse.fromJson(loadJson('quote_a1b2_opted_out.json'));
      expect(qA1b2Off.scoreDetail?.healthStatus, 'not_evaluated');
      expect(qA1b2Off.scoreDetail?.usageScore, isNull);
      expect(qA1b2Off.scoreDetail?.maintenanceScore, isNull);
      expect(qA1b2Off.scoreDetail?.compositeScore, isNull);
      expect(qA1b2Off.scoreDetail?.hasOpenRecall, isNull);
      expect(qA1b2Off.scoreDetail?.dataFlags, contains('consent_not_given_usage'));
      expect(qA1b2Off.scoreDetail?.dataFlags, contains('consent_not_given_maintenance'));
      expect(qA1b2Off.discountBreakdown.any((d) => d.reason == 'standard_pricing_no_consent'), isTrue);
      expect(qA1b2Off.scoreDetail?.contributingFactors.any((f) => f.factorName == 'tracking_off'), isTrue);

      final qNewOff = QuoteResponse.fromJson(loadJson('quote_new_reg_opted_out.json'));
      expect(qNewOff.scoreDetail?.healthStatus, 'not_evaluated');
      expect(qNewOff.discountBreakdown.any((d) => d.reason == 'standard_pricing_no_consent'), isTrue);

      final qUnavail = QuoteResponse.fromJson(loadJson('quote_consent_status_unavailable.json'));
      expect(qUnavail.scoreDetail?.healthStatus, 'not_evaluated');
      expect(qUnavail.scoreDetail?.dataFlags, contains('consent_status_unavailable'));
    });

    test('Opted out and opted in advisories fixtures parse correctly', () {
      final advA1b2Off = AdvisoryResponse.fromJson(loadJson('advisories_a1b2_opted_out.json'));
      expect(advA1b2Off.advisories, hasLength(1));
      expect(advA1b2Off.advisories.first.advisoryType, 'TRACKING_DISABLED');
      expect(advA1b2Off.advisories.first.actionUrl, 'https://paym.app/settings/consent');

      final advNewOff = AdvisoryResponse.fromJson(loadJson('advisories_new_reg_opted_out.json'));
      expect(advNewOff.advisories.first.advisoryType, 'TRACKING_DISABLED');

      final advNewIn = AdvisoryResponse.fromJson(loadJson('advisories_new_reg_opted_in.json'));
      expect(advNewIn.advisories.first.advisoryType, 'NO_SERVICE_RECORD');
    });

    test('New registration and consent toggles parse correctly', () {
      final regNew = VehicleRegistration.fromJson(loadJson('registration_new_opted_out_201.json'));
      expect(regNew.vehicleId, isNotNull);
      expect(regNew.policyId, isNotNull);
      expect(regNew.kycStatus, 'VERIFIED');

      final getOff = ConsentRecord.fromJson(loadJson('consent_get_opted_out_200.json'));
      expect(getOff.usageTrackingOptIn, isFalse);
      expect(getOff.maintenanceTrackingOptIn, isFalse);

      final putOff = ConsentRecord.fromJson(loadJson('consent_put_opted_out_200.json'));
      expect(putOff.usageTrackingOptIn, isFalse);
      expect(putOff.maintenanceTrackingOptIn, isFalse);

      final getIn = ConsentRecord.fromJson(loadJson('consent_get_opted_in_200.json'));
      expect(getIn.usageTrackingOptIn, isTrue);
      expect(getIn.maintenanceTrackingOptIn, isTrue);

      final putIn = ConsentRecord.fromJson(loadJson('consent_put_opted_in_200.json'));
      expect(putIn.usageTrackingOptIn, isTrue);
      expect(putIn.maintenanceTrackingOptIn, isTrue);
    });
  });
}
