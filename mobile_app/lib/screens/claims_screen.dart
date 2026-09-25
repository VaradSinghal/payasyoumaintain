import 'package:flutter/material.dart';

class ClaimsScreen extends StatelessWidget {
  const ClaimsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Claims')),
      body: const Center(
        child: Text('Claims Content Here'),
      ),
    );
  }
}
