import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/models.dart';
import '../providers/api_providers.dart';
import '../providers/auth_provider.dart';

enum ConnectionType { none, real, simulated }

class ObdState {
  final bool isConnected;
  final ConnectionType connectionType;
  final List<String> confirmedCodes;
  final List<String> pendingCodes;
  final DateTime? lastChecked;
  final bool newCodeDetected;

  ObdState({
    this.isConnected = false,
    this.connectionType = ConnectionType.none,
    this.confirmedCodes = const [],
    this.pendingCodes = const [],
    this.lastChecked,
    this.newCodeDetected = false,
  });

  ObdState copyWith({
    bool? isConnected,
    ConnectionType? connectionType,
    List<String>? confirmedCodes,
    List<String>? pendingCodes,
    DateTime? lastChecked,
    bool? newCodeDetected,
  }) {
    return ObdState(
      isConnected: isConnected ?? this.isConnected,
      connectionType: connectionType ?? this.connectionType,
      confirmedCodes: confirmedCodes ?? this.confirmedCodes,
      pendingCodes: pendingCodes ?? this.pendingCodes,
      lastChecked: lastChecked ?? this.lastChecked,
      newCodeDetected: newCodeDetected ?? this.newCodeDetected,
    );
  }
}

class ObdConnectionService extends Notifier<ObdState> {
  Timer? _pollingTimer;

  @override
  ObdState build() {
    return ObdState();
  }

  void connectReal() {
    _connect(ConnectionType.real);
  }

  void connectSimulated() {
    _connect(ConnectionType.simulated);
  }

  void _connect(ConnectionType type) {
    state = state.copyWith(isConnected: true, connectionType: type, lastChecked: DateTime.now(), newCodeDetected: false);
    // Post initial healthy reading (empty lists) on connect
    _postReading([], []);
    
    _pollingTimer?.cancel();
    _pollingTimer = Timer.periodic(const Duration(seconds: 30), (_) {
      _pollDtcCodes();
    });
  }

  void disconnect() {
    _pollingTimer?.cancel();
    state = state.copyWith(isConnected: false, connectionType: ConnectionType.none, newCodeDetected: false);
  }

  void checkNow() {
    _pollDtcCodes();
  }

  void _pollDtcCodes() {
    // Simulated mock codes that occasionally change.
    // For demo, we just add a pending code if there are none, or change it around.
    final currentConfirmed = ['P0101'];
    final currentPending = state.pendingCodes.isEmpty ? ['U0100'] : ['U0100', 'P0300'];

    if (_hasChanged(currentConfirmed, currentPending)) {
      state = state.copyWith(
        confirmedCodes: currentConfirmed,
        pendingCodes: currentPending,
        lastChecked: DateTime.now(),
        newCodeDetected: true,
      );
      _postReading(currentConfirmed, currentPending);
    } else {
      state = state.copyWith(lastChecked: DateTime.now(), newCodeDetected: false);
    }
  }

  void clearNewCodeFlag() {
    state = state.copyWith(newCodeDetected: false);
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

  void _postReading(List<String> confirmed, List<String> pending) async {
    final vehicleId = ref.read(authProvider).vehicleId;
    if (vehicleId == null) return;

    final reading = DtcReading(
      vehicleId: vehicleId,
      timestamp: DateTime.now().toUtc().toIso8601String(),
      confirmedCodes: confirmed,
      pendingCodes: pending,
      source: "obd_device",
      deviceId: "mock-dongle-123",
    );

    try {
      await ref.read(maintenanceApiProvider).postDtcReading(reading);
    } catch (e) {
      debugPrint("Failed to post DTC reading: $e");
    }
  }

  void disposeTimer() {
    _pollingTimer?.cancel();
  }
}

final obdConnectionProvider = NotifierProvider<ObdConnectionService, ObdState>(() {
  return ObdConnectionService();
});
