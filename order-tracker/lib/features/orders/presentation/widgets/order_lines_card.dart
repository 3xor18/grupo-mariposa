import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/format/app_formatters.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/money/money.dart';
import 'package:order_tracker/features/orders/domain/entities/order_line.dart';
import 'package:order_tracker/features/orders/presentation/widgets/section_card.dart';

class OrderLinesCard extends StatelessWidget {
  const OrderLinesCard({required this.lines, super.key});

  final List<OrderLine> lines;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      title: AppStrings.linesTitle(lines.length),
      icon: Icons.inventory_2_outlined,
      children: lines.isEmpty
          ? const [Text(AppStrings.noLines)]
          : [for (final line in lines) _OrderLineTile(line: line)],
    );
  }
}

class _OrderLineTile extends StatelessWidget {
  const _OrderLineTile({required this.line});

  final OrderLine line;

  @override
  Widget build(BuildContext context) {
    final formatters = context.read<AppFormatters>();
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(line.displayName),
      subtitle: Text(AppStrings.joinDetails(_details(formatters))),
      trailing: switch (line.lineTotal) {
        final Money total => Text(
          formatters.money(total),
          style: Theme.of(context).textTheme.titleSmall,
        ),
        null => null,
      },
    );
  }

  List<String> _details(AppFormatters formatters) {
    return [
      line.sku ?? line.productId,
      AppStrings.quantityTimesPrice(line.quantity, formatters.money(line.unitPrice)),
      if ((line.discountRate, line.discount) case (final double rate, final Money amount))
        AppStrings.discountDetail(formatters.percent(rate), formatters.money(amount)),
      if ((line.taxRate, line.taxAmount) case (final double rate, final Money amount))
        AppStrings.taxDetail(formatters.percent(rate), formatters.money(amount)),
    ];
  }
}
