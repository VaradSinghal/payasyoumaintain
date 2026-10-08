import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/premium_response.dart';

final dashboardProvider = FutureProvider<PremiumResponse>((ref) async {
  // Simulate network delay
  await Future.delayed(const Duration(seconds: 1));
  return PremiumResponse.mock();
});
