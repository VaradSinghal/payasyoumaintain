import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../models/models.dart';
import '../providers/api_providers.dart';
import '../providers/auth_provider.dart';
import '../providers/dashboard_provider.dart';
import '../services/api_exception.dart';

class AddServiceRecordScreen extends ConsumerStatefulWidget {
  const AddServiceRecordScreen({super.key});

  static const Map<String, String> serviceTypeLabels = {
    'oil_change': 'Oil Change',
    'tyre_rotation': 'Tyre Rotation',
    'brake_service': 'Brake Service',
    'battery_replacement': 'Battery Replacement',
    'engine_service': 'Engine Service',
    'transmission_service': 'Transmission Service',
    'suspension_repair': 'Suspension Repair',
    'electrical_repair': 'Electrical Repair',
    'body_repair': 'Body Repair',
    'inspection': 'Inspection',
    'full_service': 'Full Service',
    'other': 'Other',
  };

  @override
  ConsumerState<AddServiceRecordScreen> createState() => _AddServiceRecordScreenState();
}

class _AddServiceRecordScreenState extends ConsumerState<AddServiceRecordScreen> {
  final _formKey = GlobalKey<FormState>();

  String _selectedServiceType = 'oil_change';
  DateTime? _selectedDate;
  final TextEditingController _dateController = TextEditingController();
  final TextEditingController _odometerController = TextEditingController();
  final TextEditingController _notesController = TextEditingController();
  final TextEditingController _receiptRefController = TextEditingController();

  bool _isSubmitting = false;
  String? _errorMessage;
  bool _consentRequired = false;

  ServiceRecordResponse? _result;

  @override
  void initState() {
    super.initState();
    final now = ref.read(clockProvider)();
    _selectedDate = DateTime(now.year, now.month, now.day);
    _dateController.text = _formatDate(_selectedDate!);
  }

  @override
  void dispose() {
    _dateController.dispose();
    _odometerController.dispose();
    _notesController.dispose();
    _receiptRefController.dispose();
    super.dispose();
  }

  String _formatDate(DateTime dt) {
    final y = dt.year.toString().padLeft(4, '0');
    final m = dt.month.toString().padLeft(2, '0');
    final d = dt.day.toString().padLeft(2, '0');
    return '$y-$m-$d';
  }

  DateTime? _parseDate(String str) {
    try {
      final parts = str.trim().split('-');
      if (parts.length == 3) {
        final y = int.parse(parts[0]);
        final m = int.parse(parts[1]);
        final d = int.parse(parts[2]);
        return DateTime(y, m, d);
      }
    } catch (_) {}
    return null;
  }

  Future<void> _pickDate() async {
    final now = ref.read(clockProvider)();
    final today = DateTime(now.year, now.month, now.day);
    final firstDate = DateTime(today.year - 5, today.month, today.day);

    final initial = _selectedDate ?? today;
    final validInitial = (initial.isBefore(firstDate) || initial.isAfter(today)) ? today : initial;

    final picked = await showDatePicker(
      context: context,
      initialDate: validInitial,
      firstDate: firstDate,
      lastDate: today,
    );

    if (picked != null) {
      setState(() {
        _selectedDate = picked;
        _dateController.text = _formatDate(picked);
      });
    }
  }

