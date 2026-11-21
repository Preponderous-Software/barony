// Node's built-in test runner. Run with: node --test web-client/src/test/js/
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const W = require('../../main/resources/static/js/grid-wire.js');

const close = (a, b, eps = 1e-5) => Math.abs(a - b) <= eps;
const closeAll = (as, bs, eps) => as.length === bs.length && as.every((a, i) => close(a, bs[i], eps));

test('every message starts with the wire 1.0 header: G3, 1, 0, its kind, then zeros', () => {
    const messages = {
        [W.KIND.BOARD]: W.encodeBoard([]),
        [W.KIND.PIECES]: W.encodePieces({}),
        [W.KIND.HUD]: W.encodeHud([], []),
        [W.KIND.VIEW]: W.encodeView({ eye: [0, 1, 1], target: [0, 0, 0], fovDegrees: 40, near: 0.1, far: 100,
            clear: [0, 0, 0, 1], sun: [0, 1, 0] })
    };
    for (const [kind, bytes] of Object.entries(messages)) {
        assert.deepEqual(Array.from(bytes.slice(0, 8)), [0x47, 0x33, 1, 0, Number(kind), 0, 0, 0]);
    }
});

test('record sizes are the ones WIRE.md fixes: 48-byte shapes, 36-byte lights, 16-byte anchors, 64-byte views', () => {
    const s = W.shape(W.PRIM.CUBE, [1, 2, 3], [1, 1, 1], [1, 0, 0, 1]);
    const light = { position: [0, 1, 0], color: [1, 1, 1], radius: 2, intensity: 1 };
    assert.equal(W.encodeBoard([s, s], [light]).length, 8 + 8 + 2 * 48 + 36);
    assert.equal(W.encodePieces({ anchors: [[0, 0, 1, 1]], shapes: [s] }).length, 8 + 16 + 16 + 48);
    assert.equal(W.encodeView({ eye: [0, 1, 1], target: [0, 0, 0], fovDegrees: 40, near: 0.1, far: 100,
        clear: [0, 0, 0, 1], sun: [0, 1, 0] }).length, 8 + 64);
});

test('a board reads back as the shapes and lights it was given, every shape attached to the world', () => {
    const shapes = [
        W.shape(W.PRIM.CUBE, [1, 2, 3], [0.5, 0.25, 0.125], [0.1, 0.2, 0.3, 1]),
        Object.assign(W.shape(W.PRIM.SPHERE, [-1, 0, 4], [1, 1, 1], [1, 1, 1, 1]), { attach: W.ATTACH.PIECE, anchor: 7 })
    ];
    const lights = [{ position: [0, 3, 0], color: [1, 0.9, 0.8], radius: 5, intensity: 2, shadows: true }];
    const back = W.decode(W.encodeBoard(shapes, lights));
    assert.equal(back.kind, W.KIND.BOARD);
    assert.equal(back.shapes.length, 2);
    assert.equal(back.shapes[0].kind, W.PRIM.CUBE);
    assert.ok(closeAll(back.shapes[0].position, [1, 2, 3]));
    assert.ok(closeAll(back.shapes[0].scale, [0.5, 0.25, 0.125]));
    assert.ok(closeAll(back.shapes[0].color, [0.1, 0.2, 0.3, 1]));
    assert.equal(back.shapes[1].attach, W.ATTACH.WORLD, 'a board shape is never attached to a piece');
    assert.equal(back.lights.length, 1);
    assert.equal(back.lights[0].shadows, true);
    assert.ok(close(back.lights[0].radius, 5));
});

test('pieces carry their anchors, tick and piece-attached shapes', () => {
    const s = Object.assign(W.shape(W.PRIM.CAPSULE, [0.1, 0.5, -0.1], [0.2, 0.4, 0.2], [0, 0, 1, 1]),
        { attach: W.ATTACH.PIECE, anchor: 1 });
    const back = W.decode(W.encodePieces({ tickSeconds: 0.45, anchors: [[0, 0, 0, 0], [1, 2, 3, 4]], shapes: [s] }));
    assert.equal(back.kind, W.KIND.PIECES);
    assert.ok(close(back.tickSeconds, 0.45));
    assert.deepEqual(back.anchors[1], [1, 2, 3, 4]);
    assert.equal(back.shapes[0].attach, W.ATTACH.PIECE);
    assert.equal(back.shapes[0].anchor, 1);
});

