# Box 独立代码审查报告（第二轮 · 含自审）

> 审查对象：`dororo42/dorobox` @ `145c097`（takagen99/Box 血统 + 本轮 P0/P1/P2 加固批次）
> 审查日期：2026-09-30 ｜ 方式：静态审查（安全/稳定性/性能/可维护性/回归五路）
> 特别说明：本轮为**不受既有实现思路影响**的独立复审，**包含对前几批加固改动自身的批判性核查**，并确实发现了 3 个由加固批次引入的严重回归。

---

## 一、风险矩阵

| # | 严重度 | 领域 | 问题 | 位置 |
|---|---|---|---|---|
| C-1 | 🔴 | 稳定 | **DbIo 重入死锁**：单线程 DB executor 内的任务若再经 `DbIo.run` 提交并阻塞等待（如 `fetchVodInfo`→`getVodInfo`），内层任务永远无法调度 → DB 线程永久挂死，后续所有 DB 操作全部超时失败。当前 `fetchVodInfo` 尚无调用方，属"埋雷 API" | `data/DbIo.java:44-72`、`cache/RoomDataManger.java` fetchVodInfo |
| C-2 | 🔴 | 功能 | **clan:// 配置链路断裂（加固批次回归）**：`clanToAddress` 将 `clan://localhost/xxx` 转为 `http://<LAN-IP>:9978/file/xxx` 后经 OkHttp 拉取；P0-1b 给 `/file` 加的 token 鉴权**连本机自身的拉取也一并拦截** → 存量 clan:// 配置的 ext/jar/资源加载全部 403。威胁模型是"局域网其它设备"，设备自身的请求不应被拦 | `api/ApiConfig.java:857-865`、`server/RemoteServer.java` isProtected |
| C-3 | 🔴 | 功能 | **内置 Web 远控 UI 全面失效（加固批次回归）**：`script.js` 的 `/file`、`/action(api/live/epg/proxys/push)`、`/upload`、`/newFolder`、`/delFolder`、`/delFile` 请求全部未携带 token → 手机浏览器打开 `http://box:9978/` 后所有功能 403。设置页"复制 token"无法帮助 web UI（页面无从得知 token） | `res/raw/script.js:28,110,182,206,223,239,256` |
| H-1 | 🟡 | 稳定 | **backup/restore 与 DbIo 队列竞态**：`backup()` 先 `close()` dbInstance 再拷贝，此刻 DbIo 队列中可能在飞写任务持有旧连接 → 异常被吞/备份不完整。应把 close+copy 放进同一 DB 串行队列 | `data/AppDataManager.java:108-119`、`ui/dialog/BackupDialog.java:133,190` |
| H-2 | 🟡 | 兼容 | **/api/updateUrl token 化破坏存量推送 App 生态**：第三方"投屏/推送"App 无法得知 token，推送功能失效。属有意安全变更，但缺少用户侧迁移说明（当前仅设置页可查看 token） | `server/WebController.kt:20-31` |
| H-3 | 🟡 | UX | **XWalk 设置开关与运行时行为不一致**：设置页仍可把嗅探内核切到"XWalkView"，但运行时已硬编码强制系统 WebView——UI 谎报状态 | `ui/fragment/ModelSettingFragment.java:511-513` |
| H-4 | 🟡 | 稳定 | **搜索超时的 cancel 无法中断非协作式爬虫**：`future.cancel(true)` 只发中断信号，QuickJS/死循环网络调用不一定响应 → 4 个 worker 可能被死源占满，后续搜索排队（有 15s 超时兜底，属已知限界，标注不修） | `viewmodel/SourceViewModel.java` spiderSearchPool |
| L-1 | 🟢 | 兼容 | `/dns-query` token 化后，把盒子当 LAN DoH 网关的客户端用法失效（影响面极小） | `server/RemoteServer.java` |
| L-2 | 🟢 | 结构 | `ConfirmClearDialog` 仍直接操作 `CollectActivity/HistoryActivity` 静态 adapter（跨 Activity 强耦合，浅改有风险，标注不修） | `ui/dialog/ConfirmClearDialog.java:38-45` |
| L-3 | 🟢 | 性能 | `LiveEpgAdapter.convert` 每条绑定时 `new Date()`（EPG 数百条滚动时 GC 压力，微优化） | `ui/adapter/LiveEpgAdapter.java:37` |
| L-4 | 🟢 | 维护 | 依赖版本散落在 build.gradle 字面量，无 version catalog / BOM 集中管理（长期维护成本） | `app/build.gradle` |

## 二、正面结论（保持项）

- 🎉 CI 建立（GitHub Actions，debug 双 ABI + release R8 校验），编译回归即时暴露——本轮 C-1/C-2/C-3 中的 C-3 正是靠 CI 抓出第一层（R.id 缺失），其余两项静态审查补充。
- 🎉 DbIo 统一数据库 IO 门面方向正确：主线程零直接 SQLite IO，调用方零改动。
- 🎉 jar fail-closed 校验、zip-slip 防护、canonical path 校验、CrashHandler 实现正确。
- 🎉 搜索 15s 超时 + 4 并发限流、EPG 精确刷新、media3 1.4.1 编译层验证通过。

## 三、修复计划（按优先级）

1. 🔴 C-1：DbIo 增加重入检测（executor 线程内直接执行）+ 非主线程 30s 超时兜底。
2. 🔴 C-2：RemoteServer 增加"本机自请求"识别（remote IP ∈ {loopback, 本机 LAN IP}）→ GET `/file`、`/dns-query` 豁免 token；写/删端点仍强制 token。恢复 clan:// 链路，不放松对局域网其它设备的防护。
3. 🔴 C-3：`script.js` 增加 token 支持：localStorage 持久化、所有危险请求自动附加 `token` 参数、403 时提示输入。
4. 🟡 H-1：`backup/restore` 全程放入 DbIo 串行队列执行。
5. 🟡 H-3：XWalk 开关点击时提示"已停用"，文案固定"系统自带"。
6. 🟡 H-2 / 🟢 L-1：不做代码改动，在 handoff 与指南中记录行为变化（生态兼容属产品决策）。

## 四、结论

💬 **继续可用，但须先合入 C-1/C-2/C-3 三个回归修复**。三者均由加固批次引入且都属"安全机制误伤合法链路/自身功能"类问题——教训：token 鉴权设计时必须先盘点全部合法调用方（含本机内部拉取与自带 Web UI），CI 只能抓编译层，行为回归需真机/集成覆盖。

## 五、修复实施记录（同日完成）

| # | 状态 | 实施 |
|---|---|---|
| C-1 | ✅ | DbIo 增加重入检测（DB 线程内直接执行）+ 非主线程 30s 超时兜底 |
| C-2 | ✅ | RemoteServer 新增 `isSelfRequest`（loopback / 本机 LAN IP），GET `/file`、`/dns-query` 对本机豁免；写/删端点仍强制 token |
| C-3 | ✅ | script.js 全部危险请求附加 token；localStorage 持久化；403 时引导输入令牌并重试 |
| H-1 | ✅ | backup/restore 的 close+copy 全程放入 DbIo 串行队列 |
| H-3 | ✅ | XWalk 开关点击仅提示"已停用"，文案固定"系统自带" |
| H-2/L-1 | 📝 | 记录于本文档与 handoff，属产品决策不做代码改动 |

*行号对应 commit `145c097`；关键结论已逐条对照源码复核。*
