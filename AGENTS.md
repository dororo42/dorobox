# AGENTS.md

takagen99/Box 的本地开发副本（TVBox 血统安卓应用，Java，minSdk 21 / targetSdk 28，构建链 AGP 7.4.2 + Gradle 7.5 + JDK 11–17）。当前主线：按 `/home/doro/reports/BOX_CODE_REVIEW_2026-09-30.md` 的 P0→P1→P2 顺序做安全与稳定性加固。长期规则：改动聚焦报告条目；不主动重构无关代码；未经用户明说不 commit/push。

## Current Handoff

- Latest: `handoffs/2026-09-30-2120-box-p0-security-hardening/HANDOFF.md`
- Branch: main @ 258a5fe（含未提交的 P0 加固改动）
- Status: 进行中 —— P0 三项代码已落地，待有 SDK 环境编译验证后提交；下一步为 P1 稳定性项

任何 agent 开始工作前，先读上面的 HANDOFF.md。这份指针由 agent-handoff 维护，手工交接时请同步更新。
