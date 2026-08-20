import 'package:shared_preferences/shared_preferences.dart';

class AppPreferences {
  static SharedPreferences? _prefs;

  static const _keySoundEnabled = 'sound_enabled';
  static const _keyVibrationEnabled = 'vibration_enabled';
  static const _keyKeepScreenOn = 'keep_screen_on';
  static const _keyBaseUrl = 'api_base_url';

  static Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  static bool get soundEnabled => _prefs?.getBool(_keySoundEnabled) ?? true;
  static set soundEnabled(bool value) => _prefs?.setBool(_keySoundEnabled, value);

  static bool get vibrationEnabled => _prefs?.getBool(_keyVibrationEnabled) ?? true;
  static set vibrationEnabled(bool value) => _prefs?.setBool(_keyVibrationEnabled, value);

  static bool get keepScreenOn => _prefs?.getBool(_keyKeepScreenOn) ?? false;
  static set keepScreenOn(bool value) => _prefs?.setBool(_keyKeepScreenOn, value);

  // Fallback default dev backend URL
  static String get baseUrl => _prefs?.getString(_keyBaseUrl) ?? 'https://lemondrop-b7su.onrender.com';
  static set baseUrl(String value) => _prefs?.setString(_keyBaseUrl, value);
}
