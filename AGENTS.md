# AGENTS.md

takagen99/Box 的本地开发副本（TVBox 血统安卓应用，Java，minSdk 21 / targetSdk 28，构建链 AGP 7.4.2 + Gradle 7.5 + JDK 11–17）。当前主线：按 `/home/doro/reports/BOX_CODE_REVIEW_2026-09-30.md` 的 P0→P1→P2 顺序做安全与稳定性加固。长期规则：改动聚焦报告条目；不主动重构无关代码；未经用户明说不 commit/push。

## Current Handoff

- Latest: `handoffs/2026-10-05-crashfix-round3-review/HANDOFF.md`
- Branch: main @ b35163e61（**本轮 31 项修复在工作区未 commit**，真机回归已通过，等用户拍板提交）
- Status: 第 3 轮独立审查（报告 `/home/doro/reports/dorobox-independent-review-2026-10-05.md`）+ 真机回归完成：闪退根因 = 旧包 EventBus + `HomeActivity.onDestroy appExit(0)` 进程自杀，均已修并实证（设置源后 PID 不变）；另发现并缓解 QuickJS 预编译 wrapper 段错误（D-5 潜伏雷，搜索 10/10 轮零崩溃）、script.js XSS、restore 回滚、body token 403 回归等 31 项。设备 192.168.2.230 已恢复原配置运行新包。

任何 agent 开始工作前，先读上面的 HANDOFF.md。这份指针由 agent-handoff 维护，手工交接时请同步更新。
