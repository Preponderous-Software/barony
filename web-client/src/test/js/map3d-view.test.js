// Node's built-in test runner. Run with: node --test web-client/src/test/js/
// The 3D map's controller (map3d-view.js) against a stand-in for Strata's grid relay page API.
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const W = require('../../main/resources/static/js/grid-wire.js');
const S = require('../../main/resources/static/js/map3d-scene.js');
const V = require('../../main/resources/static/js/map3d-view.js');

const PALETTE = {
    player1Tint: 'rgba(100, 150, 255, 0.3)', player2Tint: 'rgba(255, 100, 100, 0.3)',
    neutralTint: 'rgba(200, 200, 200, 0.05)', player1Army: '#0066FF', player2Army: '#FF0000',
    selectionRing: '#FFD700', castle: '#888', castleBorder: '#aaa', captureBarTrack: 'rgba(0, 0, 0, 0.6)',
    village: '#a0724a', villageBorder: '#c0926a', grid: '#3a3228', background: '#1a1510', textWhite: '#FFF'
};

function game() {
    const grid = [];
    for (let x = 0; x < 6; x++) {
        grid.push([]);
        for (let y = 0; y < 5; y++) grid[x].push({ type: 'EMPTY', ownerId: 0, occupationTicks: 0 });
    }
    grid[0][0] = { type: 'CASTLE', ownerId: 1, occupationTicks: 0 };
    return {
        width: 6, height: 5, grid, tickCount: 0,
        armies: [
            { id: 1, playerId: 1, x: 1, y: 1, soldiers: 10, morale: 100, loyalty: 100 },
            { id: 2, playerId: 2, x: 4, y: 3, soldiers: 9, morale: 100, loyalty: 100 }
        ]
    };
}

// A page around the controller: the relay stand-in, a frame loop driven by hand, storage.
function page(options) {
    const o = options || {};
    const frames = [];
    const listeners = [];
    let clock = 0;
    const relay = {
        st: o.relayState || 'idle',
        requests: [], sent: [], events: [], closed: 0,
        state() { return this.st; },
        request(json) { this.requests.push(JSON.parse(json)); if (this.st === 'idle') this.st = o.loadsTo || 'ready'; return this.st; },
        sendBytes(bytes) { this.sent.push(bytes); return 0; },
        poll() { const out = JSON.stringify(this.events); this.events = []; return out; },
        close() { this.closed++; this.st = 'idle'; },
        reducedMotion() { return 0; }
    };
    const store = Object.assign({}, o.stored ? { barony_map_view: o.stored } : {});
    const storage = { getItem: (k) => (k in store ? store[k] : null), setItem: (k, v) => { store[k] = String(v); } };
    const stage = { hidden: true, classes: new Set(), classList: null, querySelector: () => null };
    stage.classList = { add: (c) => stage.classes.add(c), remove: (c) => stage.classes.delete(c) };
    const calls = { press: [], deselect: 0, tooltip: [], changes: [] };
    let state = o.state === undefined ? game() : o.state;
    let selected = null;
    const view = V.create({
        relay: o.noRelay ? null : relay,
        scene: S,
        wire: W,
        document: {
            addEventListener: (type, fn, capture) => listeners.push({ type, fn, capture: !!capture }),
            createElement: () => ({ getContext: (kind) => (kind === 'webgl2' && o.webgl2 !== false ? {} : null) })
        },
        window: { requestAnimationFrame: (fn) => { frames.push(fn); return frames.length; }, performance: { now: () => clock } },
        stage,
        canvas: 'mapRelay',
        canvasElement: { clientWidth: 800, clientHeight: 600 },
        assets: { getAttribute: (name) => '/map3d/' + name },
        getState: () => state,
        getSelectedId: () => selected,
        palette: () => PALETTE,
        groundTint: {},
        captureTurns: 3,
        onPress: (gx, gy, own) => calls.press.push([gx, gy, own ? own.id : null]),
        onDeselect: () => calls.deselect++,
        isOwnArmy: (a) => a.playerId === 1,
        tooltipText: (gx, gy, army) => (army ? 'Army #' + army.id : 'tile ' + gx + ',' + gy),
        showTooltip: (text) => calls.tooltip.push(text),
        hideTooltip: () => calls.tooltip.push(null),
        onChange: (info) => calls.changes.push(info),
        storage,
        location: { search: o.search || '' }
    });
    return {
        view, relay, store, stage, calls, listeners,
        frame(n) {
            for (let i = 0; i < (n || 1); i++) frames.splice(0).forEach((f) => f());
        },
        advance(ms) { clock += ms; },
        setState(s) { state = s; },
        select(id) { selected = id; },
        // Canvas point of a world point under the controller's current camera.
        at(x, y, z) { return view.screenOf(x, y, z); },
        last(kind) {
            const all = relay.sent.map((b) => W.decode(b)).filter((m) => m.kind === kind);
            return all[all.length - 1];
        }
    };
}

