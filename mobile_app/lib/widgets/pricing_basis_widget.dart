import 'package:flutter/material.dart';
import '../models/models.dart';
import '../utils/currency_formatter.dart';

const Map<String, String> defaultCatalogueLabels = {
  'hatchback_compact': 'Compact hatchback',
  'sedan_entry': 'Entry sedan',
  'suv_compact': 'Compact SUV',
  'sedan_mid': 'Mid-size sedan',
  'suv_premium': 'Premium SUV',
  'suv_luxury': 'Luxury SUV',
};

class PricingBasisSection extends StatelessWidget {
  final PricingBasis? pricingBasis;
  final List<VehicleSegment>? catalogue;

  const PricingBasisSection({
    super.key,
    required this.pricingBasis,
    this.catalogue,
  });

  @override
  Widget build(BuildContext context) {
    final basis = pricingBasis;
    if (basis == null) return const SizedBox.shrink();

    String sourceLabel;
    switch (basis.source) {
      case 'registered':
        sourceLabel = 'Your registered vehicle';
        break;
      case 'demo_profile':
        sourceLabel = 'Demo vehicle profile';
        break;
      case 'generic_default':
        sourceLabel = 'Standard profile — no vehicle details on file';
        break;
      default:
        sourceLabel = basis.source ?? 'Standard profile';
    }

    String? segmentLabel;
    if (basis.vehicleSegmentId != null && basis.vehicleSegmentId!.isNotEmpty) {
      final found = catalogue?.cast<VehicleSegment?>().firstWhere(
            (s) => s?.id == basis.vehicleSegmentId,
            orElse: () => null,
          );
      segmentLabel = found?.label ??
          defaultCatalogueLabels[basis.vehicleSegmentId] ??
          basis.vehicleSegmentId!;
    }

    return Column(
      key: const Key('how_we_priced_your_vehicle_section'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const SizedBox(height: 12),
        const Divider(),
        const SizedBox(height: 8),
        Text(
          'How we priced your vehicle',
          style: Theme.of(context).textTheme.titleSmall?.copyWith(
                fontWeight: FontWeight.bold,
                fontSize: 15,
              ) ??
              const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
        ),
        const SizedBox(height: 8),
        _buildRow('Profile source', sourceLabel),
        if (segmentLabel != null) _buildRow('Vehicle segment', segmentLabel),
        if (basis.vehicleAgeYears != null)
          _buildRow('Vehicle age', '${basis.vehicleAgeYears} ${basis.vehicleAgeYears == 1 ? 'year' : 'years'}'),
        if (basis.exShowroomPriceInr != null)
          _buildRow('Indicative ex-showroom', CurrencyFormatter.formatWhole(basis.exShowroomPriceInr)),
        if (basis.depreciationFactor != null)
          _buildRow('Depreciation factor', '${(basis.depreciationFactor! * 100).toStringAsFixed(0)}%'),
        if (basis.idv != null)
          _buildRow('IDV', CurrencyFormatter.format(basis.idv)),
        if (basis.ncbPct != null)
          _buildRow('NCB', '${basis.ncbPct!.toStringAsFixed(1)}%'),
      ],
    );
  }

  Widget _buildRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: const TextStyle(fontSize: 13, color: Colors.black54),
          ),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.end,
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}
