#!/usr/bin/env bash
# =============================================
# 众乐电子商城 - 自动部署脚本（服务器端，由宝塔计划任务调用）
#
# 定位：只做「接收产物 → 原子切换 → 健康检查 → 失败回滚」，**服务器上零构建**。
#       构建在本地/CI 完成后发布为 Gitee Release，脚本从 Release 拉取产物。
#
# 用法：
#   bash /www/wwwroot/happy-mart/deploy/update.sh            # 正常执行（有新版本才动）
#   bash /www/wwwroot/happy-mart/deploy/update.sh --check    # 只比对版本，不部署
#   bash /www/wwwroot/happy-mart/deploy/update.sh --force    # 忽略版本号强制重装
#
# 宝塔面板 → 计划任务 → Shell 脚本 → 每 10 分钟：
#   bash /www/wwwroot/happy-mart/deploy/update.sh >> /www/wwwlogs/hm-deploy.log 2>&1
# =============================================

set -euo pipefail

# ---------- 配置 ----------
CONF_FILE="${CONF_FILE:-/www/wwwroot/happy-mart/deploy/update.conf}"
if [[ -f "$CONF_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$CONF_FILE"
fi

BASE_DIR="${BASE_DIR:-/www/wwwroot/happy-mart}"
REPO_OWNER="${REPO_OWNER:-liao-zhenhan}"
REPO_NAME="${REPO_NAME:-happy-mart}"
REPO_BRANCH="${REPO_BRANCH:-master}"

DIST_DIR="$BASE_DIR/dist"
DIST_PREV_DIR="$BASE_DIR/dist.prev"
DIST_NEW_DIR="$BASE_DIR/dist.new"

BACKEND_DIR="$BASE_DIR/backend"
JAR_PATH="$BACKEND_DIR/app.jar"
JAR_PREV_PATH="$BACKEND_DIR/app.jar.prev"

VERSION_FILE="$BASE_DIR/VERSION"
TMP_DIR="/tmp/hm-release"
KEEP_RELEASES=2

HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8074/api/health}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-60}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-3}"

LOG_FILE="${LOG_FILE:-/www/wwwlogs/hm-deploy.log}"
NGINX_BIN="${NGINX_BIN:-/www/server/nginx/sbin/nginx}"

# ---------- 运行参数 ----------
MODE="deploy"
for arg in "$@"; do
  case "$arg" in
    --check) MODE="check" ;;
    --force) MODE="force" ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

mkdir -p "$(dirname "$LOG_FILE")" "$TMP_DIR"

log() {
  local level="$1"; shift
  local line="[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $*"
  echo "$line"
  echo "$line" >> "$LOG_FILE"
}

# ---------- 前置依赖检查 ----------
check_deps() {
  local missing=()
  for cmd in curl unzip sha256sum docker; do
    command -v "$cmd" >/dev/null 2>&1 || missing+=("$cmd")
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    log ERROR "缺少依赖命令: ${missing[*]}（宝塔面板终端里可 yum/apt 安装）"
    return 1
  fi
  return 0
}

# 宝塔的 Docker 管理器在不同版本里装的是 docker compose(v2) 或 docker-compose(v1)
compose_cmd() {
  if docker compose version >/dev/null 2>&1; then
    echo "docker compose"
  elif command -v docker-compose >/dev/null 2>&1; then
    echo "docker-compose"
  else
    return 1
  fi
}

# ---------- 防重入锁 ----------
# 计划任务间隔短于一次部署耗时会重叠执行，这里用 flock 串行化；
# 没有 flock 的精简系统退化成 mkdir 锁。
acquire_lock() {
  local lockfile="/var/lock/hm-deploy.lock"
  if command -v flock >/dev/null 2>&1; then
    exec 9>"$lockfile"
    if ! flock -n 9; then
      log WARN "上一次部署还在跑，本次跳过"
      exit 0
    fi
  else
    local d="/tmp/hm-deploy.lock.d"
    if ! mkdir "$d" 2>/dev/null; then
      log WARN "上一次部署还在跑（mkdir 锁），本次跳过"
      exit 0
    fi
    trap 'rmdir "$d" 2>/dev/null || true' EXIT
  fi
}

