package io.mpfluent.core;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 持有数据库连接工厂，为每个实体类创建 {@link KnifeFluent} 查询链。
 *
 * <p>通过 {@link KnifeOrmFactory} 或 {@link io.mpfluent.Knife#init} 构建实例。
 *
 * <h3>普通模式（每次操作独立 Session）</h3>
 * <pre>{@code
 * KnifeOrm orm = KnifeOrmFactory.builder()
 *         .driver("com.mysql.cj.jdbc.Driver")
 *         .url("jdbc:mysql://localhost:3306/mydb")
 *         .username("root").password("secret")
 *         .build();
 *
 * orm.of(User.class).eq("status", 1).list();
 * }</pre>
 *
 * <h3>事务模式（所有操作共享同一 Session）</h3>
 * <pre>{@code
 * orm.transact(tx -> {
 *     tx.of(User.class).insert(user);
 *     tx.of(Order.class).insert(order);
 * });
 * }</pre>
 */
public class KnifeOrm {

    /** 普通模式下的连接工厂，事务模式下为 {@code null}。 */
    private final SqlSessionFactory factory;

    /** 事务模式下的共享 Session，普通模式下为 {@code null}。 */
    private final SqlSession txSession;

    // -------------------------------------------------------------------------
    // 构造
    // -------------------------------------------------------------------------

    /**
     * 普通模式：每次操作自动开启/关闭独立 Session。
     */
    public KnifeOrm(SqlSessionFactory factory) {
        this.factory   = Objects.requireNonNull(factory, "SqlSessionFactory must not be null");
        this.txSession = null;
    }

    /**
     * 事务模式（仅供框架内部使用）：所有操作共享传入的 {@link SqlSession}，
     * 提交/回滚由调用方（{@link #transact}）负责。
     */
    KnifeOrm(SqlSession txSession) {
        this.txSession = Objects.requireNonNull(txSession, "SqlSession must not be null");
        this.factory   = null;
    }

    // -------------------------------------------------------------------------
    // 查询链入口
    // -------------------------------------------------------------------------

    /**
     * 为指定实体类创建链式构造器。每次调用返回独立实例，线程安全。
     *
     * @param entityClass 实体类型
     * @param <T>         实体泛型
     */
    public <T> KnifeFluent<T> of(Class<T> entityClass) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        return txSession != null
                ? new KnifeFluent<>(txSession, entityClass)
                : new KnifeFluent<>(factory, entityClass);
    }

    // -------------------------------------------------------------------------
    // 事务
    // -------------------------------------------------------------------------

    /**
     * 在单个事务内执行多个操作（无返回值）。
     * 所有通过参数 {@code tx} 发起的操作共享同一 {@link SqlSession}。
     * 操作块抛出任何异常时自动回滚。
     */
    public void transact(Consumer<KnifeOrm> block) {
        transact(tx -> { block.accept(tx); return null; });
    }

    /**
     * 在单个事务内执行多个操作（有返回值）。
     */
    public <R> R transact(Function<KnifeOrm, R> block) {
        if (txSession != null) {
            // 已在事务上下文中，直接委托，不嵌套提交
            return block.apply(this);
        }
        try (SqlSession session = factory.openSession(false)) {
            try {
                R result = block.apply(new KnifeOrm(session));
                session.commit();
                return result;
            } catch (Exception e) {
                session.rollback();
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // 访问器
    // -------------------------------------------------------------------------

    /**
     * 返回底层 {@link SqlSessionFactory}。
     * 事务模式下返回 {@code null}；普通模式下始终非 {@code null}。
     */
    public SqlSessionFactory getFactory() {
        return factory;
    }
}
