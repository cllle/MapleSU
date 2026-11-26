/* ══════════════════════════════════════════════════════════════════
   MapleSU Ultra 网页管理器 — 页面逻辑（Ultra 模式适配版）
   ──────────────────────────────────────────────────────────────────
   来源：7kimisu 网页管理器（KernelSU / 7kimisu userspace/ksud/assets/webadmin/app.js，
        GPL-3.0-or-later）。本文件按其许可再分发；完整许可原文见
        a/licenses/KernelSU-GPL-3.0.txt，来源与改动清单见
        docs/development/lite-webadmin-frontend-adaptation.md。
   适配摘要：
     · 前缀 /<prefix>/<token> 由 location.pathname 解析（LiteWebHandler 固定 /x7k9f/<64hex>/…）；
     · /api/status 使用 Lite 字段（mode/root/moduleCount/hidden/uapi/serverVersion），
       UAPI / 内核版本 / KMI / ksud 等 KernelSU 专属行不再展示；
     · /api/modules 为数组；启停走 POST /api/module/toggle（JSON body）；
     · /api/superuser 按真实 capabilities 提供包名全局授权与环境日志；
     · 未实现的写接口不展示入口；变更立即反馈，并回读确认真实状态。
   ──────────────────────────────────────────────────────────────────
   安全约定（改这个文件时别破坏）：
     · 所有动态数据（模块名/说明/App 名…）一律用 textContent 写入，
       绝不拼进 innerHTML —— 那些内容来自第三方 module.prop，属于不可信数据。
       本文件里的 innerHTML 只用于「我自己写的静态模板」。
     · 所有请求都只发往同源（127.0.0.1），页面没有任何外部地址。
   ══════════════════════════════════════════════════════════════════ */
"use strict";// 🔐 访问鉴权（2026-09-18）：地址里必须带**专属密钥**（`{PATH}/{token}`）。
// 服务端把带密钥的完整前缀注入到下面的 P 里，所以本文件所有请求天然带密钥 ——
// 本文件里**不要**自己拼 /x7k9f 或任何不带密钥的地址。
var LITE_MODE = true;     // 本资源包只用于 MapleSU_Ultra 网页管理器
// 路径前缀 = /<prefix>/<token>（LiteWebHandler 固定 /x7k9f/<64hex token>/...）。
// Lite 服务端不注入服务端模板变量，从 location.pathname 的前两段解析；宿主也可预设。
var P = (function () {
  if (typeof window.__MAPLESU_WEB_PREFIX__ === "string") return window.__MAPLESU_WEB_PREFIX__;
  var parts = location.pathname.split("/").filter(Boolean);
  return parts.length >= 2 ? "/" + parts[0] + "/" + parts[1] : "";
})();
var NS = "http://www.w3.org/2000/svg";

var state = {
  tab: "home",
  modules: [],
  search: "",
  sort: "default",
  theme: "auto",
  style: "miuix",
  busy: false,
  // 超级用户
  apps: [],
  appSearch: "",
  appFilter: "all",     // all | granted | plain | system
  // 超级用户日志
  sulog: [],
  sulogQuery: "",
  sulogEnabled: false,
  sulogSupported: true,
  sulogAvailable: null,
  sulogReason: "",
  status: null
};

/* ── DOM 小工具 ── */
function $(sel, root) { return (root || document).querySelector(sel); }
function el(tag, cls, text) {
  var n = document.createElement(tag);
  if (cls) n.className = cls;
  if (text != null) n.textContent = text;   // ⚠️ 只用 textContent，防 XSS
  return n;
}
function ic(name, cls) {
  var svg = document.createElementNS(NS, "svg");
  svg.setAttribute("class", cls || "ic");
  var use = document.createElementNS(NS, "use");
  use.setAttribute("href", "#i-" + name);
  svg.appendChild(use);
  return svg;
}
function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); }

/* ── 请求 ──
   ⚠️ 查询参数一律通过 params 传，**绝不要自己往 api 里塞 "?xxx"**。
   踩过的坑（v0.13.99 真机）：给 get() 传了 "/api/app/profile?pkg=x&uid=1"，
   而 get() 在末尾拼 "?k=口令"（当时还有口令）→ 变成 "...?pkg=x&uid=1?k=token"，
   uid 被当成 "1?k=token" 解析失败 → 「配置」按钮点了没反应、日志页也打不开。
   现在统一由 qs() 拼，结构上不可能再拼错。 */
function qs(params) {
  if (!params) return "";
  return Object.keys(params).filter(function (k) {
    return params[k] !== undefined && params[k] !== null;
  }).map(function (k) {
    return "&" + encodeURIComponent(k) + "=" + encodeURIComponent(params[k]);
  }).join("");
}
function apiUrl(api, params) {
  var extra = qs(params);            // 形如 "&pkg=x&uid=1"
  return P + api + (extra ? "?" + extra.slice(1) : "");
}
function get(api, params) {
  return fetchTimed(apiUrl(api, params), { cache: "no-store" }).then(function (r) {
    if (!r.ok) throw httpError(r.status);
    return r.json().then(MapleSUInput);
  });
}
function fetchTimed(url, options) {
  var controller = new AbortController();
  var timer = setTimeout(function () { controller.abort(); }, 15000);
  options = Object.assign({}, options || {}, { signal: controller.signal });
  return fetch(url, options).finally(function () { clearTimeout(timer); });
}
function post(api, params) {
  var body = Object.keys(params).map(function (k) {
    return encodeURIComponent(k) + "=" + encodeURIComponent(params[k]);
  }).join("&");
  return fetch(apiUrl(api), {
    method: "POST", cache: "no-store",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body
  }).then(function (r) {
    if (!r.ok) throw httpError(r.status);
    return r.json().then(MapleSUInput);
  });
}

/* ── Ultra 模式 HTTP 语义 ──
   LiteWebHandler 只实现 GET status/modules/superusers/sulog + POST module/toggle；
   其它写接口统一 501 + 原因。页面必须显式显示「Ultra 模式不支持」而不是报错/空白。 */
function httpError(status) {
  var err = new Error("HTTP " + status);
  err.status = status;
  err.unsupported = (status === 501 || status === 503);
  return err;
}
function isUnsupported(e) {
  return !!(e && (e.unsupported === true || e.status === 501 || e.status === 503));
}
function apiErrorText(e) {
  if (e && e.message) console.warn("[webui]", e.message);
  return isUnsupported(e) ? "当前网页暂不支持此功能" : "操作没有成功，请稍后重试";
}
function unsupportedCard(what, reason) {
  if (reason) console.warn("[webui]", what || "", reason);   // 原因只进日志，界面只说人话
  var card = el("div", "empty");
  card.appendChild(ic("triangle-alert"));
  card.appendChild(el("div", null, "当前网页暂不支持" + (what ? "：" + what : "")));
  return card;
}
function showUnsupported(box, what, reason) {
  clear(box);
  box.appendChild(unsupportedCard(what, reason));
}
function postJson(api, obj) {
  return fetchTimed(P + api, {
    method: "POST", cache: "no-store",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(obj || {})
  }).then(function (r) {
    if (!r.ok) throw httpError(r.status);
    return r.json().then(MapleSUInput);
  });
}

/* ── Toast ── */
var toastTimer = null;
function toast(msg) {
  var t = $("#toast");
  t.textContent = msg;
  t.classList.add("on");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(function () { t.classList.remove("on"); }, 2400);
}

/* ── 底部弹层 ── */
var sheetBackPending = false;
var tabAfterSheetClose = null;
function popSheetHistory() {
  if (!sheetBackPending && history.state && history.state.maplesu && history.state.sheet) {
    sheetBackPending = true;
    history.back();
  }
}
function closeSheet() {
  var open = $("#mask").classList.contains("on");
  $("#mask").classList.remove("on");
  if (open) popSheetHistory();
}
function openSheet(title, bodyNode, actions, full) {
  var sheet = $("#sheet");
  clear(sheet);
  sheet.classList.toggle("full", !!full);
  sheet.appendChild(el("div", "grip"));
  if (title) sheet.appendChild(el("h2", null, title));
  if (bodyNode) sheet.appendChild(bodyNode);
  if (actions && actions.length) {
    var btns = el("div", "btns");
    actions.forEach(function (a) {
      var b = el("button", "btn" + (a.cls ? " " + a.cls : ""), a.label);
      b.onclick = function () {
        if (a.keepOpen !== true) $("#mask").classList.remove("on");
        a.onClick && a.onClick();
        // A chained confirmation replaces the current sheet, not a second history entry.
        if (a.keepOpen !== true && !$("#mask").classList.contains("on")) popSheetHistory();
      };
      btns.appendChild(b);
    });
    sheet.appendChild(btns);
  }
  var alreadyOpen = history.state && history.state.maplesu && history.state.sheet;
  if (!alreadyOpen && !sheetBackPending) history.pushState({ maplesu: true, tab: state.tab, sheet: true }, "", location.href);
  $("#mask").classList.add("on");
}
function confirmSheet(title, body, okLabel, onOk) {
  openSheet(title, el("div", "body", body), [
    { label: "取消" },
    { label: okLabel, cls: "danger", onClick: onOk }
  ]);
}

/* 一排可以选中的过滤标签（筛选条件 / 条数） */
function chipsRow(container, items, current, onPick) {
  var row = el("div", "chips");
  items.forEach(function (c) {
    var b = el("button", "chip" + (current() === c.id ? " on" : ""), c.label);
    b.onclick = function () {
      var all = row.querySelectorAll(".chip");
      for (var i = 0; i < all.length; i++) all[i].classList.remove("on");
      b.classList.add("on");
      onPick(c.id);
    };
    row.appendChild(b);
  });
  container.appendChild(row);
  return row;
}

/* ── 主题 ── */
var THEMES = [
  { id: "auto",     name: "跟随系统" },
  { id: "7k",       name: "MapleSU 深色" },
  { id: "7k-light", name: "MapleSU 浅色" },
  { id: "mocha",    name: "Catppuccin Mocha" },
  { id: "latte",    name: "Catppuccin Latte" },
  { id: "nord",     name: "Nord" },
  { id: "dracula",  name: "Dracula" }
];
var PROBE = { mocha: ["#1e1e2e", "#cba6f7", "#a6e3a1"], latte: ["#eff1f5", "#8839ef", "#40a02b"],
              nord: ["#2e3440", "#88c0d0", "#a3be8c"], dracula: ["#282a36", "#bd93f9", "#50fa7b"],
              "7k": ["#000000", "#277af7", "#36d167"], "7k-light": ["#f7f7f7", "#3482ff", "#1a9e5c"],
              auto: ["#000000", "#3482ff", "#36d167"] };

function themeName(id) {
  for (var i = 0; i < THEMES.length; i++) if (THEMES[i].id === id) return THEMES[i].name;
  return "";
}

function applyTheme() {
  var id = state.theme;
  var html = document.documentElement;
  var scheme, palette;
  if (id === "auto") {
    scheme = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    palette = "7k";
  } else if (id === "7k-light") {
    scheme = "light";
    palette = "7k";
  } else {
    // latte 是浅色，其余是深色
    scheme = (id === "latte") ? "light" : "dark";
    palette = id;
  }
  html.setAttribute("data-style", state.style);
  html.setAttribute("data-theme", palette);
  html.setAttribute("data-scheme", scheme);
  // 浏览器状态栏/地址栏跟页面同色，不留一条异色带
  var meta = document.querySelector('meta[name="theme-color"]');
  if (meta) {
    var bg = "";
    try { bg = getComputedStyle(html).getPropertyValue("--bg").trim(); } catch (e) {}
    if (bg) meta.setAttribute("content", bg);
  }
  try { localStorage.setItem("7k-webadmin-theme", id); localStorage.setItem("maplesu-web-style", state.style); } catch (e) {}
}

function themeSheet() {
  var box = el("div");
  var styles = el("div", "style-options");
  [{ id: "miuix", title: "Miuix", summary: "细腻通透，轻盈有质感" },
   { id: "material", title: "Material", summary: "简洁克制，专注内容" }].forEach(function (style) {
    var option = el("button", "style-option" + (state.style === style.id ? " on" : ""));
    option.appendChild(el("strong", null, style.title));
    option.appendChild(el("span", null, style.summary));
    option.setAttribute("aria-pressed", String(state.style === style.id));
    option.onclick = function () {
      state.style = style.id; applyTheme();
      styles.querySelectorAll("button").forEach(function (button) {
        var active = button === option;
        button.classList.toggle("on", active); button.setAttribute("aria-pressed", String(active));
      });
    };
    styles.appendChild(option);
  });
  box.appendChild(styles);
  box.appendChild(el("div", "small-title", "配色"));
  THEMES.forEach(function (t) {
    var opt = el("button", "theme-opt" + (state.theme === t.id ? " on" : ""));
    var sw = el("div", "swatches");
    (PROBE[t.id] || PROBE["7k"]).forEach(function (c) {
      var i = el("i");
      i.style.background = c;
      sw.appendChild(i);
    });
    opt.appendChild(sw);
    opt.appendChild(el("div", "nm", t.name));
    if (state.theme === t.id) opt.appendChild(ic("check"));
    opt.onclick = function () {
      state.theme = t.id; applyTheme(); closeSheet(); toast("配色：" + t.name);
      if (settingsState.loaded) renderSettings();
    };
    box.appendChild(opt);
  });
  openSheet("配色", box, [{ label: "关闭" }]);
}

/* ── 状态页 ── */
function badge(text, kind, iconName) {
  var b = el("span", "badge b-" + kind);
  if (iconName) b.appendChild(ic(iconName, "ic"));
  b.appendChild(el("span", null, text));
  return b;
}
function row(iconName, key, value, isBadge, kind) {
  var show = value == null || value === "" ? "—" : String(value);
  return pref({
    icon: iconName, title: key,
    value: isBadge ? null : show,
    end: isBadge ? badge(show, kind || "dim") : null
  });
}

/* ── Miuix 列表组件 ── */
/* 设置行：左图标（可选）+ 标题/副标题 + 右侧值/控件/箭头；传 onClick 就是可点的整行。 */
function pref(o) {
  var r = el(o.onClick ? "button" : "div", "pref" + (o.cls ? " " + o.cls : ""));
  if (o.onClick) { r.type = "button"; r.onclick = o.onClick; }
  if (o.icon) {
    var box = el("span", "pref-ic");
    box.appendChild(ic(o.icon));
    r.appendChild(box);
  }
  var t = el("span", "pref-t");
  t.appendChild(el("span", "pref-title", o.title));
  if (o.summary) t.appendChild(el("span", "pref-sum", o.summary));
  r.appendChild(t);
  if (o.value !== undefined && o.value !== null) r.appendChild(el("span", "pref-val", String(o.value)));
  if (o.end) r.appendChild(o.end);
  if (o.arrow) r.appendChild(ic("chevron-right", "ic pref-arrow"));
  return r;
}
function group(box, title) {
  if (title) box.appendChild(el("div", "small-title", title));
  var c = el("div", "card");
  box.appendChild(c);
  return c;
}
function skeleton(box) {
  // 已有内容时保持原样，后台刷新完直接替换，不闪骨架
  if (box.firstChild) return;
  for (var i = 0; i < 3; i++) box.appendChild(el("div", "skel"));
}
/* 开关的读屏状态与键盘操作 */
function swState(sw, on) { sw.setAttribute("aria-checked", on ? "true" : "false"); }
function swKeys(sw) {
  sw.tabIndex = 0;
  sw.onkeydown = function (e) {
    if (e.key === " " || e.key === "Enter") { e.preventDefault(); sw.click(); }
  };
}

