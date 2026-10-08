import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/models.dart';
import 'api_providers.dart';
import 'auth_provider.dart';

final latestDtcProvider = FutureProvider.autoDispose<DtcReading?>((ref) async {
  final vehicleId = ref.watch(authProvider).vehicleId;
  if (vehicleId == null) return null;
  return ref.read(maintenanceApiProvider).getLatestReading(vehicleId);
});
