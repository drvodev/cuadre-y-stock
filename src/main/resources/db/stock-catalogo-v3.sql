CREATE TABLE stock_minimo (
    sku text NOT NULL, almacen text NOT NULL, cantidad numeric NOT NULL CHECK(cantidad >= 0),
    PRIMARY KEY(sku,almacen), FOREIGN KEY(sku,almacen) REFERENCES stock_saldo(sku,almacen)
);
CREATE TABLE catalogo_historial (
    secuencia bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku text NOT NULL, fecha timestamptz NOT NULL DEFAULT now(),
    anterior jsonb, posterior jsonb NOT NULL
);
CREATE INDEX catalogo_historial_sku ON catalogo_historial(sku,secuencia);
CREATE FUNCTION registrar_historial_catalogo() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO catalogo_historial(sku,anterior,posterior)
    VALUES(NEW.sku, CASE WHEN TG_OP='UPDATE' THEN to_jsonb(OLD) ELSE NULL END,to_jsonb(NEW));
    RETURN NEW;
END;
$$;
CREATE TRIGGER historial_catalogo AFTER INSERT OR UPDATE ON catalogo_variante
FOR EACH ROW EXECUTE FUNCTION registrar_historial_catalogo();
