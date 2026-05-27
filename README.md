# mp-knife

> 基于 MyBatis-Plus 的轻量级流式 SQL 构建库

**中文 | [English](./README.en.md)**

mp-knife 在 MyBatis-Plus `BaseMapper` 之上封装了一套极简的链式查询 API，让你无需手写 `QueryWrapper` 样板代码，也无需引入 Spring 容器，只需持有一个 `SqlSession` 即可使用。

## 特性

- **两套 API 按需选择**：字符串 SQL 风格（`Chain`）和 Lambda 类型安全风格（`LambdaChain`）
- **零侵入**：不依赖 Spring，不需要注解扫描，直接包装已有 `SqlSession`
- **自动 Mapper 发现**：从 MyBatis 配置中自动定位实体类对应的 `BaseMapper`，并缓存复用
- **内置分表支持**：通过 `shard(key)` 一行代码路由到 16 个分表之一
- **结果类型投影**：查询结果可直接转换为 DTO，无需手动映射

## 依赖

mp-knife 本身不包含 MyBatis-Plus 等依赖（均为 `provided`），需要你的项目自行引入：

```xml
<!-- 必须自行提供 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus</artifactId>
    <version>3.5.6</version>
</dependency>

<!-- 若使用 BeanCopierUtil 进行结果投影，需提供 MapStruct -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5.Final</version>
    <scope>provided</scope>
</dependency>
```

然后引入 mp-knife：

```xml
<dependency>
    <groupId>com.mp.knife</groupId>
    <artifactId>mp-knife</artifactId>
    <version>0.1</version>
</dependency>
```

## 快速开始

### 1. 准备实体类和 Mapper

```java
// 实体类（使用 MyBatis-Plus 标准注解）
@TableName("user")
public class User {
    @TableId
    private Long id;
    private String name;
    private Integer age;
    private String email;
    // getter / setter ...
}

// Mapper 继承 BaseMapper 即可，mp-knife 会自动发现它
public interface UserMapper extends BaseMapper<User> {}
```

### 2. 创建 DB 实例

```java
// sqlSession 由 MyBatis SqlSessionFactory 提供
SqlSession sqlSession = sqlSessionFactory.openSession();
DB db = new DB(sqlSession);
```

在 Spring 环境中，可以用 `SqlSessionTemplate` 替代：

```java
@Bean
public DB db(SqlSessionTemplate sqlSessionTemplate) {
    return new DB(sqlSessionTemplate);
}
```

---

## Chain API — 字符串 SQL 风格

适合需要写复杂条件或已有 SQL 片段的场景。

### 查询

```java
// 查单条
User user = db.table(User.class)
        .where("id = {0}", 1L)
        .first();

// 查列表
List<User> users = db.table(User.class)
        .where("age > {0}", 18)
        .and("email is not null")
        .orderBy("age ASC")
        .list();

// 只查部分列
List<User> users = db.table(User.class)
        .select("id, name")
        .where("age >= {0}", 18)
        .list();
```

### 分页

```java
// 查第 1 页，每页 10 条
IPage<User> page = db.table(User.class)
        .where("age > {0}", 18)
        .page(1, 10);

long total = page.getTotal();
List<User> records = page.getRecords();
```

### 统计

```java
long count = db.table(User.class)
        .where("age > {0}", 18)
        .count();
```

### OR 条件

```java
List<User> users = db.table(User.class)
        .where("age < {0}", 18)
        .or("age > {0}", 60)
        .list();
```

### LIMIT / OFFSET

```java
List<User> users = db.table(User.class)
        .where("age > {0}", 18)
        .orderBy("id DESC")
        .limit(5)
        .offset(10)
        .list();
```

> ⚠️ `join()`、`orderBy()`、`offset()` 均通过 MyBatis-Plus `wrapper.last()` 追加 SQL 片段，**同一条查询链只能使用一次**，多次调用只有最后一次生效。

### 更新

```java
boolean ok = db.table(User.class)
        .where("id = {0}", 1L)
        .update(u -> {
            u.setName("张三");
            u.setAge(25);
        });
```

### 删除

```java
boolean ok = db.table(User.class)
        .where("age < {0}", 0)
        .delete();
```

### 结果投影为 DTO

