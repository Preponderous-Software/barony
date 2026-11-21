/* grid-relay.js: the page side of the grid relay (WIRE.md).

   Part of the Strata web kit. A classic script with no dependencies; load it
   after strata-loader.js and strata-kit.js. It defines window.StrataGridRelay,
   the one object a game in the page talks to. Every call takes and returns
   strings and numbers only, so a bridge that passes nothing richer (Python
   under pygbag, through platform.window) can drive it:

     StrataGridRelay.request('{"canvas": "relay", "maxBytes": 4000000}')
                                      // start loading the 3D view; returns at once
     StrataGridRelay.state()          // "idle", "loading", "ready" or "failed:<reason>"
     StrataGridRelay.send(base64)     // one wire message; 0, 1 (queued) or a negative status
     StrataGridRelay.poll()           // input since the last poll, as a JSON array
     StrataGridRelay.reducedMotion()  // 1 when the player asked for reduced motion
     StrataGridRelay.stats()          // frames, frame times, what the last frame drew (JSON)
     StrataGridRelay.close()          // stop drawing and hide the canvas

   The 3D view is only downloaded when request() is called, and only when
   the module is no bigger than maxBytes (the size gate): a page shows its 2D
   view until then and keeps it when the gate or WebGL 2 says no.

   Input is forwarded, not interpreted: keys (with desktop Strata's
   scancodes, USB HID usage ids, and SDL3 modifier bits), the mouse, the
   wheel, touch and pen, and resizes, as STRATA_EVENT_* records (see WIRE.md,
   "Input"), so every control a game has in 2D works the same in 3D.

   MIT licensed, as Strata. */
