# Handoff: Box P0/P1 安全与稳定性加固（按 BOX_CODE_REVIEW_2026-09-30.md 执行）

## 元数据

- Created: 2026-09-30 21:20:42
- Source agent: CodeBuddy (GLM-5.3-Flash)
- Target agent: unknown（三档全配：S/R/P）
- Target mode: all
- Project: https://github.com/takagen99/Box.git（本地副本 /home/doro/Box）
- Branch: main
- HEAD: a8dfc43（P0 加固 + handoff 套件已提交；工作树另有 P1 改动未提交）
- OS: linux

### 交接链

- **Continues from**: None（首份交接）
- **Supersedes**: None

> 接手方：从本份读起。审查报告原件：`/home/doro/reports/BOX_CODE_REVIEW_2026-09-30.md`（P0/P1/P2 优先级清单的权威来源）。

## 当前状态摘要

按 2026-09-30 的 Box 独立代码审查报告，**P0 三项已全部完成并提交**（commit `a8dfc43`，含 handoff 套件与 AGENTS.md）。随后完成 **P1 稳定性第 4–7 项的大部分**（见"已完成工作"），暂未提交。**编译验证始终被环境阻塞**（本机无 Android SDK platforms，JDK 25 与 AGP 7.4.2 不兼容），仅做了静态核查（IDE 诊断零告警）——接手方第一件事就是在有 SDK 的机器上跑 `assembleDebug`，把 P0+P1 一起验证。

## 最近提交（上下文参考）

