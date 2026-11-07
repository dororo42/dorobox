package com.github.tvbox.osc.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * P-12：MD5.string2MD5 已知向量回归（缓存文件名、jar key 均依赖该实现，变更即坏档）。
 * 注：TextUtils.isEmpty 在 JVM 单测（returnDefaultValues）下恒 false，
 * 空/null 入参分支行为与真机不同，故只覆盖非空输入。
 */
public class MD5Test {

    @Test
    public void knownVectors() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", MD5.string2MD5("abc"));
    }

    @Test
    public void deterministicAcrossCalls() {
        String first = MD5.string2MD5("https://clun.top/box.json");
        assertNotNull(first);
        assertEquals(first, MD5.string2MD5("https://clun.top/box.json"));
    }

    @Test
    public void chineseInputStable() {
        String first = MD5.string2MD5("流人");
        assertNotNull(first);
        assertEquals(first, MD5.string2MD5("流人"));
        assertEquals(32, first.length());
    }
}
