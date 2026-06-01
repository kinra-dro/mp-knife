package io.mpfluent.core;

import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * {@link KnifeOrm} 的工厂类，提供独立运行（无 Spring）和 DataSource 两种构建方式。
 *
 * <h3>独立使用</h3>
 * <pre>{@code
 * KnifeOrm orm = KnifeOrmFactory.builder()
 *         .driver("com.mysql.cj.jdbc.Driver")
 *         .url("jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC")
 *         .username("root")
 *         .password("secret")
 *         .poolMaxActive(10)
 *         .build();
 * }</pre>
 *
 * <h3>与外部 DataSource 集成（Spring Boot 等）</h3>
 * <pre>{@code
 * KnifeOrm orm = KnifeOrmFactory.fromDataSource(dataSource);
 * }</pre>
 */
public final class KnifeOrmFactory {

    private KnifeOrmFactory() {
    }

    /** 返回链式构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 使用已有的 {@link DataSource} 构建 {@link KnifeOrm}，适合 Spring Boot 等托管场景。
     *
     * @param dataSource 外部数据源
     * @return {@link KnifeOrm} 实例
     */
    public static KnifeOrm fromDataSource(DataSource dataSource) {
        return fromDataSource(dataSource, false);
    }

    /**
     * 使用已有的 {@link DataSource} 构建 {@link KnifeOrm}。
     *
     * @param dataSource 外部数据源
     * @param enableLog  是否开启 SQL 日志输出
     * @return {@link KnifeOrm} 实例
     */
    public static KnifeOrm fromDataSource(DataSource dataSource, boolean enableLog) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        KnifeFluent.setLogEnabled(enableLog);
        Environment env = new Environment("default", new JdbcTransactionFactory(), dataSource);
        Configuration config = buildConfiguration(env);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
        return new KnifeOrm(factory);
    }

    // -------------------------------------------------------------------------

    private static Configuration buildConfiguration(Environment env) {
        Configuration config = new Configuration(env);
        config.setMapUnderscoreToCamelCase(true);
        config.setUseGeneratedKeys(true);
        config.setCacheEnabled(false);
        return config;
    }

    // -------------------------------------------------------------------------

    public static final class Builder {

        private String driver;
        private String url;
        private String username = "";
        private String password = "";
        private int poolMaxActive     = 10;
        private int poolMaxIdle       = 5;
        private int poolMaxWaitMs     = 20_000;
        private int connectionTimeout = 5_000;
        private boolean enableLog      = false;

        private Builder() {
        }

        /** JDBC 驱动全类名，例如 {@code com.mysql.cj.jdbc.Driver}。 */
        public Builder driver(String driver) {
            this.driver = Objects.requireNonNull(driver);
            return this;
        }

        /** JDBC 连接 URL。 */
        public Builder url(String url) {
            this.url = Objects.requireNonNull(url);
            return this;
        }

        /** 数据库用户名。 */
        public Builder username(String username) {
            this.username = Objects.requireNonNull(username);
            return this;
        }

        /** 数据库密码。 */
        public Builder password(String password) {
            this.password = Objects.requireNonNull(password);
            return this;
        }

        /** 连接池最大活跃连接数，默认 {@code 10}。 */
        public Builder poolMaxActive(int max) {
            this.poolMaxActive = max;
            return this;
        }

        /** 连接池最大空闲连接数，默认 {@code 5}。 */
        public Builder poolMaxIdle(int max) {
            this.poolMaxIdle = max;
            return this;
        }

        /** 等待连接的最大毫秒数，默认 {@code 20000}。 */
        public Builder poolMaxWaitMs(int ms) {
            this.poolMaxWaitMs = ms;
            return this;
        }

        /** 建立连接的超时毫秒数，默认 {@code 5000}。 */
        public Builder connectionTimeout(int ms) {
            this.connectionTimeout = ms;
            return this;
        }

        /**
         * 开启 SQL 日志输出，默认关闭。
         * 开启后每次执行前输出 SQL 语句和参数，格式参考 MyBatis。
         */
        public Builder enableLog(boolean enable) {
            this.enableLog = enable;
            return this;
        }

        /**
         * 构建 {@link KnifeOrm} 实例，内部使用 MyBatis 连接池。
         *
         * @throws IllegalStateException 若 {@code driver} 或 {@code url} 未设置
         */
        public KnifeOrm build() {
            Objects.requireNonNull(driver, "driver must not be null");
            Objects.requireNonNull(url, "url must not be null");

            PooledDataSource ds = new PooledDataSource(driver, url, username, password);
            ds.setPoolMaximumActiveConnections(poolMaxActive);
            ds.setPoolMaximumIdleConnections(poolMaxIdle);
            ds.setPoolMaximumCheckoutTime(poolMaxWaitMs);
            ds.setPoolTimeToWait(connectionTimeout);

            Environment env = new Environment("default", new JdbcTransactionFactory(), ds);
            Configuration config = buildConfiguration(env);
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
            KnifeFluent.setLogEnabled(enableLog);
            return new KnifeOrm(factory);
        }
    }
}
