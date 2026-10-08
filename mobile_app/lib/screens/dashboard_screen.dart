import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/dashboard_provider.dart';
import '../models/models.dart';
import '../widgets/shared_widgets.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dashboardAsync = ref.watch(dashboardProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Dashboard')),
      body: dashboardAsync.when(
        data: (data) => RefreshIndicator(
          onRefresh: () async {
            ref.invalidate(dashboardProvider);
            try {
              await ref.read(dashboardProvider.future);
            } catch (_) {}
          },
          child: _buildContent(context, data),
        ),
        loading: () => const AppLoading(message: 'Loading dashboard...'),
        error: (error, stack) => AppError(
          message: error.toString(),
          onRetry: () => ref.invalidate(dashboardProvider),
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context, DashboardData data) {
    final quote = data.quote;
    final advisories = data.advisories;
    final scoreDetail = quote.scoreDetail;

    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        if (scoreDetail != null) _buildRecallBanner(scoreDetail.hasOpenRecall),
        
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Your Premium Quote', style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 8),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    Text(
                      '\$${quote.finalPremium.toStringAsFixed(2)}',
                      style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                            color: Theme.of(context).primaryColor,
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                    const SizedBox(width: 8),
                    if (quote.methodologyNote != null)
                      Expanded(
                        child: Text(
                          quote.methodologyNote!,
                          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                color: Colors.grey.shade600,
                                fontStyle: FontStyle.italic,
                              ),
                        ),
                      ),
                  ],
                ),
              ],
            ),
          ),
        ),

        if (scoreDetail != null) ...[
          const SizedBox(height: 16),
          Card(
            color: Theme.of(context).primaryColorLight,
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    'Renewal Recommendation',
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    scoreDetail.renewalRecommendation ?? 'No recommendation',
                    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          _buildScoresSection(context, scoreDetail),
        ],

        const SizedBox(height: 16),
        _buildDiagnosticsShortcut(context),

        const SizedBox(height: 16),
        Text('Advisories', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        _buildAdvisoriesList(advisories.advisories),
      ],
    );
  }

  Widget _buildRecallBanner(bool? hasOpenRecall) {
    if (hasOpenRecall == true) {
      return Container(
        padding: const EdgeInsets.all(12),
        margin: const EdgeInsets.only(bottom: 16),
        decoration: BoxDecoration(
          color: Colors.red.shade100,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colors.red),
        ),
        child: Row(
          children: [
            const Icon(Icons.warning, color: Colors.red),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                'URGENT: Your vehicle has an open safety recall.',
                style: TextStyle(color: Colors.red.shade900, fontWeight: FontWeight.bold),
              ),
            ),
          ],
        ),
      );
    } else if (hasOpenRecall == null) {
      return Container(
        padding: const EdgeInsets.all(12),
        margin: const EdgeInsets.only(bottom: 16),
        decoration: BoxDecoration(
          color: Colors.amber.shade100,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colors.amber),
        ),
        child: Row(
          children: [
            const Icon(Icons.info, color: Colors.amber),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                "Recall status couldn't be checked",
                style: TextStyle(color: Colors.amber.shade900, fontWeight: FontWeight.bold),
              ),
            ),
          ],
        ),
      );
    }
    return const SizedBox.shrink();
  }

  Widget _buildScoresSection(BuildContext context, ScoreDetail detail) {
    final isInsufficient = detail.healthStatus == 'insufficient_data';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Scores', style: Theme.of(context).textTheme.titleMedium),
                if (isInsufficient)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.orange.shade100,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      'Insufficient Data',
                      style: TextStyle(color: Colors.orange.shade900, fontWeight: FontWeight.bold),
                    ),
                  )
                else if (detail.healthConfidence == 'unverified_self_reported')
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.amber.shade100,
                      borderRadius: BorderRadius.circular(4),
                      border: Border.all(color: Colors.amber),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.info_outline, size: 16, color: Colors.amber.shade900),
                        const SizedBox(width: 4),
                        Text(
                          'Unverified — self-reported only',
                          style: TextStyle(color: Colors.amber.shade900, fontSize: 12, fontWeight: FontWeight.bold),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            _buildScoreBar('Usage Score', detail.usageScore),
            const SizedBox(height: 16),
            _buildScoreBar('Maintenance Score', detail.maintenanceScore),
            if (detail.contributingFactors.isNotEmpty) ...[
              const SizedBox(height: 16),
              const Divider(),
              ExpansionTile(
                title: const Text('Contributing Factors'),
                children: detail.contributingFactors.map((f) {
                  return ListTile(
                    leading: Icon(
                      f.direction == 'positive'
                          ? Icons.arrow_upward
                          : f.direction == 'negative'
                              ? Icons.arrow_downward
                              : Icons.horizontal_rule,
                      color: f.direction == 'positive'
                          ? Colors.green
                          : f.direction == 'negative'
                              ? Colors.red
                              : Colors.grey,
                    ),
                    title: Text(f.factorName),
                    subtitle: Text(f.description ?? ''),
                  );
                }).toList(),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildScoreBar(String label, double? score) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(label, style: const TextStyle(fontWeight: FontWeight.w500)),
            Text(score == null ? 'Not enough data yet' : '${score.toStringAsFixed(0)} / 100'),
          ],
        ),
        const SizedBox(height: 8),
        LinearProgressIndicator(
          value: score == null ? 0 : score / 100,
          backgroundColor: Colors.grey.shade300,
          minHeight: 8,
          borderRadius: BorderRadius.circular(4),
        ),
      ],
    );
  }

  Widget _buildDiagnosticsShortcut(BuildContext context) {
    return Card(
      color: Theme.of(context).primaryColorLight,
      child: InkWell(
        onTap: () => context.push('/diagnostics'),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Row(
            children: [
              Icon(Icons.directions_car, size: 32, color: Theme.of(context).primaryColorDark),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Vehicle Diagnostics', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 4),
                    const Text('Connect your OBD device for a fully verified health score.'),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildAdvisoriesList(List<AdvisoryMessage> advisories) {
    if (advisories.isEmpty) {
      return Card(
        color: Colors.green.shade50,
        child: const Padding(
          padding: EdgeInsets.all(16.0),
          child: Row(
            children: [
              Icon(Icons.check_circle, color: Colors.green),
              SizedBox(width: 8),
              Text('All caught up', style: TextStyle(color: Colors.green, fontWeight: FontWeight.bold)),
            ],
          ),
        ),
      );
    }
    return Column(
      children: advisories.map((a) {
        Color color = Colors.grey;
        if (a.priority == 'HIGH') color = Colors.red;
        if (a.priority == 'MEDIUM') color = Colors.orange;
        if (a.priority == 'LOW') color = Colors.blue;

        return Card(
          child: ListTile(
            leading: Icon(Icons.info, color: color),
            title: Text(a.title, style: const TextStyle(fontWeight: FontWeight.bold)),
            subtitle: Text(a.message),
          ),
        );
      }).toList(),
    );
  }
}
