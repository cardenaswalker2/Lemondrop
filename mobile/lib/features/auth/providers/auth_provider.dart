import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/models/models.dart';
import '../../../core/network/api_client.dart';
import '../../../core/storage/secure_storage.dart';

// Provider for ApiClient
final apiClientProvider = Provider<ApiClient>((ref) {
  final client = ApiClient();
  client.onUnauthorized = () {
    ref.read(authProvider.notifier).logout();
  };
  return client;
});

// State class for Auth State
class AuthState {
  final bool isLoading;
  final User? user;
  final String? errorMessage;

  AuthState({
    this.isLoading = false,
    this.user,
    this.errorMessage,
  });

  AuthState copyWith({
    bool? isLoading,
    User? user,
    String? errorMessage,
  }) {
    return AuthState(
      isLoading: isLoading ?? this.isLoading,
      user: user ?? this.user,
      errorMessage: errorMessage,
    );
  }
}

// StateNotifier for Authentication
class AuthNotifier extends StateNotifier<AuthState> {
  final Ref _ref;

  AuthNotifier(this._ref) : super(AuthState(isLoading: true)) {
    _tryRestoreSession();
  }

  Future<void> _tryRestoreSession() async {
    final token = await SecureStorage.getToken();
    final username = await SecureStorage.getUsername();
    final name = await SecureStorage.getName();
    final role = await SecureStorage.getRole();

    if (token != null && username != null && name != null && role != null) {
      state = AuthState(
        user: User(id: '', name: name, username: username, role: role),
      );
    } else {
      state = AuthState(user: null);
    }
  }

  Future<bool> login(String username, String password) async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final client = _ref.read(apiClientProvider);
      final response = await client.dio.post('/api/mobile/auth/login', data: {
        'username': username,
        'password': password,
      });

      if (response.statusCode == 200) {
        final data = response.data as Map<String, dynamic>;
        final token = data['token'] as String;
        final userData = data['user'] as Map<String, dynamic>;
        final user = User.fromJson(userData);

        await SecureStorage.saveSession(
          token: token,
          username: user.username,
          name: user.name,
          role: user.role,
        );

        state = AuthState(user: user);
        return true;
      }
    } catch (e) {
      String msg = 'No se pudo conectar con el servidor.';
      try {
        final err = e as dynamic;
        if (err.response != null && err.response.data != null) {
          final rawData = err.response.data;
          if (rawData is Map && rawData.containsKey('message')) {
            msg = rawData['message'].toString();
          }
        }
      } catch (_) {}
      state = AuthState(user: null, errorMessage: msg);
    }
    return false;
  }

  Future<void> logout() async {
    state = state.copyWith(isLoading: true);
    await SecureStorage.clearSession();
    state = AuthState(user: null);
  }
}

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(ref);
});
