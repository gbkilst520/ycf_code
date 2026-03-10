# 网关当前链路与组件交互详解（含 Dynamic + Governance + Ops）

## 1. 文档目标

本文描述当前仓库里网关的真实运行链路、组件职责边界、生命周期与扩展机制，覆盖以下能力：

- 静态/动态路由配置（YAML + Nacos Config）
- 服务发现（Nacos Naming）与负载均衡
- 过滤器链（Pre / Core / Post）
- 限流、灰度、熔断重试与降级
- SPI 扩展机制（过滤器、负载均衡、配置中心）
- 运维能力（Prometheus 指标、Access Log、管理后台）

当前整体请求主链路：

`Client -> Netty Gateway -> Backend HTTP Service`

支持控制面本地端点：

- `/metrics/prometheus`
- `/admin`
- `/admin/api/routes`
- `/admin/api/services`

---

## 2. 项目与模块分层

## 2.1 模块结构

- `common`
  - 基础响应码、常量、异常定义
- `gateway-service`
  - 网关核心实现（配置、注册、过滤器链、转发、治理、运维）
- `http-service`
  - 下游 HTTP 联调服务
- `rpc-service`
  - 内部 RPC 服务骨架（当前未直接挂到网关路由）

## 2.2 网关内部分层

- `bootstrap`
  - 启动装配与生命周期管理
- `config`
  - 配置模型、YAML 解析、配置中心抽象与实现
- `register`
  - 注册中心抽象与 Nacos 服务发现
- `core.access`
  - Netty 服务入口、请求上下文
- `core.filter`
  - 过滤器链与治理过滤器
- `core.forward`
  - AsyncHttpClient 下游转发
- `core.loadbalance`
  - 负载均衡抽象与策略
- `core.resilience`
  - 熔断重试与降级
- `core.ops`
  - 指标采集、管理端点
- `core.filter.spi` / `config.center.spi`
  - SPI 扩展点与 ServiceLoader 装配

---

## 3. 启动阶段交互（Bootstrap）

启动入口：`com.ycf.gateway.bootstrap.GatewayBootstrap`

启动顺序如下：

1. 读取网关配置
   - 默认类路径配置：`gateway-config.yaml`
   - 可通过 `-Dgateway.config=xxx.yaml` 覆盖
2. 初始化路由仓库
   - `InMemoryRouteRepository` 持有运行时路由快照
3. 选择注册中心
   - `nacos.register.enabled=true` 时使用 `NacosRegisterCenter`
   - 否则使用 `NoopRegisterCenter`
4. 通过 SPI 选择配置中心
   - `ServiceLoaderConfigCenterRegistry` 扫描 `ConfigCenterFactory`
   - `NacosConfigCenterFactory` 与 `NoopConfigCenterFactory` 按 `order` 选择
5. 注册中心启动与服务监听
   - 以当前路由关联的 serviceId 作为初始监听目标
6. 配置中心启动
   - 若启用 Nacos Config，先拉一次配置，再监听变更
   - 配置变更回调会 `replaceAll` 路由并 `watchServices`
7. 初始化核心转发组件
   - `NettyHttpClient`
   - `LoadBalancerRegistry`（SPI + 默认回退）
   - `ResilienceExecutor`
8. 初始化指标采集
   - `GatewayMetricsCollector`（Prometheus registry）
9. 初始化核心过滤器
   - `RouteFilter` -> `LoadBalanceFilter` -> `ForwardFilter`
10. 通过 SPI 加载可编排过滤器（gray / flow / responseHeader / accessLog）
11. 构建过滤器链
12. 初始化控制面 `GatewayControlPlane`
13. 启动 `NettyHttpServer`
14. 注册 JVM ShutdownHook 统一释放资源

---

## 4. 配置模型与动态更新

## 4.1 配置来源

- 本地 YAML：启动时必读
- Nacos Config（可选）：运行时动态刷新路由

## 4.2 关键配置块

