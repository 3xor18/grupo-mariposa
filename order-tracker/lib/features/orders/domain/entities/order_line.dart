import 'package:equatable/equatable.dart';

final class OrderLine extends Equatable {
  const OrderLine({
    required this.productId,
    required this.quantity,
    required this.unitPrice,
    this.name,
    this.sku,
    this.taxCategory,
    this.grossSubtotal,
    this.discountRate,
    this.discount,
    this.netSubtotal,
    this.taxRate,
    this.taxAmount,
    this.lineTotal,
  });

  final String productId;
  final String? name;
  final String? sku;
  final String? taxCategory;
  final int quantity;
  final double unitPrice;
  final double? grossSubtotal;
  final double? discountRate;
  final double? discount;
  final double? netSubtotal;
  final double? taxRate;
  final double? taxAmount;
  final double? lineTotal;

  String get displayName => name ?? productId;

  @override
  List<Object?> get props => [
    productId,
    name,
    sku,
    taxCategory,
    quantity,
    unitPrice,
    grossSubtotal,
    discountRate,
    discount,
    netSubtotal,
    taxRate,
    taxAmount,
    lineTotal,
  ];
}
