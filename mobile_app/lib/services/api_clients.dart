import 'package:dio/dio.dart';
import '../config/api_config.dart';
import '../models/models.dart';
import 'api_exception.dart';

Dio _createDio(String baseUrl) {
  final dio = Dio(BaseOptions(
    baseUrl: baseUrl,
    connectTimeout: const Duration(seconds: 8),
    receiveTimeout: const Duration(seconds: 8),
    sendTimeout: const Duration(seconds: 8),
  ));
  dio.interceptors.add(InterceptorsWrapper(
    onError: (DioException e, handler) {
      String msg = e.message ?? 'Unknown error';
      if (e.response != null && e.response!.data != null) {
        if (e.response!.data is Map<String, dynamic> && e.response!.data['message'] != null) {
          msg = e.response!.data['message'].toString();
        } else {
          msg = e.response!.data.toString();
        }
      }
      return handler.next(DioException(
        requestOptions: e.requestOptions,
        error: ApiException(e.response?.statusCode, msg),
      ));
    },
  ));
  return dio;
}

class IdentityConsentApi {
  final Dio _dio = _createDio(ApiConfig.identityUrl);

  Future<VehicleRegistration> registerVehicle(Map<String, dynamic> requestData) async {
    if (ApiConfig.isDemoMode) {
      return VehicleRegistration(vehicleId: 'demo-vehicle-123', policyId: 'demo-policy-123');
    }
    try {
      final response = await _dio.post('/vehicles', data: requestData);
      return VehicleRegistration.fromJson(response.data);
    } on DioException catch (e) {
      throw e.error ?? ApiException(null, 'Network error');
    }
  }

  Future<ConsentRecord> getConsent(String vehicleId) async {
    if (ApiConfig.isDemoMode) {
      return ConsentRecord(usageTrackingOptIn: true, maintenanceTrackingOptIn: true);
    }
    try {
      final response = await _dio.get('/vehicles/$vehicleId/consent');
      return ConsentRecord.fromJson(response.data);
    } on DioException catch (e) {
      throw e.error ?? ApiException(null, 'Network error');
    }
  }

  Future<ConsentRecord> updateConsent(String vehicleId, bool usageOptIn, bool maintenanceOptIn) async {
    if (ApiConfig.isDemoMode) {
      return ConsentRecord(usageTrackingOptIn: usageOptIn, maintenanceTrackingOptIn: maintenanceOptIn);
    }
    try {
      final response = await _dio.put(
        '/vehicles/$vehicleId/consent',
        data: {
          'usage_tracking_opt_in': usageOptIn,
          'maintenance_tracking_opt_in': maintenanceOptIn,
        },
      );
      return ConsentRecord.fromJson(response.data);
    } on DioException catch (e) {
      throw e.error ?? ApiException(null, 'Network error');
    }
  }
}

class PricingPolicyApi {
  final Dio _dio = _createDio(ApiConfig.pricingUrl);

  Future<QuoteResponse> quote(String vehicleId) async {
    if (ApiConfig.isDemoMode) {
      return QuoteResponse(
        finalPremium: 85.00,
        dynamicMultiplier: 0.85,
        discountBreakdown: [
          DiscountBreakdown(reason: 'Safe Driving', adjustmentPct: -10.0),
          DiscountBreakdown(reason: 'Good Maintenance', adjustmentPct: -5.0),
        ],
        scoreDetail: ScoreDetail(
          usageScore: 92.0,
          maintenanceScore: 85.0,
          compositeScore: 90.0,
          vehicleHealthScore: 88.0,
          healthStatus: 'scored',
          healthConfidence: 'verified',
          hasOpenRecall: false,
          dataFlags: [],
          renewalRecommendation: 'RENEW',
          contributingFactors: [
            ContributingFactor(
              factorName: 'safe_driving_bonus',
              impact: 0.8,
              direction: 'positive',
              description: 'Safe driving history',
            )
          ],
        ),
        methodologyNote: 'Demo methodology',
      );
    }
    try {
      final response = await _dio.post('/quote/$vehicleId');
      return QuoteResponse.fromJson(response.data);
    } on DioException catch (e) {
      throw e.error ?? ApiException(null, 'Network error');
    }
  }
}

