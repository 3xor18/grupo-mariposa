abstract final class AppStrings {
  static const appTitle = 'Mariposa Order Tracker';

  static const navSearch = 'Buscar';
  static const navOrders = 'Pedidos';

  static const loginSubtitle =
      'Inicia sesión con tu cuenta corporativa para consultar pedidos B2B.';
  static const loginButton = 'Iniciar sesión';
  static const logout = 'Cerrar sesión';
  static const checkingSession = 'Verificando sesión…';
  static const unknownUser = 'Usuario';
  static const configErrorTitle = 'No se pudo iniciar la aplicación';
  static const configErrorMessage =
      'Falta la configuración de ejecución (config.json). Contacta al equipo de soporte.';

  static const orderIdLabel = 'ID del pedido';
  static const orderIdHint = 'Ej. ORD-MX-000147';
  static const searchButton = 'Buscar';
  static const clearSearch = 'Limpiar búsqueda';
  static const orderIdRequired = 'Ingresa un ID de pedido';
  static const searchIdleTitle = 'Consulta un pedido';
  static const searchIdleMessage = 'Escribe el ID para ver su estado, sus líneas y sus totales.';
  static const loadingOrder = 'Buscando pedido…';
  static const notFoundTitle = 'Pedido no encontrado';
  static const retry = 'Reintentar';

  static const statusApproved = 'Aprobado';
  static const statusRejected = 'Rechazado';
  static const statusTechnicalFailure = 'Falla técnica';
  static const statusUnknown = 'Desconocido';

  static const client = 'Cliente';
  static const market = 'Mercado';
  static const channel = 'Canal';
  static const segment = 'Segmento';
  static const occurredAt = 'Emitido';
  static const receivedAt = 'Recibido';
  static const processedAt = 'Procesado';
  static const noLines = 'El pedido no tiene líneas registradas.';
  static const totals = 'Totales';
  static const grossSubtotal = 'Subtotal bruto';
  static const discount = 'Descuento';
  static const netSubtotal = 'Subtotal neto';
  static const tax = 'Impuestos';
  static const grandTotal = 'Total';
  static const rejectionTitle = 'Motivo del rechazo';
  static const violations = 'Reglas incumplidas';
  static const failureTitle = 'Falla técnica en el procesamiento';
  static const failureCategory = 'Categoría';
  static const failureCause = 'Causa';
  static const failureAttempts = 'Intentos';
  static const failureHint = 'El pedido se reintentará o será revisado por operaciones.';

  static const recentOrders = 'Pedidos recientes';
  static const filterStatus = 'Estado';
  static const filterMarket = 'Mercado';
  static const loadMore = 'Cargar más';
  static const loadingOrders = 'Cargando pedidos…';
  static const emptyListTitle = 'Sin pedidos';
  static const emptyListMessage = 'No hay pedidos que coincidan con los filtros seleccionados.';
  static const selectOrderTitle = 'Selecciona un pedido';
  static const selectOrderMessage = 'Elige un pedido de la lista para ver su detalle.';
  static const refreshFailed = 'No se pudo actualizar la lista.';
  static const nextPageFailed = 'No se pudieron cargar más pedidos.';

  static const marketMx = 'México';
  static const marketCo = 'Colombia';
  static const marketPe = 'Perú';

  static const errorTitle = 'Algo salió mal';
  static const networkError = 'No hay conexión con el servidor. Revisa tu red e intenta de nuevo.';
  static const serverError = 'El servicio no está disponible en este momento. Intenta de nuevo.';
  static const rateLimitedError =
      'Demasiadas solicitudes. Espera unos segundos e intenta de nuevo.';
  static const unauthorizedError = 'Tu sesión expiró. Inicia sesión nuevamente.';
  static const forbiddenError = 'Tu usuario no tiene permiso para consultar pedidos.';
  static const validationError = 'La consulta no es válida. Revisa los datos ingresados.';
  static const unexpectedError = 'Recibimos una respuesta inesperada del servidor.';
  static const notFoundError = 'No se encontró el recurso solicitado.';
  static const loginFailedError = 'No se pudo iniciar sesión. Intenta nuevamente.';

  static String orderIdTooLong(int maxLength) => 'El ID admite máximo $maxLength caracteres';

  static String notFoundMessage(String orderId) =>
      'No existe un pedido con el ID $orderId. Revisa el identificador e intenta de nuevo.';

  static String retryAfter(int seconds) => 'Puedes reintentar en $seconds s.';

  static String supportCode(String traceId) => 'Código de soporte: $traceId';

  static String statusSemantics(String status) => 'Estado: $status';

  static String grandTotalSemantics(String amount) => 'Total del pedido: $amount';

  static String quantityTimesPrice(int quantity, String unitPrice) => '$quantity × $unitPrice';

  static String discountDetail(String rate, String amount) => 'Desc. $rate ($amount)';

  static String taxDetail(String rate, String amount) => 'Imp. $rate ($amount)';

  static String productReference(String productId) => 'Producto $productId';

  static String clientWithId(String name, String clientId) => '$name · $clientId';

  static String joinDetails(Iterable<String> parts) => parts.join(' · ');

  static String linesTitle(int count) => 'Líneas ($count)';

  static String signedInAs(String name) => 'Sesión iniciada como $name';

  static String orderSemantics(String orderId, String status, String total) =>
      'Pedido $orderId, $status, total $total';
}