  Future<void> _submitForm() async {
    setState(() {
      _errorMessage = null;
    });

    if (!_formKey.currentState!.validate()) {
      return;
    }

    final vid = ref.read(authProvider).vehicleId;
    if (vid == null) {
      setState(() {
        _errorMessage = "This vehicle isn't registered.";
      });
      return;
    }

    final dateStr = _dateController.text.trim();
    final odo = int.parse(_odometerController.text.trim());

    final request = ServiceRecordRequest(
      vehicleId: vid,
      serviceDate: dateStr,
      serviceType: _selectedServiceType,
      odometerKm: odo,
      notes: _notesController.text.trim().isEmpty ? null : _notesController.text.trim(),
      receiptRef: _receiptRefController.text.trim().isEmpty ? null : _receiptRefController.text.trim(),
    );

    setState(() {
      _isSubmitting = true;
    });

    try {
      final response = await ref.read(maintenanceApiProvider).postServiceRecord(request);
      if (!mounted) return;

      // Invalidate dashboard and history providers on accepted or flagged
      if (response.stored || response.verification?.status == 'accepted' || response.verification?.status == 'flagged') {
        ref.invalidate(dashboardProvider);
      }

      setState(() {
        _isSubmitting = false;
        _result = response;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _isSubmitting = false;
        if (e.statusCode == 403 || e.error == 'CONSENT_REQUIRED') {
          _consentRequired = true;
        } else if (e.error == 'VEHICLE_NOT_REGISTERED') {
          _errorMessage = "This vehicle isn't registered.";
        } else if (e.statusCode == 503 || e.error == 'SERVICE_UNAVAILABLE') {
          _errorMessage = 'Service temporarily unavailable. Please try again.';
        } else if (e.error == 'INVALID_REQUEST') {
          _errorMessage = e.message;
        } else {
          _errorMessage = e.message;
        }
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isSubmitting = false;
        final str = e.toString();
        if (str.contains('503') || str.contains('SERVICE_UNAVAILABLE')) {
          _errorMessage = 'Service temporarily unavailable. Please try again.';
        } else if (str.contains('VEHICLE_NOT_REGISTERED')) {
          _errorMessage = "This vehicle isn't registered.";
        } else {
          _errorMessage = str;
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final dashboardAsync = ref.watch(dashboardProvider);
    final scoreDetail = dashboardAsync.asData?.value.quote.scoreDetail;
    final isNotEvaluated = scoreDetail?.healthStatus == 'not_evaluated';

    final showConsentCard = isNotEvaluated || _consentRequired;

    return Scaffold(
      appBar: AppBar(
        title: Text(_result == null ? 'Add Service Record' : 'Record Submitted'),
      ),
      body: showConsentCard
          ? _buildConsentRequiredView(context)
          : _result != null
              ? _buildResultView(context, _result!)
              : _buildFormView(context),
    );
  }

  Widget _buildConsentRequiredView(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Card(
          elevation: 2,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.privacy_tip_outlined, size: 56, color: Colors.orange.shade700),
                const SizedBox(height: 16),
                const Text(
                  'Turn on maintenance tracking to add records',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 12),
                Text(
                  'Maintenance tracking consent is required to process and verify self-reported service records.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.grey.shade700, fontSize: 14),
                ),
                const SizedBox(height: 24),
                ElevatedButton.icon(
                  key: const Key('privacy_tracking_redirect_button'),
                  onPressed: () => context.push('/privacy'),
                  icon: const Icon(Icons.settings),
                  label: const Text('Privacy & tracking'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildFormView(BuildContext context) {
    final now = ref.read(clockProvider)();
    final today = DateTime(now.year, now.month, now.day);
    final fiveYearsAgo = DateTime(today.year - 5, today.month, today.day);

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16.0),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (_errorMessage != null) ...[
              Container(
                key: const Key('error_banner'),
                padding: const EdgeInsets.all(12),
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: Colors.red.shade50,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.red.shade300),
                ),
                child: Row(
                  children: [
                    Icon(Icons.error_outline, color: Colors.red.shade700),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _errorMessage!,
                        style: TextStyle(color: Colors.red.shade900, fontWeight: FontWeight.w500),
                      ),
                    ),
                  ],
                ),
              ),
            ],
            // Service Type dropdown
            DropdownButtonFormField<String>(
              key: const Key('service_type_dropdown'),
              initialValue: _selectedServiceType,
              decoration: const InputDecoration(
                labelText: 'Service Type *',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.car_repair),
              ),
              items: AddServiceRecordScreen.serviceTypeLabels.entries.map((entry) {
                return DropdownMenuItem<String>(
                  value: entry.key,
                  child: Text(entry.value),
                );
              }).toList(),
              onChanged: (val) {
                if (val != null) {
                  setState(() {
                    _selectedServiceType = val;
                  });
                }
              },
            ),
            const SizedBox(height: 16),

            // Date picker
            TextFormField(
              key: const Key('service_date_field'),
              controller: _dateController,
              decoration: InputDecoration(
                labelText: 'Service Date (YYYY-MM-DD) *',
                hintText: 'YYYY-MM-DD',
                border: const OutlineInputBorder(),
                prefixIcon: const Icon(Icons.calendar_today),
                suffixIcon: IconButton(
                  key: const Key('calendar_picker_button'),
                  icon: const Icon(Icons.event),
                  onPressed: _pickDate,
                ),
              ),
              validator: (val) {
                if (val == null || val.trim().isEmpty) {
                  return 'Service date is required';
                }
                final parsed = _parseDate(val);
                if (parsed == null) {
                  return 'Enter a valid date (YYYY-MM-DD)';
                }
                if (parsed.isAfter(today)) {
                  return 'Service date cannot be in the future';
                }
                if (parsed.isBefore(fiveYearsAgo)) {
                  return 'Service date cannot be older than 5 years';
                }
                return null;
              },
            ),
            const SizedBox(height: 16),

            // Odometer
            TextFormField(
              key: const Key('odometer_field'),
              controller: _odometerController,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: const InputDecoration(
                labelText: 'Odometer (km) *',
                hintText: 'e.g. 15000',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.speed),
              ),
              validator: (val) {
                if (val == null || val.trim().isEmpty) {
                  return 'Odometer reading is required';
                }
                final numVal = int.tryParse(val.trim());
                if (numVal == null) {
                  return 'Enter a valid odometer reading';
                }
                if (numVal < 0 || numVal > 1500000) {
                  return 'Odometer must be between 0 and 1,500,000 km';
                }
                return null;
              },
            ),
            const SizedBox(height: 16),

            // Notes (optional, 500 chars)
            TextFormField(
              key: const Key('notes_field'),
              controller: _notesController,
              maxLength: 500,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Notes (optional)',
                hintText: 'Details from technician or garage...',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.note_alt_outlined),
              ),
            ),
            const SizedBox(height: 8),

            // Receipt reference (optional, 200 chars)
            TextFormField(
              key: const Key('receipt_ref_field'),
              controller: _receiptRefController,
              maxLength: 200,
              decoration: const InputDecoration(
                labelText: 'Receipt reference (optional)',
                helperText: "reference only; receipts aren't uploaded in this version",
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.receipt_outlined),
              ),
            ),
            const SizedBox(height: 24),

            // Submit Button
            ElevatedButton(
              key: const Key('submit_service_record_button'),
              onPressed: _isSubmitting ? null : _submitForm,
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
              ),
              child: _isSubmitting
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : const Text('Save Record', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildResultView(BuildContext context, ServiceRecordResponse res) {
    final status = res.verification?.status.toLowerCase() ?? (res.stored ? 'accepted' : 'rejected');
    final checks = res.verification?.checks ?? [];

    Color chipColor;
    Color chipTextColor;
    String statusLabel;
    String explanation;

    if (status == 'accepted') {
      chipColor = Colors.green.shade100;
      chipTextColor = Colors.green.shade900;
      statusLabel = 'Accepted';
      explanation = 'Saved. Self-reported records can raise your health score up to 85 until a verified source confirms them.';
    } else if (status == 'flagged') {
      chipColor = Colors.orange.shade100;
      chipTextColor = Colors.orange.shade900;
      statusLabel = 'Flagged for review';
      explanation = "Saved, but not used in your score until it's reviewed.";
    } else if (status == 'rejected') {
      chipColor = Colors.red.shade100;
      chipTextColor = Colors.red.shade900;
      statusLabel = 'Rejected';
      final failedChecks = checks.where((c) => !c.passed).map((c) => c.message.isNotEmpty ? c.message : c.code).join('; ');
      explanation = failedChecks.isNotEmpty ? 'Not saved. $failedChecks' : 'Not saved.';
    } else {
      chipColor = Colors.grey.shade200;
      chipTextColor = Colors.grey.shade900;
      statusLabel = status;
      explanation = res.stored ? 'Saved.' : 'Not saved.';
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Header Card with Status Chip
          Card(
            elevation: 2,
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                children: [
                  Container(
                    key: const Key('status_chip'),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                    decoration: BoxDecoration(
                      color: chipColor,
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(color: chipTextColor.withAlpha(128)),
                    ),
                    child: Text(
                      statusLabel,
                      style: TextStyle(
                        color: chipTextColor,
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    explanation,
                    textAlign: TextAlign.center,
                    style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w500),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Consistency Checks List
          Text(
            'Consistency Checks',
            style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          if (checks.isEmpty)
            const Card(
              child: Padding(
                padding: EdgeInsets.all(16.0),
                child: Text('No consistency check details returned.'),
              ),
            )
          else
            ...checks.map((check) {
              return Card(
                key: Key('check_${check.code}'),
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  leading: Icon(
                    check.passed ? Icons.check_circle : Icons.cancel,
                    color: check.passed ? Colors.green : Colors.red,
                    size: 24,
                  ),
                  title: Text(
                    check.message.isNotEmpty ? check.message : check.code,
                    style: TextStyle(
                      fontWeight: check.passed ? FontWeight.normal : FontWeight.bold,
                      color: check.passed ? Colors.black87 : Colors.red.shade900,
                    ),
                  ),
                  subtitle: Text(
                    check.code,
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                  ),
                ),
              );
            }),

          const SizedBox(height: 24),
          ElevatedButton(
            key: const Key('done_button'),
            onPressed: () {
              if (context.canPop()) {
                context.pop();
              } else {
                context.go('/');
              }
            },
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 16),
            ),
            child: const Text('Done', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }
}
