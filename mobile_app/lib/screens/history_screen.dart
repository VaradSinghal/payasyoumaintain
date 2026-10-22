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

  static String? formatSourceLabel(String? source) {
    if (source == null) return null;
    switch (source.toLowerCase()) {
      case 'oem_api':
      case 'oem':
        return 'OEM';
      case 'self_upload':
      case 'self_reported':
      case 'self-reported':
        return 'Self-reported';
      case 'inspection':
        return 'Inspection';
      default:
        return null;
    }
  }

  static String formatSourceBadge(String? source) {
    return formatSourceLabel(source) ?? source ?? 'Unknown';
  }

  static String humaniseServiceType(String? serviceType) {
    if (serviceType == null || serviceType.trim().isEmpty) {
      return 'General service';
    }
    final clean = serviceType.replaceAll('_', ' ').trim();
    if (clean.isEmpty) return 'General service';
    return clean[0].toUpperCase() + clean.substring(1);
  }

  static String formatIndianInteger(int value) {
    final str = value.abs().toString();
    if (str.length <= 3) {
      return value < 0 ? '-$str' : str;
    }
    final lastThree = str.substring(str.length - 3);
    final rest = str.substring(0, str.length - 3);
    final buffer = StringBuffer();
    for (int i = 0; i < rest.length; i++) {
      if (i > 0 && (rest.length - i) % 2 == 0) {
        buffer.write(',');
      }
      buffer.write(rest[i]);
    }
    final res = '${buffer.toString()},$lastThree';
    return value < 0 ? '-$res' : res;
  }

  static String formatOdometer(num? odometerKm) {
    if (odometerKm == null) return 'N/A';
    return '${formatIndianInteger(odometerKm.round())} km';
  }

  @override
  ConsumerState<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends ConsumerState<HistoryScreen> {
  bool _isLoading = true;
  String? _error;
  MaintenanceTimeline? _timeline;
  List<TripAggregate>? _aggregates;
  bool _showAllTrips = false;
  bool _showFlagged = false;

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
        ref.read(maintenanceApiProvider).getTimeline(vid, includeFlagged: _showFlagged),
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

  Widget _buildSourceBadge(String? source) {
    final label = HistoryScreen.formatSourceLabel(source);
    final isKnown = label != null;
    final text = label ?? source ?? 'Unknown';

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: isKnown ? Colors.blue.shade50 : Colors.grey.shade100,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(
          color: isKnown ? Colors.blue.shade300 : Colors.grey.shade400,
        ),
      ),
      child: Text(
        text,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: isKnown ? Colors.blue.shade900 : Colors.grey.shade800,
        ),
      ),
    );
  }

  Widget _buildVerificationBadge(TimelineEvent e) {
    if (e.verificationStatus == 'flagged') {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
        decoration: BoxDecoration(
          color: Colors.orange.shade50,
          borderRadius: BorderRadius.circular(4),
          border: Border.all(
            color: Colors.orange.shade400,
          ),
        ),
        child: Text(
          'Flagged — not used in scoring',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: Colors.orange.shade900,
          ),
        ),
      );
    }
    if (e.source == 'self_upload') {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
        decoration: BoxDecoration(
          color: Colors.green.shade50,
          borderRadius: BorderRadius.circular(4),
          border: Border.all(
            color: Colors.green.shade400,
          ),
        ),
        child: Text(
          'Accepted',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: Colors.green.shade900,
          ),
        ),
      );
    }
    return const SizedBox.shrink();
  }

  @override
  Widget build(BuildContext context) {
    final dashboardState = ref.watch(dashboardProvider);
    final isNotEvaluated = dashboardState.asData?.value.quote.scoreDetail?.healthStatus == 'not_evaluated';

    return Scaffold(
      appBar: AppBar(
        title: const Text('Why this score?'),
        actions: [
          IconButton(
            key: const Key('add_service_record_button_appbar'),
            icon: const Icon(Icons.add),
            tooltip: 'Add a service record',
            onPressed: () async {
              await context.push('/add-service-record');
              _loadData();
            },
          ),
        ],
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
    final rawEvents = List<TimelineEvent>.from(_timeline?.events ?? []);
    final events = _showFlagged
        ? rawEvents
        : rawEvents.where((e) => e.verificationStatus != 'flagged').toList();

    // Sort service records newest first
    events.sort((a, b) {
      final dateA = a.serviceDate ?? '';
      final dateB = b.serviceDate ?? '';
      return dateB.compareTo(dateA);
    });

    final trips = List<TripAggregate>.from(_aggregates ?? []);
    // Sort trips newest first
    trips.sort((a, b) {
      final startA = a.tripStart;
      final startB = b.tripStart;
      return startB.compareTo(startA);
    });

    final displayedTrips = _showAllTrips ? trips : trips.take(10).toList();

    final authState = ref.watch(authProvider);
    final demoScenario = authState.isDemo && authState.vehicleId != null
        ? DemoScenario.findByVehicleId(authState.vehicleId!)
        : null;

    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        if (demoScenario != null)
          Container(
            margin: const EdgeInsets.only(bottom: 16),
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: Colors.blue.shade50,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: Colors.blue.shade200),
            ),
            child: Row(
              children: [
                Icon(Icons.directions_car, size: 20, color: Colors.blue.shade900),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Demo Evidence: ${demoScenario.displayName}',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 14,
                          color: Colors.blue.shade900,
                        ),
                      ),
                      Text(
                        demoScenario.descriptor,
                        style: TextStyle(fontSize: 12, color: Colors.blue.shade800),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Service History',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
            ),
            OutlinedButton.icon(
              key: const Key('add_service_record_button'),
              icon: const Icon(Icons.add, size: 16),
              label: const Text('Add a service record'),
              onPressed: () async {
                await context.push('/add-service-record');
                _loadData();
              },
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (_timeline != null && _timeline!.flaggedCount > 0) ...[
          Card(
            color: Colors.orange.shade50,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(8),
              side: BorderSide(color: Colors.orange.shade300),
            ),
            margin: const EdgeInsets.only(bottom: 12),
            child: SwitchListTile(
              key: const Key('show_flagged_switch'),
              title: const Text('Show flagged', style: TextStyle(fontWeight: FontWeight.w600)),
              subtitle: Text(
                _timeline!.flaggedCount == 1
                    ? '1 flagged record is not used in your score'
                    : '${_timeline!.flaggedCount} flagged records are not used in your score',
                style: TextStyle(color: Colors.orange.shade900, fontSize: 13),
              ),
              value: _showFlagged,
              onChanged: (val) {
                setState(() {
                  _showFlagged = val;
                });
                _loadData();
              },
            ),
          ),
        ],
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
                          HistoryScreen.humaniseServiceType(e.serviceType),
                          style: const TextStyle(fontWeight: FontWeight.bold),
                        ),
                      ),
                      Wrap(
                        spacing: 6,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          _buildSourceBadge(e.source),
                          _buildVerificationBadge(e),
                        ],
                      ),
                    ],
                  ),
                  subtitle: Padding(
                    padding: const EdgeInsets.only(top: 4.0),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(e.serviceDate ?? 'Unknown date'),
                        Text(HistoryScreen.formatOdometer(e.odometerKm)),
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
        else ...[
          ...displayedTrips.map((t) {
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
          if (trips.length > 10) ...[
            const SizedBox(height: 8),
            Center(
              child: TextButton.icon(
                key: const Key('show_all_trips_button'),
                icon: Icon(_showAllTrips ? Icons.expand_less : Icons.expand_more),
                label: Text(_showAllTrips ? 'Show less' : 'Show all'),
                onPressed: () {
                  setState(() {
                    _showAllTrips = !_showAllTrips;
                  });
                },
              ),
            ),
          ],
        ],
      ],
    );
  }
}
