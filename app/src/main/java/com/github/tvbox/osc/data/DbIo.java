package com.github.tvbox.osc.data;

import android.os.Looper;
import android.util.Log;

import com.github.tvbox.osc.base.App;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 统一数据库 IO 门面（配合 AppDataManager 移除 allowMainThreadQueries，审查报告 P1-4）。
 * <p>
 * 规则：
 * - 非主线程调用 run()：IO 线程执行并阻塞取结果。
 * - 主线程调用 run()：仍在 IO 线程执行 SQLite，主线程仅做有界等待（3s），既保证主线程零 DB IO，
 *   又兼容既有同步调用方；典型索引读取耗时毫秒级。
 * - 重负载列表（历史/收藏上百条 + Gson 反序列化）请用 fetch() 真异步：IO 执行、结果回调主线程。
 */
public class DbIo {
    private static final String TAG = "DbIo";
    // P-5：ANR 阈值 5s，主线程一次 3s 等待 + 后续操作即可触发 ANR，收敛到 1s；调用方普遍已有判空
    private static final long MAIN_WAIT_MS = 1000;
    private static final long BG_WAIT_MS = 30000;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    // 记录 executor 线程，用于重入检测：DB 任务内部再调 run() 时直接执行，防止单线程池自死锁
    private static volatile Thread dbThread;

    static {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                dbThread = Thread.currentThread();
                // 哨兵任务：仅标记线程身份，之后 executor 继续正常排队
            }
        });
    }

    public interface Callback<T> {
        void onResult(T result);
    }

    /** 提交无需返回值的写操作。 */
    public static void post(final Runnable task) {
        if (Thread.currentThread() == dbThread) {
            wrap(task).run();
            return;
        }
        EXECUTOR.execute(wrap(task));
    }

    /** 需要返回值的读操作。主线程上最多等待 MAIN_WAIT_MS；其它线程 BG_WAIT_MS 兜底；DB 线程内重入直接执行。 */
    public static <T> T run(final Callable<T> task) {
        // 重入：已在 DB 线程上（如 fetch 任务内部再调用读封装），直接执行防死锁
        if (Thread.currentThread() == dbThread) {
            try {
                return task.call();
            } catch (Exception e) {
                Log.e(TAG, "db op failed (reentrant)", e);
                return null;
            }
        }
        boolean onMain = Looper.myLooper() == Looper.getMainLooper();
        Future<T> future = EXECUTOR.submit(task);
        try {
            return future.get(onMain ? MAIN_WAIT_MS : BG_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            Log.e(TAG, "db op timeout on " + (onMain ? "main" : "background") + " thread", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "db op interrupted", e);
        } catch (ExecutionException | RuntimeException e) {
            Log.e(TAG, "db op failed", e);
        }
        return null;
    }

    /** 真异步读：IO 线程执行，结果回调主线程；callback 可为 null。 */
    public static <T> void fetch(final Callable<T> task, final Callback<T> callback) {
        EXECUTOR.execute(wrap(new Runnable() {
            @Override
            public void run() {
                T result = null;
                try {
                    result = task.call();
                } catch (Throwable th) {
                    Log.e(TAG, "db op failed", th);
                }
                final T r = result;
                if (callback != null) {
                    App.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(r);
                        }
                    });
                }
            }
        }));
    }

    private static Runnable wrap(final Runnable task) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Throwable th) {
                    Log.e(TAG, "db op failed", th);
                }
            }
        };
    }
}
