# SuperSonic

## 本地开发调试

本地开发需要分别启动后端（Java）和前端（React）。

### 后端

要求：Java 21 + Maven。

先整体构建再启动脚本：

```bash
./assembly/bin/supersonic-build.sh standalone
./assembly/bin/supersonic-daemon.sh start
```

输出：assembly/log/error.log

```log
错误: 找不到或无法加载主类 com.tencent.supersonic.StandaloneLauncher
原因: java.lang.ClassNotFoundException: com.tencent.supersonic.StandaloneLauncher
```

`ClassNotFoundException` 的原因是：`supersonic-daemon.sh` 是从 `assembly/lib/*.jar` 加载类路径的，而 `baseDir` 被解析为脚本所在目录的上一级（即 `assembly/`）。源码仓库里 `assembly/lib/` 并不存在 —— jar 包只存在于构建产物中。

也就是说，**不能直接在源码目录里运行 `supersonic-daemon.sh`**。

正确流程：

```bash
# 1. 构建（会执行 mvn package + 前端打包，产物在 assembly/build/ 下）
./assembly/bin/supersonic-build.sh standalone

# 2. 解压生成的 release zip
cd assembly/build
unzip supersonic-standalone-<version>.zip
cd supersonic-standalone-<version>

# 3. 在解压后的目录里启动（该目录下有 bin/ lib/ conf/）
./bin/supersonic-daemon.sh start
```

`supersonic-build.sh` 会把 `launchers/standalone/target/*.tar.gz` 和前端包打进 `assembly/build/supersonic-standalone-<version>.zip`，解压后的目录才包含 `lib/` 下的所有 jar 和 `conf/` 配置。

如果你只是想做本地开发调试，更简单的方式是跳过 daemon 脚本，在 IDE 中直接运行 `launchers/standalone` 模块的启动类 `com.tencent.supersonic.StandaloneLauncher`（这是合并 Chat + Headless 的默认模式）。

1. 先 `mvn install -DskipTests` 编译依赖模块
2. 在 IDE 里运行 `launchers/standalone/src/main/java/com/tencent/supersonic/StandaloneLauncher.java`
3. 默认端口 **9080**，数据库通过环境变量 `S2_DB_TYPE` 控制，默认使用内置的 `h2`，无需额外配置即可启动。
4. 如需 MySQL/PostgreSQL，设置 `S2_DB_TYPE=mysql` 或 `S2_DB_TYPE=postgresql`（对应 `application-mysql.yaml` / `application-postgresql.yaml`）。

启动后访问 <http://localhost:9080> 。

### 前端

要求：Node.js >= 16，pnpm 9.12.3+。

```bash
cd webapp
./start-fe-dev.sh
```

前端 dev server 运行后访问 <http://localhost:9000，接口代理到后端> 9080。

注意：`webapp/package.json` 只是 workspace 根配置，实际前端代码在 `webapp/packages/supersonic-fe/`。
