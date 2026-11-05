# 阿里云 2核2G 部署手册（宝塔面板 + Docker）

> 目标：在 2核2G / Linux 云服务器上用宝塔面板部署众乐电子商城，域名 `happy-mart.pw` 访问（备案期间先用公网 IP）。
>
> **本文件是命令行视角的完整方案**（架构取舍、内存预算、构建上传流程）。
> **纯面板点击操作的逐步指引**见同目录 `宝塔面板部署操作.md`——在面板里干活时看那一份更顺手。

---

## 一、最终架构

```
浏览器
  │  http://happy-mart.pw（备案期间先用 http://公网IP）
  ▼
宝塔 Nginx（宿主机，占 80）
  ├── /            → /www/wwwroot/happy-mart/dist（Vue 静态产物）
  ├── ^~ /api/     → 127.0.0.1:8074（Docker 后端）
  └── ^~ /upload/  → 127.0.0.1:8074（商品图片）
                        │
                        ▼
              Docker: hm-backend（Spring Boot）
                 ├── hm-mysql      127.0.0.1:3306
                 ├── hm-redis      127.0.0.1:6379
                 └── hm-rabbitmq   127.0.0.1:5672 / 15672
```

**为什么不把 Nginx 也放进容器**：宝塔自带 Nginx 且已占 80，再起一个容器 Nginx 会端口冲突；把前端交给宝塔还有额外好处——证书、日志、防火墙都在面板里可视化管，且省掉一个容器约 64MB 内存。

**内存预算（2G 的硬上限）**

| 项目 | 限制 | 说明 |
|---|---|---|
| hm-mysql | 448MB | buffer pool 压到 80M，关 performance_schema |
| hm-backend | 620MB | JVM 堆 384M + 元空间 160M |
| hm-rabbitmq | 288MB | 内存水位 50% |
| hm-redis | 96MB | maxmemory 64M |
| 宝塔面板 + Nginx | ~150MB | |
| 系统 + Docker | ~250MB | |
| **合计** | **~1.85GB** | **余量很薄，swap 是必需项不是优化项** |

---

## 二、服务器准备（一次性）

### 1. 阿里云安全组

入方向放行：

| 端口 | 用途 | 授权对象 |
|---|---|---|
| 22 | SSH | 你的本机 IP（别开 0.0.0.0/0） |
| 80 | 网站 | 0.0.0.0/0 |
| 8888 | 宝塔面板 | 你的本机 IP |

**不要**放行 3306 / 6379 / 5672 / 15672，compose 里已把它们绑到 `127.0.0.1`，公网访问不到。

### 2. 加 2G swap（必做）

2G 内存跑这套全家桶，没有 swap 会在构建或 AI 请求时被 OOM Killer 杀进程。

```bash
# 创建 2G swap 文件
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 开机自动挂载
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 降低 swap 倾向（只有真紧张才用 swap，不然会拖慢响应）
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
sudo sysctl -p

# 验证
free -h
```

`free -h` 最后一行 Swap 应该是 2.0Gi。

### 3. 安装宝塔

```bash
# 阿里云 / CentOS / Alibaba Cloud Linux
yum install -y wget && wget -O install.sh https://download.bt.cn/install/install_6.0.sh && sh install.sh ed8484bec

# Ubuntu / Debian
wget -O install.sh https://download.bt.cn/install/install-ubuntu_6.0.sh && sudo bash install.sh ed8484bec
```

装完会输出面板地址、用户名、密码，记下来。

### 4. 宝塔软件的取舍（关键，别按默认全装）

进面板 → 软件商店，**只装这两个**：

| 装 | 说明 |
|---|---|
| Nginx 1.24（或 1.26） | 托管前端 + 反代后端 |
| Docker 管理器 | 管容器和 compose |

**绝对不要装**：MySQL、Redis、PHP、phpMyAdmin、Redis 管理器、Memcached。
理由：宝塔装的 MySQL 会抢占 3306，Redis 抢占 6379，而且会额外吃 400MB+ 内存——和 Docker 里的容器直接撞车。

> 需要看数据库怎么办：用 `docker exec -it hm-mysql mysql -uroot -p` 进命令行，或装宝塔的 **phpMyAdmin（Docker 版）**。

### 5. 关掉用不上的宝塔常驻服务

宝塔面板 → 设置 → 可以关掉「监控」「防火墙日志」这类常驻任务，能省 30~60MB。
如果面板里开了「网站防篡改」「文件监控」，也建议关掉。

