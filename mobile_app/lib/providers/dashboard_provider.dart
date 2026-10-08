import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/models.dart';
import 'api_providers.dart';
import 'auth_provider.dart';

class DashboardData {
  final QuoteResponse quote;
  final AdvisoryResponse advisories;

  DashboardData(this.quote, this.advisories);
}

final dashboardProvider = FutureProvider.autoDispose<DashboardData>((ref) async {
  final vehicleId = ref.watch(authProvider).vehicleId;
  if (vehicleId == null) throw Exception('No vehicle ID');

  final quoteFuture = ref.read(pricingApiProvider).quote(vehicleId);
  final advisoriesFuture = ref.read(advisoryApiProvider).getAdvisories(vehicleId);

  final results = await Future.wait([quoteFuture, advisoriesFuture]);

  return DashboardData(
    results[0] as QuoteResponse,
    results[1] as AdvisoryResponse,
  );
});
