class PremiumResponse {
  final String requestId;
  final String vehicleId;
  final String policyId;
  final double basePremium;
  final double dynamicMultiplier;
  final double finalPremium;
  final String currency;
  final String validUntil;
  final String computedAt;
  final String methodologyNote;
  final ScoreDetail? scoreDetail;

  PremiumResponse({
    required this.requestId,
    required this.vehicleId,
    required this.policyId,
    required this.basePremium,
    required this.dynamicMultiplier,
    required this.finalPremium,
    required this.currency,
    required this.validUntil,
    required this.computedAt,
    required this.methodologyNote,
    this.scoreDetail,
  });

  factory PremiumResponse.mock() {
    return PremiumResponse(
      requestId: 'req-123',
      vehicleId: 'veh-123',
      policyId: 'pol-123',
      basePremium: 13650.0,
      dynamicMultiplier: 1.0,
      finalPremium: 13650.0,
      currency: 'INR',
      validUntil: '2026-12-31',
      computedAt: '2026-10-08',
      methodologyNote: 'Dynamic Risk Multiplier is illustrative, pending actuarial validation on real claims data (Phase A).',
      scoreDetail: ScoreDetail.mock(),
    );
  }
}

class ScoreDetail {
  final double usageScore;
  final double? maintenanceScore;
  final double? compositeScore;
  final String healthStatus;
  final double? vehicleHealthScore;
  final String healthConfidence;
  final String renewalRecommendation;
  final bool? hasOpenRecall;
  final List<String> dataFlags;

  ScoreDetail({
    required this.usageScore,
    this.maintenanceScore,
    this.compositeScore,
    required this.healthStatus,
    this.vehicleHealthScore,
    required this.healthConfidence,
    required this.renewalRecommendation,
    this.hasOpenRecall,
    required this.dataFlags,
  });

  factory ScoreDetail.mock() {
    return ScoreDetail(
      usageScore: 80.0,
      maintenanceScore: null,
      compositeScore: null,
      healthStatus: 'insufficient_data',
      vehicleHealthScore: null,
      healthConfidence: 'unverified_self_reported',
      renewalRecommendation: 'Service immediately to maintain active policy.',
      hasOpenRecall: true,
      dataFlags: ['conflict_detected'],
    );
  }
}
