# VoxCoach Sync Server（docs/05 落地）

单用户（`DATA_OWNER`）Go + SQLite（WAL）同步服务，实现 `docs/05` §4.1（设备鉴权）、§4.2–4.3（增量同步 + LWW）、§4.5（只读统计）、§6（可选 LLM 代理）。

> 驱动用 `modernc.org/sqlite`（纯 Go、无 cgo），可 `GOOS=linux GOARCH=amd64` 直接交叉编译出单二进制。首次构建前执行 `go mod tidy` 生成 `go.sum`。

## 本地验证 / 构建

```bash
go mod tidy          # 生成 go.sum（首次）
go vet ./...
go test ./...        # 含端到端：注册→push→pull→LWW 冲突→墓碑→统计→吊销
GOOS=linux GOARCH=amd64 go build -o voxcoach-server .
```

## 契约对齐（勿漂移）

- 路径/头常量与客户端 `core/domain/model/SyncContract.kt` 一致：
  `POST /api/v1/sync/push`、`GET /api/v1/sync/pull`、`GET /api/v1/health`、
  `POST /api/v1/auth/register-device`、`POST /api/v1/auth/revoke-device`，鉴权头 `X-Device-Key`。
- change JSON：`{entity, op, id, updatedAt, deviceId, data}`；`updatedAt` 同时接受 RFC3339 UTC 字符串与 epoch 毫秒；pull 返回 RFC3339。
- 白名单实体：`session / ev_result / mistake / vocab_note / profile / grammar_progress`；`turns` 与录音**永不**入库。
- LWW：`updatedAt` 新者胜，平局 `deviceId` 字典序大者胜；`DELETE` 为墓碑，可被更新的 UPSERT 覆盖。
- 错误码（§9）：`401 invalid_device_key`、`403 model_not_allowed`、`413 payload_too_large`、`429 rate_limited`、`500 internal`；扩展 `400 invalid_change`（实体白名单/缺 data/坏 updatedAt）。

## 部署（docs/05 §8）

目标布局：

```
/opt/voxcoach/
├── voxcoach-server        # Go 单二进制
├── db.sqlite              # 数据文件（WAL）
├── .env                   # 环境变量
└── backups/               # 每日备份
```

`.env` 变量：

| 变量 | 默认 | 说明 |
|---|---|---|
| `PORT` | `8080` | 监听端口（反代 Caddy/Nginx → 127.0.0.1:8080） |
| `DB_PATH` | `db.sqlite` | SQLite 文件路径 |
| `DATA_OWNER` | `owner` | 单用户命名空间（export 元数据） |
| `LOG_LEVEL` | `info` | 日志级别 |
| `LLM_PROXY_ENABLED` | `false` | 模式 B 开关（默认关） |
| `LLM_UPSTREAM_BASE_URL` | — | 模式 B 上游（如 `https://api.xiaomimimo.com/v1`） |
| `LLM_UPSTREAM_KEY` | — | 模式 B 上游 Key，**只在服务端，绝不返回客户端** |
| `LLM_ALLOWED_MODELS` | `mimo-v2.5` | 代理模型白名单（逗号分隔） |
| `RATE_LIMIT_PER_MIN` | `20` | 代理按设备限流 |
| `MAX_BODY_BYTES` | `65536` | 单变更/代理请求体上限 |
| `PULL_LIMIT` | `1000` | 单次 pull 上限 |

systemd 要点：`After=network-online.target`、`Restart=on-failure`、`EnvironmentFile=/opt/voxcoach/.env`、非 root 运行。

备份：cron 每日 `sqlite3 db.sqlite ".backup backups/db-$(date +%F).sqlite"`，保留 14 份。

## 客户端接入（Android）

1. 设置页填服务器 Base URL（`https://<域名>`，前缀 `/api/v1` 由代码拼接）。
2. 点「注册设备」→ `register-device` 返回 `deviceId/deviceKey`；Key 经 Android Keystore 封装落盘（`SecureKeys.SYNC_DEVICE_KEY`）。
3. 开启同步开关 → `SyncEngine.runSync()`：push 本地脏数据 → pull `seq > cursor` → `SyncResolver.resolve` LWW 合并 → 更新 cursor。
4. 换机：新设备注册后首次 `pull?full=1` 全量拉取。

## 已知边界

- `vocab_note` / `grammar_progress` 实体契约与镜像表已就绪；客户端 Room 表（VB-04 语料收藏）未建，暂不参与推送。
- 拉取游标只以 **pull 响应的 `cursor`** 推进；push 响应 `newCursor` 仅作参考（避免跳过其他设备的并发变更）。
