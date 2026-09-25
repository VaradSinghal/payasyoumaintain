import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

final secureStorageProvider = Provider<FlutterSecureStorage>((ref) {
  return const FlutterSecureStorage();
});

class AuthState {
  final bool isLoading;
  final String? vehicleId;

  AuthState({this.isLoading = true, this.vehicleId});
}

class AuthNotifier extends StateNotifier<AuthState> {
  final FlutterSecureStorage _storage;

  AuthNotifier(this._storage) : super(AuthState()) {
    _init();
  }

  Future<void> _init() async {
    final vehicleId = await _storage.read(key: 'vehicle_id');
    state = AuthState(isLoading: false, vehicleId: vehicleId);
  }

  Future<void> login(String vehicleId) async {
    await _storage.write(key: 'vehicle_id', value: vehicleId);
    state = AuthState(isLoading: false, vehicleId: vehicleId);
  }

  Future<void> logout() async {
    await _storage.delete(key: 'vehicle_id');
    state = AuthState(isLoading: false, vehicleId: null);
  }
}

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final storage = ref.watch(secureStorageProvider);
  return AuthNotifier(storage);
});
