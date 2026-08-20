import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import '../storage/secure_storage.dart';
import '../storage/preferences.dart';

class ApiClient {
  late final Dio _dio;
  VoidCallback? onUnauthorized;

  ApiClient({this.onUnauthorized}) {
    _dio = Dio(BaseOptions(
      baseUrl: AppPreferences.baseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    ));

    // Request & Response Logging + Bearer token injector interceptor
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        // Update URL dynamically if preferences changed
        options.baseUrl = AppPreferences.baseUrl;

        final token = await SecureStorage.getToken();
        if (token != null) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        if (kDebugMode) {
          print('API REQUEST [${options.method}] => ${options.uri}');
        }
        return handler.next(options);
      },
      onResponse: (response, handler) {
        if (kDebugMode) {
          print('API RESPONSE [${response.statusCode}] <= ${response.requestOptions.uri}');
        }
        return handler.next(response);
      },
      onError: (DioException error, handler) async {
        if (kDebugMode) {
          print('API ERROR [${error.response?.statusCode}] <= ${error.requestOptions.uri}');
        }
        if (error.response?.statusCode == 401) {
          // Clear session and notify unauthorized event
          await SecureStorage.clearSession();
          if (onUnauthorized != null) {
            onUnauthorized!();
          }
        }
        return handler.next(error);
      },
    ));
  }

  Dio get dio => _dio;
}
