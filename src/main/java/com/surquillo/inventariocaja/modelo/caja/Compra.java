package com.surquillo.inventariocaja.modelo.caja;
import com.surquillo.inventariocaja.excepcion.ValidacionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Compra confirmada e inmutable: documento, recepción y pago completo. */
public record Compra(UUID id,long secuencia,UUID turno,Instant fecha,String proveedor,Documento documento,
                     MovimientoCaja.Fuente fuente,BigDecimal total,List<DetalleCompra> items,Map<MedioPago,BigDecimal> pagos) {
    public Compra {
        if(id==null || turno==null || fecha==null || documento==null || fuente==null || secuencia<0)
            throw new ValidacionException("Identifica la compra, documento, fuente y turno");
        proveedor=proveedor==null?"":proveedor.strip().replaceAll("\\s+"," ");
        if(!documento.manual())throw new ValidacionException("Ingresa manualmente el documento del proveedor");
        if(proveedor.isBlank())throw new ValidacionException("Indica el proveedor");
        total=moneda(total,false);items=List.copyOf(items);
        if(items.isEmpty() || items.size()>1000)throw new ValidacionException("La compra requiere entre 1 y 1000 ítems");
        var medios=new EnumMap<MedioPago,BigDecimal>(MedioPago.class);
        pagos.forEach((medio,monto)->{if(medio==null)throw new ValidacionException("Medio de pago obligatorio");medios.put(medio,moneda(monto,false));});
        pagos=Collections.unmodifiableMap(medios);
        if(pagos.isEmpty() || pagos.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(total)!=0)
            throw new ValidacionException("La compra se registra recibida y pagada por su total");
        var conocido=items.stream().map(DetalleCompra::totalConocido).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(conocido.compareTo(total)>0 || items.stream().allMatch(i->i.costoUnitario()!=null) && conocido.compareTo(total)!=0)
            throw new ValidacionException("Revisa los costos de los ítems y el total del documento");
    }
    private static BigDecimal moneda(BigDecimal n,boolean cero) {
        if(n==null || n.signum()<0 || !cero && n.signum()==0 || n.stripTrailingZeros().scale()>2 || n.abs().compareTo(new BigDecimal("100000000000000"))>=0)
            throw new ValidacionException("Importe inválido; usa hasta dos decimales");
        return n.setScale(2);
    }
    public String claveProveedor() { return proveedor.toLowerCase(Locale.ROOT); }
}
