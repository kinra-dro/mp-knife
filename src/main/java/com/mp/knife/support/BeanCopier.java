package com.mp.knife.support;

import org.mapstruct.Mapper;

import static org.mapstruct.factory.Mappers.getMapper;

@Mapper
public interface BeanCopier {

    BeanCopier INSTANCE = getMapper(BeanCopier.class);

    <T> T copy(Object source, Class<T> targetClass);
}
