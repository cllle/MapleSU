/* MapleSU browser transport for the fixed KernelSU annotated object surface (GPL-3.0-or-later). */
(function () {
  "use strict";
  if (window.ksu) return;
  var BASE = window.__7K_BASE__ || "";
  var MOD = window.__7K_MODULE__ || { id: "" };
  var session, schema, closed = false, pollTimer, pending = Object.create(null);

  function body(action, fields) {
    return JSON.stringify(Object.assign({ action: action, id: MOD.id, session: session }, fields || {}));
  }
  function reply(xhr) {
    var value = JSON.parse(xhr.responseText);
    if (xhr.status !== 200 || value.ok === false) throw new Error(value.reason || value.error || ("HTTP " + xhr.status));
    return value;
  }
  function sync(action, fields) {
    if (closed) throw new Error("MODULE_WEB_SESSION_RETIRED");
    var xhr = new XMLHttpRequest();
    xhr.open("POST", BASE + "/api/module/bridge", false);
    xhr.setRequestHeader("Content-Type", "application/json");
    xhr.send(body(action, fields));
    return reply(xhr);
  }
  function post(action, fields, keepalive) {
    return fetch(BASE + "/api/module/bridge", {
      method: "POST", cache: "no-store", credentials: "same-origin", keepalive: !!keepalive,
      headers: { "Content-Type": "application/json" }, body: body(action, fields)
    }).then(function (response) {
      return response.json().then(function (value) {
        if (!response.ok || value.ok === false) throw new Error(value.reason || value.error || ("HTTP " + response.status));
        return value;
      });
    });
  }
  function callback(name) {
    if (Object.prototype.hasOwnProperty.call(window, name)) return window[name];
    var parts = String(name).split("."), target = window;
    if (parts.some(function (part) { return !/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(part) || /^(?:__proto__|prototype|constructor)$/.test(part); })) return undefined;
    parts.forEach(function (part) { target = target == null ? undefined : target[part]; });
    return target;
  }
  function finishChild(child, code, message) {
    if (!child || typeof child.emit !== "function") return;
    child.emit("exit", code);
    if (code !== 0) { var error = new Error(message || ""); error.exitCode = code; child.emit("error", error); }
  }
  function fail(error) {
    Object.keys(pending).forEach(function (name) {
      var target = callback(name), kind = pending[name];
      delete pending[name];
      try {
        if (kind === "exec" && typeof target === "function") target(-1, "", String(error));
        else if (kind === "spawn") finishChild(target, -1, String(error));
      } catch (callbackError) { console.error(callbackError); }
    });
    console.error(error);
    closeDocument();
  }
  function closeDocument() {
    if (closed) return;
    closed = true;
    clearTimeout(pollTimer);
    if (session) post("close", {}, true).catch(function () {});
    pending = Object.create(null);
  }
  function download(event) {
    var parts = [];
    function read() {
      if (closed) return Promise.reject(new Error("MODULE_WEB_SESSION_RETIRED"));
      return post("download", { download: event.id }).then(function (chunk) {
        if (chunk.base64) {
          var binary = atob(chunk.base64), bytes = new Uint8Array(binary.length);
          for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
          parts.push(bytes);
        }
        if (!chunk.done) return read();
        if (closed) return;
        var url = URL.createObjectURL(new Blob(parts, { type: event.mimeType }));
        var anchor = document.createElement("a");
        anchor.href = url; anchor.download = event.fileName;
        document.body.appendChild(anchor); anchor.click(); anchor.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 10000);
      });
    }
    read().catch(function (error) {
      if (!closed) post("cancelDownload", { download: event.id }).catch(function () {});
      console.error(error);
    });
  }
  function events(list) {
    (list || []).forEach(function (event) {
      if (closed) return;
      try {
        var target = callback(event.callback);
        if (event.kind === "exec") {
          delete pending[event.callback];
          if (typeof target === "function") target(event.code, event.stdout, event.stderr);
        } else if (event.kind === "data") {
          if (target && target[event.stream]) target[event.stream].emit("data", event.value);
        } else if (event.kind === "exit") {
          delete pending[event.callback]; finishChild(target, event.code, event.stderr);
        } else if (event.kind === "toast") {
          parent.postMessage({ source: "7kimisu-webui", type: "toast", message: event.message }, window.location.origin);
        } else if (event.kind === "exitPage") {
          closeDocument(); parent.postMessage({ source: "7kimisu-webui", type: "exit" }, window.location.origin);
        } else if (event.kind === "openExternal") {
          var external = new URL(event.url);
          if (/^https?:$/.test(external.protocol)) window.open(external.href, "_blank", "noopener,noreferrer");
        } else if (event.kind === "download") download(event);
        else if (event.kind === "fullScreen") {
          var change = event.enable ? document.documentElement.requestFullscreen() : document.exitFullscreen();
          if (change && change.catch) change.catch(console.error);
        } else if (event.kind === "edgeToEdge") {
          var viewport = document.querySelector('meta[name="viewport"]');
          if (!viewport) { viewport = document.createElement("meta"); viewport.name = "viewport"; viewport.content = "width=device-width,initial-scale=1"; document.head.appendChild(viewport); }
          viewport.content = viewport.content.replace(/,?\s*viewport-fit\s*=\s*[^,]+/g, "") + ",viewport-fit=" + (event.enable ? "cover" : "auto");
        }
      } catch (error) { console.error(error); }
    });
  }
  function encode(value) {
    if (value === null) return ["null"];
    var type = typeof value;
    if (type === "number") return [type, String(value)];
    if (type === "string" || type === "boolean") return [type, value];
    return [type === "undefined" ? "undefined" : "object"];
  }
  function object(root, path, type) {
    var impl = Object.create(null), singletons = Object.create(null);
    Object.keys(schema[type]).forEach(function (name) {
      impl[name] = function () {
        if (root === "ksu" && path.length === 0 && name === "exec" && arguments.length >= 3 &&
            (arguments[2] === undefined || arguments[2] === null || arguments[2] === "")) {
          return sync("syncExec", { command: String(arguments[0]), options: typeof arguments[1] === "string" ? arguments[1] : "{}" }).stdout;
        }
        // Legacy saves may be much larger than one bounded HTTP request. Keep
        // the native contract, transporting the same bytes through its chunk API.
        if (root === "ksu_download" && path.length === 0 && name === "save" &&
            typeof arguments[0] === "string" && arguments[0].length > 512 * 1024) {
          var data = arguments[0], id = impl.startChunkedDownload(arguments[1], null);
          if (!id) throw new Error("MODULE_DOWNLOAD_START_FAILED");
          try {
            for (var offset = 0; offset < data.length; offset += 512 * 1024) {
              if (!impl.writeDownloadChunk(id, data.slice(offset, offset + 512 * 1024))) throw new Error("MODULE_DOWNLOAD_WRITE_FAILED");
            }
            if (!impl.completeChunkedDownload(id)) throw new Error("MODULE_DOWNLOAD_COMPLETE_FAILED");
          } catch (error) { impl.cancelChunkedDownload(id); throw error; }
          return;
        }
        var args = Array.prototype.map.call(arguments, encode), nameOfCallback;
        if (root === "ksu" && path.length === 0 && (name === "spawn" || name === "exec") && args.length > 1) {
          nameOfCallback = arguments[arguments.length - 1];
          if (typeof nameOfCallback === "string") pending[nameOfCallback] = name;
        }
        var value;
        try { value = sync("invoke", { call: { root: root, path: path, method: name, args: args } }); }
        catch (error) { if (nameOfCallback) delete pending[nameOfCallback]; throw error; }
        // Native callbacks are posted to the page queue. In particular spawn's
        // caller installs its listeners only after this synchronous call returns.
        var deferred = [], immediate = [];
        (value.events || []).forEach(function (event) {
          (/^(?:exec|data|exit)$/.test(event.kind) ? deferred : immediate).push(event);
        });
        events(immediate);
        if (deferred.length) setTimeout(function () { events(deferred); }, 0);
        if (value.kind === "error") { if (nameOfCallback) delete pending[nameOfCallback]; throw new Error(value.message); }
        if (value.kind === "void") return;
        if (value.kind === "number") return Number(value.value);
        if (value.kind === "object") {
          var next = path.concat([[name, args]]);
          if (value.type === 3) return object(root, next, value.type);
          return singletons[name] || (singletons[name] = object(root, next, value.type));
        }
        return value.value;
      };
    });
    return new Proxy(impl, { get: function (target, name) {
      if (typeof name === "symbol" || name === "then") return undefined;
      if (Object.prototype.hasOwnProperty.call(target, name)) return target[name];
      throw new Error("WEBUI_BRIDGE_METHOD_UNAVAILABLE:" + root + "." + String(name));
    } });
  }
  var opened = sync("open");
  session = opened.session; schema = opened.schema;
  window.ksu = object("ksu", [], 0);
  window.ksu_download = object("ksu_download", [], 1);
  function poll() {
    if (closed) return;
    post("poll").then(function (value) {
      if (closed) return;
      events(value.events);
      if (!closed) pollTimer = setTimeout(poll, 250);
    }).catch(fail);
  }
  pollTimer = setTimeout(poll, 0);
  window.addEventListener("pagehide", closeDocument);
  window.addEventListener("pageshow", function (event) { if (event.persisted) window.location.reload(); });
})();
