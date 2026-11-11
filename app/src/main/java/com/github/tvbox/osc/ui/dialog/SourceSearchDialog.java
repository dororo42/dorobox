package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.SourceDiscovery;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自动搜索数据源对话框（设计借鉴 yunTV 种子页发现机制）：
 * 子线程跑 {@link SourceDiscovery#discover}，主线程收结果；
 * 勾选批量写入 HawkConfig.API_HISTORY（去重，上限沿用既有的 20 条）。
 * 不做每日限额（与项目开源定位冲突，yunTV 的 donate_* 限额不搬）。
 */
public class SourceSearchDialog extends BaseDialog {
    private static final long BUDGET_MS = 30000;

    private final SourceAdapter adapter;
    private final TextView btnSearch;
    private final TextView tvStatus;
    private volatile boolean searching = false;

    public SourceSearchDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_source_search);
        setCanceledOnTouchOutside(true);
        adapter = new SourceAdapter();
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);
        tvStatus = findViewById(R.id.tvStatus);
        btnSearch = findViewById(R.id.btnSearch);
        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSearch();
            }
        });
        findViewById(R.id.btnAddSelected).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addSelected();
            }
        });
        findViewById(R.id.btnClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    private void startSearch() {
        if (searching) return;
        searching = true;
        btnSearch.setEnabled(false);
        btnSearch.setText("搜索中…");
        adapter.setItems(new ArrayList<SourceDiscovery.FoundSource>());
        tvStatus.setText("正在扫描种子页…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<SourceDiscovery.FoundSource> found = SourceDiscovery.discover(getContext(), BUDGET_MS);
                App.post(new Runnable() {
                    @Override
                    public void run() {
                        searching = false;
                        btnSearch.setEnabled(true);
                        btnSearch.setText("重新搜索");
                        adapter.setItems(found);
                        if (found.isEmpty()) {
                            tvStatus.setText("未发现可用地址，请再试一次");
                        } else {
                            tvStatus.setText("发现 " + found.size() + " 个地址，勾选后点击「添加所选」");
                        }
                    }
                });
            }
        }, "source-discover").start();
    }

    private void addSelected() {
        List<SourceDiscovery.FoundSource> selected = adapter.getSelected();
        if (selected.isEmpty()) {
            Toast.makeText(getContext(), "请先勾选要添加的源", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
        Set<String> known = new HashSet<>(history);
        int added = 0;
        for (SourceDiscovery.FoundSource src : selected) {
            if (known.add(src.url)) {
                history.add(0, src.url);
                added++;
            }
        }
        while (history.size() > 20) history.remove(20);
        Hawk.put(HawkConfig.API_HISTORY, history);
        Toast.makeText(getContext(), "已添加 " + added + " 个（" + (selected.size() - added) + " 个已存在跳过），可在接口历史中切换", Toast.LENGTH_LONG).show();
        adapter.clearChecked();
    }

    /** 列表适配器：行点击切换勾选（TV 上 OK 键即可操作） */
    private static class SourceAdapter extends RecyclerView.Adapter<SourceAdapter.Holder> {
        private final List<SourceDiscovery.FoundSource> items = new ArrayList<>();
        private final List<Boolean> checked = new ArrayList<>();

        void setItems(List<SourceDiscovery.FoundSource> list) {
            items.clear();
            items.addAll(list);
            checked.clear();
            for (int i = 0; i < items.size(); i++) checked.add(Boolean.FALSE);
            notifyDataSetChanged();
        }

        List<SourceDiscovery.FoundSource> getSelected() {
            List<SourceDiscovery.FoundSource> out = new ArrayList<>();
            for (int i = 0; i < items.size() && i < checked.size(); i++) {
                if (checked.get(i)) out.add(items.get(i));
            }
            return out;
        }

        void clearChecked() {
            for (int i = 0; i < checked.size(); i++) checked.set(i, Boolean.FALSE);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_source_search, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final SourceDiscovery.FoundSource src = items.get(position);
            String liveTag = src.live ? "（直播）" : "（" + src.siteCount + " 站点）";
            holder.tvName.setText(src.name + liveTag);
            holder.tvUrl.setText(src.url);
            holder.check.setChecked(position < checked.size() && checked.get(position));
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int pos = holder.getAdapterPosition();
                    if (pos < 0 || pos >= checked.size()) return;
                    checked.set(pos, !checked.get(pos));
                    holder.check.setChecked(checked.get(pos));
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvUrl;
            final CheckBox check;

            Holder(@NonNull View view) {
                super(view);
                tvName = view.findViewById(R.id.name);
                tvUrl = view.findViewById(R.id.url);
                check = view.findViewById(R.id.check);
            }
        }
    }
}
