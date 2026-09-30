# Box Spider 开发指南

> 适用范围：takagen99/Box 血统（本仓库）。把隐含在 `SourceViewModel` 解析代码里的源契约显性化，供自研源参考。
> 结论先行：**优先用 JS 格式写源**（单文件、可热加载、无构建链），简单站 2–6 小时，复杂站 1–3 天。

---

## 1. Spider API 契约

每种格式的源最终都要实现以下方法（返回 JSON 字符串或对象，由 `SourceViewModel` 统一解析）：

| 方法 | 签名 | 说明 |
|---|---|---|
| `init` | `init(ctx, ext)` | 初始化。`ext` 为站点配置里的 `ext` 字段（注意：本仓不识别 `extension` 字段） |
| `homeContent` | `homeContent(filter)` | 首页分类 + 推荐列表。`filter: bool` 是否带筛选 |
| `homeVideoContent` | `homeVideoContent()` | 首页推荐影片（无分类时用） |
| `categoryContent` | `categoryContent(tid, pg, filter, extend)` | 分类列表。`extend` 为筛选条件 JSON 字符串 |
| `detailContent` | `detailContent(ids)` | 详情。`ids` 为 vod_id 数组（取首个） |
| `searchContent` | `searchContent(key, quick[, pg])` | 搜索。`quick=true` 时可返回空表示不支持 |
| `playerContent` | `playerContent(flag, id, vipFlags)` | 播放信息。返回 `{parse, url, playUrl, flag, header?}` |
| `proxyLocal` | `proxyLocal(params)` | 本地代理（可选），经 `/proxy?do=...` 访问 |

### 列表结果形状（MacCMS 兼容）

```
list[]: { vod_id, vod_name, vod_pic, vod_remarks, vod_year, vod_area, vod_actor,
          vod_director, vod_content, type_name, ... }
```

### 详情播放线路形状

```
vod_play_from = "线路1$$$线路2"
vod_play_url  = "第1集$https://...#第2集$https://...$$$第1集$..."
```

### 播放结果

```json
{ "parse": 0, "url": "https://...", "playUrl": "", "flag": "线路1" }
```
- `parse: 1` 走嗅探（Web 播放页），`0` 直链。
- 直链带自定义头时用 `header`（本仓支持的扩展）。

---

## 2. JS 源（首选）

### 约定（四选一，推荐前两种）

1. `export default {...}` / `__JS_SPIDER__` 赋值（ES module）
2. 返回 `__jsEvalReturn` 对象（catvod 风格）
3. 文件头 `//DRPY`（drpy 约定）
4. 文件头 `//bb` + Base64 字节码

### 内置能力

- `pdfh / pdfa / pdfb`：类 jQuery 规则解析（详见内置 `cat.js`，约 625KB drpy 核心）
- `cheerio`、`crypto-js`、`gbk`、`模板.js`（模板站套壳）
- `js2Proxy`：把站内请求转经本地代理
- `req(url, options)` / 网络请求走内置 OkHttp（**已默认走系统证书校验**，自签名站点需自行处理）
- `console.log` → logcat（调试用）

### 最小示例

见 [`examples/spider_demo.js`](examples/spider_demo.js)。

### 部署

- 配置里站点项写 `"type": 3, "api": "https://.../spider_demo.js"`。
- 改 JS 后重进站点即生效（按 api URL 缓存，无 md5 缓存一周）。

---

## 3. Java jar 源

- 配置：`"spider": "https://.../spider.jar;md5;<md5>"`（**强烈建议带 md5**：下载后校验不匹配会拒绝加载；不带 md5 时受设置里严格模式 `JAR_VERIFY_STRICT` 控制）。
- 类：`com.github.catvod.spider.<类名>`，站点 `"api": "csp_类名"`。
- 约定类：`Init.init(Context)`（加载后自动调用）、`Proxy.proxy(Map)`（本地代理）。
- 结果直接返回 JSON 字符串（同第 1 节契约）。
- 注意：`parser.Json*` / `parser.Mix*` 热插拔类从 **main jar**（配置顶层 `spider`）加载。

## 4. 声明式源（XPath / XBPQ / AppYsV2 / JsonMix）

- app 本体不含这些解析类，**依赖 jar 捆绑**（社区大 jar 一般有；最小 jar 会静默退化为 SpiderNull）。
- 配置示例见 [`examples/xpath_demo.json`](examples/xpath_demo.json)。
- 适用：规则化网站，纯配置 0.5–2 小时/站。

## 5. Python 源（pyramid，仅 python flavor APK）

- 依赖链约定见 `pyramid/src/python/app.py`；远程 .py 下载后 `load_module` 执行。
- 分发面窄（需 python flavor 构建 + buildPython），除非必要不建议。

---

## 6. 调试回路

1. `HawkConfig.DEBUG_OPEN`（调试模式）打开：嗅探 WebView 可见（800×400）、图片不屏蔽。
2. logcat 过滤 `echo-` 前缀（`SourceViewModel`/`JarLoader`/`JsLoader` 均有 echo 日志）。
3. 本地 HTTP 托管 JS/配置（`python3 -m http.server`），配置 URL 填局域网地址即可热改。
4. 搜索已内置 15s 超时 + 4 并发上限；超时源会在 logcat 报 `getSearch timeout`。

## 7. 安全须知

- 源配置可被 `/action?do=api` 远程下发（需 token，见设置页"远程控制令牌"）。
- jar 强烈建议在配置中带 md5；CI/固件分发可开 `JAR_VERIFY_STRICT`（未声明 md5 一律拒载）。
- 网络层默认系统证书校验（trust-all 已移除）；源站自签名证书会失败，这是预期安全行为。
