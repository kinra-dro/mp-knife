package com.mp.knife.chain;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Consumer;

public interface Chain<T> {
    Chain<T> select(String columns);

    Chain<T> where(String condition, Object... args);

    Chain<T> and(String condition, Object... args);

    Chain<T> or(String condition, Object... args);

    Chain<T> join(String join);

    Chain<T> orderBy(String order);

    Chain<T> limit(int size);

    Chain<T> offset(int offset);

    Chain<T> table(String tableName);

    Chain<T> shard(Object shardKey);

    T first();

    List<T> list();

    <R> List<R> list(Class<R> resultType);

    long count();

    IPage<T> page(int page, int size);

    <R> IPage<R> page(int page, int size, Class<R> resultType);

    boolean update(Consumer<T> consumer);

    boolean delete();
}
