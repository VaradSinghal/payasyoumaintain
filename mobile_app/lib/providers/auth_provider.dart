import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

final secureStorageProvider = Provider<FlutterSecureStorage>((ref) {
  return const FlutterSecureStorage();
});

class AuthState {
  final bool isLoading;
  final String? vehicleId;
  final String? policyId;

  AuthState({this.isLoading = true, this.vehicleId, this.policyId});
}

class AuthNotifier extends Notifier<AuthState> {
  late FlutterSecureStorage _storage;

  @override
  AuthState build() {
    _storage = ref.watch(secureStorageProvider);
    _init();
    return AuthState();
  }

  Future<void> _init() async {
    final vehicleId = await _storage.read(key: 'vehicle_id');
    final policyId = await _storage.read(key: 'policy_id');
    state = AuthState(isLoading: false, vehicleId: vehicleId, policyId: policyId);
  }

  Future<void> login(String vehicleId, String policyId) async {
    await _storage.write(key: 'vehicle_id', value: vehicleId);
    await _storage.write(key: 'policy_id', value: policyId);
    state = AuthState(isLoading: false, vehicleId: vehicleId, policyId: policyId);
  }

  Future<void> logout() async {
    await _storage.delete(key: 'vehicle_id');
    await _storage.delete(key: 'policy_id');
    state = AuthState(isLoading: false, vehicleId: null, policyId: null);
  }
}

final authProvider = NotifierProvider<AuthNotifier, AuthState>(() {
  return AuthNotifier();
});