(function () {
  "use strict";
  const WIRE_MAJOR = 1;
  const STATUS = {0: "ok", "-1": "not a grid relay message", "-2": "unsupported wire format major version",
    "-3": "message truncated", "-4": "unknown message kind", "-5": "bad value in message", "-6": "message over the limits"};

  // STRATA_EVENT_* (strata.h), and the web's additions for touch and pens.
  const EV = {KEY_DOWN: 2, KEY_UP: 3, MOUSE_MOTION: 4, MOUSE_BUTTON_DOWN: 5, MOUSE_BUTTON_UP: 6, MOUSE_WHEEL: 7,
    RESIZED: 8, TEXT_INPUT: 9, FINGER_DOWN: 16, FINGER_UP: 17, FINGER_MOTION: 18, FINGER_CANCELED: 19};

  // KeyboardEvent.code -> USB HID usage id (= SDL3 scancode = StrataEvent.scancode).
  const HID = (function () {
    const m = {};
    for (let i = 0; i < 26; i++) m["Key" + String.fromCharCode(65 + i)] = 4 + i;
    for (let i = 1; i <= 9; i++) m["Digit" + i] = 29 + i;
    m.Digit0 = 39;
    for (let i = 1; i <= 12; i++) m["F" + i] = 57 + i;
    for (let i = 1; i <= 9; i++) m["Numpad" + i] = 88 + i;
    Object.assign(m, {Enter: 40, Escape: 41, Backspace: 42, Tab: 43, Space: 44, Minus: 45, Equal: 46,
      BracketLeft: 47, BracketRight: 48, Backslash: 49, Semicolon: 51, Quote: 52, Backquote: 53, Comma: 54,
      Period: 55, Slash: 56, CapsLock: 57, PrintScreen: 70, ScrollLock: 71, Pause: 72, Insert: 73, Home: 74,
      PageUp: 75, Delete: 76, End: 77, PageDown: 78, ArrowRight: 79, ArrowLeft: 80, ArrowDown: 81, ArrowUp: 82,
      NumLock: 83, NumpadDivide: 84, NumpadMultiply: 85, NumpadSubtract: 86, NumpadAdd: 87, NumpadEnter: 88,
      Numpad0: 98, NumpadDecimal: 99, IntlBackslash: 100, ContextMenu: 101, NumpadEqual: 103,
      ControlLeft: 224, ShiftLeft: 225, AltLeft: 226, MetaLeft: 227,
      ControlRight: 228, ShiftRight: 229, AltRight: 230, MetaRight: 231});
    return m;
  })();
  // SDL3 SDL_Keymod bits, per held modifier key.
  const MOD = {ShiftLeft: 0x1, ShiftRight: 0x2, ControlLeft: 0x40, ControlRight: 0x80, AltLeft: 0x100,
    AltRight: 0x200, MetaLeft: 0x400, MetaRight: 0x800};
  // Keys whose browser default (scrolling, focus moves) a game never wants.
  const NO_DEFAULT = new Set(["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Space", "Tab", "PageUp",
    "PageDown", "Home", "End", "Backspace"]);

  let state = "idle", kit = null, mod = null, canvas = null, opts = {};
  let queue = [];  // wire messages that arrived before the module
  let events = [];
  const MAX_EVENTS = 1024;
  let heap = 0, heapCap = 0;
  let cpuMs = 0, cpuFrames = 0, frameStart = 0, lastErr = null;
  const held = new Set();

  function fail(reason) {
    state = "failed:" + reason;
    console.error("grid-relay: " + reason);
  }

  function push(e) {
    if (events.length >= MAX_EVENTS) events.shift();  // a game that stopped polling loses the oldest
    events.push(e);
  }

  function modifiers(e) {
    let m = 0;
    for (const code of held) m |= MOD[code] || 0;
    // A modifier held since before the page had focus: the left one.
    if (e.shiftKey && !(m & 0x3)) m |= 0x1;
    if (e.ctrlKey && !(m & 0xc0)) m |= 0x40;
    if (e.altKey && !(m & 0x300)) m |= 0x100;
    if (e.metaKey && !(m & 0xc00)) m |= 0x400;
    if (e.getModifierState && e.getModifierState("NumLock")) m |= 0x1000;
    if (e.getModifierState && e.getModifierState("CapsLock")) m |= 0x2000;
    return m;
  }

  function onKey(e) {
    if (state !== "ready" || !canvas || canvas.hidden) return;
    const down = e.type === "keydown";
    if (MOD[e.code]) { if (down) held.add(e.code); else held.delete(e.code); }
    const sc = HID[e.code] || 0;
    push({type: down ? EV.KEY_DOWN : EV.KEY_UP, scancode: sc, modifiers: modifiers(e), repeat: e.repeat ? 1 : 0,
      key: e.key, code: e.code});
    if (down && e.key && e.key.length === 1 && !e.ctrlKey && !e.metaKey) push({type: EV.TEXT_INPUT, text: e.key});
    if (NO_DEFAULT.has(e.code)) e.preventDefault();
  }

  function local(e) {
    const r = canvas.getBoundingClientRect();
    return {x: e.clientX - r.left, y: e.clientY - r.top, w: r.width || 1, h: r.height || 1};
  }

  // Pointer -> mouse events (a touch's primary pointer too, as SDL turns touch
  // into mouse by default, so a game's mouse controls work on phones), plus
  // FINGER_* for every touch and pen pointer, for games that use gestures.
  const BUTTON = [1, 2, 3, 4, 5];  // DOM button 0 left, 1 middle, 2 right -> SDL 1, 2, 3
  const last = new Map();
  function onPointer(e) {
    if (state !== "ready") return;
    const p = local(e);
    const asMouse = e.pointerType === "mouse" || e.isPrimary;
    const finger = e.pointerType !== "mouse";
    const base = {x: p.x, y: p.y, pointer: e.pointerType, id: e.pointerId};
    if (e.type === "pointerdown") {
      try { canvas.setPointerCapture(e.pointerId); } catch (err) { /* synthetic pointer */ }
      last.set(e.pointerId, p);
      if (asMouse) push(Object.assign({type: EV.MOUSE_BUTTON_DOWN, button: BUTTON[e.button] || 1}, base));
      if (finger) push(Object.assign({type: EV.FINGER_DOWN, nx: p.x / p.w, ny: p.y / p.h, pressure: e.pressure}, base));
      e.preventDefault();
    } else if (e.type === "pointermove") {
      const q = last.get(e.pointerId) || p;
      last.set(e.pointerId, p);
      const dx = p.x - q.x, dy = p.y - q.y;
      if (asMouse) push(Object.assign({type: EV.MOUSE_MOTION, dx, dy, buttons: e.buttons}, base));
      if (finger && e.buttons) {
        push(Object.assign({type: EV.FINGER_MOTION, nx: p.x / p.w, ny: p.y / p.h, dx: dx / p.w, dy: dy / p.h,
          pressure: e.pressure}, base));
      }
    } else {  // pointerup, pointercancel
      last.delete(e.pointerId);
      const cancel = e.type === "pointercancel";
      if (asMouse && !cancel) push(Object.assign({type: EV.MOUSE_BUTTON_UP, button: BUTTON[e.button] || 1}, base));
      if (finger) push(Object.assign({type: cancel ? EV.FINGER_CANCELED : EV.FINGER_UP, nx: p.x / p.w, ny: p.y / p.h}, base));
    }
  }

  function onWheel(e) {
    if (state !== "ready") return;
    e.preventDefault();
    // SDL's sign: positive y scrolls away from the user (up), DOM's deltaY is the opposite.
    const unit = e.deltaMode === 1 ? 1 : e.deltaMode === 2 ? 3 : 1 / 100;
    push({type: EV.MOUSE_WHEEL, x: e.deltaX * unit, y: -e.deltaY * unit});
  }

  let lastSize = "";
  function resized() {
    if (!canvas) return;
    const w = Math.round(canvas.clientWidth), h = Math.round(canvas.clientHeight), size = w + "x" + h;
    if (size === lastSize || !w || !h) return;
    lastSize = size;
    push({type: EV.RESIZED, width: w, height: h});
  }

  function b64(s) {
    const bin = atob(s), n = bin.length, out = new Uint8Array(n);
    for (let i = 0; i < n; i++) out[i] = bin.charCodeAt(i);
    return out;
  }

  function submit(bytes) {
    if (bytes.length >= 3 && bytes[0] === 0x47 && bytes[1] === 0x33 && bytes[2] !== WIRE_MAJOR) {
      // Said here as well as by the module, in words: a page and a game built
      // against different majors of the format must be rebuilt together.
      const msg = `grid-relay: wire format ${bytes[2]}.${bytes[3]} is not readable by this relay ` +
        `(reads ${WIRE_MAJOR}.x); rebuild the game and the relay from the same release`;
      if (lastErr !== msg) console.error(msg);
      lastErr = msg;
      return -2;
    }
    if (!mod) { queue.push(bytes); return 1; }
    if (bytes.length > heapCap) {
      if (heap) mod._free(heap);
      heapCap = Math.max(bytes.length, 4096);
      heap = mod._malloc(heapCap);
    }
    new Uint8Array(mod.HEAPU8 ? mod.HEAPU8.buffer : mod.HEAP32.buffer, heap, bytes.length).set(bytes);
    const rc = mod._gr_submit(heap, bytes.length);
    if (rc !== 0) {
      const msg = `grid-relay: message kind ${bytes[4]} refused: ${STATUS[rc] || "status " + rc}`;
      if (lastErr !== msg) console.error(msg);
      lastErr = msg;
    }
    return rc;
  }

  async function headBytes(url) {
    try {
      const res = await fetch(url, {method: "HEAD", credentials: "same-origin"});
      const n = parseInt(res.headers.get("Content-Length") || "", 10);
      return res.ok && n > 0 ? n : 0;
    } catch (e) { return 0; }
  }

  async function open(o) {
    opts = Object.assign({canvas: "relay", base: "", script: "grid-relay-scene.js", wasm: "grid-relay-scene.wasm",
      factory: "createGridRelay", maxBytes: 4 * 1024 * 1024, title: "the 3D view"}, o || {});
    canvas = typeof opts.canvas === "string" ? document.getElementById(opts.canvas) : opts.canvas;
    if (!canvas) return fail("no canvas #" + opts.canvas);
    state = "loading";
    const base = opts.base;
    // The size gate: the module's bytes, before any are downloaded.
    const bytes = (await headBytes(base + opts.wasm)) + (await headBytes(base + opts.script));
    if (opts.maxBytes && bytes > opts.maxBytes) {
      return fail(`the 3D view is ${(bytes / 1e6).toFixed(1)} MB, over the ${(opts.maxBytes / 1e6).toFixed(1)} MB limit`);
    }
    canvas.hidden = false;
    try {
      mod = await StrataLoader.load({script: base + opts.script, factory: opts.factory, wasm: base + opts.wasm,
        base, title: opts.title, bytes: bytes || undefined, gate: !!opts.gate,
        mount: opts.mount || canvas.parentElement, contain: opts.contain !== false});
    } catch (e) {
      return fail(e.code || "load");
    }
    if (mod._gr_wire_version() >> 8 !== WIRE_MAJOR) {
      return fail(`relay module reads wire ${mod._gr_wire_version() >> 8}.x, this page ${WIRE_MAJOR}.x`);
    }
    try {
      kit = StrataKit.create({
        module: mod, canvas, idleOrbit: false, quality: opts.quality, reducedMotion: opts.reducedMotion,
        limits: {minPitch: -1.6, maxPitch: 1.6, minDist: 0, maxDist: 1e9},
        // The game owns every key: none moves the kit's camera.
        onKey: () => true,
        frame() {
          frameStart = performance.now();
          mod._gr_set_ui_scale(canvas.width / Math.max(canvas.clientWidth, 1));
          // The view's camera, every frame: the kit's drag-to-orbit never moves it.
          const p = mod._gr_orbit() >> 2, f = mod.HEAPF32;
          Object.assign(kit.cam, {yaw: f[p], pitch: f[p + 1], dist: f[p + 2], tx: f[p + 3], ty: f[p + 4], tz: f[p + 5]});
        },
        after() { cpuMs += performance.now() - frameStart; cpuFrames++; },
        onSlow: () => StrataLoader.slow(),
        onFatal: (code, msg) => { fail(code); StrataLoader.fallback(code, msg); },
      });
    } catch (e) {
      StrataLoader.fallback(e.code || "engine", e.message);
      return fail(e.code || "engine");
    }
    canvas.style.touchAction = "none";
    canvas.addEventListener("pointerdown", onPointer);
    canvas.addEventListener("pointermove", onPointer);
    canvas.addEventListener("pointerup", onPointer);
    canvas.addEventListener("pointercancel", onPointer);
    canvas.addEventListener("wheel", onWheel, {passive: false});
    window.addEventListener("keydown", onKey);
    window.addEventListener("keyup", onKey);
    window.addEventListener("blur", () => held.clear());
    if (typeof ResizeObserver === "function") new ResizeObserver(resized).observe(canvas);
    state = "ready";
    resized();
    const pending = queue;
    queue = [];
    for (const m of pending) submit(m);
    kit.start();
    StrataLoader.done();
    return kit;
  }

  window.StrataGridRelay = {
    wireMajor: WIRE_MAJOR,
    EVENTS: EV,
    open,
    request(json) {
      if (state === "loading" || state === "ready") return state;
      if (kit && state === "idle") {  // reopened after close(): the module is still here
        canvas.hidden = false;
        state = "ready";
        kit.start();
        return state;
      }
      let o = {};
      try { o = json ? JSON.parse(String(json)) : {}; } catch (e) { fail("bad options: " + e.message); return state; }
      open(o).catch((e) => fail(String(e && e.message || e)));
      return state;
    },
    state: () => state,
    send(s) {
      try { return submit(b64(String(s))); } catch (e) { console.error("grid-relay: send: " + e.message); return -3; }
    },
    sendBytes: (bytes) => submit(bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)),
    poll() {
      resized();
      const out = JSON.stringify(events);
      events = [];
      return out;
    },
    reducedMotion: () => (kit ? (kit.reducedMotion ? 1 : 0)
      : (matchMedia("(prefers-reduced-motion: reduce)").matches ? 1 : 0)),
    stats() {
      const s = kit ? kit.stats() : {};
      return JSON.stringify(Object.assign(s, {state, messages: mod ? mod._gr_messages() : 0,
        cpuMs: cpuFrames ? cpuMs / cpuFrames : 0, frameMs: kit ? kit.frameMs || 0 : 0}));
    },
    resetStats() { cpuMs = 0; cpuFrames = 0; },
    close() {
      if (kit) kit.running = false;
      if (canvas) canvas.hidden = true;
      state = state === "ready" ? "idle" : state;
    },
    get kit() { return kit; },
  };
})();
