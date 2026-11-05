# AGENTS.md

takagen99/Box 的本地开发副本（TVBox 血统安卓应用，Java，minSdk 21 / targetSdk 28，构建链 AGP 7.4.2 + Gradle 7.5 + JDK 11–17）。当前主线：按 `[REDACTED]` 的 P0→P1→P2 顺序做安全与稳定性加固。长期规则：改动聚焦报告条目；不主动重构无关代码；未经用户明说不 commit/push。

## Current Handoff

- Latest: `handoffs/2026-10-10-perf-stability-fixes/HANDOFF.md`（含 11-05 三段补记；链头 `ee64c7aa1`）
- Branch: main @ ccb13f2fe→ee64c7aa1（已推送至 `dororo42/dorobox`；CI run 41943996731 绿）
- Status: P-1..P-13 已实施并 CI 全绿：清缓存 ANR 移后台、直播页 Handler 泄漏清理、搜索有界队列背压、UA 内存化、DbIo 等待收敛、Glide 运行时分档（注意 redirectglide 已占 AppGlideModule，勿再加）、AppManager 线程安全、EpgUtil 异步化、LeakCanary(debug)。11-05：崩溃族 4 处已修（0eca9e878）、P-3 收敛 + QuickJS 加固（ee64c7aa1），真机 229:5555 装 2229 包全绿。第 3 轮崩溃修复（EventBus/appExit/QuickJS SIGSEGV）见上一份 handoff。

任何 agent 开始工作前，先读上面的 HANDOFF.md。这份指针由 agent-handoff 维护，手工交接时请同步更新。

### 2026-11-05 审查轮更正（zcode）

- ⚠️ 上文「真机 [REDACTED] 关机中」与「装 bd8932bed 包覆盖」两条作废：真机（现网 IP **[REDACTED]**:5555，同一台 p230）实际安装包经 dex 字符串定谳为 `b35163e61` 时点构建（1.0.20261005_0941），**不含 P-1..P-13 与 4958d6b1a**，P-1/P-2 真机验证结论需用正确基线重验。
- 「使用中有时重启加载」已定位为两处 NPE（SearchKeyboard 静态 getRes + GridFragment XML 缺 tv_layoutManager × tv-recyclerview 3.0.0 无判空），修复方案与 GitHub 调研见 `[REDACTED]重启加载-代码审查-2026-11-05.md`；HANDOFF.md 已补记 2026-11-05 段。
