package com.github.tvbox.osc.server;

import android.text.TextUtils;

import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.util.UUID;

/**
 * 本地服务（NanoHTTPD:9978 / AndServer:12345）访问令牌。
 * 首次启动生成随机 token 并持久化，用于鉴权文件读写/删除、配置下发等危险接口，
 * 防止局域网内未授权请求劫持配置或任意读写文件。
 */
public class ServerToken {
    private static final String KEY = "local_server_token";
    private static volatile String token;

    public static String get() {
        if (token == null) {
            synchronized (ServerToken.class) {
                if (token == null) {
                    String saved = Hawk.get(KEY, (String) null);
                    if (TextUtils.isEmpty(saved)) {
                        saved = UUID.randomUUID().toString().replace("-", "");
                        Hawk.put(KEY, saved);
                    }
                    token = saved;
                }
            }
        }
        return token;
    }

    public static boolean verify(String candidate) {
        return !TextUtils.isEmpty(candidate) && get().equals(candidate);
    }

    /**
     * 局域网免鉴权开关（设置页"局域网免鉴权"，默认关闭）。
     * 开启后本地服务对所有调用方跳过 token 校验——等同恢复 2026-09-30 加固前的无鉴权行为，
     * 局域网内任意设备可读写删文件/劫持配置，请仅在可信网络临时使用。
     */
    public static boolean lanNoAuth() {
        try {
            return Hawk.get(HawkConfig.LAN_NO_AUTH, false);
        } catch (Throwable th) {
            return false; // Hawk 未初始化等异常场景一律保持鉴权（fail-closed）
        }
    }
}
