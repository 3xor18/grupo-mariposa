export const copy = {
  login: 'Iniciar sesión',
  logout: 'Cerrar sesión',
  orderIdField: 'ID del pedido',
  approved: 'Aprobado',
  notFound: 'Pedido no encontrado',
  ordersTab: /^Pedidos(\s|$)/,
  recentOrders: 'Pedidos recientes',
  searchIdle: 'Consulta un pedido',
} as const;

export const seed = {
  approvedOrderId: 'ORD-MX-000147',
  approvedGrandTotal: /2,100\.11/,
  unknownOrderId: 'ORD-XX-999999',
} as const;
