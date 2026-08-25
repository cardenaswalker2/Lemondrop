import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class SecureStorage {
  static const _storage = FlutterSecureStorage();

  static const _keyToken = 'auth_token';
  static const _keyUsername = 'auth_username';
  static const _keyName = 'auth_name';
  static const _keyRole = 'auth_role';

  static const _keyAiClientToken = 'lemon_ai_client_token';

  static Future<void> saveSession({
    required String token,
    required String username,
    required String name,
    required String role,
  }) async {
    await _storage.write(key: _keyToken, value: token);
    await _storage.write(key: _keyUsername, value: username);
    await _storage.write(key: _keyName, value: name);
    await _storage.write(key: _keyRole, value: role);
  }

  static Future<String?> getToken() async => await _storage.read(key: _keyToken);
  static Future<String?> getUsername() async => await _storage.read(key: _keyUsername);
  static Future<String?> getName() async => await _storage.read(key: _keyName);
  static Future<String?> getRole() async => await _storage.read(key: _keyRole);

  static Future<void> saveAiClientToken(String token) async =>
      await _storage.write(key: _keyAiClientToken, value: token);
  static Future<String?> getAiClientToken() async =>
      await _storage.read(key: _keyAiClientToken);
  static Future<void> clearAiClientToken() async =>
      await _storage.delete(key: _keyAiClientToken);

  static Future<void> clearSession() async {
    await _storage.delete(key: _keyToken);
    await _storage.delete(key: _keyUsername);
    await _storage.delete(key: _keyName);
    await _storage.delete(key: _keyRole);
  }
}