test('HUD labels are UTF-8, padded to four bytes, and read back with their panels', () => {
    const panels = [{ x: 1, y: 2, width: 30, height: 12, color: [0, 0, 0, 0.5] }];
    const labels = [
        { x: 4, y: 5, size: 16, color: [1, 1, 1, 1], text: '120' },
        { x: 9, y: 9, size: 8, color: [1, 1, 0, 1], text: 'Château' },
        { x: 0, y: 0, size: 8, color: [1, 1, 1, 1], text: 'abcd' }
    ];
    const bytes = W.encodeHud(panels, labels);
    // 'Château' is 8 UTF-8 bytes; '120' pads to 4; 'abcd' needs no padding.
    assert.equal(bytes.length, 8 + 8 + 32 + (32 + 4) + (32 + 8) + (32 + 4));
    const back = W.decode(bytes);
    assert.equal(back.panels.length, 1);
    assert.ok(close(back.panels[0].width, 30));
    assert.deepEqual(back.labels.map((l) => l.text), ['120', 'Château', 'abcd']);
    assert.ok(close(back.labels[0].size, 16));
});

test('a CSS colour is passed through as its own components, as Strata draws colours unconverted', () => {
    assert.deepEqual(W.parseCss('#FF0000'), [1, 0, 0, 1]);
    assert.deepEqual(W.parseCss('#fff'), [1, 1, 1, 1]);
    assert.ok(closeAll(W.parseCss('rgba(100, 150, 255, 0.3)'), [100 / 255, 150 / 255, 1, 0.3]));
    assert.ok(closeAll(W.rgba(W.parseCss('#808080')), [128 / 255, 128 / 255, 128 / 255, 1]));
    assert.ok(closeAll(W.rgba([0.5, 0.5, 0.5, 1], 4), [1, 1, 1, 1]), 'a shade is clamped to 1');
    assert.throws(() => W.parseCss('blue'));
});

test('blend paints a translucent colour over an opaque one as a 2D canvas does', () => {
    const out = W.blend([0, 0, 0, 1], [1, 0.5, 0, 0.5]);
    assert.ok(closeAll(out, [0.5, 0.25, 0, 1]));
});

test('the orbit eye matches the relay\'s orbitOf: yaw from +x towards +z, pitch up, at dist', () => {
    const eye = W.orbitEye({ yaw: Math.PI / 2, pitch: 0, dist: 10, target: [1, 0, 2] });
    assert.ok(closeAll(eye, [1, 0, 12]));
    const up = W.orbitEye({ yaw: 0, pitch: Math.PI / 6, dist: 2, target: [0, 0, 0] });
    assert.ok(closeAll(up, [Math.sqrt(3), 1, 0]));
});

test('projecting the target lands mid-canvas, and a ray through any pixel hits the ground under it', () => {
    const view = { eye: W.orbitEye({ yaw: 1.2, pitch: 0.9, dist: 14, target: [2, 0, -1] }), target: [2, 0, -1],
        fovDegrees: 40 };
    const p = W.project(view, [2, 0, -1], 390, 600);
    assert.ok(close(p.x, 195, 1e-3) && close(p.y, 300, 1e-3));
    for (const [x, y] of [[10, 20], [380, 590], [200, 450]]) {
        const hit = W.hitPlaneY(W.rayAt(view, x, y, 390, 600), 0);
        assert.ok(hit, 'the ray reaches the ground');
        const back = W.project(view, hit, 390, 600);
        assert.ok(close(back.x, x, 1e-3) && close(back.y, y, 1e-3), 'and projects back to the same pixel');
    }
    assert.equal(W.project(view, view.eye.map((v, i) => v + (view.eye[i] - view.target[i])), 390, 600), null,
        'a point behind the eye has no place on the canvas');
});

test('sameBytes compares messages byte for byte', () => {
    assert.ok(W.sameBytes(new Uint8Array([1, 2]), new Uint8Array([1, 2])));
    assert.ok(!W.sameBytes(new Uint8Array([1, 2]), new Uint8Array([1, 3])));
    assert.ok(!W.sameBytes(undefined, new Uint8Array([1])));
});

test('toBase64 gives what the relay\'s send() takes', () => {
    assert.equal(W.toBase64(new Uint8Array([0x47, 0x33, 1, 0])), 'RzMBAA==');
});