- `gateway.port`
- `gateway.routes[]`
- `gateway.services[]`
- `gateway.filterChain.pre[] / post[]`
- `gateway.flow`
- `gateway.gray`
- `gateway.resilience`
- `gateway.nacos.register`
- `gateway.nacos.config`

## 4.3 动态路由流程

1. `NacosConfigCenter` 收到变更事件
2. 使用 `ConfigLoader.loadRoutesFromText(...)` 解析文本
3. 解析成功后通过回调刷新 `RouteRepository`
4. 网关立即开始按新路由匹配
5. 同步 `watchServices(newServiceIds)`，确保新服务也进入服务发现监听

特点：

- 路由变更不需要重启网关
- 解析失败时拒绝更新，保持旧快照继续服务

---

## 5. 数据平面请求链路（Data Plane）

以示例请求 `GET /http/hello?name=A` 说明。

## 5.1 入口接收

`NettyHttpServer` 处理顺序：

1. `HttpServerCodec` 解码
2. `HttpObjectAggregator` 聚合为 `FullHttpRequest`
3. 解析客户端 IP（优先 `X-Forwarded-For`）
4. 生成或透传 `X-Request-Id`
5. 创建 `GatewayContext`

`GatewayContext` 核心字段：

- 请求对象与方法
- 原始 URI / path / query
- `clientIp`
- `requestStartNanos`
- `requestId`
- 路由匹配结果、解析后的 serviceId
- 选中的服务实例与目标地址

## 5.2 控制面旁路

进入过滤器链前，先判断是否控制面路径：

- `/metrics/prometheus`
- `/admin*`

如果命中，直接在网关本地处理并返回，不走转发链。

## 5.3 过滤器链执行

当前顺序：

1. `RouteFilter`
2. `pre filters`（来自配置，默认 `gray -> flow`）
3. `LoadBalanceFilter`
4. `post filters`（来自配置，默认 `responseHeader -> accessLog`）
5. `ForwardFilter`（终点，实际发起下游请求）

说明：

- `DefaultGatewayFilterChain` 以递归式 chain 推进
- 如果链尾没有终端转发过滤器会返回内部错误

## 5.4 各核心过滤器职责

### RouteFilter

- 根据 `RouteRepository` 按 path 匹配路由
- 写入 `routeDefinition`、`resolvedServiceId`
- 将后续链路包裹进 `ResilienceExecutor`（熔断 + 重试）
- 异常时返回 fallback 响应

### GrayFilter（Pre）

- 当灰度开启时，按规则匹配 header/cookie/ip
- 命中后把 `resolvedServiceId` 从 source 替换为 target
- 只改路由目标，不改原始 URL

### FlowFilter（Pre）

- 支持全局 / 服务 / IP 三层令牌桶限流
- 命中限流直接返回 `429 TOO_MANY_REQUESTS`

### LoadBalanceFilter

- 先按 serviceId 查询注册中心实例
- 若有实例，按路由配置策略选择（轮询或随机）
- 若无实例，回退到静态 `services.baseUrl`
- 均不可用时返回 `503`

### ResponseHeaderPostFilter（Post）

- 为响应追加网关标识头

### AccessLogPostFilter（Post）

- 记录详细访问日志（成功/异常）
- 包括请求、路由、实例、状态、耗时、流量、trace 信息

### ForwardFilter（Terminal）

- 调用 `NettyHttpClient.forward(context)`
- 异常时返回 `500`

## 5.5 下游转发

`NettyHttpClient` 工作流程：

1. 用 `stripPrefix` 重写请求 path
2. 拼接 `targetBaseUrl + rewrittenPath + query`
3. 复制大部分请求头（排除 Host/Content-Length/Transfer-Encoding）
4. 透传 body
5. 异步请求下游服务
6. 把返回转换成 Netty `FullHttpResponse`

---

## 6. 控制面能力（Extension & Ops）

## 6.1 Prometheus 指标

实现类：`GatewayMetricsCollector`

默认指标：