/* 隐身模式：带开关的行（网页不受隐身影响，所以这里也能把隐身关掉） */
function stealthRow(s) {
  var r = el("div", "row");
  var k = el("div", "k");
  k.appendChild(ic("eye-off", "ic ic-sm"));
  k.appendChild(el("span", null, "隐身模式"));
  r.appendChild(k);
  r.appendChild(el("div", "sp"));

  var sw = el("div", "sw" + (s.stealth ? " on" : ""));
  sw.setAttribute("role", "switch");
  sw.title = s.stealth ? "点击关闭隐身" : "点击开启隐身";
  sw.onclick = function () {
    if (state.busy) return;
    var want = !s.stealth;
    if (want) {
      var code = s.stealthCode || "";
      var how = code ? "退出方式：拨号盘输入 *#*#" + code + "#*#*" : "退出方式：本页这个开关（拨号密令未读到）";
      confirmSheet("开启隐身模式？",
        "开启后管理器界面会伪装成「未安装」，超级用户 / 模块等页面全部隐藏，" +
        "桌面图标保持可见。\n\n" + how +
        "\n\n（放心：本网页不受隐身影响，万一拨号退不出来，回到这一页关掉即可）",
        "开启", function () { setStealth(true); });
    } else {
      confirmSheet("关闭隐身模式？", "关闭后管理器界面立即恢复正常显示。", "关闭",
        function () { setStealth(false); });
    }
  };
  r.appendChild(sw);
  return r;
}

function setStealth(enable) {
  state.busy = true;
  post("/api/stealth", { enable: enable ? "1" : "0" }).then(function (r) {
    toast(r.message || (r.ok ? "已切换" : (r.error || "操作失败")));
    setTimeout(loadHome, 500);
  }).catch(function (e) { toast(apiErrorText(e)); })
    .then(function () { state.busy = false; });
}


/* ══════════════════════════════════════════════════════════════════
   主页 / 隐藏与恢复（最终形态：主按钮「恢复管理器」）
   ──────────────────────────────────────────────────────────────────
   设计口径（2026-10-30 最终版，替代此前的多档位/开关概念）：
     · App 一键启用后管理器 APK 被真实卸载；WebUI 与管理器同构，
       隐藏与恢复页只保留：状态显示、恢复管理器、取消隐藏、
       WebUI 端口、重置访问密钥；
     · 状态字段（GET /api/hidden）：managerUninstalled / hidden /
       daemonRunning / apkBackupPresent / apkShaValid / bootPersistence；
       未上报显示「—」，不猜测；安全性由内部检查负责；
     · POST /api/hidden {enable:false} 取消隐藏；
       POST /api/restore {action:"full"} 恢复管理器（分步结果 ok/fail 与原因）；
     · 文案：小字、单句不超过 15 字；用词与管理器保持一致。
   ══════════════════════════════════════════════════════════════════ */

var homeState = { status: null, hidden: null, port: null, mode: null };
var homeReadId = 0;
var restoreState = { hidden: null, result: null, port: null, pending: false, completed: null, awaitingConfirmation: false,
  checking: false, connectionLost: false, pollTimer: null, statusRequest: null };
var settingsState = { port: null, items: [], loaded: false, settingsResult: null };
var usersView = "users";

function merge(a, b) {
  var o = {};
  Object.keys(a || {}).forEach(function (k) { o[k] = a[k]; });
  Object.keys(b || {}).forEach(function (k) { o[k] = b[k]; });
  return o;
}
function pick(obj, keys, dflt) {
  for (var i = 0; i < keys.length; i++) {
    var v = obj ? obj[keys[i]] : undefined;
    if (v !== undefined && v !== null) return v;
  }
  return dflt;
}
function boolOf(v) {
  if (v === true || v === 1 || v === "1" || v === "true") return true;
  if (v === false || v === 0 || v === "0" || v === "false") return false;
  return null;
}

/* ── 宽容 API：501/403/网络错误都返回结构化结果，页面不抛异常 ── */
function apiSafe(method, api, params, body) {
  var url = apiUrl(api, params);
  var opts = { method: method, cache: "no-store" };
  if (body !== undefined) {
    opts.headers = { "Content-Type": "application/json" };
    opts.body = JSON.stringify(body);
  }
  return fetchTimed(url, opts).then(function (r) {
    var ct = "";
    try { ct = (r.headers && r.headers.get && r.headers.get("Content-Type")) || ""; } catch (e) {}
    var p = ct.indexOf("application/json") >= 0
      ? r.json().catch(function () { return null; })
      : Promise.resolve(null);
    return p.then(function (data) {
      return { ok: r.ok, status: r.status, data: data, reason: (data && (data.reason || data.error)) || "" };
    });
  }).catch(function (e) {
    return { ok: false, status: 0, data: null, reason: (e && e.message) || "network" };
  });
}
function apiGetSafe(api, params) { return apiSafe("GET", api, params); }
function apiPostSafe(api, body) { return apiSafe("POST", api, null, body || {}); }
function dataOf(r) { return (r && r.ok && r.data) ? r.data : null; }
function validData(r) { var d = dataOf(r); return d && d.ok !== false ? d : null; }
function unsupportedText(r, what) {
  if (!r) return "";
  if (r.reason) console.warn("[webui]", what || "", r.status, r.reason);   // 代码只进日志
  if (r.status === 501) {
    return "当前网页暂不支持" + (what ? "：" + what : "");
  }
  if (r.status === 503) return "服务暂未就绪，请稍后重试";
  if (r.status === 403) return (what ? what + "：" : "") + "不允许在网页里修改";
  if (r.status === 0) return "连接已断开，请确认服务仍在运行";
  return (what || "操作") + "没有成功，请稍后重试";
}
function noticeCard(title, text, kind) {
  var card = el("div", "card notice" + (kind ? " " + kind : ""));
  card.appendChild(ic(kind === "ok" ? "circle-check"
    : ((kind === "bad" || kind === "warn") ? "triangle-alert" : "info")));
  var t = el("div");
  t.appendChild(el("div", "n-t", title));
  if (text) t.appendChild(el("div", "n-b", String(text)));
  card.appendChild(t);
  return card;
}
function setMsg(node, text, kind) {
  if (!node) return;
  clear(node);
  node.appendChild(noticeCard(kind === "ok" ? "成功" : (kind === "bad" ? "没有成功" : "提示"), text, kind));
}

/* bootPersistence 是字符串：proven / unproven / unknown（未知不猜） */
function bootOf(v) {
  if (v === "proven") return true;
  if (v === "unproven") return false;
  if (v === "unknown") return null;
  return boolOf(v);
}

function normalizeHidden(data) {
  var d = data || {};
  // 服务端把恢复相关字段放在 recovery 对象里；兼容平铺的写法。
  var both = merge((d.recovery && typeof d.recovery === "object") ? d.recovery : {}, d);
  return {
    managerUninstalled: boolOf(pick(both, ["managerUninstalled", "apkUninstalled", "managerRemoved", "uninstalled"], null)),
    hidden: boolOf(pick(both, ["hidden", "hiddenActive", "concealed"], null)),
    daemonRunning: boolOf(pick(both, ["daemonRunning", "daemon", "serviceRunning"], null)),
    apkBackupPresent: boolOf(pick(both, ["apkBackupPresent", "apkBackup", "backupPresent"], null)),
    apkShaValid: boolOf(pick(both, ["apkShaValid", "apkValid", "shaValid"], null)),
    bootPersistence: bootOf(pick(both, ["bootPersistence", "bootPersist", "persistent"], null)),
    port: pick(both, ["webuiPort", "port", "webPort"], null),
    raw: d
  };
}
function normalizePort(data) {
  var d = data || {};
  return {
    port: pick(d, ["webuiPort", "port", "current", "listen", "value", "recoveryPort"], null),
    min: pick(d, ["min", "minPort"], 1024),
    max: pick(d, ["max", "maxPort"], 65535)
  };
}
/* 服务端白名单的键（LiteWebSettingsPolicy.WRITABLE）对应的用户文案 */
var SETTING_LABELS = {
  ui_style: "界面风格", theme: "主题", language: "语言",
  snow: "枫叶飘落", wallpaper_enabled: "壁纸", wallpaper_switch: "壁纸切换"
};
var SETTING_BOOLS = ["snow", "wallpaper_enabled", "wallpaper_switch"];

function normalizeSettings(data) {
  var list = pick(data || {}, ["items", "settings", "list", "entries"], null);
  if (!list) return [];
  var arr = Array.isArray(list)
    ? list
    : Object.keys(list).map(function (k) {
        var v = list[k];
        return (v && typeof v === "object") ? merge({ key: k }, v) : { key: k, value: v };
      });
  return arr.map(function (it, i) {
    var key = String(pick(it, ["key", "id", "name"], "item" + i));
    var value = pick(it, ["value"], "");
    var type = String(pick(it, ["type", "kind"], typeof value === "boolean" ? "bool" : "text") || "").toLowerCase();
    return {
      key: key,
      label: SETTING_LABELS[key] || String(pick(it, ["label", "title"], key)),
      type: SETTING_BOOLS.indexOf(key) >= 0 ? "bool" : type,
      value: value,
      options: pick(it, ["options", "values", "choices"], null),
      writable: pick(it, ["writable", "enabled", "allowed"], true) !== false,
      reason: String(pick(it, ["disabledReason", "reason", "error"], "") || "")
    };
  });
}
function stateText(v, onText, offText) {
  if (v === true) return onText;
  if (v === false) return offText;
  return "—";
}
function badgeKind(v) {
  if (v === true) return "ok";
  if (v === false) return "bad";
  return "dim";
}

