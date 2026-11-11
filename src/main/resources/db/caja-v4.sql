CREATE TABLE caja_turno (
    id uuid PRIMARY KEY, caja text NOT NULL, fecha_operativa date NOT NULL,
    encargado text NOT NULL, fondo numeric NOT NULL CHECK(fondo>=0),
    version bigint NOT NULL CHECK(version>=0), cerrado boolean NOT NULL,
    cierre_fecha timestamptz, cierre_firma text, esperado numeric, contado numeric,
    diferencia numeric, observacion text,
    CHECK ((NOT cerrado AND cierre_fecha IS NULL) OR (cerrado AND cierre_fecha IS NOT NULL))
);
CREATE UNIQUE INDEX caja_un_turno_abierto ON caja_turno(caja) WHERE NOT cerrado;
CREATE TABLE caja_movimiento (
    id uuid PRIMARY KEY, turno uuid NOT NULL REFERENCES caja_turno(id),
    version bigint NOT NULL, fecha timestamptz NOT NULL, tipo text NOT NULL, fuente text NOT NULL,
    referencia text NOT NULL, UNIQUE(turno,version)
);
CREATE TABLE caja_linea (
    movimiento uuid NOT NULL REFERENCES caja_movimiento(id), medio text NOT NULL,
    importe numeric NOT NULL CHECK(importe>0), PRIMARY KEY(movimiento,medio)
);
CREATE TABLE caja_solicitud (
    id uuid PRIMARY KEY, huella text NOT NULL, caja text NOT NULL,
    turno uuid NOT NULL REFERENCES caja_turno(id), version bigint NOT NULL, cerrado boolean NOT NULL
);
