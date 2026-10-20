package data_centers.brasil.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CargaLockRepository {
    private final JdbcTemplate jdbc;

    /** Lock transacional compartilhado entre instâncias PostgreSQL; liberado no commit/rollback. */
    public void acquire() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            if ("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                    statement.setLong(1, 721390220260922L);
                    statement.execute();
                }
            }
            return null;
        });
    }
}
