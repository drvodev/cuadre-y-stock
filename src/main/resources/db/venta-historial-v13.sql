-- El historial comienza al instalar esta versión. Los registros anteriores reciben
-- una instantánea de migración; no se inventan estados para fechas anteriores.
CREATE TABLE venta_estado_historial (
    secuencia bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operacion uuid NOT NULL REFERENCES venta_operacion(id),
    fecha timestamptz NOT NULL DEFAULT clock_timestamp(),
    abonado numeric NOT NULL,
    estado text NOT NULL,
    origen text NOT NULL CHECK(origen IN ('MIGRACION','OPERACION'))
);
CREATE INDEX venta_estado_historial_corte ON venta_estado_historial(operacion,fecha DESC,secuencia DESC);
INSERT INTO venta_estado_historial(operacion,abonado,estado,origen)
SELECT id,abonado,estado,'MIGRACION' FROM venta_operacion;
CREATE FUNCTION auditar_venta_estado() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.abonado IS DISTINCT FROM OLD.abonado OR NEW.estado IS DISTINCT FROM OLD.estado THEN
        INSERT INTO venta_estado_historial(operacion,abonado,estado,origen)
        VALUES(NEW.id,NEW.abonado,NEW.estado,'OPERACION');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER venta_estado_auditoria AFTER INSERT OR UPDATE ON venta_operacion
FOR EACH ROW EXECUTE FUNCTION auditar_venta_estado();
