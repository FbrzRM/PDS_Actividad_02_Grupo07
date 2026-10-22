import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Patrón Composite aplicado a una estructura presupuestaria jerárquica.
 *
 * Component : ElementoPresupuestario
 * Leaf      : PartidaPresupuestaria
 * Composite : GrupoPresupuestario
 * Client    : Presupuesto (main)
 */

// ===================== COMPONENTE =====================
interface ElementoPresupuestario {
    String getCodigo();
    String getDescripcion();
    BigDecimal calcularValorTotal();
    String mostrarEstructura(int nivel);   // retorna el texto; el cliente decide dónde mostrarlo
}

// ===================== HOJA =====================
class PartidaPresupuestaria implements ElementoPresupuestario {
    private final String codigo;
    private final String descripcion;
    private final BigDecimal valorAsignado;

    public PartidaPresupuestaria(String codigo, String descripcion, BigDecimal valorAsignado) {
        this.codigo = Objects.requireNonNull(codigo);
        this.descripcion = Objects.requireNonNull(descripcion);
        if (valorAsignado == null || valorAsignado.signum() < 0) {
            throw new IllegalArgumentException("El valor asignado debe ser mayor o igual a cero");
        }
        this.valorAsignado = valorAsignado;
    }

    @Override public String getCodigo() { return codigo; }
    @Override public String getDescripcion() { return descripcion; }

    // En una hoja, el valor total es directamente el valor asignado
    @Override
    public BigDecimal calcularValorTotal() {
        return valorAsignado;
    }

    @Override
    public String mostrarEstructura(int nivel) {
        return String.format("%s- [%s] %s: $%,.2f%n",
                "    ".repeat(nivel), codigo, descripcion, valorAsignado);
    }
}

// ===================== COMPUESTO =====================
class GrupoPresupuestario implements ElementoPresupuestario {
    private final String codigo;
    private final String descripcion;
    private final List<ElementoPresupuestario> elementos = new ArrayList<>();

    public GrupoPresupuestario(String codigo, String descripcion) {
        this.codigo = Objects.requireNonNull(codigo);
        this.descripcion = Objects.requireNonNull(descripcion);
    }

    public void agregar(ElementoPresupuestario elemento) {
        if (elemento == this) {
            throw new IllegalArgumentException("Un grupo no puede contenerse a sí mismo");
        }
        elementos.add(Objects.requireNonNull(elemento));
    }

    public void eliminar(ElementoPresupuestario elemento) {
        elementos.remove(elemento);
    }

    public List<ElementoPresupuestario> getElementos() {
        return Collections.unmodifiableList(elementos);
    }

    @Override public String getCodigo() { return codigo; }
    @Override public String getDescripcion() { return descripcion; }

    // En un grupo, el valor total es la suma recursiva de sus elementos
    @Override
    public BigDecimal calcularValorTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (ElementoPresupuestario e : elementos) {
            total = total.add(e.calcularValorTotal());
        }
        return total;
    }

    // Construye su propia línea y concatena recursivamente la estructura de sus hijos
    @Override
    public String mostrarEstructura(int nivel) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s+ [%s] %s (Total: $%,.2f)%n",
                "    ".repeat(nivel), codigo, descripcion, calcularValorTotal()));
        for (ElementoPresupuestario e : elementos) {
            sb.append(e.mostrarEstructura(nivel + 1));
        }
        return sb.toString();
    }
}

// ===================== CLIENTE =====================
public class Presupuesto {

    // El cliente trabaja solo con la abstracción: no distingue hojas de grupos
    static void imprimirResumen(ElementoPresupuestario elemento) {
        System.out.printf("%n%s - %s | Valor total: $%,.2f%n",
                elemento.getCodigo(), elemento.getDescripcion(), elemento.calcularValorTotal());
    }

    public static void main(String[] args) {
        // Partidas individuales (hojas)
        PartidaPresupuestaria licencias = new PartidaPresupuestaria(
                "TI-01", "Licenciamiento de software", new BigDecimal("45000"));
        PartidaPresupuestaria servicios = new PartidaPresupuestaria(
                "TI-03", "Servicios de soporte y nube", new BigDecimal("30000"));
        PartidaPresupuestaria servidores = new PartidaPresupuestaria(
                "TI-02-01", "Servidores", new BigDecimal("60000"));
        PartidaPresupuestaria almacenamiento = new PartidaPresupuestaria(
                "TI-02-02", "Almacenamiento", new BigDecimal("25000"));
        PartidaPresupuestaria redes = new PartidaPresupuestaria(
                "TI-02-03-01", "Redes LAN/WAN", new BigDecimal("18000"));
        PartidaPresupuestaria telefonia = new PartidaPresupuestaria(
                "TI-02-03-02", "Telefonía IP", new BigDecimal("7000"));

        // Grupos (compuestos), con varios niveles
        GrupoPresupuestario comunicaciones = new GrupoPresupuestario("TI-02-03", "Comunicaciones");
        comunicaciones.agregar(redes);
        comunicaciones.agregar(telefonia);

        GrupoPresupuestario infraestructura = new GrupoPresupuestario("TI-02", "Infraestructura");
        infraestructura.agregar(servidores);
        infraestructura.agregar(almacenamiento);
        infraestructura.agregar(comunicaciones); // grupo dentro de grupo

        GrupoPresupuestario ti = new GrupoPresupuestario("TI", "Tecnologías de la Información");
        ti.agregar(licencias);
        ti.agregar(infraestructura);
        ti.agregar(servicios);

        GrupoPresupuestario presupuestoGeneral = new GrupoPresupuestario("PG-2026", "Presupuesto General 2026");
        presupuestoGeneral.agregar(ti);
        presupuestoGeneral.agregar(new PartidaPresupuestaria(
                "ADM-01", "Gastos administrativos", new BigDecimal("40000")));

        // Mostrar la estructura completa
        System.out.println("=== ESTRUCTURA DEL PRESUPUESTO ===");
        System.out.print(presupuestoGeneral.mostrarEstructura(0));

        // Mismo tratamiento para hojas y grupos
        imprimirResumen(presupuestoGeneral);
        imprimirResumen(infraestructura);
        imprimirResumen(licencias);
    }
}
