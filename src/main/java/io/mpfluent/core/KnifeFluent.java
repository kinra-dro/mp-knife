package io.mpfluent.core;

import io.mpfluent.annotation.TableField;
import io.mpfluent.annotation.TableId;
import io.mpfluent.annotation.TableName;
import org.apache.ibatis.jdbc.SqlRunner;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 链式查询 / 写入构造器，通过 {@link KnifeOrm#of(Class)} 获取新实例。
 *
 * <p>
 * <strong>每个实例仅供单次使用，不可跨线程复用。</strong>
 *
 * <h3>查询</h3>
 *
 * <pre>{@code
 * List<User> list = orm.of(User.class)
 *         .eq("status", 1)
 *         .like("name", "张")
 *         .orderByDesc("create_time")
 *         .page(1, 20)
 *         .list();
 * }</pre>
 *
 * <h3>原生 WHERE 片段</h3>
 *
 * <pre>{@code
 * orm.of(User.class).where("id = ?", 1L).first();
 * orm.of(User.class).where("age > ? AND status = ?", 18, 1).list();
 * }</pre>
 *
 * <h3>批量写入</h3>
 *
 * <pre>{@code
 * orm.of(User.class).insertBatch(userList);
 * }</pre>
 */
public class KnifeFluent<T> {

    // =========================================================================
    // 日志
    // =========================================================================

    private static final Logger LOG = Logger.getLogger("io.mpfluent");
    private static volatile boolean SQL_LOG = false;

    /**
     * 全局 SQL 日志开关。开启后每次执行前输出 SQL 语句和参数，格式参考 MyBatis：
     * 
     * <pre>
     * ==>  Preparing: SELECT id, name FROM user WHERE status = ?
     * ==> Parameters: 1(Integer)
     * </pre>
     * 
     * 通过 {@link io.mpfluent.Knife#enableLog(boolean)} 或
     * {@link KnifeOrmFactory.Builder#enableLog(boolean)} 设置。
     */
    public static void setLogEnabled(boolean enabled) {
        SQL_LOG = enabled;
    }

    private static void logSql(String sql, List<Object> params) {
        if (!SQL_LOG)
            return;
        LOG.info("==>  Preparing: " + sql);
        if (params != null && !params.isEmpty()) {
            String paramStr = params.stream()
                    .map(v -> v == null ? "null" : v + "(" + v.getClass().getSimpleName() + ")")
                    .collect(Collectors.joining(", "));
            LOG.info("==> Parameters: " + paramStr);
        }
    }

    // =========================================================================

    /** 普通模式工厂，事务模式下为 {@code null}。 */
    private final SqlSessionFactory factory;
    /** 事务模式共享 Session，普通模式下为 {@code null}。 */
    private final SqlSession txSession;

    private final Class<T> entityClass;
    private final TableMeta meta;

    private final List<Condition> conditions = new ArrayList<>();
    private final List<String[]> sorts = new ArrayList<>();
    private final Map<String, Object> setValues = new LinkedHashMap<>();
    private List<String> selectColumns;
    private int limitVal = -1;
    private int offsetVal = -1;

    // -------------------------------------------------------------------------
    // 构造（package-private，外部通过 KnifeOrm.of() 获取）
    // -------------------------------------------------------------------------

    KnifeFluent(SqlSessionFactory factory, Class<T> entityClass) {
        this.factory = factory;
        this.txSession = null;
        this.entityClass = entityClass;
        this.meta = TableMeta.resolve(entityClass);
    }

    KnifeFluent(SqlSession txSession, Class<T> entityClass) {
        this.factory = null;
        this.txSession = txSession;
        this.entityClass = entityClass;
        this.meta = TableMeta.resolve(entityClass);
    }

    // =========================================================================
    // 条件过滤 — 类型安全方法
    // =========================================================================

    /** 等于 {@code column = value}。 */
    public KnifeFluent<T> eq(String column, Object value) {
        return cond(column, "=", value);
    }

    /** 不等于 {@code column != value}。 */
    public KnifeFluent<T> ne(String column, Object value) {
        return cond(column, "!=", value);
    }

    /** 大于 {@code column > value}。 */
    public KnifeFluent<T> gt(String column, Object value) {
        return cond(column, ">", value);
    }

    /** 大于等于 {@code column >= value}。 */
    public KnifeFluent<T> ge(String column, Object value) {
        return cond(column, ">=", value);
    }

    /** 小于 {@code column < value}。 */
    public KnifeFluent<T> lt(String column, Object value) {
        return cond(column, "<", value);
    }

    /** 小于等于 {@code column <= value}。 */
    public KnifeFluent<T> le(String column, Object value) {
        return cond(column, "<=", value);
    }

    /** {@code column LIKE '%keyword%'}。 */
    public KnifeFluent<T> like(String column, String keyword) {
        return cond(column, "LIKE", keyword);
    }

    /** {@code column IN (v1, v2, ...)}。 */
    public KnifeFluent<T> in(String column, Collection<?> values) {
        return cond(column, "IN", values);
    }

    /** {@code column IS NULL}。 */
    public KnifeFluent<T> isNull(String column) {
        return cond(column, "IS NULL", null);
    }

    /** {@code column IS NOT NULL}。 */
    public KnifeFluent<T> isNotNull(String column) {
        return cond(column, "IS NOT NULL", null);
    }

    private KnifeFluent<T> cond(String column, String op, Object value) {
        conditions.add(new Condition(column, op, value));
        return this;
    }

    // =========================================================================
    // 条件过滤 — 原生 SQL 片段
    // =========================================================================

    /**
     * 追加一段原生 SQL 条件，占位符使用标准 JDBC {@code ?} 语法。
     * 多个 {@code where()} 调用之间以 {@code AND} 拼接。
     *
     * <pre>{@code
     * .where("id = ?", 1L)
     * .where("age > ? AND status = ?", 18, 1)
     * .where("create_time BETWEEN ? AND ?", start, end)
     * .where("name IS NOT NULL")
     * }</pre>
     *
     * @param condition SQL 条件片段，{@code ?} 为参数占位符
     * @param params    按顺序对应条件中每个 {@code ?} 的值
     */
    public KnifeFluent<T> where(String condition, Object... params) {
        conditions.add(new Condition(condition, "RAW", params));
        return this;
    }

    // =========================================================================
    // 排序 / 分页 / 投影 / 暂存 SET
    // =========================================================================

    /** 升序排序。 */
    public KnifeFluent<T> orderByAsc(String column) {
        sorts.add(new String[] { column, "ASC" });
        return this;
    }

    /** 降序排序。 */
    public KnifeFluent<T> orderByDesc(String column) {
        sorts.add(new String[] { column, "DESC" });
        return this;
    }

    /** 限制返回行数。 */
    public KnifeFluent<T> limit(int limit) {
        this.limitVal = limit;
        return this;
    }

    /** 跳过前 N 行。 */
    public KnifeFluent<T> offset(int offset) {
        this.offsetVal = offset;
        return this;
    }

    /**
     * 页码分页，{@code pageNum} 从 1 开始。
     * 等价于 {@code offset((pageNum-1)*pageSize).limit(pageSize)}。
     */
    public KnifeFluent<T> page(int pageNum, int pageSize) {
        this.offsetVal = (pageNum - 1) * pageSize;
        this.limitVal = pageSize;
        return this;
    }

    /** 仅查询指定列，覆盖默认全列 SELECT。 */
    public KnifeFluent<T> select(String... columns) {
        this.selectColumns = Arrays.asList(columns);
        return this;
    }

    /** 暂存 SET 赋值，配合 {@link #update()} 使用，可链式调用多个。 */
    public KnifeFluent<T> set(String column, Object value) {
        setValues.put(column, value);
        return this;
    }

    // =========================================================================
    // 终端操作 — 读
    // =========================================================================

    /** 返回满足条件的所有行，映射为实体列表。 */
    public List<T> list() {
        List<Object> params = new ArrayList<>();
        String sql = buildSelect(params);
        logSql(sql, params);
        return withRead(session -> {
            SqlRunner runner = new SqlRunner(session.getConnection());
            return mapResults(runner.selectAll(sql, params.toArray()));
        });
    }

    /**
     * 返回第一条匹配记录，无结果时返回 {@code null}。
     * 内部自动追加 {@code LIMIT 1}。
     */
    public T first() {
        limitVal = 1;
        List<T> results = list();
        return results.isEmpty() ? null : results.get(0);
    }

    /** 返回满足条件的行数。 */
    public long count() {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(params);
        String sql = "SELECT COUNT(*) FROM " + meta.tableName + where;
        logSql(sql, params);
        return withRead(session -> {
            SqlRunner runner = new SqlRunner(session.getConnection());
            Map<String, Object> row = runner.selectOne(sql, params.toArray());
            return ((Number) row.values().iterator().next()).longValue();
        });
    }

    /** 是否存在满足条件的记录。 */
    public boolean exists() {
        return count() > 0;
    }

    /** 按主键查询单条记录，无结果时返回 {@code null}。 */
    public T getById(Object id) {
        return eq(meta.idColumn, id).first();
    }

    // =========================================================================
    // 终端操作 — 写
    // =========================================================================

    /**
     * 插入一条实体记录，并根据 {@link TableId#type()} 策略处理主键：
     * <ul>
     * <li>{@code AUTO}：由数据库自增，插入后主键值回填到 entity。</li>
     * <li>{@code UUID}：框架生成 32 位 UUID 并回填。</li>
     * <li>{@code SNOWFLAKE}：框架生成雪花 ID 并回填。</li>
     * <li>{@code NONE}：调用方自行赋值。</li>
     * </ul>
     */
    public boolean insert(T entity) {
        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        boolean autoId = meta.idType == TableId.IdType.AUTO;

        for (ColumnMeta col : meta.columns) {
            if (!col.insertable)
                continue;

            if (col.isId) {
                if (autoId)
                    continue;
                Object idVal = getFieldValue(col.field, entity);
                if (isEmpty(idVal)) {
                    idVal = generateId(meta.idType);
                    if (idVal != null)
                        setFieldValue(col.field, entity, idVal);
                }
                if (!isEmpty(idVal)) {
                    cols.add(col.columnName);
                    vals.add(idVal);
                }
                continue;
            }

            if (col.version) {
                cols.add(col.columnName);
                vals.add(0L);
                setFieldValue(col.field, entity, 0L);
                continue;
            }

            if (col.fill == TableField.FillStrategy.INSERT
                    || col.fill == TableField.FillStrategy.INSERT_UPDATE) {
                Object filled = fillNow(col.field.getType());
                if (filled != null) {
                    cols.add(col.columnName);
                    vals.add(filled);
                    setFieldValue(col.field, entity, filled);
                    continue;
                }
            }

            Object val = getFieldValue(col.field, entity);
            if (val == null)
                continue;
            cols.add(col.columnName);
            vals.add(val);
        }

        if (cols.isEmpty())
            throw new IllegalStateException(
                    "No insertable columns for " + entityClass.getSimpleName());

        String sql = "INSERT INTO " + meta.tableName + " ("
                + String.join(", ", cols) + ") VALUES ("
                + String.join(", ", Collections.nCopies(cols.size(), "?")) + ")";

        logSql(sql, vals);
        return withWrite(session -> {
            Connection conn = session.getConnection();
            try (PreparedStatement ps = autoId
                    ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                    : conn.prepareStatement(sql)) {
                for (int i = 0; i < vals.size(); i++)
                    ps.setObject(i + 1, vals.get(i));
                int rows = ps.executeUpdate();
                if (autoId && meta.idField != null) {
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next())
                            setFieldValue(meta.idField, entity, keys.getObject(1));
                    }
                }
                return rows > 0;
            }
        });
    }

    /**
     * 批量插入，所有记录在同一 Session 中执行（共享事务）。
     *
     * @return 成功插入的行数
     */
    public int insertBatch(List<T> entities) {
        if (entities == null || entities.isEmpty())
            return 0;
        return withWrite(session -> {
            int count = 0;
            for (T entity : entities) {
                if (new KnifeFluent<>(session, entityClass).insert(entity))
                    count++;
            }
            return count;
        });
    }

    /**
     * 按主键更新实体中的非空字段。
     * 乐观锁字段自动校验并自增；自动填充字段写入当前时间戳。
     *
     * @return 更新成功返回 {@code true}；版本冲突或记录不存在返回 {@code false}
     */
    public boolean updateById(T entity) {
        if (meta.idField == null)
            throw new IllegalStateException(
                    entityClass.getSimpleName() + " has no @TableId field");
        Object idVal = getFieldValue(meta.idField, entity);
        if (idVal == null)
            throw new IllegalStateException("updateById: id value is null");

        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (ColumnMeta col : meta.columns) {
            if (col.isId || !col.updatable)
                continue;
            if (col.version) {
                Object ver = getFieldValue(col.field, entity);
                if (ver != null) {
                    conditions.add(new Condition(col.columnName, "=", ver));
                    sets.add(col.columnName + " = " + col.columnName + " + 1");
                }
                continue;
            }
            if (col.fill == TableField.FillStrategy.UPDATE
                    || col.fill == TableField.FillStrategy.INSERT_UPDATE) {
                Object filled = fillNow(col.field.getType());
                if (filled != null) {
                    sets.add(col.columnName + " = ?");
                    params.add(filled);
                    continue;
                }
            }
            Object val = getFieldValue(col.field, entity);
            if (val == null)
                continue;
            sets.add(col.columnName + " = ?");
            params.add(val);
        }
        if (sets.isEmpty())
            return false;

        conditions.add(new Condition(meta.idColumn, "=", idVal));
        String where = buildWhere(params);
        return executeWrite("UPDATE " + meta.tableName + " SET "
                + String.join(", ", sets) + where, params) > 0;
    }

    /**
     * 批量按主键更新，所有操作在同一 Session 中执行。
     *
     * @return 成功更新的行数
     */
    public int updateBatchById(List<T> entities) {
        if (entities == null || entities.isEmpty())
            return 0;
        return withWrite(session -> {
            int count = 0;
            for (T entity : entities) {
                if (new KnifeFluent<>(session, entityClass).updateById(entity))
                    count++;
            }
            return count;
        });
    }

    /**
     * 按已暂存的 {@link #set} 值和 WHERE 条件批量更新。
     *
     * <pre>{@code
     * orm.of(User.class).eq("dept_id", 10).set("status", 0).update();
     * orm.of(User.class).where("dept_id = ? AND status != ?", 10, 0).set("status", 0).update();
     * }</pre>
     *
     * @return 受影响行数
     */
    public int update() {
        if (setValues.isEmpty())
            throw new IllegalStateException(
                    "No SET values staged. Call set() before update()");
        List<Object> params = new ArrayList<>();
        List<String> sets = new ArrayList<>();
        setValues.forEach((col, val) -> {
            sets.add(col + " = ?");
            params.add(val);
        });
        String where = buildWhere(params);
        return executeWrite("UPDATE " + meta.tableName + " SET "
                + String.join(", ", sets) + where, params);
    }

    /**
     * 按 WHERE 条件删除。若配置了 {@link TableName#logicDeleteField()} 则执行软删。
     *
     * @return 受影响行数
     */
    public int delete() {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(params);
        String sql = meta.logicDeleteField != null
                ? "UPDATE " + meta.tableName + " SET " + meta.logicDeleteField
                        + " = '" + meta.logicDeleteValue + "'" + where
                : "DELETE FROM " + meta.tableName + where;
        return executeWrite(sql, params);
    }

    /** 按主键删除（或逻辑删除）单条记录。 */
    public boolean deleteById(Object id) {
        return eq(meta.idColumn, id).delete() > 0;
    }

    /** 主键为空/零值时 INSERT，否则按主键 UPDATE。 */
    public boolean saveOrUpdate(T entity) {
        if (meta.idField == null)
            return insert(entity);
        Object idVal = getFieldValue(meta.idField, entity);
        return isEmpty(idVal) ? insert(entity) : updateById(entity);
    }

    // =========================================================================
    // Session 管理
    // =========================================================================

    @FunctionalInterface
    private interface SessionWork<R> {
        R apply(SqlSession session) throws Exception;
    }

    private <R> R withRead(SessionWork<R> work) {
        if (txSession != null) {
            try {
                return work.apply(txSession);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        try (SqlSession session = factory.openSession(true)) {
            return work.apply(session);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private <R> R withWrite(SessionWork<R> work) {
        if (txSession != null) {
            try {
                return work.apply(txSession);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        try (SqlSession session = factory.openSession(false)) {
            R result = work.apply(session);
            session.commit();
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private int executeWrite(String sql, List<Object> params) {
        logSql(sql, params);
        return withWrite(session -> new SqlRunner(session.getConnection())
                .update(sql, params.toArray()));
    }

    // =========================================================================
    // SQL 构建
    // =========================================================================

    private String buildSelect(List<Object> params) {
        String cols = (selectColumns != null && !selectColumns.isEmpty())
                ? String.join(", ", selectColumns)
                : meta.columns.stream()
                        .filter(c -> c.select)
                        .map(c -> c.columnName)
                        .collect(Collectors.joining(", "));
        return "SELECT " + cols + " FROM " + meta.tableName
                + buildWhere(params) + buildOrderBy() + buildPaging();
    }

    private String buildWhere(List<Object> params) {
        boolean hasLogicDel = meta.logicDeleteField != null;
        boolean hasConditions = !conditions.isEmpty();
        if (!hasLogicDel && !hasConditions)
            return "";

        StringBuilder sb = new StringBuilder(" WHERE ");
        if (hasLogicDel) {
            sb.append(meta.logicDeleteField)
                    .append(" = '").append(meta.logicNotDeleteValue).append("'");
            if (hasConditions)
                sb.append(" AND ");
        }
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0)
                sb.append(" AND ");
            appendCondition(sb, conditions.get(i), params);
        }
        return sb.toString();
    }

    private void appendCondition(StringBuilder sb, Condition c, List<Object> params) {
        switch (c.op) {
            case "RAW":
                sb.append(c.column); // column 字段存的是原始 SQL 片段
                if (c.value instanceof Object[]) {
                    params.addAll(Arrays.asList((Object[]) c.value));
                }
                break;
            case "IS NULL":
                sb.append(c.column).append(" IS NULL");
                break;
            case "IS NOT NULL":
                sb.append(c.column).append(" IS NOT NULL");
                break;
            case "LIKE":
                sb.append(c.column).append(" LIKE ?");
                params.add("%" + c.value + "%");
                break;
            case "IN":
                Collection<?> vals = (Collection<?>) c.value;
                sb.append(c.column).append(" IN (")
                        .append(String.join(", ", Collections.nCopies(vals.size(), "?")))
                        .append(")");
                params.addAll(vals);
                break;
            default:
                sb.append(c.column).append(' ').append(c.op).append(" ?");
                params.add(c.value);
        }
    }

    private String buildOrderBy() {
        if (sorts.isEmpty())
            return "";
        return " ORDER BY " + sorts.stream()
                .map(s -> s[0] + " " + s[1]).collect(Collectors.joining(", "));
    }

    private String buildPaging() {
        StringBuilder sb = new StringBuilder();
        if (limitVal > 0)
            sb.append(" LIMIT ").append(limitVal);
        if (offsetVal >= 0)
            sb.append(" OFFSET ").append(offsetVal);
        return sb.toString();
    }

    // =========================================================================
    // 结果映射
    // =========================================================================

    private List<T> mapResults(List<Map<String, Object>> rows) {
        return rows.stream().map(this::mapRow).collect(Collectors.toList());
    }

    private T mapRow(Map<String, Object> row) {
        try {
            T instance = entityClass.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getValue() == null)
                    continue;
                Field field = meta.columnToField.get(entry.getKey().toLowerCase());
                if (field == null)
                    continue;
                setFieldValue(field, instance, coerce(entry.getValue(), field.getType()));
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Row mapping failed for " + entityClass.getSimpleName(), e);
        }
    }

    // =========================================================================
    // 工具
    // =========================================================================

    private static boolean isEmpty(Object val) {
        return val == null
                || (val instanceof Number && ((Number) val).longValue() == 0)
                || "".equals(val);
    }

    private static Object generateId(TableId.IdType type) {
        switch (type) {
            case UUID:
                return UUID.randomUUID().toString().replace("-", "");
            case SNOWFLAKE:
                return Snowflake.nextId();
            default:
                return null;
        }
    }

    private static Object fillNow(Class<?> type) {
        if (type == LocalDateTime.class)
            return LocalDateTime.now();
        if (type == LocalDate.class)
            return LocalDate.now();
        if (type == java.util.Date.class)
            return new java.util.Date();
        if (type == java.sql.Timestamp.class)
            return new java.sql.Timestamp(System.currentTimeMillis());
        if (type == Long.class || type == long.class)
            return System.currentTimeMillis();
        return null;
    }

    private static Object getFieldValue(Field field, Object obj) {
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot read " + field.getName(), e);
        }
    }

    private static void setFieldValue(Field field, Object obj, Object value) {
        try {
            field.set(obj, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot write " + field.getName(), e);
        }
    }

    private static Object coerce(Object raw, Class<?> target) {
        if (raw == null || target.isAssignableFrom(raw.getClass()))
            return raw;
        if (raw instanceof Number) {
            Number n = (Number) raw;
            if (target == int.class || target == Integer.class)
                return n.intValue();
            if (target == long.class || target == Long.class)
                return n.longValue();
            if (target == double.class || target == Double.class)
                return n.doubleValue();
            if (target == float.class || target == Float.class)
                return n.floatValue();
            if (target == short.class || target == Short.class)
                return n.shortValue();
            if (target == boolean.class || target == Boolean.class)
                return n.intValue() != 0;
        }
        if (target == String.class)
            return raw.toString();
        return raw;
    }

    static String toSnakeCase(String name) {
        return name.replaceAll("([A-Z])", "_$1").toLowerCase().replaceAll("^_", "");
    }

    // =========================================================================
    // 实体元数据（带缓存）
    // =========================================================================

    static final class TableMeta {
        private static final Map<Class<?>, TableMeta> CACHE = new ConcurrentHashMap<>();

        final String tableName;
        final String idColumn;
        final Field idField;
        final TableId.IdType idType;
        final List<ColumnMeta> columns;
        final Map<String, Field> columnToField;
        final String logicDeleteField;
        final String logicDeleteValue;
        final String logicNotDeleteValue;

        private TableMeta(String tableName, String idColumn, Field idField, TableId.IdType idType,
                List<ColumnMeta> columns, String logicDeleteField,
                String logicDeleteValue, String logicNotDeleteValue) {
            this.tableName = tableName;
            this.idColumn = idColumn;
            this.idField = idField;
            this.idType = idType;
            this.columns = Collections.unmodifiableList(columns);
            this.logicDeleteField = logicDeleteField;
            this.logicDeleteValue = logicDeleteValue;
            this.logicNotDeleteValue = logicNotDeleteValue;
            Map<String, Field> m = new HashMap<>();
            for (ColumnMeta c : columns)
                m.put(c.columnName.toLowerCase(), c.field);
            this.columnToField = Collections.unmodifiableMap(m);
        }

        static TableMeta resolve(Class<?> clazz) {
            return CACHE.computeIfAbsent(clazz, TableMeta::build);
        }

        private static TableMeta build(Class<?> clazz) {
            TableName tn = clazz.getAnnotation(TableName.class);
            String rawName = (tn != null && !tn.value().isEmpty())
                    ? tn.value()
                    : toSnakeCase(clazz.getSimpleName());
            String prefix = (tn != null && !tn.prefix().isEmpty()) ? tn.prefix() : "";
            String logicDel = (tn != null && !tn.logicDeleteField().isEmpty()) ? tn.logicDeleteField() : null;
            String delVal = tn != null ? tn.logicDeleteValue() : "1";
            String notDel = tn != null ? tn.logicNotDeleteValue() : "0";

            List<ColumnMeta> cols = new ArrayList<>();
            String idCol = null;
            Field idFld = null;
            TableId.IdType idTyp = TableId.IdType.NONE;

            Class<?> cursor = clazz;
            while (cursor != null && cursor != Object.class) {
                for (Field f : cursor.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic())
                        continue;
                    f.setAccessible(true);

                    TableId tid = f.getAnnotation(TableId.class);
                    if (tid != null) {
                        String col = !tid.value().isEmpty() ? tid.value() : toSnakeCase(f.getName());
                        idCol = col;
                        idFld = f;
                        idTyp = tid.type();
                        cols.add(new ColumnMeta(f, col, true, true, false, true,
                                false, TableField.FillStrategy.DEFAULT));
                        continue;
                    }
                    TableField tf = f.getAnnotation(TableField.class);
                    String cName = (tf != null && !tf.value().isEmpty())
                            ? tf.value()
                            : toSnakeCase(f.getName());
                    cols.add(new ColumnMeta(f, cName, false,
                            tf == null || tf.insertable(), tf == null || tf.updatable(),
                            tf == null || tf.select(), tf != null && tf.version(),
                            tf != null ? tf.fill() : TableField.FillStrategy.DEFAULT));
                }
                cursor = cursor.getSuperclass();
            }
            return new TableMeta(prefix + rawName, idCol, idFld, idTyp, cols, logicDel, delVal, notDel);
        }
    }

    static final class ColumnMeta {
        final Field field;
        final String columnName;
        final boolean isId;
        final boolean insertable, updatable, select, version;
        final TableField.FillStrategy fill;

        ColumnMeta(Field field, String columnName, boolean isId, boolean insertable,
                boolean updatable, boolean select, boolean version,
                TableField.FillStrategy fill) {
            this.field = field;
            this.columnName = columnName;
            this.isId = isId;
            this.insertable = insertable;
            this.updatable = updatable;
            this.select = select;
            this.version = version;
            this.fill = fill;
        }
    }

    private static final class Condition {
        final String column; // 列名，或 RAW 模式下的 SQL 片段
        final String op;
        final Object value; // RAW 模式下为 Object[]，IN 模式下为 Collection<?>

        Condition(String col, String op, Object val) {
            this.column = col;
            this.op = op;
            this.value = val;
        }
    }

    // =========================================================================
    // 雪花 ID 生成器
    // =========================================================================

    private static final class Snowflake {
        private static final long EPOCH = 1_609_459_200_000L;
        private static final long SEQ_BITS = 12L;
        private static final long MACHINE_BITS = 10L;
        private static final long MAX_SEQ = ~(-1L << SEQ_BITS);
        private static final long MACHINE_SHIFT = SEQ_BITS;
        private static final long TIMESTAMP_SHIFT = SEQ_BITS + MACHINE_BITS;
        private static final long MACHINE_ID = ProcessHandle.current().pid() & 0x3FFL;

        private static long lastTs = -1L;
        private static long seq = 0L;

        static synchronized long nextId() {
            long ts = System.currentTimeMillis();
            if (ts == lastTs) {
                seq = (seq + 1) & MAX_SEQ;
                if (seq == 0)
                    while ((ts = System.currentTimeMillis()) <= lastTs) {
                        /* spin */ }
            } else {
                seq = 0L;
            }
            lastTs = ts;
            return ((ts - EPOCH) << TIMESTAMP_SHIFT) | (MACHINE_ID << MACHINE_SHIFT) | seq;
        }
    }
}