/* ── 恢复结果分步展示（完成/失败如实显示，失败不伪装成功） ── */
var STEP_NAMES = {
  preconditionBackend: "检查恢复能力", preconditionBackup: "检查安装包备份",
  preconditionBackupSha: "校验安装包备份", preconditionBootPersistence: "检查开机自启",
  urlTokenPresent: "检查访问链接", validate: "校验请求", validatePort: "检查端口",
  verifyBackupSha: "校验安装包备份", installApk: "重新安装管理器",
  verifyPackageInstalled: "确认管理器已安装", restoreComponents: "恢复应用组件",
  unhide: "取消隐藏", daemon: "启动后台服务", resetToken: "重置访问密钥",
  restore_host_so: "还原宿主应用", remove_loader: "移除加载项",
  revoke_su_auth: "撤销宿主授权", stop_daemon: "停止网页服务", exception: "意外中断",
  verify: "检查恢复备份", apk_sha: "校验安装包", apk_stage: "准备安装包",
  pm_install: "安装 MapleSU", pm_path: "确认安装完成", launcher: "恢复桌面入口",
  data: "校验管理器数据访问", data_backup: "备份迁移前数据", data_uid: "适配管理器新 UID",
  hidden_off: "确认隐藏已关闭", cleanup_stale_backups: "整理历史备份",
  archive_host_backup: "整理恢复备份", await_manager_confirmation: "等待管理器内确认恢复",
  launch_manager: "打开管理器",
  kernel_stealth_off: "关闭隐身",
  normal_boot_cleanup: "清理恢复启动项",
  ensure_manager: "确认管理器已安装", restore_webui: "恢复网页设置", restore_components: "恢复应用入口"
};
var REASON_TEXT = {
  APK_BACKUP_MISSING: "没有找到安装包备份",
  APK_BACKUP_SHA_INVALID: "安装包备份校验未通过",
  BOOT_PERSISTENCE_UNPROVEN: "还不能确认开机自启",
  DATA_DIRECTORY_MISSING: "数据目录尚未就绪，请解锁设备后重试恢复",
  DATA_OWNER_UNREADABLE: "数据目录读取失败，请解锁设备后重试恢复",
  DATA_UID_MISMATCH: "保留数据的 UID 与卸载记录不一致，恢复入口已保留",
  DATA_PACKAGE_QUERY_FAILED: "包管理器查询失败，请稍后重试恢复",
  DATA_PACKAGE_UID_MISMATCH: "安装 UID 与数据目录不一致，恢复入口已保留",
  DATA_RECEIPT_INVALID: "卸载记录校验失败，恢复入口已保留",
  DATA_DIRECTORY_UNSAFE: "数据目录类型异常，恢复入口已保留",
  DATA_BACKUP_FAILED: "迁移前数据备份未完成，请检查可用存储后重试",
  DATA_MIGRATION_FAILED: "数据属主迁移未完成，原数据备份和恢复入口已保留，请重试",
  DATA_LABEL_RESTORE_FAILED: "数据访问标签恢复未完成，请重试恢复",
  DATA_VERIFICATION_UNAVAILABLE: "数据校验未取得有效结果，请重试恢复"
};
function extractSteps(data) {
  var raw = pick(data || {}, ["steps", "results", "actions"], null);
  if (!raw) return [];
  var arr = Array.isArray(raw)
    ? raw
    : Object.keys(raw).map(function (k) {
        var v = raw[k];
        return (v && typeof v === "object") ? merge({ id: k }, v) : { id: k, status: String(v) };
      });
  return arr.map(function (s, i) {
    if (typeof s === "string") {
      var match = /^([A-Za-z0-9_]+):([A-Za-z_]+)(?:\(([\s\S]*)\))?$/.exec(s);
      s = match ? { id: match[1], status: match[2], reason: match[3] || "" } : {};
    }
    var status = String(pick(s, ["status", "state", "result"], "")).toLowerCase();
    var okFlag = boolOf(pick(s, ["ok"], null));
    var failed = (okFlag === false) || status === "fail" || status === "failed" || status === "error" || status === "mismatch";
    var ok = (okFlag === true) || status === "ok" || status === "success" || status === "done";
    return {
      id: String(pick(s, ["id", "step", "name"], "step" + (i + 1))),
      label: pick(s, ["label", "title"], null),
      ok: ok && !failed,
      failed: failed,
      reason: String(pick(s, ["reason", "error", "message"], "") || "")
    };
  });
}
function recoverySucceeded(r) {
  var data = validData(r);
  return !!data && data.ok === true && !extractSteps(data).some(function (s) {
    return s.failed && s.id !== "cleanup_stale_backups";
  });
}
function renderRestoreSteps(container, r, title) {
  if (!container) return;
  clear(container);
  if (!r) { container.appendChild(noticeCard("恢复结果", "尚未执行", "info")); return; }
  var data = dataOf(r);
  var steps = extractSteps(data || r.data);
  var okFlag = recoverySucceeded(r);
  if (!okFlag) {
    container.appendChild(noticeCard("恢复没有成功", unsupportedText(r, "恢复管理器"), "bad"));
  } else if (data && data.awaitingConfirmation === true) {
    container.appendChild(noticeCard("等待管理器确认", "请打开 MapleSU，确认管理功能正常后，在管理器弹窗内确认恢复。确认前网页会一直保留，可在此重试安装。", "info"));
  } else {
    container.appendChild(noticeCard(title || "恢复完成", "已完成恢复所需的步骤", "ok"));
  }
  if (steps.length) {
    var card = group(container, "恢复步骤");
    appendRestoreStepRows(card, steps);
  } else if (okFlag) {
    container.appendChild(el("div", "hint", "没有返回详细步骤"));
  }
}
function appendRestoreStepRows(container, steps) {
  steps.forEach(function (s, i) {
    if (s.reason) console.warn("[webui] restore step", s.id, s.reason);
    container.appendChild(pref({
      icon: s.ok ? "circle-check" : (s.failed ? "circle-x" : "info"),
      title: STEP_NAMES[s.id] || s.label || ("第 " + (i + 1) + " 步"),
      summary: s.failed ? (REASON_TEXT[s.reason] || "这一步没有完成") : null,
      end: badge(s.ok ? "完成" : (s.failed ? "失败" : "未执行"), s.ok ? "ok" : (s.failed ? "bad" : "dim"))
    }));
  });
}
function updateRecoveryControls() {
  document.documentElement.classList.toggle("recovery-completed", !!restoreState.completed);
  var locked = restoreState.pending || !!restoreState.completed || (restoreState.checking && !restoreState.connectionLost);
  var controls = document.querySelectorAll("#dock button, #refresh, #t-home button, #t-hidden button, #t-hidden input");
  for (var i = 0; i < controls.length; i++) {
    controls[i].disabled = locked;
    controls[i].setAttribute("aria-disabled", String(locked));
  }
}
function renderRecoveryComplete(box) {
  clear(box);
  var complete = el("div", "recovery-complete");
  var card = el("div", "card recovery-success");
  var icon = el("div", "recovery-success-icon");
  icon.appendChild(ic("circle-check"));
  card.appendChild(icon);
  card.appendChild(el("h2", null, restoreState.completed === "unhide" ? "已取消隐藏" : "恢复完成"));
  card.appendChild(el("p", "recovery-success-message", "现在可以回到管理器继续使用。"));
  var next = el("div", "recovery-next");
  next.appendChild(el("strong", null, "回到 MapleSU"));
  next.appendChild(el("p", null, "请返回桌面，打开 MapleSU。此网页已完成恢复，可以关闭。"));
  card.appendChild(next);
  complete.appendChild(card);
  var steps = extractSteps(dataOf(restoreState.result));
  if (steps.length) {
    var details = el("details", "card recovery-details recovery-complete-details");
    var summary = el("summary");
    summary.appendChild(el("span", null, "查看恢复详情"));
    summary.appendChild(ic("chevron-right", "ic pref-arrow"));
    details.appendChild(summary);
    appendRestoreStepRows(details, steps);
    complete.appendChild(details);
  }
  box.appendChild(complete);
}
function finishRecovery(action, r) {
  restoreState.completed = action;
  restoreState.awaitingConfirmation = false;
  restoreState.checking = false;
  restoreState.connectionLost = false;
  clearTimeout(restoreState.pollTimer);
  restoreState.pollTimer = null;
  restoreState.result = r;
  homeReadId++;
  updateRecoveryControls();
  renderHome();
  renderRestoreCenter();
  $("#large-title").textContent = "MapleSU";
  $("#bar-title").textContent = "MapleSU";
  window.scrollTo(0, 0);
}
function runFullRestore(box) {
  if (state.busy || (restoreState.checking && !restoreState.connectionLost) || restoreState.completed) return;
  restoreState.revision = (restoreState.revision || 0) + 1;
  restoreState.result = null;
  restoreState.awaitingConfirmation = false;
  restoreState.checking = false;
  clearTimeout(restoreState.pollTimer);
  state.busy = true; restoreState.pending = true;
  updateRecoveryControls();
  if (box) setMsg(box, "正在恢复…", "info");
  return apiPostSafe("/api/restore", { action: "full" }).then(function (r) {
    if (r.status === 0) {
      restoreState.checking = true;
      restoreState.connectionLost = true;
      renderHome(); renderRestoreCenter();
      return;
    }
    restoreState.checking = !!(dataOf(r) && dataOf(r).restoring);
    restoreState.awaitingConfirmation = false;
    restoreState.connectionLost = false;
    restoreState.result = r;
    renderRestoreSteps(box, r);
    if (recoverySucceeded(r)) {
      if (dataOf(r).restoring === true) {
        renderHome(); renderRestoreCenter();
      } else if (dataOf(r).awaitingConfirmation === true) {
        restoreState.awaitingConfirmation = true;
        renderHome();
        renderRestoreCenter();
        toast("请在管理器内确认恢复，网页将持续保留");
      } else {
        finishRecovery("full", r);
        toast("恢复完成");
      }
    } else {
      toast(unsupportedText(r, "恢复管理器") || "恢复失败");
    }
  }).finally(function () {
    state.busy = false; restoreState.pending = false; updateRecoveryControls(); scheduleRestoreStatus();
  });
}
function scheduleRestoreStatus() {
  clearTimeout(restoreState.pollTimer);
  restoreState.pollTimer = null;
  if (restoreState.completed || !(restoreState.awaitingConfirmation || restoreState.checking)) return;
  restoreState.pollTimer = setTimeout(function () {
    restoreState.pollTimer = null;
    return refreshRestoreHandover();
  }, restoreState.connectionLost ? 5000 : 1000);
}
function refreshRestoreHandover() {
  if (restoreState.pending || restoreState.completed) return Promise.resolve();
  if (restoreState.statusRequest) return restoreState.statusRequest;
  var revision = restoreState.revision || 0;
  restoreState.statusRequest = apiGetSafe("/api/restore/status").then(function (r) {
    if (restoreState.pending || restoreState.completed || revision !== (restoreState.revision || 0)) return;
    var data = dataOf(r);
    if (!data) {
      if (restoreState.awaitingConfirmation || restoreState.checking) restoreState.connectionLost = true;
    } else {
      var unresolved = (restoreState.awaitingConfirmation || restoreState.checking) && data.ok === true &&
        data.restoring !== true && data.awaitingConfirmation !== true && data.completed !== true;
      restoreState.connectionLost = false;
      restoreState.checking = data.restoring === true || unresolved;
      restoreState.connectionLost = !!unresolved;
      restoreState.awaitingConfirmation = data.awaitingConfirmation === true;
      if (data.completed === true && recoverySucceeded(r)) {
        finishRecovery("full", r);
        return;
      }
      if (data.ok === false || restoreState.awaitingConfirmation) restoreState.result = r;
    }
    renderHome(); renderRestoreCenter(); updateRecoveryControls();
  }).finally(function () {
    restoreState.statusRequest = null;
    scheduleRestoreStatus();
  });
  return restoreState.statusRequest;
}
function renderRestoreStatus(box) {
  if (restoreState.connectionLost) {
    box.appendChild(noticeCard("恢复状态待确认", "暂未取得恢复结果。请返回 MapleSU 查看；尚未恢复时，可重新打开寄生入口。网页连接恢复后会自动更新。", "info"));
  } else if (restoreState.checking) {
    box.appendChild(noticeCard("正在恢复", "安装仍在进行，网页会自动确认结果，请稍候。", "info"));
  } else if (restoreState.awaitingConfirmation) {
    box.appendChild(noticeCard("等待管理器确认恢复", "请打开 MapleSU 并确认恢复。返回网页后会自动更新结果；桌面没有管理器时可重试恢复。", "info"));
  }
}
function cancelHidden(box) {
  var hidden = normalizeHidden(validData(state.tab === "hidden" ? restoreState.hidden : homeState.hidden));
  if (state.busy || (restoreState.checking && !restoreState.connectionLost) || restoreState.completed || hidden.managerUninstalled !== false) return;
  restoreState.revision = (restoreState.revision || 0) + 1;
  clearTimeout(restoreState.pollTimer);
  state.busy = true; restoreState.pending = true;
  updateRecoveryControls();
  if (box) setMsg(box, "正在取消隐藏…", "info");
  return apiPostSafe("/api/hidden", { enable: false }).then(function (r) {
    if (r.status === 0) { restoreState.checking = true; restoreState.connectionLost = true; return; }
    restoreState.awaitingConfirmation = false;
    restoreState.connectionLost = false;
    restoreState.checking = !!(dataOf(r) && dataOf(r).restoring);
    restoreState.result = r;
    if (recoverySucceeded(r)) {
      if (dataOf(r).restoring === true) {
        renderHome(); renderRestoreCenter();
      } else if (dataOf(r).awaitingConfirmation === true) {
        restoreState.awaitingConfirmation = true;
        restoreState.result = r;
        renderHome();
        renderRestoreCenter();
        toast("请在管理器内确认恢复，网页将持续保留");
      } else {
        finishRecovery("unhide", r);
        toast("已取消隐藏");
      }
    } else {
      if (box) setMsg(box, unsupportedText(r, "取消隐藏"), "bad");
      toast(unsupportedText(r, "取消隐藏"));
    }
  }).finally(function () {
    state.busy = false; restoreState.pending = false; updateRecoveryControls(); scheduleRestoreStatus();
  });
}
function copyRecoveryLink() {
  copyText(location.origin + P + "/", function () { toast("已复制恢复链接"); });
}

/* ══ 主页 ══ */
function loadHome() {
  var box = $("#t-home");
  if (!box) return;
  if (restoreState.completed) { renderRecoveryComplete(box); return Promise.resolve(); }
  if (restoreState.pending) return Promise.resolve();
  skeleton(box);
  var readId = ++homeReadId;
  return Promise.all([
    apiGetSafe("/api/status"),
    apiGetSafe("/api/hidden"),
    apiGetSafe("/api/config/port"),
    apiGetSafe("/api/workmode")
  ]).then(function (res) {
    if (readId !== homeReadId || restoreState.completed || restoreState.pending) return;
    homeState.status = res[0];
    homeState.hidden = res[1];
    homeState.port = res[2];
    homeState.mode = res[3];
    renderHome();
  });
}
function renderHome() {
  var box = $("#t-home");
  if (!box) return;
  if (restoreState.completed) { renderRecoveryComplete(box); return; }
  clear(box);
  renderRestoreStatus(box);
  var s = validData(homeState.status) || {};
  var reachable = !!validData(homeState.status);
  var hdR = homeState.hidden;
  var hd = validData(hdR) ? normalizeHidden(hdR.data) : null;
  var pt = dataOf(homeState.port) ? normalizePort(homeState.port.data)
    : { port: (location.port ? parseInt(location.port, 10) : 18427), min: 1024, max: 65535 };

  // 状态卡：只说服务能证明的事实
  var gone = !!(hd && hd.managerUninstalled === true);
  var root = boolOf(s.root);
  var workMode = validData(homeState.mode) || s;
  var modeName = String(workMode.mode || "").toUpperCase();
  var ultraMode = /^(ULTRA|MAPLESU_ULTRA|LITE)$/.test(modeName);
  var normalMode = /^(NORMAL|MAPLESU_NORMAL|KSU)$/.test(modeName);
  var ready = reachable && root === true && (ultraMode || normalMode) && !s.moduleCountError;
  var good = gone || ready;
  var heroHead = gone ? "管理器已卸载"
    : (!reachable || root === null ? "状态待确认" : (root === false ? "没有超级用户权限" : (ready ? "工作中" : "工作状态待确认")));
  var top = el("div", "home-top");
  var hero = el("div", "status-card " + (good ? "on" : "off"));
  hero.appendChild(ic(good ? "circle-check" : "triangle-alert", "ic status-mark"));
  hero.appendChild(el("div", "status-title", heroHead));
  hero.appendChild(el("div", "status-sub",
    reachable ? "网页管理器已连接" : "暂未连接，请刷新重试"));
  hero.appendChild(el("div", "status-mode", ultraMode ? "MapleSU Ultra" : (normalMode ? "MapleSU Normal" : "MapleSU")));
  top.appendChild(hero);

  var tiles = el("div", "tiles");
  var tMods = el("button", "tile");
  tMods.type = "button";
  tMods.appendChild(el("span", "l", "模块"));
  tMods.appendChild(el("span", "n", !s.moduleCountError && typeof s.moduleCount === "number" && s.moduleCount >= 0 ? String(s.moduleCount) : "—"));
  tMods.onclick = function () { show("modules"); };
  tiles.appendChild(tMods);
  var tPort = el("button", "tile");
  tPort.type = "button";
  tPort.appendChild(el("span", "l", "端口"));
  tPort.appendChild(el("span", "n", pt.port != null ? String(pt.port) : "—"));
  tPort.onclick = function () { show("hidden"); };
  tiles.appendChild(tPort);
  top.appendChild(tiles);
  box.appendChild(top);

  // 状态：字段来自 GET /api/hidden；未上报显示「—」
  var c0 = group(box, "当前状态");
  if (hd && hd.managerUninstalled !== null) {
    c0.appendChild(row("package", "管理器", stateText(hd.managerUninstalled, "已卸载", "运行中")));
  }
  c0.appendChild(row("eye-off", "隐藏", stateText(hd ? hd.hidden : null, "生效中", "未开启")));

  // 恢复：恢复管理器（主）、取消隐藏、复制恢复链接
  var c1 = group(box, "恢复");
  var wrap = el("div", "btn-wrap");
  var big = el("button", "btn primary big");
  big.appendChild(ic("rotate-ccw", "ic"));
  big.appendChild(el("span", null, "恢复管理器"));
  big.onclick = function () {
    confirmSheet("恢复管理器？", "重新安装管理器并取消隐藏", "恢复", function () {
      runFullRestore($("#home-result"));
    });
  };
  wrap.appendChild(big);
  c1.appendChild(wrap);
  if (hd && hd.managerUninstalled === false) c1.appendChild(pref({
    icon: "eye-off", title: "取消隐藏", summary: "解除隐藏，恢复原来的状态", arrow: true,
    onClick: function () {
      confirmSheet("取消隐藏？", "解除隐藏后界面恢复显示，此页面可能随后断开", "取消隐藏", function () {
        cancelHidden($("#home-result"));
      });
    }
  }));
  c1.appendChild(pref({
    icon: "copy", title: "复制恢复链接", summary: "妥善保存，隐藏后用它回到这里", arrow: true,
    onClick: copyRecoveryLink
  }));
  var resBox = el("div");
  resBox.id = "home-result";
  if (restoreState.result) renderRestoreSteps(resBox, restoreState.result);
  box.appendChild(resBox);
  recoveryDetails(box, hd);

  if (!hd) {
    box.appendChild(noticeCard("暂时读不到状态", unsupportedText(hdR, "读取恢复状态"), "warn"));
  }
}

function recoveryDetails(box, hd) {
  var details = el("details", "card recovery-details");
  var summary = el("summary");
  summary.appendChild(el("span", null, "恢复状态详情"));
  summary.appendChild(ic("chevron-right", "ic pref-arrow"));
  details.appendChild(summary);
  details.appendChild(row("play", "后台服务", stateText(hd ? hd.daemonRunning : null, "运行中", "未运行")));
  details.appendChild(row("file-archive", "安装包备份", stateText(hd ? hd.apkBackupPresent : null, "已备份", "没有备份")));
  details.appendChild(row("shield-check", "备份校验", stateText(hd ? hd.apkShaValid : null, "通过", "未通过")));
  details.appendChild(row("power", "开机自启", stateText(hd ? hd.bootPersistence : null, "已开启", "未开启")));
  box.appendChild(details);
}

/* ══ 恢复中心（隐藏与恢复页） ══ */
function loadRestoreCenter() {
  var box = $("#t-hidden");
  if (!box) return;
  if (restoreState.completed) { renderRecoveryComplete(box); return Promise.resolve(); }
  if (restoreState.pending) return Promise.resolve();
  skeleton(box);
  return Promise.all([apiGetSafe("/api/hidden"), apiGetSafe("/api/config/port")]).then(function (res) {
    if (restoreState.completed || restoreState.pending) return;
    restoreState.hidden = res[0];
    restoreState.port = res[1];
    renderRestoreCenter();
  });
}
function renderRestoreCenter() {
  var box = $("#t-hidden");
  if (!box) return;
  if (restoreState.completed) { renderRecoveryComplete(box); return; }
  clear(box);
  renderRestoreStatus(box);
  var hdR = restoreState.hidden;
  var hd = validData(hdR) ? normalizeHidden(hdR.data) : null;
  var pt = dataOf(restoreState.port) ? normalizePort(restoreState.port.data)
    : { port: (location.port ? parseInt(location.port, 10) : 18427), min: 1024, max: 65535 };

  // 主操作：恢复管理器
  var c1 = group(box, null);
  var wrap = el("div", "btn-wrap");
  var big = el("button", "btn primary big");
  big.appendChild(ic("rotate-ccw", "ic"));
  big.appendChild(el("span", null, "恢复管理器"));
  big.onclick = function () {
    confirmSheet("恢复管理器？", "重新安装管理器并取消隐藏", "恢复", function () {
      runFullRestore($("#restore-result"));
    });
  };
  wrap.appendChild(big);
  c1.appendChild(wrap);
  if (hd && hd.managerUninstalled === false) c1.appendChild(pref({
    icon: "eye-off", title: "取消隐藏", summary: "解除隐藏，恢复原来的状态", arrow: true,
    onClick: function () {
      confirmSheet("取消隐藏？", "解除隐藏后界面恢复显示，此页面可能随后断开", "取消隐藏", function () {
        cancelHidden($("#restore-result"));
      });
    }
  }));
  var resBox = el("div");
  resBox.id = "restore-result";
  if (restoreState.result) renderRestoreSteps(resBox, restoreState.result);
  box.appendChild(resBox);

  // 恢复条件
  if (hd && (hd.daemonRunning === false || hd.apkBackupPresent === false || hd.apkShaValid === false)) {
    box.appendChild(noticeCard("恢复条件不完整", "请先确认管理器可以恢复", "warn"));
  }
  var c0 = group(box, "当前状态");
  if (hd && hd.managerUninstalled !== null) {
    c0.appendChild(row("package", "管理器", stateText(hd.managerUninstalled, "已卸载", "运行中")));
  }
  c0.appendChild(row("eye-off", "隐藏", stateText(hd ? hd.hidden : null, "生效中", "未开启")));
  if (!hd) c0.appendChild(el("div", "hint", unsupportedText(hdR, "读取恢复状态")));
  recoveryDetails(box, hd);

  // 网页端口：默认 18427
  var cPort = group(box, "网页端口");
  cPort.appendChild(pref({ icon: "globe", title: "端口", summary: "与管理器使用同一配置" }));
  var pRow = el("div", "field-row");
  var pInput = el("input");
  pInput.type = "number";
  pInput.min = "1024";
  pInput.max = "65535";
  pInput.className = "code-input";
  pInput.value = pt.port != null ? String(pt.port) : "18427";
  pInput.placeholder = "18427";
  pInput.setAttribute("aria-label", "端口");
  pRow.appendChild(pInput);
  var pSave = el("button", "btn primary");
  pSave.appendChild(el("span", null, "保存"));
  pSave.onclick = function () { savePort(portMsg, pInput); };
  pRow.appendChild(pSave);
  cPort.appendChild(pRow);
  var portMsg = el("div");
  box.appendChild(portMsg);

  // 重置访问密钥
  var cTok = group(box, "访问密钥");
  cTok.appendChild(pref({
    icon: "key-round", title: "重置访问密钥", summary: "旧链接立即失效，新链接请在管理器中查看",
    cls: "danger", arrow: true,
    onClick: function () {
      confirmSheet("重置访问密钥？", "旧链接立即失效", "重置", function () { resetToken(tokenMsg); });
    }
  }));
  var tokenMsg = el("div");
  box.appendChild(tokenMsg);

  box.appendChild(el("div", "hint", "卸载管理器前，请先保存恢复链接。恢复后可继续使用。"));
}

