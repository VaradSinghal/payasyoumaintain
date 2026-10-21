import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../models/models.dart';
import '../providers/auth_provider.dart';
import '../providers/dashboard_provider.dart';
import '../utils/currency_formatter.dart';
import '../widgets/pricing_basis_widget.dart';
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
          child: _buildContent(context, data, authState, ref),
        ),
        loading: () => const AppLoading(message: 'Loading dashboard...'),
        error: (error, stack) => AppError(
          message: error.toString(),
          onRetry: () => ref.invalidate(dashboardProvider),
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context, DashboardData data, AuthState authState, WidgetRef ref) {
    final quote = data.quote;
    final advisories = data.advisories;
    final scoreDetail = quote.scoreDetail;
    final isNotEvaluated = scoreDetail?.healthStatus == 'not_evaluated';
    final isUnavailable = scoreDetail?.dataFlags.contains('consent_status_unavailable') ?? false;

    final demoScenario = authState.vehicleId != null
        ? DemoScenario.findByVehicleId(authState.vehicleId!)
        : null;

    return ListView(
      scrollCacheExtent: const ScrollCacheExtent.viewport(3.0),
      padding: const EdgeInsets.all(16.0),
      children: [
        if (authState.isDemo && demoScenario != null)
          _buildAboutDemoVehicle(context, demoScenario),

        if (scoreDetail != null && !isNotEvaluated) _buildRecallBanner(scoreDetail.hasOpenRecall),

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
          if (isNotEvaluated)
            _buildTrackingOffCard(context, ref, isUnavailable)
          else
            _buildScoresSection(context, scoreDetail),
        ],

        const SizedBox(height: 16),
        _buildAddServiceRecordCard(context),

        const SizedBox(height: 16),
        _buildDiagnosticsShortcut(context),

        const SizedBox(height: 16),
        Text('Advisories', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        _buildAdvisoriesList(context, advisories.advisories),
      ],
    );
  }

  Widget _buildAboutDemoVehicle(BuildContext context, DemoScenario scenario) {
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
                const Divider(),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.shield_outlined, color: Colors.blue),
                  title: const Text(
                    'Privacy & tracking',
                    style: TextStyle(fontWeight: FontWeight.bold, color: Colors.blue),
                  ),
                  subtitle: const Text('Manage data sharing and tracking preferences'),
                  trailing: const Icon(Icons.chevron_right, color: Colors.blue),
                  onTap: () => context.push('/privacy'),
                ),
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
                PricingBasisSection(pricingBasis: quote.pricingBasis),
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
      case 'standard_pricing_no_consent':
        return 'Standard pricing (tracking off)';
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
    final visibleFlags = detail.dataFlags
        .where((flag) =>
            !flag.startsWith('consent_not_given_') &&
            flag != 'vehicle_not_registered' &&
            flag != 'consent_status_unavailable')
        .toList();

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
                  InkWell(
                    key: const Key('self_reported_badge'),
                    onTap: () {
                      showDialog(
                        context: context,
                        builder: (ctx) => AlertDialog(
                          title: const Text('Self-reported records'),
                          content: const Text(
                            'Self-reported records can raise your health score up to 85 until a verified source confirms them.',
                          ),
                          actions: [
                            TextButton(
                              onPressed: () => Navigator.of(ctx).pop(),
                              child: const Text('OK'),
                            ),
                          ],
                        ),
                      );
                    },
                    borderRadius: BorderRadius.circular(4),
                    child: Container(
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
                            'Includes self-reported records',
                            style: TextStyle(color: Colors.amber.shade900, fontSize: 12, fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
            if (visibleFlags.isNotEmpty) ...[
              const SizedBox(height: 12),
              Wrap(
                spacing: 8.0,
                runSpacing: 6.0,
                children: visibleFlags.map((flag) {
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
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                key: const Key('why_this_score_button'),
                icon: const Icon(Icons.history, size: 18),
                label: const Text('Why this score?'),
                onPressed: () => context.push('/history'),
              ),
            ),
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

  Widget _buildTrackingOffCard(BuildContext context, WidgetRef ref, bool isUnavailable) {
    if (isUnavailable) {
      return Card(
        key: const Key('consent_unavailable_card'),
        color: Colors.amber.shade50,
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.warning_amber_rounded, color: Colors.amber.shade900),
                  const SizedBox(width: 8),
                  Text(
                    'Preferences Unavailable',
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                      color: Colors.amber.shade900,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              const Text(
                "We couldn't check your tracking preferences. Standard pricing is shown.",
                style: TextStyle(fontSize: 14),
              ),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                key: const Key('retry_consent_button'),
                icon: const Icon(Icons.refresh, size: 18),
                label: const Text('Retry'),
                onPressed: () => ref.invalidate(dashboardProvider),
              ),
            ],
          ),
        ),
      );
    }

    return Card(
      key: const Key('tracking_off_card'),
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
                Expanded(
                  child: Text(
                    'Tracking is off — standard pricing',
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                      color: Colors.blue.shade900,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            const Text(
              'Your driving and maintenance data are not tracked. Turn tracking on to unlock personalised pricing and health insights.',
              style: TextStyle(fontSize: 14),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                ElevatedButton(
                  key: const Key('privacy_tracking_button'),
                  onPressed: () => context.push('/privacy'),
                  child: const Text('Privacy & tracking'),
                ),
                const SizedBox(width: 12),
                TextButton(
                  onPressed: () => context.push('/history'),
                  child: const Text('Why this score?'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAdvisoriesList(BuildContext context, List<AdvisoryMessage> advisories) {
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

        final isConsentAdvisory = a.advisoryType == 'TRACKING_DISABLED' ||
            (a.actionUrl != null &&
                (a.actionUrl!.contains('consent') ||
                    a.actionUrl!.contains('settings/consent') ||
                    a.actionUrl!.contains('privacy')));

        return Card(
          child: ListTile(
            leading: Icon(Icons.info, color: color),
            title: Text(a.title, style: const TextStyle(fontWeight: FontWeight.bold)),
            subtitle: Text(a.message),
            trailing: isConsentAdvisory ? const Icon(Icons.chevron_right) : null,
            onTap: isConsentAdvisory
                ? () {
                    // Map action_url to internal privacy screen; never open external https URL
                    context.push('/privacy');
                  }
                : null,
          ),
        );
      }).toList(),
    );
  }

  Widget _buildAddServiceRecordCard(BuildContext context) {
    return Card(
      key: const Key('add_service_record_card'),
      child: ListTile(
        leading: const Icon(Icons.build_circle_outlined, color: Colors.blue, size: 28),
        title: const Text('Add a service record', style: TextStyle(fontWeight: FontWeight.bold)),
        subtitle: const Text('Self-report recent maintenance or service entries'),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => context.push('/add-service-record'),
      ),
    );
  }
}
