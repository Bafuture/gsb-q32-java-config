# config4j：配置加载与校验库

一个零外部依赖（仅 JDK 17）的轻量配置库，解决「同一份配置散落在各处、没人说得清哪份生效」的问题。
支持多来源分层覆盖、类型转换、注解校验、精确错误定位、嵌套结构映射与配置文件热刷新。

- 构建验证：`mvn -q verify`（或 `./mvnw -q verify`）
- 测试框架：JUnit 5 + AssertJ
- JDK：17

---

## 1. 快速上手

```java
// 1) 定义配置类（普通 POJO，字段初始化值即「代码内默认值」）
public class AppConfig {
    public String name = "my-app";
    public int port = 8080;
    public Server server = new Server();
    public static class Server {
        public String host = "localhost";
        public Duration timeout = Duration.ofSeconds(10);
    }
}

// 2) 按优先级装配并加载
AppConfig config = ConfigLoader.builder()
        .defaults(Map.of("port", "9000"))          // 代码内默认值（最低）
        .configFile(Path.of("app.properties"))     // 配置文件
        .environment()                             // 环境变量
        .commandLine(args)                         // 启动参数（最高）
        .build()
        .load(AppConfig.class);
```

配置文件 `app.properties`：

```properties
name = billing-service
server.host = 0.0.0.0
server.timeout = 30s
```

启动：`java -jar app.jar --port=80 --name=cli-name`，最终 `port=80`（启动参数胜出）。

---

## 2. 优先级设计

来源按优先级 **从低到高** 固定排列，高优先级对同一配置键整体覆盖低优先级；
低优先级来源中存在、高优先级未提供的键保持原值（逐键覆盖，而非整层覆盖）：

| 顺序 | 来源 | 构造方式 | 键的写法示例 |
|----|----|----|----|
| 1（最低） | 代码内默认值 | `defaults(Map)` + 配置类字段初始化值 | `server.port` |
| 2 | 配置文件（Properties） | `configFile(Path)` | `server.port=8080` |
| 3 | 环境变量 | `environment()` / `environment(Map)` | `SERVER_PORT=8080` |
| 4（最高） | 启动参数 | `commandLine(args)` | `--server.port=8080` |

查询某个键时，绑定器从最高优先级向最低回溯，取第一个命中的值；任何来源都没有时，
保留字段在 Java 代码中的初始化值（即字段内默认值）。

优先级的逐层覆盖关系由 `PriorityTest` 中的测试逐个验证：
仅默认值 → 文件逐键覆盖默认值 → 环境变量覆盖文件 → 启动参数覆盖一切。

### 2.1 启动参数语法

- `--key=value`
- `--key value`（下一参数不以 `--` / `-D` 开头时作为其值）
- `-Dkey=value`（JVM 系统属性风格）

### 2.2 环境变量名规则

配置键统一规范化为 kebab-case，环境变量名再做如下变换后整体大写：

- `.` 与 `-` → `_`
- 列表下标 `[0]` → `_0`（`]` 删除）

| 配置键 | 环境变量名 |
|----|----|
| `server.port` | `SERVER_PORT` |
| `connect-timeout` | `CONNECT_TIMEOUT` |
| `endpoints[0].host` | `ENDPOINTS_0_HOST` |

> 注意：环境变量无法表达 camelCase 的词边界，因此从环境变量反向发现键时一律按 kebab-case 处理。
> 请在配置键中使用 kebab-case（如 `max-size` 而非 `maxSize`），字段名仍可使用 `maxSize`。

---

## 3. 结构映射与命名规则

### 3.1 字段名 → 配置键

- Java 字段使用 `camelCase`，配置键使用 `kebab-case`，自动转换：
  - `serverPort` ↔ `server-port`
  - `connectTimeoutMs` ↔ `connect-timeout-ms`
  - 单词 `port` 保持 `port`
