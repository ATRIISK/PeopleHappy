# 众乐电子商城（Happy Mart）

> 一个可写进简历的轻量级 B2C 电商全栈项目：用户端（商品浏览 / 购物车 / 下单 / 支付宝沙箱扫码支付 / AI 购物助手）+ 管理后台（商品 / 订单 / 用户 / Dashboard 数据看板）。

- 后端：Java 17 · Spring Boot 3.5.14 · MyBatis-Plus · MySQL · Redis · RabbitMQ
- 前端：Vue 3 · Vite · Pinia · Element Plus
- 支付：支付宝开放平台**沙箱环境**（`alipay.trade.precreate` 扫码 + RSA2 验签 + refund 退款，无需企业资质）
- AI：商品知识库 RAG 混合检索 → 通义千问 qwen-turbo 导购（`spring-ai-alibaba`）
- 部署：Docker Compose（MySQL + Redis + RabbitMQ + Backend + Nginx）

---

## 目录

- [功能模块](#功能模块)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [环境搭建](#环境搭建)
- [接口文档](#接口文档)
- [优化思路](#优化思路)
- [部署架构](#部署架构)
- [配置与安全说明](#配置与安全说明)
- [测试](#测试)

---

## 功能模块

| 模块 | 说明 | 状态 |
|---|---|---|
| 用户模块 | 注册 / 登录 / 自定义 JWT 拦截器（`@Auth` 注解） | ✅ |
| 商品模块 | 分类树 / 商品分页 / 详情 / 热门榜 / 一级分类自动包含子分类 | ✅ |
| 购物车模块 | 增删改查清，联表查商品信息；**加购/改数量校验累计 ≤ 库存**，下架商品拦截 | ✅ |
| 地址模块 | 收货地址增删改查 / 默认地址 | ✅ |
| 订单模块 | 下单（原子条件更新扣库存）/ 取消 / 确认收货 / 修改地址 / 退单退款 | ✅ |
| 支付模块 | 支付宝沙箱扫码支付 + RSA2 验签回调 + 主动查询兜底 + 退款 | ✅ |
| 订单超时取消 | RabbitMQ 原生 TTL + 死信队列（DLX），30 分钟未支付自动取消并恢复库存 | ✅ |
| Redis 缓存 | 商品详情 / 分类树 `@Cacheable` + 热门榜 ZSet 定时刷新 + 延时双删 + 故障降级 | ✅ |
| 管理后台 | 商品 / 订单 / 用户管理 + Dashboard 看板 + 用户禁用 + 图片本地上传 | ✅ |
| AI 购物助手 | 商品知识库 RAG 混合检索（关键词 LIKE + embedding 向量召回）→ 通义千问导购 + 参考商品卡片 | ✅ |
| 单元测试 | 订单核心业务 16 用例（JUnit 5 + Mockito，不连库秒跑） | ✅ |

---

## 技术栈

| 层 | 技术 |
|---|---|
| 后端框架 | Java 17 / Spring Boot 3.5.14 |
| 安全认证 | 自定义 JWT 拦截器（`@Auth` + HandlerInterceptor，支持管理员角色校验 / 用户禁用即时拦截），不使用 Spring Security |
| ORM | MyBatis-Plus 3.5.7 |
| 数据库 | MySQL 8.x（8 张表） |
| 缓存 | Redis（Spring Cache + StringRedisTemplate + ZSet） |
| 消息队列 | RabbitMQ（原生 TTL + DLX 死信队列实现延迟任务） |
| 支付 | 支付宝开放平台沙箱 SDK `alipay-sdk-java 4.40.645.ALL` |
| AI | `spring-ai-alibaba-starter-dashscope 1.1.2.1` + SimpleVectorStore |
| 前端 | Vue 3 / Vite / Pinia / Vue Router / Element Plus / axios / qrcode |
| 构建 / 部署 | Maven 3.9+ / Node 18+ / Docker Compose / Nginx |

**端口约定**

| 服务 | 端口 | 说明 |
|---|---|---|
| 后端 | 8074 | Spring Boot |
| 前端开发 | 3000 | Vite Dev Server，代理 `/api`、`/upload` → 8074 |
| 生产 | 80 | Nginx，`/` 静态资源 + `/api`、`^~ /upload/` 反代 backend:8074 |

---

## 项目结构

```
PeopleHappy/
├── happy_mart/                  # 后端 Spring Boot 项目
│   ├── src/main/java/com/happymart/
│   │   ├── controller/          # 表现层（User/Product/Category/Cart/Order/Address/PayNotify/Ai + admin/ 5 个）
│   │   ├── service/             # 业务层（含 Alipay、Admin*、KnowledgeBase 等）
│   │   ├── mapper/              # 数据访问层（8 个 Mapper，XML 手写联表/条件更新 SQL）
│   │   ├── entity/ vo/ dto/     # 模型分层（Entity 不进 Controller，VO/DTO 隔离）
│   │   ├── mq/                  # OrderTimeoutConsumer 订单超时消费者
│   │   ├── common/              # Result / ResultCodeEnum / 全局异常 / @Auth 注解
│   │   ├── config/              # Redis / RabbitMQ / MyBatis-Plus / WebMvc / Alipay / AI 等配置
│   │   ├── interceptor/         # JwtAuthInterceptor
│   │   └── util/                # JwtUtil
│   ├── src/main/resources/
│   │   ├── application.yml      # 数据源 / Redis / RabbitMQ / 支付宝 / AI / 上传配置
│   │   ├── mapper/*.xml         # SQL 映射
│   │   └── db/init-data.sql     # 商品 + 分类初始化数据
│   ├── src/test/                # OrderServiceImplTest（16 用例）+ 上下文加载测试
│   ├── Dockerfile
│   └── pom.xml
├── happy-mart-web/              # 前端 Vue 3 项目
│   └── src/
│       ├── api/                 # axios 接口封装（product/category/cart/order/address/admin/ai）
│       ├── stores/              # Pinia（user/cart）
│       ├── router/              # 15 个路由 + 导航守卫（requiresAdmin）
│       ├── layouts/             # FrontLayout（含 AI 悬浮助手）/ AdminLayout
│       ├── components/          # AiAssistant.vue 等
│       └── views/               # 用户端 11 页 + 管理后台 4 页
├── nginx/conf/happy-mart.conf   # Nginx 反代配置
├── docs/design/                 # 《众乐电子商城-开发设计文档.md》（v1.13，最详细，建议优先阅读）
└── docker-compose.yml           # MySQL + Redis + RabbitMQ + Backend + Nginx 一键编排
```

---

## 环境搭建

### 环境要求

- JDK 17+
- Maven 3.9+（IDEA 自带即可）
- Node.js 18+
- MySQL 8.x（本地或 Docker）
- Redis（本地或 Docker）
- RabbitMQ（本地或 Docker；管理插件 `rabbitmq_management` 可选）
- 支付宝沙箱账号 + AI 通义千问 API Key（可选，跳过则对应功能降级）

### 1. 创建数据库

```sql
CREATE DATABASE happy_mart DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

在 `happy_mart` 库中按 `docs/design/众乐电子商城-开发设计文档.md` **第 4 章**执行 8 张表的建表 DDL（`user / category / product / cart / order / order_item / address / payment_log`），然后执行初始化数据：

```bash
mysql -uroot -p happy_mart < happy_mart/src/main/resources/db/init-data.sql
```

### 2. 启动后端

1. 修改 `happy_mart/src/main/resources/application.yml`：
   - 数据源：`spring.datasource.url/username/password`（默认 `root/123456`，库名 `happy_mart`）
   - Redis：`spring.data.redis.*`（默认 `localhost:6379` database 0 无密码）
   - RabbitMQ：`spring.rabbitmq.*`（默认 `guest/guest@localhost:5672`）
   - 支付宝：`alipay.*`（沙箱 APPID / 商户私钥 / 支付宝公钥 / 网关 / 回调隧道地址，见「配置与安全说明」）
   - AI（可选）：`spring.ai.dashscope.api-key`（通义千问 API Key，无 key 后端照常启动，AI 调用降级提示）
2. 启动：

```bash
cd happy_mart
mvn spring-boot:run
# 或 mvn package 后运行 java -jar target/happy-mart-0.0.1-SNAPSHOT.jar
```

后端启动成功后监听 `8074`。

### 3. 启动前端

```bash
cd happy-mart-web
npm install
npm run dev
```

浏览器访问 `http://localhost:3000`（Vite 已把 `/api`、`/upload` 代理到 `localhost:8074`）。

> 默认有 200 个演示商品（上架）；如需管理后台账号，执行
> `UPDATE user SET role='ADMIN' WHERE username='你的账号';` 后重新登录即可进入 `/admin`。

### 4. Docker 一键部署（可选）

```bash
# 1. 前端构建产物
cd happy-mart-web && npm run build && cd ..

# 2. 一键启动全部服务（MySQL/Redis/RabbitMQ/Backend/Nginx）
docker compose up -d --build
```

- 访问 `http://localhost`（Nginx，前端 + 反代后端）
- 管理后台：`http://localhost/admin`
- RabbitMQ 管理界面：`http://localhost:15672`（guest/guest）
- 后端日志：`docker compose logs -f backend`

> Docker 环境下数据库账号默认 `root/123456`（docker-compose.yml 的 `MYSQL_ROOT_PASSWORD`），后端连接串由 `SPRING_DATASOURCE_*` 环境变量注入到 `mysql` 容器主机名，**本地跑时**记得把 `application.yml` 改回 `localhost`。

---

## 接口文档

- 统一返回格式：`Result { code, message, data }`，`code = 200` 表示成功（业务错误码见 `ResultCodeEnum`）。
- 认证方式：自定义 JWT，请求头 `Authorization: Bearer <token>`。
- 订单状态：`0 待支付 · 1 已支付 · 2 已发货 · 3 已完成 · 4 已取消 · 5 已退款`。

### 用户

| 方法 | 路径 | 说明 | 认证 |
|---|---|---|---|
| POST | `/api/user/register` | 注册（BCrypt 加密密码） | 否 |
| POST | `/api/user/login` | 登录，返回 JWT token | 否 |
| GET | `/api/user/info` | 当前用户信息 | 是 |

### 商品 / 分类

| 方法 | 路径 | 说明 | 认证 |
|---|---|---|---|
| GET | `/api/product/list` | 商品分页列表（分类 / 排序 / 搜索；一级分类自动包含子分类商品） | 否 |
| GET | `/api/product/detail/{id}` | 商品详情（Redis 缓存 30min） | 否 |
| GET | `/api/product/hot` | 热门商品榜（Redis ZSet，销量 Top8） | 否 |
| GET | `/api/category/tree` | 分类树（含子分类，Redis 缓存 1h） | 否 |

### 购物车

| 方法 | 路径 | 说明 | 认证 |
|---|---|---|---|
| POST | `/api/cart/add` | 加购（upsert 原子累加；校验累计 ≤ 库存、商品须上架，超限报 `STOCK_NOT_ENOUGH`） | 是 |
| GET | `/api/cart/list` | 购物车列表（联表查商品名称/图片/价格/库存） | 是 |
| PUT | `/api/cart/update` | 改数量（body: `productId` + `quantity`；依次校验项存在 → 上架 → ≤ 库存） | 是 |
| DELETE | `/api/cart/remove` | 删除购物车项（body: `productId`） | 是 |
| DELETE | `/api/cart/clear` | 清空购物车 | 是 |

### 订单

| 方法 | 路径 | 说明 | 认证 |
|---|---|---|---|
| POST | `/api/order/create` | 创建订单（从购物车，可选 `productIds`；原子条件更新扣库存） | 是 |
| GET | `/api/order/list` | 我的订单（分页；批量组装订单项，避免一对多联表截断） | 是 |
| GET | `/api/order/detail/{id}` | 订单详情（含地址） | 是 |
| PUT | `/api/order/cancel/{id}` | 取消订单（仅待支付；条件更新防并发） | 是 |
| PUT | `/api/order/refund/{id}` | 退单退款（仅已支付未发货；调支付宝退款 + 恢复库存 + 幂等键） | 是 |
| PUT | `/api/order/confirm/{id}` | 确认收货（仅已发货） | 是 |
| PUT | `/api/order/updateAddress/{id}` | 修改地址（仅待支付/待发货） | 是 |
| POST | `/api/order/pay/{id}` | 支付宝扫码支付（沙箱 precreate，返回二维码 URL） | 是 |
| GET | `/api/order/status/{id}` | 查询支付状态（兜底主动查支付宝） | 是 |

### 地址

| 方法 | 路径 | 说明 | 认证 |
|---|---|---|---|
| POST | `/api/address/add` | 新增地址 | 是 |
| GET | `/api/address/list` | 地址列表（默认地址排最前） | 是 |
| PUT | `/api/address/update` | 修改地址 | 是 |
| DELETE | `/api/address/delete/{id}` | 删除地址 | 是 |

### 支付回调

| 方法 | 路径 | 说明 | 认证 |
|---|---|---|---|
| POST | `/api/pay/notify` | 支付宝异步通知（表单参数，RSA2 验签，返回纯文本 `success/failure`） | 验签 |
| POST | `/api/pay/simulate/{orderId}` | 【仅开发调试】模拟支付回调，标记订单已支付（**上线前移除**） | 是 |

### 管理后台（全部需 `ADMIN` 角色，非管理员返回 403）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/product/list` | 分页查全部商品（可按名称搜索） |
| POST | `/api/admin/product/save` | 新增/修改商品（改后清商品详情缓存） |
| PUT | `/api/admin/product/status/{id}` | 上架/下架（query 传 `status`） |
| DELETE | `/api/admin/product/{id}` | 删除商品（逻辑删除 + 级联清购物车 + 清缓存） |
| GET | `/api/admin/order/list` | 分页查全部订单（含下单人，可按状态筛选） |
| PUT | `/api/admin/order/status` | 修改订单状态（仅发货 1→2，白名单状态机 + 条件更新） |
| GET | `/api/admin/user/list` | 分页查用户（可按 username/phone 搜索） |
| PUT | `/api/admin/user/status/{id}` | 禁用/启用用户（禁止禁自己/禁 ADMIN） |
| PUT | `/api/admin/user/resetPwd/{id}` | 重置密码（BCrypt 覆盖） |
| GET | `/api/admin/dashboard/stats` | 数据看板统计（商品/订单/用户数 + 总交易额） |
| GET | `/api/admin/dashboard/recent-orders` | 最近订单（最新 5 条） |
| POST | `/api/admin/upload/image` | 商品图片本地上传（multipart `file`，≤5MB，返回 `/upload/yyyyMMdd/<uuid>.ext`） |

### AI 购物助手（游客开放）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/ai/chat` | body: `{"message":"推荐适合运动的耳机"}` → `data: {response, sources[]}`（参考商品卡片） |

---

## 优化思路

### 1. 缓存优化（Redis）

- **商品详情 / 分类树**：`@Cacheable` 注解缓存（String JSON），读时回填、写时清除。
- **热门榜**：ZSet 按销量排序，`@Scheduled` 每小时整点刷新（ZADD 覆盖 + `removeRange` 清理掉榜成员，避免删 key 重建的毫秒级空窗）。
- **经典延时双删**：下单扣库存 / 退单·取消恢复库存会改 `product.stock`，而详情有 30min 缓存 → 采用「`@CacheEvict` + 事务提交后延时 500ms 再删一次」保证缓存最终一致。
- **故障降级**：Redis 宕机时 `CacheErrorHandler` 打日志不抛异常，接口照常回数据库，商城不挂。

### 2. 异步与延迟任务（RabbitMQ）

- **订单超时自动取消**：原生 TTL（30min）+ 死信交换机（DLX）实现延迟队列，不依赖 delayed-message 插件。
- 消费者按「先改状态（条件更新 `status=0→4`）再恢复库存」的顺序执行，**不会误取消已支付订单、不会双倍恢复库存**。
- MQ 发送失败不阻塞下单主流程（try-catch 打日志告警，预留定时补偿）。

### 3. 并发安全

- **原子条件更新扣库存（非乐观锁）**：`UPDATE product SET stock=stock-? WHERE id=? AND stock>=?`。InnoDB 执行时对匹配行加排他行锁，并发事务阻塞排队，拿到锁后重判条件，不满足 → 影响行数 0 → 抛异常不超卖。（注意：无 version 字段、冲突时阻塞而非应用层重试，属悲观锁机制，不满足乐观锁定义。）
- **条件更新防竞态**：`cancelPendingOrder(0→4)` / `refundPaidOrder(1→5)` / `markOrderPaid(0→1)` 全部带 `WHERE status=xx`，保证「已支付不被误取消、退款不重复、回调不重复处理」。
- **加购 upsert 原子累加**：`INSERT ... ON DUPLICATE KEY UPDATE` 数据库层累加，替代"先查再改"，并发加购不撞唯一键 / 不丢更新 / 不死锁。
- **退款幂等**：支付宝退款传 `out_request_no = 订单号`，同一订单重复退款返回幂等结果。

### 4. 安全设计

- **自定义 JWT 拦截器**：`@Auth` 注解声明需认证，支持 `requireAdmin=true` 强制管理员校验；每个 `@Auth` 请求查库，保证**用户禁用 / 改角色即时生效**。
- **上传安全**：类型白名单（jpg/png/gif/webp，排除 svg 防 XSS）+ 大小三层限制（前端/后端/nginx）+ UUID 重命名 + 日期子目录防路径穿越。
- **密码加密**：BCrypt，不存明文；统一异常体系（5 类异常 + 业务错误码），敏感字段不随 VO 返回。

### 5. AI 购物助手（RAG）

- **混合检索**：关键词 LIKE（商品名/描述）+ embedding 向量召回，合并后按相关性重排，拼入上下文交给通义千问生成导购回答，并返回参考商品卡片。
- **增量同步**：管理后台商品增删改实时 upsert/remove 知识库；应用启动全量重建兜底；无 API Key 时优雅降级不崩。

### 6. 工程化

- 严格 MVC 三层 + VO/DTO 隔离，Entity 不直接返回前端。
- **16 个订单核心单元测试**（JUnit 5 + Mockito），覆盖条件更新扣库存、状态机条件更新防并发、退单/回调幂等，不连库秒级跑完。
- 前端 axios 统一封装 token / 401 处理 / 错误提示；管理后台路由守卫 + 后端 `@Auth(requireAdmin=true)` 双端鉴权。

---

## 部署架构

```
浏览器
  │
  ▼
Nginx :80
  ├── /           → Vue 静态资源（dist）
  ├── /api/*      → 反向代理 backend:8074
  └── ^~ /upload/*→ 反向代理 backend（商品图片）
              │
              ▼
Spring Boot :8074（自定义 JWT 拦截器认证）
  ├── MySQL :3306   ├── Redis :6379   └── RabbitMQ :5672
```

生产环境建议：后端容器化（Dockerfile）+ 三件套容器 + Nginx 反代，见 `docker-compose.yml`。商品图片目录 `upload/` 与 AI 知识库索引 `data/` 已挂 Docker volume 持久化。

---

## 配置与安全说明

| 配置 | 位置 | 说明 |
|---|---|---|
| 数据库 / Redis / RabbitMQ | `application.yml` `spring.*` | 本地联调默认 `localhost`，Docker 环境用 `SPRING_DATASOURCE_*` 等环境变量覆盖 |
| 支付宝沙箱 | `application.yml` `alipay.*` | APPID / 商户私钥 / 支付宝公钥 / 沙箱网关 / 回调隧道地址；沙箱密钥无真实资金，可入库用于演示，**生产环境严禁提交** |
| 通义千问 API Key | `application.yml` `spring.ai.dashscope.api-key` | 已按项目内配置随 jar 打包部署；**生产环境建议改回环境变量注入，key 不进代码库** |
| 图片上传目录 | `app.upload-dir` | 默认 `./upload`（本仓库 `.gitignore` 已忽略，不上传图片文件） |

> ⚠️ 本仓库 `.gitignore` 已忽略 `happy_mart/upload/`（本地上传图片）与 `happy_mart/data/`（AI 向量索引），推送仓库不会携带运行时生成的图片与索引文件。

---

## 测试

```bash
cd happy_mart
mvn test -Dtest=OrderServiceImplTest   # 只跑订单核心业务单元测试（16 用例）
mvn test                               # 跑全部测试
```

---

## 更多文档

- **《众乐电子商城-开发设计文档 v1.13》**：`docs/design/众乐电子商城-开发设计文档.md` —— 含 8 张表 DDL、全部 API 清单、认证流程、下单/支付/超时取消/退单完整时序、Redis 与 RabbitMQ 方案详解、开发计划与简历亮点。