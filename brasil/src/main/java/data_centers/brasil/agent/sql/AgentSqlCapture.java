package data_centers.brasil.agent.sql;

import java.util.*;

/** Captures raw JDBC SQL only within an agent tool execution on the current thread. */
public final class AgentSqlCapture implements AutoCloseable {
    private static final ThreadLocal<AgentSqlCapture> ACTIVE = new ThreadLocal<>();
    private final AgentSqlCapture previous;
    private final List<String> statements = new ArrayList<>();

    private AgentSqlCapture() { previous = ACTIVE.get(); ACTIVE.set(this); }
    public static AgentSqlCapture iniciar() { return new AgentSqlCapture(); }
    static void executada(String sql) {
        var scope = ACTIVE.get();
        if (scope != null) scope.statements.add(sql);
    }
    public List<String> consultas() { return List.copyOf(statements); }
    @Override public void close() {
        if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
    }
}
