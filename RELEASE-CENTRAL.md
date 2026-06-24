# Maven Central 发布指南

> `groupId`: `io.github.hiwepy` · `artifactId`: `xxljob-spring-boot-starter`  
> 本指南仅说明本地发布步骤，**请勿在 CI/无凭证环境执行 `deploy`**。

## 前置条件

1. **Sonatype OSSRH 账号**（Central Portal / legacy OSSRH）已关联 `io.github.hiwepy`
2. **GPG 密钥**（RSA 4096 推荐），公钥已上传到 keyserver.ubuntu.com 或 keys.openpgp.org
3. 本地 `~/.m2/settings.xml` 配置 `server` 与 `profile`（见下文）

## settings.xml 示例

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username><!-- Sonatype 用户名 --></username>
      <password><!-- Sonatype 令牌/密码 --></password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>ossrh-gpg</id>
      <properties>
        <gpg.keyname><!-- GPG Key ID，如 ABCD1234 --></gpg.keyname>
        <gpg.passphrase><!-- 可选：或使用 gpg-agent --></gpg.passphrase>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>ossrh-gpg</activeProfile>
  </activeProfiles>
</settings>
```

> `distributionManagement` 中 `repository` / `snapshotRepository` 的 `<id>` 必须为 **`ossrh`**，与 settings 中 server id 一致。

## 发布前：render 分支 pom

```bash
cd xxljob-spring-boot-starter
git checkout 3.0.x

# SNAPSHOT（默认当天日期）
python3 scripts/render-branch-pom.py 3.0.x
# → 3.0.x.20260624-SNAPSHOT

# 正式版（去 -SNAPSHOT）
RELEASE=1 RELEASE_DATE=20260624 python3 scripts/render-branch-pom.py 3.0.x
# → 3.0.x.20260624
```

## 发布 SNAPSHOT

```bash
mvn clean deploy -DskipTests
```

SNAPSHOT 将发布至：`https://s01.oss.sonatype.org/content/repositories/snapshots`

## 发布正式版

### 方式一：release 插件（推荐）

```bash
mvn release:clean
mvn release:prepare -DreleaseVersion=3.0.x.20260624 -DdevelopmentVersion=3.0.x.20260625-SNAPSHOT
mvn release:perform
```

### 方式二：手动 deploy

```bash
RELEASE=1 RELEASE_DATE=20260624 python3 scripts/render-branch-pom.py 3.0.x
mvn clean deploy -P release -DskipTests
```

### 关闭 Staging Repository

登录 https://s01.oss.sonatype.org/ → Staging Repositories → 选中本次 staging → **Close** → 校验通过后 **Release**。  
`pom.xml` 已配置 `autoReleaseAfterClose=true`，通常会自动完成。

## release Profile 包含的插件

| 插件 | 作用 |
|------|------|
| `maven-source-plugin` | 附加 `-sources.jar` |
| `maven-javadoc-plugin` | 附加 `-javadoc.jar` |
| `maven-gpg-plugin` | 对构件 GPG 签名 |
| `nexus-staging-maven-plugin` | 上传至 Sonatype staging |

## 版本线说明

| Git 分支 | Starter 版本示例 | Spring Boot | JDK | xxl-job-core |
|----------|-----------------|-------------|-----|--------------|
| `2.7.x` | `2.7.x.20260624-SNAPSHOT` | 2.7.18 | 11 | 2.5.0 |
| `3.0.x` | `3.0.x.20260624-SNAPSHOT` | 3.0.13 | 17 | 3.0.0 |
| `3.1.x` | `3.1.x.{date}-SNAPSHOT` | 3.1.12 | 17 | 3.1.1 |
| `3.2.x` | `3.2.x.{date}-SNAPSHOT` | 3.2.12 | 17 | 3.2.0 |
| `3.3.x` | `3.3.x.{date}-SNAPSHOT` | 3.3.6 | 17 | 3.3.2 |
| `3.4.x` | `3.4.x.{date}-SNAPSHOT` | 3.4.2 | 17 | 3.4.2 |

## 阿里云私有仓库（可选）

```bash
mvn clean deploy -Paliyun-deploy -DskipTests
```

## 常见问题

- **401 Unauthorized**：检查 settings.xml 中 `ossrh` 凭证
- **GPG 签名失败**：确认 `gpg-agent` 运行或配置 `gpg.passphrase`
- **Javadoc 报错**：已启用 `doclint=none` profile（JDK 17+）
- **Staging 校验失败**：检查 `groupId`/`artifactId` 是否与 Central 上已有坐标一致
