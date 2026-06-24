# xxljob-spring-boot-starter

> XXL-JOB 是一个分布式任务调度平台，其核心设计目标是开发迅速、学习简单、轻量级、易扩展。

- 官方文档：https://www.xuxueli.com/xxl-job/
- GitHub：https://github.com/xuxueli/xxl-job/

## 组件简介

基于 [xxl-job](https://github.com/xuxueli/xxl-job/) 的 Spring Boot Starter 封装，提供以下增强能力：

- **`@XxlJobCron` 注解**：可 100% 替代 `@XxlJob`，支持独立使用，自动注册定时任务到 Admin
- **双注解兼容**：支持 `@XxlJob` + `@XxlJobCron` 组合使用，向后兼容
- **Micrometer 指标采集**：内置任务执行耗时、提交/完成/运行计数等指标
- **多版本 Admin 兼容**：支持 xxl-job-admin 2.x / 3.x（V2_X / V3_2_X / V3_X）

## 多分支版本模型（对齐 opencli）

Git **分支名 = 产品线前缀**。每条分支通过 `scripts/render-branch-pom.py` 固定 Spring Boot parent、JDK、`xxl-job-core` 与 Maven 坐标：

```bash
git checkout 3.0.x
python3 scripts/render-branch-pom.py 3.0.x
mvn clean test -DskipTests=false
mvn clean install
```

Maven 坐标：`{分支前缀}.{日期}-SNAPSHOT`，例如 `3.0.x.20260624-SNAPSHOT`。  
正式版：`RELEASE=1 RELEASE_DATE=20260624 python3 scripts/render-branch-pom.py 3.0.x` → `3.0.x.20260624`。

### Starter 分支矩阵

| Git 分支 | Maven 坐标示例 | Spring Boot | JDK | xxl-job-core | 执行器 API |
|----------|----------------|-------------|-----|--------------|------------|
| `2.7.x` | `2.7.x.20260624-SNAPSHOT` | 2.7.18 | 11 | 2.5.0 | `registJobHandler` |
| **`3.0.x`** | **`3.0.x.{date}-SNAPSHOT`** | **3.0.13** | 17 | **3.0.0** | `registJobHandler` |
| `3.1.x` | `3.1.x.{date}-SNAPSHOT` | 3.1.12 | 17 | 3.1.1 | `registJobHandler` |
| `3.2.x` | `3.2.x.{date}-SNAPSHOT` | 3.2.12 | 17 | 3.2.0 | `registJobHandler` |
| `3.3.x` | `3.3.x.{date}-SNAPSHOT` | 3.3.6 | 17 | 3.3.2 | `registryJobHandler` |
| `3.4.x` | `3.4.x.{date}-SNAPSHOT` | 3.4.2 | 17 | 3.4.2 | `registryJobHandler` |

> **两层版本语义**：Starter 分支决定接入方 Spring Boot 线；`xxl-job-core` 决定执行器 RPC；`AdminVersion`（Nacos）决定 Admin HTTP Web API 路径，三者独立配置。

### Admin 协议版本（`xxl.job.admin.version`）

| AdminVersion | 适用 xxl-job-admin | HTTP 特征 | 推荐 starter 分支 |
|--------------|-------------------|-----------|-------------------|
| `V2_X`（默认） | 2.x、3.0.0、3.1.x | `/login`、`save`/`add`/`remove`、Cookie `XXL_JOB_LOGIN_IDENTITY` | `3.0.x`（core 3.0.0） |
| `V3_2_X` | 3.2.0 | `/auth/doLogin`，CRUD 仍 V2 路径 | `3.2.x` |
| `V3_X` | 3.3.0+ | `insert`/`delete`、`ids[]`、`offset`/`pagesize` | `3.3.x`+（core 3.3+） |

## 注解扫描机制

本组件采用**双注解独立扫描 + 优先级回退**策略，兼容 `@XxlJob` 和 `@XxlJobCron` 两种注解模式。

### 为什么不用元注解？

`@XxlJob` 的 `@Target` 仅包含 `ElementType.METHOD`，不支持 `ElementType.ANNOTATION_TYPE`，因此无法作为元注解直接标注在 `@XxlJobCron` 上。

### 扫描流程

```
扫描阶段（XxlJobAutoBindingSpringExecutor.initJobHandlerMethodRepository）
┌──────────────────────────────────────────────────────────────────────┐
│ 1. 扫描 @XxlJob      → Map<Method, XxlJob>                         │
│ 2. 扫描 @XxlJobCron  → Map<Method, XxlJobCron>                     │
│ 3. 合并两种模式的方法集（LinkedHashSet 去重）                              │
│ 4. 优先用 @XxlJobCron.value() 作为 handlerName                      │
│ 5. 注册到执行器 + 自动绑定到 Admin                                     │
└──────────────────────────────────────────────────────────────────────┘
```

## 使用说明

### 1、添加 Maven 依赖

**3.0.x 线（Spring Boot 3.0.13 / JDK 17 / core 3.0.0，对接 admin 3.0.0）**

```xml
<dependency>
    <groupId>io.github.hiwepy</groupId>
    <artifactId>xxljob-spring-boot-starter</artifactId>
    <version>3.0.x.20260624-SNAPSHOT</version>
</dependency>
```

**2.7.x 遗留线（Spring Boot 2.7 / JDK 11 / core 2.5.0）**

```xml
<dependency>
    <groupId>io.github.hiwepy</groupId>
    <artifactId>xxljob-spring-boot-starter</artifactId>
    <version>2.7.x.20260624-SNAPSHOT</version>
</dependency>
```

### 2、配置

#### application.yaml

```yaml
xxl:
  job:
    accessToken: default_token
    admin:
      addresses: http://localhost:8091/xxl-job-admin
      username: admin
      password: 123456
      # admin 3.0.0 用 V2_X；admin 3.3+ 须换 starter 3.3.x 分支并设 V3_X
      version: V2_X
      cookie:
        maximum-size: 1000
        expire-after-write: 5s
        refresh-after-write: 5s
    executor:
      ip:
      app-name: default-job-executor
      title: 任务执行器
      port: 31734
      log-path: /logs/xxl-job/jobhandler
      log-retention-days: 30
```

### 3、使用示例

#### 方式一：仅使用 `@XxlJobCron`（推荐）

```java
@Component
@Slf4j
public class SampleXxlJob {

    @XxlJobCron(value = "demoJobHandler", cron = "0/10 * * * * ?", desc = "简单任务示例", author = "admin")
    public void demoJobHandler() throws Exception {
        XxlJobHelper.log("XXL-JOB, Hello World.");
    }
}
```

### 4、`@XxlJobCron` 属性说明

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | `""` | JobHandler 名称 |
| `cron` | String | `""` | CRON 表达式 |
| `blockStrategy` | ExecutorBlockStrategyEnum | `COVER_EARLY` | 阻塞处理策略（来自 xxl-job-core） |

### 5、Micrometer 指标采集

```yaml
xxl:
  job:
    metrics:
      enabled: true
```

### 6、自动配置发现

同时提供 `META-INF/spring.factories` 与 `AutoConfiguration.imports`，兼容 Spring Boot 2.7 与 3.x。

### 7、Maven Central 发布

详见 [RELEASE-CENTRAL.md](RELEASE-CENTRAL.md)。

```bash
# SNAPSHOT（render 后）
mvn clean deploy -DskipTests

# 正式版
RELEASE=1 RELEASE_DATE=20260624 python3 scripts/render-branch-pom.py 3.0.x
mvn clean deploy -P release -DskipTests
```

## Jeebiz 技术社区

Jeebiz 技术社区 **微信公共号**、**小程序**，欢迎关注反馈意见和一起交流，关注公众号回复「Jeebiz」拉你入群。
