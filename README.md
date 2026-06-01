# mp-knife

> 基于原生 MyBatis 的轻量级链式 ORM，无需 MyBatis-Plus、无需 Mapper 接口、无需 XML  
> A lightweight fluent ORM on top of plain MyBatis — no MyBatis-Plus, no Mapper interfaces, no XML

**[中文](#中文文档) | [English](#english-documentation)**

---

## 中文文档

mp-knife 封装原生 MyBatis `SqlSessionFactory`，提供链式 CRUD API。通过自定义注解描述实体，
SQL 在运行时动态构建并由 MyBatis 直接执行，结果通过反射自动映射回实体对象。

### 特性

- **零额外依赖**：仅依赖 `org.mybatis:mybatis`，无运行时捆绑依赖
- **自定义注解**：`@TableName` / `@TableId` / `@TableField`，支持逻辑删除、乐观锁、自动填充
- **完整 CRUD**：insert、getById、updateById、deleteById、saveOrUpdate 及批量操作
- **链式查询**：条件、排序、分页、投影，全部链式调用
- **主键策略**：AUTO（数据库自增）/ UUID / 雪花算法 / NONE
- **软删除**：通过 `@TableName(logicDeleteField=...)` 配置，删除操作自动改写为 UPDATE
- **乐观锁**：`@TableField(version=true)`，updateById 自动校验并自增版本号
- **自动填充**：`@TableField(fill=INSERT/UPDATE/INSERT_UPDATE)`，自动写入当前时间戳
- **原生 SQL 条件**：`where("age > ? AND status = ?", 18, 1)`，`?` 占位符防注入，与链式条件自由混用
- **SQL 日志**：`Knife.enableLog(true)` 一行开启，格式参考 MyBatis，仅输出语句与参数，不含结果集
- **事务支持**：`Knife.transact(orm -> {...})` 在同一 Session 内提交多步操作
- **BeanConverter**：纯 Java 反射 Bean 拷贝，用于 Entity → DTO 转换

---

### 依赖引入

```xml
<!-- 使用方仅需提供 MyBatis，mp-knife 以 provided 方式声明依赖 -->
<dependency>
    <groupId>org.mybatis</groupId>
    <artifactId>mybatis</artifactId>
    <version>3.5.16</version>
</dependency>

<dependency>
    <groupId>io.mpfluent</groupId>
    <artifactId>mp-knife</artifactId>
    <version>0.2</version>
</dependency>
```

---

### 实体类注解

```java
@TableName(
    value            = "sys_user",        // 表名；省略时类名转 snake_case（UserInfo→user_info）
    prefix           = "",                // 表名前缀，最终 = prefix + value
    logicDeleteField = "is_deleted",      // 逻辑删除字段；配置后 delete() 改写为 UPDATE
    logicDeleteValue    = "1",            // 已删除标记值（默认 "1"）
    logicNotDeleteValue = "0"             // 未删除标记值（默认 "0"）
)
public class User {

    @TableId(
        value = "id",                     // 主键列名；省略时字段名转 snake_case
        type  = TableId.IdType.AUTO       // AUTO / UUID / SNOWFLAKE / NONE（默认）
    )
    private Long id;

    @TableField("user_name")              // 列名映射
    private String userName;

    @TableField(
        value      = "create_time",
        insertable = true,
        updatable  = false,
        fill       = TableField.FillStrategy.INSERT  // INSERT 时自动填充当前时间
    )
    private LocalDateTime createTime;

    @TableField(
        value  = "update_time",
        fill   = TableField.FillStrategy.UPDATE      // UPDATE 时自动填充当前时间
    )
    private LocalDateTime updateTime;

    @TableField(version = true)           // 乐观锁版本号
    private Long version;

    @TableField(select = false)           // 不出现在 SELECT 结果中
    private String password;

    // getter / setter ...
}
```

---

### 初始化

```java
// 方式一：独立运行，内置 MyBatis 连接池
Knife.init(
    Knife.builder()
        .driver("com.mysql.cj.jdbc.Driver")
        .url("jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC")
        .username("root")
        .password("secret")
        .poolMaxActive(10)
        .enableLog(true)          // 开启 SQL 日志（可选）
        .build()
);

// 方式二：Spring Boot，注入 DataSource
Knife.init(dataSource);

// 方式三：Spring Boot，注入 SqlSessionFactory
Knife.init(sqlSessionFactory);

// Spring Bean 写法
@Bean
public KnifeOrm knifeOrm(DataSource dataSource) {
    return KnifeOrmFactory.fromDataSource(dataSource);
}

@PostConstruct
public void initKnife(KnifeOrm orm) {
    Knife.init(orm);
}

// 任意时刻动态切换日志
Knife.enableLog(true);
Knife.enableLog(false);
```

---

### 查询

```java
// 按主键查询
User user = Knife.of(User.class).getById(1L);

// 条件查询 + 排序 + 分页
List<User> list = Knife.of(User.class)
        .eq("status", 1)
        .like("user_name", "张")
        .ge("age", 18)
        .orderByDesc("create_time")
        .page(1, 20)
        .list();

// 取第一条
User first = Knife.of(User.class).eq("status", 1).orderByAsc("id").first();

// 统计
long count = Knife.of(User.class).eq("dept_id", 10).count();

// 是否存在
boolean exists = Knife.of(User.class).eq("email", "a@b.com").exists();

// 指定列查询
List<User> list = Knife.of(User.class)
        .select("id", "user_name", "status")
        .eq("status", 1)
        .list();

// IN 查询
List<User> list = Knife.of(User.class)
        .in("status", List.of(1, 2, 3))
        .list();

// 原生 SQL 条件（? 占位符）
User user = Knife.of(User.class).where("id = ?", 1L).first();
List<User> list = Knife.of(User.class).where("age > ? AND status = ?", 18, 1).list();
List<User> list = Knife.of(User.class).where("create_time BETWEEN ? AND ?", start, end).list();
```

---

### 写入

```java
// 插入（主键按 @TableId.type 策略处理，AUTO 自增后回填）
User user = new User();
user.setUserName("张三");
Knife.of(User.class).insert(user);
// 插入后 user.getId() 已被回填

// 按主键更新（null 字段跳过；乐观锁字段自动校验+自增）
user.setUserName("李四");
Knife.of(User.class).updateById(user);

// 条件批量更新
Knife.of(User.class)
        .eq("dept_id", 10)
        .set("status", 0)
        .set("remark", "禁用")
        .update();

// 存在则更新，不存在则插入（主键 null/0/"" → insert）
Knife.of(User.class).saveOrUpdate(user);

// 按主键删除（配置了 logicDeleteField 则软删）
Knife.of(User.class).deleteById(1L);

// 条件删除
Knife.of(User.class).eq("status", 0).delete();

// 批量插入（同一 Session，共享事务）
Knife.of(User.class).insertBatch(userList);

// 批量按主键更新
Knife.of(User.class).updateBatchById(userList);
```

---

### 事务

```java
// 无返回值
Knife.transact(orm -> {
    orm.of(User.class).insert(user);
    orm.of(Order.class).insert(order);
});

// 有返回值
Long userId = Knife.transact(orm -> {
    orm.of(User.class).insert(user);
    return user.getId();
});

// KnifeOrm 实例上直接调用
knifeOrm.transact(orm -> {
    orm.of(Account.class).eq("id", fromId).set("balance", from - amount).update();
    orm.of(Account.class).eq("id", toId).set("balance", to + amount).update();
});
```

事务块内抛出任何异常自动回滚；嵌套调用 `transact` 时复用外层 Session，不重复提交。

---

### SQL 日志

```java
// 初始化时开启
Knife.init(
    Knife.builder()...enableLog(true).build()
);

// 或 DataSource 场景
KnifeOrmFactory.fromDataSource(dataSource, true);

// 或运行时动态切换
Knife.enableLog(true);
```

输出格式参考 MyBatis，**仅输出 SQL 语句和参数，不含查询结果**：

```
INFO: ==>  Preparing: SELECT id, user_name FROM sys_user WHERE status = ? AND age > ?
INFO: ==> Parameters: 1(Integer), 18(Integer)

INFO: ==>  Preparing: INSERT INTO sys_user (user_name, status) VALUES (?, ?)
INFO: ==> Parameters: 张三(String), 1(Integer)

INFO: ==>  Preparing: UPDATE sys_user SET status = ? WHERE is_deleted = '0' AND dept_id = ?
INFO: ==> Parameters: 0(Integer), 10(Long)
```

日志通过标准 JUL（`java.util.logging`，Logger 名 `io.mpfluent`）输出。Spring Boot 项目引入 `jul-to-slf4j` 后自动桥接到 Logback / Log4j2，`logback.xml` 中配置 `io.mpfluent` 的级别即可控制输出。

---

### 结果转换（DTO）

```java
// 单个转换
UserDTO dto = BeanConverter.copy(user, UserDTO.class);

// 列表转换
List<UserDTO> dtos = BeanConverter.copyList(userList, UserDTO.class);
```

`BeanConverter` 按属性名匹配，仅复制类型兼容的属性，目标类需提供 public 无参构造器。

---

### 注解速查

#### `@TableName`（类级别）

| 属性 | 说明 | 默认 |
|---|---|---|
| `value` | 表名，空时类名转 snake_case | `""` |
| `prefix` | 表名前缀 | `""` |
| `logicDeleteField` | 逻辑删除列名，非空时 delete 改写为 UPDATE | `""` |
| `logicDeleteValue` | 已删除标记值 | `"1"` |
| `logicNotDeleteValue` | 未删除标记值 | `"0"` |

#### `@TableId`（字段级别，主键）

| 属性 | 说明 | 默认 |
|---|---|---|
| `value` | 列名，空时字段名转 snake_case | `""` |
| `type` | `NONE` / `AUTO` / `UUID` / `SNOWFLAKE` | `NONE` |

#### `@TableField`（字段级别，普通列）

| 属性 | 说明 | 默认 |
|---|---|---|
| `value` | 列名，空时字段名转 snake_case | `""` |
| `insertable` | 是否参与 INSERT | `true` |
| `updatable` | 是否参与 UPDATE | `true` |
| `select` | 是否出现在 SELECT 中 | `true` |
| `version` | 标记为乐观锁版本号 | `false` |
| `fill` | `DEFAULT` / `INSERT` / `UPDATE` / `INSERT_UPDATE` | `DEFAULT` |

---

### 项目结构

```
io.mpfluent
├── Knife.java                    ← 全局静态入口
├── annotation/
│   ├── TableName.java            ← 实体 → 表映射
│   ├── TableId.java              ← 主键策略
│   └── TableField.java           ← 列映射、乐观锁、自动填充
├── core/
│   ├── KnifeOrm.java             ← 持有 SqlSessionFactory，提供 of() 和 transact()
│   ├── KnifeOrmFactory.java      ← 构建 KnifeOrm（连接池 / DataSource）
│   └── KnifeFluent.java          ← 链式查询/写入构造器（含实体元数据缓存）
├── convert/
│   └── BeanConverter.java        ← 反射 Bean 拷贝工具
```

---

## English Documentation

mp-knife is a lightweight fluent ORM wrapper over plain MyBatis. No MyBatis-Plus dependency, no Mapper interfaces, no XML mapping files — just a `SqlSessionFactory` and annotated entity classes.

### Features

- **Zero extra runtime dependencies**: only `org.mybatis:mybatis` required
- **Custom annotations**: `@TableName` / `@TableId` / `@TableField` with logic-delete, optimistic-lock, and auto-fill support
- **Full CRUD**: insert, getById, updateById, deleteById, saveOrUpdate, plus batch variants
- **Fluent chain API**: filter, sort, paginate, project — all chainable
- **Primary key strategies**: AUTO (DB increment), UUID, Snowflake, NONE
- **Soft delete**: configure `@TableName(logicDeleteField=...)` — `delete()` becomes an UPDATE automatically
- **Optimistic lock**: `@TableField(version=true)` — version check + increment applied on every `updateById`
- **Auto-fill**: `@TableField(fill=INSERT/UPDATE/INSERT_UPDATE)` — timestamp written automatically
- **Raw SQL conditions**: `where("age > ? AND status = ?", 18, 1)` — `?` placeholder, SQL-injection safe, freely mixed with typed methods
- **SQL logging**: `Knife.enableLog(true)` — logs SQL and parameters only (no result data), MyBatis-style format
- **Transaction support**: `Knife.transact(orm -> {...})` — multiple operations share one Session
- **BeanConverter**: pure-reflection bean copy, no MapStruct or code generation

---

### Dependency

```xml
<dependency>
    <groupId>org.mybatis</groupId>
    <artifactId>mybatis</artifactId>
    <version>3.5.16</version>
</dependency>

<dependency>
    <groupId>io.mpfluent</groupId>
    <artifactId>mp-knife</artifactId>
    <version>0.2</version>
</dependency>
```

---

### Entity class

```java
@TableName(
    value               = "sys_user",
    logicDeleteField    = "is_deleted",
    logicDeleteValue    = "1",
    logicNotDeleteValue = "0"
)
public class User {

    @TableId(type = TableId.IdType.AUTO)
    private Long id;

    @TableField("user_name")
    private String userName;

    @TableField(value = "create_time", updatable = false, fill = TableField.FillStrategy.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = TableField.FillStrategy.UPDATE)
    private LocalDateTime updateTime;

    @TableField(version = true)
    private Long version;

    @TableField(select = false)
    private String password;
}
```

---

### Initialization

```java
// Standalone — built-in connection pool
Knife.init(
    Knife.builder()
        .driver("com.mysql.cj.jdbc.Driver")
        .url("jdbc:mysql://localhost:3306/mydb")
        .username("root").password("secret")
        .enableLog(true)   // optional SQL logging
        .build()
);

// Spring Boot — inject DataSource
Knife.init(dataSource);

// Spring Boot — inject DataSource with logging
KnifeOrmFactory.fromDataSource(dataSource, true);

// Spring Boot — inject SqlSessionFactory
Knife.init(sqlSessionFactory);

// Toggle at runtime
Knife.enableLog(true);
Knife.enableLog(false);
```

---

### Query

```java
User user = Knife.of(User.class).getById(1L);

List<User> list = Knife.of(User.class)
        .eq("status", 1)
        .like("user_name", "zhang")
        .orderByDesc("create_time")
        .page(1, 20)
        .list();

long count = Knife.of(User.class).eq("dept_id", 10).count();
boolean exists = Knife.of(User.class).eq("email", "a@b.com").exists();

// Raw SQL condition (? placeholder)
User user = Knife.of(User.class).where("id = ?", 1L).first();
List<User> list = Knife.of(User.class).where("age > ? AND status = ?", 18, 1).list();
```

---

### Write

```java
Knife.of(User.class).insert(user);              // PK filled back on AUTO
Knife.of(User.class).updateById(user);          // null fields skipped; version auto-checked
Knife.of(User.class).saveOrUpdate(user);        // insert if PK null/0/"", else updateById
Knife.of(User.class).deleteById(1L);            // soft-delete if logicDeleteField configured
Knife.of(User.class).eq("status", 0).set("remark", "disabled").update();
Knife.of(User.class).insertBatch(userList);
Knife.of(User.class).updateBatchById(userList);
```

---

### Transactions

```java
Knife.transact(orm -> {
    orm.of(User.class).insert(user);
    orm.of(Order.class).insert(order);
});

Long id = Knife.transact(orm -> {
    orm.of(User.class).insert(user);
    return user.getId();
});
```

Any exception thrown inside the block triggers an automatic rollback. Nested `transact` calls reuse the outer Session.

---

### SQL Logging

```java
Knife.init(Knife.builder()...enableLog(true).build());
// or
KnifeOrmFactory.fromDataSource(dataSource, true);
// or at runtime
Knife.enableLog(true);
```

Logs SQL statements and bound parameters only — **no result data**:

```
INFO: ==>  Preparing: SELECT id, user_name FROM sys_user WHERE status = ? AND age > ?
INFO: ==> Parameters: 1(Integer), 18(Integer)

INFO: ==>  Preparing: INSERT INTO sys_user (user_name, status) VALUES (?, ?)
INFO: ==> Parameters: Alice(String), 1(Integer)
```

Output is via JUL (`java.util.logging`, logger name `io.mpfluent`). In Spring Boot, add `jul-to-slf4j` to bridge to Logback/Log4j2 and control the level with `io.mpfluent` in your `logback.xml`.

---

### DTO Conversion

```java
UserDTO dto  = BeanConverter.copy(user, UserDTO.class);
List<UserDTO> dtos = BeanConverter.copyList(userList, UserDTO.class);
```

---

### Annotation reference

#### `@TableName` (class-level)

| Attribute | Description | Default |
|---|---|---|
| `value` | Table name; snake_case of class name when empty | `""` |
| `prefix` | Table name prefix | `""` |
| `logicDeleteField` | Soft-delete column; when set, `delete()` becomes UPDATE | `""` |
| `logicDeleteValue` | Deleted marker value | `"1"` |
| `logicNotDeleteValue` | Not-deleted marker value | `"0"` |

#### `@TableId` (field-level, primary key)

| Attribute | Description | Default |
|---|---|---|
| `value` | Column name; snake_case of field name when empty | `""` |
| `type` | `NONE` / `AUTO` / `UUID` / `SNOWFLAKE` | `NONE` |

#### `@TableField` (field-level, regular column)

| Attribute | Description | Default |
|---|---|---|
| `value` | Column name; snake_case of field name when empty | `""` |
| `insertable` | Include in INSERT | `true` |
| `updatable` | Include in UPDATE | `true` |
| `select` | Include in SELECT | `true` |
| `version` | Mark as optimistic-lock version | `false` |
| `fill` | `DEFAULT` / `INSERT` / `UPDATE` / `INSERT_UPDATE` | `DEFAULT` |

---

## License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
