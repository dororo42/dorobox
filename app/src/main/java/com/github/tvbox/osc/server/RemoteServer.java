package com.github.tvbox.osc.server;

import static com.github.tvbox.osc.util.RegexUtils.getPattern;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.util.Base64;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.OkGoHelper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.github.tvbox.osc.util.Proxy;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
public class RemoteServer extends NanoHTTPD {
    private Context mContext;
    public static int serverPort = 9978;
    private boolean isStarted = false;
    private DataReceiver mDataReceiver;
    private ArrayList < RequestProcess > getRequestList = new ArrayList < > ();
    private ArrayList < RequestProcess > postRequestList = new ArrayList < > ();
    private static final String PATTERN_ETH_STR = "^eth\\d+$";
    private static final Pattern ETH_PATTERN = Pattern.compile(PATTERN_ETH_STR);
    public static String m3u8Content;
    public static String vodName;
    public static String artist;

    public RemoteServer(int port, Context context) {
        super(port);
        mContext = context;
        addGetRequestProcess();
        addPostRequestProcess();
    }

    private void addGetRequestProcess() {
        getRequestList.add(new RawRequestProcess(this.mContext, "/", R.raw.index, NanoHTTPD.MIME_HTML));
        getRequestList.add(new RawRequestProcess(this.mContext, "/index.html", R.raw.index, NanoHTTPD.MIME_HTML));
        getRequestList.add(new RawRequestProcess(this.mContext, "/style.css", R.raw.style, "text/css"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/ui.css", R.raw.ui, "text/css"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/jquery.js", R.raw.jquery, "application/x-javascript"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/script.js", R.raw.script, "application/x-javascript"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/favicon.ico", R.drawable.app_icon, "image/x-icon"));
    }

    private void addPostRequestProcess() {
        postRequestList.add(new InputRequestProcess(this));
    }