class NotificationAdvisoryApi {
  final Dio _dio = _createDio(ApiConfig.advisoryUrl);

  Future<AdvisoryResponse> getAdvisories(String vehicleId) async {
    if (ApiConfig.isDemoMode) {
      return AdvisoryResponse(advisories: [
        AdvisoryMessage(
          advisoryType: 'SERVICE_DUE',
          priority: 'MEDIUM',
          title: 'Scheduled Maintenance Due',
          message: 'Your vehicle is due for a scheduled maintenance.',
        ),
      ]);
    }
    try {
      final response = await _dio.get('/advisories/$vehicleId');
      return AdvisoryResponse.fromJson(response.data);
    } on DioException catch (e) {
      throw e.error ?? ApiException(null, 'Network error');
    }
  }
}

class ClaimsVerificationApi {
  final Dio _dio = _createDio(ApiConfig.claimsUrl);

  Future<FnolResponse> submitFnol(FnolRequest request) async {
    if (ApiConfig.isDemoMode) {
      return FnolResponse(claimId: 'demo-claim-789');
    }
    try {
      final response = await _dio.post('/fnol', data: request.toJson());
      return FnolResponse.fromJson(response.data);
    } on DioException catch (e) {
      throw e.error ?? ApiException(null, 'Network error');
    }
  }

  Future<Map<String, dynamic>> getClaim(String claimId) async {
    if (ApiConfig.isDemoMode) {
      return {'claim_id': claimId, 'status': 'PROCESSING', 'details': 'Demo claim details'};
    }
    try {
      final response = await _dio.get('/claims/$claimId');
      return response.data;
    } on DioException catch (e) {
      throw e.error ?? ApiException(null, 'Network error');
    }
  }
}

class MaintenanceApi {
  final Dio _dio = _createDio(ApiConfig.maintenanceUrl);

  Future<void> postDtcReading(DtcReading reading) async {
    if (ApiConfig.isDemoMode) {
      return;
    }
    try {
      await _dio.post('/dtc-readings', data: reading.toJson());
    } on DioException catch (e) {
      throw e.error ?? ApiException(null, 'Network error');
    }
  }

  Future<DtcReading?> getLatestReading(String vehicleId) async {
    if (ApiConfig.isDemoMode) {
      return DtcReading(
        vehicleId: vehicleId,
        timestamp: DateTime.now().toUtc().toIso8601String(),
        source: 'obd_device',
        deviceId: 'demo-dongle',
        confirmedCodes: ['P0101'],
        pendingCodes: [],
      );
    }
    try {
      final response = await _dio.get('/dtc-readings/$vehicleId/latest');
      if (response.statusCode == 204) {
        return null;
      }
      return DtcReading.fromJson(response.data);
    } on DioException catch (e) {
      throw e.error ?? ApiException(null, 'Network error');
    }
  }

  Future<List<DtcReading>> getHistory(String vehicleId) async {
    if (ApiConfig.isDemoMode) {
      return [
        DtcReading(
          vehicleId: vehicleId,
          timestamp: DateTime.now().subtract(const Duration(days: 1)).toUtc().toIso8601String(),
          source: 'obd_device',
          deviceId: 'demo-dongle',
          confirmedCodes: [],
          pendingCodes: [],
        ),
      ];
    }
    try {
      final response = await _dio.get('/dtc-readings/$vehicleId/history');
      return (response.data as List).map((e) => DtcReading.fromJson(e)).toList();
    } on DioException catch (e) {
      throw e.error ?? ApiException(null, 'Network error');
    }
  }
}
