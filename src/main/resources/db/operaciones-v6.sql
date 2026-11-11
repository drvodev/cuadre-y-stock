CREATE TABLE venta_operacion (
    id uuid PRIMARY KEY, modalidad text NOT NULL, tipo text NOT NULL, numero text NOT NULL,
    manual boolean NOT NULL, cliente text NOT NULL, contacto text NOT NULL,
    total numeric NOT NULL CHECK(total>0), abonado numeric NOT NULL CHECK(abonado>=0 AND abonado<=total),
    estado text NOT NULL, fecha timestamptz NOT NULL
);
CREATE TABLE venta_documento (
    secuencia bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operacion uuid NOT NULL REFERENCES venta_operacion(id), tipo text NOT NULL, numero text NOT NULL,
    manual boolean NOT NULL, fecha timestamptz NOT NULL DEFAULT now(), UNIQUE(tipo,numero)
);
CREATE TABLE venta_item (
    operacion uuid NOT NULL REFERENCES venta_operacion(id), posicion integer NOT NULL,
    sku text NOT NULL, almacen text NOT NULL, descripcion text NOT NULL, unidad text NOT NULL,
    cantidad numeric NOT NULL CHECK(cantidad>0), precio numeric NOT NULL CHECK(precio>0), indicaciones text NOT NULL,
    PRIMARY KEY(operacion,posicion)
);
CREATE TABLE venta_solicitud (
    id uuid PRIMARY KEY, huella text NOT NULL, operacion uuid NOT NULL REFERENCES venta_operacion(id)
);
