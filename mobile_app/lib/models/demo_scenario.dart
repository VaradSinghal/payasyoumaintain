import 'dart:convert';
import 'package:flutter/services.dart';

class DemoExpected {
  final double? usageScore;
  final double? maintenanceScore;
  final double? compositeScore;
  final double dynamicMultiplier;
  final double basePremium;
  final double finalPremium;
  final String healthStatus;
  final String? healthConfidence;
  final bool hasOpenRecall;
  final List<String> dataFlags;
  final List<String> advisoryTypes;

  const DemoExpected({
    this.usageScore,
    this.maintenanceScore,
    this.compositeScore,
    required this.dynamicMultiplier,
    required this.basePremium,
    required this.finalPremium,
    required this.healthStatus,
    this.healthConfidence,
    required this.hasOpenRecall,
    required this.dataFlags,
    required this.advisoryTypes,
  });

  factory DemoExpected.fromJson(Map<String, dynamic> json) {
    return DemoExpected(
      usageScore: (json['usage_score'] as num?)?.toDouble(),
      maintenanceScore: (json['maintenance_score'] as num?)?.toDouble(),
      compositeScore: (json['composite_score'] as num?)?.toDouble(),
      dynamicMultiplier: (json['dynamic_multiplier'] as num).toDouble(),
      basePremium: (json['base_premium'] as num).toDouble(),
      finalPremium: (json['final_premium'] as num).toDouble(),
      healthStatus: json['health_status'] as String,
      healthConfidence: json['health_confidence'] as String?,
      hasOpenRecall: json['has_open_recall'] as bool,
      dataFlags: (json['data_flags'] as List<dynamic>).map((e) => e.toString()).toList(),
      advisoryTypes: (json['advisory_types'] as List<dynamic>).map((e) => e.toString()).toList(),
    );
  }
}

class DemoSuggestedClaim {
  final String claimedCause;
  final String expectedTriageDecision;

  const DemoSuggestedClaim({
    required this.claimedCause,
    required this.expectedTriageDecision,
  });

  factory DemoSuggestedClaim.fromJson(Map<String, dynamic> json) {
    return DemoSuggestedClaim(
      claimedCause: json['claimed_cause'] as String,
      expectedTriageDecision: json['expected_triage_decision'] as String,
    );
  }
}

class DemoScenario {
  final String vehicleId;
  final String displayName;
  final String descriptor;
  final String headline;
  final String talkingPoint;
  final DemoExpected expected;
  final DemoSuggestedClaim suggestedClaim;

  const DemoScenario({
    required this.vehicleId,
    required this.displayName,
    required this.descriptor,
    required this.headline,
    required this.talkingPoint,
    required this.expected,
    required this.suggestedClaim,
  });

  factory DemoScenario.fromJson(Map<String, dynamic> json) {
    return DemoScenario(
      vehicleId: json['vehicle_id'] as String,
      displayName: json['display_name'] as String,
      descriptor: json['descriptor'] as String,
      headline: json['headline'] as String,
      talkingPoint: json['talking_point'] as String,
      expected: DemoExpected.fromJson(json['expected'] as Map<String, dynamic>),
      suggestedClaim: DemoSuggestedClaim.fromJson(json['suggested_claim'] as Map<String, dynamic>),
    );
  }

