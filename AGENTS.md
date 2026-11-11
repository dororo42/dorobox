# AGENTS.md

takagen99/Box 的本地开发副本 dorobox（TVBox 血统安卓应用，Java，minSdk 21 / targetSdk 28，构建链 AGP 7.4.2 + Gradle 7.5 + JDK 11–17；仓库 dororo42/dorobox，**已 PUBLIC**）。当前主线：公开发布准备（Release + handoffs 裁切）；稳定性/工程化加固四轮已收口。长期规则：改动聚焦报告条目；不主动重构无关代码；未经用户明说不 commit/push；内部信息（IP/token/交接细节）不再入库；**后续建构和编译一律放到 GH（GitHub Actions）执行**，本地不跑 gradlew 构建/编译/单测/lint，验证以 CI run 状态 + CI artifact 真机安装为准。

## Current Handoff

- Latest: `handoffs/2026-11-07-221414-release-prep/HANDOFF.md`（validator 100/100 READY；前序 handoffs/2026-10-10-perf-stability-fixes/HANDOFF.md 含 11-05~07 全部补记轨迹）
- Branch: main @ bdfcf94fb（CI run 42374366665 绿）
- Status: 进行中——Release 发布与 handoffs 裁切（git rm --cached 已做，历史 rewrite 待用户拍板）；四轮修复（崩溃族/P-3 watchdog/QuickJS H-5/P-12/ANR 节流）已全部落地并真机验证

任何 agent 开始工作前，先读上面的 HANDOFF.md。这份指针由 orbital_handoff 维护，手工交接时请同步更新。
