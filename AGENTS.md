# AGENTS.md

takagen99/Box 的本地开发副本（TVBox 血统安卓应用，Java，minSdk 21 / targetSdk 28，构建链 AGP 7.4.2 + Gradle 7.5 + JDK 11–17）。当前主线：按 `/home/doro/reports/BOX_CODE_REVIEW_2026-09-30.md` 的 P0→P1→P2 顺序做安全与稳定性加固。长期规则：改动聚焦报告条目；不主动重构无关代码；未经用户明说不 commit/push。

## Current Handoff

- Latest: `handoffs/2026-09-30-2120-box-p0-security-hardening/HANDOFF.md`
- Branch: main @ 678d165（P0 `a8dfc43` + P1 `678d165` 均已本地提交，未推送）
- Status: 进行中 —— 远程 dorobox 仓库血统为 FongMi/TV，用户决定暂不推送、待整理 GitHub 仓库；下一步为 P1 残余项与 P2 工程化

任何 agent 开始工作前，先读上面的 HANDOFF.md。这份指针由 agent-handoff 维护，手工交接时请同步更新。
