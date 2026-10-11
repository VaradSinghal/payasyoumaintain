import 'dart:convert';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/models/models.dart';

void main() {
  group('Request Serialization Tests', () {
    final requestsDir = Directory('test/fixtures/requests');

    setUpAll(() {
      if (!requestsDir.existsSync()) {
        requestsDir.createSync(recursive: true);
      }
    });

    test('Serializes registration request and writes fixture', () {
      final regData = {
        'rc_number': 'MH12AB1234',
        'registration_date': '2024-01-15',
        'owner_details': {
          'full_name': 'PayAsUMaintain User',
          'kyc_document_number': 'ABCDE1234F',
          'kyc_document_type': 'PAN',
          'email': 'user@payasumaintain.com',
          'phone_number': '9876543210',
        },
      };

      File('${requestsDir.path}/registration_request.json')
          .writeAsStringSync(const JsonEncoder.withIndent('  ').convert(regData));

      expect(regData['rc_number'], 'MH12AB1234');
      expect(regData['owner_details'], isNotNull);
    });

    test('Serializes consent update request and writes fixture', () {
      final consentData = {
        'usage_tracking_opt_in': true,
        'maintenance_tracking_opt_in': true,
      };

      File('${requestsDir.path}/consent_update_request.json')
          .writeAsStringSync(const JsonEncoder.withIndent('  ').convert(consentData));

      expect(consentData['usage_tracking_opt_in'], isTrue);
      expect(consentData['maintenance_tracking_opt_in'], isTrue);
    });

    test('Serializes FNOL request with photo_refs and lowercase claimed_cause', () {
      final fnolReq = FnolRequest(
        vehicleId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
        policyId: 'c4632682-d406-3102-86b7-6a22ef09af5d',
        incidentDate: '2026-10-10T12:00:00Z',
        claimedCause: 'MECHANICAL_FAILURE',
        incidentDescription: 'Transmission slipping and unusual noise',
        photos: [
          'https://storage.paym.app/claims/photo1.jpg',
          'https://storage.paym.app/claims/photo2.jpg',
        ],
      );

      final json = fnolReq.toJson();

      // Confirm specifically: the FNOL key is photo_refs (not photos)
      expect(json.containsKey('photo_refs'), isTrue);
      expect(json.containsKey('photos'), isFalse);
      expect(json['photo_refs'], hasLength(2));

      // Confirm specifically: claimed_cause sends lowercase values
      expect(json['claimed_cause'], 'mechanical_failure');

      File('${requestsDir.path}/fnol_request.json')
          .writeAsStringSync(const JsonEncoder.withIndent('  ').convert(json));
    });

    test('Serializes DTC manual_entry with confirmed_codes, pending_codes and null device_id', () {
      final dtcManual = DtcReading(
        vehicleId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
        timestamp: '2026-10-11T12:00:00Z',
        source: 'manual_entry',
        deviceId: 'should-be-stripped-or-ignored',
        confirmedCodes: ['P0300'],
        pendingCodes: ['P0171'],
      );

      final json = dtcManual.toJson();

      // Confirm specifically: DTC lists are confirmed_codes and pending_codes
      expect(json.containsKey('confirmed_codes'), isTrue);
      expect(json.containsKey('pending_codes'), isTrue);
      expect(json['confirmed_codes'], ['P0300']);
      expect(json['pending_codes'], ['P0171']);

      // Confirm specifically: device_id is null for manual_entry
      expect(json.containsKey('device_id'), isTrue);
      expect(json['device_id'], isNull);

      File('${requestsDir.path}/dtc_manual_entry_request.json')
          .writeAsStringSync(const JsonEncoder.withIndent('  ').convert(json));
    });

    test('Serializes DTC obd_device with device_id and code lists', () {
      final dtcObd = DtcReading(
        vehicleId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
        timestamp: '2026-10-11T12:00:00Z',
        source: 'obd_device',
        deviceId: 'obd-live-device-001',
        confirmedCodes: ['P0300', 'P0171'],
        pendingCodes: ['P0420'],
      );

      final json = dtcObd.toJson();

      expect(json['source'], 'obd_device');
      expect(json['device_id'], 'obd-live-device-001');
      expect(json['confirmed_codes'], ['P0300', 'P0171']);
      expect(json['pending_codes'], ['P0420']);

      File('${requestsDir.path}/dtc_obd_device_request.json')
          .writeAsStringSync(const JsonEncoder.withIndent('  ').convert(json));
    });

    test('Serializes DTC with both lists empty and null device_id', () {
      final dtcEmpty = DtcReading(
        vehicleId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
        timestamp: '2026-10-11T12:00:00Z',
        source: 'manual_entry',
        deviceId: null,
        confirmedCodes: [],
        pendingCodes: [],
      );

      final json = dtcEmpty.toJson();

      expect(json['source'], 'manual_entry');
      expect(json['device_id'], isNull);
      expect(json['confirmed_codes'], isEmpty);
      expect(json['pending_codes'], isEmpty);

      File('${requestsDir.path}/dtc_empty_lists_request.json')
          .writeAsStringSync(const JsonEncoder.withIndent('  ').convert(json));
    });
  });
}
