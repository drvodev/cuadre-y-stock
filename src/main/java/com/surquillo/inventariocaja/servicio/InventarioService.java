package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.modelo.stock.MovimientoStock.Tipo;
import com.surquillo.inventariocaja.repositorio.StockRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;

/** Coordina las reglas de stock con el almacén de saldos y movimientos. */
public class InventarioService {
    private final StockRepository repositorio;
    private final StockService calculos = new StockService();
    private final Clock reloj;

    public InventarioService(StockRepository repositorio, Clock reloj) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public StockRepository.Estado iniciar(UUID id, ClaveStock clave, boolean fraccion, BigDecimal inicial, String referencia) {
        Objects.requireNonNull(id); Objects.requireNonNull(clave);
        SaldoStock.validarCantidad(inicial, fraccion);
        String ref = texto(referencia);
        String huella = huella("INICIAL", Boolean.toString(fraccion), numero(inicial), ref);
        return repositorio.registrar(id, huella, clave, anterior -> {
            if (anterior.isPresent()) throw new ValidacionException("Ya existe stock inicial para este SKU y almacén");
            var nuevo = new SaldoStock(clave.sku(), clave.almacen(), fraccion, inicial, BigDecimal.ZERO);
            return estado(id, Tipo.INICIAL, nuevo, BigDecimal.ZERO, inicial, inicial, ref, "Stock inicial");
        });
    }

    public StockRepository.Estado entrada(UUID id, ClaveStock clave, Tipo tipo, BigDecimal cantidad, String referencia) {
        if (tipo != Tipo.COMPRA && tipo != Tipo.FABRICACION && tipo != Tipo.DEVOLUCION)
            throw new ValidacionException("Tipo de entrada no válido");
        return cambiar(id, clave, tipo, cantidad, referencia, "", false);
    }

    public StockRepository.Estado venta(UUID id, ClaveStock clave, BigDecimal cantidad, String referencia) {
        return cambiar(id, clave, Tipo.VENTA, cantidad, referencia, "", false);
    }

    public StockRepository.Estado ajustar(UUID id, ClaveStock clave, BigDecimal nuevoFisico, String motivo) {
        return cambiar(id, clave, Tipo.AJUSTE, nuevoFisico, "", motivo, true);
    }

    public Optional<SaldoStock> consultar(ClaveStock clave) { return repositorio.buscar(clave); }
    public List<MovimientoStock> historial(ClaveStock clave) { return repositorio.historial(clave); }

    public boolean stockBajo(ClaveStock clave, BigDecimal minimo) {
        var saldo = repositorio.buscar(clave).orElseThrow(() -> new ValidacionException("Stock no registrado"));
        SaldoStock.validarCantidad(minimo, saldo.permiteFraccion());
        return saldo.disponible().compareTo(minimo) <= 0;
    }
    public void configurarMinimo(ClaveStock clave,BigDecimal minimo) { repositorio.configurarMinimo(clave,minimo); }
    public boolean stockBajo(ClaveStock clave) { return stockBajo(clave,repositorio.minimo(clave)); }

    private StockRepository.Estado cambiar(UUID id, ClaveStock clave, Tipo tipo, BigDecimal cantidad,
                                           String referencia, String motivo, boolean ajuste) {
        Objects.requireNonNull(id); Objects.requireNonNull(clave);
        if (cantidad == null) throw new ValidacionException("Cantidad obligatoria");
        String ref = texto(referencia), razon = texto(motivo);
        if (ajuste && razon.isBlank()) throw new ValidacionException("El motivo es obligatorio");
        String huella = huella(tipo.name(), numero(cantidad), ref, razon);
        return repositorio.registrar(id, huella, clave, anterior -> {
            var saldo = anterior.orElseThrow(() -> new ValidacionException("Stock no registrado"));
            SaldoStock nuevo = switch (tipo) {
                case VENTA -> calculos.descontarVenta(saldo, cantidad);
                case AJUSTE -> calculos.ajustar(saldo, cantidad, razon);
                default -> calculos.entrada(saldo, cantidad);
            };
            return estado(id, tipo, nuevo, saldo.fisico(), cantidad,
                    nuevo.fisico().subtract(saldo.fisico()), ref, razon);
        });
    }

    private StockRepository.Estado estado(UUID id, Tipo tipo, SaldoStock nuevo, BigDecimal anterior,
                                           BigDecimal solicitada, BigDecimal cambio, String ref, String motivo) {
        var movimiento = new MovimientoStock(id, reloj.instant(), tipo, nuevo.sku(), nuevo.almacen(),
                solicitada, cambio, anterior, nuevo.fisico(), ref, motivo);
        return new StockRepository.Estado(nuevo, movimiento);
    }
    private static String texto(String valor) { return valor == null ? "" : valor.strip(); }
    private static String numero(BigDecimal valor) { return valor.stripTrailingZeros().toPlainString(); }
    private static String huella(String... partes) {
        var b = new StringBuilder();
        for (String parte : partes) b.append(parte.length()).append(':').append(parte);
        return b.toString();
    }
}
