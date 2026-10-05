package com.github.tvbox.osc.util;

import com.github.tvbox.osc.base.App;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P-4/P-11：原实现每次 random() 都打开 657KB 的 ua.db 做随机 seek，且被 ImgUtil
 * 对每张海报调用（主线程）——滑动列表的确定性 IO 卡顿源。现首用时一次性读入
 * 内存轮换池，轮换语义保留（反爬效果等同），调用零 IO。
 */
public class UA {

    private static final int POOL_SIZE = 16;
    private static final List<String> CACHE = new ArrayList<>(POOL_SIZE);
    private static final AtomicInteger IDX = new AtomicInteger();
    private static final String DEFAULT_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36";

    static {
        try {
            InputStream fis = App.getInstance().getAssets().open("ua.db");
            DataInputStream dis = new DataInputStream(fis);
            int len = dis.readInt();
            int count = Math.min(len, POOL_SIZE);
            for (int i = 0; i < count; i++) {
                dis.readInt(); // offset
                CACHE.add(dis.readUTF());
            }
            dis.close();
        } catch (Throwable th) {
            th.printStackTrace();
            CACHE.clear();
        }
    }

    public static String random() {
        if (CACHE.isEmpty()) return DEFAULT_UA;
        int idx = Math.abs(IDX.getAndIncrement() % CACHE.size());
        return CACHE.get(idx);
    }
}
