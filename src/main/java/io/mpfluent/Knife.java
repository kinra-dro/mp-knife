package io.mpfluent;

import io.mpfluent.core.KnifeFluent;
import io.mpfluent.core.KnifeOrm;
import io.mpfluent.core.KnifeOrmFactory;
import org.apache.ibatis.session.SqlSessionFactory;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * mp-knife 全局静态入口。
 *
 * <h3>初始化（任选其一）</h3>
 *
 * <pre>{@code
 * // 1. 独立运行：通过 Builder 自建连接池
 * Knife.init(
 *         Knife.builder()
 *                 .driver("com.mysql.cj.jdbc.Driver")
 *                 .url("jdbc:mysql://localhost:3306/mydb")
 *                 .username("root")
 *                 .password("secret")
 *                 .build());
 *
 * // 2. Spring Boot：注入已有 DataSource
 * Knife.init(dataSource);
 *
 * // 3. Spring Boot：注入已有 SqlSessionFactory
 * Knife.init(sqlSessionFactory);
 * }</pre>
 *
 * <h3>查询</h3>
 *
 * <pre>{@code
 * List<User> list = Knife.of(User.class)
 *         .eq("status", 1)
 *         .like("name", "张")
 *         .orderByDesc("create_time")
 *         .limit(20)
 *         .list();
 *
 * User user = Knife.of(User.class).getById(1L);
 * long total = Knife.of(User.class).eq("dept_id", 10).count();
 * }</pre>
 *
 * <h3>写入</h3>
 *
 * <pre>{@code
 * Knife.of(User.class).insert(user);
 * Knife.of(User.class).updateById(user);
 * Knife.of(User.class).saveOrUpdate(user);
 * Knife.of(User.class).deleteById(1L);
 * Knife.of(User.class).eq("status", 0).set("remark", "禁用").update();
 * }</pre>
 *
 * <h3>分片</h3>
 *
 * <pre>{@code
 * Knife.of(User.class).shard(userId, 16).getById(userId);
 * }</pre>
 *
 * <h3>事务</h3>
 *
 * <pre>{@code
 * Knife.transact(orm -> {
 *     orm.of(User.class).insert(user);
 *     orm.of(Order.class).insert(order);
 * });
 * }</pre>
 */
public final class Knife {

    private static volatile KnifeOrm instance;

    private Knife() {
    }

    // =========================================================================
    // 初始化
    // =========================================================================

    /**
     * 以已构建的 {@link KnifeOrm} 实例初始化全局上下文。
     * Spring 环境推荐将 {@link KnifeOrm} 声明为 Bean 后调用此方法。
     */
    public static void init(KnifeOrm orm) {
        instance = Objects.requireNonNull(orm, "KnifeOrm must not be null");
    }

    /**
     * 以已有的 {@link SqlSessionFactory} 初始化，适合直接集成 MyBatis Spring。
     */
    public static void init(SqlSessionFactory factory) {
        instance = new KnifeOrm(Objects.requireNonNull(factory, "SqlSessionFactory must not be null"));
    }

    /**
     * 以外部 {@link DataSource} 初始化，适合 Spring Boot 数据源托管场景。
     */
    public static void init(DataSource dataSource) {
        instance = KnifeOrmFactory.fromDataSource(
                Objects.requireNonNull(dataSource, "DataSource must not be null"));
    }

    // =========================================================================
    // 日志
    // =========================================================================

    /**
     * 全局 SQL 日志开关。
     * 开启后每次执行前输出 SQL 语句和参数，格式参考 MyBatis：
     * <pre>
     * ==>  Preparing: SELECT id, name FROM user WHERE status = ?
     * ==> Parameters: 1(Integer)
     * </pre>
     * 日志通过 {@code java.util.logging}（JUL）输出，Logger 名称为 {@code io.mpfluent}。
     * 如需集成 SLF4J 等框架，配置 jul-to-slf4j 桥接即可。
     */
    public static void enableLog(boolean enable) {
        KnifeFluent.setLogEnabled(enable);
    }

    // =========================================================================
    // 构建器快捷入口
    // =========================================================================

    /**
     * 返回独立运行模式的连接池构建器（无 Spring 依赖）。
     *
     * <pre>{@code
     * Knife.init(
     *         Knife.builder()
     *                 .driver("com.mysql.cj.jdbc.Driver")
     *                 .url("jdbc:mysql://localhost:3306/mydb")
     *                 .username("root").password("secret")
     *                 .build());
     * }</pre>
     */
    public static KnifeOrmFactory.Builder builder() {
        return KnifeOrmFactory.builder();
    }

    // =========================================================================
    // 查询入口
    // =========================================================================

    /**
     * 为指定实体类创建链式查询/写入构造器。
     * 每次调用返回独立的 {@link KnifeFluent} 实例，线程安全。
     *
     * @param entityClass 实体类型
     * @param <T>         实体泛型
     */
    public static <T> KnifeFluent<T> of(Class<T> entityClass) {
        return orm().of(entityClass);
    }

    // =========================================================================
    // 事务
    // =========================================================================

    /**
     * 在单个事务内执行多个操作（无返回值版本）。
     * 所有通过参数 {@code orm} 发起的操作共享同一 {@link org.apache.ibatis.session.SqlSession}。
     * 抛出任何异常时自动回滚。
     *
     * <pre>{@code
     * Knife.transact(orm -> {
     *     orm.of(User.class).insert(user);
     *     orm.of(Order.class).insert(order);
     * });
     * }</pre>
     */
    public static void transact(Consumer<KnifeOrm> block) {
        orm().transact(block);
    }

    /**
     * 在单个事务内执行多个操作（有返回值版本）。
     *
     * <pre>{@code
     * Long newId = Knife.transact(orm -> {
     *     orm.of(User.class).insert(user);
     *     return user.getId();
     * });
     * }</pre>
     */
    public static <R> R transact(Function<KnifeOrm, R> block) {
        return orm().transact(block);
    }

    // =========================================================================
    // 访问全局实例
    // =========================================================================

    /**
     * 返回全局 {@link KnifeOrm} 实例。
     *
     * @throws IllegalStateException 若尚未调用任何 {@code init()} 方法
     */
    public static KnifeOrm orm() {
        KnifeOrm orm = instance;
        if (orm == null) {
            throw new IllegalStateException(
                    "Knife has not been initialized. Call Knife.init(...) before use.");
        }
        return orm;
    }
}
