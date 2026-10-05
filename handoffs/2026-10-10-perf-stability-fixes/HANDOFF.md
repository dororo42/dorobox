# Handoff: dorobox 性能与稳定性审查修复（P-1..P-13）

## 元数据

- Created: 2026-10-10
- Source agent: CodeBuddy (GLM-5.3-Flash)
- Branch: main @ bd8932bed
- Supersedes: handoffs/2026-10-05-review-fixes-dorobox/HANDOFF.md（该轮内容仍有效）
- 审查输入：/home/doro/reports/dorobox-perf-stability-review-2026-10-10.md（基线 384358b4a）

## 已实施（提交 85a6707aa + bd8932bed，CI run 37264374646 全绿）

- 🔴P-1 HomeActivity 清缓存整体移后台线程（cache 目录数百 MB 主线程递归删必 ANR；App.onCreate 同类操作早已异步，此为漏改点）；dataInitOk/jarInitOk 加 volatile；reloadHome 回主线程
- 🔴P-2 LivePlayActivity.onDestroy `mHandler.removeCallbacksAndMessages(null)`——三个每秒自重投 Runnable 曾使非返回键路径（7 处 finish()）每次泄漏整个 Activity
- 🟡P-3 搜索 fan-out 有界队列：ThreadPoolExecutor(5,5,queue=64,DiscardPolicy)；`execute()` 返回 boolean，SearchActivity 对被拒任务回滚 allRunCount（防 loading 永久卡住）
- 🟡P-4/P-11 UA.random() 预载 16 条 UA 进内存轮换（原每张海报图主线程开 657KB asset 随机 seek）
- 🟡P-5 DbIo 主线程等待 3000→1000ms
- 🟡P-6 改运行时方案：App.onCreate 按设备内存分档 setMemoryCategory（HIGH≥3GB / NORMAL 2-3GB / LOW<2GB）。**注意：不能加自定义 AppGlideModule——redirectglide 库已自带一个（com.aminography.redirectglide.OkHttpAppGlideModule），Glide 只允许一个，加了 kapt 直接失败（已踩坑）**
- 🟡P-7 AppManager 重写：synchronized ArrayDeque + 空栈判空（原 Stack 懒初始化竞态/EmptyStackException/CME）
- 🟢P-8/P-9 EpgUtil.init 移后台线程（131KB asset+Gson 解析曾阻塞冷启动），key 小写归一化，未就绪 getEpgInfo 返回 null
- 🟢P-13 LeakCanary 2.14 debugImplementation（运行时注意：debug 包启动后会自动在通知栏提示泄漏，属预期）

## 报告中未实施（记录）

- P-12 单元测试（零 test 源集）、lint 门禁（NewApi/StaticFieldLeak/HandlerLeak 设 error）、性能基线度量（gfxinfo/atrace）——工程化项，待排期
- P-3 的 OkGo 统一取消路径、P-5 长期双线程 executor + WAL——评估后再做

## 待办

- 真机（关机中）回归：装 bd8932bed 包 → 验证启动/清缓存无 ANR/反复进出直播内存不涨/搜索有终态
- 第 3 轮报告遗留：com.dorobox.tvbox 若数据为空需重配源
