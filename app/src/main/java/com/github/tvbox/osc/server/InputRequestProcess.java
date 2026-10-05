package com.github.tvbox.osc.server;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description: 响应按键和输入
 */

public class InputRequestProcess implements RequestProcess {
    private RemoteServer remoteServer;

    public InputRequestProcess(RemoteServer remoteServer) {
        this.remoteServer = remoteServer;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        if (session.getMethod() == NanoHTTPD.Method.POST) {
            switch (fileName) {
                case "/action":
                    return true;
            }
        }
        return false;
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        DataReceiver mDataReceiver = remoteServer.getDataReceiver();
        switch (fileName) {
            case "/action": {
                String action = params.get("do");
                // 缺 do / 未知 do 返回 400 而非 200 "ok"（调用方按状态码处理，监控可探测）
                if (action == null) {
                    return RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "{\"error\":\"missing param do\"}");
                }
                if (mDataReceiver != null) {

                    // 参数校验（M-2）：缺必选参数返回 400 而非 NPE 断连
                    String missing = null;
                    switch (action) {
                        case "search":
                            if (params.get("word") == null) missing = "word";
                            break;
                        case "api":
                        case "live":
                        case "epg":
                        case "proxys":
                        case "push":
                            if (params.get("url") == null) missing = "url";
                            break;
                        case "mirror":
                            if (params.get("id") == null) missing = "id";
                            else if (params.get("sourceKey") == null) missing = "sourceKey";
                            break;
                    }
                    if (missing != null) {
                        return RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                                "{\"error\":\"missing param " + missing + "\"}");
                    }

                    // 高危动作（M-3：mirror 可推送播放内容，与 push 同级，一并纳入）须携带有效 token
                    //（设置页"局域网免鉴权"开启时豁免）
                    if (!ServerToken.lanNoAuth()) {
                        switch (action) {
                            case "api":
                            case "live":
                            case "epg":
                            case "proxys":
                            case "push":
                            case "mirror": {
                                String token = params.get("token");
                                if (token == null) token = session.getHeaders().get("x-token");
                                if (!ServerToken.verify(token)) {
                                    com.github.tvbox.osc.util.LOG.i("auth-denied(action): do=" + action + " lanNoAuth=" + ServerToken.lanNoAuth());
                                    return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.FORBIDDEN, "Forbidden");
                                }
                                break;
                            }
                        }
                    }

                    switch (action) {
                        case "search": {
                            mDataReceiver.onTextReceived(params.get("word").trim());
                            break;
                        }
                        case "api": {
                            mDataReceiver.onApiReceived(params.get("url").trim());
                            break;
                        }
                        case "live": {
                            mDataReceiver.onLiveReceived(params.get("url").trim());
                            break;
                        }
                        case "epg": {
                            mDataReceiver.onEpgReceived(params.get("url").trim());
                            break;
                        }
                        case "proxys": {
                            mDataReceiver.onProxysReceived(params.get("url").trim());
                            break;
                        }
                        case "push": {
                            // 暂未实现
                            mDataReceiver.onPushReceived(params.get("url").trim());
                            break;
                        }
                        case "mirror": {
                            //推送当前电影、电视剧……
                            mDataReceiver.onMirrorReceived(params.get("id").trim(), params.get("sourceKey").trim());
                            return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "mirrored");
                        }
                        // 未知 do 动作
                        default:
                            return RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "{\"error\":\"unknown action\"}");
                    }
                    return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok");
                }
                // receiver 未接线（服务未就绪）
                return RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, "{\"error\":\"no receiver\"}");
            }
            default:
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found.");
        }
    }
}
