CREATE TABLE venta_ganancia (
    operacion uuid PRIMARY KEY REFERENCES venta_operacion(id),
    importe numeric(16,2) NOT NULL
);
CREATE TABLE venta_ganancia_historial (
    secuencia bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operacion uuid NOT NULL REFERENCES venta_operacion(id),
    anterior numeric(16,2),
    nuevo numeric(16,2) NOT NULL,
    fecha timestamptz NOT NULL DEFAULT clock_timestamp(),
    usuario text NOT NULL DEFAULT session_user
);
CREATE FUNCTION auditar_venta_ganancia() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO venta_ganancia_historial(operacion, anterior, nuevo)
    VALUES (NEW.operacion, OLD.importe, NEW.importe);
    RETURN NEW;
END;
$$;
CREATE TRIGGER cambio_ganancia AFTER INSERT OR UPDATE ON venta_ganancia
FOR EACH ROW EXECUTE FUNCTION auditar_venta_ganancia();
