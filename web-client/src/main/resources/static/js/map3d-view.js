// map3d-view.js: the game page's 3D map, drawn by Strata's grid relay (static/map3d/, through its
// JS page API window.StrataGridRelay) from map3d-scene.js's description, with the 2D canvas as
// its fallback.
//
// 3D is the default where it can run: the relay's module (about 1.3 MB) is downloaded only when
// the 3D map is wanted, and only where the browser has WebGL 2. While it loads, and wherever it
// cannot run, the 2D map is shown, with the reason in words. The 2D/3D switch is remembered
// (localStorage, and for a signed-in player with their other preferences); ?view=2d or ?view=3d
// in the address picks one for a visit without changing what is remembered.
//
// Everything the 2D map does, the 3D map does through the page's own handlers: a click or tap
// selects or moves (handleMapPress), right-click deselects, hovering or tapping shows the same
// tooltip text, and the page's keys (Space, R, S, A, Escape) work unchanged. The 3D camera adds
// drag to pan, right-drag (or Shift-drag) and two-finger twist to turn, wheel and pinch to zoom,
// buttons for each, and keys (arrows, Q/E, +/-, PageUp/PageDown, 0).
//
// The pure parts (which view to open, failure wording) are exported for the Node tests.
(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.Map3DView = factory();
    }
})(typeof window !== 'undefined' ? window : globalThis, function () {
    'use strict';

    // Kept as JSON, as every preference in PREFERENCE_STORAGE_KEYS is (game-logic.js).
    var STORAGE_KEY = 'barony_map_view';
    var SLIDE_SECONDS = 0.45;
    var TAP_SLOP_PX = 8;
    var CLICK_SLOP_PX = 5;
    var TOUCH_PICK_PX = 18;
    var MOUSE_PICK_PX = 6;
    var TOUCH_TOOLTIP_MS = 3000;
    var EV = { KEY_DOWN: 2, KEY_UP: 3, MOUSE_MOTION: 4, MOUSE_BUTTON_DOWN: 5, MOUSE_BUTTON_UP: 6, MOUSE_WHEEL: 7,
        RESIZED: 8, FINGER_DOWN: 16, FINGER_UP: 17, FINGER_MOTION: 18, FINGER_CANCELED: 19 };

    // Which map to open: the address first (?view=2d or ?view=3d, for this visit), then the
    // remembered choice (JSON '"2d"' or '"3d"'), else 3D. {view, fromAddress}.
    function chooseView(search, stored) {
        var asked = /[?&]view=(2d|3d)(?:&|$)/i.exec(search || '');
        if (asked) return { view: asked[1].toLowerCase(), fromAddress: true };
        var remembered = null;
        try { remembered = stored ? JSON.parse(stored) : null; } catch (e) { remembered = stored; }
        return { view: remembered === '2d' ? '2d' : '3d', fromAddress: false };
    }

    // The relay's failure ("failed:<reason>", or the loader's codes) in words for the note.
    function describeFailure(reason) {
        var r = String(reason || '').replace(/^failed:/, '');
        var words = {
            'no-webgl2': 'this browser has no WebGL 2',
            'network': 'the 3D map could not be downloaded',
            'engine': 'the 3D engine could not start',
            'load': 'the 3D map could not be loaded',
            'context-lost': 'the graphics context was lost',
            'slow': 'this device draws it too slowly'
        };
        if (words[r]) return words[r];
        return r ? r : 'it could not start';
    }

    function hasWebGL2(doc) {
        try {
            var c = doc.createElement('canvas');
            return !!(c.getContext && c.getContext('webgl2'));
        } catch (e) {
            return false;
        }
    }

    // The 3D map on the page. `deps`:
    //   relay, scene (Map3DScene), wire (GridWire): the relay page API and the two modules
    //   stage, canvas, assets: the 3D map's box, the relay's canvas, the element carrying the
    //     module's content-hashed URLs (data-script, data-wasm)
    //   getState(), getSelectedId(), palette(), groundTint, captureTurns
    //   onPress(gridX, gridY, ownArmy), onDeselect(): the 2D map's click and right-click
    //   isOwnArmy(army), tooltipText(gridX, gridY, army)
    //   showTooltip(text, canvas, x, y, below), hideTooltip()
    //   onChange(info): {want, status: 'off'|'loading'|'on'|'failed', reason}, to update the page
    //   storage: localStorage-like, location: {search}
    function create(deps) {
        var relay = deps.relay;
        var scene = deps.scene;
        var wire = deps.wire;
        var doc = deps.document || document;
        var win = deps.window || window;
        var choice = chooseView(deps.location ? deps.location.search : '', read());
        var want = choice.view === '3d';
        var status = 'off';
        var reason = '';
        var frame = null;
        var cam = scene.defaultCamera();
        var sent = {};
        var lastState = null;
        var lastSelected = null;
        var lastPaletteKey = '';
        var hover = null;
        var size = { width: 0, height: 0 };
        var placements = [];
        var slide = { from: {}, to: {}, start: 0, seconds: 0 };
        var press = null;          // a mouse button held: {button, x, y, moved, mode}
        var fingers = {};          // touch and pen pointers down, by id: {x, y}
        var tap = null;            // a one-finger press that may still be a tap
        var gesture = null;        // two fingers: {span, angle, mx, my}
        var shift = false;
        var touchTooltipTimer = null;
        var boardShown = false;

        function read() {
            try { return deps.storage ? deps.storage.getItem(STORAGE_KEY) : null; } catch (e) { return null; }
        }

        function remember(view) {
            try { if (deps.storage) deps.storage.setItem(STORAGE_KEY, JSON.stringify(view)); } catch (e) { /* private window */ }
        }

        function changed() {
            if (deps.onChange) deps.onChange({ want: want, status: status, reason: reason });
        }

        function setStatus(next, why) {
            if (next === status && (why || '') === reason) return;
            status = next;
            reason = why || '';
            changed();
        }

        function fail(why) {
            if (relay && relay.state() === 'ready') relay.close();
            deps.stage.hidden = true;
            boardShown = false;
            setStatus('failed', describeFailure(why));
        }

        function start() {
            if (!relay) { fail('the 3D map did not load'); return; }
            if (!hasWebGL2(doc)) { fail('no-webgl2'); return; }
            var st = relay.state();
            if (st.indexOf('failed:') === 0) { fail(st); return; }
            deps.stage.hidden = false;
            boardShown = false;
            deps.stage.classList.add('map3d-loading');
            sent = {};
            setStatus('loading');
            relay.request(JSON.stringify({
                canvas: deps.canvas,
                base: '',
                script: deps.assets.getAttribute('data-script'),
                wasm: deps.assets.getAttribute('data-wasm'),
                title: 'the 3D map',
                maxBytes: 4000000,
                // Strata 0.9.1 and later: the page keeps its own keys (Space, Tab, the arrows in
                // the split box); 0.9.0 ignores it, and holdKeys below does the same job there.
                keys: false
            }));
            watch();
        }

        function stop() {
            if (relay && relay.state() === 'ready') relay.close();
            deps.stage.hidden = true;
            deps.stage.classList.remove('map3d-loading');
            boardShown = false;
            hover = null;
            deps.hideTooltip();
            setStatus('off');
            watch();  // finishes closing a relay that is still loading
        }

        function watch() {
            if (frame === null) frame = win.requestAnimationFrame(tick);
        }

        function now() { return win.performance ? win.performance.now() : Date.now(); }

        // Where each army is drawn at this moment (part-way through a slide), by id.
        function currentPositions() {
            var t = slide.seconds > 0 ? Math.min(1, (now() - slide.start) / 1000 / slide.seconds) : 1;
            var out = {};
            Object.keys(slide.to).forEach(function (id) {
                var a = slide.from[id] || slide.to[id];
                var b = slide.to[id];
                out[id] = [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
            });
            return out;
        }

        function sliding() {
            return slide.seconds > 0 && (now() - slide.start) / 1000 < slide.seconds;
        }

        function sendIfChanged(name, bytes) {
            if (wire.sameBytes(sent[name], bytes)) return;
            var rc = relay.sendBytes(bytes);
            if (rc >= 0) sent[name] = bytes;
        }

        function orbit() {
            return scene.orbitOf(lastState, cam, size.width, size.height);
        }

        function view() {
            return scene.viewOf(orbit(), deps.palette().background);
        }

        // Every frame while loading or shown: the relay's state, the player's input, and any
        // message whose contents changed.
        function tick() {
            frame = null;
            var st = relay ? relay.state() : 'failed:the 3D map did not load';
            if (st.indexOf('failed:') === 0) {
                if (want) fail(st);
                return;
            }
            if (!want) {
                if (st === 'ready') relay.close();
                if (st === 'loading') watch();
                return;
            }
            // The loader's own fallback (a device too slow even at its lowest quality).
            if (deps.stage.querySelector('.sk-loader.sk-fallback')) { fail('slow'); return; }
            if (st === 'ready') {
                size = { width: deps.canvasElement.clientWidth, height: deps.canvasElement.clientHeight };
                var state = deps.getState();
                if (state && size.width > 0 && size.height > 0) {
                    input(JSON.parse(relay.poll()), state);
                    send(state);
                    if (!boardShown && sent.board && sent.view) {
                        boardShown = true;
                        deps.stage.classList.remove('map3d-loading');
                        setStatus('on');
                    }
                } else {
                    relay.poll();
                }
            }
            watch();
        }

        function send(state) {
            var palette = deps.palette();
            var paletteKey = JSON.stringify(palette);
            var selected = deps.getSelectedId();
            var stateChanged = state !== lastState;
            if (stateChanged) {
                if (!lastState || lastState.width !== state.width || lastState.height !== state.height) {
                    cam = scene.clampCamera(state, cam);
                    // A phone sizes the stage to the map's shape (style.css, --map-aspect).
                    if (deps.stage.style) deps.stage.style.setProperty('--map-aspect', String(state.height / state.width));
                }
                lastState = state;
                placements = scene.armyPlacements(state);
            }
            var piecesDue = stateChanged || selected !== lastSelected || paletteKey !== lastPaletteKey;
            if (piecesDue) {
                var from = currentPositions();
                var to = {};
                placements.forEach(function (pl) { to[pl.army.id] = [pl.x, pl.z]; });
                var reduced = relay.reducedMotion && relay.reducedMotion() === 1;
                var seconds;
                if (stateChanged) {
                    // Armies slide to where a turn put them; not on first showing, nor under
                    // reduced motion.
                    seconds = reduced || !boardShown ? 0 : SLIDE_SECONDS;
                    slide = { from: from, to: to, start: now(), seconds: seconds };
                } else {
                    // A re-send (selection, colours) keeps a slide where it is and lets it finish.
                    var left = sliding() ? slide.seconds - (now() - slide.start) / 1000 : 0;
                    seconds = Math.max(0, left);
                    slide = { from: from, to: to, start: now(), seconds: seconds };
                }
                var startAt = {};
                Object.keys(to).forEach(function (id) { if (from[id]) startAt[id] = from[id]; });
                sendIfChanged('pieces', wire.encodePieces(scene.pieces(state, palette,
                    { selectedId: selected, from: startAt, tickSeconds: seconds })));
                lastSelected = selected;
            }
            lastPaletteKey = paletteKey;
            sendIfChanged('board', wire.encodeBoard(scene.boardShapes(state, palette, deps.groundTint,
                { hover: hover, captureTurns: deps.captureTurns })));
            var v = view();
            sendIfChanged('view', wire.encodeView(v));
            var h = scene.hud(state, palette, v, size.width, size.height, placements,
                { selectedId: selected, at: sliding() ? currentPositions() : null });
            sendIfChanged('hud', wire.encodeHud(h.panels, h.labels));
        }

        // --- input

        function pickAt(x, y, matches, touch) {
            if (!lastState) return null;
            return scene.pick(lastState, view(), x, y, size.width, size.height, placements, matches,
                touch ? TOUCH_PICK_PX : MOUSE_PICK_PX);
        }

        function press1(x, y, touch) {
            var hit = pickAt(x, y, null, touch);
            if (!hit) return;
            var own = pickAt(x, y, deps.isOwnArmy, touch);
            deps.onPress(hit.gridX, hit.gridY, own ? own.army : null);
        }

        function showTooltipAt(x, y, touch) {
            var hit = pickAt(x, y, null, touch);
            var text = hit ? deps.tooltipText(hit.gridX, hit.gridY, hit.army) : null;
            if (text) deps.showTooltip(text, deps.canvasElement, x, y, touch);
            else deps.hideTooltip();
            return hit;
        }

        function setHover(hit) {
            var next = hit ? { x: hit.gridX, y: hit.gridY } : null;
            if ((next && hover && next.x === hover.x && next.y === hover.y) || (!next && !hover)) return;
            hover = next;
        }

        function input(events, state) {
            events.forEach(function (e) {
                var mouse = e.pointer === 'mouse';
                switch (e.type) {
                case EV.MOUSE_BUTTON_DOWN:
                    if (!mouse) break;
                    press = { button: e.button, x: e.x, y: e.y, moved: 0,
                        mode: e.button === 1 && !shift ? 'pan' : 'turn' };
                    break;
                case EV.MOUSE_MOTION:
                    if (!mouse) break;
                    if (press) {
                        press.moved += Math.abs(e.dx) + Math.abs(e.dy);
                        if (press.moved > CLICK_SLOP_PX) {
                            deps.hideTooltip();
                            if (press.mode === 'pan') {
                                cam = scene.panByDrag(state, cam, view(), e.x - e.dx, e.y - e.dy, e.x, e.y,
                                    size.width, size.height);
                            } else {
                                cam = scene.rotate(state, cam, e.dx * 0.008, e.dy * 0.006);
                            }
                        }
                    } else {
                        setHover(showTooltipAt(e.x, e.y, false));
                    }
                    break;
                case EV.MOUSE_BUTTON_UP:
                    if (!mouse || !press) break;
                    if (press.moved <= CLICK_SLOP_PX) {
                        if (e.button === 1) press1(e.x, e.y, false);
                        else if (e.button === 3) deps.onDeselect();
                    }
                    press = null;
                    break;
                case EV.MOUSE_WHEEL:
                    cam = scene.zoomBy(state, cam, Math.exp(-0.15 * e.y));
                    break;
                case EV.FINGER_DOWN:
                    fingers[e.id] = { x: e.x, y: e.y };
                    if (Object.keys(fingers).length === 1) {
                        tap = { id: e.id, x: e.x, y: e.y, moved: 0 };
                        // As the 2D map does on touchstart: the tooltip for what is under the
                        // finger, for three seconds.
                        showTooltipAt(e.x, e.y, true);
                        clearTimeout(touchTooltipTimer);
                        touchTooltipTimer = setTimeout(deps.hideTooltip, TOUCH_TOOLTIP_MS);
                    } else {
                        tap = null;
                        deps.hideTooltip();
                        gesture = twoFingers();
                    }
                    break;
                case EV.FINGER_MOTION:
                    if (!fingers[e.id]) break;
                    var before = fingers[e.id];
                    fingers[e.id] = { x: e.x, y: e.y };
                    var ids = Object.keys(fingers);
                    if (ids.length === 1) {
                        if (tap) tap.moved += Math.abs(e.x - before.x) + Math.abs(e.y - before.y);
                        if (!tap || tap.moved > TAP_SLOP_PX) {
                            if (tap) { deps.hideTooltip(); tap = null; }
                            cam = scene.panByDrag(state, cam, view(), before.x, before.y, e.x, e.y,
                                size.width, size.height);
                        }
                    } else if (ids.length === 2 && gesture) {
                        var g = twoFingers();
                        if (g.span > 0 && gesture.span > 0) cam = scene.zoomBy(state, cam, gesture.span / g.span);
                        var turn = g.angle - gesture.angle;
                        if (turn > Math.PI) turn -= 2 * Math.PI;
                        if (turn < -Math.PI) turn += 2 * Math.PI;
                        cam = scene.rotate(state, cam, -turn, 0);
                        cam = scene.panByDrag(state, cam, view(), gesture.mx, gesture.my, g.mx, g.my,
                            size.width, size.height);
                        gesture = g;
                    }
                    break;
                case EV.FINGER_UP:
                    if (tap && tap.id === e.id && tap.moved <= TAP_SLOP_PX && Object.keys(fingers).length === 1) {
                        press1(e.x, e.y, true);
                    }
                    delete fingers[e.id];
                    tap = null;
                    gesture = Object.keys(fingers).length === 2 ? twoFingers() : null;
                    break;
                case EV.FINGER_CANCELED:
                    delete fingers[e.id];
                    tap = null;
                    gesture = null;
                    break;
                default:
                    break;
                }
            });
        }

        function twoFingers() {
            var ids = Object.keys(fingers);
            if (ids.length < 2) return null;
            var a = fingers[ids[0]];
            var b = fingers[ids[1]];
            var dx = b.x - a.x;
            var dy = b.y - a.y;
            return { span: Math.sqrt(dx * dx + dy * dy), angle: Math.atan2(dy, dx), mx: (a.x + b.x) / 2, my: (a.y + b.y) / 2 };
        }

        // --- the page's controls

        function camera(action) {
            if (!lastState || status !== 'on') return false;
            var s = lastState;
            switch (action) {
            case 'left': cam = scene.panBySteps(s, cam, -1, 0); break;
            case 'right': cam = scene.panBySteps(s, cam, 1, 0); break;
            case 'forward': cam = scene.panBySteps(s, cam, 0, 1); break;
            case 'back': cam = scene.panBySteps(s, cam, 0, -1); break;
            case 'turn-left': cam = scene.rotate(s, cam, Math.PI / 8, 0); break;
            case 'turn-right': cam = scene.rotate(s, cam, -Math.PI / 8, 0); break;
            case 'tilt-up': cam = scene.rotate(s, cam, 0, 0.12); break;
            case 'tilt-down': cam = scene.rotate(s, cam, 0, -0.12); break;
            case 'zoom-in': cam = scene.zoomBy(s, cam, 1 / 1.2); break;
            case 'zoom-out': cam = scene.zoomBy(s, cam, 1.2); break;
            case 'reset': cam = scene.defaultCamera(); break;
            default: return false;
            }
            return true;
        }

        function setWant(next, fromUser) {
            if (next === want && fromUser) return;
            want = next;
            if (fromUser) remember(want ? '3d' : '2d');
            if (want) start(); else stop();
        }

        // Keys held back from the relay's window listener (Strata 0.9.0 forwards every key and
        // prevents Tab, Space, Backspace and the arrows while it shows, which would break the
        // split and train boxes). The page's own keydown handler runs on document, before this.
        function holdKeys(e) { e.stopPropagation(); }
        doc.addEventListener('keydown', holdKeys);
        doc.addEventListener('keyup', holdKeys);
        doc.addEventListener('keydown', function (e) { if (e.key === 'Shift') shift = true; }, true);
        doc.addEventListener('keyup', function (e) { if (e.key === 'Shift') shift = false; }, true);

        if (want) start(); else changed();

        return {
            showing: function () { return want && status === 'on'; },
            wanted: function () { return want; },
            status: function () { return status; },
            reason: function () { return reason; },
            camera: camera,
            clearHover: function () { hover = null; },
            cameraState: function () { return Object.assign({}, cam); },
            set3D: function (on) { setWant(!!on, true); },
            toggle: function () { setWant(!want, true); },
            // A preference arriving from the account (after the first load): applied unless the
            // address chose the view for this visit.
            applyStored: function () {
                if (choice.fromAddress) return;
                var next = chooseView('', read()).view === '3d';
                if (next !== want) setWant(next, false);
            },
            // Test hook: the messages last sent, decoded.
            debug: function () {
                var out = {};
                Object.keys(sent).forEach(function (k) { out[k] = wire.decode(sent[k]); });
                return { status: status, want: want, cam: cam, size: size, sent: out, hover: hover };
            },
            // Test hook: canvas point of an army's figure (its middle) or of a tile's centre.
            screenOf: function (worldX, worldY, worldZ) {
                if (!lastState) return null;
                return wire.project(view(), [worldX, worldY, worldZ], size.width, size.height);
            }
        };
    }

    return {
        STORAGE_KEY: STORAGE_KEY,
        chooseView: chooseView,
        describeFailure: describeFailure,
        create: create
    };
});
