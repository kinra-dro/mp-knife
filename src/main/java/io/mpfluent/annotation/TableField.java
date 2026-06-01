package io.mpfluent.annotation;

import java.lang.annotation.*;

/**
 * 标记实体字段与数据库列的映射关系。
 *
 * <p>若字段未标注此注解，框架默认使用字段名的下划线形式作为列名。</p>
 *
 * <pre>{@code
 * @TableField("user_name")
 * private String userName;
 *
 * // 不参与 INSERT / UPDATE
 * @TableField(value = "create_time", insertable = false, updatable = false)
 * private LocalDateTime createTime;
 *
 * // 查询时不返回该字段（如密码）
 * @TableField(select = false)
 * private String password;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TableField {

    /**
     * 对应的数据库列名。
     * 若为空字符串，使用字段名的下划线形式。
     */
    String value() default "";

    /**
     * 是否参与 INSERT 语句，默认 {@code true}。
     */
    boolean insertable() default true;

    /**
     * 是否参与 UPDATE 语句，默认 {@code true}。
     */
    boolean updatable() default true;

    /**
     * 是否包含在 SELECT 结果中，默认 {@code true}。
     * 设为 {@code false} 可屏蔽敏感字段（如密码、盐值）。
     */
    boolean select() default true;

    /**
     * 标记该字段为乐观锁版本号。
     * 框架在 UPDATE 时自动追加 {@code version = version + 1} 并在条件中校验当前版本。
     */
    boolean version() default false;

    /**
     * 标记该字段为自动填充时间戳。
     */
    FillStrategy fill() default FillStrategy.DEFAULT;

    enum FillStrategy {
        /** 不自动填充。 */
        DEFAULT,
        /** 仅在 INSERT 时填充（例如 create_time）。 */
        INSERT,
        /** 仅在 UPDATE 时填充（例如 update_time）。 */
        UPDATE,
        /** INSERT 和 UPDATE 时均填充。 */
        INSERT_UPDATE
    }
}
