import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/api_providers.dart';
import '../providers/auth_provider.dart';
import '../models/models.dart';
import '../widgets/shared_widgets.dart';

class ClaimsScreen extends ConsumerStatefulWidget {
  const ClaimsScreen({super.key});

  @override
  ConsumerState<ClaimsScreen> createState() => _ClaimsScreenState();
}

class _ClaimsScreenState extends ConsumerState<ClaimsScreen> {
  String? _lookupClaimId;
  Map<String, dynamic>? _claimDetails;
  bool _isLoading = false;
  String? _error;

  final _lookupController = TextEditingController();

  Future<void> _lookupClaim(String claimId) async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final details = await ref.read(claimsApiProvider).getClaim(claimId);
      setState(() {
        _lookupClaimId = claimId;
        _claimDetails = details;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        // If 404, we can say "Claim not found"
        if (_error!.contains('404')) {
          _error = 'Claim not found';
        }
      });
    } finally {
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(body: AppLoading(message: 'Loading claim...'));
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Claims'),
        actions: [
          if (_lookupClaimId != null || _claimDetails != null)
            IconButton(
              icon: const Icon(Icons.close),
              tooltip: 'Clear Claim',
              onPressed: () {
                setState(() {
                  _lookupClaimId = null;
                  _claimDetails = null;
                  _error = null;
                });
              },
            ),
        ],
      ),
      body: SafeArea(
        child: _claimDetails != null
            ? _buildClaimStatus()
            : _buildFnolAndLookup(),
      ),
    );
  }

  Widget _buildClaimStatus() {
    final status = _claimDetails!['status'] as String?;
    final triageDecision = _claimDetails!['triage_decision'] as String?;
    final triageReasons = (_claimDetails!['triage_reasons'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [];

    String translatedDecision = triageDecision ?? 'Processing';
    Color decisionColor = Colors.grey;

    if (triageDecision == 'AUTO_APPROVED') {
      translatedDecision = 'Automatically Approved';
      decisionColor = Colors.green;
    } else if (triageDecision == 'MANUAL_REVIEW') {
      translatedDecision = 'Requires Manual Review';
      decisionColor = Colors.orange;
    } else if (triageDecision == 'AUTO_DENIED') {
      translatedDecision = 'Automatically Denied';
      decisionColor = Colors.red;
    }

    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Claim ID', style: Theme.of(context).textTheme.titleSmall),
                Text(_lookupClaimId!, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                const SizedBox(height: 16),
                Text('Status', style: Theme.of(context).textTheme.titleSmall),
                Text(status ?? 'UNKNOWN', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Triage Decision', style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Icon(Icons.info, color: decisionColor),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        translatedDecision,
                        style: TextStyle(fontWeight: FontWeight.bold, color: decisionColor, fontSize: 16),
                      ),
                    ),
                  ],
                ),
                if (triageReasons.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  const Text('Reasons:', style: TextStyle(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  ...triageReasons.map((r) => Padding(
                        padding: const EdgeInsets.symmetric(vertical: 4.0),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('• '),
                            Expanded(child: Text(r)),
                          ],
                        ),
                      )),
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildFnolAndLookup() {
    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Lookup Existing Claim', style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _lookupController,
                        decoration: const InputDecoration(
                          labelText: 'Claim ID',
                          border: OutlineInputBorder(),
                        ),
                      ),
                    ),
                    const SizedBox(width: 16),
                    ElevatedButton(
                      onPressed: () {
                        if (_lookupController.text.isNotEmpty) {
                          _lookupClaim(_lookupController.text.trim());
                        }
                      },
                      child: const Text('Lookup'),
                    ),
                  ],
                ),
                if (_error != null) ...[
                  const SizedBox(height: 16),
                  Text(_error!, style: const TextStyle(color: Colors.red)),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 24),
        const FnolFormWidget(),
      ],
    );
  }
}

class FnolFormWidget extends ConsumerStatefulWidget {
  const FnolFormWidget({super.key});

  @override
  ConsumerState<FnolFormWidget> createState() => _FnolFormWidgetState();
}

class _FnolFormWidgetState extends ConsumerState<FnolFormWidget> {
  DateTime? _incidentDate;
  String? _claimedCause;
  final _descController = TextEditingController();
  bool _isSubmitting = false;

  final List<String> _photos = [];

  final _causes = [
    'mechanical_failure',
    'collision',
    'theft',
    'natural_disaster',
    'vandalism',
    'other'
  ];

  Future<void> _submit() async {
    final vehicleId = ref.read(authProvider).vehicleId;
    final policyId = ref.read(authProvider).policyId;

    if (vehicleId == null || policyId == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Not logged in')));
      return;
    }

    if (_incidentDate == null || _claimedCause == null || _descController.text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Please fill all fields')));
      return;
    }

    setState(() => _isSubmitting = true);
    try {
      final req = FnolRequest(
        vehicleId: vehicleId,
        policyId: policyId,
        incidentDate: _incidentDate!.toUtc().toIso8601String(),
        claimedCause: _claimedCause!,
        incidentDescription: _descController.text,
        photos: _photos,
      );

      final res = await ref.read(claimsApiProvider).submitFnol(req);
      
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Claim submitted successfully! ID: ${res.claimId ?? "UNKNOWN"}')),
      );
      
      setState(() {
        _incidentDate = null;
        _claimedCause = null;
        _descController.clear();
        _photos.clear();
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Failed: $e')));
    } finally {
      setState(() => _isSubmitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isSubmitting) {
      return const Card(
        child: Padding(
          padding: EdgeInsets.all(32.0),
          child: AppLoading(message: 'Submitting claim...'),
        ),
      );
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('File a New Claim (FNOL)', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 16),
            
            DropdownButtonFormField<String>(
              decoration: const InputDecoration(labelText: 'Claimed Cause', border: OutlineInputBorder()),
              // ignore: deprecated_member_use
              value: _claimedCause,
              items: _causes.map((c) => DropdownMenuItem(value: c, child: Text(c))).toList(),
              onChanged: (val) => setState(() => _claimedCause = val),
            ),
            const SizedBox(height: 16),
            
            InkWell(
              onTap: () async {
                final date = await showDatePicker(
                  context: context,
                  initialDate: DateTime.now(),
                  firstDate: DateTime(2000),
                  lastDate: DateTime.now(),
                );
                if (date != null) {
                  setState(() => _incidentDate = date);
                }
              },
              child: InputDecorator(
                decoration: const InputDecoration(labelText: 'Incident Date', border: OutlineInputBorder()),
                child: Text(_incidentDate != null ? _incidentDate!.toIso8601String().split('T').first : 'Select Date'),
              ),
            ),
            const SizedBox(height: 16),
            
            TextField(
              controller: _descController,
              maxLines: 3,
              decoration: const InputDecoration(labelText: 'Incident Description', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 16),
            
            ElevatedButton.icon(
              icon: const Icon(Icons.add_a_photo),
              label: const Text('Add Photo Reference'),
              onPressed: () {
                setState(() {
                  _photos.add('photo_${DateTime.now().millisecondsSinceEpoch}.jpg');
                });
              },
            ),
            if (_photos.isNotEmpty) ...[
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: _photos.map((p) => Chip(
                  label: Text(p),
                  onDeleted: () => setState(() => _photos.remove(p)),
                )).toList(),
              ),
            ],
            
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _submit,
                child: const Text('Submit Claim'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
