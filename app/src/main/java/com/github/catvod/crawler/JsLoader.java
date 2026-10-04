package com.github.catvod.crawler;


import android.util.Log;

import com.github.tvbox.osc.base.App;

import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;

import com.github.tvbox.osc.util.js.JsSpider;
import com.lzy.okgo.OkGo;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;
import okhttp3.Response;

public class JsLoader {
    private static final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Class<?>> classes = new ConcurrentHashMap<>();
    //当前的Js爬虫key
    private volatile String recentKey = "";

    public static void destroy() {
        for (Spider spider : spiders.values()){
            spider.cancelByTag();
            spider.destroy();
        }
    }
    public void clear() {
        spiders.clear();
        classes.clear();
    }

    public static void stopAll() {
        for (Spider spider : spiders.values()){
            spider.cancelByTag();
        }
    }

    private boolean loadClassLoader(String jar, String key) {
        boolean success = false;
        Class<?> classInit = null;
        try {
            File cacheDir = new File(App.getInstance().getCacheDir().getAbsolutePath() + "/catvod_jsapi");
            if (!cacheDir.exists())
                cacheDir.mkdirs();
            DexClassLoader classLoader = new DexClassLoader(jar, cacheDir.getAbsolutePath(), null, App.getInstance().getClassLoader());
            int count = 0;
            do {
                try {
                    classInit = classLoader.loadClass("com.github.catvod.js.Method");
                    if (classInit != null) {
                        Log.i("JSLoader", "echo-自定义jsapi代码加载成功!");
                        success = true;
                        break;
                    }
                    Thread.sleep(200);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                count++;
            } while (count < 5);

            if (success) {
                classes.put(key, classInit);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return success;
    }

    /** D-7：zip magic（PK\x03\x04）判定。 */
    private static boolean isZipFile(File f) {
        java.io.FileInputStream in = null;
        try {
            in = new java.io.FileInputStream(f);
            return in.read() == 'P' && in.read() == 'K';
        } catch (Throwable th) {
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private Class<?> loadJarInternal(String jar, String md5, String key) {
        if (classes.containsKey(key)){
            Log.i("JSLoader", "echo-loadJarInternal cached");
            return classes.get(key);
        }
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp/" + key + ".jar");
        if (!md5.isEmpty()) {
            if (cache.exists() && MD5.getFileMd5(cache).equalsIgnoreCase(md5)) {
                loadClassLoader(cache.getAbsolutePath(), key);
                return classes.get(key);
            }
        }else {
            if (cache.exists() && !FileUtils.isWeekAgo(cache)) {
                if(loadClassLoader(cache.getAbsolutePath(), key)){
                    return classes.get(key);
                }
            }
        }
        try {
            Response response = OkGo.<File>get(jar).execute();
            // D-7：HTTP 错误状态不得写入缓存
            if (!response.isSuccessful()) {
                Log.e("JSLoader", "jsapi jar download http " + response.code() + ": " + jar);
                return null;
            }
            InputStream is = response.body().byteStream();
            // D-7：临时文件 + 校验 + rename 原子落盘
            File tmp = new File(cache.getAbsolutePath() + ".tmp");
            OutputStream os = new FileOutputStream(tmp);
            try {
                byte[] buffer = new byte[2048];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
            } finally {
                try {
                    is.close();
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // D-7：zip magic 校验
            if (!isZipFile(tmp)) {
                Log.e("JSLoader", "downloaded jsapi jar is not a zip: " + jar);
                tmp.delete();
                return null;
            }
            // fail-closed：声明了 md5 但校验不匹配 → 拒绝执行（防中间人替换 jar）
            if (!md5.isEmpty() && !MD5.getFileMd5(tmp).equalsIgnoreCase(md5)) {
                Log.e("JSLoader", "jsapi jar md5 mismatch, refuse to load: " + jar);
                tmp.delete();
                return null;
            }
            // fail-closed：严格模式下未声明 md5 的 jar 拒绝执行（可在设置中关闭）
            if (md5.isEmpty() && Hawk.get(HawkConfig.JAR_VERIFY_STRICT, false)) {
                Log.e("JSLoader", "jsapi jar without md5 refused in strict mode: " + jar);
                tmp.delete();
                return null;
            }
            if (cache.exists()) cache.delete();
            if (!tmp.renameTo(cache)) {
                FileUtils.copyFile(tmp, cache);
                tmp.delete();
            }
            loadClassLoader(cache.getAbsolutePath(), key);
            return classes.get(key);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        if (spiders.containsKey(key)){
            Log.i("JSLoader", "echo-getSpider cached");
            return spiders.get(key);
        }
        Class<?> classLoader = null;
        if (!jar.isEmpty()) {
            String[] urls = jar.split(";md5;");
            String jarUrl = urls[0];
            String jarKey = MD5.string2MD5(jarUrl);
            String jarMd5 = urls.length > 1 ? urls[1].trim() : "";
            classLoader = loadJarInternal(jarUrl, jarMd5, jarKey);
        }
        recentKey = key;
        try {
            Log.i("JSLoader", "echo-getSpider load");
            Spider sp = new JsSpider(key, api, classLoader);
            sp.init(App.getInstance(), ext);
            spiders.put(key, sp);
            return sp;
        } catch (Throwable th) {
            LOG.i("echo-getSpider-error "+th.getMessage());
        }
        return new SpiderNull();
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            Spider proxyFun = spiders.get(recentKey);
            if (proxyFun != null) {
                return proxyFun.proxyLocal(params);
            }
        } catch (Throwable th) {
        }
        return null;
    }
}
