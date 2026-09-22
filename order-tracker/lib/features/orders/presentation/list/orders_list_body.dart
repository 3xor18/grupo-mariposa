import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/core/widgets/failure_messages.dart';
import 'package:order_tracker/core/widgets/failure_view.dart';
import 'package:order_tracker/core/widgets/loading_view.dart';
import 'package:order_tracker/core/widgets/state_message.dart';
import 'package:order_tracker/features/orders/presentation/list/order_summary_tile.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_bloc.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_event.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_state.dart';
import 'package:order_tracker/features/orders/presentation/orders_keys.dart';

class OrdersListBody extends StatelessWidget {
  const OrdersListBody({required this.onSelected, this.selectedOrderId, super.key});

  final ValueChanged<String> onSelected;
  final String? selectedOrderId;

  @override
  Widget build(BuildContext context) {
    return BlocConsumer<OrdersListBloc, OrdersListState>(
      listenWhen: (previous, current) =>
          current.refreshFailure != null && previous.refreshFailure != current.refreshFailure,
      listener: _showRefreshFailure,
      builder: (context, state) => switch (state.status) {
        OrdersListStatus.initial || OrdersListStatus.loading => const LoadingView(
          key: OrdersKeys.ordersLoading,
          label: AppStrings.loadingOrders,
        ),
        OrdersListStatus.failure => FailureView(
          key: OrdersKeys.ordersFailure,
          failure: state.failure!,
          onRetry: () => context.read<OrdersListBloc>().add(const OrdersListRequested()),
        ),
        OrdersListStatus.success => _RefreshableList(
          state: state,
          selectedOrderId: selectedOrderId,
          onSelected: onSelected,
        ),
      },
    );
  }

  void _showRefreshFailure(BuildContext context, OrdersListState state) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          AppStrings.joinDetails([
            AppStrings.refreshFailed,
            FailureMessages.of(state.refreshFailure!),
          ]),
        ),
      ),
    );
  }
}

class _RefreshableList extends StatelessWidget {
  const _RefreshableList({
    required this.state,
    required this.selectedOrderId,
    required this.onSelected,
  });

  final OrdersListState state;
  final String? selectedOrderId;
  final ValueChanged<String> onSelected;

  Future<void> _refresh(OrdersListBloc bloc) async {
    bloc.add(const OrdersListRefreshed());
    await bloc.stream.firstWhere((state) => !state.isRefreshing);
  }

  @override
  Widget build(BuildContext context) {
    final items = state.items;
    return RefreshIndicator(
      onRefresh: () => _refresh(context.read<OrdersListBloc>()),
      child: ListView.builder(
        key: OrdersKeys.ordersList,
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: items.length + 1,
        itemBuilder: (context, index) {
          if (index == items.length) {
            return _ListFooter(state: state);
          }
          final summary = items[index];
          return OrderSummaryTile(
            summary: summary,
            selected: summary.orderId == selectedOrderId,
            onTap: () => onSelected(summary.orderId),
          );
        },
      ),
    );
  }
}

class _ListFooter extends StatelessWidget {
  const _ListFooter({required this.state});

  final OrdersListState state;

  @override
  Widget build(BuildContext context) {
    final bloc = context.read<OrdersListBloc>();
    final nextPageFailure = state.nextPageFailure;
    if (state.items.isEmpty) {
      return const StateMessage(
        key: OrdersKeys.ordersEmpty,
        icon: Icons.inbox_outlined,
        title: AppStrings.emptyListTitle,
        message: AppStrings.emptyListMessage,
      );
    }
    final content = switch (state) {
      OrdersListState(isLoadingMore: true) => const CircularProgressIndicator(
        key: OrdersKeys.loadMoreProgress,
      ),
      OrdersListState(nextPageFailure: != null) => _NextPageFailure(
        message: FailureMessages.of(nextPageFailure!),
        onRetry: () => bloc.add(const OrdersListNextPageRequested()),
      ),
      OrdersListState(hasMore: true) => OutlinedButton.icon(
        key: OrdersKeys.loadMoreButton,
        onPressed: () => bloc.add(const OrdersListNextPageRequested()),
        icon: const Icon(Icons.expand_more),
        label: const Text(AppStrings.loadMore),
      ),
      _ => null,
    };
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.md),
      child: Center(child: content),
    );
  }
}

class _NextPageFailure extends StatelessWidget {
  const _NextPageFailure({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Column(
      spacing: AppSpacing.sm,
      children: [
        Text(AppStrings.joinDetails([AppStrings.nextPageFailed, message])),
        TextButton.icon(
          key: OrdersKeys.nextPageRetryButton,
          onPressed: onRetry,
          icon: const Icon(Icons.refresh),
          label: const Text(AppStrings.retry),
        ),
      ],
    );
  }
}
