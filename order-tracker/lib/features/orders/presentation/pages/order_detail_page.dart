import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/features/orders/domain/usecases/search_order.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_bloc.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_event.dart';
import 'package:order_tracker/features/orders/presentation/search/order_search_result_view.dart';

class OrderDetailPage extends StatelessWidget {
  const OrderDetailPage({required this.orderId, super.key});

  static Route<void> route(String orderId) {
    return MaterialPageRoute<void>(builder: (_) => OrderDetailPage(orderId: orderId));
  }

  final String orderId;

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (context) =>
          OrderSearchBloc(context.read<SearchOrder>())..add(OrderSearchSubmitted(orderId)),
      child: Scaffold(
        appBar: AppBar(title: Text(orderId)),
        body: const SafeArea(child: OrderSearchResult(idle: SizedBox.shrink())),
      ),
    );
  }
}
