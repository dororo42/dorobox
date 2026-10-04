package com.github.tvbox.osc.ui.dialog;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.server.ServerToken;
import com.github.tvbox.osc.ui.tv.QRCodeGen;

import org.jetbrains.annotations.NotNull;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 远程控制令牌二维码：二维码内容为带 token 的远控地址（http://<LAN-IP>:9978/?token=xxx），
 * 手机扫码打开 Web 远控页后自动登录令牌；也可手动复制完整 token。
 */
public class ServerTokenDialog extends BaseDialog {
    private final String token;

    public ServerTokenDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_server_token);
        setCanceledOnTouchOutside(false);
        token = ServerToken.get();

        String address = ControlManager.get().getAddress(false) + "?token=" + token;
        ImageView ivQRCode = findViewById(R.id.ivTokenQRCode);
        TextView tvAddress = findViewById(R.id.tvTokenAddress);
        tvAddress.setText(String.format("%s\n令牌：%s", address, token));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address,
                AutoSizeUtils.mm2px(getContext(), 240), AutoSizeUtils.mm2px(getContext(), 240)));

        findViewById(R.id.tvTokenCopy).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("token", token));
            }
            Toast.makeText(getContext(), "令牌已复制：" + token, Toast.LENGTH_LONG).show();
            dismiss();
        });
        findViewById(R.id.tvTokenCopy).requestFocus();
    }
}