# ---------- 拉取版本清单 ----------
MANIFEST_URL="https://gitee.com/${REPO_OWNER}/${REPO_NAME}/raw/${REPO_BRANCH}/deploy/manifest.txt"

fetch_manifest() {
  local out="$TMP_DIR/manifest.txt"
  # 加时间戳破 CDN 缓存，否则拿到的可能是几分钟前的旧清单
  if ! curl -fsSL --connect-timeout 15 --max-time 60 "${MANIFEST_URL}?t=$(date +%s)" -o "$out"; then
    log ERROR "拉取版本清单失败: $MANIFEST_URL"
    return 1
  fi
  MANIFEST="$out"
  return 0
}

manifest_value() {
  local key="$1"
  [[ -f "${MANIFEST:-}" ]] || return 0
  grep -E "^${key}=" "$MANIFEST" 2>/dev/null | head -n 1 | cut -d= -f2- | tr -d '\r' || true
}

# ---------- 下载产物 ----------
# 传完整 URL；支持两种来源：
#   1) Gitee Release 资产：https://gitee.com/{owner}/{repo}/releases/download/{tag}/{asset}
#   2) 清单里直接给 *_url（对象存储直链，jar 超过 Gitee 附件上限时用）
download_asset() {
  local url="$1" dest="$2" label="$3"
  log INFO "下载 $label ← $url"
  rm -f "$dest"
  if ! curl -fL --retry 3 --retry-delay 5 --connect-timeout 15 --max-time 900 -o "$dest" "$url"; then
    log ERROR "下载失败: $url"
    return 1
  fi
  return 0
}

verify_sha256() {
  local file="$1" expect="$2" label="${3:-产物}"
  [[ -n "$expect" ]] || { log WARN "$label 未提供 sha256，跳过校验"; return 0; }
  local actual
  actual="$(sha256sum "$file" | cut -d' ' -f1)"
  if [[ "$actual" != "$expect" ]]; then
    log ERROR "$label sha256 不匹配：期望=$expect 实际=$actual"
    return 1
  fi
  log INFO "$label sha256 校验通过"
  return 0
}

# ---------- 前端切换（原子） ----------
apply_dist() {
  local zip="$1"
  # 解压目录必须和 dist 同盘（BASE_DIR 下），跨文件系统 mv 会退化成复制，失去原子性
  local extract_dir="$BASE_DIR/dist.extract"

  rm -rf "$extract_dir"
  mkdir -p "$extract_dir"
  unzip -q -o "$zip" -d "$extract_dir" || { log ERROR "dist.zip 解压失败"; return 1; }

  # 产物结构校验：zip 内容应是 dist 内部的文件（顶层直接是 index.html + assets/）
  if [[ ! -f "$extract_dir/index.html" ]]; then
    log ERROR "dist.zip 顶层没有 index.html，产物结构不对（zip 里多套了一层 dist/？）"
    return 1
  fi
  if [[ ! -d "$extract_dir/assets" ]]; then
    log WARN "dist.zip 里没有 assets 目录，确认前端产物是否完整"
  fi

  rm -rf "$DIST_NEW_DIR"
  mv "$extract_dir" "$DIST_NEW_DIR"

  # 关键：先移走旧目录再改名新目录，中间窗口用户可能拿到 404，
  #       所以 mv 必须在同一文件系统内完成（rename 是原子的）
  rm -rf "$DIST_PREV_DIR"
  if [[ -d "$DIST_DIR" ]]; then
    mv -T "$DIST_DIR" "$DIST_PREV_DIR" || { log ERROR "旧 dist 目录移动失败"; return 1; }
  fi
  mv -T "$DIST_NEW_DIR" "$DIST_DIR" || { log ERROR "新 dist 目录切换失败"; return 1; }

  # 目录整体换 inode 后 reload 一下 nginx，避免 open_file_cache 指向旧目录
  if [[ -x "$NGINX_BIN" ]]; then
    if "$NGINX_BIN" -t >/dev/null 2>&1; then
      "$NGINX_BIN" -s reload >/dev/null 2>&1 && log INFO "nginx 已 reload" || log WARN "nginx reload 失败（静态资源仍会更新，可忽略）"
    else
      log WARN "nginx 配置检查未通过，未 reload"
    fi
  fi

  log INFO "前端已切换到新版本（旧版本保留在 dist.prev）"
  return 0
}

