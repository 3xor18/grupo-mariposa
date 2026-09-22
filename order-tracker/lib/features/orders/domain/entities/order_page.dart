import 'package:equatable/equatable.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';

final class OrderPage extends Equatable {
  const OrderPage({
    required this.items,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  final List<OrderSummary> items;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;

  bool get hasMore => page + 1 < totalPages;

  @override
  List<Object?> get props => [items, page, size, totalElements, totalPages];
}
