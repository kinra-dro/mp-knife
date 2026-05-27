package com.mp.knife;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mp.knife.chain.Chain;
import com.mp.knife.chain.LambdaChain;
import com.mp.knife.chain.LambdaQuery;
import com.mp.knife.chain.SQLBeanQuery;

public class DB {

    private final SqlSession sqlSession;
    private final Configuration configuration;
    private final Map<Class<?>, BaseMapper<?>> entityMapperCache = new ConcurrentHashMap<>();

    public DB(SqlSession sqlSession) {
        this.sqlSession = sqlSession;
        this.configuration = sqlSession.getConfiguration();
    }

    @SuppressWarnings("unchecked")
    private <T> BaseMapper<T> resolveMapperClass(Class<T> entityClass) {
        for (Class<?> mapperClass : configuration.getMapperRegistry().getMappers()) {
            if (BaseMapper.class.isAssignableFrom(mapperClass)) {
                Class<?> genericType = getGenericType(mapperClass);
                if (genericType != null && genericType == entityClass) {
                    return (BaseMapper<T>) configuration.getMapper(mapperClass, sqlSession);
                }
            }
        }
        throw new RuntimeException("EntityClass Mapper not found: " + entityClass.getName());
    }

    private Class<?> getGenericType(Class<?> mapperClass) {
        try {
            return (Class<?>) ((java.lang.reflect.ParameterizedType) mapperClass.getGenericInterfaces()[0])
                    .getActualTypeArguments()[0];
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Chain<T> table(Class<T> entityClass) {
        BaseMapper<T> mapper = (BaseMapper<T>) entityMapperCache.get(entityClass);
        if (mapper == null) {
            if (mapper == null) {
                synchronized (entityClass) {
                    mapper = (BaseMapper<T>) entityMapperCache.get(entityClass);
                    if (mapper == null) {
                        mapper = resolveMapperClass(entityClass);
                        entityMapperCache.put(entityClass, mapper);
                    }
                }
            }
        }
        return new SQLBeanQuery<>(entityClass, mapper);
    }

    @SuppressWarnings("unchecked")
    public <T> LambdaChain<T> lambda(Class<T> entityClass) {
        BaseMapper<T> mapper = (BaseMapper<T>) entityMapperCache.get(entityClass);
        if (mapper == null) {
            if (mapper == null) {
                synchronized (entityClass) {
                    mapper = (BaseMapper<T>) entityMapperCache.get(entityClass);
                    if (mapper == null) {
                        mapper = resolveMapperClass(entityClass);
                        entityMapperCache.put(entityClass, mapper);
                    }
                }
            }
        }
        return new LambdaQuery<>(entityClass, mapper);
    }
}
