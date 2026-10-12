class CurrencyFormatter {
  static String format(num? amount, {String? currency = 'INR'}) {
    if (amount == null) return 'N/A';
    final symbol = (currency == 'INR' || currency == null)
        ? '₹'
        : (currency == 'USD' ? '\$' : '$currency ');

    final parts = amount.toDouble().toStringAsFixed(2).split('.');
    final integerPart = parts[0];
    final decimalPart = parts[1];

    if (currency == 'INR' || currency == null) {
      if (integerPart.length <= 3) {
        return '$symbol$integerPart.$decimalPart';
      }
      final lastThree = integerPart.substring(integerPart.length - 3);
      final rest = integerPart.substring(0, integerPart.length - 3);
      final buffer = StringBuffer();
      for (int i = 0; i < rest.length; i++) {
        if (i > 0 && (rest.length - i) % 2 == 0) {
          buffer.write(',');
        }
        buffer.write(rest[i]);
      }
      return '$symbol${buffer.toString()},$lastThree.$decimalPart';
    } else {
      final reg = RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))');
      final formatted = integerPart.replaceAllMapped(reg, (Match m) => '${m[1]},');
      return '$symbol$formatted.$decimalPart';
    }
  }
}
