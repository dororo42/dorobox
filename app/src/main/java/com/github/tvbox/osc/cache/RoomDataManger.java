package com.github.tvbox.osc.cache;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.data.DbIo;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.StorageDriveType;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author pj567
 * <p>
 * 所有 DAO 访问统一经 DbIo 在 IO 线程执行（allowMainThreadQueries 已移除，审查报告 P1-4）。
 * 写操作 fire-and-forget；读操作主线程有界等待；重负载列表请用 fetchXxx 真异步版本。
 */
public class RoomDataManger {
    static ExclusionStrategy vodInfoStrategy = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesFlags")) {
                return true;
            }
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesMap")) {
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    };

    private static Gson getVodInfoGson() {
        return new GsonBuilder().addSerializationExclusionStrategy(vodInfoStrategy).create();
    }

    public static void insertVodRecord(final String sourceKey, final VodInfo vodInfo) {
        DbIo.post(new Runnable() {
            @Override
            public void run() {
                VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodInfo.id);
                if (record == null) {
                    record = new VodRecord();
                }
                record.sourceKey = sourceKey;
                record.vodId = vodInfo.id;
                record.updateTime = System.currentTimeMillis();
                record.dataJson = getVodInfoGson().toJson(vodInfo);
                AppDataManager.get().getVodRecordDao().insert(record);
            }
        });
    }

    public static VodInfo getVodInfo(final String sourceKey, final String vodId) {
        return DbIo.run(new Callable<VodInfo>() {
            @Override
            public VodInfo call() {
                VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodId);
                try {
                    if (record != null && record.dataJson != null && !TextUtils.isEmpty(record.dataJson)) {
                        VodInfo vodInfo = getVodInfoGson().fromJson(record.dataJson, new TypeToken<VodInfo>() {
                        }.getType());
                        if (vodInfo.name == null)
                            return null;
                        return vodInfo;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        });
    }

    /** 真异步版本：结果回调主线程。 */
    public static void fetchVodInfo(final String sourceKey, final String vodId, final DbIo.Callback<VodInfo> callback) {
        DbIo.fetch(new Callable<VodInfo>() {
            @Override
            public VodInfo call() {
                return getVodInfo(sourceKey, vodId);
            }
        }, callback);
    }

    public static void deleteVodRecord(final String sourceKey, final VodInfo vodInfo) {
        DbIo.post(new Runnable() {
            @Override
            public void run() {
                VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodInfo.id);
                if (record != null) {
                    AppDataManager.get().getVodRecordDao().delete(record);
                }
            }
        });
    }

    public static List<VodInfo> getAllVodRecord(final int limit) {
        return DbIo.run(new Callable<List<VodInfo>>() {
            @Override
            public List<VodInfo> call() {
                return getAllVodRecordSync(limit);
            }
        });
    }

    private static List<VodInfo> getAllVodRecordSync(int limit) {
        // 历史记录超过上限时, 删除最旧的数据.
        int count = AppDataManager.get().getVodRecordDao().getCount();
        Integer index = Hawk.get(HawkConfig.HOME_NUM, 0);
        Integer hisNum = HistoryHelper.getHisNum(index);
        if (count > hisNum) {
            AppDataManager.get().getVodRecordDao().reserver(hisNum);
        }

        List<VodRecord> recordList = AppDataManager.get().getVodRecordDao().getAll(limit);
        List<VodInfo> vodInfoList = new ArrayList<>();
        if (recordList != null) {
            for (VodRecord record : recordList) {
                VodInfo info = null;
                try {
                    if (record.dataJson != null && !TextUtils.isEmpty(record.dataJson)) {
                        info = getVodInfoGson().fromJson(record.dataJson, new TypeToken<VodInfo>() {
                        }.getType());
                        info.sourceKey = record.sourceKey;
                        SourceBean sourceBean = ApiConfig.get().getSource(info.sourceKey);
                        if (sourceBean == null || info.name == null)
                            info = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (info != null)
                    vodInfoList.add(info);
            }
        }
        return vodInfoList;
    }

    /** 真异步版本：历史页整表加载（含 Gson 反序列化），结果回调主线程。 */
    public static void fetchAllVodRecord(final int limit, final DbIo.Callback<List<VodInfo>> callback) {
        DbIo.fetch(new Callable<List<VodInfo>>() {
            @Override
            public List<VodInfo> call() {
                return getAllVodRecordSync(limit);
            }
        }, callback);
    }

    public static void insertVodCollect(final String sourceKey, final VodInfo vodInfo) {
        DbIo.post(new Runnable() {
            @Override
            public void run() {
                VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id);
                if (record != null) {
                    return;
                }
                record = new VodCollect();
                record.sourceKey = sourceKey;
                record.vodId = vodInfo.id;
                record.updateTime = System.currentTimeMillis();
                record.name = vodInfo.name;
                record.pic = vodInfo.pic;
                AppDataManager.get().getVodCollectDao().insert(record);
            }
        });
    }

    public static void deleteVodCollect(final int id) {
        DbIo.post(new Runnable() {
            @Override
            public void run() {
                AppDataManager.get().getVodCollectDao().delete(id);
            }
        });
    }

    public static void deleteVodCollect(final String sourceKey, final VodInfo vodInfo) {
        DbIo.post(new Runnable() {
            @Override
            public void run() {
                VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id);
                if (record != null) {
                    AppDataManager.get().getVodCollectDao().delete(record);
                }
            }
        });
    }

    public static void deleteVodCollectAll() {
        DbIo.post(new Runnable() {
            @Override
            public void run() {
                AppDataManager.get().getVodCollectDao().deleteAll();
            }
        });
    }

    public static void deleteVodRecordAll() {
        DbIo.post(new Runnable() {
            @Override
            public void run() {
                AppDataManager.get().getVodRecordDao().deleteAll();
            }
        });
    }

    public static boolean isVodCollect(final String sourceKey, final String vodId) {
        return DbIo.run(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodId);
                return record != null;
            }
        }) == Boolean.TRUE;
    }

    public static List<VodCollect> getAllVodCollect() {
        return DbIo.run(new Callable<List<VodCollect>>() {
            @Override
            public List<VodCollect> call() {
                return AppDataManager.get().getVodCollectDao().getAll();
            }
        });
    }

    /** 真异步版本：收藏页整表加载，结果回调主线程。 */
    public static void fetchAllVodCollect(final DbIo.Callback<List<VodCollect>> callback) {
        DbIo.fetch(new Callable<List<VodCollect>>() {
            @Override
            public List<VodCollect> call() {
                return AppDataManager.get().getVodCollectDao().getAll();
            }
        }, callback);
    }

    public static void insertDriveRecord(final @NonNull String name, final @NonNull StorageDriveType.TYPE type, final JsonObject config) {
        DbIo.post(new Runnable() {
            @Override
            public void run() {
                StorageDrive drive = new StorageDrive();
                drive.name = name;
                drive.type = type.ordinal();
                drive.configJson = config == null ? null : config.toString();
                AppDataManager.get().getStorageDriveDao().insert(drive);
            }
        });
    }

    public static void updateDriveRecord(final StorageDrive drive) {
        DbIo.post(new Runnable() {
            @Override
            public void run() {
                AppDataManager.get().getStorageDriveDao().insert(drive);
            }
        });
    }

    public static List<StorageDrive> getAllDrives() {
        return DbIo.run(new Callable<List<StorageDrive>>() {
            @Override
            public List<StorageDrive> call() {
                return AppDataManager.get().getStorageDriveDao().getAll();
            }
        });
    }

    public static void deleteDrive(final int id) {
        DbIo.post(new Runnable() {
            @Override
            public void run() {
                AppDataManager.get().getStorageDriveDao().delete(id);
            }
        });
    }
}
