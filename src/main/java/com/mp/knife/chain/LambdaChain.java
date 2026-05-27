package com.mp.knife.chain;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.util.List;
import java.util.function.Consumer;

public interface LambdaChain<T> {
    <R> LambdaChain<T> eq(SFunction<T, R> func, R val);

    <R> LambdaChain<T> ne(SFunction<T, R> func, R val);

    <R> LambdaChain<T> gt(SFunction<T, R> func, R val);

    <R> LambdaChain<T> ge(SFunction<T, R> func, R val);

    <R> LambdaChain<T> lt(SFunction<T, R> func, R val);

    <R> LambdaChain<T> le(SFunction<T, R> func, R val);

    <R> LambdaChain<T> like(SFunction<T, R> func, String val);

    <R> LambdaChain<T> in(SFunction<T, R> func, List<?> val);

    <R> LambdaChain<T> in(SFunction<T, R> func, LambdaChain<?> subQuery);

    LambdaChain<T> orderByAsc(SFunction<T, ?> func);

    LambdaChain<T> orderByDesc(SFunction<T, ?> func);

    T first();

    List<T> list();

    long count();

    IPage<T> page(int page, int size);

    boolean update(Consumer<T> consumer);

    boolean delete();
}
