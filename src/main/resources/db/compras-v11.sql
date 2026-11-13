CREATE TABLE compra_operacion (
    id uuid PRIMARY KEY, secuencia bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
    solicitud uuid NOT NULL UNIQUE REFERENCES caja_movimiento(id),
    huella text NOT NULL, turno uuid NOT NULL REFERENCES caja_turno(id),
    fecha timestamptz NOT NULL, proveedor text NOT NULL CHECK(btrim(proveedor)<>''),
    proveedor_clave text COLLATE "C" GENERATED ALWAYS AS (clave_lista(proveedor)) STORED,
    tipo text NOT NULL CHECK(tipo IN ('BOLETA','FACTURA','NOTA_VENTA')),
    numero text NOT NULL CHECK(numero=upper(btrim(numero)) AND numero<>''),
    fuente text NOT NULL CHECK(fuente IN ('CAJA','EXTERNA')),
    total numeric(16,2) NOT NULL CHECK(total>0),
    UNIQUE(proveedor_clave,tipo,numero)
);
CREATE TABLE compra_item (
    compra uuid NOT NULL REFERENCES compra_operacion(id), posicion integer NOT NULL CHECK(posicion>=0),
    sku text NOT NULL, almacen text NOT NULL, descripcion text NOT NULL CHECK(btrim(descripcion)<>''),
    unidad text NOT NULL CHECK(btrim(unidad)<>''), cantidad numeric NOT NULL CHECK(cantidad>0 AND scale(cantidad)<=3),
    costo numeric(16,2) CHECK(costo>=0), PRIMARY KEY(compra,posicion)
);
CREATE TABLE compra_pago (
    compra uuid NOT NULL REFERENCES compra_operacion(id),
    medio text NOT NULL CHECK(medio IN ('EFECTIVO','YAPE','PLIN','VISA','DEPOSITO','TRANSFERENCIA')),
    importe numeric(16,2) NOT NULL CHECK(importe>0), PRIMARY KEY(compra,medio)
);
CREATE INDEX compra_turno ON compra_operacion(turno,secuencia);