rollback_dist() {
  if [[ -d "$DIST_PREV_DIR" ]]; then
    rm -rf "$DIST_DIR"
    mv -T "$DIST_PREV_DIR" "$DIST_DIR" && log WARN "前端已回滚到上一版本"
    [[ -x "$NGINX_BIN" ]] && "$NGINX_BIN" -s reload >/dev/null 2>&1 || true
  else
    log ERROR "没有 dist.prev，无法回滚前端"
  fi
}

# ---------- 后端切换 ----------
apply_jar() {
  local jar="$1"
  if [[ -f "$JAR_PATH" ]]; then
    cp -f "$JAR_PATH" "$JAR_PREV_PATH" || { log ERROR "备份旧 jar 失败"; return 1; }
  fi
  cp -f "$jar" "$JAR_PATH" || { log ERROR "覆盖 app.jar 失败"; return 1; }

  # app.jar 是单文件 bind mount，宿主机替换文件后容器仍持有旧 inode，
  # 必须重启容器才会读到新 jar（热替换无效）
  log INFO "重启后端容器"
  local cmd
  cmd="$(compose_cmd)" || { log ERROR "找不到 docker compose / docker-compose 命令"; return 1; }
  if ! ( cd "$BASE_DIR" && $cmd restart backend ) >>"$LOG_FILE" 2>&1; then
    log ERROR "$cmd restart backend 失败"
    return 1
  fi
  return 0
}

rollback_jar() {
  if [[ -f "$JAR_PREV_PATH" ]]; then
    cp -f "$JAR_PREV_PATH" "$JAR_PATH"
    local cmd
    cmd="$(compose_cmd)" || { log ERROR "找不到 docker compose 命令，回滚中断"; return 1; }
    ( cd "$BASE_DIR" && $cmd restart backend ) >>"$LOG_FILE" 2>&1 || true
    log WARN "后端已回滚到上一版本"
  else
    log ERROR "没有 app.jar.prev，无法回滚后端"
  fi
}

# ---------- 健康检查 ----------
wait_healthy() {
  local deadline=$(( $(date +%s) + HEALTH_TIMEOUT ))
  log INFO "健康检查: $HEALTH_URL（最多 ${HEALTH_TIMEOUT}s）"
  while [[ $(date +%s) -lt $deadline ]]; do
    local code
    code="$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 5 "$HEALTH_URL" 2>/dev/null || true)"
    code="${code:-000}"
    if [[ "$code" == "200" ]]; then
      log INFO "健康检查通过"
      return 0
    fi
    sleep "$HEALTH_INTERVAL"
  done
  log ERROR "健康检查超时（${HEALTH_TIMEOUT}s 内未返回 200）"
  return 1
}

# ---------- 清理旧产物 ----------
cleanup_old_releases() {
  cd "$TMP_DIR" 2>/dev/null || return 0
  local dirs
  dirs="$(ls -1dt */ 2>/dev/null | tail -n +$((KEEP_RELEASES + 1)) || true)"
  for d in $dirs; do
    rm -rf "./$d"
  done
  return 0
}

