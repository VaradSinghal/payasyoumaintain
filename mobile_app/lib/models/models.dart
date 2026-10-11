class VehicleRegistration {
  final String? vehicleId;
  final String? policyId;
  final String? rcNumber;
  final String? registrationDate;
  final String? kycStatus;
  final String? registeredAt;

  VehicleRegistration({
    this.vehicleId,
    this.policyId,
    this.rcNumber,
    this.registrationDate,
    this.kycStatus,
    this.registeredAt,
  });

  factory VehicleRegistration.fromJson(Map<String, dynamic> json) {
    return VehicleRegistration(
      vehicleId: json['vehicle_id'] as String?,
      policyId: json['policy_id'] as String?,
      rcNumber: json['rc_number'] as String?,
      registrationDate: json['registration_date'] as String?,
      kycStatus: json['kyc_status'] as String?,
      registeredAt: json['registered_at'] as String?,
    );
  }
}

class ConsentRecord {
  final String? vehicleId;
  final bool usageTrackingOptIn;
  final bool maintenanceTrackingOptIn;
  final String? lastUpdated;

  ConsentRecord({
    this.vehicleId,
    required this.usageTrackingOptIn,
    required this.maintenanceTrackingOptIn,
    this.lastUpdated,
  });

  factory ConsentRecord.fromJson(Map<String, dynamic> json) {
    return ConsentRecord(
      vehicleId: json['vehicle_id'] as String?,
      usageTrackingOptIn: json['usage_tracking_opt_in'] as bool? ?? false,
      maintenanceTrackingOptIn: json['maintenance_tracking_opt_in'] as bool? ?? false,
      lastUpdated: json['last_updated'] as String?,
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

class ContributingFactor {
  final String factorName;
  final double impact;
  final String direction;
  final String? description;

  ContributingFactor({
    required this.factorName,
    required this.impact,
    required this.direction,
    this.description,
  });

  factory ContributingFactor.fromJson(Map<String, dynamic> json) {
    return ContributingFactor(
      factorName: (json['factor_name'] ?? json['factorName'] ?? '') as String,
      impact: ((json['impact'] ?? 0.0) as num).toDouble(),
      direction: (json['direction'] ?? '') as String,
      description: json['description'] as String?,
    );
  }
}

class ScoreDetail {
  final double? usageScore;
  final double? maintenanceScore;
  final double? compositeScore;
  final double? vehicleHealthScore;
  final String? healthStatus;
  final String? healthConfidence;
  final bool? hasOpenRecall;
  final List<String> dataFlags;
  final String? renewalRecommendation;
  final List<ContributingFactor> contributingFactors;

  ScoreDetail({
    this.usageScore,
    this.maintenanceScore,
    this.compositeScore,
    this.vehicleHealthScore,
    this.healthStatus,
    this.healthConfidence,
    this.hasOpenRecall,
    this.dataFlags = const [],
    this.renewalRecommendation,
    this.contributingFactors = const [],
  });

  factory ScoreDetail.fromJson(Map<String, dynamic> json) {
    return ScoreDetail(
      usageScore: (json['usage_score'] as num?)?.toDouble(),
      maintenanceScore: (json['maintenance_score'] as num?)?.toDouble(),
      compositeScore: (json['composite_score'] as num?)?.toDouble(),
      vehicleHealthScore: (json['vehicle_health_score'] as num?)?.toDouble(),
      healthStatus: json['health_status'] as String?,
      healthConfidence: json['health_confidence'] as String?,
      hasOpenRecall: json['has_open_recall'] as bool?,
      dataFlags: (json['data_flags'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          [],
      renewalRecommendation: json['renewal_recommendation'] as String?,
      contributingFactors: (json['contributing_factors'] as List<dynamic>?)
              ?.map((e) => ContributingFactor.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }
}

class QuoteResponse {
  final double finalPremium;
  final double dynamicMultiplier;
  final List<DiscountBreakdown> discountBreakdown;
  final ScoreDetail? scoreDetail;
  final String? methodologyNote;
  final String? currency;
  final String? requestId;
  final String? vehicleId;
  final String? policyId;
  final double? basePremium;
  final String? validUntil;
  final String? computedAt;

  QuoteResponse({
    required this.finalPremium,
    required this.dynamicMultiplier,
    this.discountBreakdown = const [],
    this.scoreDetail,
    this.methodologyNote,
    this.currency,
    this.requestId,
    this.vehicleId,
    this.policyId,
    this.basePremium,
    this.validUntil,
    this.computedAt,
  });

  factory QuoteResponse.fromJson(Map<String, dynamic> json) {
    return QuoteResponse(
      finalPremium: ((json['final_premium'] ?? 0.0) as num).toDouble(),
      dynamicMultiplier: ((json['dynamic_multiplier'] ?? 1.0) as num).toDouble(),
      discountBreakdown: (json['discount_breakdown'] as List<dynamic>?)
              ?.map((e) => DiscountBreakdown.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      scoreDetail: json['score_detail'] != null
          ? ScoreDetail.fromJson(json['score_detail'] as Map<String, dynamic>)
          : null,
      methodologyNote: json['methodology_note'] as String?,
      currency: json['currency'] as String?,
      requestId: json['request_id'] as String?,
      vehicleId: json['vehicle_id'] as String?,
      policyId: json['policy_id'] as String?,
      basePremium: (json['base_premium'] as num?)?.toDouble(),
      validUntil: json['valid_until'] as String?,
      computedAt: json['computed_at'] as String?,
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
      advisoryType: json['advisory_type'] as String? ?? '',
      priority: json['priority'] as String? ?? '',
      title: json['title'] as String? ?? '',
      message: json['message'] as String? ?? '',
      actionUrl: json['action_url'] as String?,
    );
  }
}

class AdvisoryResponse {
  final List<AdvisoryMessage> advisories;
  final String? vehicleId;
  final String? generatedAt;

  AdvisoryResponse({
    required this.advisories,
    this.vehicleId,
    this.generatedAt,
  });

  factory AdvisoryResponse.fromJson(Map<String, dynamic> json) {
    return AdvisoryResponse(
      advisories: (json['advisories'] as List<dynamic>?)
              ?.map((e) => AdvisoryMessage.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      vehicleId: json['vehicle_id'] as String?,
      generatedAt: json['generated_at'] as String?,
    );
  }
}

class FnolRequest {
  final String vehicleId;
  final String policyId;
  final String incidentDate;
  final String claimedCause;
  final String incidentDescription;
  final List<String> photoRefs;

  FnolRequest({
    required this.vehicleId,
    required this.policyId,
    required this.incidentDate,
    required this.claimedCause,
    required this.incidentDescription,
    List<String>? photos,
    List<String>? photoRefs,
  }) : photoRefs = photoRefs ?? photos ?? const [];

  List<String> get photos => photoRefs;

  Map<String, dynamic> toJson() {
    return {
      'vehicle_id': vehicleId,
      'policy_id': policyId,
      'incident_date': incidentDate,
      'claimed_cause': claimedCause.toLowerCase(),
      'incident_description': incidentDescription,
      'photo_refs': photoRefs,
    };
  }
}

class FnolResponse {
  final String? claimId;
  final String? vehicleId;
  final String? policyId;
  final String? claimedCause;
  final String? triageDecision;
  final List<String> triageReasons;
  final String? maintenanceSignal;
  final String? submittedAt;

  FnolResponse({
    this.claimId,
    this.vehicleId,
    this.policyId,
    this.claimedCause,
    this.triageDecision,
    this.triageReasons = const [],
    this.maintenanceSignal,
    this.submittedAt,
  });

  factory FnolResponse.fromJson(Map<String, dynamic> json) {
    return FnolResponse(
      claimId: json['claim_id'] as String?,
      vehicleId: json['vehicle_id'] as String?,
      policyId: json['policy_id'] as String?,
      claimedCause: json['claimed_cause'] as String?,
      triageDecision: json['triage_decision'] as String?,
      triageReasons: (json['triage_reasons'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          [],
      maintenanceSignal: json['maintenance_signal'] as String?,
      submittedAt: json['submitted_at'] as String?,
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
      'device_id': source == 'manual_entry' ? null : deviceId,
      'confirmed_codes': confirmedCodes,
      'pending_codes': pendingCodes,
    };
  }
}

