#!/usr/bin/env bash
# =============================================
# 众乐电子商城 - 发布脚本（本地执行，产出 Gitee Release）
#
# 与服务器端 deploy/update.sh 配对：
#   本地：构建 → 打包 → 写版本清单 → 推 git → 创建 Gitee Release 并上传产物
#   服务器：宝塔计划任务每 10 分钟拉清单 → 有新版本就下载并原子切换
#
# 用法（在仓库根目录执行，Git Bash / Linux 均可）：
#   bash deploy/release.sh                    # 全量发布
#   bash deploy/release.sh --skip-backend     # 只发前端
#   bash deploy/release.sh --skip-frontend    # 只发后端
#   bash deploy/release.sh --no-push          # 只生成产物与清单，不推 git、不建 Release
#   bash deploy/release.sh --dry-run          # 只打印流程，不实际执行
#
# 依赖：
#   - GITEE_TOKEN 环境变量（Gitee 私人令牌，需要 projects 权限）。
#     Windows(Git Bash)：export GITEE_TOKEN=xxxx
#     PowerShell：$env:GITEE_TOKEN='xxxx'
#   - Gitee 仓库 liao-zhenhan/happy-mart 必须是公开的（服务器拉 Release 资产免鉴权）
# =============================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

REPO_OWNER="${REPO_OWNER:-liao-zhenhan}"
REPO_NAME="${REPO_NAME:-happy-mart}"
REPO_BRANCH="${REPO_BRANCH:-master}"
GITEE_TOKEN="${GITEE_TOKEN:-}"

VERSION=""
SKIP_BACKEND=0
SKIP_FRONTEND=0
NO_PUSH=0
DRY_RUN=0
OUT_DIR="$REPO_ROOT/deploy/.release"

for arg in "$@"; do
  case "$arg" in
    --version) VERSION="${2:-}"; shift ;;
    --skip-backend) SKIP_BACKEND=1 ;;
    --skip-frontend) SKIP_FRONTEND=1 ;;
    --no-push) NO_PUSH=1 ;;
    --dry-run) DRY_RUN=1 ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

log() { echo "[$(date '+%H:%M:%S')] $*"; }
die() { echo "[$(date '+%H:%M:%S')] ERROR $*" >&2; exit 1; }

[[ -n "$VERSION" ]] || VERSION="$(date +%Y%m%d-%H%M)"
TAG="v${VERSION}"

