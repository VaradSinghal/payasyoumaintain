class ApiException implements Exception {
  final int? statusCode;
  final String message;
  final String? error;
  final String? timestamp;

  ApiException(this.statusCode, this.message, {this.error, this.timestamp});

  factory ApiException.fromJson(int? statusCode, Map<String, dynamic> json) {
    final msg = json['message']?.toString() ?? json['error']?.toString() ?? 'Unknown error';
    return ApiException(
      statusCode,
      msg,
      error: json['error']?.toString(),
      timestamp: json['timestamp']?.toString(),
    );
  }

  @override
  String toString() {
    if (statusCode != null && statusCode! >= 400) {
      return '$message (HTTP $statusCode)';
    }
    return message;
  }
}