### 6. 配 Docker 镜像加速（不做会卡在拉镜像）

国内直连 Docker Hub 拉 mysql:8.0（800MB+）经常几分钟没进度甚至超时。用阿里云自己的加速器：

```
# 登录阿里云 → 容器镜像服务 → 镜像工具 → 镜像加速器
# 拿到你的专属地址：https://<你的ID>.mirror.aliyuncs.com
```

宝塔 → Docker → 设置 → 镜像加速，填入上面的地址保存。

或者命令行：

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": ["https://你的ID.mirror.aliyuncs.com"]
}
EOF
sudo systemctl daemon-reload
sudo systemctl restart docker
```

**注意**：宝塔的 Docker 管理器和命令行 `docker` 用同一份 daemon 配置，改完两边都生效。

---

## 二点五、域名接入（happy-mart.pw）

### 1. 先等实名认证

域名刚买时状态是「实名认证审核中」，这是**硬阻塞**：实名没过，域名解析不生效、也提交不了备案。

- 阿里云控制台 → 域名 → 我的域名，看状态是不是「实名认证通过」
- 通常 1~3 个工作日，个人身份证信息一般当天到 1 天
- 认证信息必须和备案主体完全一致（本人姓名 + 身份证号），不一致备案会被打回

### 2. DNS 解析

域名 → 解析设置 → 添加两条 A 记录：

| 记录类型 | 主机记录 | 记录值 | TTL |
|---|---|---|---|
| A | `@` | 服务器公网 IP | 600 |
| A | `www` | 服务器公网 IP | 600 |

验证（本地跑，等 10 分钟生效）：

```bash
ping happy-mart.pw
nslookup happy-mart.pw
```

出 IP 就算通。**注意**：解析通了不等于能访问——大陆服务器未备案的域名，80/443 会被阿里云阻断，浏览器会看到"该网站未备案"的提示页。这是正常现象，不是配置错了。

### 3. 备案（决定域名能不能用）

备案在**阿里云备案控制台**（beian.aliyun.com）提交，免费，流程：

1. 先跑「检测域名是否可备案」，输入 `happy-mart.pw` —— **这一步是 .pw 的关键验证，检测不过就别往下走了**
2. 检测通过 → 填写主体信息（个人：身份证、手机、住址）+ 网站信息
3. 网站名称和用途**别写"商城""在线销售""电商"**，个人备案被判定经营性会被要求补 ICP 许可证，个人办不下来
   写「个人技术学习与作品展示」这类描述即可
4. 阿里云初审（1~2 个工作日，会打电话核验，保持手机畅通）
5. 工信部短信核验（24 小时内必须点，过期要重来）
6. 管局审核（5~20 个工作日）
7. 下发备案号，然后在网站底部挂备案号链接到 beian.miit.gov.cn

**备案对服务器有硬要求**（不满足直接提交不了）：

- 必须是**包年包月**的 ECS 或轻量应用服务器，**按量付费实例拿不到备案服务码**
- 服务器**剩余时长 ≥ 3 个月**
- 一台 ECS 可申请 5 个备案服务码

如果你买的是按量付费或只买了 1 个月，先去续费或转包年包月，否则备案这一关过不去。

### 4. 备案期间的访问策略

| 阶段 | 用什么地址 | 说明 |
|---|---|---|
| 备案审核中 | `http://公网IP/` | IP 访问不受备案限制，功能全部正常 |
| 备案通过后 | `http://happy-mart.pw/` | 改 nginx `server_name` + `.env` 的回调地址，重载 |

**这段时间 `.env` 的 `ALIPAY_NOTIFY_URL` 先填 IP**，支付宝回调照样能通。备案下来后改成域名，`docker compose up -d` 重建 backend 容器生效。

### 5. 备案通过后的切换清单

```bash
cd /www/wwwroot/happy-mart

# 1. 改回调地址为域名
vim .env          # ALIPAY_NOTIFY_URL=http://happy-mart.pw/api/pay/notify
docker compose up -d backend

# 2. 确认 nginx 配置里 server_name 是 happy-mart.pw www.happy-mart.pw
nginx -t && nginx -s reload

# 3. 从外部验证公网链路
curl -X POST http://happy-mart.pw/api/pay/notify -d "test=1"
# 返回 failure 就是通了（验签失败属正常）
```

---

---

## 三、本地构建与上传

### 1. 本地构建后端 jar

