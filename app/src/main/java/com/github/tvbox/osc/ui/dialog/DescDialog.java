package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.github.tvbox.osc.R;
import org.jetbrains.annotations.NotNull;

public class DescDialog extends BaseDialog {

    public DescDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_desc);
    }

    public void setDescribe(String describe) {
    	TextView tvDescribe = findViewById(R.id.describe);
        tvDescribe.setText(describe);
        tvDescribe.requestFocus();
        tvDescribe.requestFocusFromTouch();
    }

    // 原 private init() 内含 EventBus.register(this)，但本类无任何 @Subscribe 方法且从未被调用：
    // register 对无订阅方法的类会抛 EventBusException（同 UserFragment 启动崩溃 3425cf086），
    // 属一接线即崩的死代码，直接移除
}
