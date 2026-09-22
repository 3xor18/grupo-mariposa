import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/widgets/failure_view.dart';
import 'package:order_tracker/core/widgets/loading_view.dart';
import 'package:order_tracker/core/widgets/state_message.dart';
import 'package:order_tracker/features/orders/presentation/orders_keys.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_bloc.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_event.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_state.dart';
import 'package:order_tracker/features/orders/presentation/widgets/order_detail_view.dart';

class OrderSearchResult extends StatelessWidget {
  const OrderSearchResult({required this.idle, super.key});

  final Widget idle;

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<OrderSearchBloc, OrderSearchState>(
      builder: (context, state) => OrderSearchResultView(
        state: state,
        idle: idle,
        onRetry: () => context.read<OrderSearchBloc>().add(const OrderSearchRetried()),
      ),
    );
  }
}

class OrderSearchResultView extends StatelessWidget {
  const OrderSearchResultView({
    required this.state,
    required this.idle,
    required this.onRetry,
    super.key,
  });

  final OrderSearchState state;
  final Widget idle;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return switch (state) {
      OrderSearchIdle() => idle,
      OrderSearchLoading() => const LoadingView(
        key: OrdersKeys.loadingView,
        label: AppStrings.loadingOrder,
      ),
      OrderSearchNotFound(:final orderId) => StateMessage(
        key: OrdersKeys.notFoundView,
        icon: Icons.search_off,
        title: AppStrings.notFoundTitle,
        message: AppStrings.notFoundMessage(orderId),
      ),
      OrderSearchFailure(:final failure) => FailureView(
        key: OrdersKeys.failureView,
        failure: failure,
        onRetry: onRetry,
      ),
      OrderSearchSuccess(:final order) => OrderDetailView(order: order),
    };
  }
}

class OrderSearchIdleView extends StatelessWidget {
  const OrderSearchIdleView({super.key});

  @override
  Widget build(BuildContext context) {
    return const StateMessage(
      key: OrdersKeys.idleView,
      icon: Icons.manage_search,
      title: AppStrings.searchIdleTitle,
      message: AppStrings.searchIdleMessage,
    );
  }
}
