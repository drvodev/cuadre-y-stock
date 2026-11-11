CREATE TABLE caja_conciliacion (
    turno uuid NOT NULL REFERENCES caja_turno(id), medio text NOT NULL,
    cobros numeric NOT NULL, devoluciones numeric NOT NULL, egresos numeric NOT NULL, otros numeric NOT NULL,
    ver_cobros numeric NOT NULL, ver_devoluciones numeric NOT NULL, ver_egresos numeric NOT NULL, ver_otros numeric NOT NULL,
    PRIMARY KEY(turno,medio)
);
