# Handoff: dorobox 独立审查修复 + 包名独立 + keystore 入 Secrets

## 元数据

- Created: 2026-10-05
- Source agent: CodeBuddy (GLM-5.3-Flash)
- Project: 本地副本 /home/doro/Box，远程 origin = dororo42/dorobox
- Branch: main @ 3425cf086
- Supersedes: handoffs/2026-10-04-cleanup-token-qr/HANDOFF.md
- 审查输入：/home/doro/reports/dorobox-independent-review-report.md 与 /home/doro/reports/dorobox_独立审查报告_v1.md（两份独立报告，已合并实施）

## 当前状态摘要

2026-10-04/05 会话按两份独立审查报告完成 5×Major + D 系 8 项 + m/nit 系 10+ 项修复，单提交 `8436f488`；随后修了两个二次回归（`cb30f57c7` 弹窗取消行为、`3425cf086` EventBus 启动崩溃）。CI 全绿。**包名已改为 com.dorobox.tvbox（.hisense 变体）**，keystore 移出仓库改走 GitHub Secret（签名不变）。

### 两报告修复清单（commit 8436f488）

- D-1 自毁链：`/delFolder`/`/delFile` 新增 `isRoot()` 拒绝目标==外部存储根（空 path 曾可递归删整个存储）；根豁免仅限 GET /file 列表；写删端点前置空参 400
- D-2 PlayFragment `onReceivedSslError` → cancel（确认上次"补漏"实际漏改）
- D-3 ApiConfig 裸地址分支拼 `apiUrl`（原拼空串 configUrl）
- D-5 QuickJS `setMemoryLimit(64MB)` + `setMaxStackSize(1MB)`（JsSpider.createCtx）
- D-6 `ensureInit` synchronized（check-then-act 竞态）
- D-7 jar/jsapi 缓存原子写（tmp → zip magic `PK` 校验 → rename；HTTP 非 2xx 不落盘）JarLoader/JsLoader 双侧
- D-8a proxyLocal 空源返回空响应（原 NPE）
- M-2 /action 参数校验缺失返回 400 JSON
- M-3 `do=mirror` 纳入 token 鉴权（原遗漏，能力等同 push）
- M-4/N-3 文件操作 catch 返回 500+原因（原伪报 "OK"）
- M-1 DB：`dbInstance` volatile、backup/restore close 后置 null 由 get() 重建；restore 原子化（tmp → 16 字节 SQLite 头校验 → rename；失败保留原库；删 -journal/-wal/-shm sidecar）
- m-1 `/api/updateUrl` 鉴权失败抛 `BasicException(403)`（原 200+"forbidden"）
- m-2/N-1 token 恒定时间比较（MessageDigest.isEqual）
- m-4/N-13 删除每请求 SERVER_CONNECTION EventBus 事件 + UserFragment 空订阅
- N-8 jar Init 线程 join 3s 超时；N-2 unzip try-with-resources + 512MB zip-bomb 上限；nit：isStarted 成功后置位

### 二次回归教训（重要）

1. `cb30f57c7`：空源 TipDialog"取消"原为循环 initData（空源下反复弹窗/退出），改为跳转 SettingActivity。
2. `3425cf086`：**m-4 清理删掉了 UserFragment 唯一的 @Subscribe 方法但保留 register() → EventBusException 启动即崩**。教训：EventBus 类删订阅方法必须同步删 register/unregister。该修复已 CI 绿但**未装机**（见下）。

### 信任根变更

- **包名 com.dorobox.tvbox**（hisense：com.dorobox.tvbox.hisense）；manifest 源码包名仍为 com.github.tvbox.osc（未重构）；FileProvider 用 ${applicationId} 占位符无需改
- **debug.keystore 已 git rm --cached 并 gitignore**；内容 base64 后存于 GitHub Secret `BOX_DEBUG_KEYSTORE_B64`（gh secret set，签名不变）；app/build.gradle 解析顺序：env → 本地 rootProject/debug.keystore（开发者机保留）→ 默认签名
- build.yml 两个 gradle 步骤已注入该 env

## 设备状态（2026-10-05 00:0x 关机前）

- 真机 192.168.2.230:5555（p230，Android 7.1.2 / arm64 / wlan0）已关闭
- 设备上现存两个应用：旧 `com.github.tvbox.osc.tk`（含用户配置，可卸载）与 **`com.dorobox.tvbox`（build 37213925353 = cb30f57c7，含配置地址 clun.top/box.json + 直播 iptv.m3u + SOCKS 192.168.2.153:16491 + 免鉴权开启，token=2dbcac1ee68488a908c1df24f596242）**
- ⚠️ **设备上的 com.dorobox.tvbox 版本早于 3425cf086，仍含 EventBus 启动崩溃**——下次真机开机后第一件事：`gh run download 37214729271 -R dororo42/dorobox -n apk` 装 arm64 debug 包覆盖安装（签名同、数据保留）
- 用户已确认的源行为（非 App bug，勿再排查）：荐片[js] 图床 static.ztcuc.com 全球 NXDOMAIN（封面缺失）；部分线路直连被 reset（SOCKS 已配，EXO 失败自动走代理重试）
- 设置页"确定"只保存地址不触发加载（takagen99 原版设计），需重启 App 才加载

## 待办工作

1. **真机开机后**：装 37214729271 包覆盖 → 验证启动不再崩 → 重配源（若数据丢失）→ 冒烟：搜索不闪退、首页电影/电视剧 tag、取消按钮进设置页
2. **记录类**（报告建议不修/待定）：D-4 exported receiver（需查生态广播依赖）、D-9 allowBackup、m-3 设备内侧信道（targetSdk 29+ 时重审）、m-5 DbIo 平台限界、m-7 免鉴权二次确认弹窗、m-8 /m3u8 隐私面、git 历史 105MB zip（filter-repo 需拍板）、M-5 权限收敛、Hawk 热点缓存、version catalog
3. m-7 顺带：免鉴权开启建议改 AlertDialog 二次确认；script.js delFolder/doDelFolder 重复定义两份（N-10）未去重

## 环境 / 工作流（沿用 + 新增）

- 构建验证走 GitHub Actions；`gh run list` 本机不可用，用 `gh api repos/dororo42/dorobox/actions/runs`；`gh run download` 必须 `-R dororo42/dorobox`
- adb 已装（apt）；真机 `adb connect 192.168.2.230:5555`（开机后才可连）；盒子无 root，debug 包可 run-as；**uiautomator dump 与 screencap-to-file 在该盒子不可用，用 `adb exec-out screencap -p`**；盲点导航用 `input tap` 需按 1920x1080 实际坐标（截图显示 1080 宽需 ×1.78 换算）
- 鉴权速测：`curl -X POST http://<box>:9978/action -d "do=api&url=..."` 无 token 应 403（免鉴权开则 200）
- HMAC： Hawk 加密（crypto.KEY_256），token 无法离线读取；UI 取 token 走设置页二维码弹窗

## 沿用注意事项

- 布局 res/layout 与 res/layout-v21 双份都要改（对话框布局无 v21 变体）
- script.js 有预置重复函数定义（delFolder/doDelFolder 各两份），改动需两处覆盖（N-10 未去重）
- RemoteServer"括号不配平"是审查脚本误报，勿修
- EventBus：删订阅方法必须同步删 register/unregister（本轮血的教训）
