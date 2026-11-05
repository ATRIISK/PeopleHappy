# 自动更新部署（宝塔面板 + Gitee Release）

> 一句话：**本地/CI 构建 → 发布为 Gitee Release → 宝塔计划任务每 10 分钟拉取并原子切换 → 健康检查失败自动回滚。**
> 服务器上**零构建**（2G 内存跑不动 Maven/Vite，详见「为什么不能在服务器构建」）。

配套文件：

| 文件 | 位置 | 作用 |
|---|---|---|
| `deploy/release.sh` | 本地仓库 | 构建 + 打包 + 写清单 + 推 git + 建 Gitee Release |
| `deploy/manifest.txt` | 本地仓库（自动生成） | 版本清单，服务器据此判断有没有新版本 |
| `deploy/update.sh` | 服务器 | 拉产物 → 原子切换 → 健康检查 → 回滚 |
| `deploy/update.conf` | 服务器 | 可调参数（路径、仓库、健康检查地址等） |

---

## 一、为什么不能在服务器构建

服务器是 2 核 2G，容器常驻内存已经吃满：

```
hm-backend 620m + hm-mysql 448m + hm-rabbitmq 288m + hm-redis 96m = 1452MB
+ 宝塔面板约 150MB + 系统约 250MB ≈ 1.85GB
```

再加一次 Maven 多阶段构建（峰值 800MB~1.2GB）或 Vite 构建（300~600MB），
会直接触发 OOM Killer，把线上 MySQL 或后端一起带走。所以**构建必须在服务器之外完成**，
服务器只负责「接收产物 + 切换」。

---

## 二、整体流程

```
本地 deploy/release.sh
   ├─ npm run build            → happy-mart-web/dist
   ├─ ./mvnw package           → happy_mart/target/*.jar
   ├─ 打包 dist-<ver>.zip、app-<ver>.jar，算 sha256
   ├─ 写 deploy/manifest.txt 并 push 到 master
   └─ 建 Gitee Release v<ver>，上传两个产物
                    ↓
服务器（宝塔计划任务，每 10 分钟）
   ├─ 拉 deploy/manifest.txt（带时间戳破 CDN 缓存）
   ├─ 版本 == 本地 VERSION → 直接退出
   ├─ 下载产物 → sha256 校验
   ├─ 前端：解压到 dist.extract → mv 目录换掉 dist（旧版留 dist.prev）
   ├─ 后端：覆盖 app.jar（旧的存 app.jar.prev）→ docker compose restart backend
   ├─ 健康检查 http://127.0.0.1:8074/api/health，最多等 90s
   └─ 失败 → 自动回滚前后端，写 VERSION，清理旧产物
```

---

## 三、一次性配置（服务器侧）

### 3.1 传脚本

在宝塔面板「文件」里把仓库 `deploy/` 下的这几个文件传到服务器 `/www/wwwroot/happy-mart/deploy/`：

- `update.sh`
- `update.conf`
- `manifest.txt`（首次可先不放，脚本会自己从 Gitee 拉）

或者用面板终端 + scp：

```bash
# 本地执行
scp deploy/update.sh deploy/update.conf root@115.29.29.83:/www/wwwroot/happy-mart/deploy/
```

### 3.2 确认依赖

服务器需要 `curl`、`unzip`、`sha256sum`、`docker`（宝塔基础环境一般都有）：

```bash
for c in curl unzip sha256sum docker; do command -v $c || echo "缺 $c"; done
```

### 3.3 加计划任务

宝塔面板 → 左侧「**计划任务**」→ 添加任务：

| 项 | 填什么 |
|---|---|
| 任务类型 | Shell 脚本 |
| 任务名称 | 众乐商城自动部署 |
| 执行周期 | 每 10 分钟 |
| 脚本内容 | `bash /www/wwwroot/happy-mart/deploy/update.sh >> /www/wwwlogs/hm-deploy.log 2>&1` |

提交后点「执行」手动跑一次，面板里能看到输出；日志同时落在 `/www/wwwlogs/hm-deploy.log`。

> 面板自带的计划任务默认以 root 身份执行，脚本内部路径全是绝对路径，无需设置工作目录。

### 3.4 首次验证

```bash
# 只比对版本不部署
bash /www/wwwroot/happy-mart/deploy/update.sh --check
```

第一次跑会因为 `manifest.txt` 里版本为空而报「版本清单缺少 version/tag 字段」，这是正常的——
等第一次正式发布后才会拉到有效清单。

---

## 四、发布（本地）

### 4.1 准备 Gitee 私人令牌

Gitee → 头像 → 设置 → 私人令牌 → 生成，勾选 `projects` 权限，复制 token。

```bash
# Git Bash
export GITEE_TOKEN=你的token
# PowerShell
$env:GITEE_TOKEN='你的token'
```

### 4.2 执行发布

