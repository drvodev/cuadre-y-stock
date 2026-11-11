package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.modelo.stock.Variante;
import java.util.List;
import java.util.Optional;

public interface ProductoRepository {
    enum Lista { CATEGORIA, MARCA, UNIDAD, PRESENTACION }
    List<String> opciones(Lista lista, int limite);
    List<com.surquillo.inventariocaja.modelo.stock.CambioCatalogo> historial(String sku,long despues,int limite);
    Variante crear(Variante variante);
    Variante actualizar(Variante variante);
    Optional<Variante> buscarSku(String sku);
    List<Variante> buscar(String texto, String despuesDeSku, int limite, boolean incluirInactivos);
}
