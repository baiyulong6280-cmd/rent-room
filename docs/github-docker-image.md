# GitHub Docker 镜像发布说明

这个项目不能“直接运行在 GitHub”。

GitHub 适合做三件事：

- 托管代码
- 用 Actions 自动构建
- 把镜像发布到 GitHub Container Registry（`ghcr.io`）

如果你要在线运行这个项目，仍然需要 Railway、Render、云服务器或 Kubernetes 之类的运行环境。

## 当前已经补好的能力

仓库新增了工作流 [`.github/workflows/docker-image.yml`](/D:/myproject/shop/ruoyi-vue-pro/.github/workflows/docker-image.yml)，支持：

- `push master` 时自动构建并发布镜像
- 推送 `v*` tag 时自动构建并发布镜像
- 在 GitHub Actions 页面手动触发构建
- 手动触发时额外导出一个可下载的 Docker 镜像归档文件

镜像仓库地址格式为：

```text
ghcr.io/<你的 GitHub 用户名或组织名>/ruoyi-vue-pro
```

例如当前仓库如果是 `fuweiliang/ruoyi-vue-pro`，镜像地址就是：

```text
ghcr.io/fuweiliang/ruoyi-vue-pro
```

## 第一次使用前要做的事

### 1. 把工作流文件推到 GitHub

只要这个工作流文件已经进入 GitHub 仓库，Actions 才能开始构建镜像。

### 2. 打开 GitHub Packages 权限

进入 GitHub 仓库页面，确认：

- `Actions` 已启用
- 仓库允许 `GITHUB_TOKEN` 读写 packages

通常在仓库的 `Settings -> Actions -> General` 中确认即可。

### 3. 如果你希望别人匿名拉取镜像

构建完成后，到 GitHub 的 `Packages` 页面把该镜像设为 `public`。

否则默认通常只有你自己或有权限的人能拉取。

## 怎么触发构建

### 方式一：推送到 `master`

当你把代码推到 `master`，GitHub 会自动执行镜像构建并发布到 `ghcr.io`。

### 方式二：手动触发

进入仓库的 `Actions` 页面：

1. 选择 `Build and Publish Docker Image`
2. 点击 `Run workflow`
3. 保持 `export_artifact = true`
4. 运行后等待构建完成

这样除了发布到 `ghcr.io`，还会额外生成一个 `ruoyi-vue-pro-image.tar` 下载文件。

## 怎么下载镜像

### 方式一：直接拉取

```bash
docker pull ghcr.io/fuweiliang/ruoyi-vue-pro:latest
```

如果是 tag 发布，也可以拉指定版本：

```bash
docker pull ghcr.io/fuweiliang/ruoyi-vue-pro:v1.0.0
```

### 方式二：从 GitHub Actions 下载 tar 包

手动触发 workflow 后：

1. 打开对应那次 Actions 运行记录
2. 在页面底部找到 `Artifacts`
3. 下载 `ruoyi-vue-pro-image`
4. 本地执行：

```bash
docker load -i ruoyi-vue-pro-image.tar
```

## 容器启动示例

```bash
docker run -d ^
  --name ruoyi-vue-pro ^
  -p 8080:8080 ^
  -e SPRING_PROFILES_ACTIVE=railway ^
  ghcr.io/fuweiliang/ruoyi-vue-pro:latest
```

注意：

- 这个项目后端依赖数据库、Redis 和对应环境变量
- 只把镜像拉下来，并不代表业务能直接可用
- 如需在线运行，建议继续配合你现有的 [Railway 部署说明](/D:/myproject/shop/ruoyi-vue-pro/docs/railway-deploy.md)

## 我这次没有做的事

- 没有在本机直接构建镜像，因为当前环境没有安装 `docker`
- 没有在本机直接执行 Maven 打包，因为当前环境没有安装 `mvn`
- 没有替你修改 GitHub 仓库页面权限，因为这一步只能在 GitHub Web 页面完成
