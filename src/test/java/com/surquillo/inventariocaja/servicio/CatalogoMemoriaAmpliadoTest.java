package com.surquillo.inventariocaja.servicio;
import com.surquillo.inventariocaja.repositorio.*;
class CatalogoMemoriaAmpliadoTest extends CatalogoContratoTest {
    @Override protected ProductoRepository nuevoRepositorio() { return new ProductoMemoriaRepository(); }
}
