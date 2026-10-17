import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../models/models.dart';
import '../providers/auth_provider.dart';
import '../providers/dashboard_provider.dart';
import '../utils/currency_formatter.dart';
import '../widgets/shared_widgets.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dashboardAsync = ref.watch(dashboardProvider);
    final authState = ref.watch(authProvider);

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
          child: _buildContent(context, data, authState),
        ),
        loading: () => const AppLoading(message: 'Loading dashboard...'),
        error: (error, stack) => AppError(
          message: error.toString(),
          onRetry: () => ref.invalidate(dashboardProvider),
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context, DashboardData data, AuthState authState) {
    final quote = data.quote;
    final advisories = data.advisories;
    final scoreDetail = quote.scoreDetail;

    final demoScenario = authState.vehicleId != null
        ? DemoScenario.findByVehicleId(authState.vehicleId!)
        : null;

    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        if (authState.isDemo && demoScenario != null)
          _buildAboutDemoVehicle(demoScenario),

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
                      CurrencyFormatter.format(quote.finalPremium, currency: quote.currency),
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
                _buildExplainMyPremium(context, quote),
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

  Widget _buildAboutDemoVehicle(DemoScenario scenario) {
    return Card(
      color: Colors.blue.shade50,
      margin: const EdgeInsets.only(bottom: 16),
      child: ExpansionTile(
        initiallyExpanded: true,
        leading: const Icon(Icons.directions_car, color: Colors.blue),
        title: Text('About this demo vehicle (${scenario.displayName})',
            style: const TextStyle(fontWeight: FontWeight.bold)),
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  scenario.headline,
                  style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
                ),
                const SizedBox(height: 8),
                Text(
                  scenario.talkingPoint,
                  style: TextStyle(color: Colors.grey.shade800, height: 1.3),
                ),
                const SizedBox(height: 8),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildExplainMyPremium(BuildContext context, QuoteResponse quote) {
    return Card(
      margin: const EdgeInsets.only(top: 12),
      elevation: 0,
      color: Colors.grey.shade50,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: BorderSide(color: Colors.grey.shade200),
      ),
      child: ExpansionTile(
        tilePadding: const EdgeInsets.symmetric(horizontal: 12),
        title: const Text(
          'Explain my premium',
          style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
        ),
        children: [
          Padding(
            padding: const EdgeInsets.all(12.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (quote.basePremium != null)
                  _buildPremiumDetailRow(
                    'Base premium',
                    CurrencyFormatter.format(quote.basePremium, currency: quote.currency),
                  ),
                ...quote.discountBreakdown.map((d) {
                  final label = _labelForDiscountReason(d.reason);
                  final sign = d.adjustmentPct > 0 ? '+' : '';
                  final pct = '$sign${d.adjustmentPct.toStringAsFixed(1)}%';
                  return _buildPremiumDetailRow(label, pct);
                }),
                _buildPremiumDetailRow(
                  'Multiplier',
                  quote.dynamicMultiplier.toStringAsFixed(3),
                ),
                const Divider(),
                _buildPremiumDetailRow(
                  'Final premium',
                  CurrencyFormatter.format(quote.finalPremium, currency: quote.currency),
                  isBold: true,
                ),
                if (quote.methodologyNote != null && quote.methodologyNote!.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Text(
                    quote.methodologyNote!,
                    style: TextStyle(
                      fontSize: 12,
                      fontStyle: FontStyle.italic,
                      color: Colors.grey.shade600,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPremiumDetailRow(String label, String value, {bool isBold = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: TextStyle(
              fontWeight: isBold ? FontWeight.bold : FontWeight.normal,
              fontSize: 14,
            ),
          ),
          Text(
            value,
            style: TextStyle(
              fontWeight: isBold ? FontWeight.bold : FontWeight.w500,
              fontSize: 14,
            ),
          ),
        ],
      ),
    );
  }

  String _labelForDiscountReason(String reason) {
    switch (reason) {
      case 'no_claim_bonus':
        return 'No-claim bonus';
      case 'risk_score_discount':
        return 'Good vehicle and driving record';
      case 'risk_score_surcharge':
        return 'Risk adjustment';
      case 'cold_start_neutral':
        return 'New policy, standard rate';
      default:
        return reason;
    }
  }

  String _labelForDataFlag(String flag) {
    switch (flag) {
      case 'conflicting_records_detected':
        return 'Conflicting service records found; the manufacturer-verified record was used';
      case 'usage_data_unavailable':
        return 'No trip data yet';
      case 'recall_status_unavailable':
        return "Recall status couldn't be checked";
      default:
        return flag;
    }
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
            if (detail.dataFlags.isNotEmpty) ...[
              const SizedBox(height: 12),
              Wrap(
                spacing: 8.0,
                runSpacing: 6.0,
                children: detail.dataFlags.map((flag) {
                  return Chip(
                    label: Text(
                      _labelForDataFlag(flag),
                      style: const TextStyle(fontSize: 12),
                    ),
                    backgroundColor: Colors.grey.shade100,
                    side: BorderSide(color: Colors.grey.shade300),
                  );
                }).toList(),
              ),
            ],
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
        if (a.priority == 'CRITICAL') {
          color = Colors.red.shade900;
        } else if (a.priority == 'HIGH') {
          color = Colors.red;
        } else if (a.priority == 'MEDIUM') {
          color = Colors.orange;
        } else if (a.priority == 'LOW') {
          color = Colors.blue;
        }

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
