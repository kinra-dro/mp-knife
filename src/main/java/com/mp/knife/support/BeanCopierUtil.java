package com.mp.knife.support;

import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.stream.Collectors;

public class BeanCopierUtil {

    private static final BeanCopier MAPPER = Mappers.getMapper(BeanCopier.class);

    private BeanCopierUtil() {
    }

    public static <S, T> T copy(S source, Class<T> targetClass) {
        return MAPPER.copy(source, targetClass);
    }

    public static <S, T> List<T> copyList(List<S> sourceList, Class<T> targetClass) {
        return sourceList.stream()
                .map(item -> copy(item, targetClass))
                .collect(Collectors.toList());
    }
}
