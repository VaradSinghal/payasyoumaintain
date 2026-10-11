import 'dart:io' show Platform;
import 'package:flutter/foundation.dart' show kIsWeb;

class ApiConfig {
  static const String _envBaseUrl = String.fromEnvironment('API_BASE_URL');

  static String get baseUrl {
    if (_envBaseUrl.isNotEmpty) {
      return _envBaseUrl.endsWith('/')
          ? _envBaseUrl.substring(0, _envBaseUrl.length - 1)
          : _envBaseUrl;
    }
    // Web safe check: avoid calling dart:io Platform on web
    if (!kIsWeb && Platform.isAndroid) {
      return 'http://10.0.2.2:8080';
    }
    return 'http://localhost:8080';
  }

  static String get identityUrl => '$baseUrl/identity/api/v1';
  static String get pricingUrl => '$baseUrl/pricing/api/v1';
  static String get claimsUrl => '$baseUrl/claims/api/v1';
  static String get advisoryUrl => '$baseUrl/advisory/api/v1';
  static String get maintenanceUrl => '$baseUrl/maintenance/api/v1';
  static String get telematicsUrl => '$baseUrl/telematics/api/v1';

  static const bool isDemoMode = bool.fromEnvironment('DEMO_MODE', defaultValue: false);
}
