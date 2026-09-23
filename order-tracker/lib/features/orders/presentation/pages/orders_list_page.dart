import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/core/widgets/content_frame.dart';
import 'package:order_tracker/core/widgets/state_message.dart';
import 'package:order_tracker/features/orders/domain/usecases/list_orders.dart';
import 'package:order_tracker/features/orders/domain/usecases/search_order.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_filter_bar.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_bloc.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_body.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_event.dart';
import 'package:order_tracker/features/orders/presentation/orders_keys.dart';
import 'package:order_tracker/features/orders/presentation/pages/order_detail_page.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_bloc.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_event.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_result_view.dart';

class OrdersListPage extends StatelessWidget {
  const OrdersListPage({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider(
          create: (context) =>
              OrdersListBloc(context.read<ListOrders>())..add(const OrdersListRequested()),
        ),
        BlocProvider(create: (context) => OrderSearchBloc(context.read<SearchOrder>())),
      ],
      child: const ContentFrame(child: _OrdersListLayout()),
    );
  }
}

class _OrdersListLayout extends StatelessWidget {
  const _OrdersListLayout();

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) => constraints.maxWidth >= AppBreakpoints.expanded
          ? const _ListDetailLayout()
          : _ListPane(
              onSelected: (orderId) => Navigator.of(context).push(OrderDetailPage.route(orderId)),
            ),
    );
  }
}

class _ListDetailLayout extends StatelessWidget {
  const _ListDetailLayout();

  @override
  Widget build(BuildContext context) {
    final selectedOrderId = context.select<OrderSearchBloc, String?>(
      (bloc) => bloc.state.orderId,
    );
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          width: AppSizes.listPaneWidth,
          child: _ListPane(
            selectedOrderId: selectedOrderId,
            onSelected: (orderId) =>
                context.read<OrderSearchBloc>().add(OrderSearchSubmitted(orderId)),
          ),
        ),
        const VerticalDivider(width: AppSizes.dividerThickness),
        const Expanded(
          child: KeyedSubtree(
            key: OrdersKeys.detailPane,
            child: OrderSearchResult(idle: _DetailPlaceholder()),
          ),
        ),
      ],
    );
  }
}

class _ListPane extends StatelessWidget {
  const _ListPane({required this.onSelected, this.selectedOrderId});

  final ValueChanged<String> onSelected;
  final String? selectedOrderId;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: AppInsets.pageHeader,
          child: Semantics(
            header: true,
            child: Text(
              AppStrings.recentOrders,
              style: Theme.of(context).textTheme.titleLarge,
            ),
          ),
        ),
        const OrdersFilterBar(),
        Expanded(
          child: OrdersListBody(onSelected: onSelected, selectedOrderId: selectedOrderId),
        ),
      ],
    );
  }
}

class _DetailPlaceholder extends StatelessWidget {
  const _DetailPlaceholder();

  @override
  Widget build(BuildContext context) {
    return const StateMessage(
      key: OrdersKeys.detailPlaceholder,
      icon: Icons.touch_app_outlined,
      title: AppStrings.selectOrderTitle,
      message: AppStrings.selectOrderMessage,
    );
  }
}
