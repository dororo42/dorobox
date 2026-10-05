package com.github.tvbox.osc.data;

import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.FileUtils;

import java.io.File;
import java.io.IOException;


/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
public class AppDataManager {
    private static final int DB_FILE_VERSION = 3;
    private static final String DB_NAME = "tvbox";
    private static volatile AppDataManager manager;
    private static volatile AppDataBase dbInstance; // m-6/M-1：volatile，close 后置 null 由 get() 重建

    private AppDataManager() {
    }

    public static void init() {
        if (manager == null) {
            synchronized (AppDataManager.class) {
                if (manager == null) {
                    manager = new AppDataManager();
                }
            }
        }
    }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                //database.execSQL("ALTER TABLE sourceState ADD COLUMN tidSort TEXT");
                database.execSQL("CREATE TABLE IF NOT EXISTS `storageDrive` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `type` INTEGER NOT NULL, `configJson` TEXT)");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                //database.execSQL("ALTER TABLE sourceState ADD COLUMN tidSort TEXT");
                database.execSQL("CREATE TABLE IF NOT EXISTS t_search (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, searchKeyWords TEXT)");
                //添加索引
                database.execSQL("CREATE INDEX IF NOT EXISTS index_t_search_searchKeyWords ON t_search (searchKeyWords)");

                /*database.execSQL("CREATE TABLE IF NOT EXISTS t_search_temp (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, searchKeyWords TEXT)");
                // Copy the data
                database.execSQL("INSERT INTO t_search_temp (id, searchKeyWords) SELECT id, searchKeyWords FROM t_search");
                // Remove the old table
                database.execSQL("DROP TABLE t_search");
                // Change the table name to the correct one
                database.execSQL("ALTER TABLE t_search_temp RENAME TO t_search");
                //添加索引
                database.execSQL("CREATE INDEX IF NOT EXISTS index_t_search_searchKeyWords ON t_search (searchKeyWords)");*/
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };
    static String dbPath() {
        return DB_NAME + ".v" + DB_FILE_VERSION + ".db";
    }

    public static AppDataBase get() {
        if (manager == null) {
            throw new RuntimeException("AppDataManager is no init");
        }
        if (dbInstance == null)
            dbInstance = Room.databaseBuilder(App.getInstance(), AppDataBase.class, dbPath())
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
//                        LOG.i("数据库第一次创建成功");
                        }

                        @Override
                        public void onOpen(@NonNull SupportSQLiteDatabase db) {
                            super.onOpen(db);
//                        LOG.i("数据库打开成功");
                        }
                    })// allowMainThreadQueries 已移除（审查报告 P1-4）：所有 DB 访问统一经 DbIo 在 IO 线程执行
                    .build();
        return dbInstance;
    }

    // close+copy 全程放入 DbIo 串行队列，避免与在飞 DB 任务竞态（审查报告第二轮 H-1）
    // M-1：close 后置 null 由 get() 重建——原实现 close 后实例永久失效，备份后收藏/历史静默全灭
    public static boolean backup(final File path) {
        Boolean result = com.github.tvbox.osc.data.DbIo.run(new java.util.concurrent.Callable<Boolean>() {
            @Override
            public Boolean call() {
                if (dbInstance != null && dbInstance.isOpen()) {
                    dbInstance.close();
                }
                dbInstance = null; // 下次 get() 重建，后续 DB 操作不再打到已关闭连接
                File db = App.getInstance().getDatabasePath(dbPath());
                if (db.exists()) {
                    try {
                        FileUtils.copyFile(db, path);
                        return true;
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
                return false;
            }
        });
        return result == Boolean.TRUE;
    }

    /** 校验文件是合法 SQLite 库（16 字节头 "SQLite format 3\0"），防损坏备份覆盖原库。 */
    private static boolean looksLikeSqlite(File f) {
        java.io.DataInputStream in = null;
        try {
            in = new java.io.DataInputStream(new java.io.FileInputStream(f));
            byte[] head = new byte[16];
            if (f.length() < 16) return false;
            in.readFully(head);
            byte[] magic = "SQLite format 3\u0000".getBytes("UTF-8");
            for (int i = 0; i < magic.length; i++) if (head[i] != magic[i]) return false;
            return true;
        } catch (Throwable th) {
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) {}
        }
    }

    /** 删除 SQLite 关联 sidecar 文件（旧 journal/wal 与恢复后的库不匹配会导致损坏）。 */
    private static void deleteSidecars(File db) {
        String[] suffixes = {"-journal", "-wal", "-shm"};
        for (String s : suffixes) {
            File side = new File(db.getAbsolutePath() + s);
            if (side.exists()) side.delete();
        }
    }

    // M-1：restore 原子化——先拷临时文件并校验 SQLite 头，再替换正式库；失败保留原库可回滚
    public static boolean restore(final File path) {
        Boolean result = com.github.tvbox.osc.data.DbIo.run(new java.util.concurrent.Callable<Boolean>() {
            @Override
            public Boolean call() {
                if (dbInstance != null && dbInstance.isOpen()) {
                    dbInstance.close();
                }
                dbInstance = null;
                File db = App.getInstance().getDatabasePath(dbPath());
                if (!db.getParentFile().exists())
                    db.getParentFile().mkdirs();
                File tmp = new File(db.getAbsolutePath() + ".restore-tmp");
                if (tmp.exists()) tmp.delete();
                try {
                    FileUtils.copyFile(path, tmp);
                    if (!looksLikeSqlite(tmp)) {
                        tmp.delete();
                        return false; // 备份文件损坏：原库未动
                    }
                    // 先把原库挪到 .bak 再替换：若 rename 失败走 copy 回退、且 copy 中途失败，
                    // 原库不能已被删（否则收藏/历史/进度全灭且无法回滚）
                    File bak = new File(db.getAbsolutePath() + ".bak");
                    if (bak.exists()) bak.delete();
                    boolean replaced = false;
                    if (db.exists() && !db.renameTo(bak)) {
                        return false; // 原库无法挪开：放弃替换，原库完好
                    }
                    try {
                        if (tmp.renameTo(db)) {
                            replaced = true;
                        } else {
                            // rename 失败（跨文件系统等）：回退为 copy，但已校验过内容
                            FileUtils.copyFile(tmp, db);
                            replaced = true;
                        }
                    } finally {
                        if (replaced) {
                            deleteSidecars(db);
                            if (bak.exists()) bak.delete();
                        } else if (bak.exists() && !db.exists()) {
                            // copy 回退也失败：把原库挪回来
                            if (!bak.renameTo(db)) bak.delete();
                        }
                    }
                    if (tmp.exists()) tmp.delete();
                    return true;
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                    if (tmp.exists()) tmp.delete();
                    return false; // 原库保留
                }
            }
        });
        return result == Boolean.TRUE;
    }
}
