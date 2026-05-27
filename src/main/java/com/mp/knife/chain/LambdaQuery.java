package com.mp.knife.chain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.function.Consumer;

public class LambdaQuery<T> implements LambdaChain<T> {
    private final Class<T> entityClass;
    private final BaseMapper<T> mapper;
    protected final LambdaQueryWrapper<T> lambda;

    public LambdaQuery(Class<T> entityClass, BaseMapper<T> mapper) {
        this.entityClass = entityClass;
        this.mapper = mapper;
        this.lambda = new LambdaQueryWrapper<>();
    }

    @Override
    public <R> LambdaQuery<T> eq(SFunction<T, R> f, R v) {
        lambda.eq(f, v);
        return this;
    }

    @Override
    public <R> LambdaQuery<T> ne(SFunction<T, R> f, R v) {
        lambda.ne(f, v);
        return this;
    }

    @Override
    public <R> LambdaQuery<T> gt(SFunction<T, R> f, R v) {
        lambda.gt(f, v);
        return this;
    }

    @Override
    public <R> LambdaQuery<T> ge(SFunction<T, R> f, R v) {
        lambda.ge(f, v);
        return this;
    }

    @Override
    public <R> LambdaQuery<T> lt(SFunction<T, R> f, R v) {
        lambda.lt(f, v);
        return this;
    }

    @Override
    public <R> LambdaQuery<T> le(SFunction<T, R> f, R v) {
        lambda.le(f, v);
        return this;
    }

    @Override
    public <R> LambdaQuery<T> like(SFunction<T, R> f, String v) {
        lambda.like(f, v);
        return this;
    }

    @Override
    public <R> LambdaQuery<T> in(SFunction<T, R> f, List<?> list) {
        lambda.in(f, list);
        return this;
    }

    @Override
    public <R> LambdaQuery<T> in(SFunction<T, R> f, LambdaChain<?> q) {
        lambda.in(f, ((LambdaQuery<?>) q).lambda);
        return this;
    }

    @Override
    public LambdaQuery<T> orderByAsc(SFunction<T, ?> f) {
        lambda.orderByAsc(f);
        return this;
    }

    @Override
    public LambdaQuery<T> orderByDesc(SFunction<T, ?> f) {
        lambda.orderByDesc(f);
        return this;
    }

    @Override
    public T first() {
        return mapper.selectOne(lambda.last("LIMIT 1"));
    }

    @Override
    public List<T> list() {
        return mapper.selectList(lambda);
    }

    @Override
    public long count() {
        return mapper.selectCount(lambda);
    }

    @Override
    public IPage<T> page(int p, int s) {
        return mapper.selectPage(new Page<>(p, s), lambda);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean update(Consumer<T> consumer) {
        try {
            T t = entityClass.newInstance();
            consumer.accept(t);
            return mapper.update(t, lambda) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean delete() {
        return mapper.delete(lambda) > 0;
    }
}
