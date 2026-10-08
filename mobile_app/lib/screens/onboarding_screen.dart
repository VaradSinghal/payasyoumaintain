import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/api_providers.dart';
import '../providers/auth_provider.dart';
import '../widgets/shared_widgets.dart';
import '../models/models.dart';

enum OnboardingStep { register, consent, quote }

class OnboardingScreen extends ConsumerStatefulWidget {
  const OnboardingScreen({super.key});

  @override
  ConsumerState<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends ConsumerState<OnboardingScreen> {
  OnboardingStep _currentStep = OnboardingStep.register;
  bool _isLoading = false;
  String? _error;

  String? _vehicleId;
  String? _policyId;
  QuoteResponse? _quote;

  bool _usageTracking = true;
  bool _maintenanceTracking = true;

  Future<void> _register() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final reg = await ref.read(identityApiProvider).registerVehicle({
        'make': 'Generic',
        'model': 'Vehicle',
        'year': 2023,
      });
      setState(() {
        _vehicleId = reg.vehicleId;
        _policyId = reg.policyId;
        _currentStep = OnboardingStep.consent;
      });
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _submitConsent() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      await ref.read(identityApiProvider).updateConsent(_vehicleId!, _usageTracking, _maintenanceTracking);
      final quote = await ref.read(pricingApiProvider).quote(_vehicleId!);
      setState(() {
        _quote = quote;
        _currentStep = OnboardingStep.quote;
      });
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _finishOnboarding() async {
    await ref.read(authProvider.notifier).login(_vehicleId!, _policyId!);
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(body: AppLoading(message: 'Processing...'));
    }

    return Scaffold(
      appBar: AppBar(title: const Text('Onboarding')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: _buildCurrentStep(),
        ),
      ),
    );
  }

  Widget _buildCurrentStep() {
    switch (_currentStep) {
      case OnboardingStep.register:
        return _buildRegister();
      case OnboardingStep.consent:
        return _buildConsent();
      case OnboardingStep.quote:
        return _buildQuote();
    }
  }

  Widget _buildRegister() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const Text(
          'Welcome to Pay As You Maintain!',
          style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 16),
        const Text(
          'Register your vehicle to start saving based on how you drive and maintain your car.',
          textAlign: TextAlign.center,
        ),
        if (_error != null) ...[
          const SizedBox(height: 16),
          Text(_error!, style: const TextStyle(color: Colors.red)),
        ],
        const SizedBox(height: 32),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: _register,
            child: const Text('Register Vehicle'),
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
          'To offer dynamic pricing, we need your consent to collect and use telematics and diagnostic data. Opting out means you will receive our standard pricing.',
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
          child: ElevatedButton(
            onPressed: _submitConsent,
            child: const Text('Continue to Quote'),
          ),
        ),
      ],
    );
  }

  Widget _buildQuote() {
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
            '\$${_quote!.finalPremium.toStringAsFixed(2)} / month',
            style: const TextStyle(fontSize: 48, fontWeight: FontWeight.bold, color: Colors.green),
          ),
          const SizedBox(height: 16),
          Text(
            _quote!.methodologyNote ?? 'Standard pricing based on your data sharing preferences.',
            textAlign: TextAlign.center,
            style: const TextStyle(color: Colors.grey),
          ),
        ],
        const Spacer(),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: _finishOnboarding,
            child: const Text('Go to Dashboard'),
          ),
        ),
      ],
    );
  }
}
