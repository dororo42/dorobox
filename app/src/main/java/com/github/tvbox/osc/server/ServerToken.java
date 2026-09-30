package com.github.tvbox.osc.server;

import android.text.TextUtils;

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
}
