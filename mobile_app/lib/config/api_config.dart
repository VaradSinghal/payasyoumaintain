import 'dart:io';

class ApiConfig {
  static const String _envHost = String.fromEnvironment('API_HOST');

  static String get _defaultHost {
    if (_envHost.isNotEmpty) return _envHost;
    if (Platform.isAndroid) return '10.0.2.2';
    return 'localhost';
  }

  static String get identityUrl => 'http://$_defaultHost:8081/api/v1';
  static String get maintenanceUrl => 'http://$_defaultHost:8083/api/v1';
  static String get scoringUrl => 'http://$_defaultHost:8084'; // Often /api/v1 is not in FastAPI, check if needed, but requirements didn't list it. Wait, the req said: identity-consent 8081, maintenance 8083, pricing-policy 8085, claims-verification 8086, notification-advisory 8087.
  static String get pricingUrl => 'http://$_defaultHost:8085/api/v1';
  static String get claimsUrl => 'http://$_defaultHost:8086/api/v1';
  static String get advisoryUrl => 'http://$_defaultHost:8087/api/v1';

  static const bool isDemoMode = bool.fromEnvironment('DEMO_MODE', defaultValue: false);
}
