class VehicleRegistration {
  final String? vehicleId;
  final String? policyId;

  VehicleRegistration({this.vehicleId, this.policyId});

  factory VehicleRegistration.fromJson(Map<String, dynamic> json) {
    return VehicleRegistration(
      vehicleId: json['vehicle_id'] as String?,
      policyId: json['policy_id'] as String?,
    );
  }
}

class ConsentRecord {
  final bool usageTrackingOptIn;
  final bool maintenanceTrackingOptIn;

  ConsentRecord({
    required this.usageTrackingOptIn,
    required this.maintenanceTrackingOptIn,
  });

  factory ConsentRecord.fromJson(Map<String, dynamic> json) {
    return ConsentRecord(
      usageTrackingOptIn: json['usage_tracking_opt_in'] as bool? ?? false,
      maintenanceTrackingOptIn: json['maintenance_tracking_opt_in'] as bool? ?? false,
    );
  }
}

class DiscountBreakdown {
  final String reason;
  final double adjustmentPct;

  DiscountBreakdown({required this.reason, required this.adjustmentPct});

  factory DiscountBreakdown.fromJson(Map<String, dynamic> json) {
    return DiscountBreakdown(
      reason: json['reason'] as String,
      adjustmentPct: (json['adjustment_pct'] as num).toDouble(),
    );
  }
}

class ScoreDetail {
  final double? maintenanceScore;
  final double? compositeScore;
  final double? vehicleHealthScore;
  final double? healthConfidence;
  final bool? hasOpenRecall;
  final List<String> dataFlags;
  final String? renewalRecommendation;

  ScoreDetail({
    this.maintenanceScore,
    this.compositeScore,
    this.vehicleHealthScore,
    this.healthConfidence,
    this.hasOpenRecall,
    required this.dataFlags,
    this.renewalRecommendation,
  });

  factory ScoreDetail.fromJson(Map<String, dynamic> json) {
    return ScoreDetail(
      maintenanceScore: (json['maintenance_score'] as num?)?.toDouble(),
      compositeScore: (json['composite_score'] as num?)?.toDouble(),
      vehicleHealthScore: (json['vehicle_health_score'] as num?)?.toDouble(),
      healthConfidence: (json['health_confidence'] as num?)?.toDouble(),
      hasOpenRecall: json['has_open_recall'] as bool?,
      dataFlags: (json['data_flags'] as List<dynamic>?)?.map((e) => e as String).toList() ?? [],
      renewalRecommendation: json['renewal_recommendation'] as String?,
    );
  }
}

class QuoteResponse {
  final double finalPremium;
  final double dynamicMultiplier;
  final List<DiscountBreakdown> discountBreakdown;
  final ScoreDetail? scoreDetail;
  final String? methodologyNote;

  QuoteResponse({
    required this.finalPremium,
    required this.dynamicMultiplier,
    required this.discountBreakdown,
    this.scoreDetail,
    this.methodologyNote,
  });

  factory QuoteResponse.fromJson(Map<String, dynamic> json) {
    return QuoteResponse(
      finalPremium: (json['final_premium'] as num).toDouble(),
      dynamicMultiplier: (json['dynamic_multiplier'] as num).toDouble(),
      discountBreakdown: (json['discount_breakdown'] as List<dynamic>?)
              ?.map((e) => DiscountBreakdown.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      scoreDetail: json['score_detail'] != null
          ? ScoreDetail.fromJson(json['score_detail'] as Map<String, dynamic>)
          : null,
      methodologyNote: json['methodology_note'] as String?,
    );
  }
}

class AdvisoryMessage {
  final String advisoryType;
  final String priority;
  final String title;
  final String message;
  final String? actionUrl;

  AdvisoryMessage({
    required this.advisoryType,
    required this.priority,
    required this.title,
    required this.message,
    this.actionUrl,
  });

  factory AdvisoryMessage.fromJson(Map<String, dynamic> json) {
    return AdvisoryMessage(
      advisoryType: json['advisory_type'] as String,
      priority: json['priority'] as String,
      title: json['title'] as String,
      message: json['message'] as String,
      actionUrl: json['action_url'] as String?,
    );
  }
}

class AdvisoryResponse {
  final List<AdvisoryMessage> advisories;

  AdvisoryResponse({required this.advisories});

  factory AdvisoryResponse.fromJson(Map<String, dynamic> json) {
    return AdvisoryResponse(
      advisories: (json['advisories'] as List<dynamic>?)
              ?.map((e) => AdvisoryMessage.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }
}

class FnolRequest {
  final String vehicleId;
  final String policyId;
  final String incidentDate;
  final String claimedCause;
  final String incidentDescription;

  FnolRequest({
    required this.vehicleId,
    required this.policyId,
    required this.incidentDate,
    required this.claimedCause,
    required this.incidentDescription,
  });

  Map<String, dynamic> toJson() {
    return {
      'vehicle_id': vehicleId,
      'policy_id': policyId,
      'incident_date': incidentDate,
      'claimed_cause': claimedCause,
      'incident_description': incidentDescription,
    };
  }
}

class FnolResponse {
  final String? claimId;

  FnolResponse({this.claimId});

  factory FnolResponse.fromJson(Map<String, dynamic> json) {
    return FnolResponse(
      claimId: json['claim_id'] as String?,
    );
  }
}

class DtcReading {
  final String vehicleId;
  final String timestamp;
  final String source;
  final String? deviceId;
  final List<String> confirmedCodes;
  final List<String> pendingCodes;

  DtcReading({
    required this.vehicleId,
    required this.timestamp,
    required this.source,
    this.deviceId,
    required this.confirmedCodes,
    required this.pendingCodes,
  });

  factory DtcReading.fromJson(Map<String, dynamic> json) {
    return DtcReading(
      vehicleId: json['vehicle_id'] as String,
      timestamp: json['timestamp'] as String,
      source: json['source'] as String,
      deviceId: json['device_id'] as String?,
      confirmedCodes: (json['confirmed_codes'] as List<dynamic>?)?.map((e) => e as String).toList() ?? [],
      pendingCodes: (json['pending_codes'] as List<dynamic>?)?.map((e) => e as String).toList() ?? [],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'vehicle_id': vehicleId,
      'timestamp': timestamp,
      'source': source,
      'device_id': deviceId,
      'confirmed_codes': confirmedCodes,
      'pending_codes': pendingCodes,
    };
  }
}
