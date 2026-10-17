import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/models.dart';
import '../providers/api_providers.dart';
import '../providers/auth_provider.dart';
import '../utils/currency_formatter.dart';
import '../widgets/shared_widgets.dart';

enum OnboardingView { welcome, registerForm, consent, quote }

class OnboardingScreen extends ConsumerStatefulWidget {
  const OnboardingScreen({super.key});

  @override
  ConsumerState<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends ConsumerState<OnboardingScreen> {
  OnboardingView _currentView = OnboardingView.welcome;
  bool _isLoading = false;
  String? _error;

  String? _vehicleId;
  String? _policyId;
  QuoteResponse? _quote;

  bool _usageTracking = false;
  bool _maintenanceTracking = false;

  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _rcController = TextEditingController();
  final TextEditingController _regDateController = TextEditingController();
  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _phoneController = TextEditingController();
  final TextEditingController _panController = TextEditingController();

  List<DemoScenario> _scenarios = DemoScenario.fallbackScenarios;

  @override
  void initState() {
    super.initState();
    _loadScenarios();
  }

  Future<void> _loadScenarios() async {
    final list = await DemoScenario.loadScenarios();
    if (mounted) {
      setState(() => _scenarios = list);
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    _rcController.dispose();
    _regDateController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    _panController.dispose();
    super.dispose();
  }

  void _fillDemoDetails() {
    setState(() {
      _nameController.text = 'Demo Driver (Fictional)';
      _rcController.text = 'DL01DEMO9999';
      _regDateController.text = '2024-01-15';
      _emailController.text = 'demo.driver@example.test';
      _phoneController.text = '9999999999';
      _panController.text = 'ABCDE9999Z';
    });
  }

  Future<void> _selectDemoVehicle(DemoScenario scenario) async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final quote = await ref.read(pricingApiProvider).quote(scenario.vehicleId);
      final policyId = quote.policyId ?? 'demo-policy-${scenario.vehicleId.substring(0, 8)}';
      await ref.read(authProvider.notifier).login(scenario.vehicleId, policyId, isDemo: true);
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _register() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final name = _nameController.text.trim().isNotEmpty
          ? _nameController.text.trim()
          : 'PayAsUMaintain User';
      final rc = _rcController.text.trim().isNotEmpty
          ? _rcController.text.trim()
          : 'MH12AB1234';
      final date = _regDateController.text.trim().isNotEmpty
          ? _regDateController.text.trim()
          : '2024-01-15';
      final email = _emailController.text.trim().isNotEmpty
          ? _emailController.text.trim()
          : 'user@payasumaintain.com';
      final phone = _phoneController.text.trim().isNotEmpty
          ? _phoneController.text.trim()
          : '9876543210';
      final pan = _panController.text.trim().isNotEmpty
          ? _panController.text.trim()
          : 'ABCDE1234F';

      final reg = await ref.read(identityApiProvider).registerVehicle({
        'rc_number': rc,
        'registration_date': date,
        'owner_details': {
          'full_name': name,
          'kyc_document_number': pan,
          'kyc_document_type': 'PAN',
          'email': email,
          'phone_number': phone,
        },
      });
      setState(() {
        _vehicleId = reg.vehicleId;
        _policyId = reg.policyId;
        _currentView = OnboardingView.consent;
      });
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _submitConsent() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      await ref
          .read(identityApiProvider)
          .updateConsent(_vehicleId!, _usageTracking, _maintenanceTracking);
      final quote = await ref.read(pricingApiProvider).quote(_vehicleId!);
      setState(() {
        _quote = quote;
        _currentView = OnboardingView.quote;
      });
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _finishOnboarding() async {
    await ref.read(authProvider.notifier).login(_vehicleId!, _policyId!, isDemo: false);
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(body: AppLoading(message: 'Processing...'));
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(_currentView == OnboardingView.welcome
            ? 'Pay As You Maintain'
            : 'Onboarding'),
        leading: _currentView == OnboardingView.registerForm
            ? IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: () => setState(() => _currentView = OnboardingView.welcome),
              )
            : null,
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: _buildCurrentView(),
        ),
      ),
    );
  }

  Widget _buildCurrentView() {
    switch (_currentView) {
      case OnboardingView.welcome:
        return _buildWelcome();
      case OnboardingView.registerForm:
        return _buildRegisterForm();
      case OnboardingView.consent:
        return _buildConsent();
      case OnboardingView.quote:
        return _buildQuote();
    }
  }

  Widget _buildWelcome() {
    return ListView(
      children: [
        const Text(
          'Welcome to Pay As You Maintain!',
          style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 12),
        const Text(
          'Get fair, verified auto insurance based on how you drive and maintain your car.',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 15, color: Colors.black87),
        ),
        const SizedBox(height: 24),
        SizedBox(
          width: double.infinity,
          height: 48,
          child: ElevatedButton.icon(
            icon: const Icon(Icons.app_registration),
            onPressed: () => setState(() => _currentView = OnboardingView.registerForm),
            label: const Text('Register my vehicle', style: TextStyle(fontSize: 16)),
          ),
        ),
        const SizedBox(height: 32),
        Row(
          children: [
            const Expanded(child: Divider()),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0),
              child: Text(
                'Explore demo vehicles',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: Colors.blue.shade900,
                    ),
              ),
            ),
            const Expanded(child: Divider()),
          ],
        ),
        const SizedBox(height: 16),
        if (_error != null) ...[
          Text(_error!, style: const TextStyle(color: Colors.red)),
          const SizedBox(height: 12),
        ],
        ..._scenarios.map((s) => _buildDemoCard(s)),
      ],
    );
  }

  Widget _buildDemoCard(DemoScenario scenario) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      elevation: 2,
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => _selectDemoVehicle(scenario),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Text(
                      scenario.displayName,
                      style: const TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.blue.shade50,
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      scenario.descriptor,
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.blue.shade900,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                scenario.headline,
                style: TextStyle(
                  fontSize: 14,
                  color: Colors.grey.shade800,
                  height: 1.3,
                ),
              ),
              const SizedBox(height: 10),
              const Divider(height: 1),
              const SizedBox(height: 8),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Privacy & tracking',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: Colors.blue.shade900,
                    ),
                  ),
                  Icon(Icons.shield_outlined, size: 16, color: Colors.blue.shade900),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildRegisterForm() {
    const bool showDemoTools = !kReleaseMode ||
        bool.fromEnvironment('DEMO_TOOLS', defaultValue: false);

    return ListView(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text(
              'Vehicle Registration',
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
            ),
            if (showDemoTools)
              TextButton.icon(
                onPressed: _fillDemoDetails,
                icon: const Icon(Icons.flash_on, size: 18),
                label: const Text('Fill demo details'),
              ),
          ],
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _nameController,
          decoration: const InputDecoration(
            labelText: 'Full Name',
            hintText: 'e.g. Test Owner',
            border: OutlineInputBorder(),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _rcController,
          decoration: const InputDecoration(
            labelText: 'RC Number',
            hintText: 'e.g. DL01AB1234',
            border: OutlineInputBorder(),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _regDateController,
          decoration: const InputDecoration(
            labelText: 'Registration Date',
            hintText: 'YYYY-MM-DD',
            border: OutlineInputBorder(),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _emailController,
          decoration: const InputDecoration(
            labelText: 'Email Address',
            hintText: 'user@example.com',
            border: OutlineInputBorder(),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _phoneController,
          decoration: const InputDecoration(
            labelText: 'Phone Number',
            hintText: '10-digit mobile number',
            border: OutlineInputBorder(),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _panController,
          decoration: const InputDecoration(
            labelText: 'PAN Document Number',
            hintText: 'e.g. ABCDE1234F',
            border: OutlineInputBorder(),
          ),
        ),
        if (_error != null) ...[
          const SizedBox(height: 16),
          Text(_error!, style: const TextStyle(color: Colors.red)),
        ],
        const SizedBox(height: 24),
        SizedBox(
          width: double.infinity,
          height: 48,
          child: ElevatedButton(
            onPressed: _register,
            child: const Text('Register Vehicle', style: TextStyle(fontSize: 16)),
          ),
        ),
      ],
    );
  }

  Widget _buildConsent() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Data Sharing Preferences',
          style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 16),
        const Text(
          'Off: you pay the standard premium. On: your premium can reflect your driving and servicing, and it can go down or up. You can change this any time in Privacy & tracking.',
          style: TextStyle(fontSize: 16),
        ),
        const SizedBox(height: 32),
        SwitchListTile(
          title: const Text('Usage Tracking'),
          subtitle: const Text('Allow tracking of mileage, speed, and braking'),
          value: _usageTracking,
          onChanged: (val) => setState(() => _usageTracking = val),
        ),
        SwitchListTile(
          title: const Text('Maintenance Tracking'),
          subtitle: const Text('Allow tracking of OBD diagnostic codes and recall status'),
          value: _maintenanceTracking,
          onChanged: (val) => setState(() => _maintenanceTracking = val),
        ),
        if (_error != null) ...[
          const SizedBox(height: 16),
          Text(_error!, style: const TextStyle(color: Colors.red)),
        ],
        const Spacer(),
        SizedBox(
          width: double.infinity,
          height: 48,
          child: ElevatedButton(
            onPressed: _submitConsent,
            child: const Text('Continue to Quote', style: TextStyle(fontSize: 16)),
          ),
        ),
      ],
    );
  }

  Widget _buildQuote() {
    final formattedPremium = _quote != null
        ? CurrencyFormatter.format(_quote!.finalPremium, currency: _quote!.currency)
        : '₹0.00';
    final isTrackingOff = !_usageTracking && !_maintenanceTracking;

    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const Text(
          'Your Baseline Quote',
          style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 32),
        if (_quote != null) ...[
          Text(
            '$formattedPremium / month',
            style: const TextStyle(
                fontSize: 40, fontWeight: FontWeight.bold, color: Colors.green),
          ),
          const SizedBox(height: 16),
          if (isTrackingOff) ...[
            Card(
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
                      'Standard pricing is applied because tracking is off.',
                      style: TextStyle(fontSize: 14),
                    ),
                  ],
                ),
              ),
            ),
          ] else ...[
            const Text(
              "We'll personalise your premium as data arrives",
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 16, color: Colors.black87, fontWeight: FontWeight.w500),
            ),
          ],
        ],
        const Spacer(),
        SizedBox(
          width: double.infinity,
          height: 48,
          child: ElevatedButton(
            onPressed: _finishOnboarding,
            child: const Text('Go to Dashboard', style: TextStyle(fontSize: 16)),
          ),
        ),
      ],
    );
  }
}
