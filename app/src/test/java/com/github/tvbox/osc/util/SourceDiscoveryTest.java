package com.github.tvbox.osc.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

/**
 * 自动寻找源引擎的纯函数回归（设计借鉴 yunTV，实现独立）。
 * 均无 Android 依赖，可直接 JVM 运行；fetch/discover 需要网络与 App 上下文，不在单测范围。
 */
public class SourceDiscoveryTest {

    // ---------- cleanUrl ----------

    @Test
    public void cleanUrlStripsTrailingPunctuation() {
        assertEquals("https://a.com/x.json", SourceDiscovery.cleanUrl("https://a.com/x.json\","));
        assertEquals("https://a.com/x.json", SourceDiscovery.cleanUrl("https://a.com/x.json]}"));
        assertEquals("https://a.com/x.json", SourceDiscovery.cleanUrl("https://a.com/x.json\"},"));
        assertEquals("https://a.com/x.json", SourceDiscovery.cleanUrl("https://a.com/x.json/"));
        assertEquals("https://a.com/x.json", SourceDiscovery.cleanUrl("https://a.com/x.json?"));
        assertEquals("https://a.com/x.json", SourceDiscovery.cleanUrl("https://a.com/x.json.."));
    }

    @Test
    public void cleanUrlTruncatesAtQuote() {
        assertEquals("https://a.com/x.json", SourceDiscovery.cleanUrl("https://a.com/x.json\",\"note\":1"));
    }

    @Test
    public void cleanUrlPurePunctuationReturnsNull() {
        assertNull(SourceDiscovery.cleanUrl("...."));
        assertNull(SourceDiscovery.cleanUrl("\""));
    }

    // ---------- extractUrls ----------

    @Test
    public void extractFromPlainText() {
        Set<String> out = SourceDiscovery.extractUrls("see https://a.com/conf.json and http://b.org/x.txt!");
        assertTrue(out.contains("https://a.com/conf.json"));
        assertTrue(out.contains("http://b.org/x.txt"));
        assertEquals(2, out.size());
    }

    @Test
    public void extractFromEscapedJson() {
        // 配置页里 URL 常被 JSON 转义：https:\/\/a.com\/conf.json
        Set<String> out = SourceDiscovery.extractUrls("{\"u\":\"https:\\/\\/a.com\\/conf.json\"}");
        assertTrue("转义还原后必须能抽出 URL", out.contains("https://a.com/conf.json"));
    }

    @Test
    public void extractDeduplicates() {
        Set<String> out = SourceDiscovery.extractUrls("https://a.com/x.json https://a.com/x.json");
        assertEquals(1, out.size());
    }

    // ---------- isAllowedCandidate ----------

    @Test
    public void allowsPublicConfigUrls() {
        assertTrue(SourceDiscovery.isAllowedCandidate("https://example.com/box.json"));
        assertTrue(SourceDiscovery.isAllowedCandidate("http://example.org/live.txt"));
        assertTrue(SourceDiscovery.isAllowedCandidate("https://example.com/list.m3u"));
    }

    @Test
    public void blocksPrivateAddresses() {
        assertFalse(SourceDiscovery.isAllowedCandidate("https://192.168.1.1/box.json"));
        assertFalse(SourceDiscovery.isAllowedCandidate("https://127.0.0.1/box.json"));
        assertFalse(SourceDiscovery.isAllowedCandidate("https://10.0.0.5/box.json"));
        assertFalse(SourceDiscovery.isAllowedCandidate("https://169.254.1.1/box.json"));
        assertFalse(SourceDiscovery.isAllowedCandidate("https://0.0.0.0/box.json"));
    }

    @Test
    public void blocksNonConfigSuffixes() {
        assertFalse(SourceDiscovery.isAllowedCandidate("https://example.com/video.mp4"));
        assertFalse(SourceDiscovery.isAllowedCandidate("https://example.com/"));
        assertFalse(SourceDiscovery.isAllowedCandidate("not a url"));
    }

    // ---------- countSites ----------

    @Test
    public void countsTopLevelSites() {
        assertEquals(2, SourceDiscovery.countSites("{\"sites\":[{\"key\":\"a\"},{\"key\":\"b\"}],\"spider\":\"x\"}"));
    }

    @Test
    public void countsNestedVideoSites() {
        assertEquals(3, SourceDiscovery.countSites("{\"video\":{\"sites\":[{},{},{}]}}"));
    }

    @Test
    public void returnsMinusOneForInvalidOrMissing() {
        assertEquals(-1, SourceDiscovery.countSites("not json"));
        assertEquals(-1, SourceDiscovery.countSites("{\"lives\":[]}"));
        assertEquals(-1, SourceDiscovery.countSites(""));
    }

    // ---------- isLivePlaylist ----------

    @Test
    public void detectsExtM3u() {
        assertTrue(SourceDiscovery.isLivePlaylist("#EXTM3U\nhttps://x/stream"));
        assertTrue(SourceDiscovery.isLivePlaylist("  #EXTM3U"));
        assertFalse(SourceDiscovery.isLivePlaylist("{\"sites\":[]}"));
        assertFalse(SourceDiscovery.isLivePlaylist(null));
    }

    // ---------- guessName ----------

    @Test
    public void guessesFromPath() {
        assertEquals("m3u8", SourceDiscovery.guessName("https://x.com/conf/m3u8.json"));
        assertEquals("box", SourceDiscovery.guessName("https://x.com/box.json"));
        assertEquals("https://x.com/", SourceDiscovery.guessName("https://x.com/"));
    }
}
