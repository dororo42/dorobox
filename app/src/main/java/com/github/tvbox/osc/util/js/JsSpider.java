package com.github.tvbox.osc.util.js;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;

import com.whl.quickjs.wrapper.Function;
import com.whl.quickjs.wrapper.JSArray;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.JSUtils;
import com.whl.quickjs.wrapper.QuickJSContext;
import com.whl.quickjs.wrapper.UriUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class JsSpider extends Spider {

    private final ExecutorService executor;
    private final Class<?> dex;
    private QuickJSContext ctx;
    private JSObject jsObject;
    private final String key;
    private final String api;
    private boolean cat;
    // 惰性初始化标记：VM 创建（加载 cheerio/crypto-js/模板等完整 VM）开销大，推迟到首个 JS 调用
    private volatile boolean inited = false;
    private volatile boolean initQueued = false;

    public JsSpider(String key, String api, Class<?> cls) throws Exception {
        this.key = "J" + MD5.encode(key);
        this.executor = Executors.newSingleThreadExecutor();
        this.api = api;
        this.dex = cls;
    }

    /** 首个 JS 调用时才排队初始化 VM；单线程 executor 保证 init 先于任何 JS 调用执行。 */
    private synchronized void ensureInit() {
        // D-6：check-then-act（inited||initQueued 非原子）在首页加载与搜索并发首调时可双重初始化，
        // synchronized 收敛；单线程 executor 内的任务不受影响（同一对象锁仅用于本判定）
        if (inited || initQueued) return;
        initQueued = true;
        try {
            initializeJS();
        } catch (Exception e) {
            initQueued = false;
            throw new RuntimeException(e);
        }
    }
    public void cancelByTag() {
        Connect.cancelByTag("js_okhttp_tag");
    }

    private void submit(Runnable runnable) {
        executor.submit(runnable);
    }

    private <T> Future<T> submit(Callable<T> callable) {
        return executor.submit(callable);
    }

    private Object call(String func, Object... args) {
//        return executor.submit((FunCall.call(jsObject, func, args))).get();
        ensureInit();
        try {
            return submit(() -> Async.run(jsObject, func, args).get()).get();  // 等待 executor 线程完成 JS 调用
        } catch (InterruptedException | ExecutionException e) {
            LOG.i("Executor 提交或等待失败"+ e);
            return null;
        }
    }

    private JSObject cfg(String ext) {
        JSObject cfg = ctx.createJSObject();
        cfg.set("stype", 3);
        cfg.set("skey", key);
        if (Json.invalid(ext)) cfg.set("ext", ext);
        else cfg.set("ext", (JSObject) ctx.parse(ext));
        return cfg;
    }

    @Override
    public void init(Context context, String extend) {
        try {
            if (cat) call("init", submit(() -> cfg(extend)).get());
            else call("init", Json.valid(extend) ? ctx.parse(extend) : extend);
        }catch (Exception e){

        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            return (String) call("home", filter);
        }catch (Exception e){
           return null;
        }
    }

    @Override
    public String homeVideoContent() {
        try {
            return (String) call("homeVod");
        }catch (Exception e){
            return null;
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend)  {
        try {
            ensureInit();
            JSObject obj = submit(() -> new JSUtils<String>().toObj(ctx, extend)).get();
            return (String) call("category", tid, pg, filter, obj);
        }catch (Exception e){
            return null;
        }
    }

    @Override
    public String detailContent(List<String> ids)  {
        try {
            return (String) call("detail", ids.get(0));
        }catch (Exception e){
            return null;
        }
    }

    @Override
    public String searchContent(String key, boolean quick)  {
        try {
            return (String) call("search", key, quick);
        }catch (Exception e){
            return null;
        }
    }
    @Override
    public String searchContent(String key, boolean quick, String pg)  {
        try {
            return (String) call("search", key, quick, pg);
        }catch (Exception e){
            return null;
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            ensureInit();
            JSArray array = submit(() -> new JSUtils<String>().toArray(ctx, vipFlags)).get();
            return (String) call("play", flag, id, array);
        }catch (Exception e){
            return null;
        }
    }

    @Override
    public boolean manualVideoCheck()  {
        try {
            return (Boolean) call("sniffer");
        }catch (Exception e){
            return false;
        }
    }

    @Override
    public boolean isVideoFormat(String url) {
        try {
            return (Boolean) call("isVideo", url);
        }catch (Exception e){
            return false;
        }
    }

    @Override
    public Object[] proxyLocal(Map<String, String> params)  {
        try {
            if ("catvod".equals(params.get("from"))) return proxy2(params);
            else return submit(() -> proxy1(params)).get();

        }catch (Exception E){
            return new Object[0];
        }
    }

    @Override
    public void destroy() {
        submit(() -> {
            executor.shutdownNow();
            ctx.destroy();
        });
    }

    private static final String SPIDER_STRING_CODE = "import * as spider from '%s'\n\n" +
            "if (!globalThis.__JS_SPIDER__) {\n" +
            "    if (spider.__jsEvalReturn) {\n" +
            "        globalThis.req = http\n" +
            "        globalThis.__JS_SPIDER__ = spider.__jsEvalReturn()\n" +
            "        globalThis.__JS_SPIDER__.is_cat = true\n" +
            "    } else if (spider.default) {\n" +
            "        globalThis.__JS_SPIDER__ = typeof spider.default === 'function' ? spider.default() : spider.default\n" +
            "    }\n" +
            "}";
    private void initializeJS() throws Exception {
        submit(() -> {
            if (ctx == null) createCtx();
            if (dex != null) createDex();

            String content = FileUtils.loadModule(api);
            if (TextUtils.isEmpty(content)) {inited = true; return null;}
            
            if(content.startsWith("//bb")){
                cat = true;
                byte[] b = Base64.decode(content.replace("//bb",""), 0);
                ctx.execute(byteFF(b), key + ".js");
                ctx.evaluateModule(String.format(SPIDER_STRING_CODE, key + ".js") + "globalThis." + key + " = __JS_SPIDER__;", "tv_box_root.js");
                //ctx.execute(byteFF(b), key + ".js","__jsEvalReturn");
                //ctx.evaluate("globalThis." + key + " = __JS_SPIDER__;");
            } else {
                if (content.contains("__JS_SPIDER__")) {
                    content = content.replaceAll("__JS_SPIDER__\\s*=", "export default ");
                }
                String moduleExtName = "default";
                if (content.contains("__jsEvalReturn") && !content.contains("export default")) {
                    moduleExtName = "__jsEvalReturn";
                    cat = true;
                }
                // 求值前先 compile 预检：预编译 .so 的 evaluate 在模块求值失败（源 JS 语法错/
                // 触发内存或栈上限）时不判空直接 JS_GetModuleExport → SIGSEGV 杀进程。
                // 语法坏源在此跳过，不进 evaluateModule
                try {
                    ctx.compileModule(content, api);
                } catch (Throwable th) {
                    LOG.e("echo-spider JS compile failed, skip source: " + api + " (" + th.getMessage() + ")");
                    inited = true;
                    return null;
                }
                ctx.evaluateModule(content, api);
                ctx.evaluateModule(String.format(SPIDER_STRING_CODE, api) + "globalThis." + key + " = __JS_SPIDER__;", "tv_box_root.js");
                //ctx.evaluateModule(content, api, moduleExtName);
                //ctx.evaluate("globalThis." + key + " = __JS_SPIDER__;");                
            }
            jsObject = (JSObject) ctx.get(ctx.getGlobalObject(), key);
            inited = true;
            return null;
        }).get();
    }

    public static byte[] byteFF(byte[] bytes) {
        byte[] newBt = new byte[bytes.length - 4];
        newBt[0] = 1;
        System.arraycopy(bytes, 5, newBt, 1, bytes.length - 5);
        return newBt;
    }

    private void createCtx() {
        ctx = QuickJSContext.create();
        // D-5：资源上限——劣质/恶意源 JS 可无限吃 native heap 或深层递归爆栈（进程被 LMK 杀）。
        // 注意：上限一旦命中，模块求值会失败，而预编译 .so 的 evaluate 不判空直接
        // JS_GetModuleExport → SIGSEGV（真机已复现 2 次，均发生在搜索 fan-out 加载
        // js 源时）。64MB/1MB 对 drpy 系源过紧，放宽到 512MB/8MB 仍保持有界
        ctx.setMemoryLimit(512 * 1024 * 1024);     // 512MB
        ctx.setMaxStackSize(8 * 1024 * 1024);      // 8MB（drpy 系源递归较深）
        ctx.setModuleLoader(new QuickJSContext.BytecodeModuleLoader() {
            @Override
            public byte[] getModuleBytecode(String moduleName) {
                String ss = FileUtils.loadModule(moduleName);
                if (TextUtils.isEmpty(ss)) {
                    LOG.i("echo-getModuleBytecode empty :"+ moduleName);
                    return ctx.compileModule("", moduleName);
                }
                if(ss.startsWith("//DRPY")){
                    return Base64.decode(ss.replace("//DRPY",""), Base64.URL_SAFE);
                } else if(ss.startsWith("//bb")){
                    byte[] b = Base64.decode(ss.replace("//bb",""), 0);
                    return byteFF(b);
                } else {
                    if (moduleName.contains("cheerio.min.js")) {
                        FileUtils.setCacheByte("cheerio.min", ctx.compileModule(ss, "cheerio.min.js"));
                    } else if (moduleName.contains("crypto-js.js")) {
                        FileUtils.setCacheByte("crypto-js", ctx.compileModule(ss, "crypto-js.js"));
                    }
                    return ctx.compileModule(ss, moduleName);
                }
            }

            @Override
            public String moduleNormalizeName(String moduleBaseName, String moduleName) {
                return UriUtil.resolve(moduleBaseName, moduleName);
            }
        });
        ctx.setConsole(new QuickJSContext.Console() {
            @Override
            public void log(String s) {
                LOG.i("QuJs"+s);
            }
        });

        ctx.getGlobalObject().bind(new Global(executor));

        JSObject local = ctx.createJSObject();
        ctx.getGlobalObject().set("local", local);
        local.bind(new local());

        ctx.getGlobalObject().getContext().evaluate(FileUtils.loadModule("net.js"));
    }

    private void createDex() {
        try {
            JSObject obj = ctx.createJSObject();
            Class<?> clz = dex;
            Class<?>[] classes = clz.getDeclaredClasses();
            ctx.getGlobalObject().set("jsapi", obj);
            if (classes.length == 0) invokeSingle(clz, obj);
            if (classes.length >= 1) invokeMultiple(clz, obj);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void invokeSingle(Class<?> clz, JSObject jsObj) throws Throwable {
        invoke(clz, jsObj, clz.getDeclaredConstructor(QuickJSContext.class).newInstance(ctx));
    }

    private void invokeMultiple(Class<?> clz, JSObject jsObj) throws Throwable {
        for (Class<?> subClz : clz.getDeclaredClasses()) {
            Object javaObj = subClz.getDeclaredConstructor(clz).newInstance(clz.getDeclaredConstructor(QuickJSContext.class).newInstance(ctx));
            JSObject subObj = ctx.createJSObject();
            invoke(subClz, subObj, javaObj);
            jsObj.set(subClz.getSimpleName(), subObj);
        }
    }

    private void invoke(Class<?> clz, JSObject jsObj, Object javaObj) {
        for (Method method : clz.getMethods()) {
            if (!method.isAnnotationPresent(Function.class)) continue;
            invoke(jsObj, method, javaObj);
        }
    }

    private void invoke(JSObject jsObj, Method method, Object javaObj) {
        jsObj.set(method.getName(), new JSCallFunction() {
            @Override
            public Object call(Object... objects) {
                try {
                    return method.invoke(javaObj, objects);
                } catch (Throwable e) {
                    return null;
                }
            }
        });
    }

    private String getContent() {
        String global = "globalThis." + key;
        String content = FileUtils.loadModule(api);
        if (TextUtils.isEmpty(content)) {return null;}
        if (content.contains("__jsEvalReturn")) {
            ctx.evaluate("req = http");
            return content.concat(global).concat(" = __jsEvalReturn()");
        } else if (content.contains("__JS_SPIDER__")) {
            return content.replace("__JS_SPIDER__", global);
        } else {
            return content.replaceAll("export default.*?[{]", global + " = {");
        }
    }

    private Object[] proxy1(Map<String, String> params) {
        JSObject object = new JSUtils<String>().toObj(ctx, params);
        JSONArray array = ((JSArray) jsObject.getJSFunction("proxy").call(object)).toJsonArray();
        boolean headerAvailable = array.length() > 3 && array.opt(3) != null;
        Object[] result = new Object[4];
        result[0] = array.opt(0);
        result[1] = array.opt(1);
        result[2] = getStream(array.opt(2));
        result[3] = headerAvailable ? getHeader(array.opt(3)) : null;
        if (array.length() > 4) {
            try {
                if ( array.optInt(4) == 1) {
                    String content = array.optString(2);
                    if (content.contains("base64,")) content = content.substring(content.indexOf("base64,") + 7);
                    result[2] = new ByteArrayInputStream(Base64.decode(content, Base64.DEFAULT));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private Map<String, String> getHeader(Object headerRaw) {
        Map<String, String> headers = new HashMap<>();
        if (headerRaw instanceof JSONObject) {
            JSONObject json = (JSONObject) headerRaw;
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                headers.put(key, json.optString(key));
            }
        } else if (headerRaw instanceof String) {
            try {
                JSONObject json = new JSONObject((String) headerRaw);
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    headers.put(key, json.optString(key));
                }
            } catch (JSONException e) {
                LOG.i("getHeader: 无法解析 String 为 JSON"+ e);
            }
        } else if (headerRaw instanceof Map) {
            //noinspection unchecked
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) headerRaw).entrySet()) {
                headers.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return headers;
    }
    
    private Object[] proxy2(Map<String, String> params) throws Exception {
        String url = params.get("url");
        String header = params.get("header");
        JSArray array = submit(() -> new JSUtils<String>().toArray(ctx, Arrays.asList(url.split("/")))).get();
        Object object = submit(() -> ctx.parse(header)).get();
        String json = (String) call("proxy", array, object);
        Res res = Res.objectFrom(json);
        String contentType = res.getContentType();
        if (TextUtils.isEmpty(contentType)) contentType = "application/octet-stream";
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = contentType;
        if (res.getBuffer() == 2) {
            result[2] = new ByteArrayInputStream(Base64.decode(res.getContent(), Base64.DEFAULT));
        } else {
            result[2] = new ByteArrayInputStream(res.getContent().getBytes());
        }
        return result;
    }

   /* private Object[] proxy2(Map<String, String> params) throws Exception {
        String url = params.get("url");
        String header = params.get("header");
        JSArray array = submit(() -> new JSUtils<String>().toArray(ctx, Arrays.asList(url.split("/")))).get();
        Object object = submit(() -> ctx.parse(header)).get();
        String json = (String) call("proxy", array, object);
        Res res = Res.objectFrom(json);
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = "application/octet-stream";
        result[2] = new ByteArrayInputStream(Base64.decode(res.getContent(), Base64.DEFAULT));
        return result;
    }*/

    private ByteArrayInputStream getStream(Object o) {
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            byte[] bytes = new byte[a.length()];
            for (int i = 0; i < a.length(); i++) bytes[i] = (byte) a.optInt(i);
            return new ByteArrayInputStream(bytes);
        } else {
            return new ByteArrayInputStream(o.toString().getBytes());
        }
    }
}
