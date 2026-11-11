package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import java.util.*;
import java.util.function.Function;

/** Implementación de prueba; los datos no sobreviven al cierre del proceso. */
public class TurnoMemoriaRepository implements TurnoRepository {
    private record Solicitud(String comando,String caja,UUID turno,Estado resultado) {}
    private final Map<UUID,Estado> turnos=new HashMap<>();
    private final Map<String,UUID> abiertos=new HashMap<>();
    private final Map<UUID,Solicitud> solicitudes=new HashMap<>();
    @Override public synchronized Optional<Estado> consultar(UUID id) { return Optional.ofNullable(turnos.get(id)); }
    @Override public synchronized Optional<Estado> abierto(String caja) { return Optional.ofNullable(abiertos.get(caja)).flatMap(this::consultar); }
    @Override public synchronized List<com.surquillo.inventariocaja.modelo.caja.Turno> listar(int limite) {
        if(limite<1||limite>100) throw new ValidacionException("Límite de turnos inválido");
        return turnos.values().stream().map(Estado::turno).sorted(Comparator.comparing(com.surquillo.inventariocaja.modelo.caja.Turno::fechaOperativa).reversed()).limit(limite).toList();
    }
    @Override public synchronized Estado aplicar(UUID id,String comando,String caja,UUID turno,Function<Optional<Estado>,Estado> cambio) {
        Objects.requireNonNull(id); Objects.requireNonNull(comando); Objects.requireNonNull(caja); Objects.requireNonNull(turno);
        var previa=solicitudes.get(id);
        if(previa!=null) {
            if(!previa.comando().equals(comando)||!previa.caja().equals(caja)||!previa.turno().equals(turno))
                throw new ValidacionException("La solicitud ya se utilizó con otros datos");
            return previa.resultado();
        }
        var anterior=turnos.get(turno);
        if(anterior==null && abiertos.containsKey(caja)) throw new ValidacionException("La caja ya tiene un turno abierto");
        var nuevo=Objects.requireNonNull(cambio.apply(Optional.ofNullable(anterior)));
        if(!nuevo.turno().id().equals(turno)||!nuevo.turno().caja().equals(caja)) throw new IllegalStateException("Turno inconsistente");
        turnos.put(turno,nuevo);
        if(nuevo.turno().cerrado()) abiertos.remove(caja,turno); else abiertos.put(caja,turno);
        solicitudes.put(id,new Solicitud(comando,caja,turno,nuevo)); return nuevo;
    }
}
