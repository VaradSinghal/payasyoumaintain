import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/obd_connection_service.dart';

class DiagnosticsScreen extends ConsumerStatefulWidget {
  const DiagnosticsScreen({super.key});

  @override
  ConsumerState<DiagnosticsScreen> createState() => _DiagnosticsScreenState();
}

class _DiagnosticsScreenState extends ConsumerState<DiagnosticsScreen> {
  
  @override
  Widget build(BuildContext context) {
    final obdState = ref.watch(obdConnectionProvider);

    ref.listen<ObdState>(obdConnectionProvider, (previous, next) {
      if (next.newCodeDetected && !(previous?.newCodeDetected ?? false)) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Row(
              children: [
                const Icon(Icons.warning, color: Colors.white),
                const SizedBox(width: 8),
                const Expanded(child: Text('New diagnostic code detected!')),
              ],
            ),
            backgroundColor: Colors.red.shade800,
            duration: const Duration(seconds: 4),
          ),
        );
        ref.read(obdConnectionProvider.notifier).clearNewCodeFlag();
      }
    });

    return Scaffold(
      appBar: AppBar(
        title: const Text('Vehicle Diagnostics'),
        actions: [
          if (obdState.isConnected)
            IconButton(
              icon: const Icon(Icons.power_settings_new),
              tooltip: 'Disconnect',
              onPressed: () {
                ref.read(obdConnectionProvider.notifier).disconnect();
              },
            ),
        ],
      ),
      body: obdState.isConnected 
          ? _buildConnectedView(context, obdState)
          : _buildDisconnectedView(context),
    );
  }

  Widget _buildDisconnectedView(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              children: [
                Icon(Icons.bluetooth_searching, size: 64, color: Theme.of(context).primaryColor),
                const SizedBox(height: 16),
                Text(
                  'Connect your OBD device for a fully verified health score',
                  style: Theme.of(context).textTheme.titleMedium,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    icon: const Icon(Icons.bluetooth),
                    label: const Text('Connect OBD Device'),
                    onPressed: () {
                      ref.read(obdConnectionProvider.notifier).connectReal();
                    },
                  ),
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    icon: const Icon(Icons.science),
                    label: const Text('Use Demo Device'),
                    onPressed: () {
                      ref.read(obdConnectionProvider.notifier).connectSimulated();
                    },
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 24),
        const Divider(),
        const SizedBox(height: 24),
        Card(
          child: InkWell(
            onTap: () => _showManualEntryDialog(context),
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Row(
                children: [
                  const Icon(Icons.edit_note, size: 32),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Enter Manually', style: Theme.of(context).textTheme.titleMedium),
                        const SizedBox(height: 4),
                        const Text('Upload a self-reported scan from your mechanic.'),
                      ],
                    ),
                  ),
                  const Icon(Icons.chevron_right),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildConnectedView(BuildContext context, ObdState state) {
    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        if (state.connectionType == ConnectionType.simulated)
          Container(
            padding: const EdgeInsets.all(8),
            margin: const EdgeInsets.only(bottom: 16),
            decoration: BoxDecoration(
              color: Colors.blue.shade100,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.info_outline, color: Colors.blue.shade900),
                const SizedBox(width: 8),
                Text('SIMULATED DEMO DEVICE', style: TextStyle(color: Colors.blue.shade900, fontWeight: FontWeight.bold)),
              ],
            ),
          ),
          
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Connection Status', style: Theme.of(context).textTheme.titleMedium),
                    const Chip(
                      label: Text('Connected', style: TextStyle(color: Colors.white, fontSize: 12)),
                      backgroundColor: Colors.green,
                      padding: EdgeInsets.zero,
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                if (state.lastChecked != null)
                  Text('Last checked: ${state.lastChecked.toString().split('.')[0]}', style: Theme.of(context).textTheme.bodySmall),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    icon: const Icon(Icons.refresh),
                    label: const Text('Check Now'),
                    onPressed: () {
                      ref.read(obdConnectionProvider.notifier).checkNow();
                    },
                  ),
                ),
              ],
            ),
          ),
        ),
        
        const SizedBox(height: 24),
        Text('Confirmed Codes (Mode 03)', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        _buildCodeList(state.confirmedCodes),
        
        const SizedBox(height: 24),
        Text('Pending Codes (Mode 07)', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        _buildCodeList(state.pendingCodes),
      ],
    );
  }

  Widget _buildCodeList(List<String> codes) {
    if (codes.isEmpty) {
      return Card(
        color: Colors.green.shade50,
        child: const Padding(
          padding: EdgeInsets.all(16.0),
          child: Row(
            children: [
              Icon(Icons.check_circle, color: Colors.green),
              SizedBox(width: 8),
              Text('No faults detected'),
            ],
          ),
        ),
      );
    }

    return Column(
      children: codes.map((code) => Card(
        child: ListTile(
          leading: const Icon(Icons.warning, color: Colors.orange),
          title: Text(code, style: const TextStyle(fontWeight: FontWeight.bold)),
          subtitle: const Text('Diagnostic Trouble Code'),
        ),
      )).toList(),
    );
  }

  void _showManualEntryDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => const ManualEntryForm(),
    );
  }
}

class ManualEntryForm extends ConsumerStatefulWidget {
  const ManualEntryForm({super.key});

  @override
  ConsumerState<ManualEntryForm> createState() => _ManualEntryFormState();
}

class _ManualEntryFormState extends ConsumerState<ManualEntryForm> {
  bool? _isLightOn;
  final _codeController = TextEditingController();

  final List<String> _commonCodes = ['P0101', 'P0300', 'P0420', 'U0100'];

  void _submit() {
    final codes = _isLightOn == true && _codeController.text.isNotEmpty
        ? [_codeController.text.trim().toUpperCase()]
        : <String>[];

    final payload = {
      "vehicle_id": "mock-vehicle-id",
      "timestamp": DateTime.now().toIso8601String(),
      "confirmed_codes": codes,
      "pending_codes": [],
      "source": "manual_entry",
      "device_id": null
    };
    
    print("Submitting manual entry to POST /api/v1/dtc-readings: $payload");
    
    Navigator.of(context).pop();
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Manual entry submitted successfully')),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
        left: 16,
        right: 16,
        top: 24,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Enter Diagnostics Manually', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),
          const Text('Is your check engine or warning light on?'),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: RadioListTile<bool>(
                  title: const Text('Yes'),
                  value: true,
                  groupValue: _isLightOn,
                  onChanged: (val) => setState(() => _isLightOn = val),
                ),
              ),
              Expanded(
                child: RadioListTile<bool>(
                  title: const Text('No'),
                  value: false,
                  groupValue: _isLightOn,
                  onChanged: (val) => setState(() => _isLightOn = val),
                ),
              ),
            ],
          ),
          if (_isLightOn == true) ...[
            const SizedBox(height: 16),
            TextField(
              controller: _codeController,
              decoration: const InputDecoration(
                labelText: 'Enter DTC Code (e.g. P0101)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            const Text('Common Codes:'),
            Wrap(
              spacing: 8,
              children: _commonCodes.map((code) => ActionChip(
                label: Text(code),
                onPressed: () {
                  setState(() {
                    _codeController.text = code;
                  });
                },
              )).toList(),
            ),
          ],
          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _isLightOn != null ? _submit : null,
              child: const Text('Submit Reading'),
            ),
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }
}
