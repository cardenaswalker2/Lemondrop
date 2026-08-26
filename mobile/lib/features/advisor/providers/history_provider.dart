import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/models/models.dart';
import '../../auth/providers/auth_provider.dart';

class HistoryState {
  final List<Order> orders;
  final bool isLoading;
  final bool isFetchingMore;
  final int page;
  final bool isLastPage;
  final String query;
  final String timeFilter; // "TODOS", "HOY", "AYER", "7_DIAS"
  final String statusFilter; // "ALL", "DELIVERED", "CANCELLED"
  final String? errorMessage;

  HistoryState({
    this.orders = const [],
    this.isLoading = false,
    this.isFetchingMore = false,
    this.page = 0,
    this.isLastPage = false,
    this.query = '',
    this.timeFilter = 'TODOS',
    this.statusFilter = 'ALL',
    this.errorMessage,
  });

  HistoryState copyWith({
    List<Order>? orders,
    bool? isLoading,
    bool? isFetchingMore,
    int? page,
    bool? isLastPage,
    String? query,
    String? timeFilter,
    String? statusFilter,
    String? errorMessage,
  }) {
    return HistoryState(
      orders: orders ?? this.orders,
      isLoading: isLoading ?? this.isLoading,
      isFetchingMore: isFetchingMore ?? this.isFetchingMore,
      page: page ?? this.page,
      isLastPage: isLastPage ?? this.isLastPage,
      query: query ?? this.query,
      timeFilter: timeFilter ?? this.timeFilter,
      statusFilter: statusFilter ?? this.statusFilter,
      errorMessage: errorMessage,
    );
  }
}

class HistoryNotifier extends StateNotifier<HistoryState> {
  final Ref _ref;

  HistoryNotifier(this._ref) : super(HistoryState(isLoading: true)) {
    fetchHistory();
  }

  Future<void> fetchHistory({bool isRefresh = false}) async {
    if (state.isLoading && !isRefresh) return;

    state = state.copyWith(isLoading: true, errorMessage: null);

    try {
      final client = _ref.read(apiClientProvider);
      final res = await client.dio.get('/api/mobile/orders/history', queryParameters: {
        'page': 0,
        'size': 20,
        'query': state.query,
        'filter': state.timeFilter,
        'status': state.statusFilter,
      });

      if (res.statusCode == 200) {
        final List raw = res.data['content'] as List? ?? [];
        final fetched = raw.map((e) => Order.fromJson(e)).toList();
        final isLast = res.data['isLast'] as bool? ?? (fetched.length < 20);

        state = state.copyWith(
          orders: fetched,
          page: 0,
          isLastPage: isLast,
          isLoading: false,
        );
      } else {
        state = state.copyWith(
          isLoading: false,
          errorMessage: 'No se pudo cargar el historial.',
        );
      }
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: 'Error de conexión al cargar historial.',
      );
    }
  }

  Future<void> fetchNextPage() async {
    if (state.isLoading || state.isFetchingMore || state.isLastPage) return;

    state = state.copyWith(isFetchingMore: true);

    try {
      final nextPage = state.page + 1;
      final client = _ref.read(apiClientProvider);
      final res = await client.dio.get('/api/mobile/orders/history', queryParameters: {
        'page': nextPage,
        'size': 20,
        'query': state.query,
        'filter': state.timeFilter,
        'status': state.statusFilter,
      });

      if (res.statusCode == 200) {
        final List raw = res.data['content'] as List? ?? [];
        final fetched = raw.map((e) => Order.fromJson(e)).toList();
        final isLast = res.data['isLast'] as bool? ?? (fetched.isEmpty);

        // Deduplicate orders
        final existingIds = {for (var o in state.orders) o.id};
        final newOrders = fetched.where((o) => !existingIds.contains(o.id)).toList();

        state = state.copyWith(
          orders: [...state.orders, ...newOrders],
          page: nextPage,
          isLastPage: isLast,
          isFetchingMore: false,
        );
      } else {
        state = state.copyWith(isFetchingMore: false);
      }
    } catch (_) {
      state = state.copyWith(isFetchingMore: false);
    }
  }

  void setSearchQuery(String query) {
    if (state.query == query.trim()) return;
    state = state.copyWith(query: query.trim());
    fetchHistory();
  }

  void setTimeFilter(String timeFilter) {
    if (state.timeFilter == timeFilter) return;
    state = state.copyWith(timeFilter: timeFilter);
    fetchHistory();
  }

  void setStatusFilter(String statusFilter) {
    if (state.statusFilter == statusFilter) return;
    state = state.copyWith(statusFilter: statusFilter);
    fetchHistory();
  }

  void prependCompletedOrder(Order order) {
    // Check if matches current filters
    if (state.statusFilter != 'ALL' && state.statusFilter != order.status.name.toUpperCase()) {
      return;
    }

    final current = state.orders.where((o) => o.id != order.id).toList();
    state = state.copyWith(orders: [order, ...current]);
  }
}

final historyProvider = StateNotifierProvider<HistoryNotifier, HistoryState>((ref) {
  return HistoryNotifier(ref);
});
