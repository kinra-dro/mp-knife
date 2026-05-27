package com.mp.knife.chain;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mp.knife.support.BeanCopierUtil;

import java.util.List;
import java.util.function.Consumer;

public class SQLBeanQuery<T> implements Chain<T> {
    private final Class<T> entityClass;
    private final BaseMapper<T> mapper;
    private final QueryWrapper<T> wrapper;
    private int limit = -1;
    private String tableName;

    public SQLBeanQuery(Class<T> entityClass, BaseMapper<T> mapper) {
        this.entityClass = entityClass;
        this.mapper = mapper;
        this.wrapper = new QueryWrapper<>();
        this.tableName = TableInfoHelper.getTableInfo(entityClass).getTableName();
    }

    @Override
    public SQLBeanQuery<T> select(String columns) {
        wrapper.select(columns);
        return this;
    }

    @Override
    public SQLBeanQuery<T> where(String c, Object... args) {
        wrapper.apply(c, args);
        return this;
    }

    @Override
    public SQLBeanQuery<T> join(String j) {
        wrapper.last(" INNER JOIN " + j);
        return this;
    }

    @Override
    public SQLBeanQuery<T> table(String tableName) {
        this.tableName = tableName;
        return this;
    }

    @Override
    public SQLBeanQuery<T> shard(Object key) {
        int idx = key.hashCode() & 15;
        return table(this.tableName + "_" + idx);
    }

    @Override
    public T first() {
        return mapper.selectOne(wrapper.last("LIMIT 1"));
    }

    @Override
    public List<T> list() {
        return limit > 0 ? mapper.selectList(wrapper.last("LIMIT " + limit)) : mapper.selectList(wrapper);
    }

    @Override
    public <R> List<R> list(Class<R> clazz) {
        return BeanCopierUtil.copyList(list(), clazz);
    }

    @Override
    public IPage<T> page(int page, int size) {
        return mapper.selectPage(new Page<T>(page, size), wrapper);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean update(Consumer<T> consumer) {
        try {
            T t = entityClass.newInstance();
            consumer.accept(t);
            return mapper.update(t, wrapper) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean delete() {
        return mapper.delete(wrapper) > 0;
    }

    @Override
    public SQLBeanQuery<T> and(String condition, Object... args) {
        wrapper.and(w -> w.apply(condition, args));
        return this;
    }

    @Override
    public SQLBeanQuery<T> or(String condition, Object... args) {
        wrapper.or(w -> w.apply(condition, args));
        return this;
    }

    @Override
    public SQLBeanQuery<T> orderBy(String order) {
        wrapper.last("ORDER BY " + order);
        return this;
    }

    @Override
    public SQLBeanQuery<T> limit(int size) {
        this.limit = size;
        return this;
    }

    @Override
    public SQLBeanQuery<T> offset(int offset) {
        wrapper.last("OFFSET " + offset);
        return this;
    }

    @Override
    public long count() {
        return mapper.selectCount(wrapper);
    }

    @Override
    public <R> IPage<R> page(int p, int s, Class<R> clazz) {
        IPage<T> sourcePage = page(p, s);
        List<R> records = BeanCopierUtil.copyList(sourcePage.getRecords(), clazz);

        IPage<R> resultPage = new Page<>(sourcePage.getCurrent(), sourcePage.getSize(), sourcePage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }
}