/* 技术信息：默认收起，点开才读取；再点一次收起 */
function showDiagnostics(box) {
  if (!box) return;
  if (box.firstChild) { clear(box); return; }
  box.appendChild(el("div", "fld-h", "读取中…"));
  apiGetSafe("/api/diagnostics").then(function (r) {
    clear(box);
    if (!r.ok) { box.appendChild(el("div", "fld-h", unsupportedText(r, "读取技术信息"))); return; }
    var data = r.data;
    var text = typeof data === "string" ? data : JSON.stringify(data, null, 2);
    var pre = el("pre", "out");
    pre.textContent = text;
    box.appendChild(pre);
    var b = el("button", "btn");
    b.appendChild(ic("copy", "ic"));
    b.appendChild(el("span", null, "复制"));
    b.onclick = function () { copyText(text, function () { toast("已复制"); }); };
    box.appendChild(b);
  });
}

/* ══ 设置页 ══ */
function loadSettings() {
  var box = $("#settings");
  if (!box) return;
  skeleton(box);
  return Promise.all([apiGetSafe("/api/settings"), apiGetSafe("/api/config/port")]).then(function (res) {
    settingsState.items = res[0].ok ? normalizeSettings(res[0].data) : [];
    settingsState.settingsResult = res[0];
    settingsState.port = res[1].ok ? normalizePort(res[1].data)
      : { port: (location.port ? parseInt(location.port, 10) : 18427), min: 1024, max: 65535 };
    settingsState.loaded = true;
    renderSettings();
    renderAbout();
  });
}
function renderSettings() {
  var box = $("#settings");
  if (!box) return;
  clear(box);
  var pt = settingsState.port || { port: 18427, min: 1024, max: 65535 };

  // 配色：顶栏按钮的同款入口
  var cTheme = group(box, null);
  cTheme.appendChild(pref({
    icon: "palette", title: "配色", summary: themeName(state.theme), arrow: true, onClick: themeSheet
  }));

  // 端口
  var cPort = group(box, "网页端口");
  cPort.appendChild(pref({
    icon: "globe", title: "当前端口", value: pt.port != null ? String(pt.port) : "—"
  }));
  var inRow = el("div", "field-row");
  var pin = el("input");
  pin.type = "number";
  pin.min = String(pt.min || 1024);
  pin.max = String(pt.max || 65535);
  pin.value = pt.port != null ? String(pt.port) : "";
  pin.className = "code-input";
  pin.id = "port-input";
  pin.setAttribute("aria-label", "新端口");
  inRow.appendChild(pin);
  var pbtn = el("button", "btn primary");
  pbtn.appendChild(el("span", null, "保存"));
  pbtn.onclick = function () { savePort(portMsg); };
  inRow.appendChild(pbtn);
  cPort.appendChild(inRow);
  cPort.appendChild(el("div", "hint",
    "范围 " + (pt.min || 1024) + "–" + (pt.max || 65535) + "。保存后旧链接立即失效，请妥善保留新地址。"));
  var portMsg = el("div");
  box.appendChild(portMsg);

  // 访问密钥
  var cTok = group(box, "访问密钥");
  cTok.appendChild(pref({
    icon: "key-round", title: "重置访问密钥",
    summary: "换一个新的随机密钥，旧链接立即失效。之后请到管理器设置页查看新地址。",
    cls: "danger", arrow: true,
    onClick: function () {
      confirmSheet("重置访问密钥？", "旧链接会立即失效，本页也会断开。请确认能从管理器获取新地址。", "重置", function () {
        confirmSheet("再次确认重置？", "此操作不可撤销，只能从管理器重新获取链接。", "确认重置", function () {
          resetToken(tokenMsg);
        });
      });
    }
  }));
  var tokenMsg = el("div");
  box.appendChild(tokenMsg);

  // 可写白名单设置
  var cSet = group(box, "界面设置");
  if (!settingsState.items.length) {
    var sr = settingsState.settingsResult;
    cSet.appendChild(pref({
      icon: "sliders-horizontal", title: "暂无可修改的设置",
      summary: (sr && !sr.ok) ? unsupportedText(sr, "读取界面设置") : "可以到管理器中调整界面"
    }));
  } else {
    settingsState.items.forEach(function (item) {
      cSet.appendChild(settingRow(item, cSet));
    });
    if (settingsState.items.some(function (i) { return !i.writable; })) {
      cSet.appendChild(el("div", "hint", "标为「不可修改」的项目需要到管理器中调整。"));
    }
  }

  // 技术信息（折叠）
  var cDiag = group(box, null);
  var diagBox = el("div", "tech");
  cDiag.appendChild(pref({
    icon: "scroll-text", title: "技术信息", summary: "排查问题时使用，内容已去除敏感信息", arrow: true,
    onClick: function () { showDiagnostics(diagBox); }
  }));
  cDiag.appendChild(diagBox);
}
/* 写设置：服务端按 {键: 值} 接收，值一律是字符串（LiteWebSettingsPolicy） */
function settingWrite(item, value) {
  var body = {};
  body[item.key] = String(value);
  return apiPostSafe("/api/settings", body);
}
function settingRow(item, box) {
  var wrap = el("div");
  if (!item.writable) {
    if (item.reason) console.warn("[webui] setting", item.key, item.reason);
    wrap.appendChild(pref({
      icon: "settings-2", title: item.label, summary: "需要到管理器中调整",
      end: badge("不可修改", "dim")
    }));
    return wrap;
  }
  var msg = el("div");
  if (item.type === "bool" || typeof item.value === "boolean") {
    var on = boolOf(item.value) === true;
    var sw = el("div", "sw" + (on ? " on" : ""));
    sw.setAttribute("role", "switch");
    sw.setAttribute("aria-label", item.label);
    swState(sw, on);
    swKeys(sw);
    var pending = false;
    sw.onclick = function () {
      if (pending) return;
      pending = true;
      var want = !on;
      sw.classList.toggle("on", want);
      swState(sw, want);
      sw.setAttribute("aria-busy", "true"); sw.setAttribute("aria-disabled", "true");
      settingWrite(item, want).then(function (r) {
        if (r.ok && (!r.data || r.data.ok !== false)) {
          on = want; item.value = String(want);
          toast("已保存：" + item.label);
        } else {
          sw.classList.toggle("on", on); swState(sw, on);
          setMsg(msg, unsupportedText(r, item.label), "bad");
        }
      }).finally(function () {
        pending = false; sw.removeAttribute("aria-busy"); sw.removeAttribute("aria-disabled");
      });
    };
    wrap.appendChild(pref({ icon: "settings-2", title: item.label, end: sw }));
  } else if (Array.isArray(item.options) && item.options.length) {
    wrap.appendChild(pref({ icon: "settings-2", title: item.label }));
    var opts = el("div", "chips field-row");
    var optionPending = false;
    item.options.forEach(function (opt) {
      var oid = (opt && typeof opt === "object") ? pick(opt, ["id", "value", "key"], String(opt)) : String(opt);
      var olabel = (opt && typeof opt === "object") ? pick(opt, ["label", "name"], String(oid)) : String(opt);
      var b = el("button", "chip" + (String(item.value) === String(oid) ? " on" : ""), olabel);
      b.dataset.value = String(oid);
      b.onclick = function () {
        if (optionPending || String(item.value) === String(oid)) return;
        optionPending = true;
        var previous = String(item.value);
        opts.querySelectorAll("button").forEach(function (button) {
          button.classList.toggle("on", button === b); button.disabled = true;
        });
        opts.setAttribute("aria-busy", "true");
        settingWrite(item, oid).then(function (r) {
          if (r.ok && (!r.data || r.data.ok !== false)) { item.value = String(oid); toast("已保存：" + item.label); }
          else {
            opts.querySelectorAll("button").forEach(function (button) { button.classList.toggle("on", button.dataset.value === previous); });
            setMsg(msg, unsupportedText(r, item.label), "bad");
          }
        }).finally(function () {
          optionPending = false; opts.removeAttribute("aria-busy");
          opts.querySelectorAll("button").forEach(function (button) { button.disabled = false; });
        });
      };
      opts.appendChild(b);
    });
    wrap.appendChild(opts);
  } else {
    wrap.appendChild(pref({ icon: "settings-2", title: item.label }));
    var line = el("div", "field-row");
    var inp = el("input");
    inp.className = "code-input";
    inp.value = item.value == null ? "" : String(item.value);
    inp.setAttribute("aria-label", item.label);
    line.appendChild(inp);
    var save = el("button", "btn");
    save.appendChild(el("span", null, "保存"));
    save.onclick = function () {
      if (save.disabled) return;
      var value = inp.value;
      save.disabled = true; save.setAttribute("aria-busy", "true");
      settingWrite(item, value).then(function (r) {
        if (r.ok && (!r.data || r.data.ok !== false)) { item.value = value; toast("已保存：" + item.label); }
        else setMsg(msg, unsupportedText(r, item.label), "bad");
      }).finally(function () {
        save.disabled = false; save.removeAttribute("aria-busy");
      });
    };
    line.appendChild(save);
    wrap.appendChild(line);
  }
  wrap.appendChild(msg);
  return wrap;
}
function savePort(msgBox, inputEl) {
  var input = inputEl || $("#port-input");
  var value = input ? parseInt(input.value, 10) : NaN;
  if (!(value >= 1024 && value <= 65535)) {
    setMsg(msgBox, "端口必须是 1024–65535 的整数。", "bad");
    return;
  }
  if (state.busy) return;
  state.busy = true;
  setMsg(msgBox, "正在保存端口并重新监听…", "info");
  var newUrl = "http://127.0.0.1:" + value + P + "/";
  apiPostSafe("/api/config/port", { webuiPort: value }).then(function (r) {
    if (r.ok && (!r.data || r.data.ok !== false)) {
      setMsg(msgBox, "端口已改为 " + value + "。旧链接已失效，请用新链接打开：" + newUrl, "ok");
      toast("端口已保存：" + value);
    } else if (r.status === 0) {
      setMsg(msgBox, "连接中断（服务可能正在重新监听）。如果端口已改，请用新链接打开：" + newUrl, "warn");
    } else {
      setMsg(msgBox, unsupportedText(r, "修改端口"), "bad");
    }
  }).then(function () { state.busy = false; });
}
function resetToken(msgBox) {
  if (state.busy) return;
  state.busy = true;
  setMsg(msgBox, "正在重置访问密钥…", "info");
  apiPostSafe("/api/config/token/reset", {}).then(function (r) {
    if (r.ok && (!r.data || r.data.ok !== false)) {
      setMsg(msgBox,
        "访问密钥已重置，旧链接立即失效。请到管理器设置页查看新地址并重新打开，本页之后会无法使用。", "ok");
      toast("已重置访问密钥");
    } else {
      setMsg(msgBox, unsupportedText(r, "重置访问密钥"), "bad");
    }
  }).then(function () { state.busy = false; });
}

/* ══ 超级用户页内子视图：授权列表 / 日志 ══ */
function showUsersView(view) {
  usersView = view || usersView;
  var listBox = $("#users");
  var logBox = $("#sulog-sub");
  if (listBox) listBox.style.display = usersView === "users" ? "" : "none";
  if (logBox) logBox.style.display = usersView === "sulog" ? "" : "none";
  var tools = $("#app-tools");
  if (tools) tools.style.display = usersView === "users" ? "" : "none";
  var chips = document.querySelectorAll("#user-toolbar [data-view]");
  for (var i = 0; i < chips.length; i++) {
    chips[i].classList.toggle("on", chips[i].getAttribute("data-view") === usersView);
  }
  return usersView === "sulog" ? loadSulog() : loadApps();
}

/* ── 模块页 ── */
var modulePending = Object.create(null);
var moduleReadId = 0;
var modulesLoaded = false;
var modulesReadError = false;
function readModules() {
  return get("/api/modules").then(function (list) {
    if (!Array.isArray(list)) list = list && list.ok !== false ? list.modules : null;
    if (!Array.isArray(list) || list.some(function (item) { return !item || typeof item.id !== "string"; })) {
      throw new Error("invalid module catalog");
    }
    return list.map(normalizeModule);
  });
}
function loadModules() {
  var box = $("#modules");
  skeleton(box);
  var readId = ++moduleReadId;
  return readModules().then(function (list) {
    if (readId !== moduleReadId) return;
    state.modules = list.map(function (item) {
      if (modulePending[item.id]) item.enabled = modulePending[item.id].want;
      return item;
    });
    modulesLoaded = true;
    modulesReadError = false;
    renderModules();
  }).catch(function (e) {
    if (readId !== moduleReadId) return;
    if (modulesLoaded) { modulesReadError = true; renderModules(); return; }
    if (isUnsupported(e)) { clear(box); box.appendChild(noticeCard("暂时读不到模块", "工作环境尚未就绪，请刷新重试", "warn")); return; }
    showReadError(box, e);
  });
}


function filteredModules() {
  var list = state.modules.slice();
  var q = state.search.trim().toLowerCase();
  if (q) {
    list = list.filter(function (m) {
      return ((m.name || "") + " " + (m.id || "") + " " + (m.author || "") + " " + (m.description || ""))
        .toLowerCase().indexOf(q) >= 0;
    });
  }
  if (state.sort === "enabled") {
    list.sort(function (a, b) { return (b.enabled ? 1 : 0) - (a.enabled ? 1 : 0); });
  } else if (state.sort === "name") {
    list.sort(function (a, b) { return String(a.name || a.id).localeCompare(String(b.name || b.id), "zh"); });
  }
  return list;
}

/* 读取失败：原因进日志，界面给一句人话 */
function showReadError(box, e) {
  if (e && e.message) console.warn("[webui]", e.message);
  if (box.firstChild && !box.querySelector(".skel")) { toast("暂未更新，已保留当前内容"); return; }
  clear(box);
  var emp = el("div", "empty err");
  emp.appendChild(ic("triangle-alert"));
  emp.appendChild(el("div", null, "读取失败，请稍后重试"));
  box.appendChild(emp);
}

