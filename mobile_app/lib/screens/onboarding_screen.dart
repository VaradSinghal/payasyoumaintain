import 'package:flutter/material.dart';

class OnboardingScreen extends StatelessWidget {
  const OnboardingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Welcome')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text('Welcome to Pay As You Maintain!'),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () {
                // To be implemented: actual login/registration flow
              },
              child: const Text('Get Started'),
            )
          ],
        ),
      ),
    );
  }
}