test('chooseView: the address picks for a visit, else the remembered choice, else 3D', () => {
    assert.deepEqual(V.chooseView('', null), { view: '3d', fromAddress: false });
    assert.deepEqual(V.chooseView('', '"2d"'), { view: '2d', fromAddress: false });
    assert.deepEqual(V.chooseView('', '"3d"'), { view: '3d', fromAddress: false });
    assert.deepEqual(V.chooseView('?view=2d', '"3d"'), { view: '2d', fromAddress: true });
    assert.deepEqual(V.chooseView('?a=1&view=3D', '"2d"'), { view: '3d', fromAddress: true });
    assert.deepEqual(V.chooseView('?view=4d', '"2d"'), { view: '2d', fromAddress: false });
    assert.deepEqual(V.chooseView('', 'not json'), { view: '3d', fromAddress: false });
});

test('describeFailure says why in words', () => {
    assert.equal(V.describeFailure('failed:no-webgl2'), 'this browser has no WebGL 2');
    assert.equal(V.describeFailure('failed:network'), 'the 3D map could not be downloaded');
    assert.equal(V.describeFailure('failed:the 3D view is 5.0 MB, over the 4.0 MB limit'),
        'the 3D view is 5.0 MB, over the 4.0 MB limit');
    assert.equal(V.describeFailure(''), 'it could not start');
});

test('3D by default: the relay is asked for with the module\'s hashed URLs and keys left to the page', () => {
    const p = page();
    assert.equal(p.relay.requests.length, 1);
    const r = p.relay.requests[0];
    assert.equal(r.canvas, 'mapRelay');
    assert.equal(r.script, '/map3d/data-script');
    assert.equal(r.wasm, '/map3d/data-wasm');
    assert.equal(r.keys, false);
    assert.ok(r.maxBytes >= 1300000, 'the size gate lets the 1.25 MB relay through');
    assert.equal(p.view.status(), 'loading');
    assert.equal(p.stage.hidden, false);
    assert.ok(p.stage.classes.has('map3d-loading'), 'laid out but unseen while loading');
    p.frame();
    assert.equal(p.view.status(), 'on');
    assert.ok(!p.stage.classes.has('map3d-loading'));
    assert.deepEqual(p.relay.sent.map((b) => b[4]).sort(), [1, 2, 3, 4], 'board, pieces, HUD and view sent');
    assert.equal(p.last(W.KIND.BOARD).shapes.filter((s) => Math.abs(s.scale[0] - 0.95) < 1e-6).length, 30);
});

test('?view=2d and a remembered 2D never ask for the relay', () => {
    for (const p of [page({ search: '?view=2d' }), page({ stored: '"2d"' })]) {
        assert.equal(p.relay.requests.length, 0);
        assert.equal(p.view.status(), 'off');
        assert.equal(p.view.wanted(), false);
        assert.equal(p.stage.hidden, true);
    }
});

test('no WebGL 2: the 2D map stays, with the reason, and nothing is downloaded', () => {
    const p = page({ webgl2: false });
    assert.equal(p.relay.requests.length, 0);
    assert.equal(p.view.status(), 'failed');
    assert.equal(p.view.reason(), 'this browser has no WebGL 2');
    assert.equal(p.stage.hidden, true);
    assert.equal(p.calls.changes[p.calls.changes.length - 1].status, 'failed');
});

test('a relay that fails while loading falls back to 2D with its reason', () => {
    const p = page({ loadsTo: 'loading' });
    p.relay.st = 'failed:network';
    p.frame();
    assert.equal(p.view.status(), 'failed');
    assert.equal(p.view.reason(), 'the 3D map could not be downloaded');
    assert.equal(p.stage.hidden, true);
});

test('the switch is remembered as the other preferences are (JSON), and closes and reopens the relay', () => {
    const p = page();
    p.frame();
    p.view.set3D(false);
    assert.equal(p.store.barony_map_view, '"2d"');
    assert.equal(p.relay.closed, 1);
    assert.equal(p.view.status(), 'off');
    p.view.toggle();
    assert.equal(p.store.barony_map_view, '"3d"');
    assert.equal(p.relay.requests.length, 2);
    p.frame();
    assert.equal(p.view.showing(), true);
});

