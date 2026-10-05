# Handoff: dorobox 第 3 轮独立审查修复 —— 真机"设置源→加载成功→闪退"根因 + 31 项修复（真机回归通过，未 commit）

## 元数据

- Created: 2026-10-05（深夜会话）；同日真机回归完成
- Source agent: ZCode（GLM-5.3-Flash）
- Project: /home/doro/Box，远程 origin = dororo42/dorobox
- Baseline: main @ b35163e61（修复全部为工作区未提交改动，`git status` 可见 18 个文件改动 + ShellUtils 删除）
- 审查输出：`/home/doro/reports/dorobox-independent-review-2026-10-05.md`（第 3 轮，含发现/复核/修复清单 + **真机回归结果表**）

## 用户报告 bug 的根因结论（真机已验证修复）

"安装→设置源→提示加载成功→闪退" = 两条独立缺陷：

1. **根因 A（旧包，已在 main 修）**：设备上的 com.dorobox.tvbox = cb30f57c7 仍含 UserFragment EventBus 崩溃（3425cf086 已修但未装机）。UserFragment 在源加载成功后的 initViewPager 才创建，所以崩溃时序恰好是"加载成功 Toast 之后"。
2. **根因 B（main 原本仍有，本轮修）**：`HomeActivity.onDestroy` 无条件 `AppManager.appExit(0)`（killProcess，上游固有）。SettingActivity.onBackPressed 检测到配置变更会 finishAllActivity + 启动新主页，旧主页 onDestroy 把整个进程杀掉 → 新主页（连同刚弹的"加载成功"Toast）一起死。换源 reloadHome / reHome / 删缓存 reload / 换主题重启全部同病。修复 = 删除该 appExit(0)，自杀仅保留 doExit 双击退出（真机已验证 doExit 保留生效）。
3. **真机实证**：覆盖安装后，/action do=api 设置源 → **进程 PID 不变**、主页正常重载；启动/首页/历史页均无崩溃。

## 真机回归发现的第 3 个 🔴：QuickJS 预编译 wrapper 空模块段错误（H-5，已缓解）

搜索压测复现 2 次 SIGSEGV（~30%），回溯 = `JS_GetModuleExport+116`（libquickjs-android-wrapper.so），模块求值失败（D-5 的 64MB/1MB 上限命中或源 JS 语法错）时 wrapper 不判空。**雷随 8436f488 埋下，旧包因启动即崩从未跑到搜索才首次暴露**。缓解：上限放宽 512MB/8MB + evaluateModule 前 compileModule 预检跳过坏源 → **10/10 轮冷启动搜索零崩溃**。根治（后续）：vendor wrapper 源码补判空或 QuickJS 隔离独立进程。

## 本轮改动摘要（18 文件，编译通过 + 真机回归通过，未 commit/push）

- 🔴 5 项：H-1 onDestroy appExit（根因 B）、H-2 script.js 存储型 XSS（双层转义）、H-3 restore 回滚先删原库、H-4 playGroupCount==0 除零、**H-5 QuickJS wrapper 段错误缓解**
- 🟡 13 项：I-1 web 文件管理器 body token 全 403（真机实证修复）、I-2 SourceViewModel 四处 NPE、I-3 jar 加载主线程 ANR、I-4 join 线程泄漏、I-5 D-7 主 jar 路径漏网、I-6 sortCache 并发、I-7 onPause 掐断加载链、I-8 空 url 黑屏、I-9 autoRetry 无条件翻转内核、I-10 换配置旧站点残留、I-11/12 配置解析健壮性、I-13 EventBus 泄漏×2+死代码
- 🟢/小修 13 项：/newFolder isRoot、zip 实际字节计数、/action 400/503、500 兜底、日志脱敏、PlayService split、ShellUtils 删除、initViewPager 幂等/界检查/静态判空、mClockHandler、liveAutoJumped、进度单次读、Thunder TOCTOU、PiP 判空、script.js delFolder 去重（N-10 完成）
- 真机回归明细见报告第五节（9 项通过，H-3/H-4 失败路径为代码级验证）；设备已恢复原配置（免鉴权=开启、源不变、token 不变）

## 环境 / 构建注意（新增）

- **本机 JDK 21/25 与 Gradle 7.5 不兼容**（AGP 7.4.2 需 ≤17）：已 `apt install openjdk-17-jdk-headless`，构建命令 `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 sh gradlew :app:assembleArm64GenericNormalDebug`（gradlew 无执行权限需用 sh；local.properties 已建指 /home/doro/android-sdk，已 gitignore 不入库）
- APK 产物：`app/build/outputs/apk/arm64GenericNormal/debug/TVBox_debug-arm64-generic-java.apk`
- 真机 adb 已连过（192.168.2.230:5555，Android 7.1.2 arm64，无 root，debug 包可覆盖安装）；截图 `adb exec-out screencap -p`（本次输出原生 1920x1080，tap 坐标即截图坐标）；uiautomator dump 不可用

## 待办

1. **用户拍板后 commit + push**（建议拆两个 commit：闪退根因修复批 + 安全/稳定性批；或单 commit 注明第 3 轮审查）
2. 根治 H-5：vendor quickjs wrapper 源码加 NULL 检查（evaluate 模块失败路径），或 QuickJS 执行移独立进程
3. 沿袭：D-4 exported receiver、D-9 allowBackup、m-3/m-5/m-7/m-8、git 历史 105MB、M-5、Hawk 热点、version catalog、R-1/R-2 播放链并发

## 沿用注意事项（不变）

- 布局 res/layout 与 layout-v21 双份；script.js 已去重 delFolder（本轮），改动仅需一处
- RemoteServer"括号不配平"是审查脚本误报，勿修
- EventBus：删订阅方法必须同步删 register/unregister
- token=2dbcac1ee68488a908c1df24f596242（Hawk 持久化，覆盖安装不变；设置页二维码弹窗可读）

