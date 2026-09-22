export const copy = {
  login: 'Iniciar sesión',
  logout: 'Cerrar sesión',
  orderIdField: 'ID del pedido',
  approvedStatus: 'Estado: Aprobado',
  notFound: 'Pedido no encontrado',
  ordersTab: /^Pedidos\b/,
  recentOrders: 'Pedidos recientes',
} as const;

export const seed = {
  approvedOrderId: 'ORD-MX-000147',
  approvedGrandTotal: /^Total del pedido: \$2,100\.11$/,
  unknownOrderId: 'ORD-XX-999999',
} as const;
