package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 一键验证源对话框：对接口历史（API_HISTORY）最近 10 个配置地址做连通性测试，
 * 3 路并发（yunTV 串行 10×8s 最坏 80s，并发收敛到 ~30s），行内显示
 * 「OK · HTTP 状态 · 耗时 · 站点数」或失败原因。
 * 测的是用户自行配置的地址（可能含 clan://localhost 本地配置），故不做
 * SourceDiscovery.isAllowedCandidate 的 SSRF 过滤；非 http(s) 地址直接标跳过。
 */
public class SourceTestDialog extends BaseDialog {
    private static final int MAX_TARGETS = 10;
    private static final int WORKERS = 3;

    private final TestAdapter adapter;
    private final TextView tvProgress;
    private final ExecutorService pool = Executors.newFixedThreadPool(WORKERS);

    public SourceTestDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_source_test);
        setCanceledOnTouchOutside(true);
        adapter = new TestAdapter();
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);
        tvProgress = findViewById(R.id.tvProgress);
        findViewById(R.id.btnClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        loadTargets();
    }

    private void loadTargets() {
        ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
        List<String> targets = new ArrayList<>();
        for (String url : history) {
            if (targets.size() >= MAX_TARGETS) break;
            if (url != null && !url.trim().isEmpty()) targets.add(url.trim());
        }
        if (targets.isEmpty()) {
            tvProgress.setText("接口历史为空，先在配置里添加地址");
            findViewById(R.id.btnClose).requestFocus();
            return;
        }
        adapter.setItems(targets);
        tvProgress.setText(getContext().getString(com.github.tvbox.osc.R.string.source_test_progress, 0, targets.size()));
        startTests(targets);
    }

    private void startTests(final List<String> targets) {
        final CountDownLatch done = new CountDownLatch(targets.size());
        final OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(8000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(8000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
        for (int i = 0; i < targets.size(); i++) {
            final int index = i;
            final String url = targets.get(i);
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    final String result = probe(client, url);
                    done.countDown();
                    // Adapter 更新必须在主线程；进度与行状态一并刷新
                    App.post(new Runnable() {
                        @Override
                        public void run() {
                            adapter.setStatus(index, result);
                            tvProgress.setText(getContext().getString(com.github.tvbox.osc.R.string.source_test_progress,
                                    targets.size() - (int) done.getCount(), targets.size()));
                        }
                    });
                }
            });
        }
    }

    /** 单地址探测：非 http(s) 跳过；HTTP 状态 + 耗时 + 站点数/直播标记 */
    private String probe(OkHttpClient client, String url) {
        String lower = url.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "跳过（本地/非 HTTP 地址）";
        }
        long start = System.currentTimeMillis();
        Response response = null;
        try {
            response = client.newCall(new Request.Builder().url(url)
                    .header("User-Agent", com.github.tvbox.osc.util.UA.random())
                    .build()).execute();
            long cost = System.currentTimeMillis() - start;
            if (!response.isSuccessful() || response.body() == null) {
                return "失败 · HTTP " + response.code() + " · " + cost + "ms";
            }
            String body = response.body().string();
            if (body.length() > SourceDiscovery.MAX_PAGE_BYTES) body = body.substring(0, SourceDiscovery.MAX_PAGE_BYTES);
            if (SourceDiscovery.isLivePlaylist(body)) {
                return "OK · HTTP " + response.code() + " · " + cost + "ms · 直播清单";
            }
            int sites = SourceDiscovery.countSites(body);
            if (sites > 0) {
                return "OK · HTTP " + response.code() + " · " + cost + "ms · " + sites + " 站点";
            }
            return "失败 · HTTP " + response.code() + " · " + cost + "ms · 非点播/直播配置";
        } catch (Throwable th) {
            long cost = System.currentTimeMillis() - start;
            String reason = th.getMessage();
            return "失败 · " + cost + "ms · " + (reason == null || reason.isEmpty() ? th.getClass().getSimpleName() : reason);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void dismiss() {
        pool.shutdownNow();
        super.dismiss();
    }

    /** 行适配器：状态异步回填 */
    private static class TestAdapter extends RecyclerView.Adapter<TestAdapter.Holder> {
        private final List<String> urls = new ArrayList<>();
        private final List<String> status = new ArrayList<>();

        void setItems(List<String> list) {
            urls.clear();
            urls.addAll(list);
            status.clear();
            for (int i = 0; i < urls.size(); i++) status.add("测试中…");
            notifyDataSetChanged();
        }

        void setStatus(final int index, final String text) {
            synchronized (status) {
                if (index < status.size()) status.set(index, text);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_source_test, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            String url = urls.get(position);
            holder.tvName.setText(SourceDiscovery.guessName(url));
            holder.tvUrl.setText(url);
            String s;
            synchronized (status) {
                s = position < status.size() ? status.get(position) : "";
            }
            holder.tvStatus.setText(s);
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvUrl;
            final TextView tvStatus;

            Holder(@NonNull View view) {
                super(view);
                tvName = view.findViewById(R.id.name);
                tvUrl = view.findViewById(R.id.url);
                tvStatus = view.findViewById(R.id.status);
            }
        }
    }
}
