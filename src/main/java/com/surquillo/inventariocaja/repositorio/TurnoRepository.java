package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.modelo.caja.*;
import java.util.*;
import java.util.function.Function;

public interface TurnoRepository {
    record Estado(Turno turno, List<MovimientoCaja> movimientos, Cierre cierre) {
        public Estado { movimientos=List.copyOf(movimientos); }
    }
    Optional<Estado> consultar(UUID turno);
    Estado aplicar(UUID solicitud,Object comando,String caja,UUID turno,Function<Optional<Estado>,Estado> cambio);
}
