import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/features/orders/domain/entities/order_id.dart';
import 'package:order_tracker/features/orders/presentation/orders_keys.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_bloc.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_event.dart';

class OrderSearchBar extends StatefulWidget {
  const OrderSearchBar({super.key});

  @override
  State<OrderSearchBar> createState() => _OrderSearchBarState();
}

class _OrderSearchBarState extends State<OrderSearchBar> {
  final _formKey = GlobalKey<FormState>();
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    if (_formKey.currentState!.validate()) {
      context.read<OrderSearchBloc>().add(OrderSearchSubmitted(_controller.text));
    }
  }

  void _clear() {
    _controller.clear();
    _formKey.currentState!.reset();
    context.read<OrderSearchBloc>().add(const OrderSearchCleared());
  }

  static String? _validate(String? value) {
    return switch (OrderId.validate(value ?? '')) {
      OrderIdError.empty => AppStrings.orderIdRequired,
      OrderIdError.tooLong => AppStrings.orderIdTooLong(OrderId.maxLength),
      null => null,
    };
  }

  @override
  Widget build(BuildContext context) {
    return Form(
      key: _formKey,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: TextFormField(
              key: OrdersKeys.searchField,
              controller: _controller,
              validator: _validate,
              textInputAction: TextInputAction.search,
              textCapitalization: TextCapitalization.characters,
              autocorrect: false,
              onFieldSubmitted: (_) => _submit(),
              decoration: InputDecoration(
                labelText: AppStrings.orderIdLabel,
                hintText: AppStrings.orderIdHint,
                prefixIcon: const Icon(Icons.search),
                suffixIcon: IconButton(
                  key: OrdersKeys.clearButton,
                  tooltip: AppStrings.clearSearch,
                  onPressed: _clear,
                  icon: const Icon(Icons.close),
                ),
              ),
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          Padding(
            padding: const EdgeInsets.only(top: AppSpacing.xs),
            child: FilledButton(
              key: OrdersKeys.searchButton,
              onPressed: _submit,
              child: const Text(AppStrings.searchButton),
            ),
          ),
        ],
      ),
    );
  }
}