```bash
cd E:/PeopleHappy/happy_mart
./mvnw clean package -DskipTests
# 产物：target/happy-mart-0.0.1-SNAPSHOT.jar
```

### 2. 本地构建前端

```bash
cd E:/PeopleHappy/happy-mart-web
npm run build
# 产物：dist/
```

### 3. 组装上传包

把产物放进 `deploy/`，整个目录打包上传：

```bash
cd E:/PeopleHappy

# 拷产物进 deploy
cp happy_mart/target/happy-mart-0.0.1-SNAPSHOT.jar deploy/backend/app.jar
mkdir -p deploy/dist && cp -r happy-mart-web/dist/* deploy/dist/

# 可选：带上 200 张演示商品图（见第六节「商品图加载不出来」）
mkdir -p deploy/data/upload && cp -r happy_mart/upload/* deploy/data/upload/

# 打包
cd deploy && tar -czf ../happy-mart-deploy.tar.gz .
```

最终上传包结构：

```
happy-mart-deploy.tar.gz
├── docker-compose.yml
├── .env.example
├── sql/
│   ├── 01-schema.sql        # 8 张表建表（本次新增，仓库原本缺失）
│   └── 02-init-data.sql     # 8 分类 + 12 商品
├── sql-optional/
│   └── 03-local-images.sql  # 可选：商品图换本地图
├── backend/
│   └── app.jar
├── dist/                    # 前端产物
├── data/upload/             # 可选：200 张演示图
├── nginx/
│   └── baota-site.conf      # 宝塔站点配置参考
└── DEPLOY.md
```

### 4. 上传到服务器

方式 A（宝塔文件管理器，最省事）：
面板 → 文件 → 进入 `/www/wwwroot` → 上传 `happy-mart-deploy.tar.gz` → 右键解压到 `/www/wwwroot/happy-mart`

方式 B（命令行）：

```bash
scp happy-mart-deploy.tar.gz root@公网IP:/www/wwwroot/
ssh root@公网IP
mkdir -p /www/wwwroot/happy-mart
tar -xzf /www/wwwroot/happy-mart-deploy.tar.gz -C /www/wwwroot/happy-mart
```

---

## 四、启动

### 1. 配置环境变量

```bash
cd /www/wwwroot/happy-mart
cp .env.example .env

# 生成三组随机密码
openssl rand -base64 24   # → MYSQL_ROOT_PASSWORD
openssl rand -base64 24   # → RABBITMQ_PASSWORD
openssl rand -base64 32   # → JWT_SECRET

vim .env
```

`.env` 必须填对的四项：

```ini
MYSQL_ROOT_PASSWORD=刚才生成的密码
RABBITMQ_USER=hm_admin
RABBITMQ_PASSWORD=刚才生成的密码
ALIPAY_NOTIFY_URL=http://happy-mart.pw/api/pay/notify
JWT_SECRET=刚才生成的32位密钥
# GLM_API_KEY 用不到就整行注释掉，别留 "GLM_API_KEY="（空值会覆盖兜底默认值）
# GLM_API_KEY=你的智谱key
```

> ⚠️ **MySQL 密码一旦容器初始化就固化在数据卷里**，之后改 `.env` 不生效。所以第一次启动前务必确认 `.env` 填对了。填错了要清卷重来：
> ```bash
> docker compose down -v && rm -rf data/mysql && docker compose up -d
> ```

### 2. 准备目录

```bash
cd /www/wwwroot/happy-mart
mkdir -p data/upload data/kb logs
chmod -R 755 data logs
```

### 3. 启动容器

```bash
cd /www/wwwroot/happy-mart
docker compose up -d

# 看启动进度（首次要初始化数据库，约 1~2 分钟）
docker compose ps
docker compose logs -f mysql
```

等到 `docker compose ps` 里 mysql / redis / rabbitmq 都是 `healthy`、backend 是 `running`。

### 4. 验证数据库初始化成功

```bash
docker exec -it hm-mysql mysql -uroot -p -e "
USE happy_mart;
SHOW TABLES;
SELECT COUNT(*) AS product_count FROM product;
"
```

预期：8 张表，`product_count = 12`。

如果表是空的或报错，说明初始化脚本没跑（通常是数据卷已存在时不会重跑），清卷重来：

```bash
docker compose down -v && rm -rf data/mysql && docker compose up -d
```

### 5. 验证后端

```bash
curl -s http://127.0.0.1:8074/api/category/tree | head -c 300
```

