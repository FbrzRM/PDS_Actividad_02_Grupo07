import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Patrón Adapter (de objetos) aplicado al registro de asistencia.
 *
 * Target  : ServicioAsistencia              (contrato del sistema institucional)
 * Adaptee : BioNovaSDK / BioNovaPunch       (biblioteca del nuevo fabricante, no modificable)
 * Adapter : AdaptadorBioNova                (traduce el contrato a la biblioteca)
 * Client  : SistemaAsistenciaInstitucional  (solo conoce ServicioAsistencia)
 */

// =====================================================================
// TARGET: contrato estable que espera el sistema institucional
// =====================================================================

/** Información normalizada de asistencia que espera el sistema. */
record RegistroAsistencia(String idColaborador, LocalDate fecha,
                          LocalTime horaIngreso, LocalTime horaSalida) {
    @Override
    public String toString() {
        return String.format("Colaborador %s | Fecha %s | Ingreso %s | Salida %s",
                idColaborador, fecha,
                horaIngreso != null ? horaIngreso : "sin marcación",
                horaSalida != null ? horaSalida : "sin marcación");
    }
}

interface ServicioAsistencia {
    /** Solicita los registros de una persona para una fecha determinada. */
    Optional<RegistroAsistencia> obtenerRegistro(String idColaborador, LocalDate fecha);
}

// =====================================================================
// Proveedor actual: ya cumple el contrato, no necesita adaptación
// =====================================================================
class DispositivoProveedorActual implements ServicioAsistencia {
    private final Map<String, RegistroAsistencia> registros = new HashMap<>();

    public void registrar(RegistroAsistencia r) {
        registros.put(r.idColaborador() + "|" + r.fecha(), r);
    }

    @Override
    public Optional<RegistroAsistencia> obtenerRegistro(String idColaborador, LocalDate fecha) {
        return Optional.ofNullable(registros.get(idColaborador + "|" + fecha));
    }
}

// =====================================================================
// ADAPTEE: biblioteca del nuevo fabricante (NO se puede modificar).
// Se simula aquí; en la realidad vendría en un .jar del proveedor.
// Nombres, parámetros y tipos distintos al contrato institucional.
// =====================================================================

/** Tipo de dato propio del fabricante: una marcación individual. */
class BioNovaPunch {
    public static final int CHECK_IN = 0;
    public static final int CHECK_OUT = 1;

    private final int empCode;     // código numérico (no String)
    private final long punchTime;  // epoch en segundos (no LocalTime)
    private final int punchType;   // 0 = entrada, 1 = salida

    public BioNovaPunch(int empCode, long punchTime, int punchType) {
        this.empCode = empCode;
        this.punchTime = punchTime;
        this.punchType = punchType;
    }

    public int getEmpCode() { return empCode; }
    public long getPunchTime() { return punchTime; }
    public int getPunchType() { return punchType; }
}

/** API del fabricante: devuelve una colección de marcaciones, no la asistencia diaria. */
class BioNovaSDK {
    private final List<BioNovaPunch> memoria = new ArrayList<>(); // simula el dispositivo
    private final ZoneId zonaDispositivo;                           // configuración interna del equipo

    public BioNovaSDK(ZoneId zonaDispositivo) { this.zonaDispositivo = zonaDispositivo; }

    public void storePunch(BioNovaPunch p) { memoria.add(p); }

    /** fromDate y toDate en formato "yyyyMMdd" (inclusive). */
    public BioNovaPunch[] fetchPunches(int empCode, String fromDate, String toDate) {
        LocalDate desde = LocalDate.parse(fromDate, DateTimeFormatter.BASIC_ISO_DATE);
        LocalDate hasta = LocalDate.parse(toDate, DateTimeFormatter.BASIC_ISO_DATE);
        return memoria.stream()
                .filter(p -> p.getEmpCode() == empCode)
                .filter(p -> {
                    LocalDate d = Instant.ofEpochSecond(p.getPunchTime())
                            .atZone(zonaDispositivo).toLocalDate();
                    return !d.isBefore(desde) && !d.isAfter(hasta);
                })
                .toArray(BioNovaPunch[]::new);
    }
}

// =====================================================================
// ADAPTER: implementa el contrato institucional y delega en el SDK,
// realizando las transformaciones necesarias entre ambos componentes.
// =====================================================================
class AdaptadorBioNova implements ServicioAsistencia {
    private final BioNovaSDK sdk;  // composición: adaptador de objetos
    private final ZoneId zona;     // zona horaria del dispositivo (inyectada)

    public AdaptadorBioNova(BioNovaSDK sdk, ZoneId zona) {
        this.sdk = Objects.requireNonNull(sdk);
        this.zona = Objects.requireNonNull(zona);
    }