test('switching to 2D while the relay still loads closes it once it is ready', () => {
    const p = page({ loadsTo: 'loading' });
    p.view.set3D(false);
    assert.equal(p.relay.closed, 0);
    p.relay.st = 'ready';
    p.frame();
    assert.equal(p.relay.closed, 1);
});

test('a preference arriving from the account switches the view, unless the address chose it', () => {
    const p = page();
    p.frame();
    p.store.barony_map_view = '"2d"';
    p.view.applyStored();
    assert.equal(p.view.wanted(), false);
    const q = page({ search: '?view=3d' });
    q.store.barony_map_view = '"2d"';
    q.view.applyStored();
    assert.equal(q.view.wanted(), true);
});

function armyPoint(p, id, up) {
    const pl = S.armyPlacements(game()).find((x) => x.army.id === id);
    return p.at(pl.x, up === undefined ? pl.height - 0.1 : up, pl.z);
}

test('a click on the player\'s army is a 2D map click on it; on a tile, a click on that tile', () => {
    const p = page();
    p.frame();
    const a = armyPoint(p, 1);
    p.relay.events.push({ type: 5, button: 1, x: a.x, y: a.y, pointer: 'mouse', id: 1 },
        { type: 6, button: 1, x: a.x, y: a.y, pointer: 'mouse', id: 1 });
    p.frame();
    assert.deepEqual(p.calls.press, [[1, 1, 1]]);
    const [tx, tz] = S.cellCenter(game(), 3, 2);
    const t = p.at(tx, 0, tz);
    p.relay.events.push({ type: 5, button: 1, x: t.x, y: t.y, pointer: 'mouse', id: 1 },
        { type: 6, button: 1, x: t.x, y: t.y, pointer: 'mouse', id: 1 });
    p.frame();
    assert.deepEqual(p.calls.press[1], [3, 2, null]);
    // The enemy army is pressed as its tile, never as the player's own army.
    const e = armyPoint(p, 2);
    p.relay.events.push({ type: 5, button: 1, x: e.x, y: e.y, pointer: 'mouse', id: 1 },
        { type: 6, button: 1, x: e.x, y: e.y, pointer: 'mouse', id: 1 });
    p.frame();
    assert.deepEqual(p.calls.press[2], [4, 3, null]);
});

test('a right-click deselects, as on the 2D map; a drag neither clicks nor deselects', () => {
    const p = page();
    p.frame();
    p.relay.events.push({ type: 5, button: 3, x: 400, y: 300, pointer: 'mouse', id: 1 },
        { type: 6, button: 3, x: 400, y: 300, pointer: 'mouse', id: 1 });
    p.frame();
    assert.equal(p.calls.deselect, 1);
    const before = p.view.cameraState();
    p.relay.events.push({ type: 5, button: 1, x: 400, y: 300, pointer: 'mouse', id: 1 },
        { type: 4, x: 440, y: 330, dx: 40, dy: 30, buttons: 1, pointer: 'mouse', id: 1 },
        { type: 6, button: 1, x: 440, y: 330, pointer: 'mouse', id: 1 });
    p.frame();
    assert.equal(p.calls.press.length, 0);
    assert.notDeepEqual(p.view.cameraState().target, before.target, 'a left drag pans');
    p.relay.events.push({ type: 5, button: 3, x: 400, y: 300, pointer: 'mouse', id: 1 },
        { type: 4, x: 460, y: 300, dx: 60, dy: 0, buttons: 2, pointer: 'mouse', id: 1 },
        { type: 6, button: 3, x: 460, y: 300, pointer: 'mouse', id: 1 });
    p.frame();
    assert.equal(p.calls.deselect, 1);
    assert.ok(Math.abs(p.view.cameraState().yaw - before.yaw) > 0.1, 'a right drag turns');
});

test('hovering shows the tooltip for what is under the mouse and outlines its tile', () => {
    const p = page();
    p.frame();
    const a = armyPoint(p, 1);
    p.relay.events.push({ type: 4, x: a.x, y: a.y, dx: 1, dy: 0, buttons: 0, pointer: 'mouse', id: 1 });
    p.frame();
    assert.equal(p.calls.tooltip[p.calls.tooltip.length - 1], 'Army #1');
    p.frame();
    assert.deepEqual(p.view.debug().hover, { x: 1, y: 1 });
    const cream = W.rgba(W.parseCss('#f5e6c8'));
    const outline = p.last(W.KIND.BOARD).shapes.filter((s) => s.color.every((c, i) => Math.abs(c - cream[i]) < 1e-6));
    assert.equal(outline.length, 4);
});