function renderModules() {
  var box = $("#modules");
  clear(box);
  var list = filteredModules();
  if (modulesReadError) box.appendChild(noticeCard("模块状态暂未更新", "正在显示上次读取的内容，请刷新后再操作", "warn"));
  if (!state.modules.length) {
    var emp = el("div", "empty");
    emp.appendChild(ic("package"));
    emp.appendChild(el("div", null, "还没有安装任何模块"));
    box.appendChild(emp);
    return;
  }
  if (!list.length) {
    var e2 = el("div", "empty");
    e2.appendChild(ic("search"));
    e2.appendChild(el("div", null, "没有匹配的模块"));
    box.appendChild(e2);
    return;
  }
  list.forEach(function (m) { box.appendChild(moduleCard(m)); });
}

/* ksud 的模块字段是字符串 "true"/"false"，这里统一成布尔 */
function strTrue(v) { return v === true || v === "true" || v === 1 || v === "1"; }
function normalizeModule(m) {
  var o = {};
  for (var k in m) if (Object.prototype.hasOwnProperty.call(m, k)) o[k] = m[k];
  ["enabled", "action", "web", "update", "remove", "metamodule", "mount"].forEach(function (k) {
    if (k in o) o[k] = strTrue(o[k]);
  });
  return o;
}

function moduleCard(m) {
  var card = el("div", "mod" + (m.enabled ? "" : " off"));
  card.dataset.moduleId = m.id;
  var head = el("div", "mh");

  // 头像：优先用户自定义图标（/api/icon），失败退回首字母
  var ava = el("div", "ava");
  ava.appendChild(el("span", null, (String(m.name || m.id || "?").trim().charAt(0) || "?").toUpperCase()));
  var img = el("img");
  img.alt = "";
  img.style.display = "none";
  img.onload = function () { clear(ava); ava.appendChild(img); img.style.display = "block"; };
  img.onerror = function () { if (img.parentNode) img.parentNode.removeChild(img); };
  img.src = P + "/api/icon?id=" + encodeURIComponent(m.id);
  ava.appendChild(img);
  head.appendChild(ava);

  var mt = el("div", "mt");
  var nm = el("div", "m-name");
  nm.appendChild(el("span", null, m.name || m.id));
  mt.appendChild(nm);
  mt.appendChild(el("div", "m-meta",
    "v" + (m.version || "?") + " · " + (m.author || "未知作者")));
  head.appendChild(mt);
  card.appendChild(head);
  if (m.description) card.appendChild(el("div", "m-desc", m.description));

  var tags = el("div", "tags");
  function tag(iconName, text) {
    var t = el("span", "tag");
    t.appendChild(ic(iconName, "ic"));
    t.appendChild(el("span", null, text));
    return t;
  }
  var abnormal = !modulePending[m.id] && (m.backend === "maplesu_ultra") && (m.runtimeStatus || m.ultraState) === "Abnormal";
  var enabledTag = tag(abnormal ? "triangle-alert" : (m.enabled ? "circle-check" : "circle-x"),
    modulePending[m.id] ? "正在保存…" : (abnormal ? "运行异常" : (m.enabled ? "已启用" : "已禁用")));
  enabledTag.classList.add("enabled-tag"); tags.appendChild(enabledTag);
  if (m.backend === "maplesu_ultra") tags.appendChild(tag("shield-check", "MapleSU"));
  if (m.metamodule) tags.appendChild(tag("layers", "元模块"));
  if (m.action) tags.appendChild(tag("play", "有动作脚本"));
  if (m.web) tags.appendChild(tag("globe", "有网页界面"));
  if (m.update) tags.appendChild(tag("upload", "可更新"));
  if (m.remove) tags.appendChild(tag("trash", "待卸载（重启生效）"));
  else if (m.needsReboot) tags.appendChild(tag("refresh-cw", "状态已保存，待重启"));
  card.appendChild(tags);

  var acts = el("div", "acts");
  if (m.remove && !LITE_MODE && !modulesReadError) {
    var undo = el("button", "btn");
    undo.appendChild(ic("rotate-ccw", "ic"));
    undo.appendChild(el("span", null, "撤销卸载"));
    undo.onclick = function () {
      if (state.busy) return;
      state.busy = true;
      post("/api/module/undo-uninstall", { id: m.id }).then(function (r) {
        toast(r.message || (r.ok ? "已撤销卸载" : "操作失败"));
        setTimeout(loadModules, 600);
      }).catch(function (e) { toast(apiErrorText(e)); })
        .then(function () { state.busy = false; });
    };
    acts.appendChild(undo);
  }
  // 动作脚本：跑起来、把输出显示出来
  if (m.action && !modulesReadError) {
    var runAct = el("button", "btn");
    runAct.appendChild(ic("play", "ic"));
    runAct.appendChild(el("span", null, "运行动作"));
    runAct.onclick = function () {
      if (state.busy) return;
      state.busy = true;
      openSheet("动作脚本", waitBox("正在运行「" + (m.name || m.id) + "」的动作脚本…"), []);
      postJson("/api/module/action", { id: m.id, action: "action" }).then(function (r) {
        if (!r.ok) {
          if (r.error) console.warn("[webui] module action", r.error);
          openSheet("无法运行", el("div", "body", "动作脚本没有运行成功"), [{ label: "关闭" }]);
          return;
        }
        showActionOutput(m, r);
      }).catch(function (e) {
        openSheet("无法运行", el("div", "body", apiErrorText(e)), [{ label: "关闭" }]);
      }).then(function () { state.busy = false; });
    };
    acts.appendChild(runAct);
  }

  // 文件代理能力由后端明确上报；hasWebUi 仅是模块自带网页的描述。
  if (m.web && !modulesReadError && (!LITE_MODE || (m.backend === "ksu" && m.canWeb === true))) {
    var webBtn = el("button", "btn");
    webBtn.appendChild(ic("globe", "ic"));
    webBtn.appendChild(el("span", null, "网页"));
    webBtn.onclick = function () { openModuleWeb(m); };
    acts.appendChild(webBtn);
  }

  // 当前 Lite 后端没有卸载能力，不渲染入口。
  if (!LITE_MODE) {
  acts.appendChild(el("div", "sp"));
  var del = el("button", "btn danger");
  del.appendChild(ic("trash", "ic"));
  del.appendChild(el("span", null, "卸载"));
  del.onclick = function () {
    if (m.remove) { toast("这个模块已经标记卸载了，重启手机即可"); return; }
    confirmSheet("卸载模块",
      "确定卸载「" + (m.name || m.id) + "」？\n\n卸载后需要重启手机才真正生效。",
      "卸载", function () {
        state.busy = true;
        post("/api/module/uninstall", { id: m.id }).then(function (r) {
          toast(r.message || r.error || "已提交");
          setTimeout(loadModules, 800);
        }).catch(function (e) { toast(apiErrorText(e)); })
          .then(function () { state.busy = false; });
      });
  };
  acts.appendChild(del);
  }
  if (acts.firstChild) card.appendChild(acts);

  if (m.canToggle === false) {
    card.appendChild(el("div", "m-compat", "启停请在管理器中操作"));
    return card;
  }

  var sw = el("div", "sw" + (m.enabled ? " on" : ""));
  sw.setAttribute("role", "switch");
  sw.setAttribute("aria-label", "启用 " + (m.name || m.id));
  swState(sw, !!m.enabled);
  swKeys(sw);
  if (modulesReadError) sw.setAttribute("aria-disabled", "true");
  sw.onclick = function () {
    if (modulePending[m.id] || modulesReadError) return;
    var before = !!m.enabled, want = !before;
    modulePending[m.id] = { want: want };
    ++moduleReadId;
    m.enabled = want;
    updateModuleSwitch(m.id, want);
    sw.setAttribute("aria-busy", "true"); sw.setAttribute("aria-disabled", "true");
    var req = LITE_MODE
      ? postJson("/api/module/toggle", { id: m.id, enabled: want })
      : post("/api/module/toggle", { id: m.id, enable: want ? "1" : "0" });
    var writeOk = false, actual = null;
    req.then(function (r) {
      writeOk = r && r.ok === true;
    }).catch(function (e) { console.warn("[webui] module toggle", e.message); }).then(function () {
      return readModules().then(function (list) {
        actual = list.find(function (item) { return item.id === m.id; }) || null;
      }).catch(function (e) { console.warn("[webui] module readback", e.message); });
    }).finally(function () {
      ++moduleReadId;
      delete modulePending[m.id];
      m.enabled = actual ? !!actual.enabled : before;
      if (!actual) modulesReadError = true;
      updateModuleSwitch(m.id, m.enabled);
      if (!actual) renderModules();
      toast(writeOk && actual && m.enabled === want ? "已保存，重启后生效"
        : (!actual ? "状态待确认，请刷新重试" : "操作没有成功，已同步当前状态"));
    });
  };
  if (modulePending[m.id]) { sw.setAttribute("aria-busy", "true"); sw.setAttribute("aria-disabled", "true"); }
  head.appendChild(sw);
  return card;
}

function updateModuleSwitch(id, enabled) {
  state.modules.forEach(function (item) { if (item.id === id) item.enabled = enabled; });
  document.querySelectorAll("#modules .mod").forEach(function (card) {
    if (card.dataset.moduleId !== id) return;
    card.classList.toggle("off", !enabled);
    var control = card.querySelector(".sw"), label = card.querySelector(".enabled-tag");
    control.classList.toggle("on", enabled); swState(control, enabled);
    if (modulePending[id]) { control.setAttribute("aria-busy", "true"); control.setAttribute("aria-disabled", "true"); }
    else { control.removeAttribute("aria-busy"); control.removeAttribute("aria-disabled"); }
    clear(label); label.appendChild(ic(enabled ? "circle-check" : "circle-x", "ic"));
    label.appendChild(el("span", null, modulePending[id] ? "正在保存…" : (enabled ? "已启用" : "已禁用")));
  });
}

/* ── 模块动作脚本输出 / 模块网页（WebUI）── */
function waitBox(text) {
  var box = el("div");
  box.appendChild(el("div", "fld-h", text));
  var bar = el("div", "prog");
  var fill = el("i");
  fill.style.width = "35%";
  bar.appendChild(fill);
  box.appendChild(bar);
  return box;
}

function copyText(text, done) {
  var cb = done || function () { toast("已复制"); };
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(cb).catch(function () { fallbackCopy(text, cb); });
  } else {
    fallbackCopy(text, cb);
  }
}

function showActionOutput(m, r) {
  var box = el("div");
  if (r.code !== undefined && r.code !== null) {
    box.appendChild(el("div", "fld-h", r.code === 0 ? "运行完成" : "脚本运行时出错了"));
  }
  var pre = el("pre", "out");
  pre.textContent = r.output || "（没有输出）";
  box.appendChild(pre);
  openSheet("动作脚本 · " + (m.name || m.id), box, [
    { label: "复制输出", keepOpen: true, onClick: function () { copyText(r.output || "", function () { toast("输出已复制"); }); } },
    { label: "关闭" }
  ]);
}

function openModuleWeb(m) {
  var box = el("div");
  var bar = el("div", "webui-bar");
  bar.appendChild(el("div", "webui-title", m.name || m.id));
  bar.appendChild(el("div", "sp"));
  // 完整的模块网页需要 KernelSU 的 JS 桥（spawn/包列表等），浏览器里是精简版
  var appLink = el("a", "btn small");
  appLink.textContent = "在管理器里打开";
  appLink.href = "intent://webui?id=" + encodeURIComponent(m.id) +
    "&webToken=" + encodeURIComponent(P.split("/").filter(Boolean)[1] || "") +
    "#Intent;scheme=ksu;package=dev.maplesu.manager;end";
  bar.appendChild(appLink);
  box.appendChild(bar);
  var frame = el("iframe", "webui-frame");
  frame.setAttribute("referrerpolicy", "no-referrer");
  frame.src = P + "/modweb/" + encodeURIComponent(m.id) + "/index.html";
  box.appendChild(frame);
  box.appendChild(el("div", "fld-h",
    "这是模块自带的网页界面（本机）。网页里能用的是精简版接口（exec / 提示）；" +
    "模块如果用到更复杂的接口，点右上角「在管理器里打开」用完整版。"));
  openSheet("", box, [{ label: "关闭" }], true);
}

/* ══════════════════════════════════════════════════════════════════
   超级用户
   ──────────────────────────────────────────────────────────────────
   列表按 **uid 分组**（同一个 uid 下的多个包是一组，比如共享 uid 的套件）。
   左边的开关 = 授权/撤销 root；点「配置」进详情改 root 配置。
   ⚠️ 网页拿不到 APK 里的中文应用名（要解析 resources.arsc），所以显示包名。
   ══════════════════════════════════════════════════════════════════ */
function enc(s) { return encodeURIComponent(s == null ? "" : String(s)); }

