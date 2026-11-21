-- Conservar códigos usados y continuar la numeración sin modificar artículos existentes.
CREATE SEQUENCE catalogo_sku_secuencia AS bigint MINVALUE 1 START 1;
CREATE TABLE catalogo_sku_utilizado (sku text PRIMARY KEY);
INSERT INTO catalogo_sku_utilizado(sku)
SELECT sku FROM catalogo_variante UNION SELECT sku FROM catalogo_historial
ON CONFLICT DO NOTHING;
SELECT setval('catalogo_sku_secuencia', GREATEST(1,COALESCE((
    SELECT max(sku::bigint) FROM catalogo_sku_utilizado
    WHERE sku ~ '^[0-9]{1,18}$'
),1)), EXISTS(SELECT 1 FROM catalogo_sku_utilizado WHERE sku ~ '^[0-9]{1,18}$' AND CASE WHEN sku ~ '^[0-9]{1,18}$' THEN sku::numeric ELSE 0 END > 0));
CREATE FUNCTION reservar_sku_catalogo() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO catalogo_sku_utilizado(sku) VALUES(NEW.sku);
    RETURN NEW;
END;
$$;
CREATE TRIGGER catalogo_sku_no_reutilizar BEFORE INSERT ON catalogo_variante
FOR EACH ROW EXECUTE FUNCTION reservar_sku_catalogo();
