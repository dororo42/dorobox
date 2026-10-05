# AGENTS.md

takagen99/Box 的本地开发副本（TVBox 血统安卓应用，Java，minSdk 21 / targetSdk 28，构建链 AGP 7.4.2 + Gradle 7.5 + JDK 11–17）。当前主线：按 `/home/doro/reports/BOX_CODE_REVIEW_2026-09-30.md` 的 P0→P1→P2 顺序做安全与稳定性加固。长期规则：改动聚焦报告条目；不主动重构无关代码；未经用户明说不 commit/push。

## Current Handoff

- Latest: `handoffs/2026-10-10-perf-stability-fixes/HANDOFF.md`
- Branch: main @ bd8932bed（已推送至 `dororo42/dorobox`；CI run 37264374646 绿）
- Status: 性能与稳定性审查（报告 `/home/doro/reports/dorobox-perf-stability-review-2026-10-10.md`）P-1..P-13 已实施并 CI 全绿：清缓存 ANR 移后台、直播页 Handler 泄漏清理、搜索有界队列背压、UA 内存化、DbIo 等待收敛、Glide 运行时分档（注意 redirectglide 已占 AppGlideModule，勿再加）、AppManager 线程安全、EpgUtil 异步化、LeakCanary(debug)。真机 192.168.2.230 关机中，回归待开机后执行（装 bd8932bed 包覆盖）。第 3 轮崩溃修复（EventBus/appExit/QuickJS SIGSEGV）见上一份 handoff。

任何 agent 开始工作前，先读上面的 HANDOFF.md。这份指针由 agent-handoff 维护，手工交接时请同步更新。
