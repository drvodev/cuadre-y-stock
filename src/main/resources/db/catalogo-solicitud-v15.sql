-- Conserva la identidad de las creaciones automáticas para reintentos seguros.
LOCK TABLE catalogo_variante IN SHARE ROW EXCLUSIVE MODE;
CREATE TABLE catalogo_creacion_solicitud (
    id uuid PRIMARY KEY,
    huella text NOT NULL,
    sku text NOT NULL
);
-- Recoge códigos manuales creados después de la migración 14.
DO $$
DECLARE maximo bigint; actual bigint; ejecutada boolean;
BEGIN
    SELECT max(sku::bigint) INTO maximo FROM catalogo_sku_utilizado WHERE sku ~ '^[0-9]{1,18}$';
    SELECT last_value,is_called INTO actual,ejecutada FROM catalogo_sku_secuencia;
    IF maximo > actual OR (maximo = actual AND NOT ejecutada AND maximo > 0) THEN
        PERFORM setval('catalogo_sku_secuencia',maximo,true);
    END IF;
END;
$$;
-- La inserción manual y la generación automática comparten el mismo bloqueo.
CREATE OR REPLACE FUNCTION reservar_sku_catalogo() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE numero bigint; actual bigint; ejecutada boolean;
BEGIN
    PERFORM pg_advisory_xact_lock(739102817);
    INSERT INTO catalogo_sku_utilizado(sku) VALUES(NEW.sku);
    IF NEW.sku ~ '^[0-9]{1,18}$' THEN
        numero := NEW.sku::bigint;
        SELECT last_value,is_called INTO actual,ejecutada FROM catalogo_sku_secuencia;
        IF numero > actual OR (numero = actual AND NOT ejecutada AND numero > 0) THEN
            PERFORM setval('catalogo_sku_secuencia',numero,true);
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