- 嵌套对象用 `.` 连接：字段 `server.host` → 配置键 `server.host`
- 配置类必须有 **无参构造方法**；嵌套对象会被自动实例化，也可在字段上预先 `new`
- 支持父类字段（沿继承链向上收集非 static 字段）

### 3.2 列表

- **标量列表**（`List<Integer>`、`List<String>` 等）支持两种写法：
  - 逗号分隔：`ports=8080, 8081, 8082`（元素自动去首尾空白，空元素忽略）
  - 下标形式：`ports[0]=8080`、`ports[1]=8081`
- **对象列表**用下标 + 子键：

```properties
endpoints[0].host = a.example.com
endpoints[0].port = 80
endpoints[1].host = b.example.com
endpoints[1].port = 81
```

下标可来自任意来源（包括环境变量 `ENDPOINTS_0_HOST`、启动参数 `--endpoints[0].port=80`）。
未出现在任何来源中的字段保留 Java 默认值；无法识别的多余键会被忽略（见「已知限制」）。

---

## 4. 类型转换规则

原始值始终是字符串，由 `TypeConverter` 转换为字段类型：

| 目标类型 | 支持的写法 |
|----|----|
| `String` | 原样保留 |
| `char` / `Character` | 长度必须为 1 |
| `boolean` / `Boolean` | `true/false`、`1/0`、`yes/no`、`on/off`（忽略大小写） |
| `byte/short/int/long/float/double` 及包装类 | 与 JDK `valueOf` 一致（允许首尾空白） |
| 任意 `enum` | 按枚举常量名匹配，**忽略大小写**，错误时列出所有合法常量 |
| `java.time.Duration` | 简洁写法 `500ms`、`30s`、`5m`、`2h`、`1d`；也接受 ISO-8601（`PT30S`） |
| `java.nio.file.Path` | 文件路径，使用 `Paths.get(...)` |
| `List<T>` | 逗号分隔，元素递归使用同一套转换规则 |

时长简洁写法的单位：`ms`=毫秒、`s`=秒、`m`=分、`h`=时、`d`=天；数字与单位之间允许空白。
可通过 `typeConverter.register(MyType.class, raw -> ...)` 注册自定义转换器。

### 4.1 转换失败的错误信息

任何转换失败都明确给出 **配置键 + 期望类型 + 原始值 + 原因**，例如：

```
配置类型转换失败: 配置键=level, 期望类型=枚举 Level, 原始值='VERBOSE',
原因=不是合法的枚举常量，可选值: TRACE, DEBUG, INFO, WARN, ERROR
```

标量列表元素转换失败时，错误定位到列表键本身（原始值为出错的那个元素）。

---

## 5. 校验注解

三个注解均为自研，运行期保留，标注在字段上。绑定完成后统一校验，
**一次性汇总全部转换错误与校验错误**（不会遇到第一个错误就中止），通过
`ConfigBindingException` 抛出：

- `getConversionErrors()`：类型转换错误列表（`ConversionException`）
- `getViolations()`：校验违规描述列表（`String`）

### 5.1 `@Required`

必填：`null` 不通过；`String` 全空白不通过；数组/集合/`Map` 为空不通过。
可嵌套作用于对象字段（递归校验内部字段，错误键带完整路径如 `db.url`）。

### 5.2 `@Range`

数值闭区间校验，适用于所有数值类型（含包装类）：

```java
@Range(min = 1, max = 65535)
private int port;
```

### 5.3 `@Pattern`

正则匹配（`String#matches` 语义，需匹配整串），作用于 `String` 或 `List<String>` 的每个元素：

```java
@Pattern(regexp = "\\d{1,3}(\\.\\d{1,3}){3}", message = "host 必须是 IPv4 地址")
private String host;
```

三个注解都支持 `message()` 自定义错误说明；非法正则本身也会作为一个问题被汇总报告。

---

## 6. 配置文件格式与错误定位

