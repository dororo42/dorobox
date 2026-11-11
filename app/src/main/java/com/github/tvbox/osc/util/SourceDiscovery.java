package com.github.tvbox.osc.util;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 自动寻找数据源引擎（设计借鉴 be-engineer/yunTV 的种子页发现机制，独立实现）。
 * <p>
 * 流程：assets 种子页（带许可证标注）→ shuffle → 逐页抽取候选 URL（正则两遍，
 * 第二遍还原 JSON \/ 转义）→ 清洗 → SSRF 过滤（拒绝内网/环回，只留配置类后缀）
 * → shuffle → 逐候选结构校验（#EXTM3U=直播源 / JSON sites 数=点播源），命中即停。
 * 总预算 budgetMs 兜底，单请求 8s 超时，响应 2MB 截断——防失控三件套。
 * <p>
 * 必须在工作线程调用 {@link #discover}；纯函数（cleanUrl/isAllowedCandidate/
 * extractUrls/countSites/isLivePlaylist/guessName）无 Android 依赖，可直接单测。
 * <p>
 * SSRF 防护只适用于「引擎发现的外部候选」；用户自行配置的本地地址（clan://localhost
 * 等）是合法输入，验证源路径不应套用 {@link #isAllowedCandidate}。
 */
public final class SourceDiscovery {
    /** 抽取 URL 的正则（与常见 JSON/HTML 内嵌 URL 形态兼容） */
    public static final java.util.regex.Pattern URL_PATTERN =
            java.util.regex.Pattern.compile("https?://[A-Za-z0-9-_.$~!()*',;=:+&@%#?/\\[\\]]+");
    /** 单响应最大读取字节数：防超大页面拖爆内存 */
    public static final int MAX_PAGE_BYTES = 2 * 1024 * 1024;
    /** 发现的候选源上限（单次搜索命中数） */
    public static final int MAX_RESULTS = 3;
    /** 候选池上限：防止巨型清单页抽出海量 URL 撑爆集合 */
    private static final int MAX_CANDIDATES = 200;
    private static final long CONNECT_TIMEOUT_MS = 8000;
    private static final long READ_TIMEOUT_MS = 8000;

    private SourceDiscovery() {
    }

    /** 发现结果：name=URL 路径推断名，live=直播源，siteCount=点播配置的站点数 */
    public static class FoundSource {
        public final String name;
        public final String url;
        public final boolean live;
        public final int siteCount;

        public FoundSource(String name, String url, boolean live, int siteCount) {
            this.name = name;
            this.url = url;
            this.live = live;
            this.siteCount = siteCount;
        }
    }

    /**
     * 引擎主流程。工作线程调用，同步返回；budgetMs 为总时间预算（毫秒）。
     */
    public static List<FoundSource> discover(Context context, long budgetMs) {
        long deadline = System.currentTimeMillis() + budgetMs;
        List<String> seeds = loadSeeds(context);
        Collections.shuffle(seeds);
        Set<String> candidates = new LinkedHashSet<>();
        for (String seed : seeds) {
            if (System.currentTimeMillis() > deadline || candidates.size() >= MAX_CANDIDATES) break;
            String page = fetch(seed);
            if (page == null) continue;
            candidates.addAll(extractUrls(page));
        }
        List<String> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);
        List<FoundSource> found = new ArrayList<>();
        for (String url : shuffled) {
            if (found.size() >= MAX_RESULTS || System.currentTimeMillis() > deadline) break;
            String trimmed = url.trim();
            if (trimmed.isEmpty() || !isAllowedCandidate(trimmed)) continue;
            String body = fetch(trimmed);
            if (body == null) continue;
            if (isLivePlaylist(body)) {
                found.add(new FoundSource(guessName(trimmed), trimmed, true, 0));
            } else {
                int sites = countSites(body);
                if (sites > 0) found.add(new FoundSource(guessName(trimmed), trimmed, false, sites));
            }
        }
        return found;
    }

    /** 读 assets/source_seeds.json：[{"url": "...", "kind": "...", "note": "许可证说明"}] */
    public static List<String> loadSeeds(Context context) {
        List<String> seeds = new ArrayList<>();
        InputStream in = null;
        try {
            in = context.getAssets().open("source_seeds.json");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            for (JsonElement el : new JsonParser().parse(out.toString("UTF-8")).getAsJsonArray()) {
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    if (obj.has("url") && !obj.get("url").isJsonNull()) {
                        String url = obj.get("url").getAsString();
                        if (!TextUtils.isEmpty(url)) seeds.add(url.trim());
                    }
                }
            }
        } catch (Throwable th) {
            LOG.e("SourceDiscovery seeds: " + th.getMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return seeds;
    }

    /** 同步抓取：8s/8s 超时、UA 随机、2MB 截断；失败返回 null（全捕获，不外抛） */
    public static String fetch(String url) {
        Response response = null;
        try {
            Request request = new Request.Builder().url(url.trim())
                    .header("User-Agent", UA.random())
                    .build();
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build();
            response = client.newCall(request).execute();
            if (!response.isSuccessful() || response.body() == null) return null;
            String body = response.body().string();
            if (body.length() > MAX_PAGE_BYTES) body = body.substring(0, MAX_PAGE_BYTES);
            return body;
        } catch (Throwable th) {
            return null;
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 两遍抽取：原文一遍 + 把 JSON 转义 \/ 还原后再抽一遍 */
    public static Set<String> extractUrls(String body) {
        Set<String> out = new LinkedHashSet<>();
        collectInto(body, out);
        if (body.contains("\\/")) collectInto(body.replace("\\/", "/"), out);
        return out;
    }

    private static void collectInto(String body, Set<String> out) {
        java.util.regex.Matcher matcher = URL_PATTERN.matcher(body);
        while (matcher.find()) {
            String cleaned = cleanUrl(matcher.group());
            if (cleaned != null) out.add(cleaned);
        }
    }

    /** 清洗：剥尾部 ]})>,"'! 、首个 " 之后截断、循环去掉尾部 ./?/ ；清空返回 null。
     *  剥尾含 !：URL 正则字符类允许 !（合法字符），但自然语言句尾的 ! 会被一并匹配进来。 */
    public static String cleanUrl(String raw) {
        String url = raw.replaceAll("[\\]})>,\"'!]+$", "");
        int quote = url.indexOf('"');
        if (quote > 0) url = url.substring(0, quote);
        while (!url.isEmpty() && (url.endsWith(".") || url.endsWith("?") || url.endsWith("/"))) {
            url = url.substring(0, url.length() - 1);
        }
        return url.isEmpty() ? null : url;
    }

    /**
     * 候选过滤（引擎专用）：SSRF 防护——拒绝环回/站点本地/链路本地/任意本地地址；
     * 后缀只留配置类（.json/.txt/.m3u——.txt 常被直播源使用，.m3u 支持发现直播清单）。
     * 注意：用户手动配置的 clan://localhost 等本地地址是合法输入，不走本方法。
     */
    public static boolean isAllowedCandidate(String url) {
        try {
            String host = new URI(url.trim()).getHost();
            if (TextUtils.isEmpty(host)) return false;
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) return false;
            String lower = url.toLowerCase();
            return lower.endsWith(".json") || lower.endsWith(".txt")
                    || lower.endsWith(".m3u") || lower.endsWith(".json?");
        } catch (Throwable th) {
            return false;
        }
    }

    /** 点播配置结构校验：顶层 sites 数组或 video.sites（TVBox 标准嵌套）；无 sites 返回 -1 */
    public static int countSites(String json) {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (root.has("sites") && root.get("sites").isJsonArray()) {
                return root.getAsJsonArray("sites").size();
            }
            if (root.has("video") && root.getAsJsonObject("video").has("sites")
                    && root.getAsJsonObject("video").get("sites").isJsonArray()) {
                return root.getAsJsonObject("video").getAsJsonArray("sites").size();
            }
            return -1;
        } catch (Throwable th) {
            return -1;
        }
    }

    /** 直播清单判定：#EXTM3U 开头（容忍前导空白/BOM 由调用方先 trim） */
    public static boolean isLivePlaylist(String body) {
        return body != null && body.trim().startsWith("#EXTM3U");
    }

    /** 从 URL 路径尾段推断名称：https://x.com/conf/m3u8.json → m3u8；失败退回原 URL */
    public static String guessName(String url) {
        try {
            String path = new URI(url).getPath();
            if (!TextUtils.isEmpty(path)) {
                String[] parts = path.split("/");
                String last = parts[parts.length - 1];
                int dot = last.lastIndexOf('.');
                if (dot > 0) last = last.substring(0, dot);
                if (!TextUtils.isEmpty(last)) return last;
            }
        } catch (Throwable ignored) {
        }
        return url;
    }
}