```bash
cd E:/PeopleHappy
bash deploy/release.sh
```

脚本会：构建前后端 → 打包 → 写清单 → commit & push 到 `origin/master`（Gitee）→ 建 Release 并上传产物。
完成后服务器最多 10 分钟内自动部署。

常用参数：

| 参数 | 说明 |
|---|---|
| `--skip-backend` | 只发前端（改样式时用，最快） |
| `--skip-frontend` | 只发后端 |
| `--version 20261105-1030` | 指定版本号，默认取时间戳 |
| `--no-push` | 只生成产物与清单，不推 git、不建 Release（先看看产物对不对） |
| `--dry-run` | 只打印流程 |

### 4.3 也可以手工发

不想用脚本时，在 Gitee 仓库页手动「发行版 → 新建」：
tag 填 `v<版本号>`，把 `dist-<版本号>.zip` 和 `app-<版本号>.jar` 拖上去，
然后手工把 `deploy/manifest.txt` 按同样格式改好并 push。

---

## 五、manifest.txt 格式

```properties
version=20261105-1030
tag=v20261105-1030
dist=dist-20261105-1030.zip
dist_sha256=3f2a...（64 位）
jar=app-20261105-1030.jar
jar_sha256=9c81...（64 位）
```

- `dist` / `jar` 留空表示本次不更新该部分
- 也可以直接写完整地址绕过 Release 资产（jar 超过 Gitee 附件上限时用）：
  ```properties
  jar_url=https://your-bucket.oss-cn-hangzhou.aliyuncs.com/app-20261105-1030.jar
  ```
  `dist_url` 同理。清单里给了 `*_url` 时优先用它。

---

## 六、失败排查

| 现象 | 原因 | 处理 |
|---|---|---|
| 计划任务日志报「拉取版本清单失败」 | Gitee raw 访问异常 / 仓库改私有了 | 服务器上 `curl -I https://gitee.com/liao-zhenhan/happy-mart/raw/master/deploy/manifest.txt`；仓库必须保持公开 |
| 「下载失败」 | Release 资产没传完，或 tag 不匹配 | 在 Gitee 页面确认资产已上传完毕；核对 manifest 的 tag 与 Release tag 一致 |
| 「sha256 不匹配」 | 传输损坏 | 脚本已拒绝部署，重跑即可 |
| 「前端切换失败 / 产物结构不对」 | zip 顶层多套了一层 `dist/` | 用 `unzip -l dist.zip \| head` 检查，zip 内应直接是 `index.html` 和 `assets/` |
| 「健康检查超时」 | 后端启动失败或依赖没起来 | 看 `docker compose logs --tail=100 backend`；脚本会自动回滚 |
| 后端换了但行为还是旧的 | jar 是单文件 bind mount，**必须重启容器** | 脚本已自动 `docker compose restart backend`；手动换 jar 时同样要 restart |
| 页面还是旧版本 | 浏览器缓存 | `Ctrl+F5` 强刷；`favicon.svg` / `icons.svg` 不带 hash 且缓存 30 天，需改名或强刷 |
| 计划任务没执行 | 宝塔任务被禁用 / 权限不足 | 面板里看任务状态和执行日志 |

---

## 七、当前缺口（自动化管不了的部分）

| 缺口 | 说明 | 建议 |
|---|---|---|
| **数据库 schema 变更** | 没有 Flyway/Liquibase，8 张表靠手写 SQL；自动化只管代码，**新表新字段仍要人工执行** | 后续引入 Flyway，迁移脚本随版本一起发 |
| **回滚粒度** | 前端按目录回滚（保留 `dist.prev`），后端按 jar 回滚（保留 `app.jar.prev`），各留 1 份 | 够了；再想稳可以保留 3 份 |
| **告警** | 部署失败只写日志，不主动通知 | 宝塔「计划任务」可配告警插件，或脚本里加钉钉/邮件 webhook |
| **`/api/health` 公网暴露** | 会返回依赖状态与耗时 | 可选加固：宝塔站点配置加 `location = /api/health { allow 127.0.0.1; deny all; }` |

---

## 八、命令速查

```bash
# 服务器侧
bash /www/wwwroot/happy-mart/deploy/update.sh            # 正常执行
bash /www/wwwroot/happy-mart/deploy/update.sh --check     # 只比对版本
bash /www/wwwroot/happy-mart/deploy/update.sh --force     # 强制重装当前版本
tail -f /www/wwwlogs/hm-deploy.log                        # 看部署日志

cd /www/wwwroot/happy-mart && docker compose ps           # 看容器状态
cd /www/wwwroot/happy-mart && docker compose logs -f backend
curl -s http://127.0.0.1:8074/api/health                  # 手动探活

# 本地侧
bash deploy/release.sh --skip-backend                     # 只发前端
bash deploy/release.sh --no-push                          # 只出产物不发布
```