文件格式为 Properties（UTF-8），由自研解析器 `PropertiesParser` 解析（不使用
`java.util.Properties`），因此错误可以精确定位：

- `#` 或 `!` 开头为注释；空行忽略
- 分隔符：第一个未转义的 `=`、`:` 或空白
- 行尾单个 `\` 表示续行
- 支持转义：`\n \t \r \\ \= \: \# \! \ ` 以及 `\uXXXX`
- 键不能为空；重复键后者覆盖前者

格式错误抛出 `ConfigParseException`，消息包含 **文件路径 + 行号 + 原因**，
续行场景下行号定位到出错字符所在的物理行，例如：

```
配置文件格式错误: 文件=/etc/app/app.properties, 行号=3, 原因=非法的转义序列: \q
```

文件不存在不算错误，视为空来源（配置文件可选）。

---

## 7. 动态刷新

```java
ConfigLoader loader = ConfigLoader.builder()
        .defaults(Map.of("name", "default"))
        .configFile(Path.of("app.properties"))
        .environment()
        .commandLine(args)
        .build();

ConfigManager<AppConfig> manager = new ConfigManager<>(AppConfig.class, loader);
AppConfig config = manager.init();                 // 首次加载

manager.addListener(new ConfigListener() {
    @Override public void onReload(Object fresh) { /* 新配置已生效 */ }
    @Override public void onError(Throwable err)  { /* 刷新失败，旧配置仍在 */ }
});

manager.refresh();                                 // 手动刷新
manager.startWatching(500, TimeUnit.MILLISECONDS); // 轮询文件 mtime 自动刷新
// ...
manager.close();                                   // 停止后台线程（守护线程）
```

刷新语义：

- **成功**：重新读取文件 → 重新转换、校验 → 原子替换当前配置 → 回调 `onReload`
- **失败**（格式错、转换错、校验错、IO 错）：**保留旧配置不变**，回调 `onError`，并重新抛出异常
- 环境变量与启动参数在进程内不变，刷新只重读配置文件；它们的高优先级在刷新后依旧生效

---

## 8. 包结构

```
com.example.config
├── annotation     @Required / @Range / @Pattern
├── exception      ConfigException 体系（解析/转换/绑定聚合）
├── source         ConfigSource 及四种来源、Keys 命名规则
├── parse          PropertiesParser（带行号）
├── convert        TypeConverter、Converter、DurationStyle
├── binding        ConfigBinder（嵌套/列表映射 + 错误聚合）
├── validation     Validator（递归校验）
└── core           ConfigLoader、ConfigManager、ConfigListener
```

---

## 9. 已知限制

1. **配置文件只支持 Properties**，不支持 YAML/JSON。
2. 配置类必须提供无参构造方法；字段注入基于反射，不支持 record、builder、`final` 字段与 setter 绑定。
3. 不支持 `Map` 类型字段；集合仅支持 `List`（标量列表或对象列表）。
4. 未知配置键（文件/环境变量里写错的键名）默认 **静默忽略**，不报错；
   标量列表同时存在逗号写法与下标写法时，以下标写法为准。
5. 环境变量名到配置键的反向映射无法还原 camelCase 词边界（`SERVER_PORT` → `server-port`），
   请统一使用 kebab-case 键；极端情况下两个不同键可能映射到同一个环境变量名（如 `a.b` 与 `a-b`）。
6. 自动刷新基于轮询文件 `mtime`，不是 inotify 事件；检测延迟等于轮询周期，
   某些网络文件系统上 `mtime` 精度可能影响时效。
7. 监听回调在刷新线程（轮询线程）上同步执行，监听器内不应做阻塞操作；回调异常需自行处理。
8. 校验注解只作用于字段，不支持方法参数/返回值/类型注解；`@Range` 与 `@Pattern`
   不会直接作用于对象列表字段本身（会递归作用到元素内被标注的字段）。
9. Properties 转义仅支持解析器列出的几种，非法转义会直接报错而非忽略。
