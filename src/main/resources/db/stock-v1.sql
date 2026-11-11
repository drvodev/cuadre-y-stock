-- Ejecutar explícitamente al instalar la base; no incluye datos ni credenciales.
CREATE TABLE stock_saldo (
    sku text NOT NULL, almacen text NOT NULL, permite_fraccion boolean NOT NULL,
    fisico numeric NOT NULL CHECK (fisico >= 0),
    reservado numeric NOT NULL CHECK (reservado >= 0 AND reservado <= fisico),
    PRIMARY KEY (sku, almacen)
);
CREATE TABLE stock_movimiento (
    secuencia bigint GENERATED ALWAYS AS IDENTITY UNIQUE, id uuid PRIMARY KEY, huella text NOT NULL, fecha timestamptz NOT NULL,
    tipo text NOT NULL, sku text NOT NULL, almacen text NOT NULL,
    cantidad_solicitada numeric NOT NULL, cambio_fisico numeric NOT NULL,
    fisico_anterior numeric NOT NULL, fisico_posterior numeric NOT NULL,
    reservado_posterior numeric NOT NULL, permite_fraccion boolean NOT NULL,
    referencia text NOT NULL, motivo text NOT NULL,
    reserva_id uuid, separacion_id uuid, reserva_solicitada numeric, reserva_cantidad numeric, reserva_estado text,
    FOREIGN KEY (sku, almacen) REFERENCES stock_saldo(sku, almacen)
);
CREATE INDEX stock_movimiento_historial ON stock_movimiento (sku, almacen, secuencia);
CREATE TABLE stock_reserva (
    id uuid PRIMARY KEY, separacion_id uuid NOT NULL, sku text NOT NULL, almacen text NOT NULL,
    solicitada numeric NOT NULL CHECK (solicitada > 0),
    reservada numeric NOT NULL CHECK (reservada >= 0 AND reservada <= solicitada),
    estado text NOT NULL CHECK (estado IN ('ACTIVA', 'CANCELADA', 'DESPACHADA')),
    FOREIGN KEY (sku, almacen) REFERENCES stock_saldo(sku, almacen)
);