test('a tap is a click (with the tap tooltip); a one-finger drag pans; a pinch zooms', () => {
    const p = page();
    p.frame();
    const a = armyPoint(p, 1);
    p.relay.events.push({ type: 16, id: 7, x: a.x, y: a.y, pointer: 'touch' },
        // the primary touch's mouse events are not double-counted
        { type: 5, button: 1, x: a.x, y: a.y, pointer: 'touch', id: 7 },
        { type: 17, id: 7, x: a.x, y: a.y, pointer: 'touch' },
        { type: 6, button: 1, x: a.x, y: a.y, pointer: 'touch', id: 7 });
    p.frame();
    assert.deepEqual(p.calls.press, [[1, 1, 1]]);
    assert.equal(p.calls.tooltip.filter(Boolean)[0], 'Army #1');
    const start = p.view.cameraState();
    p.relay.events.push({ type: 16, id: 8, x: 300, y: 300, pointer: 'touch' },
        { type: 18, id: 8, x: 340, y: 320, pointer: 'touch' },
        { type: 17, id: 8, x: 340, y: 320, pointer: 'touch' });
    p.frame();
    assert.equal(p.calls.press.length, 1, 'a drag is not a tap');
    assert.notDeepEqual(p.view.cameraState().target, start.target);
    const zoom = p.view.cameraState().zoom;
    p.relay.events.push({ type: 16, id: 1, x: 300, y: 300, pointer: 'touch' },
        { type: 16, id: 2, x: 400, y: 300, pointer: 'touch' },
        { type: 18, id: 2, x: 500, y: 300, pointer: 'touch' },
        { type: 17, id: 2, x: 500, y: 300, pointer: 'touch' },
        { type: 17, id: 1, x: 300, y: 300, pointer: 'touch' });
    p.frame();
    assert.ok(p.view.cameraState().zoom < zoom, 'spreading two fingers zooms in');
    assert.equal(p.calls.press.length, 1, 'a pinch is not a tap');
});

test('the wheel zooms; the camera actions move, turn, tilt, zoom and reset', () => {
    const p = page();
    p.frame();
    const z0 = p.view.cameraState().zoom;
    p.relay.events.push({ type: 7, x: 0, y: 1 });
    p.frame();
    assert.ok(p.view.cameraState().zoom < z0, 'scrolling up zooms in');
    for (const action of ['left', 'right', 'forward', 'back', 'turn-left', 'turn-right', 'tilt-up', 'tilt-down', 'zoom-in', 'zoom-out']) {
        const before = JSON.stringify(p.view.cameraState());
        assert.equal(p.view.camera(action), true);
        assert.notEqual(JSON.stringify(p.view.cameraState()), before, action + ' moves the camera');
    }
    p.view.camera('reset');
    assert.deepEqual(p.view.cameraState(), S.defaultCamera());
    assert.equal(p.view.camera('nonsense'), false);
});

test('a new turn slides the armies from where they stood; selection re-sends without restarting', () => {
    const p = page();
    p.frame();
    const next = game();
    next.armies[0] = Object.assign({}, next.armies[0], { x: 2, y: 1 });
    p.setState(next);
    p.frame();
    const pieces = p.last(W.KIND.PIECES);
    assert.ok(pieces.tickSeconds > 0.4);
    const pl = S.armyPlacements(next).findIndex((x) => x.army.id === 1);
    const from = S.cellCenter(game(), 1, 1);
    assert.deepEqual(pieces.anchors[pl].slice(0, 2).map((v) => Math.round(v * 1000) / 1000), from);
    p.advance(200);
    p.select(1);
    p.frame();
    const again = p.last(W.KIND.PIECES);
    assert.ok(again.tickSeconds > 0.2 && again.tickSeconds < 0.3, 'the rest of the slide, not a fresh one');
});

test('keys are held back from the relay\'s window listener: the page handles them itself', () => {
    const p = page();
    const stoppers = p.listeners.filter((l) => (l.type === 'keydown' || l.type === 'keyup') && !l.capture);
    assert.equal(stoppers.length, 2);
    let stopped = 0;
    stoppers[0].fn({ key: ' ', stopPropagation: () => stopped++ });
    assert.equal(stopped, 1);
});

test('without the relay script the 2D map stays, with a reason', () => {
    const p = page({ noRelay: true });
    assert.equal(p.view.status(), 'failed');
    assert.equal(p.stage.hidden, true);
});
