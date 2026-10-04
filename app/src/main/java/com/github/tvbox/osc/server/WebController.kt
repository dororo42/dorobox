package com.github.tvbox.osc.server

import com.github.tvbox.osc.event.RefreshEvent
import com.yanzhenjie.andserver.annotation.GetMapping
import com.yanzhenjie.andserver.annotation.QueryParam
import com.yanzhenjie.andserver.annotation.ResponseBody
import com.yanzhenjie.andserver.annotation.RestController
import org.greenrobot.eventbus.EventBus

@RestController
class WebController {

    @GetMapping(path = ["/index.html", "/api/remote/version"])
    @ResponseBody
    fun hello(): String {
        return "hello"
    }

    @GetMapping("/api/updateUrl")
    @ResponseBody
    fun play(@QueryParam("url") url: String, @QueryParam("token") token: String?): String {
        // 推送可劫持播放内容，须携带有效 token（设置页"局域网免鉴权"开启时豁免）
        if (!ServerToken.lanNoAuth() && !ServerToken.verify(token)) {
            return "forbidden"
        }
        return try {
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_PUSH_URL, url))
            "ok"
        } catch (e: Exception) {
            e.printStackTrace()
            "error:" + e.message
        }
    }

}