import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/core/widgets/content_frame.dart';
import 'package:order_tracker/features/orders/domain/usecases/search_order.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_bar.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_bloc.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_result_view.dart';

class OrderSearchPage extends StatelessWidget {
  const OrderSearchPage({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (context) => OrderSearchBloc(context.read<SearchOrder>()),
      child: const ContentFrame(
        child: Column(
          children: [
            Padding(
              padding: AppInsets.pageHeader,
              child: OrderSearchBar(),
            ),
            Expanded(child: OrderSearchResult(idle: OrderSearchIdleView())),
          ],
        ),
      ),
    );
  }
}