    @Override
    public void start(int timeout, boolean daemon) throws IOException {
        super.start(timeout, daemon);
        isStarted = true; // nit：成功后再置位，启动失败不留残留 true
        EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_SUCCESS));
    }

    @Override
    public void stop() {
        super.stop();
        isStarted = false;
    }

    private Response getProxy(Object[] rs){
        try {
            if (rs[0] instanceof NanoHTTPD.Response) return (NanoHTTPD.Response) rs[0];
            int code = (int) rs[0];
            String mime = (String) rs[1];
            InputStream stream = rs[2] != null ? (InputStream) rs[2] : null;
            Response response = NanoHTTPD.newChunkedResponse(
                    Response.Status.lookup(code),
                    mime,
                    stream
            );
            // 添加头部信息
            if (rs.length >= 4 && rs[3] instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> mapHeader = (Map<String, String>) rs[3];
                if(!mapHeader.isEmpty()){
                    for (String key : mapHeader.keySet()) {
                        response.addHeader(key, mapHeader.get(key));
                    }
                }
            }
            return response;
        } catch (Throwable th) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "500");
        }
    }
    @Override
    public Response serve(IHTTPSession session) {
        // m-4：原每请求 SERVER_CONNECTION 事件唯一订阅者为空方法体，已删除（高频代理场景白耗主线程）
        if (!session.getUri().isEmpty()) {
            String fileName = session.getUri().trim();
            if (fileName.indexOf('?') >= 0) {
                fileName = fileName.substring(0, fileName.indexOf('?'));
            }
            // POST 必须先 parseBody 再鉴权：NanoHTTPD 只在 parseBody 时才把 body/urlencoded
            // 字段合入 parms，此前写端点的 token 若放 body（script.js 正是如此），
            // 鉴权门永远读不到 → 默认配置下全部写操作 403 且页面无提示
            Map < String, String > files = new HashMap < String, String > ();
            if (session.getMethod() == Method.POST) {
                try {
                    if (session.getHeaders().containsKey("content-type")) {
                        String hd = session.getHeaders().get("content-type");
                        if (hd != null) {
                            // cuke: 修正中文乱码问题
                            if (hd.toLowerCase().contains("multipart/form-data") && !hd.toLowerCase().contains("charset=")) {
                                Matcher matcher = getPattern("[ |\t]*(boundary[ |\t]*=[ |\t]*['|\"]?[^\"^'^;^,]*['|\"]?)", Pattern.CASE_INSENSITIVE).matcher(hd);
                                String boundary = matcher.find() ? matcher.group(1) : null;
                                if (boundary != null) {
                                    session.getHeaders().put("content-type", "multipart/form-data; charset=utf-8; " + boundary);
                                }
                            }
                        }
                    }
                    session.parseBody(files);
                } catch (IOException IOExc) {
                    return createPlainTextResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + IOExc.getMessage());
                } catch (NanoHTTPD.ResponseException rex) {
                    return createPlainTextResponse(rex.getStatus(), rex.getMessage());
                }
            }
            // 危险接口（文件读写删/DoH）统一鉴权：token 经 ?token= / X-Token 头 / POST body 传入。
            // GET 类只读端点对本机自身请求豁免（clan:// 内部拉取走 http://<LAN-IP>:9978/file/...，
            // remote IP 为本机自身）；写/删端点始终要求 token，局域网其它设备不受豁免。
            if (isProtected(fileName, session.getMethod()) && !ServerToken.lanNoAuth()) {
                boolean exempt = session.getMethod() == Method.GET && isSelfRequest(session);
                if (!exempt && !ServerToken.verify(getToken(session))) {
                    // 仅记路径前缀，不落完整 /file 路径（文件名属用户隐私，logcat/应用内日志可见）
                    String logUri = fileName.startsWith("/file/") ? "/file/..." : fileName;
                    com.github.tvbox.osc.util.LOG.i("auth-denied: " + session.getRemoteIpAddress() + " " + session.getMethod() + " " + logUri + " lanNoAuth=" + ServerToken.lanNoAuth());
                    return NanoHTTPD.newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Forbidden");
                }
            }
            if (session.getMethod() == Method.GET) {
                for (RequestProcess process: getRequestList) {
                    if (process.isRequest(session, fileName)) {
                        return process.doResponse(session, fileName, session.getParms(), null);
                    }
                }
                if (fileName.equals("/media")) {
                    JSONObject jsonObject = new JSONObject();
                    try {
                        jsonObject.put("title", vodName);
                        jsonObject.put("artist", artist);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, jsonObject.toString());
                }
                if (fileName.equals("/proxy")) {
                    Map < String, String > params = session.getParms();
                    params.putAll(session.getHeaders());
                    params.put("request-headers", new Gson().toJson(session.getHeaders()));
                    if (params.containsKey("do")) {
                        Object[] rs = ApiConfig.get().proxyLocal(params);
                        return getProxy(rs);
                    }
                    if (params.containsKey("go")) {
                        Object[] rs = Proxy.proxy(params);
                        return getProxy(rs);
                    }
                } else if (fileName.startsWith("/file/")) {
                    try {
                        String f = fileName.substring(6);
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        String file = root + "/" + f;
                        if (!isInsideRoot(new File(root), new File(file))) {
                            return NanoHTTPD.newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Forbidden path");
                        }
                        File localFile = new File(file);
                        if (localFile.exists()) {
                            if (localFile.isFile()) {
                                return NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, "application/octet-stream", new FileInputStream(localFile));
                            } else {
                                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, fileList(root, f));
                            }
                        } else {
                            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "File " + file + " not found!");
                        }
                    } catch (Throwable th) {
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, th.getMessage());
                    }
                } else if (fileName.equals("/dns-query")) {
                    String name = session.getParms().get("name");
                    byte[] rs = null;
                    try {
                        rs = OkGoHelper.dnsOverHttps.lookupHttpsForwardSync(name);
                    } catch (Throwable th) {
                        rs = new byte[0];
                    }
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/dns-message", new ByteArrayInputStream(rs), rs.length);
                } else if (fileName.equals("/m3u8")) {
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, m3u8Content);
                } else if (fileName.startsWith("/dash/")) {
                    String dashData = App.getInstance().getDashData();
                    try {
                        String data = new String(Base64.decode(dashData, Base64.DEFAULT | Base64.NO_WRAP), "UTF-8");
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/dash+xml", data);
                    } catch (Throwable th) {
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, dashData);
                    }
                }
            } else if (session.getMethod() == Method.POST) {
                // body 已在 serve 入口统一 parseBody（鉴权门需要读 body token），此处直接复用
                for (RequestProcess process: postRequestList) {
                    if (process.isRequest(session, fileName)) {
                        return process.doResponse(session, fileName, session.getParms(), files);
                    }
                }
                try {
                    Map < String, String > params = session.getParms();
                    // 空参数统一 400（缺 path 时 root+"/"+null 会拼出 "null" 目录，禁止落到文件操作）
                    if (params.get("path") == null || (fileName.equals("/newFolder") && params.get("name") == null)) {
                        return NanoHTTPD.newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Bad Request: missing path/name");
                    }
                    if (fileName.equals("/upload")) {
                        String path = params.get("path");
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        for (String k: files.keySet()) {
                            if (k.startsWith("files-")) {
                                String fn = params.get(k);
                                String tmpFile = files.get(k);
                                File tmp = new File(tmpFile);
                                File file = new File(root + "/" + path + "/" + fn);
                                if (!isInsideRoot(new File(root), file) || !isInsideRoot(new File(root), new File(root + "/" + path))) {
                                    return NanoHTTPD.newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Forbidden path");
                                }
                                if (file.exists()) file.delete();
                                if (tmp.exists()) {
                                    if (fn.toLowerCase().endsWith(".zip")) {
                                        unzip(tmp, root + "/" + path);
                                    } else {
                                        FileUtils.copyFile(tmp, file);
                                    }
                                }
                                if (tmp.exists()) tmp.delete();
                            }
                        }
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                    } else if (fileName.equals("/newFolder")) {
                        String path = params.get("path");
                        String name = params.get("name");
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        File file = new File(root + "/" + path + "/" + name);
                        // isRoot：path="" & name="" 时 canonical==根，会在存储根落 .tvbox_folder 标记（与 D-1 同口径）
                        if (!isInsideRoot(new File(root), file) || isRoot(file)) {
                            return NanoHTTPD.newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Forbidden path");
                        }
                        if (!file.exists()) {
                            file.mkdirs();
                            File flag = new File(root + "/" + path + "/" + name + "/.tvbox_folder");
                            if (!flag.exists()) flag.createNewFile();
                        }
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                    } else if (fileName.equals("/delFolder")) {
                        String path = params.get("path");
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        File file = new File(root + "/" + path);
                        // D-1：写/删端点严禁目标==根目录（根豁免仅限 GET /file 列表），
                        // 否则 path 为空时 recursiveDelete(root) 会删除整个外部存储
                        if (!isInsideRoot(new File(root), file) || isRoot(file)) {
                            return NanoHTTPD.newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Forbidden path");
                        }
                        if (file.exists()) {
                            FileUtils.recursiveDelete(file);
                        }
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                    } else if (fileName.equals("/delFile")) {
                        String path = params.get("path");
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        File file = new File(root + "/" + path);
                        if (!isInsideRoot(new File(root), file) || isRoot(file)) {
                            return NanoHTTPD.newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Forbidden path");
                        }
                        if (file.exists()) {
                            file.delete();
                        }
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                    }
                } catch (Throwable th) {
                    // M-4/N-3：文件操作失败不得伪报 "OK"；getMessage 可能为 null，兜底类名
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error: " + (th.getMessage() != null ? th.getMessage() : th.getClass().getSimpleName()));
                }
            }
        }
        //default page: index.html
        return getRequestList.get(0).doResponse(session, "", null, null);
    }

    public void setDataReceiver(DataReceiver receiver) {
        mDataReceiver = receiver;
    }

    public DataReceiver getDataReceiver() {
        return mDataReceiver;
    }

    public boolean isStarting() {
        return isStarted;
    }

    public String getServerAddress() {
        String ipAddress = getLocalIPAddress(mContext);
        return "http://" + ipAddress + ":" + RemoteServer.serverPort + "/";
    }

    public String getLoadAddress() {
        return "http://127.0.0.1:" + RemoteServer.serverPort + "/";
    }

    public static Response createPlainTextResponse(Response.IStatus status, String text) {
        return newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, text);
    }

    public static Response createJSONResponse(Response.IStatus status, String text) {
        return newFixedLengthResponse(status, "application/json", text);
    }

    /** 危险接口清单：读文件/DoH(GET) 与 上传/建删目录/删文件(POST)。 */
    private static boolean isProtected(String fileName, Method method) {
        if (method == Method.GET) {
            return fileName.startsWith("/file/") || fileName.equals("/dns-query");
        } else if (method == Method.POST) {
            return fileName.equals("/upload") || fileName.equals("/newFolder")
                    || fileName.equals("/delFolder") || fileName.equals("/delFile");
        }
        return false;
    }

    private static String getToken(IHTTPSession session) {
        String token = session.getParms().get("token");
        if (token == null) token = session.getHeaders().get("x-token");
        return token;
    }

    /** 是否为本机自身发起的请求（loopback 或源 IP == 本机 LAN IP）。 */
    private boolean isSelfRequest(IHTTPSession session) {
        try {
            String remote = session.getRemoteIpAddress();
            if (remote == null) return false;
            if (remote.equals("127.0.0.1") || remote.equals("::1") || remote.equals("0:0:0:0:0:0:0:1")) return true;
            String local = getLocalIPAddress(mContext);
            return !"0.0.0.0".equals(local) && local.equals(remote);
        } catch (Throwable th) {
            return false;
        }
    }

    /** canonical path 包含校验：目标必须位于 root 内部，防 `..` 越界读写删。 */
    private static boolean isInsideRoot(File root, File target) {
        try {
            // 根目录本身视为合法（网页文件管理列根目录 listFile('') 场景）；`..` 越界仍拒绝。
            // 注意：此豁免仅供 GET /file 列表使用，写/删端点须另加 isRoot() 拒绝==root（D-1）
            String rootPath = root.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            return targetPath.equals(rootPath) || targetPath.startsWith(rootPath + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    /** D-1：写/删端点专用——目标即外部存储根目录时必须拒绝，防 path 为空时递归删除整个存储。 */
    private static boolean isRoot(File target) {
        try {
            return target.getCanonicalPath().equals(
                    Environment.getExternalStorageDirectory().getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    @SuppressLint("DefaultLocale")
    public static String getLocalIPAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        if (ipAddress == 0) {
            try {
                Enumeration < NetworkInterface > enumerationNi = NetworkInterface.getNetworkInterfaces();
                while (enumerationNi.hasMoreElements()) {
                    NetworkInterface networkInterface = enumerationNi.nextElement();
                    String interfaceName = networkInterface.getDisplayName();
                    if (ETH_PATTERN.matcher(interfaceName).matches() || interfaceName.equals("wlan0")) {
                        Enumeration < InetAddress > enumIpAddr = networkInterface.getInetAddresses();
                        while (enumIpAddr.hasMoreElements()) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                return inetAddress.getHostAddress();
                            }
                        }
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            }
        } else {
            return String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        }
        return "0.0.0.0";
    }

    String fileTime(long time, String fmt) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        Date date = calendar.getTime();
        SimpleDateFormat sdf = new SimpleDateFormat(fmt);
        return sdf.format(date);
    }

    String fileList(String root, String path) {
        File file = new File(root + "/" + path);
        File[] list = file.listFiles();
        JsonObject info = new JsonObject();
        info.addProperty("remote", getServerAddress().replace("http://", "clan://"));
        info.addProperty("del", 0);
        if (path.isEmpty()) {
            info.addProperty("parent", ".");
        } else {
            info.addProperty("parent", file.getParentFile().getAbsolutePath().replace(root + "/", "").replace(root, ""));
        }
        if (list == null || list.length == 0) {
            info.add("files", new JsonArray());
            return info.toString();
        }
        Arrays.sort(list, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                if (o1.isDirectory() && o2.isFile()) return -1;
                return o1.isFile() && o2.isDirectory() ? 1 : o1.getName().compareTo(o2.getName());
            }
        });
        JsonArray result = new JsonArray();
        for (File f: list) {
            if (f.getName().startsWith(".")) {
                if (f.getName().equals(".tvbox_folder")) {
                    info.addProperty("del", 1);
                }
                continue;
            }
            JsonObject fileObj = new JsonObject();
            fileObj.addProperty("name", f.getName());
            fileObj.addProperty("path", f.getAbsolutePath().replace(root + "/", ""));
            fileObj.addProperty("time", fileTime(f.lastModified(), "yyyy/MM/dd aHH:mm:ss"));
            fileObj.addProperty("dir", f.isDirectory() ? 1 : 0);
            result.add(fileObj);
        }
        info.add("files", result);
        return info.toString();
    }

    void unzip(File zipFilePath, String destDirectory) throws Throwable {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        // N-2：ZipFile 用 try-with-resources 关闭（句柄泄漏），并加解压总量上限（zip bomb，有 token 保护、兜底）
        // 上限按 extractFile 实际写入字节计：entry.getSize() 是 zip 中央目录自报值，攻击者可控（可为 -1），
        // 用它计数上限形同虚设
        long totalBytes = 0;
        final long MAX_UNZIP_BYTES = 512L * 1024 * 1024;
        try (ZipFile zip = new ZipFile(zipFilePath)) {
        Enumeration < ZipEntry > iter = (Enumeration < ZipEntry > ) zip.entries();
        String destRoot = new File(destDirectory).getCanonicalPath() + File.separator;
        while (iter.hasMoreElements()) {
            ZipEntry entry = iter.nextElement();
            InputStream is = zip.getInputStream(entry);
            String filePath = destDirectory + File.separator + entry.getName();
            // zip-slip 防护：拒绝解压到目标目录之外的条目
            if (!new File(filePath).getCanonicalPath().startsWith(destRoot)) {
                continue;
            }
            if (!entry.isDirectory()) {
                totalBytes += extractFile(is, filePath);
                if (totalBytes > MAX_UNZIP_BYTES) {
                    throw new Throwable("unzip exceeds size limit (zip bomb?)");
                }
            } else {
                File dir = new File(filePath);
                if (!dir.exists()) dir.mkdirs();
                File flag = new File(dir + "/.tvbox_folder");
                if (!flag.exists()) flag.createNewFile();
            }
        }
        }
    }

    /** @return 实际写入字节数（供 zip-bomb 上限计数）。 */
    long extractFile(InputStream inputStream, String destFilePath) throws Throwable {
        File dst = new File(destFilePath);
        if (dst.exists()) dst.delete();
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destFilePath));
        byte[] bytesIn = new byte[2048];
        int len;
        long total = 0;
        try {
            len = inputStream.read(bytesIn);
            while (len > 0) {
                bos.write(bytesIn, 0, len);
                total += len;
                len = inputStream.read(bytesIn);
            }
        } finally {
            bos.close();
        }
        return total;
    }

}
