import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/models/models.dart';

void main() {
  group('Model parsing from JSON fixtures', () {
    test('Full quote', () {
      final json = {
        "final_premium": 85.00,
        "dynamic_multiplier": 0.85,
        "discount_breakdown": [],
        "score_detail": {
          "usage_score": 92.0,
          "maintenance_score": 85.0,
          "composite_score": 90.0,
          "vehicle_health_score": 88.0,
          "health_status": "scored",
          "health_confidence": "verified",
          "has_open_recall": false,
          "data_flags": [],
          "renewal_recommendation": "RENEW",
          "contributing_factors": []
        }
      };

      final quote = QuoteResponse.fromJson(json);
      expect(quote.finalPremium, 85.00);
      expect(quote.scoreDetail?.usageScore, 92.0);
      expect(quote.scoreDetail?.hasOpenRecall, false);
      expect(quote.scoreDetail?.healthConfidence, 'verified');
    });

    test('Quote with null scores and insufficient_data', () {
      final json = {
        "final_premium": 100.00,
        "dynamic_multiplier": 1.0,
        "discount_breakdown": [],
        "score_detail": {
          "usage_score": 80.0,
          "maintenance_score": null,
          "composite_score": null,
          "vehicle_health_score": null,
          "health_status": "insufficient_data",
          "health_confidence": "verified",
          "has_open_recall": null,
          "data_flags": [],
          "renewal_recommendation": "RENEW",
          "contributing_factors": []
        }
      };

      final quote = QuoteResponse.fromJson(json);
      expect(quote.scoreDetail?.maintenanceScore, null);
      expect(quote.scoreDetail?.compositeScore, null);
      expect(quote.scoreDetail?.vehicleHealthScore, null);
      expect(quote.scoreDetail?.healthStatus, 'insufficient_data');
      expect(quote.scoreDetail?.hasOpenRecall, null);
    });

    test('Quote with has_open_recall true, false, and null', () {
      final jsonTrue = {
        "final_premium": 100.0,
        "dynamic_multiplier": 1.0,
        "discount_breakdown": [],
        "score_detail": {
          "data_flags": [],
          "contributing_factors": [],
          "has_open_recall": true
        }
      };
      expect(QuoteResponse.fromJson(jsonTrue).scoreDetail?.hasOpenRecall, true);

      final jsonFalse = {
        "final_premium": 100.0,
        "dynamic_multiplier": 1.0,
        "discount_breakdown": [],
        "score_detail": {
          "data_flags": [],
          "contributing_factors": [],
          "has_open_recall": false
        }
      };
      expect(QuoteResponse.fromJson(jsonFalse).scoreDetail?.hasOpenRecall, false);

      final jsonNull = {
        "final_premium": 100.0,
        "dynamic_multiplier": 1.0,
        "discount_breakdown": [],
        "score_detail": {
          "data_flags": [],
          "contributing_factors": [],
          "has_open_recall": null
        }
      };
      expect(QuoteResponse.fromJson(jsonNull).scoreDetail?.hasOpenRecall, null);
    });

    test('DTC reading with both lists empty', () {
      final json = {
        "vehicle_id": "v1",
        "timestamp": "2023-01-01T00:00:00Z",
        "source": "obd_device",
        "device_id": "d1",
        "confirmed_codes": [],
        "pending_codes": []
      };

      final reading = DtcReading.fromJson(json);
      expect(reading.confirmedCodes.isEmpty, true);
      expect(reading.pendingCodes.isEmpty, true);
    });
  });
}
