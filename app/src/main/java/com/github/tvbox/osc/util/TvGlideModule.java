package com.github.tvbox.osc.util;

import android.content.Context;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.MemoryCategory;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.module.AppGlideModule;

/**
 * P-6：项目此前无 AppGlideModule——Glide 内存/磁盘缓存完全按默认值，TV 大量海报并发
 * 场景不可控。按设备内存分档：≥3GB 用 128MB 内存缓存，≥2GB 用 96MB，其余 64MB；
 * 磁盘缓存统一 256MB（默认内部缓存目录，卸载即清）。
 */
@GlideModule
public class TvGlideModule extends AppGlideModule {

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        long maxMem = Runtime.getRuntime().maxMemory();
        int memCacheBytes;
        if (maxMem >= 3L * 1024 * 1024 * 1024) {
            memCacheBytes = 128 * 1024 * 1024;
        } else if (maxMem >= 2L * 1024 * 1024 * 1024) {
            memCacheBytes = 96 * 1024 * 1024;
        } else {
            memCacheBytes = 64 * 1024 * 1024;
        }
        builder.setMemoryCache(new LruResourceCache(memCacheBytes));
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, 256 * 1024 * 1024));
        Log.i("TvGlideModule", "glide memCache=" + (memCacheBytes / 1024 / 1024) + "MB");
    }

    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        // 无自定义 ModelLoader/解码器，保持默认
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