    @Override
    public Optional<RegistroAsistencia> obtenerRegistro(String idColaborador, LocalDate fecha) {
        // 1. Transformar parámetros: String -> int, LocalDate -> "yyyyMMdd"
        int empCode = convertirCodigo(idColaborador);
        String dia = fecha.format(DateTimeFormatter.BASIC_ISO_DATE);

        // 2. Invocar la operación propia del fabricante
        BioNovaPunch[] marcaciones = sdk.fetchPunches(empCode, dia, dia);
        if (marcaciones.length == 0) {
            return Optional.empty();
        }

        // 3. Transformar el resultado: colección de marcaciones -> asistencia diaria
        //    (primera entrada del día y última salida del día)
        LocalTime ingreso = null;
        LocalTime salida = null;
        for (BioNovaPunch p : marcaciones) {
            LocalTime hora = convertirHora(p.getPunchTime());
            if (p.getPunchType() == BioNovaPunch.CHECK_IN) {
                if (ingreso == null || hora.isBefore(ingreso)) ingreso = hora;
            } else if (p.getPunchType() == BioNovaPunch.CHECK_OUT) {
                if (salida == null || hora.isAfter(salida)) salida = hora;
            }
        }
        return Optional.of(new RegistroAsistencia(idColaborador, fecha, ingreso, salida));
    }

    private int convertirCodigo(String idColaborador) {
        try {
            return Integer.parseInt(idColaborador.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "El dispositivo BioNova requiere un identificador numérico: " + idColaborador, e);
        }
    }

    private LocalTime convertirHora(long epochSegundos) {
        return Instant.ofEpochSecond(epochSegundos).atZone(zona).toLocalTime();
    }
}

// =====================================================================
// CLIENT: el sistema institucional solo depende del contrato.
// No conoce BioNovaSDK, BioNovaPunch ni al proveedor actual.
// =====================================================================
class SistemaAsistenciaInstitucional {
    private final ServicioAsistencia servicio;

    public SistemaAsistenciaInstitucional(ServicioAsistencia servicio) {
        this.servicio = Objects.requireNonNull(servicio);
    }

    public String consultarAsistencia(String idColaborador, LocalDate fecha) {
        return servicio.obtenerRegistro(idColaborador, fecha)
                .map(RegistroAsistencia::toString)
                .orElse("Sin registros para " + idColaborador + " el " + fecha);
    }
}

// =====================================================================
// Ejemplo de ejecución
// =====================================================================
public class Asistencia {

    public static void main(String[] args) {
        LocalDate hoy = LocalDate.of(2026, 10, 19);
        ZoneId zona = ZoneId.of("America/Guayaquil");

        // --- Dispositivo del proveedor actual (ya compatible) ---
        DispositivoProveedorActual actual = new DispositivoProveedorActual();
        actual.registrar(new RegistroAsistencia("0912345678", hoy,
                LocalTime.of(7, 58), LocalTime.of(17, 1)));

        // --- Nuevo dispositivo del fabricante BioNova (interfaz distinta) ---
        BioNovaSDK sdk = new BioNovaSDK(zona);
        sdk.storePunch(new BioNovaPunch(1001, epoch(hoy, 8, 2, zona), BioNovaPunch.CHECK_IN));
        sdk.storePunch(new BioNovaPunch(1001, epoch(hoy, 12, 30, zona), BioNovaPunch.CHECK_OUT));
        sdk.storePunch(new BioNovaPunch(1001, epoch(hoy, 13, 30, zona), BioNovaPunch.CHECK_IN));
        sdk.storePunch(new BioNovaPunch(1001, epoch(hoy, 17, 5, zona), BioNovaPunch.CHECK_OUT));

        ServicioAsistencia adaptador = new AdaptadorBioNova(sdk, zona);

        // --- El mismo cliente funciona con ambos dispositivos sin cambios ---
        SistemaAsistenciaInstitucional conActual = new SistemaAsistenciaInstitucional(actual);
        SistemaAsistenciaInstitucional conBioNova = new SistemaAsistenciaInstitucional(adaptador);

        System.out.println("=== Proveedor actual ===");
        System.out.println(conActual.consultarAsistencia("0912345678", hoy));

        System.out.println("\n=== Nuevo fabricante BioNova (a través del adaptador) ===");
        System.out.println(conBioNova.consultarAsistencia("1001", hoy));
        System.out.println(conBioNova.consultarAsistencia("2002", hoy));

        // Un futuro proveedor solo requiere un nuevo AdaptadorXxx implements ServicioAsistencia
    }

    /** Auxiliar de la simulación: genera la marca de tiempo como lo haría el dispositivo. */
    private static long epoch(LocalDate fecha, int hora, int minuto, ZoneId zona) {
        return LocalDateTime.of(fecha, LocalTime.of(hora, minuto)).atZone(zona).toEpochSecond();
    }
}
