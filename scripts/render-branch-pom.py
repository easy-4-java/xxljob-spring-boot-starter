#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
按分支名重写 xxljob-spring-boot-starter 的 pom.xml。

与 opencli-spring-boot-starter 坐标线对齐：同一分支名使用相同版本前缀（如 3.0.x.*-SNAPSHOT），
Spring Boot parent、编译 JDK、xxl-job-core 随 Git 分支固定。

JDK 基线:
  2.3.x -> JDK 8
  2.7.x -> JDK 11
  3.0.x-3.4.x -> JDK 17

执行器 API（源码侧按分支维护，脚本仅写入文档属性）:
  regist   -> core 2.5.0 ~ 3.2.x（registJobHandler）
  registry -> core 3.3.x+（registryJobHandler）

用法:
  python3 scripts/render-branch-pom.py <branch>
  RELEASE=1 RELEASE_DATE=20260624 python3 scripts/render-branch-pom.py 3.0.x
"""
from __future__ import annotations

import os
import pathlib
import sys
from datetime import date

ROOT = pathlib.Path(__file__).resolve().parents[1]
POM = ROOT / "pom.xml"


def version_date_suffix() -> str:
    """SNAPSHOT: {date}-SNAPSHOT；RELEASE(RELEASE=1): 仅 {date}。"""
    raw = os.environ.get("RELEASE_DATE", "").strip()
    day = raw if raw else date.today().strftime("%Y%m%d")
    if os.environ.get("RELEASE", "").strip().lower() in ("1", "true", "yes"):
        return day
    return f"{day}-SNAPSHOT"


VERSION_DATE_SUFFIX = version_date_suffix()

EXECUTOR_JAVA = [
    ROOT / "src/main/java/com/xxl/job/spring/boot/XxlJobAutoBindingSpringExecutor.java",
    ROOT
    / "src/main/java/com/xxl/job/spring/boot/XxlJobAutoBindingAndMetricsSpringExecutor.java",
]

# (boot parent, java.version, version prefix, xxl-job-core, executor API hint, use maven.compiler.release)
MATRIX = {
    "2.3.x": ("2.3.12.RELEASE", "1.8", "2.3.x", "2.5.0", "regist", False),
    "2.7.x": ("2.7.18", "11", "2.7.x", "2.5.0", "regist", True),
    "3.0.x": ("3.0.13", "17", "3.0.x", "3.0.0", "regist", True),
    "3.1.x": ("3.1.12", "17", "3.1.x", "3.1.1", "regist", True),
    "3.2.x": ("3.2.12", "17", "3.2.x", "3.2.0", "regist", True),
    "3.3.x": ("3.3.6", "17", "3.3.x", "3.3.2", "registry", True),
    "3.4.x": ("3.4.2", "17", "3.4.x", "3.4.2", "registry", True),
}


def patch_executor_api(executor_api: str) -> None:
    """按 xxl-job-core 线切换 registJobHandler / registryJobHandler。"""
    if executor_api == "regist":
        replacements = [
            ("protected void registryJobHandler(", "protected void registJobHandler("),
            ("XxlJobExecutor.registryJobHandler(", "XxlJobExecutor.registJobHandler("),
            ("registryJobHandler(xxlJob,", "registJobHandler(xxlJob,"),
            ("protected registryJobHandler 实例方法", "protected registJobHandler 实例方法"),
        ]
    elif executor_api == "registry":
        replacements = [
            ("protected void registJobHandler(XxlJob", "protected void registryJobHandler(XxlJob"),
            ("XxlJobExecutor.registJobHandler(", "XxlJobExecutor.registryJobHandler("),
            ("registJobHandler(xxlJob,", "registryJobHandler(xxlJob,"),
            ("protected registJobHandler 实例方法", "protected registryJobHandler 实例方法"),
        ]
    else:
        raise ValueError(f"unknown executor_api: {executor_api}")

    for path in EXECUTOR_JAVA:
        if not path.is_file():
            print(f"skip executor patch, missing: {path}", file=sys.stderr)
            continue
        text = path.read_text(encoding="utf-8")
        for old, new in replacements:
            text = text.replace(old, new)
        path.write_text(text, encoding="utf-8")


def patch_block_strategy_import(executor_api: str) -> None:
    """core <=3.2 用 enums 包；core >=3.3 用 constant 包。"""
    local_enum = ROOT / "src/main/java/com/xxl/job/spring/boot/executor/ExecutorBlockStrategyEnum.java"
    cron = ROOT / "src/main/java/com/xxl/job/spring/boot/annotation/XxlJobCron.java"
    helper = ROOT / "src/main/java/com/xxl/job/spring/boot/util/XxlJobHelper.java"
    core_enums = "import com.xxl.job.core.enums.ExecutorBlockStrategyEnum;"
    core_constant = "import com.xxl.job.core.constant.ExecutorBlockStrategyEnum;"
    local_import = "import com.xxl.job.spring.boot.executor.ExecutorBlockStrategyEnum;"

    if executor_api == "regist":
        for path in (cron, helper):
            if not path.is_file():
                continue
            text = path.read_text(encoding="utf-8")
            text = text.replace(local_import, core_enums)
            text = text.replace(core_constant, core_enums)
            path.write_text(text, encoding="utf-8")
        if local_enum.is_file():
            local_enum.unlink()
            print(f"removed local enum (use core): {local_enum.relative_to(ROOT)}")
    else:
        for path in (cron, helper):
            if not path.is_file():
                continue
            text = path.read_text(encoding="utf-8")
            text = text.replace(core_enums, core_constant)
            text = text.replace(local_import, core_constant)
            path.write_text(text, encoding="utf-8")


def compiler_config(*, java_version: str, use_release: bool) -> str:
    if use_release:
        return f"""					<configuration>
						<release>{java_version}</release>
						<encoding>${{project.build.sourceEncoding}}</encoding>
						<maxmem>512M</maxmem>
					</configuration>"""
    return f"""					<configuration>
						<source>{java_version}</source>
						<target>{java_version}</target>
						<encoding>${{project.build.sourceEncoding}}</encoding>
						<maxmem>512M</maxmem>
					</configuration>"""


def servlet_dependency(boot_parent: str) -> str:
    """Boot 2.x 使用 javax.servlet；Boot 3.x 使用 jakarta.servlet。"""
    if boot_parent.startswith("2."):
        return """		<dependency>
			<groupId>javax.servlet</groupId>
			<artifactId>javax.servlet-api</artifactId>
			<scope>provided</scope>
		</dependency>"""
    return """		<dependency>
			<groupId>jakarta.servlet</groupId>
			<artifactId>jakarta.servlet-api</artifactId>
		</dependency>"""


def write_pom(
    *,
    boot_parent: str,
    java_version: str,
    version_prefix: str,
    xxl_job_core: str,
    executor_api: str,
    use_release: bool,
) -> None:
    ver = f"{version_prefix}.{VERSION_DATE_SUFFIX}"
    comp = compiler_config(java_version=java_version, use_release=use_release)
    servlet = servlet_dependency(boot_parent)
    body = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>{boot_parent}</version>
		<relativePath />
	</parent>

	<groupId>io.github.hiwepy</groupId>
	<artifactId>xxljob-spring-boot-starter</artifactId>
	<description>Spring Boot Starter For XXL-Job (line {version_prefix}; SB {boot_parent}; JDK {java_version}; core {xxl_job_core}; executor API {executor_api})</description>
	<version>{ver}</version>
	<name>${{project.groupId}}:${{project.artifactId}}</name>
	<url>https://github.com/hiwepy/${{project.artifactId}}</url>
	<packaging>jar</packaging>

	<licenses>
		<license>
			<name>The Apache Software License, Version 2.0</name>
			<url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
		</license>
	</licenses>

	<scm>
		<connection>scm:git:https:github.com/hiwepy/${{project.artifactId}}.git</connection>
		<developerConnection>scm:git:https:github.com/hiwepy/${{project.artifactId}}.git</developerConnection>
		<url>https:github.com/hiwepy/${{project.artifactId}}</url>
		<tag>${{project.artifactId}}</tag>
	</scm>

	<developers>
		<developer>
			<name>wandl</name>
			<email>hiwepy@gmail.com</email>
			<roles>
				<role>developer</role>
			</roles>
			<timezone>+8</timezone>
		</developer>
	</developers>

	<distributionManagement>
		<repository>
			<id>ossrh</id>
			<name>Maven Central Staging Repository</name>
			<url>https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/</url>
		</repository>
		<snapshotRepository>
			<id>ossrh</id>
			<name>Maven Central Snapshot Repository</name>
			<url>https://s01.oss.sonatype.org/content/repositories/snapshots</url>
		</snapshotRepository>
	</distributionManagement>

	<build>
		<pluginManagement>
			<plugins>
				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-compiler-plugin</artifactId>
					<version>${{maven-compiler-plugin.version}}</version>
{comp}
				</plugin>
				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-enforcer-plugin</artifactId>
					<version>${{maven-enforcer-plugin.version}}</version>
					<executions>
						<execution>
							<id>default-cli</id>
							<goals>
								<goal>enforce</goal>
							</goals>
							<phase>validate</phase>
							<configuration>
								<rules>
									<requireMavenVersion>
										<message>
	                                        <![CDATA[You are running an older version of Maven. This application requires at least Maven ${{maven.version}}.]]>
										</message>
										<version>[${{maven.version}}.0,)</version>
									</requireMavenVersion>
									<requireJavaVersion>
										<message>
	                                        <![CDATA[You are running an older version of Java. This application requires at least JDK ${{java.version}}.]]>
										</message>
										<version>[${{java.version}}.0,)</version>
									</requireJavaVersion>
								</rules>
							</configuration>
						</execution>
					</executions>
				</plugin>
				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-gpg-plugin</artifactId>
					<version>${{maven-gpg-plugin.version}}</version>
					<executions>
						<execution>
							<id>sign-artifacts</id>
							<phase>verify</phase>
							<goals>
								<goal>sign</goal>
							</goals>
						</execution>
					</executions>
				</plugin>
				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-resources-plugin</artifactId>
					<version>${{maven-resources-plugin.version}}</version>
					<configuration>
						<encoding>${{project.build.sourceEncoding}}</encoding>
					</configuration>
				</plugin>
				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-release-plugin</artifactId>
					<version>${{maven-release-plugin.version}}</version>
					<configuration>
						<tagNameFormat>v@{{project.version}}</tagNameFormat>
						<autoVersionSubmodules>true</autoVersionSubmodules>
						<useReleaseProfile>false</useReleaseProfile>
						<releaseProfiles>release</releaseProfiles>
						<goals>deploy</goals>
					</configuration>
				</plugin>
				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-source-plugin</artifactId>
					<version>${{maven-source-plugin.version}}</version>
					<configuration>
						<attach>true</attach>
					</configuration>
					<executions>
						<execution>
							<id>attach-sources</id>
							<goals>
								<goal>jar-no-fork</goal>
							</goals>
						</execution>
					</executions>
				</plugin>
				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-surefire-plugin</artifactId>
					<version>${{maven-surefire-plugin.version}}</version>
					<configuration>
						<skipTests>${{skipTests}}</skipTests>
						<argLine>-Xmx1024m -Dfile.encoding=UTF-8</argLine>
						<additionalClasspathElements>
							<additionalClasspathElement>${{basedir}}/target/test-classes</additionalClasspathElement>
						</additionalClasspathElements>
						<includes>
							<include>**/*Test.java</include>
						</includes>
						<excludes>
							<exclude>**/TestBean.java</exclude>
						</excludes>
					</configuration>
				</plugin>
				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-jar-plugin</artifactId>
					<version>${{maven-jar-plugin.version}}</version>
					<configuration>
						<skipIfEmpty>true</skipIfEmpty>
						<archive>
							<manifest>
								<addDefaultImplementationEntries>true</addDefaultImplementationEntries>
								<addDefaultSpecificationEntries>true</addDefaultSpecificationEntries>
							</manifest>
						</archive>
					</configuration>
				</plugin>
				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-javadoc-plugin</artifactId>
					<version>${{maven-javadoc-plugin.version}}</version>
					<configuration>
						<charset>${{project.build.sourceEncoding}}</charset>
						<encoding>${{project.build.sourceEncoding}}</encoding>
						<docencoding>${{project.build.sourceEncoding}}</docencoding>
					</configuration>
					<executions>
						<execution>
							<id>attach-javadocs</id>
							<phase>package</phase>
							<goals>
								<goal>jar</goal>
							</goals>
						</execution>
					</executions>
				</plugin>
				<plugin>
					<groupId>org.sonatype.plugins</groupId>
					<artifactId>nexus-staging-maven-plugin</artifactId>
					<version>${{maven-nexus-staging-plugin.version}}</version>
					<extensions>true</extensions>
					<configuration>
						<serverId>ossrh</serverId>
						<nexusUrl>https://s01.oss.sonatype.org/</nexusUrl>
						<autoReleaseAfterClose>true</autoReleaseAfterClose>
						<stagingProgressPauseDurationSeconds>60</stagingProgressPauseDurationSeconds>
						<stagingProgressTimeoutMinutes>20</stagingProgressTimeoutMinutes>
						<detectBuildFailures>true</detectBuildFailures>
					</configuration>
				</plugin>
			</plugins>
		</pluginManagement>
		<plugins>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-enforcer-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-resources-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-surefire-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-jar-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-source-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-install-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-deploy-plugin</artifactId>
			</plugin>
		</plugins>
	</build>

	<profiles>
		<profile>
			<id>disable-javadoc-doclint</id>
			<activation>
				<jdk>[17,)</jdk>
			</activation>
			<properties>
				<doclint>none</doclint>
			</properties>
		</profile>
		<profile>
			<id>aliyun-deploy</id>
			<distributionManagement>
				<repository>
					<id>2624322-release-6F6h6R</id>
					<url>https://packages.aliyun.com/6927b116e6c3e0425dbdf60d/maven/2624322-release-6f6h6r</url>
				</repository>
				<snapshotRepository>
					<id>2624322-snapshot-3EoOv3</id>
					<url>https://packages.aliyun.com/6927b116e6c3e0425dbdf60d/maven/2624322-snapshot-3eoov3</url>
				</snapshotRepository>
			</distributionManagement>
		</profile>
		<profile>
			<id>release</id>
			<build>
				<plugins>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-enforcer-plugin</artifactId>
					</plugin>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-compiler-plugin</artifactId>
					</plugin>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-resources-plugin</artifactId>
					</plugin>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-surefire-plugin</artifactId>
					</plugin>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-jar-plugin</artifactId>
					</plugin>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-source-plugin</artifactId>
					</plugin>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-javadoc-plugin</artifactId>
					</plugin>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-install-plugin</artifactId>
					</plugin>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-gpg-plugin</artifactId>
					</plugin>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-deploy-plugin</artifactId>
					</plugin>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-release-plugin</artifactId>
					</plugin>
					<plugin>
						<groupId>org.sonatype.plugins</groupId>
						<artifactId>nexus-staging-maven-plugin</artifactId>
					</plugin>
				</plugins>
			</build>
		</profile>
	</profiles>

	<repositories>
		<repository>
			<id>smartbear-sweden-plugin-repository</id>
			<url>http://smartbearsoftware.com/repository/maven2/</url>
			<releases>
				<enabled>true</enabled>
			</releases>
			<snapshots>
				<enabled>false</enabled>
			</snapshots>
		</repository>
	</repositories>

	<properties>
		<java.version>{java_version}</java.version>
		<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
		<project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
		<skipTests>false</skipTests>
		<unirest-java.version>3.14.5</unirest-java.version>
		<fastjson2.version>2.0.57</fastjson2.version>
		<xxl-job.version>{xxl_job_core}</xxl-job.version>
		<xxl.job.executor.api>{executor_api}</xxl.job.executor.api>
		<maven.version>3.6.3</maven.version>
		<maven-gpg-plugin.version>3.2.7</maven-gpg-plugin.version>
		<maven-jar-plugin.version>3.4.2</maven-jar-plugin.version>
		<maven-release-plugin.version>3.1.1</maven-release-plugin.version>
		<maven-resources-plugin.version>3.3.1</maven-resources-plugin.version>
		<maven-surefire-plugin.version>3.5.2</maven-surefire-plugin.version>
		<maven-nexus-staging-plugin.version>1.7.0</maven-nexus-staging-plugin.version>
	</properties>

	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>com.alibaba.fastjson2</groupId>
				<artifactId>fastjson2</artifactId>
				<version>${{fastjson2.version}}</version>
			</dependency>
			<dependency>
			    <groupId>com.xuxueli</groupId>
			    <artifactId>xxl-job-core</artifactId>
			    <version>${{xxl-job.version}}</version>
			</dependency>
		</dependencies>
	</dependencyManagement>

	<dependencies>
		<dependency>
		    <groupId>org.projectlombok</groupId>
		    <artifactId>lombok</artifactId>
		    <scope>provided</scope>
		</dependency>
		<dependency>
			<groupId>org.slf4j</groupId>
			<artifactId>slf4j-api</artifactId>
		</dependency>
{servlet}
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter</artifactId>
			<exclusions>
				<exclusion>
					<groupId>org.springframework.boot</groupId>
					<artifactId>spring-boot-starter-logging</artifactId>
				</exclusion>
			</exclusions>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>ch.qos.logback</groupId>
			<artifactId>logback-classic</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-configuration-processor</artifactId>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-autoconfigure</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework</groupId>
			<artifactId>spring-web</artifactId>
		</dependency>
		<dependency>
			<groupId>com.alibaba.fastjson2</groupId>
			<artifactId>fastjson2</artifactId>
		</dependency>
		<dependency>
		    <groupId>com.xuxueli</groupId>
		    <artifactId>xxl-job-core</artifactId>
		</dependency>
		<dependency>
			<groupId>com.konghq</groupId>
			<artifactId>unirest-java</artifactId>
			<version>${{unirest-java.version}}</version>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-aop</artifactId>
			<exclusions>
				<exclusion>
					<groupId>org.springframework.boot</groupId>
					<artifactId>spring-boot-starter-logging</artifactId>
				</exclusion>
			</exclusions>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator</artifactId>
			<scope>provided</scope>
			<exclusions>
				<exclusion>
					<groupId>org.springframework.boot</groupId>
					<artifactId>spring-boot-starter-logging</artifactId>
				</exclusion>
				<exclusion>
					<groupId>io.micrometer</groupId>
					<artifactId>micrometer-core</artifactId>
				</exclusion>
			</exclusions>
		</dependency>
		<dependency>
			<groupId>io.micrometer</groupId>
			<artifactId>micrometer-core</artifactId>
			<scope>provided</scope>
		</dependency>
	</dependencies>

</project>
'''
    POM.write_text(body, encoding="utf-8")


def render(branch: str) -> None:
    if branch not in MATRIX:
        keys = ", ".join(sorted(MATRIX))
        raise SystemExit(f"unsupported branch: {branch}. Choose one of: {keys}")
    boot, jdk, prefix, core, executor_api, use_release = MATRIX[branch]
    write_pom(
        boot_parent=boot,
        java_version=jdk,
        version_prefix=prefix,
        xxl_job_core=core,
        executor_api=executor_api,
        use_release=use_release,
    )
    patch_executor_api(executor_api)
    patch_block_strategy_import(executor_api)
    ver = f"{prefix}.{VERSION_DATE_SUFFIX}"
    print(f"rendered {branch}: version={ver}, boot={boot}, jdk={jdk}, core={core}, executor={executor_api}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__.strip(), file=sys.stderr)
        sys.exit(2)
    render(sys.argv[1])
