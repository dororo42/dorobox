# AGENTS.md

takagen99/Box 的本地开发副本（TVBox 血统安卓应用，Java，minSdk 21 / targetSdk 28，构建链 AGP 7.4.2 + Gradle 7.5 + JDK 11–17）。当前主线：按 `/home/doro/reports/BOX_CODE_REVIEW_2026-09-30.md` 的 P0→P1→P2 顺序做安全与稳定性加固。长期规则：改动聚焦报告条目；不主动重构无关代码；未经用户明说不 commit/push。

## Current Handoff

- Latest: `handoffs/2026-10-04-cleanup-token-qr/HANDOFF.md`
- Branch: main（已推送至 `dororo42/dorobox`；原 FongMi 内容仓库已改名 `dororo42/tv-fongmi`）
- Status: 审查报告 P0/P1/P2 + 两轮自审全部闭环；Crosswalk 已物理移除（aar/代码/manifest/proguard/105MB zip）；token 二维码已上线（扫码直达远控页）；CI 全绿 + 真机（192.168.2.230:5555）自动冒烟通过。剩余：真机人工冒烟（配置加载/播放/EPG）、git 历史中 105MB zip 需 filter-repo 才能瘦身、可选优化（M-5 权限收敛、Hawk 热点缓存、version catalog）

任何 agent 开始工作前，先读上面的 HANDOFF.md。这份指针由 agent-handoff 维护，手工交接时请同步更新。
