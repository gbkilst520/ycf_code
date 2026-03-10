# ycf-cloud-platform Docker 操作文档

## 1. 先理清这个项目在干什么

这是一个 Maven 多模块微服务项目，核心是一个自研 Netty 网关：

- `gateway-service`
  - 项目主角，自定义 Netty 网关（不是 Spring Cloud Gateway）
  - 负责路由匹配、灰度、限流、熔断重试、负载均衡、转发
  - 提供管理与观测端点：`/admin`、`/admin/api/*`、`/metrics/prometheus`
- `http-service`
  - 下游 HTTP 示例服务，端口 `8081`
  - 示例接口：`GET /hello?name=xxx`
- `rpc-service`
  - 预留 RPC/内部服务骨架，端口 `8082`
  - 当前默认链路没有挂到网关路由
- `common`
  - 公共常量、异常、响应码

请求主链路：

`Client -> gateway-service(8080) -> http-service(8081)`

默认网关路由（`gateway-config.yaml`）：

- 入口：`/http/**`
- 目标服务：`http-service`
- `stripPrefix=1`，例如 `/http/hello` 会被转发成 `/hello`

---

## 2. Docker 安装（开发机准备）

按你的系统安装 Docker：

1. macOS / Windows：安装 Docker Desktop（自带 Docker Engine + Compose）
2. Linux：安装 Docker Engine + Docker Compose Plugin

安装后先做基础检查：

```bash
docker version
docker compose version
```

如果 Linux 上执行 Docker 需要 sudo，可执行：

```bash
sudo usermod -aG docker $USER
# 重新登录终端后生效
```

---

## 3. Docker Hub 基础操作

### 3.1 登录

```bash
docker login
```

### 3.2 拉取镜像

```bash
docker pull mysql:8.4
docker pull nacos/nacos-server:v2.3.2
```

### 3.3 查看本地镜像

```bash
docker images
```

### 3.4 打标签并推送（可选）

```bash
# 例子：将本地网关镜像打上你的 Docker Hub 仓库名
docker tag ycf/gateway-service:local <your-dockerhub-username>/gateway-service:1.0.0
docker push <your-dockerhub-username>/gateway-service:1.0.0
```

---

## 4. 用 Docker 运行当前项目

仓库已提供：

- `docker-compose.yml`
- `docker/http-service.Dockerfile`
- `docker/gateway-service.Dockerfile`
- `docker/rpc-service.Dockerfile`
- `docker/gateway-config.docker.yaml`

## 4.1 启动核心链路（网关 + http-service）

在项目根目录执行：

```bash
docker compose up -d --build
```

查看容器：

```bash
docker compose ps
```

查看日志：

```bash
docker compose logs -f gateway-service
docker compose logs -f http-service
```

## 4.2 功能验证

1. 直连下游服务（验证 http-service）：

```bash
curl "http://localhost:8081/hello?name=direct"
```

2. 走网关转发（验证主链路）：

```bash
curl "http://localhost:8080/http/hello?name=gateway"
```

3. 查看网关管理面（浏览器访问）：

```bash
http://localhost:8080/admin
```

4. 查看 Prometheus 指标：

```bash
curl "http://localhost:8080/metrics/prometheus"
```

## 4.3 启动可选服务

### 启动 `rpc-service`

```bash
docker compose --profile rpc up -d --build
```

### 启动 Nacos

```bash
docker compose --profile nacos up -d
```

说明：

- 当前网关 Docker 配置里 `nacos.register.enabled=false`、`nacos.config.enabled=false`，所以 Nacos 默认不是主链路必需组件
- 若后续需要动态路由/服务发现，再打开这两个开关并给后端服务接入注册中心

---

## 5. 用 Docker 管理 MySQL（后续后端服务建议）

## 5.1 启动 MySQL

```bash
docker compose --profile db up -d
```

默认参数（见 `docker-compose.yml`）：

- Host: `127.0.0.1`
- Port: `3306`
- DB: `ycf_demo`
- User: `ycf`
- Password: `ycf123456`
- Root Password: `root`

初始化脚本：

- `docker/mysql/init/001-init.sql`

## 5.2 连接测试

```bash
docker exec -it ycf-mysql mysql -uycf -pycf123456 -e "SHOW DATABASES;"
docker exec -it ycf-mysql mysql -uycf -pycf123456 -D ycf_demo -e "SELECT * FROM demo_user;"
```

## 5.3 常用运维命令

```bash
# 查看日志
docker compose logs -f mysql

# 停止服务但保留数据卷
docker compose stop mysql

# 删除容器并保留数据卷
docker compose rm -f mysql

# 删除数据卷（会清空 MySQL 数据）
docker volume rm ycf_code_mysql-data
```

---

## 6. 以后新增后端服务的 Docker 模板流程

假设新增模块 `order-service`（Spring Boot）：

1. 新建模块并实现接口（例如端口 `8083`）
2. 参考 `docker/http-service.Dockerfile` 新建 `docker/order-service.Dockerfile`
3. 在 `docker-compose.yml` 增加 `order-service` 服务
4. 在 `docker/gateway-config.docker.yaml` 增加：
   - `services` 里的 `order-service`，`baseUrl` 指向 `http://order-service:8083`
   - `routes` 里的 `/order/** -> order-service`
5. 重建并启动：

```bash
docker compose up -d --build
```

验证时优先用容器服务名互通，不用 `127.0.0.1`：

- 容器内访问另一个容器：`http://service-name:port`
- 主机访问容器：`http://localhost:映射端口`

---

## 7. 常见问题

### 7.1 网关启动了但转发失败

优先检查：

1. `docker-compose.yml` 中 `http-service` 是否在运行
2. `docker/gateway-config.docker.yaml` 的 `baseUrl` 是否是 `http://http-service:8081`
3. 网关日志里是否出现 `503`、`route not found`、`no instance discovered`

### 7.2 本地端口冲突

修改 `docker-compose.yml` 的端口映射，例如：

- `8080:8080` 改为 `18080:8080`
- `3306:3306` 改为 `13306:3306`

### 7.3 清理环境

```bash
# 停止并移除容器、网络
docker compose down

# 包括数据卷一起删除（谨慎）
docker compose down -v
```
