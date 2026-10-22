export 'demo_scenario.dart';

class VehicleSegment {
  final String id;
  final String label;
  final int exShowroomPriceInr;

  VehicleSegment({
    required this.id,
    required this.label,
    required this.exShowroomPriceInr,
  });

  factory VehicleSegment.fromJson(Map<String, dynamic> json) {
    return VehicleSegment(
      id: json['id'] as String? ?? '',
      label: json['label'] as String? ?? '',
      exShowroomPriceInr: (json['ex_showroom_price_inr'] as num?)?.toInt() ?? 0,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'label': label,
    'ex_showroom_price_inr': exShowroomPriceInr,
  };
}

class PricingBasis {
  final String? source;
  final String? vehicleSegmentId;
  final int? exShowroomPriceInr;
  final int? vehicleAgeYears;
  final double? depreciationFactor;
  final double? idv;
  final double? ncbPct;

  PricingBasis({
    this.source,
    this.vehicleSegmentId,
    this.exShowroomPriceInr,
    this.vehicleAgeYears,
    this.depreciationFactor,
    this.idv,
    this.ncbPct,
  });

  factory PricingBasis.fromJson(Map<String, dynamic>? json) {
    if (json == null) return PricingBasis();
    return PricingBasis(
      source: json['source'] as String?,
      vehicleSegmentId: json['vehicle_segment_id'] as String?,
      exShowroomPriceInr: (json['ex_showroom_price_inr'] as num?)?.toInt(),
      vehicleAgeYears: (json['vehicle_age_years'] as num?)?.toInt(),
      depreciationFactor: (json['depreciation_factor'] as num?)?.toDouble(),
      idv: (json['idv'] as num?)?.toDouble(),
      ncbPct: (json['ncb_pct'] as num?)?.toDouble(),
    );
  }

  Map<String, dynamic> toJson() => {
    if (source != null) 'source': source,
    if (vehicleSegmentId != null) 'vehicle_segment_id': vehicleSegmentId,
    if (exShowroomPriceInr != null) 'ex_showroom_price_inr': exShowroomPriceInr,
    if (vehicleAgeYears != null) 'vehicle_age_years': vehicleAgeYears,
    if (depreciationFactor != null) 'depreciation_factor': depreciationFactor,
    if (idv != null) 'idv': idv,
    if (ncbPct != null) 'ncb_pct': ncbPct,
  };
}

class OwnerDetails {
  final String fullName;
  final String kycDocumentNumber;
  final String? kycDocumentType;
  final String email;
  final String phoneNumber;

  OwnerDetails({
    required this.fullName,
    required this.kycDocumentNumber,
    this.kycDocumentType = 'PAN',
    required this.email,
    required this.phoneNumber,
  });

  factory OwnerDetails.fromJson(Map<String, dynamic> json) {
    return OwnerDetails(
      fullName: json['full_name'] as String? ?? '',
      kycDocumentNumber: json['kyc_document_number'] as String? ?? '',
      kycDocumentType: json['kyc_document_type'] as String?,
      email: json['email'] as String? ?? '',
      phoneNumber: json['phone_number'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'full_name': fullName,
      'kyc_document_number': kycDocumentNumber,
      if (kycDocumentType != null) 'kyc_document_type': kycDocumentType,
      'email': email,
      'phone_number': phoneNumber,
    };
  }
}

class RegistrationRequest {
  final String rcNumber;
  final String registrationDate;
  final String? vehicleSegmentId;
  final OwnerDetails ownerDetails;

  RegistrationRequest({
    required this.rcNumber,
    required this.registrationDate,
    this.vehicleSegmentId,
    required this.ownerDetails,
  });

  factory RegistrationRequest.fromJson(Map<String, dynamic> json) {
    return RegistrationRequest(
      rcNumber: json['rc_number'] as String? ?? '',
      registrationDate: json['registration_date'] as String? ?? '',
      vehicleSegmentId: json['vehicle_segment_id'] as String?,
      ownerDetails: OwnerDetails.fromJson(json['owner_details'] as Map<String, dynamic>? ?? {}),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'rc_number': rcNumber,
      'registration_date': registrationDate,
      if (vehicleSegmentId != null) 'vehicle_segment_id': vehicleSegmentId,
      'owner_details': ownerDetails.toJson(),
    };
  }
}

class VehicleRegistration {
  final String? vehicleId;
  final String? policyId;
  final String? rcNumber;
  final String? registrationDate;
  final String? kycStatus;
  final String? registeredAt;
  final String? vehicleSegmentId;

