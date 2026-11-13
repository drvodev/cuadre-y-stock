-- Ampliación aditiva: conserva artículos y sus versiones históricas.
ALTER TABLE catalogo_variante ADD COLUMN atributos jsonb NOT NULL DEFAULT '{}'::jsonb;
CREATE FUNCTION atributos_catalogo_validos(datos jsonb) RETURNS boolean
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE WHEN jsonb_typeof(datos) <> 'object' THEN false ELSE
        (SELECT count(*) <= 50 AND coalesce(bool_and(
            length(key) BETWEEN 1 AND 60 AND key=lower(btrim(key)) AND btrim(key)<>''
            AND jsonb_typeof(value)='string'
            AND length(value #>> '{}') BETWEEN 1 AND 500 AND btrim(value #>> '{}')<>''
        ),true) FROM jsonb_each(datos)) END;
$$;
ALTER TABLE catalogo_variante ADD CONSTRAINT atributos_catalogo_validos CHECK(atributos_catalogo_validos(atributos));

CREATE FUNCTION clave_lista(nombre text) RETURNS text LANGUAGE sql IMMUTABLE STRICT AS $$
    SELECT lower(regexp_replace(btrim(nombre),'\s+',' ','g'));
$$;
CREATE TABLE catalogo_opcion (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lista text NOT NULL CHECK(lista IN ('CATEGORIA','MARCA','UNIDAD','PRESENTACION')),
    nombre text NOT NULL CHECK(btrim(nombre)<>''),
    clave text COLLATE "C" GENERATED ALWAYS AS (clave_lista(nombre)) STORED,
    activo boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
    UNIQUE(lista,clave)
);
CREATE TABLE catalogo_opcion_historial (
    secuencia bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id uuid NOT NULL, fecha timestamptz NOT NULL DEFAULT now(),
    anterior jsonb, posterior jsonb NOT NULL
);
CREATE INDEX catalogo_opcion_historial_id ON catalogo_opcion_historial(id,secuencia);
CREATE FUNCTION historial_opcion_catalogo() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO catalogo_opcion_historial(id,anterior,posterior)
    VALUES(NEW.id,CASE WHEN TG_OP='UPDATE' THEN to_jsonb(OLD) ELSE NULL END,to_jsonb(NEW));
    RETURN NEW;
END;
$$;
CREATE TRIGGER historial_opcion_catalogo AFTER INSERT OR UPDATE ON catalogo_opcion
FOR EACH ROW EXECUTE FUNCTION historial_opcion_catalogo();

INSERT INTO catalogo_opcion(lista,nombre)
SELECT lista,regexp_replace(btrim(nombre),'\s+',' ','g') FROM (
    SELECT 'CATEGORIA' AS lista,categoria AS nombre FROM catalogo_variante
    UNION ALL SELECT 'MARCA',marca FROM catalogo_variante
    UNION ALL SELECT 'UNIDAD',unidad FROM catalogo_variante
    UNION ALL SELECT 'PRESENTACION',presentacion FROM catalogo_variante
) valores WHERE btrim(nombre)<>''
ORDER BY lista,nombre
ON CONFLICT(lista,clave) DO NOTHING;

CREATE FUNCTION registrar_opciones_articulo() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE listas text[]:=ARRAY['CATEGORIA','MARCA','UNIDAD','PRESENTACION'];
        campos text[]:=ARRAY['categoria','marca','unidad','presentacion'];
        nombre text; i integer;
BEGIN
    FOR i IN 1..4 LOOP
        nombre:=to_jsonb(NEW)->>campos[i];
        IF btrim(nombre)<>'' THEN
            IF TG_OP='INSERT' OR nombre IS DISTINCT FROM (to_jsonb(OLD)->>campos[i]) THEN
                INSERT INTO catalogo_opcion(lista,nombre)
                VALUES(listas[i],regexp_replace(btrim(nombre),'\s+',' ','g'))
                ON CONFLICT(lista,clave) DO NOTHING;
            END IF;
        END IF;
    END LOOP;
    RETURN NEW;
END;
$$;
CREATE TRIGGER registrar_opciones_articulo AFTER INSERT OR UPDATE OF categoria,marca,unidad,presentacion
ON catalogo_variante FOR EACH ROW EXECUTE FUNCTION registrar_opciones_articulo();

CREATE INDEX catalogo_categoria_sku ON catalogo_variante(clave_lista(categoria),sku);
CREATE INDEX catalogo_marca_sku ON catalogo_variante(clave_lista(marca),sku);
CREATE INDEX catalogo_unidad_sku ON catalogo_variante(clave_lista(unidad),sku);
CREATE INDEX catalogo_presentacion_sku ON catalogo_variante(clave_lista(presentacion),sku);