- `gateway_requests_total`
  - 标签：`method, route, service, outcome, internal`
- `gateway_request_latency_ms`
  - 标签：`method, route, service, status, internal`
- `gateway_error_total`
  - 标签：`method, route, service, status, internal`

暴露端点：

- `GET /metrics/prometheus`
- 文本格式符合 Prometheus scrape

采集位置：

- `NettyHttpServer` 在请求最终写回前统一记录
- 覆盖业务链路与控制面请求

## 6.2 Access Log 增强字段

AccessLog 关键字段包括：

- `requestId`
- `method / uri / path / query`
- `routeId / service / targetBaseUrl / selectedInstance`
- `clientIp`
- `status / costMs`
- `reqBytes / respBytes`
- `userAgent / traceparent / X-B3-TraceId`

价值：

- 排障可按 `requestId` 全链路追踪
- 可快速识别路由错配、实例抖动、慢请求和异常聚集

## 6.3 管理后台

实现类：`GatewayControlPlane`

### Web 页面

- `GET /admin`
- 页面内置 JS，调用管理 API 展示/更新配置

### 管理 API

- `GET /admin/api/routes`
  - 返回当前生效路由快照
- `POST|PUT /admin/api/routes`
  - body 为 YAML 文本（支持 routes 列表或 gateway.routes 结构）
  - 成功后实时替换内存路由并刷新服务监听
- `GET /admin/api/services`
  - 返回静态服务 + 已监听服务 + 已发现实例

---

## 7. SPI 扩展机制

## 7.1 过滤器 SPI

接口：`com.ycf.gateway.core.filter.spi.GatewayFilterFactory`

方法：

- `name()`：配置中引用名
- `create(FilterFactoryContext)`：创建过滤器实例

默认注册（`META-INF/services/com.ycf.gateway.core.filter.spi.GatewayFilterFactory`）：

- `GrayFilterFactory`
- `FlowFilterFactory`
- `ResponseHeaderPostFilterFactory`
- `AccessLogPostFilterFactory`

装配方式：

- `ServiceLoaderFilterRegistry` 扫描并构造 registry
- `FilterChainFactory` 按 `filterChain.pre/post` 动态编排

## 7.2 负载均衡 SPI

接口：`com.ycf.gateway.core.loadbalance.LoadBalancer`

关键点：

- 新增 `type()` 声明策略类型
- `LoadBalancerRegistry` 通过 `ServiceLoader` 自动加载
- 若未加载到某策略，回退内置实现

默认注册（`META-INF/services/com.ycf.gateway.core.loadbalance.LoadBalancer`）：

- `RoundRobinLoadBalancer`
- `RandomLoadBalancer`

## 7.3 配置中心 SPI

接口：`com.ycf.gateway.config.center.spi.ConfigCenterFactory`

关键点：

- `supports(config)` 判断是否可用
- `order()` 控制优先级
- `create(...)` 构建配置中心实例

默认注册（`META-INF/services/com.ycf.gateway.config.center.spi.ConfigCenterFactory`）：

- `NacosConfigCenterFactory`
- `NoopConfigCenterFactory`

装配方式：

- `ServiceLoaderConfigCenterRegistry` 扫描并按优先级选中

---

## 8. 异常与降级路径

常见失败路径：

1. 路由未命中：`404`
2. 限流触发：`429`
3. 无实例且无静态地址：`503`
4. 下游异常 / 熔断打开：触发 fallback（默认 `503 + 友好文案`）
5. 请求处理异常：`500`

网关统一通过 `HttpResponseFactory` 构造错误响应，并保持 `X-Request-Id` 回传。

---

## 9. 当前链路一句话总结

当前网关已经形成“可动态配置 + 可治理 + 可观测 + 可扩展”的完整主干：

- 业务流量通过 Netty + 过滤器链完成路由、治理、负载和转发
- 控制面通过本地管理 API 提供在线观测与运维操作
- 关键扩展点通过 ServiceLoader 解耦，支持二次开发按需替换

