import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/api_providers.dart';
import '../providers/auth_provider.dart';
import '../providers/dashboard_provider.dart';
import '../widgets/shared_widgets.dart';

class PrivacyScreen extends ConsumerStatefulWidget {
  final String? vehicleId;
  const PrivacyScreen({super.key, this.vehicleId});

  @override
  ConsumerState<PrivacyScreen> createState() => _PrivacyScreenState();
}

class _PrivacyScreenState extends ConsumerState<PrivacyScreen> {
  bool _isLoading = true;
  bool _isSaving = false;
  String? _error;

  bool _usageTracking = false;
  bool _maintenanceTracking = false;

  @override
  void initState() {
    super.initState();
    _fetchConsent();
  }

  Future<void> _fetchConsent() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    final vid = widget.vehicleId ?? ref.read(authProvider).vehicleId;
    if (vid == null) {
      setState(() {
        _isLoading = false;
        _error = 'No active vehicle session found.';
      });
      return;
    }

    try {
      final consent = await ref.read(identityApiProvider).getConsent(vid);
      if (mounted) {
        setState(() {
          _usageTracking = consent.usageTrackingOptIn;
          _maintenanceTracking = consent.maintenanceTrackingOptIn;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _saveConsent() async {
    final vid = widget.vehicleId ?? ref.read(authProvider).vehicleId;
    if (vid == null) return;

    setState(() {
      _isSaving = true;
      _error = null;
    });

    try {
      await ref.read(identityApiProvider).updateConsent(
            vid,
            _usageTracking,
            _maintenanceTracking,
          );
      ref.invalidate(dashboardProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Preferences saved')),
        );
        setState(() => _isSaving = false);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _isSaving = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Privacy & tracking'),
      ),
      body: _isLoading
          ? const AppLoading(message: 'Loading preferences...')
          : _error != null && !_isSaving
              ? AppError(
                  message: _error!,
                  onRetry: _fetchConsent,
                )
              : ListView(
                  padding: const EdgeInsets.all(16.0),
                  children: [
                    Card(
                      color: Colors.blue.shade50,
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Icon(Icons.shield_outlined, color: Colors.blue.shade900),
                                const SizedBox(width: 8),
                                Text(
                                  'Your Tracking Preferences',
                                  style: TextStyle(
                                    fontSize: 16,
                                    fontWeight: FontWeight.bold,
                                    color: Colors.blue.shade900,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            const Text(
                              'Off: you pay the standard premium with no personalised insights. '
                              'On: your premium reflects your driving and servicing, and can go down or up.',
                              style: TextStyle(fontSize: 14, height: 1.4),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Card(
                      child: Column(
                        children: [
                          SwitchListTile(
                            key: const Key('usage_tracking_switch'),
                            title: const Text(
                              'Usage tracking',
                              style: TextStyle(fontWeight: FontWeight.bold),
                            ),
                            subtitle: const Text('Trips and driving behaviour'),
                            value: _usageTracking,
                            onChanged: (val) => setState(() => _usageTracking = val),
                          ),
                          const Divider(height: 1),
                          SwitchListTile(
                            key: const Key('maintenance_tracking_switch'),
                            title: const Text(
                              'Maintenance tracking',
                              style: TextStyle(fontWeight: FontWeight.bold),
                            ),
                            subtitle: const Text('Service records and recalls'),
                            value: _maintenanceTracking,
                            onChanged: (val) => setState(() => _maintenanceTracking = val),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    Card(
                      color: Colors.amber.shade50,
                      child: const Padding(
                        padding: EdgeInsets.all(16.0),
                        child: Text(
                          'What changes when tracking is off:\n'
                          '• Standard pricing is applied without personalized discounts.\n'
                          '• No personalised insights or commendations.\n'
                          '• Service history and trip logs will not be displayed.',
                          style: TextStyle(fontSize: 13, height: 1.4),
                        ),
                      ),
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: 12),
                      Text(_error!, style: const TextStyle(color: Colors.red)),
                    ],
                    const SizedBox(height: 24),
                    SizedBox(
                      width: double.infinity,
                      height: 48,
                      child: ElevatedButton(
                        key: const Key('save_preferences_button'),
                        onPressed: _isSaving ? null : _saveConsent,
                        child: _isSaving
                            ? const CircularProgressIndicator(color: Colors.white)
                            : const Text('Save preferences', style: TextStyle(fontSize: 16)),
                      ),
                    ),
                  ],
                ),
    );
  }
}