var ultraUsers = {
  catalog: null, error: null, readId: 0, pending: Object.create(null),
  unverified: Object.create(null), logReadId: 0, logResult: null,
  loggingPending: null, loggingUnverified: false, logClearPending: false, logClearUnverified: false
};
function ultraCatalog(r) {
  var d = validData(r);
  return d && d.ok === true && Array.isArray(d.apps) && d.capabilities && (d.scope === "package-global" || d.scope === "uid") ? d : null;
}
function loadLiteSuperusers() {
  var box = $("#users");
  skeleton(box);
  var readId = ++ultraUsers.readId;
  return apiGetSafe("/api/superuser").then(function (r) {
    var data = ultraCatalog(r);
    if (readId !== ultraUsers.readId) return null;
    ultraUsers.error = data ? null : r;
    if (data) {
      ultraUsers.catalog = data;
      ultraUsers.unverified = Object.create(null);
      ultraUsers.loggingUnverified = false;
    }
    renderLiteSuperusers();
    return data;
  });
}
function ultraAppGroups() {
  var map = Object.create(null), d = ultraUsers.catalog;
  (d ? d.apps : []).forEach(function (a) {
    if (!a || typeof a.packageName !== "string" || typeof a.allowed !== "boolean") return;
    var key = d.scope === "uid" ? a.packageName + "#" + a.uid : a.packageName;
    var g = map[key];
    if (!g) g = map[key] = { key: key, packageName: a.packageName, allowed: a.allowed, protected: false, uids: [], users: [] };
    g.protected = g.protected || a.protected === true;
    if (Number.isInteger(a.uid) && g.uids.indexOf(a.uid) < 0) g.uids.push(a.uid);
    if (Number.isInteger(a.userId) && g.users.indexOf(a.userId) < 0) g.users.push(a.userId);
  });
  var q = state.appSearch.trim().toLowerCase();
  return Object.keys(map).map(function (pkg) { return map[pkg]; }).filter(function (g) {
    return (!q || (g.packageName + " " + g.uids.join(" ") + " " + g.users.join(" ")).toLowerCase().indexOf(q) >= 0)
      && (state.appFilter !== "granted" || g.allowed) && (state.appFilter !== "plain" || !g.allowed);
  }).sort(function (a, b) { return Number(b.allowed) - Number(a.allowed) || a.packageName.localeCompare(b.packageName); });
}
function renderLiteSuperusers() {
  var box = $("#users");
  clear(box);
  var d = ultraUsers.catalog;
  if (ultraUsers.error) {
    box.appendChild(noticeCard(d ? "授权列表暂未更新" : "暂时读不到授权列表",
      d ? "正在显示上次读取的记录，请刷新重试" : "请确认服务正常后重试", "warn"));
  }
  if (!d) return;
  if (Object.keys(ultraUsers.unverified).length) {
    box.appendChild(noticeCard("部分授权状态待确认", "请刷新后再操作这些应用", "warn"));
  }
  box.appendChild(el("div", "hint su-scope", d.scope === "uid" ? "授权按 UID 生效，不同用户分别管理，共享 UID 的应用同步生效。" : "授权按应用生效，同一应用在不同用户下共用授权状态。"));
  if (d.catalogAvailable === false || d.catalogComplete === false) {
    box.appendChild(el("div", "hint", "应用列表暂未完整读取，已有授权仍可管理。"));
  }
  var list = ultraAppGroups();
  if (!list.length) {
    var emp = el("div", "empty");
    emp.appendChild(ic("users"));
    emp.appendChild(el("div", null, d.apps.length ? "没有匹配的应用" : "暂无可管理的应用"));
    box.appendChild(emp);
    return;
  }
  list.forEach(function (g) { box.appendChild(ultraAppCard(g)); });
}
function ultraAppCard(g) {
  var card = el("div", "mod su-app");
  card.dataset.packageName = g.packageName;
  var head = el("div", "mh");
  var ava = el("div", "ava");
  ava.appendChild(ic("package")); head.appendChild(ava);
  var mt = el("div", "mt");
  mt.appendChild(el("div", "m-name", g.packageName));
  var meta = [];
  if (g.uids.length) meta.push("UID " + g.uids.join(" / "));
  if (g.users.length) meta.push("用户 " + g.users.join(" / "));
  if (meta.length) mt.appendChild(el("div", "m-meta", meta.join(" · ")));
  head.appendChild(mt); card.appendChild(head);
  var key = g.key || g.packageName;
  var pending = ultraUsers.pending[key];
  var unverified = !!ultraUsers.unverified[key];
  var allowed = pending ? pending.want : g.allowed;
  var footer = el("div", "su-footer");
  footer.appendChild(badge(pending ? "正在保存…" : (unverified ? "状态待确认" : (allowed ? "已授权" : "未授权")), allowed ? "ok" : "dim"));
  var caps = ultraUsers.catalog.capabilities;
  var canChange = !ultraUsers.error && !pending && !unverified && (allowed ? caps.revoke === true && !g.protected : caps.grant === true);
  var sw = el("button", "sw" + (allowed ? " on" : ""));
  sw.type = "button";
  sw.setAttribute("role", "switch");
  sw.setAttribute("aria-label", "允许 " + g.packageName + " 使用超级用户权限");
  swState(sw, allowed);
  sw.disabled = !canChange;
  sw.setAttribute("aria-disabled", String(!canChange));
  if (pending) { card.setAttribute("aria-busy", "true"); sw.setAttribute("aria-busy", "true"); }
  sw.onclick = function () {
    if (!canChange || ultraUsers.pending[key]) return;
    var want = !g.allowed;
    confirmSheet(want ? "授予超级用户权限？" : "撤销超级用户权限？",
      g.packageName + (want ? " 将可以使用超级用户权限。" : " 将失去超级用户权限。") +
        (ultraUsers.catalog.scope === "uid" ? "\nUID " + g.uids[0] + "，共享该 UID 的应用同步生效。" : "\n同一应用的所有用户均会生效。"),
      want ? "授予" : "撤销", function () { setUltraAppRoot(g, want); });
  };
  var touch = el("div", "su-switch-target"); touch.appendChild(sw); footer.appendChild(touch);
  card.appendChild(footer);
  if (g.protected) card.appendChild(el("div", "m-compat", "用于保持管理器与当前服务可用"));
  return card;
}
function setUltraAppRoot(g, want) {
  var pkg = g.packageName;
  var key = g.key || pkg;
  if (ultraUsers.pending[key]) return Promise.resolve();
  var caps = ultraUsers.catalog && ultraUsers.catalog.capabilities;
  if (!caps || ultraUsers.error || ultraUsers.unverified[key] || (want ? caps.grant !== true : caps.revoke !== true || g.protected)) return Promise.resolve();
  var pending = { want: want };
  ultraUsers.pending[key] = pending;
  ++ultraUsers.readId;
  renderLiteSuperusers();
  var request = { packageName: pkg, allow: want, confirm: true };
  if (ultraUsers.catalog.scope === "uid") request.uid = g.uids[0];
  return apiPostSafe("/api/superuser", request).then(function (write) {
    return loadLiteSuperusers().then(function (d) {
      var actual = d && d.apps.filter(function (a) { return a.packageName === pkg && (d.scope !== "uid" || a.uid === request.uid); });
      var verified = actual && actual.length > 0 && actual.every(function (a) { return a.allowed === want; });
      if (actual && actual.length === 0 && !want && d.scope === "package-global" && d.catalogAvailable === true && d.catalogComplete === true &&
          Array.isArray(d.grants) && d.grants.every(function (p) {
            return typeof p === "string" && p.length <= 255 && (p === "android" || /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/.test(p));
          })) verified = d.grants.indexOf(pkg) < 0;
      var uncertain = !d || (actual.length === 0 && !verified);
      if (uncertain) ultraUsers.unverified[key] = true;
      delete ultraUsers.pending[key];
      renderLiteSuperusers();
      var confirmed = validData(write);
      if (confirmed && confirmed.ok === true && confirmed.packageName === pkg && confirmed.allowed === want && verified) toast(want ? "已授予超级用户权限" : "已撤销超级用户权限");
      else if (uncertain) toast("状态待确认，请刷新重试");
      else toast("操作没有成功，已同步当前授权状态");
    });
  });
}

function loadApps() {
  if (LITE_MODE) return loadLiteSuperusers();
  var box = $("#users");
  skeleton(box);
  return get("/api/apps").then(function (r) {
    state.apps = (r && r.list) ? r.list : [];
    renderApps();
  }).catch(function (e) {
    clear(box);
    var emp = el("div", "empty err");
    emp.appendChild(ic("triangle-alert"));
    emp.appendChild(el("div", null, "读取失败：" + e.message));
    box.appendChild(emp);
  });
}

function groupedApps() {
  var map = {};
  state.apps.forEach(function (a) {
    (map[a.uid] = map[a.uid] || []).push(a);
  });
  return Object.keys(map).map(function (uid) {
    var apps = map[uid].slice().sort(function (a, b) {
      return String(a.pkg).localeCompare(String(b.pkg), "en");
    });
    return {
      uid: parseInt(uid, 10),
      apps: apps,
      primary: apps[0],
      granted: apps.some(function (a) { return a.granted; }),
      configured: apps.some(function (a) { return a.configured; }),
      system: apps[0].system,
      userId: apps[0].userId
    };
  });
}

function filteredGroups() {
  var list = groupedApps();
  var q = state.appSearch.trim().toLowerCase();
  if (q) {
    list = list.filter(function (g) {
      return g.apps.some(function (a) { return a.pkg.toLowerCase().indexOf(q) >= 0; });
    });
  }
  var f = state.appFilter;
  if (f === "granted") list = list.filter(function (g) { return g.granted; });
  else if (f === "plain") list = list.filter(function (g) { return !g.granted && !g.configured; });
  else if (f === "system") list = list.filter(function (g) { return g.system; });
  list.sort(function (a, b) {
    var ra = a.granted ? 0 : (a.configured ? 1 : 2);
    var rb = b.granted ? 0 : (b.configured ? 1 : 2);
    if (ra !== rb) return ra - rb;
    return String(a.primary.pkg).localeCompare(String(b.primary.pkg), "en");
  });
  return list;
}

function renderApps() {
  if (LITE_MODE) { renderLiteSuperusers(); return; }
  var box = $("#users");
  clear(box);
  var list = filteredGroups();
  if (!state.apps.length) {
    var emp = el("div", "empty");
    emp.appendChild(ic("users"));
    emp.appendChild(el("div", null, "没读到任何 App"));
    box.appendChild(emp);
    return;
  }
  if (!list.length) {
    var e2 = el("div", "empty");
    e2.appendChild(ic("search"));
    e2.appendChild(el("div", null, "没有匹配的 App"));
    box.appendChild(e2);
    return;
  }
  list.forEach(function (g) { box.appendChild(appCard(g)); });
}

function appCard(g) {
  var card = el("div", "mod");
  var head = el("div", "mh");

  var ava = el("div", "ava");
  ava.appendChild(el("span", null, (String(g.primary.pkg).charAt(0) || "?").toUpperCase()));
  head.appendChild(ava);

  var mt = el("div", "mt");
  var nm = el("div", "m-name");
  nm.appendChild(el("span", null, g.primary.pkg));
  if (g.granted) nm.appendChild(tagNode("shield-check", "已授权", "ok"));
  if (g.configured && !g.granted) nm.appendChild(tagNode("settings-2", "已配置", "dim"));
  if (g.system) nm.appendChild(tagNode("cpu", "系统", "dim"));
  if (g.userId > 0) nm.appendChild(tagNode("users", "分身 " + g.userId, "dim"));
  mt.appendChild(nm);
  mt.appendChild(el("div", "m-meta",
    "uid " + g.uid + (g.apps.length > 1 ? " · 同组 " + g.apps.length + " 个包" : "")));
  if (g.apps.length > 1) {
    mt.appendChild(el("div", "m-desc", g.apps.map(function (a) { return a.pkg; }).join("\n")));
  }
  head.appendChild(mt);
  card.appendChild(head);

  var acts = el("div", "acts");
  var cfg = el("button", "btn");
  cfg.appendChild(ic("settings-2", "ic"));
  cfg.appendChild(el("span", null, "配置"));
  cfg.onclick = function () { openAppProfile(g); };
  acts.appendChild(cfg);
  acts.appendChild(el("div", "sp"));

  var sw = el("div", "sw" + (g.granted ? " on" : ""));
  sw.setAttribute("role", "switch");
  sw.title = g.granted ? "点击撤销 root" : "点击授予 root";
  sw.onclick = function () {
    if (state.busy) return;
    var want = !g.granted;
    if (!want) {
      confirmSheet("撤销 root 权限",
        "确定撤销「" + g.primary.pkg + "」的 root 权限？\n\n" +
        "撤销后它再调用 su 会被拒绝（非 root 配置会被重置为默认）。",
        "撤销", function () { setAppRoot(g, false); });
      return;
    }
    setAppRoot(g, true);
  };
  acts.appendChild(sw);
  card.appendChild(acts);
  return card;
}

function tagNode(iconName, text, kind) {
  var t = el("span", "tag" + (kind ? " t-" + kind : ""));
  t.appendChild(ic(iconName, "ic"));
  t.appendChild(el("span", null, text));
  return t;
}

function setAppRoot(g, allow) {
  state.busy = true;
  post("/api/app/root", { pkg: g.primary.pkg, uid: g.uid, allow: allow ? "1" : "0" })
    .then(function (r) {
      if (r.ok) { toast(r.message || "已切换"); setTimeout(loadApps, 500); }
      else { toast(r.error || "操作失败"); }
    })
    .catch(function (e) { toast(apiErrorText(e)); })
    .then(function () { state.busy = false; });
}

/* ── 单个 App 的 root 配置 ── */
function openAppProfile(g) {
  if (state.busy) return;
  state.busy = true;
  get("/api/app/profile", { pkg: g.primary.pkg, uid: g.uid })
    .then(function (r) {
      if (!r.ok || !r.profile) throw new Error(r.error || "读取失败");
      buildProfileSheet(g, r.profile);
    })
    .catch(function (e) { toast("读取失败：" + e.message); })
    .then(function () { state.busy = false; });
}

function fieldRow(label, node, hintText) {
  var wrap = el("div", "fld");
  var top = el("div", "fld-top");
  top.appendChild(el("div", "fld-k", label));
  top.appendChild(node);
  wrap.appendChild(top);
  if (hintText) wrap.appendChild(el("div", "fld-h", hintText));
  return wrap;
}

function textField(value, placeholder, numeric) {
  var i = el("input", "f-in");
  i.type = numeric ? "number" : "text";
  if (numeric) i.inputMode = "numeric";
  i.value = value == null ? "" : String(value);
  i.placeholder = placeholder || "";
  return i;
}

function selectField(options, value) {
  var s = el("select", "f-in");
  options.forEach(function (o) {
    var op = el("option");
    op.value = String(o.v);
    op.textContent = o.t;
    if (String(o.v) === String(value)) op.selected = true;
    s.appendChild(op);
  });
  return s;
}

function buildProfileSheet(g, p) {
  var body = el("div");
  var f = {
    allowSu: !!p.allowSu,
    rootUseDefault: p.rootUseDefault !== false,
    nonRootUseDefault: p.nonRootUseDefault !== false,
    umountModules: p.umountModules !== false
  };

  body.appendChild(el("div", "fld-h",
    g.primary.pkg + " · uid " + g.uid +
    (g.apps.length > 1 ? "（这一组 " + g.apps.length + " 个包共用一份配置）" : "")));

  // 授权开关
  var swAllow = el("div", "sw" + (f.allowSu ? " on" : ""));
  swAllow.setAttribute("role", "switch");
  swAllow.onclick = function () {
    f.allowSu = !f.allowSu;
    swAllow.classList.toggle("on", f.allowSu);
    sync();
  };
  body.appendChild(fieldRow("允许使用 root", swAllow,
    "关掉就是撤销 root（对应 App 详情页那个「允许 root」）"));

  // ── root 配置（允许 root 时才生效）──
  var rootBox = el("div", "fgroup");
  rootBox.appendChild(el("div", "fgroup-t", "root 配置"));
  var swRd = el("div", "sw" + (f.rootUseDefault ? " on" : ""));
  swRd.setAttribute("role", "switch");
  swRd.onclick = function () {
    f.rootUseDefault = !f.rootUseDefault;
    swRd.classList.toggle("on", f.rootUseDefault);
    sync();
  };
  rootBox.appendChild(fieldRow("使用默认配置", swRd,
    "默认 = uid/gid 都是 0、拥有全部能力、SELinux 域 u:r:ksu:s0"));

  var inUid = textField(p.rootUid, "0", true);
  var inGid = textField(p.rootGid, "0", true);
  var inGroups = textField((p.groups || []).join(","), "例：3003,1028", false);
  var inCtx = textField(p.context || "", "u:r:ksu:s0", false);
  var selNs = selectField([
    { v: 0, t: "继承（推荐）" },
    { v: 1, t: "全局" },
    { v: 2, t: "独立" }
  ], p.namespace == null ? 0 : p.namespace);
  var swNnp = el("div", "sw" + (p.noNewPrivs !== false ? " on" : ""));
  swNnp.setAttribute("role", "switch");

  var uidRow = fieldRow("root UID", inUid);
  var gidRow = fieldRow("root GID", inGid);
  var grpRow = fieldRow("附加组 ID", inGroups, "逗号分隔，最多 32 个");
  var ctxRow = fieldRow("SELinux 域", inCtx);
  var nsRow = fieldRow("命名空间", selNs, "独立命名空间 = root 看不到原本的挂载，多数情况用「继承」");
  swNnp.onclick = function () { swNnp.classList.toggle("on"); };
  var nnpRow = fieldRow("禁止在此身份下再提权", swNnp,
    "打开后，从这个 root 身份里再调 su 会被拒绝（更安全）");
  [uidRow, gidRow, grpRow, ctxRow, nsRow, nnpRow].forEach(function (r) { rootBox.appendChild(r); });
  body.appendChild(rootBox);

  // ── 非 root 配置（没授权 root 时才生效）──
  var nrBox = el("div", "fgroup");
  nrBox.appendChild(el("div", "fgroup-t", "非 root 配置"));
  var swNrd = el("div", "sw" + (f.nonRootUseDefault ? " on" : ""));
  swNrd.setAttribute("role", "switch");
  swNrd.onclick = function () {
    f.nonRootUseDefault = !f.nonRootUseDefault;
    swNrd.classList.toggle("on", f.nonRootUseDefault);
    sync();
  };
  nrBox.appendChild(fieldRow("使用默认配置", swNrd));
  var swUm = el("div", "sw" + (f.umountModules ? " on" : ""));
  swUm.setAttribute("role", "switch");
  swUm.onclick = function () {
    f.umountModules = !f.umountModules;
    swUm.classList.toggle("on", f.umountModules);
  };
  nrBox.appendChild(fieldRow("这个 App 不加载模块", swUm,
    "打开 = 它看不到你装的模块（隐藏 root 特征常用）"));
  body.appendChild(nrBox);

  body.appendChild(el("div", "fld-h",
    "说明：内核里 root 配置和非 root 配置只能存一份 —— 开了 root 就只保留 root 配置，" +
    "关掉 root 才轮到非 root 配置。"));

  // 按当前状态显示/隐藏
  function sync() {
    rootBox.style.display = f.allowSu ? "" : "none";
    nrBox.style.display = f.allowSu ? "none" : "";
    var custom = f.allowSu && !f.rootUseDefault;
    [uidRow, gidRow, grpRow, ctxRow, nsRow, nnpRow].forEach(function (r) {
      r.style.display = custom ? "" : "none";
    });
    if (f.allowSu && !f.rootUseDefault) f.noNewPrivs = swNnp.classList.contains("on");
  }
  sync();

  openSheet("root 配置", body, [
    { label: "取消" },
    {
      label: "保存", cls: "primary", onClick: function () {
        if (state.busy) return;
        state.busy = true;
        post("/api/app/profile", {
          pkg: g.primary.pkg,
          uid: g.uid,
          allowSu: f.allowSu ? "1" : "0",
          rootUseDefault: f.rootUseDefault ? "1" : "0",
          rootUid: String(parseInt(inUid.value, 10) || 0),
          rootGid: String(parseInt(inGid.value, 10) || 0),
          groups: inGroups.value || "",
          context: inCtx.value || "",
          namespace: selNs.value,
          noNewPrivs: swNnp.classList.contains("on") ? "1" : "0",
          nonRootUseDefault: f.nonRootUseDefault ? "1" : "0",
          umountModules: swUm.classList.contains("on") ? "1" : "0"
        }).then(function (r) {
          toast(r.message || r.error || (r.ok ? "已保存" : "保存失败"));
          if (r.ok) setTimeout(loadApps, 500);
        }).catch(function (e) { toast("保存失败：" + e.message); })
          .then(function () { state.busy = false; });
      }
    }
  ]);
}

