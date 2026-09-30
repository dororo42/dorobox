package com.github.tvbox.osc.data;


import com.github.tvbox.osc.cache.SearchHistory;

import java.util.ArrayList;

/**
 * creator huangyong
 * createTime 2018/12/17 下午6:39
 * path com.nick.movie.db.dao
 * description:中介类
 */
public class DbHelper {

    // 所有 DAO 访问统一经 DbIo 在 IO 线程执行（allowMainThreadQueries 已移除，审查报告 P1-4）
    public static ArrayList<SearchHistory> getAllHistory() {
        ArrayList<SearchHistory> searchHistories = com.github.tvbox.osc.data.DbIo.run(new java.util.concurrent.Callable<ArrayList<SearchHistory>>() {
            @Override
            public ArrayList<SearchHistory> call() {
                return (ArrayList<SearchHistory>) AppDataManager.get().getSearchDao().getAll();
            }
        });
        if (searchHistories != null && searchHistories.size() > 0) {
            return searchHistories;
        } else {
            return new ArrayList<>();
        }
    }

    public static boolean checkKeyWords(final String keyword) {
        ArrayList<SearchHistory> byKeywords = com.github.tvbox.osc.data.DbIo.run(new java.util.concurrent.Callable<ArrayList<SearchHistory>>() {
            @Override
            public ArrayList<SearchHistory> call() {
                return (ArrayList<SearchHistory>) AppDataManager.get().getSearchDao().getByKeywords(keyword);
            }
        });
        return byKeywords != null && byKeywords.size() > 0;
    }

    public static void addKeywords(final String keyword) {
        com.github.tvbox.osc.data.DbIo.post(new Runnable() {
            @Override
            public void run() {
                ArrayList<SearchHistory> allHistory = (ArrayList<SearchHistory>) AppDataManager.get().getSearchDao().getAll();
                if (allHistory != null && allHistory.size() > 29) {
                    AppDataManager.get().getSearchDao().delete(allHistory.get(0));
                }
                SearchHistory searchHistory = new SearchHistory();
                searchHistory.searchKeyWords = keyword;
                AppDataManager.get().getSearchDao().insert(searchHistory);
            }
        });
    }

    public static void clearKeywords() {
        com.github.tvbox.osc.data.DbIo.post(new Runnable() {
            @Override
            public void run() {
                ArrayList<SearchHistory> allHistory = (ArrayList<SearchHistory>) AppDataManager.get().getSearchDao().getAll();
                if (allHistory != null && allHistory.size() > 0) {
                    for (SearchHistory history : allHistory) {
                        AppDataManager.get().getSearchDao().delete(history);
                    }
                }
            }
        });
    }
}
