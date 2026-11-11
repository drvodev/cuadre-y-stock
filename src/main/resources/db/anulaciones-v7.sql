CREATE TABLE venta_anulacion (
    solicitud uuid PRIMARY KEY, operacion uuid NOT NULL UNIQUE REFERENCES venta_operacion(id),
    turno uuid NOT NULL REFERENCES caja_turno(id), motivo text NOT NULL, devuelto numeric NOT NULL CHECK(devuelto>=0),
    fecha timestamptz NOT NULL, reingresados text NOT NULL
);
