import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

class ChecklistService {
  static const _prefix = 'order_prep_checklist_';

  static const List<String> defaultItems = [
    'Sabor seleccionado',
    'Tamaño correcto',
    'Hielo molido / granizado',
    'Complementos agregados',
    'Presentación y pitillo',
  ];

  static Future<Map<String, bool>> getChecklist(String orderId) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString('$_prefix$orderId');
      if (raw != null) {
        final decoded = jsonDecode(raw) as Map<String, dynamic>;
        final map = <String, bool>{};
        for (var item in defaultItems) {
          map[item] = decoded[item] == true;
        }
        return map;
      }
    } catch (_) {}

    return {for (var item in defaultItems) item: false};
  }

  static Future<void> saveChecklist(String orderId, Map<String, bool> checklist) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('$_prefix$orderId', jsonEncode(checklist));
    } catch (_) {}
  }
}
