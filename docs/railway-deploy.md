# Railway 部署说明

## 适用范围

本仓库可以直接部署的是后端服务 `yudao-server`。

`yudao-ui/yudao-ui-admin-vue3` 在当前仓库中只有入口说明，不包含完整前端源码，所以如果你还要部署管理后台前端，需要把前端仓库单独接入 Railway。

## 本次已补齐的内容

- 根目录 `Dockerfile`
  - Railway 会自动识别根目录 `Dockerfile`，直接从源码多阶段构建后端镜像
- `yudao-server/src/main/resources/application-railway.yaml`
  - 后端在 Railway 上通过环境变量读取 MySQL、Redis 和端口
- `AdminServerConfiguration`
  - 支持通过 `spring.boot.admin.server.enabled=false` 关闭内置监控面板，避免默认口令暴露

## Railway 上的推荐拓扑

- 1 个后端服务：当前仓库
- 1 个 MySQL 数据库：Railway 托管
- 1 个 Redis 数据库：Railway 托管

## 部署步骤

### 1. 创建 Railway 项目

在 Railway 中新建一个 Project。

### 2. 添加数据库服务

在同一个 Project 里添加：

- MySQL
- Redis

Railway 官方文档说明：

- MySQL 服务会提供 `MYSQLHOST`、`MYSQLPORT`、`MYSQLUSER`、`MYSQLPASSWORD`、`MYSQLDATABASE` 等变量
- Redis 服务会提供 `REDISHOST`、`REDISPORT`、`REDISUSER`、`REDISPASSWORD` 等变量

参考：

- [Railway MySQL 文档](https://docs.railway.com/databases/mysql)
- [Railway Redis 文档](https://docs.railway.com/databases/redis)

### 3. 部署当前仓库

把当前 Git 仓库连接到 Railway，部署根目录即可。

Railway 会自动识别根目录 `Dockerfile` 并构建镜像。

参考：

- [Railway Dockerfile 文档](https://docs.railway.com/builds/dockerfiles)

### 4. 给后端服务配置变量引用

在后端服务的 Variables 中配置以下变量：

```text
MYSQLHOST=${{MySQL.MYSQLHOST}}
MYSQLPORT=${{MySQL.MYSQLPORT}}
MYSQLUSER=${{MySQL.MYSQLUSER}}
MYSQLPASSWORD=${{MySQL.MYSQLPASSWORD}}
MYSQLDATABASE=${{MySQL.MYSQLDATABASE}}

REDISHOST=${{Redis.REDISHOST}}
REDISPORT=${{Redis.REDISPORT}}
REDISUSER=${{Redis.REDISUSER}}
REDISPASSWORD=${{Redis.REDISPASSWORD}}

SPRING_PROFILES_ACTIVE=railway
JAVA_OPTS=-Xms512m -Xmx512m -Djava.security.egd=file:/dev/./urandom
```

说明：

- `SPRING_PROFILES_ACTIVE=railway` 虽然在 `Dockerfile` 里已经给了默认值，建议在 Railway 控制台也显式设置
- Railway 会自动注入 `PORT`，`application-railway.yaml` 已经接管该端口
- Railway 的变量模板语法和系统变量说明见 [Variables Reference](https://docs.railway.com/variables/reference)

### 5. 初始化数据库

至少导入以下 SQL：

- `sql/mysql/ruoyi-vue-pro.sql`

当前 Railway profile 默认关闭了 Quartz 自动配置，这样首发部署不依赖 `quartz.sql`。

如果你后续需要启用在线定时任务，再补充：

- `sql/mysql/quartz.sql`

然后去掉 `application-railway.yaml` 里对 `org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration` 的排除即可。

### 6. 配置健康检查和公网域名

部署完成后，在 Railway 服务设置中：

- Healthcheck Path 设置为 `/actuator/health`
- Generate Domain 生成公网地址

参考：

- [Railway Healthcheck 文档](https://docs.railway.com/deployments/healthchecks)
- [Railway Public Networking 文档](https://docs.railway.com/guides/public-networking)

## 私网通信说明

Railway 同一个 Project 内的服务可以通过私网互通，官方文档说明服务可通过 `SERVICE_NAME.railway.internal` 访问。

不过当前后端已经直接使用数据库服务暴露的环境变量，所以不需要你手动拼接内网域名。

参考：

- [Railway Private Networking 文档](https://docs.railway.com/networking/private-networking)

## 当前限制

- 这个仓库不包含完整的 Vue3 管理后台源码，所以这里只完成了后端 Railway 部署链路
- 我当前环境里没有已登录的 Railway CLI，也没有你的 Railway 项目权限，所以还不能直接替你把远端服务点起来

如果你把前端仓库也接进来，我可以继续帮你把前端反向代理、`VITE_BASE_URL` 或网关地址一并配好。
