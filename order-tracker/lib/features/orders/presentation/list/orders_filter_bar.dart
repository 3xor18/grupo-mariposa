import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/markets/market_catalog.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/features/orders/domain/entities/market_code.dart';
import 'package:order_tracker/features/orders/domain/entities/orders_filter.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_bloc.dart';
import 'package:order_tracker/features/orders/presentation/list/orders_list_event.dart';
import 'package:order_tracker/features/orders/presentation/orders_keys.dart';
import 'package:order_tracker/features/orders/presentation/widgets/order_labels.dart';

class OrdersFilterBar extends StatelessWidget {
  const OrdersFilterBar({super.key});

  @override
  Widget build(BuildContext context) {
    final filter = context.select<OrdersListBloc, OrdersFilter>((bloc) => bloc.state.filter);
    final bloc = context.read<OrdersListBloc>();
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md, vertical: AppSpacing.sm),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        spacing: AppSpacing.sm,
        children: [
          _ChipGroup(
            label: AppStrings.filterStatus,
            chips: [
              for (final status in OrderStatusPresentation.filterable)
                FilterChip(
                  key: OrdersKeys.statusFilter(status),
                  label: Text(status.label),
                  selected: filter.status == status,
                  onSelected: (_) => bloc.add(OrdersListStatusToggled(status)),
                ),
            ],
          ),
          _ChipGroup(
            label: AppStrings.market,
            chips: [
              for (final market in context.read<MarketCatalog>().markets.map(_codeOf))
                FilterChip(
                  key: OrdersKeys.marketFilter(market),
                  label: Text(context.marketName(market)),
                  selected: filter.market == market,
                  onSelected: (_) => bloc.add(OrdersListMarketToggled(market)),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

MarketCode _codeOf(MarketDefinition market) => MarketCode(market.code);

class _ChipGroup extends StatelessWidget {
  const _ChipGroup({required this.label, required this.chips});

  final String label;
  final List<Widget> chips;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      label: label,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          spacing: AppSpacing.sm,
          children: [
            Text(label, style: Theme.of(context).textTheme.labelLarge),
            ...chips,
          ],
        ),
      ),
    );
  }
}
