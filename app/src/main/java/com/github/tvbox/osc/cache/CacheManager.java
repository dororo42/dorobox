package com.github.tvbox.osc.cache;

import com.github.tvbox.osc.data.AppDataManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
public class CacheManager {
    // 写读统一走 DbIo 单线程（与 RoomDataManger/DbHelper 串行化，避免锁竞争）；主线程读有界等待

    //反序列,把二进制数据转换成java object对象
    private static Object toObject(byte[] data) {
        ByteArrayInputStream bais = null;
        ObjectInputStream ois = null;
        try {
            bais = new ByteArrayInputStream(data);
            ois = new ObjectInputStream(bais);
            return ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (bais != null) {
                    bais.close();
                }
                if (ois != null) {
                    ois.close();
                }
            } catch (Exception ignore) {
                ignore.printStackTrace();
            }
        }
        return null;
    }

    //序列化存储数据需要转换成二进制
    private static <T> byte[] toByteArray(T body) {
        ByteArrayOutputStream baos = null;
        ObjectOutputStream oos = null;
        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(body);
            oos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (baos != null) {
                    baos.close();
                }
                if (oos != null) {
                    oos.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new byte[0];
    }

    public static <T> void delete(final String key, T body) {
        final Cache cache = new Cache();
        cache.key = key;
        cache.data = toByteArray(body);
        com.github.tvbox.osc.data.DbIo.post(new Runnable() {
            @Override
            public void run() {
                try {
                    AppDataManager.get().getCacheDao().delete(cache);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        });
    }

    public static <T> void save(final String key, T body) {
        final Cache cache = new Cache();
        cache.key = key;
        cache.data = toByteArray(body);
        com.github.tvbox.osc.data.DbIo.post(new Runnable() {
            @Override
            public void run() {
                try {
                    AppDataManager.get().getCacheDao().save(cache);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        });
    }

    public static Object getCache(final String key) {
        return com.github.tvbox.osc.data.DbIo.run(new java.util.concurrent.Callable<Object>() {
            @Override
            public Object call() {
                Cache cache = AppDataManager.get().getCacheDao().getCache(key);
                if (cache != null && cache.data != null) {
                    return toObject(cache.data);
                }
                return null;
            }
        });
    }
}
