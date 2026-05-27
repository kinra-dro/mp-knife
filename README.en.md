# mp-knife

> A lightweight fluent SQL builder on top of MyBatis-Plus

**[中文](./README.md) | English**

mp-knife wraps MyBatis-Plus `BaseMapper` with a minimal chained query API. No `QueryWrapper` boilerplate, no Spring container required — just hand it a `SqlSession` and go.

## Features

- **Two APIs, pick one**: raw SQL string style (`Chain`) or Lambda type-safe style (`LambdaChain`)
- **Zero intrusion**: no Spring dependency, no annotation scanning — wraps any existing `SqlSession`
- **Auto Mapper discovery**: locates the right `BaseMapper` for each entity from the MyBatis configuration and caches it
- **Built-in sharding**: route to one of 16 sub-tables with a single `.shard(key)` call
- **Result projection**: query results can be converted directly to a DTO without manual mapping

## Dependencies

mp-knife's own dependencies are all `provided` — they must be supplied by your project:

```xml
<!-- Required -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus</artifactId>
    <version>3.5.6</version>
</dependency>

<!-- Required only if you use BeanCopierUtil for result projection -->
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

Then add mp-knife:

```xml
<dependency>
    <groupId>com.mp.knife</groupId>
    <artifactId>mp-knife</artifactId>
    <version>0.1</version>
</dependency>
```

## Quick Start

### 1. Entity and Mapper

```java
// Standard MyBatis-Plus entity
@TableName("user")
public class User {
    @TableId
    private Long id;
    private String name;
    private Integer age;
    private String email;
    // getters / setters ...
}

// Mapper — just extend BaseMapper; mp-knife will find it automatically
public interface UserMapper extends BaseMapper<User> {}
```

### 2. Create a DB instance

```java
// sqlSession comes from your MyBatis SqlSessionFactory
SqlSession sqlSession = sqlSessionFactory.openSession();
DB db = new DB(sqlSession);
```

In a Spring application, use `SqlSessionTemplate`:

```java
@Bean
public DB db(SqlSessionTemplate sqlSessionTemplate) {
    return new DB(sqlSessionTemplate);
}
```

---

## Chain API — SQL String Style

Best for complex conditions or when you already have SQL fragments.

### Query

```java
// Single record
User user = db.table(User.class)
        .where("id = {0}", 1L)
        .first();

// List
List<User> users = db.table(User.class)
        .where("age > {0}", 18)
        .and("email is not null")
        .orderBy("age ASC")
        .list();

// Select specific columns
List<User> users = db.table(User.class)
        .select("id, name")
        .where("age >= {0}", 18)
        .list();
```

### Pagination

```java
// Page 1, 10 records per page
IPage<User> page = db.table(User.class)
        .where("age > {0}", 18)
        .page(1, 10);

long total   = page.getTotal();
List<User> records = page.getRecords();
```

### Count

```java
long count = db.table(User.class)
        .where("age > {0}", 18)
        .count();
```

### OR Conditions

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

> ⚠️ `join()`, `orderBy()`, and `offset()` all append SQL via MyBatis-Plus `wrapper.last()`. **Only the last call to any of these takes effect** within a single query chain; calling them more than once silently discards previous calls.

### Update

```java
boolean ok = db.table(User.class)
        .where("id = {0}", 1L)
        .update(u -> {
            u.setName("Alice");
            u.setAge(25);
        });
```

### Delete

```java
boolean ok = db.table(User.class)
        .where("age < {0}", 0)
        .delete();
```

### Result Projection to DTO

```java
public class UserDTO {
    private Long id;
    private String name;
    // getters / setters ...
}

// Query and convert in one step
List<UserDTO> dtos = db.table(User.class)
        .select("id, name")
        .where("age > {0}", 18)
        .list(UserDTO.class);

// Pagination with projection
IPage<UserDTO> page = db.table(User.class)
        .where("age > {0}", 18)
        .page(1, 10, UserDTO.class);
```

Projection is powered by MapStruct and maps fields **by name**. Make sure the MapStruct annotation processor is on your compiler classpath.

---

## LambdaChain API — Lambda Type-Safe Style

Best when you want compile-time field validation and no string field names.

### Basic Conditions

```java
List<User> users = db.lambda(User.class)
        .eq(User::getName, "Alice")
        .gt(User::getAge, 18)
        .list();
```

### Supported Operators

| Method | Meaning |
|---|---|
| `eq(field, val)` | `=` |
| `ne(field, val)` | `!=` |
| `gt(field, val)` | `>` |
| `ge(field, val)` | `>=` |
| `lt(field, val)` | `<` |
| `le(field, val)` | `<=` |
| `like(field, val)` | `LIKE '%val%'` |
| `in(field, list)` | `IN (...)` |
| `in(field, subQuery)` | `IN (sub-query)` |
| `orderByAsc(field)` | `ORDER BY ... ASC` |
| `orderByDesc(field)` | `ORDER BY ... DESC` |

### Sorting

```java
List<User> users = db.lambda(User.class)
        .ge(User::getAge, 18)
        .orderByDesc(User::getAge)
        .orderByAsc(User::getName)
        .list();
```

### IN with a List

```java
List<Long> ids = List.of(1L, 2L, 3L);

List<User> users = db.lambda(User.class)
        .in(User::getId, ids)
        .list();
```

### IN with a Sub-Query

```java
// Build the sub-query first
LambdaChain<VipUser> sub = db.lambda(VipUser.class)
        .eq(VipUser::getLevel, "GOLD");

// Use its result as the IN range
List<User> users = db.lambda(User.class)
        .in(User::getId, sub)
        .list();
```

### Pagination and Count

```java
IPage<User> page = db.lambda(User.class)
        .eq(User::getAge, 25)
        .orderByDesc(User::getId)
        .page(1, 20);

long count = db.lambda(User.class)
        .like(User::getName, "Ali")
        .count();
```

### Update and Delete

```java
boolean ok = db.lambda(User.class)
        .eq(User::getId, 1L)
        .update(u -> u.setName("Bob"));

boolean ok = db.lambda(User.class)
        .lt(User::getAge, 0)
        .delete();
```

---

## Sharding

`shard(key)` routes the query to one of 16 sub-tables (`tableName_0` … `tableName_15`) by computing `key.hashCode() & 15`.

```java
// Assumes tables order_0 ~ order_15 exist in the database
Long userId = 10086L;

List<Order> orders = db.table(Order.class)
        .shard(userId)            // routes to order_6  (10086 & 15 == 6)
        .where("user_id = {0}", userId)
        .list();
```

> ⚠️ Call `shard()` **before** any other condition method. It only updates the internal table-name string and does not reset the `QueryWrapper` already being built.

---

## Comparison with Native MyBatis-Plus

| Scenario | Native MyBatis-Plus | mp-knife |
|---|---|---|
| Query by condition | `userMapper.selectList(new QueryWrapper<User>().gt("age", 18))` | `db.table(User.class).where("age > {0}", 18).list()` |
| Lambda query | `userMapper.selectList(new LambdaQueryWrapper<User>().gt(User::getAge, 18))` | `db.lambda(User.class).gt(User::getAge, 18).list()` |
| No per-entity Mapper injection | ✗ `@Autowired` every Mapper | ✓ one `DB` instance |
| Direct DTO projection | ✗ manual conversion | ✓ `.list(UserDTO.class)` |
| Sub-table routing | ✗ custom implementation needed | ✓ `.shard(key)` |

---

## License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
