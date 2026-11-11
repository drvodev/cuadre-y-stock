-- Ejecutar mediante una futura migración controlada, antes de cargar datos reales.
CREATE TABLE catalogo_variante (
    sku text PRIMARY KEY CHECK (sku = upper(btrim(sku)) AND sku <> ''),
    descripcion text NOT NULL CHECK (btrim(descripcion) <> ''),
    categoria text NOT NULL, marca text NOT NULL, presentacion text NOT NULL, tipo text NOT NULL,
    unidad text NOT NULL CHECK (btrim(unidad) <> ''), permite_fraccion boolean NOT NULL,
    origen text NOT NULL CHECK (origen IN ('COMPRADO','FABRICADO')),
    contenido numeric CHECK (contenido > 0), unidad_contenido text NOT NULL,
    precio_venta numeric CHECK (precio_venta > 0), costo numeric CHECK (costo >= 0),
    activo boolean NOT NULL, version bigint NOT NULL CHECK (version >= 0),
    CHECK ((contenido IS NULL AND unidad_contenido = '') OR (contenido IS NOT NULL AND unidad_contenido <> ''))
);
CREATE INDEX catalogo_variante_activas ON catalogo_variante (sku) WHERE activo;
