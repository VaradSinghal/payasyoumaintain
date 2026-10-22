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

      expect(quote.basePremium, 12480.0);
      expect(quote.finalPremium, 12265.34);
      expect(quote.scoreDetail?.hasOpenRecall, isTrue);
      expect(quote.scoreDetail?.healthStatus, 'scored');
      expect(quote.scoreDetail?.healthConfidence, 'verified');
    });

    test('Conflicting vehicle carries conflicting_records_detected (c3d4 / demo-veh-003)', () {
      final json = loadJson('quote_demo_veh_003.json');
      final quote = QuoteResponse.fromJson(json);

      expect(quote.basePremium, 27300.0);
      expect(quote.finalPremium, 21075.6);
      expect(quote.scoreDetail?.dataFlags, contains('conflicting_records_detected'));
      expect(quote.scoreDetail?.hasOpenRecall, isFalse);
    });

    test('Unverified vehicle parses healthConfidence as unverified_self_reported (d4e5 / demo-veh-004)', () {
      final json = loadJson('quote_demo_veh_004.json');
      final quote = QuoteResponse.fromJson(json);

      expect(quote.basePremium, 11700.0);
      expect(quote.finalPremium, 6563.7);
      expect(quote.scoreDetail?.healthConfidence, 'unverified_self_reported');
    });

    test('e5f6 and fresh vehicle parse with null usage, maintenance and composite scores and insufficient_data', () {
      final json5 = loadJson('quote_demo_veh_005.json');
      final quote5 = QuoteResponse.fromJson(json5);
      expect(quote5.basePremium, 44460.0);
      expect(quote5.finalPremium, 44460.0);
      final detail5 = quote5.scoreDetail;

      expect(detail5, isNotNull);
      expect(detail5!.usageScore, isNull);
      expect(detail5.maintenanceScore, isNull);
      expect(detail5.compositeScore, isNull);
      expect(detail5.healthStatus, 'insufficient_data');
      expect(detail5.dataFlags, contains('usage_data_unavailable'));

      final jsonFresh = loadJson('quote_fresh_unregistered.json');
      final quoteFresh = QuoteResponse.fromJson(jsonFresh);
      expect(quoteFresh.basePremium, 13650.0);
      expect(quoteFresh.finalPremium, 13650.0);
      final detailFresh = quoteFresh.scoreDetail;

      expect(detailFresh, isNotNull);
      expect(detailFresh!.usageScore, isNull);
      expect(detailFresh.maintenanceScore, isNull);
      expect(detailFresh.compositeScore, isNull);
      expect(detailFresh.healthStatus, 'not_evaluated');
      expect(detailFresh.dataFlags, contains('vehicle_not_registered'));
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
      expect(qA1b2Off.basePremium, 24960.0);
      expect(qA1b2Off.finalPremium, 19968.0);
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
      expect(qNewOff.basePremium, 13650.0);
      expect(qNewOff.finalPremium, 13650.0);
      expect(qNewOff.scoreDetail?.healthStatus, 'not_evaluated');
      expect(qNewOff.discountBreakdown.any((d) => d.reason == 'standard_pricing_no_consent'), isTrue);

      final qUnavail = QuoteResponse.fromJson(loadJson('quote_consent_status_unavailable.json'));
      expect(qUnavail.basePremium, 24960.0);
      expect(qUnavail.finalPremium, 19968.0);
      expect(qUnavail.scoreDetail?.healthStatus, 'not_evaluated');
      expect(qUnavail.scoreDetail?.dataFlags, equals(['consent_status_unavailable']));
      expect(qUnavail.scoreDetail?.renewalRecommendation, "We couldn't check your tracking preferences right now, so standard pricing is shown.");

      final qNewIn = QuoteResponse.fromJson(loadJson('quote_new_reg_opted_in.json'));
      expect(qNewIn.basePremium, 13650.0);
      expect(qNewIn.finalPremium, 13650.0);
      expect(qNewIn.scoreDetail?.healthStatus, 'insufficient_data');
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

    test('Vehicle catalogue fixture parses all 6 segments', () {
      final file = File('${liveDir.path}/vehicle_catalogue_200.json');
      final list = jsonDecode(file.readAsStringSync()) as List<dynamic>;
      final catalogue = list.map((e) => VehicleSegment.fromJson(e as Map<String, dynamic>)).toList();
      expect(catalogue, hasLength(6));
      expect(catalogue.any((s) => s.id == 'sedan_mid' && s.label == 'Mid-size sedan' && s.exShowroomPriceInr == 1200000), isTrue);
      expect(catalogue.any((s) => s.id == 'hatchback_compact' && s.label == 'Compact hatchback' && s.exShowroomPriceInr == 800000), isTrue);
      expect(catalogue.any((s) => s.id == 'sedan_entry' && s.label == 'Entry sedan' && s.exShowroomPriceInr == 900000), isTrue);
      expect(catalogue.any((s) => s.id == 'suv_compact' && s.label == 'Compact SUV' && s.exShowroomPriceInr == 1000000), isTrue);
      expect(catalogue.any((s) => s.id == 'suv_premium' && s.label == 'Premium SUV' && s.exShowroomPriceInr == 1500000), isTrue);
      expect(catalogue.any((s) => s.id == 'suv_luxury' && s.label == 'Luxury SUV' && s.exShowroomPriceInr == 1800000), isTrue);
    });

    test('Pricing basis parsing with value assertions across sources', () {
      // 1. a1b2: demo_profile, sedan_mid, age 2, idv 960000.0, ncb 20.0
      final qA1b2 = QuoteResponse.fromJson(loadJson('quote_demo_veh_001.json'));
      expect(qA1b2.pricingBasis, isNotNull);
      expect(qA1b2.pricingBasis?.source, 'demo_profile');
      expect(qA1b2.pricingBasis?.vehicleSegmentId, 'sedan_mid');
      expect(qA1b2.pricingBasis?.vehicleAgeYears, 2);
      expect(qA1b2.pricingBasis?.idv, 960000.0);
      expect(qA1b2.pricingBasis?.ncbPct, 20.0);

      // 2. New registration (sedan_mid, age 2): registered, sedan_mid, age 2, base_premium 24960.0
      final qSedanOff = QuoteResponse.fromJson(loadJson('quote_sedan_mid_opted_out.json'));
      expect(qSedanOff.basePremium, 24960.0);
      expect(qSedanOff.pricingBasis, isNotNull);
      expect(qSedanOff.pricingBasis?.source, 'registered');
      expect(qSedanOff.pricingBasis?.vehicleSegmentId, 'sedan_mid');
      expect(qSedanOff.pricingBasis?.vehicleAgeYears, 2);
      expect(qSedanOff.pricingBasis?.idv, 960000.0);
      expect(qSedanOff.pricingBasis?.ncbPct, 0.0);

      final qSedanIn = QuoteResponse.fromJson(loadJson('quote_sedan_mid_opted_in.json'));
      expect(qSedanIn.basePremium, 24960.0);
      expect(qSedanIn.pricingBasis?.source, 'registered');
      expect(qSedanIn.pricingBasis?.vehicleSegmentId, 'sedan_mid');
      expect(qSedanIn.pricingBasis?.vehicleAgeYears, 2);

      // 3. No-segment registration: generic_default, base_premium 13650.0
      final qNoSeg = QuoteResponse.fromJson(loadJson('quote_no_segment_generic_default.json'));
      expect(qNoSeg.basePremium, 13650.0);
      expect(qNoSeg.pricingBasis, isNotNull);
      expect(qNoSeg.pricingBasis?.source, 'generic_default');
      expect(qNoSeg.pricingBasis?.vehicleSegmentId, isNull);
      expect(qNoSeg.pricingBasis?.vehicleAgeYears, 3);
      expect(qNoSeg.pricingBasis?.idv, 525000.0);

      // 4. Registration response fixture parses vehicleSegmentId
      final regSedan = VehicleRegistration.fromJson(loadJson('registration_sedan_mid_201.json'));
      expect(regSedan.vehicleSegmentId, 'sedan_mid');
      expect(regSedan.vehicleId, isNotNull);

      final regNoSeg = VehicleRegistration.fromJson(loadJson('registration_no_segment_201.json'));
      expect(regNoSeg.vehicleSegmentId, isNull);

      // 5. Quote without pricing_basis must still parse cleanly
      final rawQuote = loadJson('quote_no_segment_generic_default.json');
      rawQuote.remove('pricing_basis');
      final qNoBasis = QuoteResponse.fromJson(rawQuote);
      expect(qNoBasis.pricingBasis, isNull);
      expect(qNoBasis.finalPremium, 13650.0);
      expect(qNoBasis.basePremium, 13650.0);
    });

    test('New service record fixtures parse with exact value assertions', () {
      // 1. service_record_accepted_201.json
      final accJson = loadJson('service_record_accepted_201.json');
      final acc = ServiceRecordResponse.fromJson(accJson);
      expect(acc.stored, isTrue);
      expect(acc.source, 'self_upload');
      expect(acc.serviceType, 'oil_change');
      expect(acc.odometerKm, 15000);
      expect(acc.verification, isNotNull);
      expect(acc.verification?.status, 'accepted');
      expect(acc.verification?.checks.length, 6);
      for (final check in acc.verification!.checks) {
        expect(check.passed, isTrue, reason: 'Check ${check.code} should pass');
        expect(check.message, isNotEmpty);
      }
      final accCodes = acc.verification!.checks.map((c) => c.code).toList();
      expect(accCodes, containsAll([
        'date_not_future',
        'date_after_registration',
        'odometer_monotonic',
        'odometer_rate_plausible',
        'not_duplicate',
        'daily_limit',
      ]));

      // 2. service_record_flagged_201.json
      final flagJson = loadJson('service_record_flagged_201.json');
      final flag = ServiceRecordResponse.fromJson(flagJson);
      expect(flag.stored, isTrue);
      expect(flag.serviceType, 'tyre_rotation');
      expect(flag.odometerKm, 12000);
      expect(flag.verification?.status, 'flagged');
      final flagOdoCheck = flag.verification!.checks.firstWhere((c) => c.code == 'odometer_monotonic');
      expect(flagOdoCheck.passed, isFalse);
      expect(flagOdoCheck.message, contains('violates monotonicity'));

      // 3. service_record_rejected_200.json
      final rejJson = loadJson('service_record_rejected_200.json');
      final rej = ServiceRecordResponse.fromJson(rejJson);
      expect(rej.stored, isFalse);
      expect(rej.verification?.status, 'rejected');
      final rejDateCheck = rej.verification!.checks.firstWhere((c) => c.code == 'date_after_registration');
      expect(rejDateCheck.passed, isFalse);
      expect(rejDateCheck.message, contains('before registration date'));

      // 4. Error fixtures: 400, 403, 404
      final err400 = ApiException.fromJson(400, loadJson('service_record_400.json'));
      expect(err400.statusCode, 400);
      expect(err400.error, 'INVALID_REQUEST');

      final err403 = ApiException.fromJson(403, loadJson('service_record_403.json'));
      expect(err403.statusCode, 403);
      expect(err403.error, 'CONSENT_REQUIRED');

      final err404 = ApiException.fromJson(404, loadJson('service_record_404.json'));
      expect(err404.statusCode, 404);
      expect(err404.error, 'VEHICLE_NOT_REGISTERED');

      // 5. timeline_sr_default_200.json
      final tlDef = MaintenanceTimeline.fromJson(loadJson('timeline_sr_default_200.json'));
      expect(tlDef.flaggedCount, 1);
      expect(tlDef.totalServices, 1);
      expect(tlDef.events.length, 1);
      expect(tlDef.events.first.verificationStatus, 'accepted');
      expect(tlDef.events.first.source, 'self_upload');

      // 6. timeline_sr_include_flagged_200.json
      final tlInc = MaintenanceTimeline.fromJson(loadJson('timeline_sr_include_flagged_200.json'));
      expect(tlInc.flaggedCount, 1);
      expect(tlInc.totalServices, 2);
      expect(tlInc.events.length, 2);
      expect(tlInc.events[0].verificationStatus, 'accepted');
      expect(tlInc.events[1].verificationStatus, 'flagged');

      // 7. quote_service_record_200.json (post-record quote)
      final qSr = QuoteResponse.fromJson(loadJson('quote_service_record_200.json'));
      expect(qSr.scoreDetail?.maintenanceScore, 85.0);
      expect(qSr.scoreDetail?.healthConfidence, 'unverified_self_reported');
      expect(qSr.scoreDetail?.healthStatus, 'scored');
      expect(qSr.basePremium, 24960.0);
      expect(qSr.finalPremium, 24960.0);
      expect(qSr.dynamicMultiplier, 1.0);
    });
  });
}
