package io.mpfluent.convert;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于 Java 反射的 Bean 属性复制工具，不依赖任何第三方库。
 *
 * <p>按属性名匹配，仅复制类型兼容的属性，忽略不存在或类型不兼容的字段。
 * 目标类必须提供 public 无参构造器。</p>
 *
 * <pre>{@code
 * UserDTO dto = BeanConverter.copy(userEntity, UserDTO.class);
 *
 * List<UserDTO> dtoList = BeanConverter.copyList(userList, UserDTO.class);
 * }</pre>
 */
public final class BeanConverter {

    private BeanConverter() {
    }

    /**
     * 将 {@code source} 的属性复制到 {@code targetClass} 的新实例中。
     *
     * @param source      源对象，为 {@code null} 时直接返回 {@code null}
     * @param targetClass 目标类型，需有 public 无参构造器
     * @param <T>         目标类型泛型
     * @return 填充后的目标对象，或 {@code null}（当 source 为 null 时）
     * @throws RuntimeException 若目标类实例化失败或属性读写失败
     */
    public static <T> T copy(Object source, Class<T> targetClass) {
        if (source == null) return null;
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();

            // 读取源对象所有可读属性
            Map<String, Object> srcValues = new HashMap<>();
            BeanInfo srcInfo = Introspector.getBeanInfo(source.getClass(), Object.class);
            for (PropertyDescriptor pd : srcInfo.getPropertyDescriptors()) {
                Method reader = pd.getReadMethod();
                if (reader != null) {
                    srcValues.put(pd.getName(), reader.invoke(source));
                }
            }

            // 按属性名写入目标对象（类型兼容时才写入）
            BeanInfo tgtInfo = Introspector.getBeanInfo(targetClass, Object.class);
            for (PropertyDescriptor pd : tgtInfo.getPropertyDescriptors()) {
                Method writer = pd.getWriteMethod();
                if (writer == null) continue;
                Object val = srcValues.get(pd.getName());
                if (val != null && pd.getPropertyType().isAssignableFrom(val.getClass())) {
                    writer.invoke(target, val);
                }
            }
            return target;
        } catch (Exception e) {
            throw new RuntimeException(
                    "BeanConverter.copy failed: " + source.getClass().getSimpleName()
                    + " -> " + targetClass.getSimpleName(), e);
        }
    }

    /**
     * 批量复制列表，等价于对每个元素调用 {@link #copy(Object, Class)}。
     *
     * @param sourceList  源列表，为 {@code null} 时返回空列表
     * @param targetClass 目标类型
     * @param <S>         源类型泛型
     * @param <T>         目标类型泛型
     */
    public static <S, T> List<T> copyList(List<S> sourceList, Class<T> targetClass) {
        if (sourceList == null) return List.of();
        return sourceList.stream()
                .map(s -> copy(s, targetClass))
                .collect(Collectors.toList());
    }
}
