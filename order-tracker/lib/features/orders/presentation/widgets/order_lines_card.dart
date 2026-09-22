import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/format/app_formatters.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/features/orders/domain/entities/order_line.dart';
import 'package:order_tracker/features/orders/presentation/widgets/labeled_value.dart';

class OrderLinesCard extends StatelessWidget {
  const OrderLinesCard({required this.lines, required this.currency, super.key});

  final List<OrderLine> lines;
  final String currency;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      title: AppStrings.linesTitle(lines.length),
      icon: Icons.inventory_2_outlined,
      children: lines.isEmpty
          ? const [Text(AppStrings.noLines)]
          : [for (final line in lines) _OrderLineTile(line: line, currency: currency)],
    );
  }
}

class _OrderLineTile extends StatelessWidget {
  const _OrderLineTile({required this.line, required this.currency});

  final OrderLine line;
  final String currency;

  @override
  Widget build(BuildContext context) {
    final formatters = context.read<AppFormatters>();
    final lineTotal = line.lineTotal;
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(line.displayName),
      subtitle: Text(AppStrings.joinDetails(_details(formatters))),
      trailing: lineTotal == null
          ? null
          : Text(
              formatters.currency(lineTotal, currency),
              style: Theme.of(context).textTheme.titleSmall,
            ),
    );
  }

  List<String> _details(AppFormatters formatters) {
    String money(double amount) => formatters.currency(amount, currency);
    final discountRate = line.discountRate;
    final discount = line.discount;
    final taxRate = line.taxRate;
    final taxAmount = line.taxAmount;
    return [
      line.sku ?? line.productId,
      AppStrings.quantityTimesPrice(line.quantity, money(line.unitPrice)),
      if (discountRate != null && discount != null)
        AppStrings.discountDetail(formatters.percent(discountRate), money(discount)),
      if (taxRate != null && taxAmount != null)
        AppStrings.taxDetail(formatters.percent(taxRate), money(taxAmount)),
    ];
  }
}
