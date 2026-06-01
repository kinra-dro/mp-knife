package io.mpfluent.annotation;

import java.lang.annotation.*;

/**
 * 标记实体类中的主键字段。
 *
 * <pre>{@code
 * @TableId
 * private Long id;
 *
 * // 自定义列名 + 自增策略
 * @TableId(value = "user_id", type = IdType.AUTO)
 * private Long userId;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TableId {

    /**
     * 主键对应的数据库列名。
     * 若为空字符串，使用字段名的下划线形式。
     */
    String value() default "";

    /**
     * 主键生成策略，默认 {@link IdType#NONE}（由数据库或调用方决定）。
     */
    IdType type() default IdType.NONE;

    enum IdType {
        /** 不干预，由数据库自增或调用方赋值。 */
        NONE,
        /** 数据库自增（INSERT 时不传主键列）。 */
        AUTO,
        /** 框架生成 UUID（去掉连字符的 32 位字符串）。 */
        UUID,
        /** 框架生成雪花算法 ID（long 类型字段适用）。 */
        SNOWFLAKE
    }
}
