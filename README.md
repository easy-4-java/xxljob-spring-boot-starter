# xxljob-spring-boot-starter

> XXL-JOB 是一个分布式任务调度平台，其核心设计目标是开发迅速、学习简单、轻量级、易扩展。

- 官方文档：https://www.xuxueli.com/xxl-job/
- GitHub：https://github.com/xuxueli/xxl-job/

## 组件简介

基于 [xxl-job](https://github.com/xuxueli/xxl-job/) 的 Spring Boot Starter 封装，提供以下增强能力：

- **`@XxlJobCron` 注解**：可 100% 替代 `@XxlJob`，支持独立使用，自动注册定时任务到 Admin
- **双注解兼容**：支持 `@XxlJob` + `@XxlJobCron` 组合使用，向后兼容
- **Micrometer 指标采集**：内置任务执行耗时、提交/完成/运行计数等指标
- **多版本 Admin 兼容**：支持 xxl-job-admin 2.x / 3.x

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

执行阶段（MetricMethodJobHandler.execute）
┌──────────────────────────────────────────────────────────────────────┐
│ 1. 查找 @XxlJob      → 有则用 value()                               │
│ 2. 查找 @XxlJobCron  → 有则用 value() / desc()                      │
│ 3. 都没有            → 兜底用 method.getName()                       │
└──────────────────────────────────────────────────────────────────────┘
```

### 使用场景对照

| 场景 | 注解 | 扫描 | 执行 | 说明 |
|------|------|------|------|------|
| 仅 `@XxlJob("a")` | ✅ | ✅ 用 `a` | ✅ 用 `a` | 官方原生用法 |
| 仅 `@XxlJobCron("b")` | ✅ | ✅ 用 `b` | ✅ 用 `b` | 本组件推荐用法 |
| 两者同时存在 | ✅ | ✅ 合并 | ✅ 优先 `@XxlJobCron` | 向后兼容 |

## 使用说明

### 1、添加 Maven 依赖

```xml
<dependency>
    <groupId>com.github.hiwepy</groupId>
    <artifactId>xxljob-spring-boot-starter</artifactId>
    <version>${project.version}</version>
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

#### K8s 部署

```yaml
xxl:
  job:
    executor:
      ip: [使用k8s主节点IP]
      app-name: default-job-executor
      title: 任务执行器
      port: 31734
```

> 指定 xxl-job 执行器端口，并配置宿主服务的 Service 对外暴露端口与 xxl-job 执行器端口相同！

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-xxx-job-svc
spec:
  ports:
    - name: tcp-31734
      protocol: TCP
      port: 31734
      targetPort: 31734
      nodePort: 31734
  selector:
    app: my-xxx-job
  type: NodePort
```

### 3、使用示例

#### 方式一：仅使用 `@XxlJobCron`（推荐）

```java
@Component
@Slf4j
public class SampleXxlJob {

    /**
     * 简单任务 —— 只需 @XxlJobCron，无需 @XxlJob
     */
    @XxlJobCron(value = "demoJobHandler", cron = "0/10 * * * * ?", desc = "简单任务示例", author = "admin")
    public void demoJobHandler() throws Exception {
        XxlJobHelper.log("XXL-JOB, Hello World.");
        for (int i = 0; i < 5; i++) {
            XxlJobHelper.log("beat at:" + i);
            TimeUnit.SECONDS.sleep(2);
        }
    }

    /**
     * 带生命周期的任务
     */
    @XxlJobCron(value = "lifecycleJob", cron = "0/30 * * * * ?", desc = "生命周期任务", author = "admin",
                init = "initMethod", destroy = "destroyMethod")
    public void lifecycleJob() throws Exception {
        XxlJobHelper.log("lifecycle job running");
    }

    public void initMethod() {
        log.info("job init");
    }

    public void destroyMethod() {
        log.info("job destroy");
    }
}
```

#### 方式二：`@XxlJob` + `@XxlJobCron` 组合（向后兼容）

```java
@Component
public class LegacyXxlJob {

    @XxlJob("shardingJobHandler")
    @XxlJobCron(cron = "0/10 * * * * ?", desc = "分片广播任务", author = "admin")
    public void shardingJobHandler() throws Exception {
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        XxlJobHelper.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);
    }
}
```

#### 方式三：仅使用 `@XxlJob`（官方原生）

```java
@Component
public class NativeXxlJob {

    @XxlJob("nativeJobHandler")
    public void nativeJobHandler() throws Exception {
        XxlJobHelper.log("native job");
    }
}
```

### 4、`@XxlJobCron` 属性说明

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | `""` | JobHandler 名称（等同于 `@XxlJob.value()`） |
| `cron` | String | `""` | CRON 表达式 |
| `desc` | String | `""` | 任务描述 |
| `author` | String | `"xxl-job"` | 负责人 |
| `alarmEmail` | String | `""` | 报警邮件 |
| `scheduleType` | ScheduleTypeEnum | `CRON` | 调度类型 |
| `glueType` | GlueTypeEnum | `BEAN` | GLUE 类型 |
| `routeStrategy` | ExecutorRouteStrategyEnum | `LEAST_FREQUENTLY_USED` | 路由策略 |
| `blockStrategy` | ExecutorBlockStrategyEnum | `COVER_EARLY` | 阻塞处理策略 |
| `misfireStrategy` | MisfireStrategyEnum | `DO_NOTHING` | 调度过期策略 |
| `timeout` | int | `3000` | 超时时间（毫秒） |
| `failRetryCount` | int | `3` | 失败重试次数 |
| `param` | String | `""` | 任务参数 |
| `init` | String | `""` | 初始化方法名 |
| `destroy` | String | `""` | 销毁方法名 |
| `selfStarting` | boolean | `false` | 是否自启动 |

### 5、Micrometer 指标采集

引入依赖：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

启用配置：

```yaml
xxl:
  job:
    metrics:
      enabled: true
```

采集指标：

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `xxl_job_submitted_total` | Counter | 任务提交总数 |
| `xxl_job_running_total` | Gauge | 当前运行中任务数 |
| `xxl_job_completed_total` | Counter | 任务完成总数 |
| `xxl_job_duration_seconds` | Timer | 任务执行耗时 |
| `xxl_job_queue_size_total` | Gauge | 回调队列大小 |

### 6、兼容性说明

| xxl-job-core 版本 | xxl-job-admin 版本 | 本组件兼容 |
|-------------------|-------------------|-----------|
| 2.5.0 | 2.5.0 | ✅ |
| 2.5.0 | 3.3.0 | ✅ |
| 2.5.0 | 3.4.1 | ❌ (Spring Boot 版本不兼容) |

> **注意**：xxl-job 3.x 要求 Spring Boot 3.4+ / JDK 17+，本组件基于 Spring Boot 2.7.x 构建，最高兼容 xxl-job-admin 3.3.0。

## Jeebiz 技术社区

Jeebiz 技术社区 **微信公共号**、**小程序**，欢迎关注反馈意见和一起交流，关注公众号回复「Jeebiz」拉你入群。

|公共号|小程序|
|---|---|
| ![](https://raw.githubusercontent.com/hiwepy/static/main/images/qrcode_for_gh_1d965ea2dfd1_344.jpg)| ![](https://raw.githubusercontent.com/hiwepy/static/main/images/gh_09d7d00da63e_344.jpg)|
