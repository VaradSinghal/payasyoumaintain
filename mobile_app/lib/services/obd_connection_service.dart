import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class ObdState {
  final bool isConnected;
  final List<String> confirmedCodes;
  final List<String> pendingCodes;
  final DateTime? lastChecked;

  ObdState({
    this.isConnected = false,
    this.confirmedCodes = const [],
    this.pendingCodes = const [],
    this.lastChecked,
  });

  ObdState copyWith({
    bool? isConnected,
    List<String>? confirmedCodes,
    List<String>? pendingCodes,
    DateTime? lastChecked,
  }) {
    return ObdState(
      isConnected: isConnected ?? this.isConnected,
      confirmedCodes: confirmedCodes ?? this.confirmedCodes,
      pendingCodes: pendingCodes ?? this.pendingCodes,
      lastChecked: lastChecked ?? this.lastChecked,
    );
  }
}

class ObdConnectionService extends Notifier<ObdState> {
  Timer? _pollingTimer;

  @override
  ObdState build() {
    return ObdState();
  }

  void connect() {
    state = state.copyWith(isConnected: true, lastChecked: DateTime.now());
    // Post initial healthy reading (empty lists) on connect
    _postReading([], []);
    
    _pollingTimer = Timer.periodic(const Duration(seconds: 30), (_) {
      _pollDtcCodes();
    });
  }

  void disconnect() {
    _pollingTimer?.cancel();
    state = state.copyWith(isConnected: false);
  }

  void _pollDtcCodes() {
    // Simulated mock codes
    final currentConfirmed = ['P0101'];
    final currentPending = ['U0100'];

    if (_hasChanged(currentConfirmed, currentPending)) {
      state = state.copyWith(
        confirmedCodes: currentConfirmed,
        pendingCodes: currentPending,
        lastChecked: DateTime.now(),
      );
      _postReading(currentConfirmed, currentPending);
    }
  }

  bool _hasChanged(List<String> newConfirmed, List<String> newPending) {
    if (state.confirmedCodes.length != newConfirmed.length ||
        state.pendingCodes.length != newPending.length) {
      return true;
    }
    
    for (var code in newConfirmed) {
      if (!state.confirmedCodes.contains(code)) return true;
    }
    for (var code in newPending) {
      if (!state.pendingCodes.contains(code)) return true;
    }
    for (var code in state.confirmedCodes) {
      if (!newConfirmed.contains(code)) return true;
    }
    for (var code in state.pendingCodes) {
      if (!newPending.contains(code)) return true;
    }
    return false;
  }

  void _postReading(List<String> confirmed, List<String> pending) {
    // Send to backend via HTTP (mocked for now, or real API client)
    final payload = {
      "vehicle_id": "mock-vehicle-id",
      "timestamp": DateTime.now().toIso8601String(),
      "confirmed_codes": confirmed,
      "pending_codes": pending,
      "source": "obd_device",
      "device_id": "mock-dongle-123"
    };
    print("Posting reading to backend: $payload");
    // TODO: Send via MaintenanceApiClient
  }

  void disposeTimer() {
    _pollingTimer?.cancel();
  }
}

final obdConnectionProvider = NotifierProvider<ObdConnectionService, ObdState>(() {
  return ObdConnectionService();
});
