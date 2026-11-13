CREATE TABLE venta_correccion (
    id uuid PRIMARY KEY, secuencia bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
    solicitud uuid NOT NULL UNIQUE REFERENCES venta_solicitud(id) DEFERRABLE INITIALLY DEFERRED,
    original uuid NOT NULL REFERENCES venta_operacion(id),
    turno uuid NOT NULL REFERENCES caja_turno(id),
    tipo text NOT NULL CHECK(tipo IN ('DEVOLUCION','CAMBIO')),
    comprobante text NOT NULL UNIQUE CHECK(comprobante=upper(btrim(comprobante)) AND comprobante<>''),
    motivo text NOT NULL CHECK(btrim(motivo)<>''), fecha timestamptz NOT NULL,
    encargado text NOT NULL CHECK(btrim(encargado)<>''),
    valor_original numeric(16,2) NOT NULL CHECK(valor_original>=0),
    reconocido numeric(16,2) NOT NULL CHECK(reconocido>=0 AND reconocido<=valor_original),
    total_reemplazo numeric(16,2) NOT NULL CHECK(total_reemplazo>=0),
    diferencia numeric(16,2) GENERATED ALWAYS AS (total_reemplazo-reconocido) STORED,
    reemplazo uuid UNIQUE REFERENCES venta_operacion(id),
    CHECK((tipo='DEVOLUCION' AND reemplazo IS NULL AND total_reemplazo=0)
       OR (tipo='CAMBIO' AND reemplazo IS NOT NULL AND total_reemplazo>0))
);
CREATE INDEX venta_correccion_original ON venta_correccion(original,secuencia);
CREATE TABLE venta_correccion_item (
    correccion uuid NOT NULL REFERENCES venta_correccion(id),
    posicion integer NOT NULL CHECK(posicion>=0),
    cantidad numeric NOT NULL CHECK(cantidad>0 AND scale(cantidad)<=3),
    reingresar boolean NOT NULL,
    valor_original numeric(16,2) NOT NULL CHECK(valor_original>=0),
    PRIMARY KEY(correccion,posicion)
);
CREATE TABLE venta_correccion_pago (
    correccion uuid NOT NULL REFERENCES venta_correccion(id),
    medio text NOT NULL CHECK(medio IN ('EFECTIVO','VISA','TRANSFERENCIA','DEPOSITO','YAPE','PLIN')),
    importe numeric(16,2) NOT NULL CHECK(importe>0), PRIMARY KEY(correccion,medio)
);
