import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:audioplayers/audioplayers.dart';
import '../../../core/models/models.dart';
import '../../../core/storage/preferences.dart';
import '../../auth/providers/auth_provider.dart';
import 'history_provider.dart';

export 'history_provider.dart';

// Live Ticker Provider: emits current epoch milliseconds every second for seamless live timers
final liveSecondsProvider = StreamProvider<int>((ref) {
  return Stream.periodic(const Duration(seconds: 1), (_) => DateTime.now().millisecondsSinceEpoch);
});

// Catalog Provider
final catalogProvider = FutureProvider<Map<String, List<dynamic>>>((ref) async {
  final client = ref.read(apiClientProvider);
  final res = await client.dio.get('/api/mobile/catalog');
  if (res.statusCode == 200) {
    final data = res.data as Map<String, dynamic>;
    final products = (data['products'] as List).map((e) => Product.fromJson(e)).toList();
    final flavors = (data['flavors'] as List).map((e) => Flavor.fromJson(e)).toList();
    final addons = (data['addons'] as List).map((e) => Addon.fromJson(e)).toList();
    return {
      'products': products,
      'flavors': flavors,
      'addons': addons,
    };
  }
  throw Exception('No se pudo cargar el catálogo.');
});

// Order Status History Provider
final orderStatusHistoryProvider = FutureProvider.family<List<OrderStatusHistoryEntry>, String>((ref, orderId) async {
  final client = ref.read(apiClientProvider);
  final res = await client.dio.get('/api/mobile/orders/$orderId/status-history');
  if (res.statusCode == 200) {
    final List raw = res.data as List? ?? [];
    return raw.map((e) => OrderStatusHistoryEntry.fromJson(e)).toList();
  }
  return [];
});

// Order Change History Provider
final orderChangeHistoryProvider = FutureProvider.family<List<OrderChangeHistoryEntry>, String>((ref, orderId) async {
  final client = ref.read(apiClientProvider);
  final res = await client.dio.get('/api/mobile/orders/$orderId/change-history');
  if (res.statusCode == 200) {
    final List raw = res.data as List? ?? [];
    return raw.map((e) => OrderChangeHistoryEntry.fromJson(e)).toList();
  }
  return [];
});

// Stats Provider
final statsProvider = StateNotifierProvider<StatsNotifier, AsyncValue<Stats>>((ref) {
  return StatsNotifier(ref);
});

class StatsNotifier extends StateNotifier<AsyncValue<Stats>> {
  final Ref _ref;
  StatsNotifier(this._ref) : super(const AsyncValue.loading()) {
    fetchStats();
  }

