package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.modelo.stock.*;
import java.util.List;
import java.util.Optional;

public interface ProductoRepository {
    List<String> opciones(ListaCatalogo lista, int limite);
    OpcionCatalogo crearOpcion(OpcionCatalogo opcion);
    OpcionCatalogo actualizarOpcion(OpcionCatalogo opcion);
    Optional<OpcionCatalogo> buscarOpcion(java.util.UUID id);
    List<OpcionCatalogo> listarOpciones(ListaCatalogo lista,String texto,String despues,int limite,boolean incluirInactivos);
    List<CambioOpcionCatalogo> historialOpcion(java.util.UUID id,long despues,int limite);
    List<com.surquillo.inventariocaja.modelo.stock.CambioCatalogo> historial(String sku,long despues,int limite);
    Variante crear(Variante variante);
    Variante actualizar(Variante variante);
    Optional<Variante> buscarSku(String sku);
    List<Variante> buscar(String texto, String despuesDeSku, int limite, boolean incluirInactivos);
    List<Variante> buscar(FiltroCatalogo filtro,String despuesDeSku,int limite);
}
