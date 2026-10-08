import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/config/api_config.dart';

void main() {
  group('ApiConfig', () {
    test('never produces a docker container name', () {
      final urls = [
        ApiConfig.identityUrl,
        ApiConfig.maintenanceUrl,
        ApiConfig.pricingUrl,
        ApiConfig.claimsUrl,
        ApiConfig.advisoryUrl,
      ];

      final dockerNames = [
        'identity-consent',
        'maintenance-vehicle-health-ingestion',
        'risk-scoring-engine',
        'pricing-policy',
        'claims-verification',
        'notification-advisory',
      ];

      for (final url in urls) {
        for (final name in dockerNames) {
          expect(url.contains(name), isFalse, reason: 'URL $url should not contain docker name $name');
        }
      }
    });
  });
}