返回 JSON 且含分类数据即正常。若报错看日志：

```bash
docker compose logs -f backend
```

---

## 五、宝塔建站（前端 + 反代）

### 1. 添加站点

宝塔 → 网站 → 添加站点：

| 项 | 值 |
|---|---|
| 域名 | `happy-mart.pw`（备案没下来之前先用公网 IP 建站） |
| 根目录 | `/www/wwwroot/happy-mart/dist` |
| FTP | 不创建 |
| 数据库 | 不创建 |
| PHP 版本 | **纯静态** |

### 2. 替换站点配置

站点 → 设置 → 配置文件，把整个 `server { }` 块换成 `deploy/nginx/baota-site.conf` 的内容（`server_name` 已写好 `happy-mart.pw www.happy-mart.pw`）。

关键三段别漏：

- `location /` 里的 `try_files $uri $uri/ /index.html` —— 不加的话刷新 `/admin/order` 会 404
- `client_max_body_size 5m` —— 不加的话传 1MB 以上的商品图报 413
- `location ^~ /upload/` 前面的 `^~` —— 不加的话图片会被静态规则拦截后去 dist 找文件，全是 404

### 3. 重载 Nginx

```bash
nginx -t && nginx -s reload
```

或面板里点「重载配置」。

---

## 六、验收清单

按顺序点一遍，全绿才算部署完成：

| # | 检查项 | 预期 |
|---|---|---|
| 1 | `http://happy-mart.pw/`（备案未下则用 `http://公网IP/`） | 商城首页，商品卡片正常渲染 |
| 2 | 商品图显示 | picsum.photos 外链图能加载（服务器需能访问外网） |
| 3 | 商品详情页 `/#/product/1` 直接刷新 | 不 404 |
| 4 | 注册 → 登录 | 返回 token，跳转正常 |
| 5 | 加购物车 → 下单 | 订单创建成功，商品库存减少 |
| 6 | 下单后 30 分钟（或临时改 TTL 测） | 未支付订单自动变「已取消」，库存恢复 |
| 7 | 进入支付页 | 返回支付宝二维码 |
| 8 | 支付宝沙箱扫码付款 | 订单变「已支付」（回调打通） |
| 9 | 后台 `/admin` | 需 ADMIN 账号，非管理员 403 |

**开管理员账号**：

```bash
# 先在前台注册一个账号，然后提权
docker exec -it hm-mysql mysql -uroot -p -e "
USE happy_mart;
UPDATE \`user\` SET role='ADMIN' WHERE username='你的账号';
"
```

重新登录后即可进 `/admin`。

**验证支付宝回调是否打通**（最容易出问题的一环）：

```bash
# 服务器本机先测后端收不收得到
curl -X POST http://127.0.0.1:8074/api/pay/notify -d "test=1"

# 再从外部测公网链路（在你本机跑）
curl -X POST http://happy-mart.pw/api/pay/notify -d "test=1"
```

两个都返回 `failure`（验签失败是正常的，说明路由通了）。如果第二个超时/502，是宝塔反代没生效或安全组没开 80。

---

## 六点五、商品图加载不出来的处理

初始化数据里 12 个商品的图片是 `https://picsum.photos/...` 外链，**国内浏览器访问这个域名经常超时**，部署后很可能看到首页一片空白图框。这不是部署失败，是外链不可达。

换成服务器本地图（仓库自带 200 张演示图）：

```bash
cd /www/wwwroot/happy-mart

# 1. 确认本地图已上传（打包时带了 deploy/data/upload）
ls data/upload/20260815/ | head -3

# 2. 执行替换脚本
docker exec -i hm-mysql mysql -uroot -p你的密码 happy_mart < sql-optional/03-local-images.sql

# 3. 清掉商品详情缓存，否则还要等 30 分钟
docker exec hm-redis redis-cli FLUSHDB
```

刷新页面，商品图应该变成站内 `/upload/20260815/prodX.jpg`。

如果还是 404，说明后端的 upload volume 没挂对：

```bash
docker exec hm-backend ls /app/upload/20260815/ | head -3
```

列表为空 → 检查 `docker-compose.yml` 里 `./data/upload:/app/upload` 和宿主机目录是否对得上。

---

## 七、故障排查

