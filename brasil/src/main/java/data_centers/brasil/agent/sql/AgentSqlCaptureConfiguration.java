package data_centers.brasil.agent.sql;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;

@Configuration(proxyBeanMethods = false)
public class AgentSqlCaptureConfiguration {
    @Bean
    static BeanPostProcessor agentSqlDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                return bean instanceof DataSource source && !(bean instanceof CapturingDataSource)
                    ? new CapturingDataSource(source) : bean;
            }
        };
    }

    static final class CapturingDataSource extends DelegatingDataSource implements AutoCloseable {
        CapturingDataSource(DataSource target) { super(target); }
        @Override public Connection getConnection() throws SQLException { return connection(super.getConnection()); }
        @Override public Connection getConnection(String user, String password) throws SQLException {
            return connection(super.getConnection(user, password));
        }
        @Override public void close() throws Exception {
            if (getTargetDataSource() instanceof AutoCloseable closeable) closeable.close();
        }
    }

    private static Connection connection(Connection target) {
        return (Connection) Proxy.newProxyInstance(AgentSqlCaptureConfiguration.class.getClassLoader(), new Class<?>[]{Connection.class},
            (proxy, method, args) -> {
                Object result = invoke(target, method, args);
                if (result instanceof Statement statement && (method.getName().equals("prepareStatement")
                        || method.getName().equals("prepareCall") || method.getName().equals("createStatement"))) {
                    String sql = args != null && args.length > 0 && args[0] instanceof String s ? s : null;
                    return statement(statement, sql);
                }
                return result;
            });
    }
    private static Statement statement(Statement target, String preparedSql) {
        Class<?> type = target instanceof CallableStatement ? CallableStatement.class
            : target instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        return (Statement) Proxy.newProxyInstance(AgentSqlCaptureConfiguration.class.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> {
                Object result = invoke(target, method, args);
                if (method.getName().startsWith("execute")) {
                    String sql = preparedSql != null ? preparedSql
                        : args != null && args.length > 0 && args[0] instanceof String s ? s : null;
                    if (sql != null) AgentSqlCapture.executada(sql);
                }
                return result;
            });
    }
    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException ex) { throw ex.getCause(); }
    }
}