  VehicleRegistration({
    this.vehicleId,
    this.policyId,
    this.rcNumber,
    this.registrationDate,
    this.kycStatus,
    this.registeredAt,
    this.vehicleSegmentId,
  });

  factory VehicleRegistration.fromJson(Map<String, dynamic> json) {
    return VehicleRegistration(
      vehicleId: json['vehicle_id'] as String?,
      policyId: json['policy_id'] as String?,
      rcNumber: json['rc_number'] as String?,
      registrationDate: json['registration_date'] as String?,
      kycStatus: json['kyc_status'] as String?,
      registeredAt: json['registered_at'] as String?,
      vehicleSegmentId: json['vehicle_segment_id'] as String?,
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
  final PricingBasis? pricingBasis;

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
    this.pricingBasis,
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
      pricingBasis: json['pricing_basis'] != null
          ? PricingBasis.fromJson(json['pricing_basis'] as Map<String, dynamic>)
          : null,
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

class TimelineEvent {
  final String? eventId;
  final String? vehicleId;
  final String? serviceDate;
  final String? serviceType;
  final double? odometerKm;
  final String? source;
  final String? notes;
  final List<dynamic> partsReplaced;
  final List<String> documentRefs;
  final String? verificationStatus;
  final String? recordedAt;

  TimelineEvent({
    this.eventId,
    this.vehicleId,
    this.serviceDate,
    this.serviceType,
    this.odometerKm,
    this.source,
    this.notes,
    this.partsReplaced = const [],
    this.documentRefs = const [],
    this.verificationStatus,
    this.recordedAt,
  });

  factory TimelineEvent.fromJson(Map<String, dynamic> json) {
    return TimelineEvent(
      eventId: json['event_id'] as String?,
      vehicleId: json['vehicle_id'] as String?,
      serviceDate: json['service_date'] as String?,
      serviceType: json['service_type'] as String?,
      odometerKm: (json['odometer_km'] as num?)?.toDouble(),
      source: json['source'] as String?,
      notes: json['notes'] as String?,
      partsReplaced: (json['parts_replaced'] as List<dynamic>?) ?? const [],
      documentRefs: (json['document_refs'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      verificationStatus: json['verification_status'] as String?,
      recordedAt: json['recorded_at'] as String?,
    );
  }
}

class MaintenanceTimeline {
  final String? vehicleId;
  final int totalServices;
  final String? latestServiceDate;
  final double? latestOdometerKm;
  final int flaggedCount;
  final List<TimelineEvent> events;

  MaintenanceTimeline({
    this.vehicleId,
    this.totalServices = 0,
    this.latestServiceDate,
    this.latestOdometerKm,
    this.flaggedCount = 0,
    this.events = const [],
  });

  factory MaintenanceTimeline.fromJson(Map<String, dynamic> json) {
    return MaintenanceTimeline(
      vehicleId: json['vehicle_id'] as String?,
      totalServices: (json['total_services'] as num?)?.toInt() ?? 0,
      latestServiceDate: json['latest_service_date'] as String?,
      latestOdometerKm: (json['latest_odometer_km'] as num?)?.toDouble(),
      flaggedCount: (json['flagged_count'] as num?)?.toInt() ?? 0,
      events: (json['events'] as List<dynamic>?)
              ?.map((e) => TimelineEvent.fromJson(e as Map<String, dynamic>))
              .toList() ??
          const [],
    );
  }
}

class VerificationCheck {
  final String code;
  final bool passed;
  final String message;

  VerificationCheck({
    required this.code,
    required this.passed,
    required this.message,
  });

  factory VerificationCheck.fromJson(Map<String, dynamic> json) {
    return VerificationCheck(
      code: json['code'] as String? ?? 'unknown_check',
      passed: json['passed'] as bool? ?? false,
      message: json['message'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'code': code,
      'passed': passed,
      'message': message,
    };
  }
}

class ServiceRecordVerification {
  final String status;
  final List<VerificationCheck> checks;

  ServiceRecordVerification({
    required this.status,
    this.checks = const [],
  });

  factory ServiceRecordVerification.fromJson(Map<String, dynamic> json) {
    return ServiceRecordVerification(
      status: json['status'] as String? ?? 'unknown',
      checks: (json['checks'] as List<dynamic>?)
              ?.map((e) => VerificationCheck.fromJson(e as Map<String, dynamic>))
              .toList() ??
          const [],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'status': status,
      'checks': checks.map((e) => e.toJson()).toList(),
    };
  }
}

class ServiceRecordResponse {
  final String? eventId;
  final String? vehicleId;
  final String? serviceDate;
  final String? serviceType;
  final int? odometerKm;
  final String? source;
  final bool stored;
  final ServiceRecordVerification? verification;
  final String? recordedAt;

  ServiceRecordResponse({
    this.eventId,
    this.vehicleId,
    this.serviceDate,
    this.serviceType,
    this.odometerKm,
    this.source,
    this.stored = false,
    this.verification,
    this.recordedAt,
  });

  factory ServiceRecordResponse.fromJson(Map<String, dynamic> json) {
    return ServiceRecordResponse(
      eventId: json['event_id'] as String?,
      vehicleId: json['vehicle_id'] as String?,
      serviceDate: json['service_date'] as String?,
      serviceType: json['service_type'] as String?,
      odometerKm: (json['odometer_km'] as num?)?.toInt(),
      source: json['source'] as String?,
      stored: json['stored'] as bool? ?? false,
      verification: json['verification'] is Map<String, dynamic>
          ? ServiceRecordVerification.fromJson(json['verification'] as Map<String, dynamic>)
          : null,
      recordedAt: json['recorded_at'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'event_id': eventId,
      'vehicle_id': vehicleId,
      'service_date': serviceDate,
      'service_type': serviceType,
      'odometer_km': odometerKm,
      'source': source,
      'stored': stored,
      if (verification != null) 'verification': verification!.toJson(),
      'recorded_at': recordedAt,
    };
  }
}

class ServiceRecordRequest {
  final String vehicleId;
  final String serviceDate;
  final String serviceType;
  final int odometerKm;
  final String? notes;
  final String? receiptRef;
  final String? receiptSha256;

  ServiceRecordRequest({
    required this.vehicleId,
    required this.serviceDate,
    required this.serviceType,
    required this.odometerKm,
    this.notes,
    this.receiptRef,
    this.receiptSha256,
  });

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{
      'vehicle_id': vehicleId,
      'service_date': serviceDate,
      'service_type': serviceType,
      'odometer_km': odometerKm,
    };
    if (notes != null && notes!.trim().isNotEmpty) {
      map['notes'] = notes!.trim();
    }
    if (receiptRef != null && receiptRef!.trim().isNotEmpty) {
      map['receipt_ref'] = receiptRef!.trim();
    }
    if (receiptSha256 != null && receiptSha256!.trim().isNotEmpty) {
      map['receipt_sha256'] = receiptSha256!.trim();
    }
    return map;
  }
}

class TripAggregate {
  final String tripId;
  final String vehicleId;
  final int eventCount;
  final double avgSpeedKmh;
  final double maxSpeedKmh;
  final int harshBrakingCount;
  final int hardAccelerationCount;
  final double distanceKm;
  final String tripStart;
  final String tripEnd;

  TripAggregate({
    required this.tripId,
    required this.vehicleId,
    required this.eventCount,
    required this.avgSpeedKmh,
    required this.maxSpeedKmh,
    required this.harshBrakingCount,
    required this.hardAccelerationCount,
    required this.distanceKm,
    required this.tripStart,
    required this.tripEnd,
  });

  factory TripAggregate.fromJson(Map<String, dynamic> json) {
    return TripAggregate(
      tripId: json['trip_id'] as String? ?? '',
      vehicleId: json['vehicle_id'] as String? ?? '',
      eventCount: (json['event_count'] as num?)?.toInt() ?? 0,
      avgSpeedKmh: ((json['avg_speed_kmh'] ?? 0.0) as num).toDouble(),
      maxSpeedKmh: ((json['max_speed_kmh'] ?? 0.0) as num).toDouble(),
      harshBrakingCount: (json['harsh_braking_count'] as num?)?.toInt() ?? 0,
      hardAccelerationCount: (json['hard_acceleration_count'] as num?)?.toInt() ?? 0,
      distanceKm: ((json['distance_km'] ?? 0.0) as num).toDouble(),
      tripStart: json['trip_start'] as String? ?? '',
      tripEnd: json['trip_end'] as String? ?? '',
    );
  }

  static List<TripAggregate> fromJsonList(List<dynamic> list) {
    return list.map((e) => TripAggregate.fromJson(e as Map<String, dynamic>)).toList();
  }
}

