package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.SaldoStock;
import com.surquillo.inventariocaja.modelo.stock.Reserva;
import java.math.BigDecimal;
import java.util.Objects;

/** Cálculos puros: la persistencia y las transacciones se incorporarán después. */
public class StockService {
    public SaldoStock entrada(SaldoStock saldo, BigDecimal cantidad) {
        validarPositiva(saldo, cantidad);
        return nuevo(saldo, saldo.fisico().add(cantidad));
    }

    /** Descuenta únicamente el disponible; nunca consume stock reservado. */
    public SaldoStock descontarVenta(SaldoStock saldo, BigDecimal cantidad) {
        validarPositiva(saldo, cantidad);
        BigDecimal descuento = saldo.disponible().min(cantidad);
        return nuevo(saldo, saldo.fisico().subtract(descuento));
    }

    public SaldoStock ajustar(SaldoStock saldo, BigDecimal nuevoFisico, String motivo) {
        Objects.requireNonNull(saldo, "saldo");
        if (motivo == null || motivo.isBlank()) throw new ValidacionException("El motivo es obligatorio");
        SaldoStock.validarCantidad(nuevoFisico, saldo.permiteFraccion());
        return nuevo(saldo, nuevoFisico);
    }

    /** Permite mostrar la oferta y el faltante antes de que el vendedor confirme. */
    public Reserva proponerReserva(SaldoStock saldo, BigDecimal solicitada) {
        validarPositiva(saldo, solicitada);
        BigDecimal disponible = saldo.disponible().min(solicitada);
        return new Reserva(solicitada, disponible, solicitada.subtract(disponible));
    }

    /** Solo se confirma la cantidad aceptada por el vendedor y disponible en ese momento. */
    public SaldoStock reservar(SaldoStock saldo, BigDecimal aceptada) {
        validarPositiva(saldo, aceptada);
        if (aceptada.compareTo(saldo.disponible()) > 0)
            throw new ValidacionException("El stock disponible cambió; revise nuevamente la reserva");
        return new SaldoStock(saldo.sku(), saldo.almacen(), saldo.permiteFraccion(),
                saldo.fisico(), saldo.reservado().add(aceptada));
    }

    public SaldoStock liberarReserva(SaldoStock saldo, BigDecimal cantidad) {
        validarReservaExistente(saldo, cantidad);
        return new SaldoStock(saldo.sku(), saldo.almacen(), saldo.permiteFraccion(),
                saldo.fisico(), saldo.reservado().subtract(cantidad));
    }

    /** La comprobación del pago y la entrega única pertenecen al flujo de Caja. */
    public SaldoStock despacharReserva(SaldoStock saldo, BigDecimal cantidad) {
        validarReservaExistente(saldo, cantidad);
        return new SaldoStock(saldo.sku(), saldo.almacen(), saldo.permiteFraccion(),
                saldo.fisico().subtract(cantidad), saldo.reservado().subtract(cantidad));
    }

    private void validarReservaExistente(SaldoStock saldo, BigDecimal cantidad) {
        validarPositiva(saldo, cantidad);
        if (cantidad.compareTo(saldo.reservado()) > 0)
            throw new ValidacionException("La cantidad supera el stock reservado");
    }

    private void validarPositiva(SaldoStock saldo, BigDecimal cantidad) {
        Objects.requireNonNull(saldo, "saldo");
        SaldoStock.validarCantidad(cantidad, saldo.permiteFraccion());
        if (cantidad.signum() == 0) throw new ValidacionException("La cantidad debe ser positiva");
    }

    private SaldoStock nuevo(SaldoStock saldo, BigDecimal fisico) {
        return new SaldoStock(saldo.sku(), saldo.almacen(), saldo.permiteFraccion(), fisico, saldo.reservado());
    }
}
