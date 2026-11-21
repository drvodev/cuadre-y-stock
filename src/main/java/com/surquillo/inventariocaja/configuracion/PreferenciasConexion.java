package com.surquillo.inventariocaja.configuracion;

import java.util.Objects;
import java.util.prefs.Preferences;

/** Preferencias de conexión no sensibles; no almacena claves. */
public final class PreferenciasConexion {
    public record Datos(String servidor,String puerto,String base,String usuario,String esquema) {}
    private final Preferences preferencias;
    public PreferenciasConexion(Preferences preferencias) { this.preferencias=Objects.requireNonNull(preferencias); }
    public Datos cargar() { return new Datos(preferencias.get("servidor","127.0.0.1"),preferencias.get("puerto","5432"),preferencias.get("base","postgres"),preferencias.get("usuario","postgres"),preferencias.get("esquema","cuadre_pruebas")); }
    public void guardar(Datos d) {
        Objects.requireNonNull(d);preferencias.put("servidor",d.servidor());preferencias.put("puerto",d.puerto());
        preferencias.put("base",d.base());preferencias.put("usuario",d.usuario());preferencias.put("esquema",d.esquema());
    }
}
