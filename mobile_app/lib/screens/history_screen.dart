import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../models/models.dart';
import '../providers/api_providers.dart';
import '../providers/auth_provider.dart';
import '../providers/dashboard_provider.dart';
import '../widgets/shared_widgets.dart';

class HistoryScreen extends ConsumerStatefulWidget {
  final String? vehicleId;
  const HistoryScreen({super.key, this.vehicleId});

  @override
  ConsumerState<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends ConsumerState<HistoryScreen> {
  bool _isLoading = true;
  String? _error;
  MaintenanceTimeline? _timeline;
  List<TripAggregate>? _aggregates;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final syncData = ref.read(dashboardProvider).asData?.value;
    if (syncData?.quote.scoreDetail?.healthStatus == 'not_evaluated') {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
      return;
    }

    final vid = widget.vehicleId ?? ref.read(authProvider).vehicleId;
    if (vid == null) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _error = 'No active vehicle found.';
        });
      }
      return;
    }

    try {
      final dashboardData = await ref.read(dashboardProvider.future);
      if (dashboardData.quote.scoreDetail?.healthStatus == 'not_evaluated') {
        if (mounted) {
          setState(() {
            _isLoading = false;
          });
        }
        return;
      }
    } catch (_) {}

    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final results = await Future.wait([
        ref.read(maintenanceApiProvider).getTimeline(vid),
        ref.read(telematicsApiProvider).getAggregates(vid),
      ]);
      if (mounted) {
        setState(() {
          _timeline = results[0] as MaintenanceTimeline;
          _aggregates = results[1] as List<TripAggregate>;
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

  String _formatSourceBadge(String? source) {
    if (source == null) return 'Unknown';
    switch (source.toLowerCase()) {
      case 'oem':
        return 'OEM';
      case 'self_reported':
      case 'self-reported':
        return 'Self-reported';
      case 'inspection':
        return 'Inspection';
      default:
        return source;
    }
  }

  @override
  Widget build(BuildContext context) {
    final dashboardState = ref.watch(dashboardProvider);
    final isNotEvaluated = dashboardState.asData?.value.quote.scoreDetail?.healthStatus == 'not_evaluated';

    return Scaffold(
      appBar: AppBar(
        title: const Text('Why this score?'),
      ),
      body: isNotEvaluated
          ? _buildNotEvaluatedView(context)
          : _isLoading
              ? const AppLoading(message: 'Loading score evidence...')
              : _error != null
                  ? AppError(message: _error!, onRetry: _loadData)
                  : _buildHistoryContent(context),
    );
  }

  Widget _buildNotEvaluatedView(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.privacy_tip_outlined, size: 64, color: Colors.blue.shade700),
            const SizedBox(height: 16),
            const Text(
              'Turn on tracking to see your history here',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              'When usage and maintenance tracking are off, trip summaries and service logs are not processed.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey.shade700, fontSize: 14),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () => context.push('/privacy'),
              child: const Text('Privacy & tracking'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHistoryContent(BuildContext context) {
    final events = _timeline?.events ?? [];
    final trips = _aggregates ?? [];

    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        Text(
          'Service History',
          style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 8),
        if (events.isEmpty)
          const Card(
            child: Padding(
              padding: EdgeInsets.all(16.0),
              child: Text(
                'No service records yet',
                style: TextStyle(color: Colors.grey, fontSize: 15),
              ),
            ),
          )
        else
          ...events.map((e) => Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  title: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Text(
                          e.serviceType ?? 'General Service',
                          style: const TextStyle(fontWeight: FontWeight.bold),
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(
                          color: Colors.blue.shade50,
                          borderRadius: BorderRadius.circular(4),
                          border: Border.all(color: Colors.blue.shade300),
                        ),
                        child: Text(
                          _formatSourceBadge(e.source),
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: Colors.blue.shade900,
                          ),
                        ),
                      ),
                    ],
                  ),
                  subtitle: Padding(
                    padding: const EdgeInsets.only(top: 4.0),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(e.serviceDate ?? 'Unknown date'),
                        Text(e.odometerKm != null ? '${e.odometerKm!.toStringAsFixed(0)} km' : 'N/A'),
                      ],
                    ),
                  ),
                ),
              )),
        const SizedBox(height: 24),
        Text(
          'Recent Trips',
          style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 8),
        if (trips.isEmpty)
          const Card(
            child: Padding(
              padding: EdgeInsets.all(16.0),
              child: Text(
                'No trips recorded yet',
                style: TextStyle(color: Colors.grey, fontSize: 15),
              ),
            ),
          )
        else
          ...trips.map((t) {
            final dateStr = t.tripStart.isNotEmpty
                ? t.tripStart.contains('T')
                    ? t.tripStart.split('T').first
                    : t.tripStart
                : 'Recent trip';
            return Card(
              margin: const EdgeInsets.only(bottom: 8),
              child: ListTile(
                title: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(dateStr, style: const TextStyle(fontWeight: FontWeight.bold)),
                    Text('${t.distanceKm.toStringAsFixed(1)} km', style: const TextStyle(fontWeight: FontWeight.w600)),
                  ],
                ),
                subtitle: Padding(
                  padding: const EdgeInsets.only(top: 4.0),
                  child: Row(
                    children: [
                      Icon(Icons.warning_amber, size: 16, color: Colors.orange.shade800),
                      const SizedBox(width: 4),
                      Text('${t.harshBrakingCount} harsh braking'),
                      const SizedBox(width: 12),
                      Icon(Icons.speed, size: 16, color: Colors.deepOrange.shade700),
                      const SizedBox(width: 4),
                      Text('${t.hardAccelerationCount} hard acceleration'),
                    ],
                  ),
                ),
              ),
            );
          }),
      ],
    );
  }
}
