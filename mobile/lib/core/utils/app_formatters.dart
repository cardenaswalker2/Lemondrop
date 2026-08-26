import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

class AppFormatters {
  static final NumberFormat _decimalFormatter = NumberFormat('#,##0', 'es_CO');

  static const String defaultStoreWhatsApp = '573001234567';

  /// Format an amount into Colombian Pesos, e.g. $12.000
  static String formatCurrency(num? amount) {
    if (amount == null) return '\$0';
    return '\$${_decimalFormatter.format(amount.toInt())}';
  }

  /// Format date to readable string, e.g. 26/08/2026 11:30 AM
  static String formatDate(DateTime? date) {
    if (date == null) return '';
    return DateFormat('dd/MM/yyyy hh:mm a').format(date);
  }

  /// Opens WhatsApp with a pre-filled support message containing the order code
  static Future<bool> openOrderWhatsAppSupport(String orderCode, {String? storePhone}) async {
    final phone = (storePhone != null && storePhone.trim().isNotEmpty)
        ? storePhone.replaceAll(RegExp(r'\D'), '')
        : defaultStoreWhatsApp;

    final targetPhone = phone.length == 10 ? '57$phone' : phone;
    final message = 'Hola Lemon Drop 👋, tengo una consulta sobre mi pedido $orderCode.';
    final uri = Uri.parse('https://wa.me/$targetPhone?text=${Uri.encodeComponent(message)}');

    try {
      return await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {
      return false;
    }
  }
}
