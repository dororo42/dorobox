# Handoff: Box 清理收尾 + token 二维码（Crosswalk 物理移除、CI/真机冒烟）

## 元数据

- Created: 2026-10-04
- Source agent: CodeBuddy (GLM-5.3-Flash)
- Project: 本地副本 /home/doro/Box，远程 origin = dororo42/dorobox（Box 血统）
- Branch: main
- Supersedes: handoffs/2026-09-30-2120-box-p0-security-hardening/HANDOFF.md（其中所有状态以本份为准）

## 当前状态摘要

2026-09-30 报告（P0/P1/P2）与第二轮自审全部闭环后，2026-10-04 会话完成三批工作并经 CI（run 37195002560 全绿）+ 真机（192.168.2.230:5555，Android 7.1.2 / arm64）冒烟验证：

1. **B1** `6369dffa` 删除死配置 `.github/workflows/test.yml`（无效任务名 assemblerelease、废弃 upload-artifact@v3，从未启用）。
2. **B2** `bdbc6aec` 删除仓库内 105MB `xwalk/crosswalk-apks-*.zip`（无代码读取的死重）。
3. **B3** `071f6d52` **Crosswalk 物理移除**：删 `app/libs/xwalk_shared_library-23.53.589.4.aar`、`XWalkUtils`、`XWalkInitDialog`、`res/layout/dialog_xwalk.xml`；`PlayActivity`/`PlayFragment` 清除全部 xwalk 分支/字段/内部类；`VodController.evaluateScript` 去掉 XWalkView 参数；manifest 删 `xwalk_enable_download_mode`/`xwalk_verify=disable`；proguard 删 keep 规则。**附带修复**：PlayFragment 系统 WebView 此前遗漏 H-1 加固（file access 仍为 true），已对齐 PlayActivity 全关。
4. **C1** token 二维码：设置页"远程控制令牌"点击弹出新 `ServerTokenDialog`（复用 `ui/tv/QRCodeGen`），二维码内容为 `http://<LAN-IP>:9978/?token=<token>`；`script.js` 顶部新增 `?token=` 深链解析（URLSearchParams → localStorage 自动登录），扫码即完成远控登录。

## 真机冒烟已验证（自动部分）

- App 启动无崩溃，双服务 9978/12345 监听。
- `/file`、`/upload` 无 token → 403；`POST /action?do=api` 无 token → 403；`POST do=search` 按决策放行 → 200；Web UI 首页 → 200。
- GET `/action` 无处理器，落空到 Web UI，不执行动作（鉴权在 POST 路由，符合设计）。
- APK 内容 0 个 xwalk 条目。

## 待办工作

### 真机人工冒烟（需遥控器，未做）

1. 加载常用配置：验证 clan:// 链路与 jar（带/不带 md5、开 `JAR_VERIFY_STRICT`）三态。
2. 播放四内核切换，重点 EXO media3 1.4.1 + nextlib FFmpeg 软解路径。
3. 设置 → 远程控制令牌 → 确认二维码弹出、手机扫码直达远控页且不再 403。
4. EPG 回看。

### 已知观察项（不阻塞）

- 未配置源时 `/proxy` 请求 NPE（`ApiConfig.proxyLocal` spider 为 null，NanoHTTPD 捕获后断开）——干净设备既有行为，配置源后消失。
- **git 历史里 105MB zip 仍在**（B2 只删工作区）：在意 clone 体积需 `git filter-repo` 重写历史（破坏性，需用户拍板）。
- 依赖版本散落 build.gradle（L-4）；Hawk 热点缓存未做；M-5 权限收敛未做（`REQUEST_INSTALL_PACKAGES` 无代码使用可删；`MANAGE_EXTERNAL_STORAGE` 降级需评估；`usesCleartextTraffic` 生态依赖，记录为接受项）。

## 环境 / 工作流

- **构建验证走 GitHub Actions**（本机 JDK 25、无 SDK platforms、不装本地构建环境）：push main 自动触发 `.github/workflows/build.yml`。
- `gh run list` 在本机返回空（原因未查明），用 `gh api repos/dororo42/dorobox/actions/runs` 查询；`gh run download` 必须带 `-R dororo42/dorobox`（否则解析到 upstream）。
- adb 已装（apt），真机 `adb connect 192.168.2.230:5555`。设备上另有一个 `com.github.tvbox.osc.tk` 旧签名版已卸载重装。
- token 鉴权行为速测：`curl -X POST http://<box>:9978/action -d "do=api&url=..."` 无 token 应 403。

## 沿用注意事项（仍有效）

- `/proxy` 有意不加 token（播放链路）；NanoHTTPD header key 一律小写。
- 布局 `res/layout` 与 `res/layout-v21` 双份，加控件两份都要改（对话框布局无 v21 变体，单份即可）。
- `script.js` 有预置重复函数定义（delFolder/doDelFolder 各两份），改动需两处覆盖。
- `git rm` 后用 `git add <path>` 对已删路径会报 pathspec 错误，用 `git add -A <dir>`。
- RemoteServer"括号不配平"是审查脚本误报，勿修。
