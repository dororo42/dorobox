// Box JS Spider 最小示例（契约见 ../SPIDER_GUIDE.md）
// 四种文件约定任选其一，本示例用 __JS_SPIDER__ 赋值风格。

const SPIDER = {
    init(cfg) {
        // cfg: 站点 ext 字段内容（字符串），需要对象时自行 JSON.parse
        this.siteUrl = "https://demo.example.com";
    },

    homeContent(filter) {
        return JSON.stringify({
            class: [
                { type_id: 1, type_name: "电影" },
                { type_id: 2, type_name: "剧集" }
            ],
            list: [] // 也可放首页推荐影片，形状同下
        });
    },

    homeVideoContent() {
        return JSON.stringify({ list: [] });
    },

    categoryContent(tid, pg, filter, extend) {
        // 真实源：请求分类页并解析成 MacCMS 列表形状
        const list = [{
            vod_id: "1001",
            vod_name: "示例影片",
            vod_pic: "https://demo.example.com/pic/1001.jpg",
            vod_remarks: "更新至第 1 集"
        }];
        return JSON.stringify({ list: list, page: Number(pg), pagecount: 1, total: list.length });
    },

    detailContent(ids) {
        const id = ids[0];
        return JSON.stringify({
            list: [{
                vod_id: id,
                vod_name: "示例影片",
                vod_play_from: "示例线路",
                vod_play_url: "第01集$https://demo.example.com/play/1001/1#第02集$https://demo.example.com/play/1001/2"
            }]
        });
    },

    searchContent(key, quick, pg) {
        if (quick) return ""; // quick=true 时返回空表示不支持快速搜索
        // 需要分页的源用 pg（可能为 undefined 表示第 1 页）
        return JSON.stringify({ list: [] });
    },

    playerContent(flag, id, vipFlags) {
        // parse:0 直链播放；1 表示交给 app 嗅探
        return JSON.stringify({
            parse: 0,
            url: "https://demo.example.com/stream/" + id + ".m3u8",
            playUrl: "",
            flag: flag
        });
    },

    localProxy(param) {
        return [0, "text/plain", "", ""];
    }
};

// __JS_SPIDER__ = SPIDER;  ← evaluateModule 模板会自动拼接这一行
globalThis.__JS_SPIDER__ = SPIDER;
