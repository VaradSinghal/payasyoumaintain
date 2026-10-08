import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/api_clients.dart';

final identityApiProvider = Provider((ref) => IdentityConsentApi());
final pricingApiProvider = Provider((ref) => PricingPolicyApi());
final advisoryApiProvider = Provider((ref) => NotificationAdvisoryApi());
final claimsApiProvider = Provider((ref) => ClaimsVerificationApi());
final maintenanceApiProvider = Provider((ref) => MaintenanceApi());
