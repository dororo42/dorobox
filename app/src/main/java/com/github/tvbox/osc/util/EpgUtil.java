package com.github.tvbox.osc.util;

import android.content.res.AssetManager;

import com.github.tvbox.osc.base.App;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;

public class EpgUtil {

    private static JsonObject epgDoc = null;
    private static HashMap<String, JsonObject> epgHashMap = new HashMap<>();
    private static volatile boolean inited = false;

    public static void init() {
        if (inited)
            return;
        inited = true;

        // P-8：131KB asset 读取 + Gson 解析移出主线程（冷启动链路）；EPG 非关键路径，未就绪时 getEpgInfo 返回 null
        new Thread(() -> {
            //credit by 龍
            try {
                AssetManager assetManager = App.getInstance().getAssets(); //获得assets资源管理器（assets中的文件无法直接访问，可以使用AssetManager访问）
                InputStreamReader inputStreamReader = new InputStreamReader(assetManager.open("epg_data.json"),"UTF-8"); //使用IO流读取json文件内容
                BufferedReader br = new BufferedReader(inputStreamReader);//使用字符高效流
                String line;
                StringBuilder builder = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    builder.append(line);
                }
                br.close();
                inputStreamReader.close();
                if (!builder.toString().isEmpty()) {
                    JsonObject doc = new Gson().fromJson(builder.toString(), (Type) JsonObject.class);// 从builder中读取了json中的数据。
                    HashMap<String, JsonObject> map = new HashMap<>();
                    // P-9：key 统一小写归一化，避免频道名大小写差异查不到
                    for (JsonElement opt : doc.get("epgs").getAsJsonArray()) {
                        JsonObject obj = (JsonObject) opt;
                        String name = obj.get("name").getAsString().trim().toLowerCase();
                        String[] names = name.split(",");
                        for (String string : names) {
                            map.put(string.trim(), obj);
                        }
                    }
                    epgHashMap = map;
                    epgDoc = doc; // 最后置位，保证看到 doc 时 map 已就绪
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "epg-init").start();
    }

    public static String[] getEpgInfo(String channelName) {
        try {
            if (epgDoc == null || channelName == null) return null;
            // P-9：查询侧同样归一化
            JsonObject obj = epgHashMap.get(channelName.trim().toLowerCase());
            if (obj != null) {
                return new String[]{
                        obj.get("logo").getAsString(),
                        obj.get("epgid").getAsString()
                };
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