| 现象 | 原因 | 处理 |
|---|---|---|
| 容器反复重启，`dmesg` 有 `Killed process` | 内存不足被 OOM Killer 杀 | `free -h` 确认 swap 生效；`docker stats` 看哪个容器超限；调低对应 mem_limit 与 JVM 堆 |
| 后端日志 `PLAIN login refused` 或 `ACCESS_REFUSED` | RabbitMQ 用了 guest 账号（只允许 localhost） | 确认 `.env` 里 `RABBITMQ_USER` 不是 guest；若是 guest 需清 `data/rabbitmq` 重建 |
| 后端日志 `Unknown column` / `Table doesn't exist` | SQL 初始化没执行 | 清数据卷重来（见四.4） |
| 后端日志 `Access denied for user 'root'` | `.env` 密码与数据卷里的不一致 | 清 `data/mysql` 重来，或把密码改回数据卷里那个 |
| 首页白屏、静态资源 404 | 站点根目录指错，或 dist 没传全 | 确认根目录是 `/www/wwwroot/happy-mart/dist`，里面有 `index.html` 和 `assets/` |
| 刷新子路由 404 | 缺 `try_files` | 见五.2 |
| 商品图 404（新上传的） | `^~` 缺失或 upload volume 没挂上 | 见五.2；`docker exec hm-backend ls /app/upload` 确认文件在 |
| 上传图片报 413/500 | `client_max_body_size` 没调 | 见五.2 |
| 支付完成但订单还是待支付 | 回调地址不通 | `ALIPAY_NOTIFY_URL` 必须是公网可达的 `http://happy-mart.pw/api/pay/notify`（备案未下时用 IP）；改完 `.env` 需 `docker compose up -d` 重建容器 |
| AI 助手报错 | `GLM_API_KEY` 没配或额度不足 | 留空即可，其他功能不受影响 |

**看内存实况**

```bash
free -h
docker stats --no-stream
```

可用内存持续低于 100MB 且 swap 使用率超过 50%，说明 2G 到顶了，考虑升 2核4G 或把 MySQL 换成阿里云 RDS。

---

## 八、日常运维

### 更新代码

```bash
# 本地重新构建 jar / dist 后上传覆盖
cd /www/wwwroot/happy-mart
docker compose restart backend        # 只换后端
# 前端换完 dist 后 nginx -s reload 即可，不用重启容器
```

### 备份数据库

```bash
mkdir -p /www/backup
docker exec hm-mysql mysqldump -uroot -p密码 happy_mart | gzip > /www/backup/happy_mart_$(date +%F).sql.gz
```

宝塔 → 计划任务 → Shell 脚本，设成每天凌晨 3 点跑上面这行，保留 7 天。

### 看日志

```bash
docker compose logs -f backend        # 后端
docker compose logs -f mysql          # 数据库
docker compose logs --tail=100        # 全部
tail -f /www/wwwlogs/happy-mart.log   # Nginx 访问日志
```

### 重启整套

```bash
cd /www/wwwroot/happy-mart
docker compose down && docker compose up -d
```

---

## 九、还没做的两件事（部署后按需补）

1. **支付宝私钥仍是明文**在 `happy_mart/src/main/resources/application.yml`（沙箱密钥，无真实资金，风险可控）。要彻底处理就把它改成 `${ALIPAY_PRIVATE_KEY}` 环境变量注入，跟 GLM_API_KEY 一样走 `.env`。
2. **`POST /api/pay/simulate/{orderId}` 模拟支付调试口仍存在**，公网部署后任何人都能调它把自己的订单标成已支付。要么删掉这个 Controller 方法重建 jar，要么在 Nginx 里拦掉：
   ```nginx
   location = /api/pay/simulate {
       return 403;
   }
   ```
   （正则匹配更保险：`location ~ ^/api/pay/simulate/ { return 403; }`）

---

## 十、自动更新部署（2026-11-05 起）

日常发版不必再手动 `scp` 产物 + 面板里解压：

```bash
# 本地：构建 + 打包 + 写清单 + 推 git + 建 Gitee Release（需 GITEE_TOKEN）
bash deploy/release.sh
```

服务器侧由**宝塔「计划任务」**每 10 分钟轮询 `deploy/manifest.txt`，有新版本就下载 → 原子切换 → `/api/health` 检查 → 失败自动回滚。

- 服务器脚本：`deploy/update.sh`（+ `deploy/update.conf` 配置）
- 面板配置、故障排查、命令速查：**`deploy/AUTO-DEPLOY.md`**

**服务器上依然不做构建**（2G 内存跑不动 Maven/Vite，见第三节）。