  Future<void> fetchStats() async {
    try {
      final client = _ref.read(apiClientProvider);
      final res = await client.dio.get('/api/mobile/stats');
      if (res.statusCode == 200) {
        state = AsyncValue.data(Stats.fromJson(res.data));
      }
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
}

// Active Orders State (List of Orders)
final activeOrdersProvider = StateNotifierProvider<ActiveOrdersNotifier, AsyncValue<List<Order>>>((ref) {
  return ActiveOrdersNotifier(ref);
});

class ActiveOrdersNotifier extends StateNotifier<AsyncValue<List<Order>>> {
  final Ref _ref;
  Timer? _pollingTimer;
  DateTime _lastUpdateTime = DateTime.now().subtract(const Duration(days: 1));
  final AudioPlayer _audioPlayer = AudioPlayer();

  ActiveOrdersNotifier(this._ref) : super(const AsyncValue.loading()) {
    fetchActiveOrders();
    _startPolling();
  }

  Future<void> fetchActiveOrders() async {
    try {
      final client = _ref.read(apiClientProvider);
      final res = await client.dio.get('/api/mobile/orders');
      if (res.statusCode == 200) {
        final List raw = res.data['content'] as List? ?? [];
        final orders = raw.map((e) => Order.fromJson(e)).toList();
        state = AsyncValue.data(orders);
        _lastUpdateTime = DateTime.now();
      }
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  void _startPolling() {
    _pollingTimer?.cancel();
    _pollingTimer = Timer.periodic(const Duration(seconds: 5), (timer) async {
      if (_ref.read(authProvider).user == null) return;

      try {
        final client = _ref.read(apiClientProvider);
        final timestamp = _lastUpdateTime.toIso8601String().split('.').first;
        final res = await client.dio.get('/api/mobile/orders/updates', queryParameters: {
          'since': timestamp,
        });

        if (res.statusCode == 200) {
          final List updatesRaw = res.data as List? ?? [];
          if (updatesRaw.isNotEmpty) {
            final updates = updatesRaw.map((e) => Order.fromJson(e)).toList();
            _lastUpdateTime = DateTime.now();

            final currentList = state.value ?? [];
            final updatedMap = {for (var o in currentList) o.id: o};

            bool hasNewReceived = false;
            bool historyChanged = false;

            for (var u in updates) {
              if (u.status == OrderStatus.delivered || u.status == OrderStatus.cancelled) {
                updatedMap.remove(u.id);
                // Prepend completed order directly to history state
                _ref.read(historyProvider.notifier).prependCompletedOrder(u);
                historyChanged = true;
              } else {
                if (u.status == OrderStatus.received && !updatedMap.containsKey(u.id)) {
                  hasNewReceived = true;
                }
                updatedMap[u.id] = u;
              }
            }

            state = AsyncValue.data(updatedMap.values.toList()
              ..sort((a, b) => b.createdAt.compareTo(a.createdAt)));

            if (hasNewReceived) {
              _triggerAlert();
            }

            if (historyChanged) {
              _ref.read(historyProvider.notifier).fetchHistory(isRefresh: true);
            }

            _ref.read(statsProvider.notifier).fetchStats();
          }
        }
      } catch (_) {}
    });
  }

  static const _channel = MethodChannel('com.lemondrop.lemondrop_mobile/notification');

  Future<void> _triggerAlert() async {
    try {
      if (AppPreferences.soundEnabled || AppPreferences.vibrationEnabled) {
        await _channel.invokeMethod('playNotificationSoundAndVibrate', {
          'sound': AppPreferences.soundEnabled,
          'vibrate': AppPreferences.vibrationEnabled,
        });
        return;
      }
    } catch (_) {}

    if (AppPreferences.vibrationEnabled) {
      HapticFeedback.vibrate();
    }
    if (AppPreferences.soundEnabled) {
      try {
        await _audioPlayer.play(UrlSource('https://assets.mixkit.co/active_storage/sfx/2869/2869-600.wav'));
      } catch (_) {}
    }
  }

  Future<bool> updateStatus(String orderId, OrderStatus newStatus, {String notes = ''}) async {
    try {
      final client = _ref.read(apiClientProvider);
      final res = await client.dio.post('/api/mobile/orders/$orderId/status', data: {
        'status': newStatus.toJson(),
        'notes': notes,
      });

      if (res.statusCode == 200) {
        final updatedOrder = Order.fromJson(res.data);
        final currentList = state.value ?? [];

        if (newStatus == OrderStatus.delivered || newStatus == OrderStatus.cancelled) {
          state = AsyncValue.data(currentList.where((o) => o.id != orderId).toList());
          // Sync directly to history provider
          _ref.read(historyProvider.notifier).prependCompletedOrder(updatedOrder);
          _ref.read(historyProvider.notifier).fetchHistory(isRefresh: true);
        } else {
          state = AsyncValue.data(currentList.map((o) => o.id == orderId ? updatedOrder : o).toList());
        }

        // Invalidate histories for this order if open
        _ref.invalidate(orderStatusHistoryProvider(orderId));
        _ref.invalidate(orderChangeHistoryProvider(orderId));

        // Refresh stats
        _ref.read(statsProvider.notifier).fetchStats();
        return true;
      }
    } catch (_) {}
    return false;
  }

  Future<String?> claimOrder(String orderId) async {
    try {
      final client = _ref.read(apiClientProvider);
      final res = await client.dio.post('/api/mobile/orders/$orderId/claim');

      if (res.statusCode == 200) {
        final updatedOrder = Order.fromJson(res.data);
        final currentList = state.value ?? [];
        state = AsyncValue.data(currentList.map((o) => o.id == orderId ? updatedOrder : o).toList());

        _ref.invalidate(orderStatusHistoryProvider(orderId));
        _ref.invalidate(orderChangeHistoryProvider(orderId));
        _ref.read(statsProvider.notifier).fetchStats();
        return null; // success
      }
    } catch (e) {
      try {
        final err = e as dynamic;
        if (err.response != null && err.response.data != null && err.response.data['message'] != null) {
          return err.response.data['message'].toString();
        }
      } catch (_) {}
      return 'No se pudo tomar el pedido.';
    }
    return 'No se pudo tomar el pedido.';
  }

  Future<bool> editOrder(String orderId, List<Map<String, dynamic>> items, String reason) async {
    try {
      final client = _ref.read(apiClientProvider);
      final res = await client.dio.post('/api/mobile/orders/$orderId/edit', data: {
        'items': items,
        'reason': reason,
      });

      if (res.statusCode == 200) {
        final updatedOrder = Order.fromJson(res.data);
        final currentList = state.value ?? [];
        state = AsyncValue.data(currentList.map((o) => o.id == orderId ? updatedOrder : o).toList());

        _ref.invalidate(orderStatusHistoryProvider(orderId));
        _ref.invalidate(orderChangeHistoryProvider(orderId));
        _ref.read(statsProvider.notifier).fetchStats();
        return true;
      }
    } catch (_) {}
    return false;
  }

  @override
  void dispose() {
    _pollingTimer?.cancel();
    _audioPlayer.dispose();
    super.dispose();
  }
}
