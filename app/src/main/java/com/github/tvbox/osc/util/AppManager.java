package com.github.tvbox.osc.util;

import android.app.Activity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 * P-7：静态强引用栈改 ArrayDeque + synchronized——原 Stack 懒初始化非同步、
 * lastElement() 空栈抛 EmptyStackException、遍历期间修改栈有 CME 风险。
 * 读取方法一律空栈判空返回 null。
 */
public class AppManager {
    private static final Deque<Activity> activityStack = new ArrayDeque<>();

    private AppManager() {
    }

    private static class SingleHolder {
        private static AppManager instance = new AppManager();
    }

    public static AppManager getInstance() {
        return SingleHolder.instance;
    }

    /**
     * 添加Activity到堆栈
     */
    public void addActivity(Activity activity) {
        synchronized (activityStack) {
            activityStack.addLast(activity);
        }
    }

    /**
     * 是否有activity
     */
    public boolean isActivity() {
        synchronized (activityStack) {
            return !activityStack.isEmpty();
        }
    }

    /**
     * 获取当前Activity（堆栈中最后一个压入的）
     */
    public Activity currentActivity() {
        synchronized (activityStack) {
            return activityStack.isEmpty() ? null : activityStack.getLast();
        }
    }

    /**
     * 结束当前Activity（堆栈中最后一个压入的）
     */
    public void finishActivity() {
        Activity activity = currentActivity();
        if (activity != null && !activity.isFinishing()) {
            activity.finish();
        }
    }

    public void finishActivity(Activity activity) {
        synchronized (activityStack) {
            activityStack.remove(activity);
        }
    }

    /**
     * 结束指定类名的Activity
     */
    public void finishActivity(Class<?> cls) {
        synchronized (activityStack) {
            for (Activity activity : activityStack) {
                if (activity.getClass().equals(cls)) {
                    if (!activity.isFinishing()) {
                        activity.finish();
                    }
                    break;
                }
            }
        }
    }

    public void backActivity(Class<?> cls) {
        synchronized (activityStack) {
            while (!activityStack.isEmpty()) {
                Activity activity = activityStack.pollLast();
                if (activity.getClass().equals(cls)) {
                    activityStack.addLast(activity);
                    break;
                } else {
                    activity.finish();
                }
            }
        }
    }

    /**
     * 结束所有Activity
     */
    public void finishAllActivity() {
        synchronized (activityStack) {
            Iterator<Activity> it = activityStack.iterator();
            while (it.hasNext()) {
                Activity activity = it.next();
                if (activity != null && !activity.isFinishing()) {
                    activity.finish();
                }
            }
            activityStack.clear();
        }
    }

    /**
     * 获取指定的Activity
     */
    public Activity getActivity(Class<?> cls) {
        synchronized (activityStack) {
            for (Activity activity : activityStack) {
                if (activity.getClass().equals(cls)) {
                    return activity;
                }
            }
        }
        return null;
    }

    public void appExit(int code) {
        try {
            finishAllActivity();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(code);
        } catch (Exception e) {
            synchronized (activityStack) {
                activityStack.clear();
            }
            e.printStackTrace();
        }
    }
}
