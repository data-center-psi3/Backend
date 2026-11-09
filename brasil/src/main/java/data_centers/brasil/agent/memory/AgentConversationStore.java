package data_centers.brasil.agent.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class AgentConversationStore {
    private final JdbcTemplate jdbc;
    public AgentConversationStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Stored(UUID id,String history,long version,java.time.Instant updatedAt) {}
    public Stored create(String owner) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO agent_conversation(id,owner_name,history_json,version,updated_at) VALUES (?,?,'[]',0,CURRENT_TIMESTAMP)",id,owner);
        return find(id,owner).orElseThrow();
    }
    public Optional<Stored> find(UUID id,String owner) {
        return jdbc.query("SELECT id,history_json,version,updated_at FROM agent_conversation WHERE id=? AND owner_name=?",
            (rs,n)->new Stored(rs.getObject("id",UUID.class),rs.getString("history_json"),rs.getLong("version"),rs.getTimestamp("updated_at").toInstant()),id,owner).stream().findFirst();
    }
    public List<Stored> list(String owner) {
        return jdbc.query("SELECT id,history_json,version,updated_at FROM agent_conversation WHERE owner_name=? AND version>0 ORDER BY updated_at DESC LIMIT 100",
            (rs,n)->new Stored(rs.getObject("id",UUID.class),rs.getString("history_json"),rs.getLong("version"),rs.getTimestamp("updated_at").toInstant()),owner);
    }
    public boolean save(Stored stored,String owner,String history) {
        return jdbc.update("UPDATE agent_conversation SET history_json=?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=? AND owner_name=? AND version=?",
            history,stored.id(),owner,stored.version())==1;
    }
}