# ---------- 前置检查 ----------
check_tools() {
  local missing=()
  command -v node >/dev/null 2>&1 || missing+=("node")
  command -v npm >/dev/null 2>&1 || missing+=("npm")
  command -v java >/dev/null 2>&1 || missing+=("java")
  if ! command -v zip >/dev/null 2>&1 && ! command -v jar >/dev/null 2>&1; then
    missing+=("zip|jar")
  fi
  [[ ${#missing[@]} -eq 0 ]] || die "缺少工具: ${missing[*]}"
}

# ---------- 打包前端 ----------
pack_dist() {
  local dist_dir="$REPO_ROOT/happy-mart-web/dist"
  local zip_file="$OUT_DIR/dist-${VERSION}.zip"
  [[ -d "$dist_dir" ]] || die "前端产物目录不存在: $dist_dir"

  log "打包前端产物 → $(basename "$zip_file")"
  rm -f "$zip_file"
  if command -v zip >/dev/null 2>&1; then
    # -r 递归，路径以 . 开头 → zip 内顶层直接是 index.html / assets/，不套 dist 外层
    ( cd "$dist_dir" && zip -qr "$zip_file" . )
  else
    # Git Bash 常常没有 zip，退回 JDK 自带的 jar（M = 不写 MANIFEST）
    jar --create --file "$zip_file" --no-manifest -C "$dist_dir" .
  fi
  [[ -f "$zip_file" ]] || die "前端打包失败"
  # 结构自检：zip 顶层必须有 index.html
  if command -v unzip >/dev/null 2>&1; then
    unzip -l "$zip_file" | grep -q "index.html" || die "zip 里没有 index.html，产物结构不对"
  fi
}

# ---------- 构建前端 ----------
build_frontend() {
  log "构建前端 (npm run build)"
  ( cd "$REPO_ROOT/happy-mart-web" && npm run build ) || die "前端构建失败"
}

# ---------- 构建后端 ----------
build_backend() {
  log "构建后端 (mvnw clean package -DskipTests)"
  ( cd "$REPO_ROOT/happy_mart" && ./mvnw -q clean package -DskipTests ) || die "后端构建失败"
  local jar
  jar="$(find "$REPO_ROOT/happy_mart/target" -maxdepth 1 -name '*.jar' ! -name '*.original' ! -name '*-sources.jar' | head -n 1)"
  [[ -n "$jar" ]] || die "在 happy_mart/target 下没找到 jar"
  cp -f "$jar" "$OUT_DIR/app-${VERSION}.jar"
}

# ---------- 写版本清单 ----------
write_manifest() {
  local file="$REPO_ROOT/deploy/manifest.txt"
  local dist_name="" dist_sha="" jar_name="" jar_sha=""
  if [[ -f "$OUT_DIR/dist-${VERSION}.zip" ]]; then
    dist_name="dist-${VERSION}.zip"
    dist_sha="$(sha256sum "$OUT_DIR/$dist_name" | cut -d' ' -f1)"
  fi
  if [[ -f "$OUT_DIR/app-${VERSION}.jar" ]]; then
    jar_name="app-${VERSION}.jar"
    jar_sha="$(sha256sum "$OUT_DIR/$jar_name" | cut -d' ' -f1)"
  fi

  cat > "$file" <<EOF
# 众乐电子商城 - 版本清单
# 由 deploy/release.sh 自动生成并提交，勿手工编辑。
# 服务器端 deploy/update.sh 按此文件拉取产物并原子切换。
version=${VERSION}
tag=${TAG}
dist=${dist_name}
dist_sha256=${dist_sha}
jar=${jar_name}
jar_sha256=${jar_sha}
EOF
  log "版本清单已写入 deploy/manifest.txt"
  log "  版本 $VERSION / $TAG"
  [[ -n "$dist_name" ]] && log "  前端 $dist_name  sha256=${dist_sha:0:16}…"
  [[ -n "$jar_name" ]] && log "  后端 $jar_name  sha256=${jar_sha:0:16}…"
  return 0
}

# ---------- Gitee Release ----------
create_release() {
  [[ -n "$GITEE_TOKEN" ]] || die "缺少 GITEE_TOKEN 环境变量（创建 Release 需要）"

  log "创建 Gitee Release $TAG"
  local body
  body=$(printf '{"tag_name":"%s","name":"%s","body":"自动发布 %s","prerelease":false}' "$TAG" "$TAG" "$VERSION")
  local resp
  resp="$(curl -fsSL -X POST \
    "https://gitee.com/api/v5/repos/${REPO_OWNER}/${REPO_NAME}/releases?access_token=${GITEE_TOKEN}" \
    -H "Content-Type: application/json" -d "$body")" || die "创建 Release 失败"

  local rel_id
  rel_id="$(printf '%s' "$resp" | grep -o '"id":[0-9]*' | head -n 1 | cut -d: -f2)"
  [[ -n "$rel_id" ]] || die "解析 Release id 失败: $resp"
  log "Release 创建成功 id=$rel_id"

  local f
  for f in "$OUT_DIR"/*; do
    [[ -f "$f" ]] || continue
    log "上传资产 $(basename "$f")"
    curl -fsSL -X POST \
      "https://gitee.com/api/v5/repos/${REPO_OWNER}/${REPO_NAME}/releases/${rel_id}/attach_files?access_token=${GITEE_TOKEN}" \
      -F "file=@${f}" >/dev/null || die "上传失败: $(basename "$f")"
  done
  log "资产上传完成"
}

# =============== 主流程 ===============
main() {
  check_tools
  rm -rf "$OUT_DIR"
  mkdir -p "$OUT_DIR"

  log "===== 发布开始 version=$VERSION tag=$TAG ====="

  if [[ $DRY_RUN -eq 1 ]]; then
    log "[dry-run] 将执行：构建前端 → 构建后端 → 打包 → 写清单 → 推 git → 建 Release"
    exit 0
  fi

  if [[ $SKIP_FRONTEND -eq 0 ]]; then
    build_frontend
    pack_dist
  fi
  if [[ $SKIP_BACKEND -eq 0 ]]; then
    build_backend
  fi

  write_manifest

  if [[ $NO_PUSH -eq 1 ]]; then
    log "--no-push：跳过 git 推送与 Release 创建"
    log "产物目录：$OUT_DIR"
    exit 0
  fi

  log "提交版本清单到 $REPO_BRANCH"
  git add deploy/manifest.txt
  git -c user.name="${GIT_AUTHOR_NAME:-ATRI}" -c user.email="${GIT_AUTHOR_EMAIL:-atri@local}" \
    commit -m "chore(release): $TAG" || log WARN "无改动可提交（清单与上次相同）"
  git push origin "$REPO_BRANCH"

  create_release

  log "===== 发布完成 ====="
  log "服务器端最多 10 分钟内自动部署（宝塔计划任务轮询）"
  log "查看进度：宝塔面板 → 计划任务 → 执行日志，或 tail /www/wwwlogs/hm-deploy.log"
}

main