- 258a5fe 系统播放器增加音轨选择（= 审查报告基线 commit）
- 5d7e7db 详情页倒序按钮：点击后文字在「倒序」与「正序」间切换
- 33917ba 逆序时上一集/下一集与自动播放下一条按当前列表顺序切换
- 34ec7da 弹幕
- 13d4bfd feat(kodi): 支持新版Kodi入口Activity (#181)

## 未提交修改

| 文件 | 改动说明 | 原因 |
|---|---|---|
| `app/src/main/java/com/github/catvod/net/SSLCompat.java` | 重写：构造器私有化，`create()` 走系统信任库，`createTrustAll()`/`TRUST_ALL_VERIFIER` 仅为按源 opt-in；`TM` 改为系统 TrustManagerFactory 委托校验；不再调用 `HttpsURLConnection.setDefaultSSLSocketFactory` 全局安装 | C-1：进程级 trust-all TLS |
| `app/src/main/java/com/github/catvod/net/OkHttp.java` | 两处 `new SSLCompat()` → `SSLCompat.create()` | 适配私有构造器 + 安全默认 |
| `app/src/main/java/com/github/tvbox/osc/util/OkGoHelper.java` | `setOkHttpSsl` 用 `SSLCompat.create()` + `SSLCompat.VERIFIER`（现为系统校验）；删 `HttpsUtils` import | C-1 |
| `app/src/main/java/com/github/tvbox/osc/server/ServerToken.java` | **新建**：本地服务 token，Hawk 持久化 UUID，`verify()` 静态方法 | C-2 鉴权基础设施 |
| `app/src/main/java/com/github/tvbox/osc/server/RemoteServer.java` | serve() 入口对 `/file`、`/dns-query`、`/upload`、`/newFolder`、`/delFolder`、`/delFile` 做 token 鉴权（`?token=` 或 `X-Token` 头）；上述端点 + 目标路径全部做 canonical path 包含校验；`unzip()` 加 zip-slip 防护 | C-2：任意读/写/删、zip-slip、开放 DoH |
| `app/src/main/java/com/github/tvbox/osc/server/InputRequestProcess.java` | `/action` 的 `api/live/epg/proxys/push` 五个动作要求 token | C-2：远程劫持配置 URL |
| `app/src/main/java/com/github/tvbox/osc/server/WebController.kt` | `/api/updateUrl` 增加 `token` 参数校验 | C-2（AndServer:12345 侧） |
| `app/src/main/java/com/github/catvod/crawler/JarLoader.java` | 下载后：md5 声明了但匹配失败 → 删缓存拒载；无 md5 且严格模式开 → 拒载 | C-3：jar 零校验即执行 |
| `app/src/main/java/com/github/catvod/crawler/JsLoader.java` | 同 JarLoader | C-3 |
| `app/src/main/java/com/github/tvbox/osc/util/HawkConfig.java` | 新增 `JAR_VERIFY_STRICT`（默认 false，见决策记录） | C-3 严格模式开关 |
| `app/src/main/java/com/github/tvbox/osc/util/CrashHandler.java` | **新建**：UncaughtExceptionHandler，堆栈+设备信息写 `files/crash/crash_*.log`，保留最近 10 份，链回默认处理器 | H-4：无崩溃处理器 |
| `app/src/main/java/com/github/tvbox/osc/base/App.java` | `onCreate` 最前安装 CrashHandler | H-4 |
| `app/src/main/java/com/github/tvbox/osc/ui/activity/PlayActivity.java` | 系统/XWalk WebView 均 `setAllowFileAccess(false)` + 关 universal/file access；`onReceivedSslError` 改 cancel（XWalk 回调 false） | H-1 |
| `app/src/main/java/com/github/tvbox/osc/ui/fragment/PlayFragment.java` | `loadWebView` 强制 `useSystemWebView = true` | H-1/P0-3：停用 Crosswalk |
| `app/src/main/java/com/github/tvbox/osc/ui/activity/PlayActivity.java` | 同上（PlayActivity 的 `loadWebView`） | 同上 |
| `pyramid/src/python/app.py` | `requests.get(verify=False)` → `verify=True` | H-3 |

## 架构与关键文件

### 架构概览

TVBox 血统安卓应用：`ApiConfig` 加载配置（可加密/clan://）→ 站点三类爬虫（Java jar `csp_*` 走 `JarLoader`/DexClassLoader；JS 走 `JsLoader`/QuickJS；Python 仅 python flavor 走 pyramid）。本地双服务：`RemoteServer`(NanoHTTPD:9978，含 Web 文件管理/推送/代理) + AndServer:12345（推送 URL）。播放内核四选一（系统/IJK/EXO/阿里云，dkplayer 框架）。网络栈：OkGo + OkHttp（`com/github/catvod/net/`），ExoPlayer media3 1.3.1。

### 关键文件

| 文件 | 作用 | 对当前任务为何重要 |
|---|---|---|
| `app/src/main/java/com/github/catvod/net/SSLCompat.java` | TLS 兼容工厂 | P0-1① 的核心；老设备 TLS1.2 启用逻辑必须保留 |
| `app/src/main/java/com/github/tvbox/osc/server/RemoteServer.java` | NanoHTTPD:9978 全部路由 | P0-1② 的核心；`/proxy` 有意未加 token（见必读） |
| `app/src/main/java/com/github/catvod/crawler/JarLoader.java` | jar 爬虫加载（DexClassLoader） | P0-1③ fail-closed 校验位置 |
| `/home/doro/reports/BOX_CODE_REVIEW_2026-09-30.md` | 审查报告（P1/P2 待办清单） | 后续 P1/P2 开发的需求来源 |

## 已完成工作

### 任务清单

- [x] P0-1① 移除进程级 trust-all TLS（SSLCompat 重写 + OkHttp/OkGoHelper 改安全默认；`createTrustAll`/`TRUST_ALL_VERIFIER` 保留作按源 opt-in）
- [x] P0-1② 本地双服务 token 鉴权（ServerToken 新建；9978 六个危险端点 + `/action` 五个配置下发动作 + AndServer `/api/updateUrl`）+ canonical path 包含校验 + zip-slip 防护
- [x] P0-1③ jar 下载 fail-closed：md5 声明不匹配必拒载；无 md5 时受 `JAR_VERIFY_STRICT` 开关控制（默认放行但记 error 日志）
- [x] P0-2 CrashHandler 新建 + App.onCreate 安装，崩溃日志落 `files/crash/`
- [x] P0-3 WebView：file:// access 全关、SSL 错误不再 proceed；Crosswalk 使用路径在 PlayActivity/PlayFragment 两处硬编码停用（模块本身仍在 settings.gradle/依赖里，见延后项）
- [x] pyramid `verify=False` 移除
- [x] **P1-4** `CacheManager.save/delete`（含播放进度，每次暂停触发）序列化+SQLite 写入移后台单线程池；`allowMainThreadQueries` 保留（读路径异步化工程量大，见延后项）
- [x] **P1-5** `getSearch` 爬虫搜索加 15s 超时（独立 cached 线程池 `spiderSearchPool`，防慢源阻塞+防饿死）；`cleanPlayerCache` 移后台线程
- [x] **P1-6** IJK 默认硬解码（`Hawk.get` 默认值 + 兜底选择均优先"硬解码"组）；`autoRetry` 升级为两段式：第一次 IJK 硬/软解互换（`HawkUtils.nextIJKCodec()`），第二次切换内核 IJK↔EXO；proguard 恢复 `com.github.tvbox.osc.bean.**` keep
- [x] **P1-7（部分）** `HistoryActivity`/`CollectActivity` onDestroy 置空静态 adapter；`BaseActivity.onTrimMemory` 内存告警时释放 `globalWp` 位图
- [x] 静态核查：IDE 诊断 0 告警；改动文件括号配平自检通过（RemoteServer 的配平"异常"与原始文件差值一致，系字符串内正则干扰，非真问题）

### 决策记录

| 决策 | 备选项 | 为什么选它 |
|---|---|---|
| `JAR_VERIFY_STRICT` 默认 **false** | 默认 true（报告字面要求"无 md5 时拒绝执行"） | 市面主流 TVBox 配置的 spider 大多不带 md5，默认 true 会让 App 对存量生态直接不可用；折中：声明了 md5 的必须匹配（真 fail-closed），无 md5 的留严格开关给用户/固件打包者打开 |
| token 持久化（Hawk）而非每次启动随机 | 每次启动随机（报告原文"启动随机 token"） | 随机 token 无法被手机端遥控页面/推送脚本获知（Web UI 是静态资源，没有下发 token 的通道），持久化才能实际用起来；安全性仍优于"无鉴权" |
| `/action` 只对 `api/live/epg/proxys/push` 鉴权，`search/mirror` 放行 | 全部 /action 鉴权 | 保留手机遥控"搜索/推送影片"的可用性；配置劫持类动作才是审查指出的威胁链入口 |
| Crosswalk 只停用调用路径，不删模块 | 从 settings.gradle/依赖中移除 xwalk 模块 | 删模块牵动 gradle flavor/打包脚本，风险大且本机无法编译验证；先断运行时路径，模块移除列为延后项 |
| 崩溃日志写 `getExternalFilesDir`（无则 `getFilesDir`） | 仅内部存储 | 外部 files 目录用户可用文件管理器直接取日志，老盒子取证更方便；无权限时自动降级 |

## 待办工作

### 立即下一步

1. **提交工作树中未提交的 P1 改动**（若接手时仍未提交）：`cd /home/doro/Box && git add -A && git commit`，建议 message：`stability: P1 hardening per code review 2026-09-30 (async cache writes, search timeout, IJK hw-decode default, retry chain, static leak fixes)`。
2. **在有 Android SDK 的机器上编译验证**：JDK 11–17 + `sdkmanager "platforms;android-28"`，然后 `./gradlew assembleDebug`（先 normal flavor）。重点核对 `SSLCompat.create()` 与 OkHttp `sslSocketFactory()` 签名、`ServerToken` 在 Kotlin 侧调用、SourceViewModel 新增 spiderSearchPool。
3. 真机冒烟：带 md5 / 不带 md5 / 开启 `JAR_VERIFY_STRICT` 三种配置各加载一次；搜索 30 源观察超时日志；IJK 硬解失败时观察自动切软解。

### Blocker / 未决问题

- [ ] **远程仓库血统不一致（已决策，待用户在 GitHub 侧处理）**：`dororo42/dorobox` 仓库现 main 是 FongMi/TV 内容（HEAD `a4d00938c`，与 `/home/doro/TV` fongmi 分支同源），与 takagen99/Box 无共同祖先。用户已决策"先不动远程"；本地 Box 仓库已配 `origin`→dorobox、`upstream`→takagen99/Box，**在用户整理好 GitHub 仓库（改名/清空/另建）之前不要 push**。FongMi 内容在 `dororo42/TV` 有完整副本。
- [ ] 编译验证未执行（本机无 Android SDK platforms；JDK 25 与 AGP 7.4.2 不兼容）—— 需要：装有 JDK 11–17 + Android SDK 的环境，或在本机安装两者。
- [ ] token 如何触达用户/遥控端尚无 UI—— 需要：在设置页显示 token（或生成带 token 的二维码），否则 AndServer 推送与文件端点实际不可用。

### 延后项

- 从 `settings.gradle`/依赖中物理移除 `xwalk` 模块与 `XWalkInitDialog`/`XWalkUtils` 相关代码（P0-3 的收尾；运行时路径已断，编译期仍在）。
- P1 残余：`allowMainThreadQueries` 完全移除（读路径逐点异步化）；JS 源惰性创建 QuickJS VM + 限制并发搜索数；EPG/直播列表 DiffUtil（`LivePlayActivity:1222,1261` 一带）；Hawk 热点 key 内存缓存。
- P2 工程化 8–10 项（Spider 开发指南、proguard 无效规则清理、release shrinkResources、media3 升级）。
- `usesCleartextTraffic="true"` 与 `MANAGE_EXTERNAL_STORAGE`、`REQUEST_INSTALL_PACKAGES` 权限收敛（报告 M-5）。

## 接手方必读

### 重要上下文

- `/home/doro/TV` 是另一个项目（FongMi fork，分支 fongmi），与本工作无关；本轮工作全部发生在 `/home/doro/Box`。审查报告里"FongMi fork（已审）已修"指的是 TV 那边的历史工作。
- `/proxy` 端点**有意未加 token**：它是播放链路的一部分（播放器/spider 经 127.0.0.1 拉流），加 token 会断播放。SSRF 风险被记录为接受项。
- NanoHTTPD 的 header key 一律小写，所以 `getToken()` 里找的是 `x-token` 而不是 `X-Token`。
- `RemoteServer` 里出现"括号不配平"是审查脚本的误报（字符串里有 `['|\"]?` 这类正则），与原始 commit 差值一致，不要试图"修复"。
- 审查报告行号对应 commit `258a5fe`，本轮改动后行号已漂移，引用报告时按类名/方法名定位。

### 假设

- Hawk 在 `ServerToken.get()` 首次调用时已初始化（App.initParams 在 onCreate 早期执行，而本地服务在其后由 ControlManager/播放推送触发）——接手方应在真机上验证 `/action` 首次调用不会因 Hawk 未初始化抛异常。
- `XWalkSettings`/`XWalkUIClient` 的 `setAllowFileAccess(false)` 系 API 与系统 WebView 同名同语义（按交叉资源确认，未实测）。

### 已知坑

- **远程命名**：`origin` = dororo42/dorobox（用户 fork，但仓库 main 血统是 FongMi/TV，勿直接 push main）；`upstream` = takagen99/Box（真上游）。历史遗留：之前按 FongMi/TV 开发时把内容推进了 dorobox。
- 本机 `java` 是 JDK 25：不要尝试直接跑 `./gradlew`，会报 AGP 版本不兼容。
- `app/libs` 与 `jniLibs` 有提交进仓库的二进制（42MB `.so` 等），clone/构建耗时较长属正常。
- OkGo 的 `OkGo.<File>get(jar).execute()` 走的是 OkGoHelper 初始化的全局 client，其 SSL 已改为安全默认——自签名证书的源会开始失败，这是预期行为，个别源可用 `createTrustAll` 思路做按源 opt-in（尚未实现 UI）。

## 环境

### 构建 / 运行 / 测试命令

```
# 要求：JDK 11–17（AGP 7.4.2 + Gradle 7.5），Android SDK platform 28
cd /home/doro/Box
./gradlew assembleDebug          # normal flavor
./gradlew assembleDebugPython    # python flavor（含 chaquopy，需要 buildPython）
```

### 环境变量（只写名字，绝不写值）

- `ANDROID_HOME` / `ANDROID_SDK_ROOT` —— 本机当前**未设置**，需先装 SDK 再设。
- pyramid flavor 需要 `buildPython` 指向本机 Python 3（报告 P2 第 9 项建议参数化）。

---

**安全提醒**：定稿前运行 validate_handoff.py。检测到密钥或质量分 <70 时不得交付。
