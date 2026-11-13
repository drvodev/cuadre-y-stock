-- Índice adicional para consultas históricas; los saldos posteriores ya están en cada movimiento.
CREATE INDEX stock_movimiento_corte ON stock_movimiento(sku,almacen,fecha,secuencia DESC);