  static const List<DemoScenario> fallbackScenarios = [
    DemoScenario(
      vehicleId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
      displayName: 'The Model Citizen',
      descriptor: 'Mid-segment sedan · 2 years',
      headline: 'Serviced on time, driven carefully, and it shows.',
      talkingPoint:
          'Manufacturer-verified service records and calm driving earn the best case: multiplier 0.700 on top of a 20% no-claim bonus.',
      expected: DemoExpected(
        usageScore: 100.0,
        maintenanceScore: 100.0,
        compositeScore: 100.0,
        dynamicMultiplier: 0.700,
        basePremium: 24960.00,
        finalPremium: 13977.60,
        healthStatus: 'scored',
        healthConfidence: 'verified',
        hasOpenRecall: false,
        dataFlags: [],
        advisoryTypes: ['SAFE_DRIVING_COMMENDATION'],
      ),
      suggestedClaim: DemoSuggestedClaim(
        claimedCause: 'mechanical_failure',
        expectedTriageDecision: 'MANUAL_REVIEW',
      ),
    ),
    DemoScenario(
      vehicleId: 'b2c3d4e5-f6a7-8901-bcde-f12345678901',
      displayName: 'The Overdue Hatchback',
      descriptor: 'Compact hatchback · 4 years',
      headline: '800 days since the last service, an open recall, and harsh driving.',
      talkingPoint:
          'The score tells the owner exactly what to fix; nothing here is a denial. Surcharge multiplier 1.092.',
      expected: DemoExpected(
        usageScore: 75.0,
        maintenanceScore: 15.0,
        compositeScore: 51.0,
        dynamicMultiplier: 1.092,
        basePremium: 12480.00,
        finalPremium: 12265.34,
        healthStatus: 'scored',
        healthConfidence: 'verified',
        hasOpenRecall: true,
        dataFlags: [],
        advisoryTypes: ['OPEN_RECALL', 'OVERDUE_SERVICE', 'LOW_RISK_SCORE'],
      ),
      suggestedClaim: DemoSuggestedClaim(
        claimedCause: 'mechanical_failure',
        expectedTriageDecision: 'MANUAL_REVIEW',
      ),
    ),
    DemoScenario(
      vehicleId: 'c3d4e5f6-a7b8-9012-cdef-123456789012',
      displayName: 'The Conflicting Records',
      descriptor: 'Premium SUV · 3 years',
      headline: 'Two service records that can\'t both be true.',
      talkingPoint:
          'A dealer record and a self-uploaded record whose odometer readings contradict each other. The engine trusts the verified record and flags the conflict instead of silently choosing.',
      expected: DemoExpected(
        usageScore: 85.0,
        maintenanceScore: 100.0,
        compositeScore: 91.0,
        dynamicMultiplier: 0.772,
        basePremium: 27300.00,
        finalPremium: 21075.60,
        healthStatus: 'scored',
        healthConfidence: 'verified',
        hasOpenRecall: false,
        dataFlags: ['conflicting_records_detected'],
        advisoryTypes: ['SAFE_DRIVING_COMMENDATION'],
      ),
      suggestedClaim: DemoSuggestedClaim(
        claimedCause: 'collision',
        expectedTriageDecision: 'STRAIGHT_THROUGH_PROCESSING',
      ),
    ),
    DemoScenario(
      vehicleId: 'd4e5f6a7-b8c9-0123-defa-234567890123',
      displayName: 'The Self-Reporter',
      descriptor: 'Mid-range sedan · 5 years',
      headline: 'Only self-uploaded records.',
      talkingPoint:
          'Self-reported data can never earn a top score: capped at 85 and labelled Unverified until a verified source confirms it.',
      expected: DemoExpected(
        usageScore: 100.0,
        maintenanceScore: 85.0,
        compositeScore: 94.0,
        dynamicMultiplier: 0.748,
        basePremium: 11700.00,
        finalPremium: 6563.70,
        healthStatus: 'scored',
        healthConfidence: 'unverified_self_reported',
        hasOpenRecall: false,
        dataFlags: [],
        advisoryTypes: ['SAFE_DRIVING_COMMENDATION'],
      ),
      suggestedClaim: DemoSuggestedClaim(
        claimedCause: 'theft',
        expectedTriageDecision: 'STRAIGHT_THROUGH_PROCESSING',
      ),
    ),
    DemoScenario(
      vehicleId: 'e5f6a7b8-c9d0-1234-efab-345678901234',
      displayName: 'Brand New',
      descriptor: 'Luxury SUV · new',
      headline: 'No history yet.',
      talkingPoint:
          'With no trips and no service records we say \'not enough data\', not 100 and not 0, keep the neutral 1.0 multiplier and nudge a baseline inspection.',
      expected: DemoExpected(
        usageScore: null,
        maintenanceScore: null,
        compositeScore: null,
        dynamicMultiplier: 1.0,
        basePremium: 44460.00,
        finalPremium: 44460.00,
        healthStatus: 'insufficient_data',
        healthConfidence: null,
        hasOpenRecall: false,
        dataFlags: ['usage_data_unavailable'],
        advisoryTypes: ['NO_SERVICE_RECORD'],
      ),
      suggestedClaim: DemoSuggestedClaim(
        claimedCause: 'collision',
        expectedTriageDecision: 'STRAIGHT_THROUGH_PROCESSING',
      ),
    ),
  ];

  static Future<List<DemoScenario>> loadScenarios() async {
    try {
      final jsonStr = await rootBundle.loadString('assets/demo/demo_scenario.json');
      final list = jsonDecode(jsonStr) as List<dynamic>;
      return list.map((e) => DemoScenario.fromJson(e as Map<String, dynamic>)).toList();
    } catch (_) {
      return fallbackScenarios;
    }
  }

  static DemoScenario? findByVehicleId(String vehicleId) {
    for (final s in fallbackScenarios) {
      if (s.vehicleId == vehicleId) return s;
    }
    return null;
  }
}
