package com.github.tvbox.osc.util;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * 全局崩溃处理器：捕获未处理异常，将堆栈与设备信息写入本地日志（保留最近 10 份），
 * 老盒子闪退后可据此取证。记录完毕后交还系统默认处理（保持崩溃行为不变）。
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static final int MAX_LOGS = 10;

    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final Context context;

    private CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void install(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            saveCrashLog(thread, throwable);
        } catch (Throwable ignored) {
        }
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }

    private void saveCrashLog(Thread thread, Throwable throwable) {
        File dir = getCrashDir();
        if (dir == null) return;
        prune(dir);
        String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        File file = new File(dir, "crash_" + time + ".log");
        PrintWriter pw = null;
        try {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            pw = new PrintWriter(new FileWriter(file));
            pw.println("Time: " + new Date());
            pw.println("Thread: " + thread.getName());
            pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            pw.println("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            pw.println("App version: " + getVersionName());
            pw.println();
            pw.print(sw.toString());
        } catch (IOException e) {
            Log.e(TAG, "write crash log failed", e);
        } finally {
            if (pw != null) pw.close();
        }
    }

    private File getCrashDir() {
        try {
            File base = context.getExternalFilesDir(null);
            if (base == null) base = context.getFilesDir();
            File dir = new File(base, "crash");
            if (!dir.exists() && !dir.mkdirs()) return null;
            return dir;
        } catch (Throwable t) {
            return null;
        }
    }

    private void prune(File dir) {
        File[] logs = dir.listFiles();
        if (logs == null || logs.length < MAX_LOGS) return;
        Arrays.sort(logs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < logs.length - MAX_LOGS + 1; i++) logs[i].delete();
    }

    private String getVersionName() {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
