package io.mpfluent.annotation;

import java.lang.annotation.*;

/**
 * 标记一个类为数据库实体，并指定其映射的表名。
 *
 * <p>
 * 若不标注，框架默认使用类名的下划线形式作为表名（例如 {@code UserInfo} → {@code user_info}）。
 * </p>
 *
 * <pre>{@code
 * &#64;TableName("sys_user")
 * public class User { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TableName {

    /**
     * 对应的数据库表名。
     * 若为空字符串，框架将类名转换为下划线命名作为表名。
     */
    String value() default "";

    /**
     * 表名前缀，最终表名为 {@code prefix + value}。
     * 适用于多租户或分库场景中统一添加前缀。
     */
    String prefix() default "";

    /**
     * 逻辑删除字段名。不为空时，框架的删除操作将执行 UPDATE 而非 DELETE。
     * 例如 {@code "is_deleted"}。
     */
    String logicDeleteField() default "";

    /**
     * 逻辑删除的"已删除"值，默认为 {@code "1"}。
     */
    String logicDeleteValue() default "1";

    /**
     * 逻辑删除的"未删除"值，默认为 {@code "0"}。
     */
    String logicNotDeleteValue() default "0";
}
