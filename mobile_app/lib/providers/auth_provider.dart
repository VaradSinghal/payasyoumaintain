import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

final secureStorageProvider = Provider<FlutterSecureStorage>((ref) {
  return const FlutterSecureStorage();
});

class AuthState {
  final bool isLoading;
  final String? vehicleId;
  final String? policyId;
  final bool isDemo;

  AuthState({
    this.isLoading = true,
    this.vehicleId,
    this.policyId,
    this.isDemo = false,
  });
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
    final isDemoStr = await _storage.read(key: 'is_demo');
    state = AuthState(
      isLoading: false,
      vehicleId: vehicleId,
      policyId: policyId,
      isDemo: isDemoStr == 'true',
    );
  }

  Future<void> login(String vehicleId, String policyId, {bool isDemo = false}) async {
    await _storage.write(key: 'vehicle_id', value: vehicleId);
    await _storage.write(key: 'policy_id', value: policyId);
    await _storage.write(key: 'is_demo', value: isDemo ? 'true' : 'false');
    state = AuthState(
      isLoading: false,
      vehicleId: vehicleId,
      policyId: policyId,
      isDemo: isDemo,
    );
  }

  Future<void> logout() async {
    await _storage.delete(key: 'vehicle_id');
    await _storage.delete(key: 'policy_id');
    await _storage.delete(key: 'is_demo');
    state = AuthState(isLoading: false, vehicleId: null, policyId: null, isDemo: false);
  }
}

final authProvider = NotifierProvider<AuthNotifier, AuthState>(() {
  return AuthNotifier();
});
