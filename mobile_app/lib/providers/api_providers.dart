import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/api_clients.dart';

final identityApiProvider = Provider((ref) => IdentityConsentApi());
final pricingApiProvider = Provider((ref) => PricingPolicyApi());
final advisoryApiProvider = Provider((ref) => NotificationAdvisoryApi());
final claimsApiProvider = Provider((ref) => ClaimsVerificationApi());
final maintenanceApiProvider = Provider((ref) => MaintenanceApi());
final telematicsApiProvider = Provider((ref) => TelematicsApi());
final clockProvider = Provider<DateTime Function()>((ref) => DateTime.now);