/* ══════════════════════════════════════════════════════════════════
   超级用户日志（sulog）
   ══════════════════════════════════════════════════════════════════ */
var SULOG_LIMIT = 400;
var SULOG_TYPE = "all";
var sulogTimer = null;

function loadSulog() {
  if (LITE_MODE) return loadUltraSulog();
  var box = $("#sulog");
  skeleton(box);
  return get("/api/sulog", { limit: SULOG_LIMIT, q: state.sulogQuery }).then(function (r) {
    state.sulog = (r && r.lines) ? r.lines : [];
    state.sulogAvailable = (r && typeof r.available === "boolean") ? r.available : null;
    state.sulogReason = (r && r.reason) ? String(r.reason) : "";
    state.sulogEnabled = !!(r && r.enabled);
    state.sulogSupported = !(r && r.supported === false) && state.sulogAvailable !== false;
    renderSulog();
  }).catch(function (e) {
    if (isUnsupported(e)) { showUnsupported(box, "超级用户日志", e.message); return; }
    showReadError(box, e);
  });
}

function loadUltraSulog() {
  skeleton($("#sulog"));
  var readId = ++ultraUsers.logReadId;
  return Promise.all([loadLiteSuperusers(), apiGetSafe("/api/superuser/log")]).then(function (results) {
    if (readId !== ultraUsers.logReadId) return null;
    ultraUsers.logResult = results[1];
    var d = validData(results[1]);
    if (d && typeof d.text === "string") {
      state.sulog = d.text ? d.text.split(/\r?\n/) : [];
      ultraUsers.logClearUnverified = false;
    }
    renderUltraSulog();
    return { catalog: results[0], log: d };
  });
}
function setUltraLogging(want) {
  if (ultraUsers.loggingPending !== null) return Promise.resolve();
  var d = ultraUsers.catalog;
  if (!d || ultraUsers.error || ultraUsers.loggingUnverified || d.capabilities.logging !== true || typeof d.loggingEnabled !== "boolean") return Promise.resolve();
  ultraUsers.loggingPending = want;
  ++ultraUsers.logReadId;
  renderUltraSulog();
  return apiPostSafe("/api/superuser/logging", { enabled: want, confirm: true }).then(function (write) {
    return loadUltraSulog().then(function (r) {
      ultraUsers.loggingPending = null;
      if (!r || !r.catalog) ultraUsers.loggingUnverified = true;
      renderUltraSulog();
      if (validData(write) && write.data.enabled === want && r && r.catalog && r.catalog.loggingEnabled === want) toast(want ? "已开启日志记录" : "已关闭日志记录");
      else toast(r && r.catalog ? "操作没有成功，已同步当前状态" : "状态待确认，请刷新重试");
    });
  });
}
function clearUltraLog() {
  if (ultraUsers.logClearPending) return Promise.resolve();
  var log = validData(ultraUsers.logResult);
  if (!ultraUsers.catalog || ultraUsers.error || ultraUsers.logClearUnverified || ultraUsers.catalog.capabilities.logging !== true || !log || typeof log.text !== "string") return Promise.resolve();
  ultraUsers.logClearPending = true;
  ++ultraUsers.logReadId;
  renderUltraSulog();
  return apiPostSafe("/api/superuser/log", { action: "clear", confirm: true }).then(function (write) {
    return loadUltraSulog().then(function (r) {
      ultraUsers.logClearPending = false;
      if (!r || !r.log) ultraUsers.logClearUnverified = true;
      renderUltraSulog();
      toast(validData(write) && write.data.cleared === true && r && r.log ? "已清空日志" : "清空未完成，请刷新重试");
    });
  });
}
function renderUltraSulog() {
  var box = $("#sulog");
  clear(box);
  var d = ultraUsers.catalog, log = validData(ultraUsers.logResult);
  box.appendChild(el("div", "hint", "MapleSU Ultra 工作环境日志"));
  var card = group(box, null);
  var enabled = d && typeof d.loggingEnabled === "boolean" ? d.loggingEnabled : null;
  var pending = ultraUsers.loggingPending !== null;
  var end = badge("状态待确认", "dim");
  if (d && d.capabilities.logging === true && enabled !== null) {
    var want = pending ? ultraUsers.loggingPending : enabled;
    var sw = el("button", "sw" + (want ? " on" : ""));
    sw.type = "button"; sw.setAttribute("role", "switch");
    sw.setAttribute("aria-label", "记录工作环境日志"); swState(sw, want);
    sw.disabled = pending || !!ultraUsers.error || ultraUsers.loggingUnverified;
    if (pending) sw.setAttribute("aria-busy", "true");
    sw.onclick = function () { if (!sw.disabled) setUltraLogging(!enabled); };
    var touch = el("div", "su-switch-target"); touch.appendChild(sw);
    end = touch;
  }
  card.appendChild(pref({ icon: "scroll-text", title: "记录工作环境日志", summary: pending ? "正在保存…" : (ultraUsers.loggingUnverified ? "状态待确认，请刷新重试" : "保留当前工作环境的运行记录"), end: end }));
  if (d && d.capabilities.logging === true) {
    var clearButton = el("button", "btn danger", ultraUsers.logClearPending ? "正在清空…" : "清空");
    clearButton.disabled = ultraUsers.logClearPending || ultraUsers.logClearUnverified || !!ultraUsers.error || !log || typeof log.text !== "string";
    if (ultraUsers.logClearPending) clearButton.setAttribute("aria-busy", "true");
    clearButton.onclick = function () {
      confirmSheet("清空工作环境日志？", "已有记录会被清空，之后的记录仍会继续写入。", "清空", clearUltraLog);
    };
    card.appendChild(pref({ icon: "trash", title: "清空日志", end: clearButton }));
  }
  if (!log || typeof log.text !== "string") {
    box.appendChild(noticeCard("暂时读不到日志", "请确认服务正常后重试", "warn"));
    return;
  }
  if (log.truncated) box.appendChild(el("div", "hint", "仅显示最近的日志记录"));
  if (ultraUsers.logClearUnverified) box.appendChild(noticeCard("日志状态待确认", "请刷新后再操作", "warn"));
  var query = state.sulogQuery.trim().toLowerCase();
  var lines = state.sulog.filter(function (line) { return !query || line.toLowerCase().indexOf(query) >= 0; }).slice(-SULOG_LIMIT);
  if (!lines.length) {
    box.appendChild(el("div", "empty", query ? "没有匹配的日志" : "还没有工作环境日志"));
    return;
  }
  box.appendChild(el("pre", "card environment-log", lines.join("\n")));
}

function sulogTypeName(t) {
  if (t === "ROOT_EXECVE") return "提权执行";
  if (t === "SUCOMPAT") return "su 兼容";
  if (t === "IOCTL_GRANT_ROOT") return "授权 root";
  if (t === "gate") return "权限检查";
  if (t === "request") return "授权请求";
  if (t === "decision") return "授权结果";
  if (t === "exec") return "执行命令";
  if (t === "exit") return "执行结束";
  if (t === "cancel") return "已取消";
  return "记录";
}

function parseSulogLine(line) {
  // MapleSU_Ultra 的日志是 JSONL（SuAuditLog.line）；其余仍是 key=value
  var text = String(line == null ? "" : line).trim();
  if (text.charAt(0) === "{") {
    try {
      var j = JSON.parse(text);
      var pkgs = Array.isArray(j.callerPackages) ? j.callerPackages : [];
      var failed = j.decision ? j.decision === "deny"
        : (j.exitCode !== null && j.exitCode !== undefined && String(j.exitCode) !== "0");
      return {
        json: true, type: j.event, retval: failed ? "1" : "0",
        ts_ns: j.ts ? Number(j.ts) * 1e6 : 0,
        uid: j.callerUid, comm: pkgs[0], file: j.argv0
      };
    } catch (e) { /* 损坏的一行按普通文本处理 */ }
  }
  var o = {};
  var re = /([A-Za-z_][A-Za-z0-9_]*)=("(?:[^"\\]|\\.)*"|\S+)/g;
  var m;
  while ((m = re.exec(line)) !== null) {
    var v = m[2];
    if (v.charAt(0) === '"') v = v.slice(1, -1).replace(/\\(.)/g, "$1");
    o[m[1]] = v;
  }
  return o;
}

function fmtTime(ns) {
  var n = Number(ns);
  if (!n) return "";
  var d = new Date(n / 1e6);
  function p(x) { return (x < 10 ? "0" : "") + x; }
  return p(d.getMonth() + 1) + "-" + p(d.getDate()) + " " +
         p(d.getHours()) + ":" + p(d.getMinutes()) + ":" + p(d.getSeconds());
}

function renderSulog() {
  if (LITE_MODE) { renderUltraSulog(); return; }
  var box = $("#sulog");
  clear(box);
  if (state.sulogAvailable === false) {
    if (state.sulogReason) console.warn("[webui] sulog", state.sulogReason);
    var none = el("div", "empty");
    none.appendChild(ic("scroll-text"));
    none.appendChild(el("div", null,
      /READ_FAILED/.test(state.sulogReason) ? "日志暂时读不到，请稍后再试" : "还没有日志记录"));
    box.appendChild(none);
    return;
  }

  // 开关 + 清空
  var card = group(box, null);
  var end;
  if (!state.sulogSupported) {
    end = badge("不支持", "warn");
  } else {
    var sw = el("div", "sw" + (state.sulogEnabled ? " on" : ""));
    sw.setAttribute("role", "switch");
    sw.setAttribute("aria-label", "记录超级用户日志");
    swState(sw, state.sulogEnabled);
    swKeys(sw);
    sw.onclick = function () {
      if (state.busy) return;
      var want = !state.sulogEnabled;
      state.busy = true;
      post("/api/sulog/enable", { enable: want ? "1" : "0" }).then(function (x) {
        toast(x.message || (x.error ? "操作没有成功，请稍后重试" : "已切换"));
        setTimeout(loadSulog, 500);
      }).catch(function (e) { toast(apiErrorText(e)); })
        .then(function () { state.busy = false; });
    };
    end = sw;
  }
  card.appendChild(pref({ icon: "scroll-text", title: "记录超级用户日志", end: end }));
  var cbtn = el("button", "btn danger");
  cbtn.appendChild(el("span", null, "清空"));
  cbtn.onclick = function () {
    confirmSheet("清空超级用户日志", "已有记录会被清空，之后的记录继续写入。", "清空", function () {
      state.busy = true;
      post("/api/sulog/clear", {}).then(function (x) {
        toast(x.message || (x.error ? "操作没有成功，请稍后重试" : "已清空"));
        setTimeout(loadSulog, 400);
      }).catch(function (e) { toast(apiErrorText(e)); })
        .then(function () { state.busy = false; });
    });
  };
  card.appendChild(pref({ icon: "trash", title: "清空日志", end: cbtn }));

  appendSulogLines(box);
}
function appendSulogLines(box) {
  var query = state.sulogQuery.trim().toLowerCase();
  var list = state.sulog.filter(function (line) { return !query || line.toLowerCase().indexOf(query) >= 0; });
  if (SULOG_TYPE !== "all") {
    list = list.filter(function (line) { return parseSulogLine(line).type === SULOG_TYPE; });
  }
  list = list.slice(0, SULOG_LIMIT);
  if (!list.length) {
    var emp = el("div", "empty");
    emp.appendChild(ic("scroll-text"));
    emp.appendChild(el("div", null, state.sulog.length ? "当前筛选下没有记录" : "还没有日志记录"));
    box.appendChild(emp);
    return;
  }

  var wrap = el("div", "loglist");
  list.forEach(function (line) {
    var o = parseSulogLine(line);
    var it = el("div", "logitem");
    var top = el("div", "lgtop");
    top.appendChild(badge(sulogTypeName(o.type), o.retval === "0" ? "ok" : "bad"));
    top.appendChild(el("span", "lgtime", fmtTime(o.ts_ns)));
    if (o.comm) top.appendChild(el("span", "lgcomm", o.comm));
    it.appendChild(top);
    var det = [];
    if (o.uid !== undefined && o.uid !== null) det.push("UID " + o.uid);
    if (o.pid) det.push("PID " + o.pid);
    if (o.file) det.push(o.file);
    if (o.argv) det.push(o.argv);
    if (det.length || !o.json) it.appendChild(el("div", "lgb", det.join(" · ") || line));
    wrap.appendChild(it);
  });
  box.appendChild(wrap);
}

/* ══════════════════════════════════════════════════════════════════
   网页上传装模块
   ══════════════════════════════════════════════════════════════════ */
function uploadSheet(file) {
  var body = el("div");
  var bar = el("div", "prog");
  var fill = el("i");
  bar.appendChild(fill);
  body.appendChild(el("div", "fld-h", file.name + " · " + (file.size / 1048576).toFixed(2) + " MB"));
  body.appendChild(bar);
  var status = el("div", "fld-h", "正在上传…");
  body.appendChild(status);
  // 上传中也能关掉弹层（关掉不会中断上传，装完会 toast 提示）
  openSheet("安装模块", body, [{ label: "关闭" }]);
  fill.style.width = "0%";
  status.textContent = "正在上传…";

  var xhr = new XMLHttpRequest();
  xhr.open("POST", P + "/api/module/install?name=" + enc(file.name), true);
  xhr.setRequestHeader("Content-Type", "application/octet-stream");
  xhr.upload.onprogress = function (e) {
    if (e.lengthComputable) {
      var pct = Math.round(e.loaded * 100 / e.total);
      fill.style.width = pct + "%";
      status.textContent = "正在上传 " + pct + "%";
    }
  };
  xhr.onload = function () {
    var r = null;
    try { r = JSON.parse(xhr.responseText); } catch (e) {}
    if (r && r.ok) {
      fill.style.width = "100%";
      status.textContent = r.message || "安装完成";
      toast(r.message || "安装完成");
      setTimeout(function () { closeSheet(); loadModules(); }, 1200);
    } else {
      status.textContent = "安装失败：" + ((r && r.error) || ("HTTP " + xhr.status));
      toast("安装失败：" + ((r && r.error) || ("HTTP " + xhr.status)));
    }
  };
  xhr.onerror = function () {
    status.textContent = "上传中断（服务或连接断了）";
    toast("上传中断");
  };
  xhr.send(file);
}

function pickZip() {
  var inp = el("input");
  inp.type = "file";
  inp.accept = ".zip,application/zip";
  inp.style.display = "none";
  inp.onchange = function () {
    var f = inp.files && inp.files[0];
    document.body.removeChild(inp);
    if (!f) return;
    if (!/\.zip$/i.test(f.name)) { toast("只能传 .zip 模块包"); return; }
    confirmSheet("安装模块",
      "确定安装「" + f.name + "」？\n\n" +
      (f.size / 1048576).toFixed(2) + " MB\n" +
      "安装脚本会以 root 身份运行（模块自带），装完需要重启手机才生效。",
      "安装", function () { uploadSheet(f); });
  };
  document.body.appendChild(inp);
  inp.click();
}