# =============== 主流程 ===============
main() {
  log INFO "===== 自动部署开始 (mode=$MODE) ====="
  check_deps || exit 1
  acquire_lock

  fetch_manifest || exit 1

  local version tag dist_name jar_name dist_sha jar_sha dist_url jar_url
  version="$(manifest_value version)"
  tag="$(manifest_value tag)"
  dist_name="$(manifest_value dist)"
  jar_name="$(manifest_value jar)"
  dist_sha="$(manifest_value dist_sha256)"
  jar_sha="$(manifest_value jar_sha256)"
  dist_url="$(manifest_value dist_url)"
  jar_url="$(manifest_value jar_url)"

  # 资产名拼 URL；清单若直接给了 *_url 则优先用它
  if [[ -z "$dist_url" && -n "$dist_name" ]]; then
    dist_url="https://gitee.com/${REPO_OWNER}/${REPO_NAME}/releases/download/${tag}/${dist_name}"
  fi
  if [[ -z "$jar_url" && -n "$jar_name" ]]; then
    jar_url="https://gitee.com/${REPO_OWNER}/${REPO_NAME}/releases/download/${tag}/${jar_name}"
  fi

  if [[ -z "$version" || -z "$tag" ]]; then
    log ERROR "版本清单缺少 version/tag 字段，放弃部署"
    exit 1
  fi

  local current=""
  if [[ -f "$VERSION_FILE" ]]; then
    current="$(tr -d '\r\n ' < "$VERSION_FILE" 2>/dev/null || true)"
  fi

  log INFO "线上版本: ${current:-无}   待部署版本: $version"

  if [[ "$MODE" == "check" ]]; then
    if [[ "$current" == "$version" ]]; then
      log INFO "已是最新版本，无需部署"
    else
      log INFO "有新版本可部署（--check 模式不执行）"
    fi
    exit 0
  fi

  if [[ "$MODE" != "force" && "$current" == "$version" ]]; then
    log INFO "版本未变化，跳过"
    exit 0
  fi

  local rel_dir="$TMP_DIR/$version"
  rm -rf "$rel_dir"
  mkdir -p "$rel_dir"

  local dist_updated=0 jar_updated=0

  # --- 前端 ---
  if [[ -n "$dist_url" ]]; then
    local dist_asset="${dist_name:-$(basename "${dist_url%%\?*}")}"
    if ! download_asset "$dist_url" "$rel_dir/$dist_asset" "前端产物"; then
      log ERROR "前端产物下载失败，终止"
      exit 1
    fi
    if ! verify_sha256 "$rel_dir/$dist_asset" "$dist_sha" "前端产物"; then
      log ERROR "前端产物校验失败，终止"
      exit 1
    fi
    if ! apply_dist "$rel_dir/$dist_asset"; then
      log ERROR "前端切换失败"
      rollback_dist
      exit 1
    fi
    dist_updated=1
  fi

  # --- 后端 ---
  if [[ -n "$jar_url" ]]; then
    local jar_asset="${jar_name:-$(basename "${jar_url%%\?*}")}"
    if ! download_asset "$jar_url" "$rel_dir/$jar_asset" "后端产物"; then
      log ERROR "后端产物下载失败，终止"
      exit 1
    fi
    if ! verify_sha256 "$rel_dir/$jar_asset" "$jar_sha" "后端产物"; then
      log ERROR "后端产物校验失败，终止"
      exit 1
    fi
    if ! apply_jar "$rel_dir/$jar_asset"; then
      log ERROR "后端切换失败"
      rollback_jar
      exit 1
    fi
    if ! wait_healthy; then
      log ERROR "新版本健康检查失败，自动回滚"
      rollback_jar
      [[ $dist_updated -eq 1 ]] && rollback_dist
      exit 1
    fi
    jar_updated=1
  fi

  # 只更新了前端也要确认站点可访问
  if [[ $dist_updated -eq 1 && $jar_updated -eq 0 ]]; then
    if ! wait_healthy; then
      log WARN "站点健康检查未通过（未改动后端，不触发回滚）"
    fi
  fi

  printf '%s' "$version" > "$VERSION_FILE"
  cleanup_old_releases

  log INFO "===== 部署完成，版本 $version ====="
}

main "$@"