```java
public class UserDTO {
    private Long id;
    private String name;
    // getter / setter ...
}

// 查询并直接转换类型
List<UserDTO> dtos = db.table(User.class)
        .select("id, name")
        .where("age > {0}", 18)
        .list(UserDTO.class);

// 分页也支持投影
IPage<UserDTO> page = db.table(User.class)
        .where("age > {0}", 18)
        .page(1, 10, UserDTO.class);
```

结果投影依赖 MapStruct，字段按**同名映射**，需确保 MapStruct 注解处理器已配置。

---

## LambdaChain API — Lambda 类型安全风格

适合追求编译期类型检查、不想写字符串字段名的场景。

### 基础条件

```java
List<User> users = db.lambda(User.class)
        .eq(User::getName, "张三")
        .gt(User::getAge, 18)
        .list();
```

### 全部支持的操作符

| 方法 | 含义 |
|---|---|
| `eq(field, val)` | `=` |
| `ne(field, val)` | `!=` |
| `gt(field, val)` | `>` |
| `ge(field, val)` | `>=` |
| `lt(field, val)` | `<` |
| `le(field, val)` | `<=` |
| `like(field, val)` | `LIKE '%val%'` |
| `in(field, list)` | `IN (...)` |
| `in(field, subQuery)` | `IN (子查询)` |
| `orderByAsc(field)` | `ORDER BY ... ASC` |
| `orderByDesc(field)` | `ORDER BY ... DESC` |

### 排序

```java
List<User> users = db.lambda(User.class)
        .ge(User::getAge, 18)
        .orderByDesc(User::getAge)
        .orderByAsc(User::getName)
        .list();
```

### IN 列表

```java
List<Long> ids = List.of(1L, 2L, 3L);

List<User> users = db.lambda(User.class)
        .in(User::getId, ids)
        .list();
```

### IN 子查询

```java
// 先构建子查询（查出 VIP 用户的 id）
LambdaChain<VipUser> sub = db.lambda(VipUser.class)
        .eq(VipUser::getLevel, "GOLD");

// 再用子查询结果作为 IN 的范围
List<User> users = db.lambda(User.class)
        .in(User::getId, sub)
        .list();
```

### 分页与统计

```java
IPage<User> page = db.lambda(User.class)
        .eq(User::getAge, 25)
        .orderByDesc(User::getId)
        .page(1, 20);

long count = db.lambda(User.class)
        .like(User::getName, "张")
        .count();
```

### 更新与删除

```java
boolean ok = db.lambda(User.class)
        .eq(User::getId, 1L)
        .update(u -> u.setName("李四"));

boolean ok = db.lambda(User.class)
        .lt(User::getAge, 0)
        .delete();
```

---

## 分表路由（Sharding）

`shard(key)` 根据 `key.hashCode() & 15` 将请求路由到 `tableName_0` … `tableName_15` 共 16 张分表。

```java
// 假设数据库中存在 order_0 ~ order_15 共 16 张表
Long userId = 10086L;

List<Order> orders = db.table(Order.class)
        .shard(userId)          // 路由到 order_6（10086 & 15 = 6）
        .where("user_id = {0}", userId)
        .list();
```

> ⚠️ `shard()` 必须在其他条件方法之前调用，因为它只是修改了内部的表名字符串，而不会重置已经构建的 `QueryWrapper`。

---

## 与 MyBatis-Plus 原生 API 的对比

| 场景 | 原生 MyBatis-Plus | mp-knife |
|---|---|---|
| 按条件查列表 | `userMapper.selectList(new QueryWrapper<User>().gt("age", 18))` | `db.table(User.class).where("age > {0}", 18).list()` |
| Lambda 查询 | `userMapper.selectList(new LambdaQueryWrapper<User>().gt(User::getAge, 18))` | `db.lambda(User.class).gt(User::getAge, 18).list()` |
| 不需要注入 Mapper | ✗ 每个实体都要 `@Autowired` | ✓ 只需一个 `DB` 实例 |
| 结果直接投影为 DTO | ✗ 需要手动转换 | ✓ `.list(UserDTO.class)` |
| 分表路由 | ✗ 需要自行实现 | ✓ `.shard(key)` |

---

## License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