var LICENSES = [
  { file: "KernelSU-GPL-3.0.txt",  name: "页面逻辑来源：KernelSU/7kimisu webadmin", tag: "GPL-3.0-or-later" },
  { file: "Lucide-ISC.txt",        name: "Lucide 图标",          tag: "ISC" },
  { file: "Catppuccin-MIT.txt",    name: "Catppuccin 配色",      tag: "MIT" },
  { file: "Nord-MIT.txt",          name: "Nord 配色",            tag: "MIT" },
  { file: "Dracula-MIT.txt",       name: "Dracula 配色",         tag: "MIT" }
];

function renderAbout() {
  var box = $("#about"); if (!box) return;
  clear(box);

  var c1 = group(box, "关于");
  c1.appendChild(row("package", "网页管理器", "MapleSU 内置"));
  // 隐藏状态为真或未知时不显示模式信息；只用已有快照，不增加探测请求。
  var hs = dataOf(homeState.status);
  var hh = dataOf(homeState.hidden);
  if (hs && hs.hidden === false && hh && normalizeHidden(hh).hidden === false) {
    c1.appendChild(row("package", "当前模式", "MapleSU_Ultra", true, "ok"));
  }
  c1.appendChild(pref({ icon: "cpu", title: "页面来源", summary: "7kimisu webadmin（GPL-3.0-or-later）" }));
  c1.appendChild(pref({ icon: "shield", title: "访问范围", summary: "仅本机 127.0.0.1" }));
  c1.appendChild(pref({ icon: "key-round", title: "访问方式", summary: "专属链接，地址里带 256 位随机密钥" }));
  c1.appendChild(pref({ icon: "ban", title: "防止猜解", summary: "密钥随机生成，无法被猜出来" }));
  c1.appendChild(pref({ icon: "globe", title: "外部请求", summary: "没有，全部内嵌，断网也能使用" }));

  var c2 = group(box, "第三方资源与许可");
  LICENSES.forEach(function (l) {
    c2.appendChild(pref({
      title: l.name, summary: l.tag, arrow: true,
      onClick: function () { showLicense(l); }
    }));
  });
  box.appendChild(el("div", "hint", "以上资源随安装包一起分发，页面不加载任何外部地址。"));
}

function showLicense(l) {
  var pre = el("pre", "out", "读取中…");
  openSheet(l.name + " · " + l.tag, pre, [{ label: "关闭" }]);
  fetch(P + "/a/licenses/" + l.file, { cache: "no-store" })
    .then(function (r) { if (!r.ok) throw new Error("HTTP " + r.status); return r.text(); })
    .then(function (txt) { pre.textContent = txt; })
    .catch(function (e) { console.warn("[webui]", e.message); pre.textContent = "读取失败，请稍后重试"; });
}

/* 复制到剪贴板：navigator.clipboard 不可用时退回老办法 */
function fallbackCopy(text, done) {
  var ta = el("textarea");
  ta.value = text;
  ta.style.position = "fixed";
  ta.style.opacity = "0";
  document.body.appendChild(ta);
  ta.select();
  var ok = false;
  try { ok = document.execCommand("copy"); } catch (e) { ok = false; }
  document.body.removeChild(ta);
  if (ok) done(); else toast("复制失败，请长按地址手动复制");
}

/* ── 标签切换 / 刷新 ── */
var TABS = ["home", "users", "modules", "settings", "hidden"];
var TAB_TITLES = { home: "MapleSU", users: "超级用户", modules: "模块", settings: "设置", hidden: "隐藏与恢复" };

/* 底栏：高亮胶囊只走 transform（百分比相对自身宽度，刚好一格一格） */
function movePill(pos) {
  var pill = $("#dock-pill");
  if (pill) pill.style.transform = "translate3d(" + (pos * 100) + "%,0,0)";
}
function markDock(index) {
  var btns = document.querySelectorAll("nav button");
  for (var i = 0; i < btns.length; i++) {
    var on = i === index;
    btns[i].classList.toggle("on", on);
    btns[i].setAttribute("aria-selected", on ? "true" : "false");
  }
}

var tabRequests = Object.create(null);
var tabScroll = Object.create(null);
function loadTab(tab) {
  if (restoreState.pending || restoreState.completed) return Promise.resolve();
  if (tabRequests[tab]) return tabRequests[tab];
  var loaders = { home: loadHome, users: function () { return showUsersView(usersView); },
    modules: loadModules, settings: loadSettings, hidden: loadRestoreCenter };
  // Keep rendered content while refreshing; returning to a tab must not freeze old state.
  var request = Promise.resolve(loaders[tab]());
  tabRequests[tab] = request.finally(function () { delete tabRequests[tab]; });
  return tabRequests[tab];
}
function show(tab, fromHistory) {
  if (restoreState.pending || restoreState.completed) return Promise.resolve();
  if (TABS.indexOf(tab) < 0) tab = "home";
  var changed = state.tab !== tab;
  tabScroll[state.tab] = window.pageYOffset || 0;
  if (changed && !fromHistory) {
    if (sheetBackPending) tabAfterSheetClose = tab;
    else history.pushState({ maplesu: true, tab: tab, sheet: false }, "", location.href);
  }
  state.tab = tab;
  TABS.forEach(function (t) {
    var sec = $("#t-" + t);
    if (sec) sec.classList.toggle("on", t === tab);
  });
  var index = TABS.indexOf(tab);
  markDock(index); movePill(index);
  var title = TAB_TITLES[tab] || "";
  $("#large-title").textContent = title; $("#bar-title").textContent = title;
  window.scrollTo(0, tabScroll[tab] || 0);
  return loadTab(tab);
}

/* 大标题随滚动淡出，顶栏淡入玻璃：scroll 只记一帧，rAF 里写 opacity / class */
function initScrollBar() {
  var html = document.documentElement;
  var head = $("#large-head");
  var ticking = false;
  var RANGE = 52;
  function apply() {
    ticking = false;
    var y = window.pageYOffset || 0;
    var t = Math.min(1, Math.max(0, y / RANGE));
    head.style.opacity = String(1 - t);
    html.classList.toggle("scrolled", y > RANGE * 0.7);
  }
  window.addEventListener("scroll", function () {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(apply);
  }, { passive: true });
  apply();
}

/* 底栏拖动：手指在胶囊上滑动时高亮跟手，松手吸附到最近一格 */
function initDockDrag() {
  var dock = $("#dock");
  var btns = document.querySelectorAll("nav button");
  var start = null;
  function posAt(x) {
    var r = dock.getBoundingClientRect();
    var cell = (r.width - 8) / btns.length;
    return Math.max(0, Math.min(btns.length - 1, (x - r.left - 4) / cell - 0.5));
  }
  dock.addEventListener("pointerdown", function (e) {
    if (restoreState.pending || restoreState.completed) return;
    start = { x: e.clientX, id: e.pointerId, moved: false };
  });
  dock.addEventListener("pointermove", function (e) {
    if (!start || e.pointerId !== start.id) return;
    if (!start.moved) {
      if (Math.abs(e.clientX - start.x) < 8) return;
      start.moved = true;
      dock.classList.add("drag");
      try { dock.setPointerCapture(e.pointerId); } catch (err) {}
    }
    var pos = posAt(e.clientX);
    movePill(pos);
    markDock(Math.round(pos));
  });
  function finish(e) {
    if (!start || e.pointerId !== start.id) return;
    var moved = start.moved;
    start = null;
    dock.classList.remove("drag");
    if (!moved) return;
    var index = Math.round(posAt(e.clientX));
    if (e.type === "pointerup" && TABS[index] !== state.tab) show(TABS[index]);
    else { markDock(TABS.indexOf(state.tab)); movePill(TABS.indexOf(state.tab)); }
  }
  dock.addEventListener("pointerup", finish);
  dock.addEventListener("pointercancel", finish);
}

function refresh() {
  if (restoreState.pending || restoreState.completed) return Promise.resolve();
  refreshRestoreHandover();
  var b = $("#refresh");
  if (b && b.getAttribute("aria-busy") === "true") return tabRequests[state.tab] || Promise.resolve();
  if (b) { b.classList.add("spin"); b.setAttribute("aria-busy", "true"); }
  return loadTab(state.tab).finally(function () {
    if (b) { b.classList.remove("spin"); b.removeAttribute("aria-busy"); }
  });
}

/* ── 初始化 ── */
function initSprite() {
  // 图标 sprite 是内嵌资源（Lucide，ISC），从同源取回来注入 DOM
  return fetch(P + "/a/icons.svg", { cache: "force-cache" })
    .then(function (r) { return r.ok ? r.text() : ""; })
    .then(function (txt) {
      // 注意：icons.svg 开头是许可注释，所以不能要求它以 "<svg" 开头
      if (txt && txt.indexOf("<symbol") > 0) {
        var holder = el("div");
        holder.style.display = "none";
        holder.innerHTML = txt;          // 静态资源，内容由我们自己打包
        document.body.appendChild(holder);
      }
    })
    .catch(function () { /* 图标失败不影响功能 */ });
}

(function boot() {
  try {
    var style = localStorage.getItem("maplesu-web-style");
    if (style === "material" || style === "miuix") state.style = style;
    var saved = localStorage.getItem("7k-webadmin-theme");
    if (saved && THEMES.some(function (t) { return t.id === saved; })) state.theme = saved;
  } catch (e) {}
  applyTheme();
  // 低内存设备不做毛玻璃，底栏退回半透明底
  if (navigator.deviceMemory && navigator.deviceMemory <= 2) {
    document.documentElement.classList.add("no-glass");
  }
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", function () {
    if (state.theme === "auto") applyTheme();
  });

  // 模块页工具条（工具条与列表分开，避免刷新时把输入框清掉）
  var sBox = el("div", "toolbar");
  var sWrap = el("div", "search");
  sWrap.appendChild(ic("search"));
  var sInput = el("input");
  sInput.type = "search";
  sInput.placeholder = "搜索模块名称或作者";
  sInput.oninput = function () { state.search = sInput.value; renderModules(); };
  sWrap.appendChild(sInput);
  sBox.appendChild(sWrap);
  // Lite 不支持网页上传安装（POST /api/module/install → 501）：不渲染入口
  if (!LITE_MODE) {
    var upBtn = el("button", "btn primary");
    upBtn.appendChild(ic("upload", "ic"));
    upBtn.appendChild(el("span", null, "上传安装"));
    upBtn.onclick = pickZip;
    sBox.appendChild(upBtn);
  }
  $("#mod-toolbar").appendChild(sBox);

  var chips = el("div", "chips");
  [{ id: "default", label: "默认顺序" }, { id: "enabled", label: "启用优先" }, { id: "name", label: "按名称" }]
    .forEach(function (c) {
      var b = el("button", "chip" + (state.sort === c.id ? " on" : ""), c.label);
      b.onclick = function () {
        state.sort = c.id;
        var all = chips.querySelectorAll(".chip");
        for (var i = 0; i < all.length; i++) all[i].classList.remove("on");
        b.classList.add("on");
        renderModules();
      };
      chips.appendChild(b);
    });
  $("#mod-toolbar").appendChild(chips);

  // ── 超级用户页工具条 ──
  var uBox = el("div", "toolbar");
  var uWrap = el("div", "search");
  uWrap.appendChild(ic("search"));
  var uInput = el("input");
  uInput.type = "search";
  uInput.placeholder = "搜索应用包名或 UID";
  uInput.setAttribute("aria-label", "搜索超级用户应用");
  uInput.oninput = function () { state.appSearch = uInput.value; renderApps(); };
  uWrap.appendChild(uInput);
  uBox.appendChild(uWrap);
  var appTools = el("div"); appTools.id = "app-tools";
  $("#user-toolbar").appendChild(appTools);
  appTools.appendChild(uBox);
  chipsRow(appTools, [
    { id: "all", label: "全部" },
    { id: "granted", label: "已授权" },
    { id: "plain", label: "未授权" }
  ].concat(LITE_MODE ? [] : [{ id: "system", label: "系统应用" }]), function () { return state.appFilter; }, function (id) {
    state.appFilter = id;
    renderApps();
  });

  // ── 日志页工具条 ──
  var lBox = el("div", "toolbar");
  var lWrap = el("div", "search");
  lWrap.appendChild(ic("search"));
  var lInput = el("input");
  lInput.type = "search";
  lInput.placeholder = LITE_MODE ? "搜索工作环境日志" : "搜索 uid / 包名 / 路径 / 命令";
  lInput.setAttribute("aria-label", LITE_MODE ? "搜索工作环境日志" : "搜索超级用户日志");
  lInput.oninput = function () {
    var v = lInput.value;
    clearTimeout(sulogTimer);
    sulogTimer = setTimeout(function () { state.sulogQuery = v; if (LITE_MODE) renderUltraSulog(); else loadSulog(); }, 200);
  };
  lWrap.appendChild(lInput);
  lBox.appendChild(lWrap);
  $("#sulog-toolbar").appendChild(lBox);
  if (!LITE_MODE) chipsRow($("#sulog-toolbar"), [
    { id: "all", label: "全部" },
    { id: "ROOT_EXECVE", label: "提权执行" },
    { id: "SUCOMPAT", label: "su 兼容" },
    { id: "IOCTL_GRANT_ROOT", label: "授权 root" }
  ], function () { return SULOG_TYPE; }, function (id) {
    SULOG_TYPE = id;
    renderSulog();
  });
  chipsRow($("#sulog-toolbar"), [
    { id: "200", label: "最近 200 条" },
    { id: "400", label: "最近 400 条" },
    { id: "1000", label: "最近 1000 条" }
  ], function () { return String(SULOG_LIMIT); }, function (id) {
    SULOG_LIMIT = parseInt(id, 10);
    if (LITE_MODE) renderUltraSulog(); else loadSulog();
  });

  var navBtns = document.querySelectorAll("nav button");
  for (var i = 0; i < navBtns.length; i++) {
    (function (b) { b.onclick = function () { show(b.getAttribute("data-tab")); }; })(navBtns[i]);
  }
  // 模块网页（iframe）里发过来的消息：弹提示 / 请求关闭
  window.addEventListener("message", function (ev) {
    if (ev.origin && location.origin && ev.origin !== location.origin) return;
    var d = ev.data;
    if (!d || d.source !== "7kimisu-webui") return;
    if (d.type === "toast") toast(String(d.message || ""));
    if (d.type === "exit") closeSheet();
  });

  window.addEventListener("pageshow", refreshRestoreHandover);
  document.addEventListener("visibilitychange", function () {
    if (!document.hidden) refreshRestoreHandover();
  });
  $("#refresh").onclick = refresh;
  $("#theme").onclick = themeSheet;
  $("#mask").onclick = function (e) { if (e.target === this) closeSheet(); };
  document.addEventListener("keydown", function (e) { if (e.key === "Escape") closeSheet(); });
  initScrollBar();
  initDockDrag();

  if (LITE_MODE) {
    var viewChips = el("div", "chips");
    [{ id: "users", label: "授权列表" }, { id: "sulog", label: "日志" }].forEach(function (c) {
      var b = el("button", "chip" + (usersView === c.id ? " on" : ""), c.label);
      b.setAttribute("data-view", c.id);
      b.onclick = function () { showUsersView(c.id); };
      viewChips.appendChild(b);
    });
    $("#user-toolbar").insertBefore(viewChips, $("#user-toolbar").firstChild);
  }

  history.replaceState({ maplesu: true, tab: "home", sheet: false }, "", location.href);
  window.addEventListener("popstate", function (event) {
    if (sheetBackPending) {
      sheetBackPending = false;
      if (tabAfterSheetClose || $("#mask").classList.contains("on")) {
        history.pushState({ maplesu: true, tab: state.tab, sheet: $("#mask").classList.contains("on") }, "", location.href);
        tabAfterSheetClose = null;
        return;
      }
    }
    $("#mask").classList.remove("on");
    var target = event.state && event.state.maplesu ? event.state.tab : "home";
    if (target !== state.tab) show(target, true);
  });
  // Content is independent of the decorative sprite request.
  initSprite();
  show("home");
  refreshRestoreHandover();
})();
