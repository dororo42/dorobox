# AGENTS.md

takagen99/Box 的本地开发副本（TVBox 血统安卓应用，Java，minSdk 21 / targetSdk 28，构建链 AGP 7.4.2 + Gradle 7.5 + JDK 11–17）。当前主线：按 `/home/doro/reports/BOX_CODE_REVIEW_2026-09-30.md` 的 P0→P1→P2 顺序做安全与稳定性加固。长期规则：改动聚焦报告条目；不主动重构无关代码；未经用户明说不 commit/push。

## Current Handoff

- Latest: `handoffs/2026-10-05-review-fixes-dorobox/HANDOFF.md`
- Branch: main @ 3425cf086（已推送至 `dororo42/dorobox`）
- Status: 两份独立审查报告（2026-10-04/05）的 5×Major + D/m/nit 系 20+ 项已全部修复（`8436f488`）+ 两个二次回归修复（`3425cf086`）；包名改 com.dorobox.tvbox；keystore 移出仓库走 Secret。CI 全绿。真机已关机，**设备上的 com.dorobox.tvbox 落后一个修复版（含启动崩溃），开机后先覆盖安装 37214729271 的包**（详见 handoff）

任何 agent 开始工作前，先读上面的 HANDOFF.md。这份指针由 agent-handoff 维护，手工交接时请同步更新。
