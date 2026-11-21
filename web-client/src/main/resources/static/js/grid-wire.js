// grid-wire.js: a small encoder for Strata's grid relay (Strata's web/kit/grid-relay/WIRE.md,
// wire format 1.0), and the camera arithmetic a page needs to pick what is under the pointer.
//
// Nothing here knows about Barony: it turns plain shape, light, panel, label and view records
// into the relay's little-endian messages, reads them back (for tests), and projects and casts
// rays with the same right-handed look-at and OpenGL perspective Strata draws with. It is kept
// generic so it can move into Strata's web kit beside grid-relay.js (as grid-wire.js) and be
// shared by every JavaScript game that drives the relay.
//
// Loaded as a plain <script> in the browser (window.GridWire) and with require() under Node,
// where web-client/src/test/js/grid-wire.test.js checks it.
(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.GridWire = factory();
    }
})(typeof window !== 'undefined' ? window : globalThis, function () {
    'use strict';

    var MAJOR = 1;
    var MINOR = 0;
    var KIND = { BOARD: 1, PIECES: 2, HUD: 3, VIEW: 4 };
    // Strata's primitive kinds (STRATA_PRIMITIVE_*).
    var PRIM = { CUBE: 0, CAPSULE: 1, CYLINDER: 2, SPHERE: 3 };
    var ATTACH = { WORLD: 0, PIECE: 1, BRIDGE: 2 };

    var HEADER_BYTES = 8;
    var SHAPE_BYTES = 48;
    var LIGHT_BYTES = 36;
    var ANCHOR_BYTES = 16;
    var PANEL_BYTES = 32;
    var LABEL_FIXED_BYTES = 32;
    var VIEW_BYTES = 64;

    // --- colour. The wire's colours are 'linear' in Strata's sense: passed through unconverted
    // and written to the canvas as they are, which a browser then shows as sRGB. So a CSS colour
    // goes in as its own sRGB components (an opaque one drawn unlit looks exactly as picked; lit
    // surfaces are multiplied by the light). Decoding to linear first would only darken it.

    // A CSS colour (#rgb, #rrggbb, rgb(...) or rgba(...)) as 0..1 components and alpha.
    function parseCss(css) {
        var s = String(css).trim();
        var m = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(s);
        if (m) {
            var hex = m[1].length === 3
                ? m[1].split('').map(function (ch) { return ch + ch; }).join('')
                : m[1];
            return [0, 2, 4].map(function (i) { return parseInt(hex.substr(i, 2), 16) / 255; }).concat([1]);
        }
        m = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/i.exec(s);
        if (m) {
            return [m[1] / 255, m[2] / 255, m[3] / 255, m[4] === undefined ? 1 : parseFloat(m[4])];
        }
        throw new Error('not a CSS colour: ' + css);
    }

    // Paints `over` (sRGB with alpha) onto the opaque sRGB colour `under`, as a 2D canvas does.
    function blend(under, over) {
        var a = over[3];
        return [0, 1, 2].map(function (i) { return under[i] * (1 - a) + over[i] * a; }).concat([1]);
    }

    // Colour components (0..1, with alpha) as a wire RGBA; `shade` multiplies the colour (below 1
    // darkens, above lightens), clamped to 0..1.
    function rgba(c, shade) {
        var k = shade === undefined ? 1 : shade;
        var out = [0, 1, 2].map(function (i) { return Math.max(0, Math.min(1, c[i] * k)); });
        out.push(c.length > 3 ? c[3] : 1);
        return out;
    }

    function shape(kind, position, scale, color) {
        return { kind: kind, position: position, scale: scale, color: color };
    }

    // --- encoding

    function Writer(bytes) {
        this.buffer = new ArrayBuffer(bytes);
        this.view = new DataView(this.buffer);
        this.at = 0;
    }
    Writer.prototype.u8 = function (v) { this.view.setUint8(this.at, v); this.at += 1; };
    Writer.prototype.u16 = function (v) { this.view.setUint16(this.at, v, true); this.at += 2; };
    Writer.prototype.u32 = function (v) { this.view.setUint32(this.at, v, true); this.at += 4; };
    Writer.prototype.f32 = function (v) { this.view.setFloat32(this.at, v, true); this.at += 4; };
    Writer.prototype.floats = function (vs, n) {
        for (var i = 0; i < n; i++) this.f32(vs[i] === undefined ? 0 : vs[i]);
    };
    Writer.prototype.header = function (kind) {
        this.u8(0x47); this.u8(0x33); this.u8(MAJOR); this.u8(MINOR); this.u8(kind); this.u8(0); this.u16(0);
    };
    Writer.prototype.shape = function (s) {
        this.u8(s.kind);
        this.u8(s.attach || ATTACH.WORLD);
        this.u16(s.anchor || 0);
        this.u16(s.anchorB || 0);
        this.u16(0);
        this.floats(s.position, 3);
        this.floats(s.scale, 3);
        this.floats(s.color, 4);
    };
    Writer.prototype.light = function (l) {
        this.u8(l.attach || ATTACH.WORLD);
        this.u8(l.shadows ? 1 : 0);
        this.u16(l.anchor || 0);
        this.floats(l.position, 3);
        this.floats(l.color, 3);
        this.f32(l.radius);
        this.f32(l.intensity);
    };
    Writer.prototype.bytes = function () { return new Uint8Array(this.buffer); };

    // BOARD: world shapes and lights, drawn under the pieces.
    function encodeBoard(shapes, lights) {
        var ls = lights || [];
        var w = new Writer(HEADER_BYTES + 8 + shapes.length * SHAPE_BYTES + ls.length * LIGHT_BYTES);
        w.header(KIND.BOARD);
        w.u32(shapes.length);
        w.u32(ls.length);
        shapes.forEach(function (s) { w.shape(Object.assign({}, s, { attach: ATTACH.WORLD })); });
        ls.forEach(function (l) { w.light(Object.assign({}, l, { attach: ATTACH.WORLD })); });
        return w.bytes();
    }

    // PIECES: anchors ([startX, startZ, endX, endZ]) that slide over tickSeconds, and shapes
    // measured from them. A shape with attach PIECE gives x and z as offsets from its anchor
    // (y is absolute); attach WORLD shapes are allowed too.
    function encodePieces(pieces) {
        var anchors = pieces.anchors || [];
        var shapes = pieces.shapes || [];
        var ls = pieces.lights || [];
        var w = new Writer(HEADER_BYTES + 16 + anchors.length * ANCHOR_BYTES + shapes.length * SHAPE_BYTES
            + ls.length * LIGHT_BYTES);
        w.header(KIND.PIECES);
        w.f32(pieces.tickSeconds || 0);
        w.u32(anchors.length);
        w.u32(shapes.length);
        w.u32(ls.length);
        anchors.forEach(function (a) { w.floats(a, 4); });
        shapes.forEach(function (s) { w.shape(s); });
        ls.forEach(function (l) { w.light(l); });
        return w.bytes();
    }

    function utf8(text) {
        if (typeof TextEncoder === 'function') return new TextEncoder().encode(text);
        var bin = unescape(encodeURIComponent(text));
        var out = new Uint8Array(bin.length);
        for (var i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
        return out;
    }

    // HUD: panels ({x, y, width, height, color}) under labels ({x, y, size, color, text}), in CSS
    // pixels from the canvas's top-left. Strata's font is 8 x 8 cells, `size` pixels a side, so a
    // label of n characters is n * size pixels wide.
    function encodeHud(panels, labels) {
        var ps = panels || [];
        var texts = (labels || []).map(function (l) { return utf8(String(l.text).replace(/\0/g, '')); });
        var bytes = HEADER_BYTES + 8 + ps.length * PANEL_BYTES;
        texts.forEach(function (t) { bytes += LABEL_FIXED_BYTES + Math.ceil(t.length / 4) * 4; });
        var w = new Writer(bytes);
        w.header(KIND.HUD);
        w.u32(ps.length);
        w.u32(texts.length);
        ps.forEach(function (p) {
            w.floats([p.x, p.y, p.width, p.height], 4);
            w.floats(p.color, 4);
        });
        (labels || []).forEach(function (l, i) {
            var t = texts[i];
            w.floats([l.x, l.y, l.size], 3);
            w.floats(l.color, 4);
            w.u16(t.length);
            w.u16(0);
            for (var k = 0; k < t.length; k++) w.u8(t[k]);
            w.at += (4 - (t.length % 4)) % 4;
        });
        return w.bytes();
    }

    // VIEW: the camera (eye, target, vertical field of view, near and far planes), the clear
    // colour (RGBA, as rgba() gives it) and the direction to the sun.
    function encodeView(view) {
        var w = new Writer(HEADER_BYTES + VIEW_BYTES);
        w.header(KIND.VIEW);
        w.floats(view.eye, 3);
        w.floats(view.target, 3);
        w.floats([view.fovDegrees, view.near, view.far], 3);
        w.floats(view.clear, 4);
        w.floats(view.sun, 3);
        return w.bytes();
    }

    // What a message says, read back: its kind and contents.
    function decode(bytes) {
        var v = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
        if (v.getUint8(0) !== 0x47 || v.getUint8(1) !== 0x33 || v.getUint8(2) !== MAJOR) {
            throw new Error('not a wire 1.x message');
        }
        var kind = v.getUint8(4);
        var f = function (at) { return v.getFloat32(at, true); };
        var fs = function (at, n) {
            var out = [];
            for (var i = 0; i < n; i++) out.push(f(at + 4 * i));
            return out;
        };
        var readShape = function (at) {
            return {
                kind: v.getUint8(at), attach: v.getUint8(at + 1), anchor: v.getUint16(at + 2, true),
                anchorB: v.getUint16(at + 4, true),
                position: fs(at + 8, 3), scale: fs(at + 20, 3), color: fs(at + 32, 4)
            };
        };
        var readLight = function (at) {
            return {
                attach: v.getUint8(at), shadows: v.getUint8(at + 1) === 1, anchor: v.getUint16(at + 2, true),
                position: fs(at + 4, 3), color: fs(at + 16, 3), radius: f(at + 28), intensity: f(at + 32)
            };
        };
        var i;
        if (kind === KIND.BOARD) {
            var n = v.getUint32(8, true);
            var nl = v.getUint32(12, true);
            var shapes = [];
            for (i = 0; i < n; i++) shapes.push(readShape(16 + SHAPE_BYTES * i));
            var lights = [];
            for (i = 0; i < nl; i++) lights.push(readLight(16 + SHAPE_BYTES * n + LIGHT_BYTES * i));
            return { kind: kind, shapes: shapes, lights: lights };
        }
        if (kind === KIND.PIECES) {
            var na = v.getUint32(12, true);
            var ns = v.getUint32(16, true);
            var nL = v.getUint32(20, true);
            var anchors = [];
            for (i = 0; i < na; i++) anchors.push(fs(24 + ANCHOR_BYTES * i, 4));
            var at = 24 + ANCHOR_BYTES * na;
            var pieceShapes = [];
            for (i = 0; i < ns; i++) pieceShapes.push(readShape(at + SHAPE_BYTES * i));
            var pieceLights = [];
            for (i = 0; i < nL; i++) pieceLights.push(readLight(at + SHAPE_BYTES * ns + LIGHT_BYTES * i));
            return { kind: kind, tickSeconds: f(8), anchors: anchors, shapes: pieceShapes, lights: pieceLights };
        }
        if (kind === KIND.HUD) {
            var np = v.getUint32(8, true);
            var nt = v.getUint32(12, true);
            var panels = [];
            for (i = 0; i < np; i++) {
                var p = fs(16 + PANEL_BYTES * i, 8);
                panels.push({ x: p[0], y: p[1], width: p[2], height: p[3], color: p.slice(4) });
            }
            var labels = [];
            var pos = 16 + PANEL_BYTES * np;
            for (i = 0; i < nt; i++) {
                var len = v.getUint16(pos + 28, true);
                var raw = new Uint8Array(bytes.buffer, bytes.byteOffset + pos + 32, len);
                var text = typeof TextDecoder === 'function'
                    ? new TextDecoder().decode(raw)
                    : decodeURIComponent(escape(String.fromCharCode.apply(null, raw)));
                labels.push({ x: f(pos), y: f(pos + 4), size: f(pos + 8), color: fs(pos + 12, 4), text: text });
                pos += LABEL_FIXED_BYTES + Math.ceil(len / 4) * 4;
            }
            return { kind: kind, panels: panels, labels: labels };
        }
        if (kind === KIND.VIEW) {
            return {
                kind: kind, eye: fs(8, 3), target: fs(20, 3), fovDegrees: f(32), near: f(36), far: f(40),
                clear: fs(44, 4), sun: fs(60, 3)
            };
        }
        return { kind: kind };
    }

    function sameBytes(a, b) {
        if (!a || !b || a.length !== b.length) return false;
        for (var i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
        return true;
    }

    // --- the camera: orbit form (as the relay reports it) and picking

    // The eye of an orbit camera: yaw about +y from +x towards +z, pitch up from the ground plane,
    // dist from the target. The relay turns a VIEW back into this same orbit (relay.h orbitOf).
    function orbitEye(orbit) {
        var cp = Math.cos(orbit.pitch);
        return [
            orbit.target[0] + orbit.dist * cp * Math.cos(orbit.yaw),
            orbit.target[1] + orbit.dist * Math.sin(orbit.pitch),
            orbit.target[2] + orbit.dist * cp * Math.sin(orbit.yaw)
        ];
    }

    function sub(a, b) { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; }
    function dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
    function unit(a) {
        var n = Math.sqrt(dot(a, a)) || 1;
        return [a[0] / n, a[1] / n, a[2] / n];
    }

    function basis(view) {
        var forward = unit(sub(view.target, view.eye));
        var right = unit(cross(forward, [0, 1, 0]));
        return [forward, right, cross(right, forward)];
    }

    // Where world point p lands on a width x height canvas (CSS pixels from the top left), with
    // its depth along the view; null when it is behind the eye.
    function project(view, p, width, height) {
        var b = basis(view);
        var offset = sub(p, view.eye);
        var depth = dot(offset, b[0]);
        if (depth <= 1e-6) return null;
        var focal = 1 / Math.tan((view.fovDegrees * Math.PI) / 360);
        var aspect = width / height;
        return {
            x: ((focal / aspect) * dot(offset, b[1]) / depth + 1) / 2 * width,
            y: (1 - (focal * dot(offset, b[2])) / depth) / 2 * height,
            depth: depth
        };
    }

    // The ray from the eye through canvas pixel (x, y): {origin, direction} (direction not unit).
    function rayAt(view, x, y, width, height) {
        var b = basis(view);
        var focal = 1 / Math.tan((view.fovDegrees * Math.PI) / 360);
        var aspect = width / height;
        var nx = (x / width) * 2 - 1;
        var ny = 1 - (y / height) * 2;
        var d = [0, 1, 2].map(function (i) {
            return b[0][i] + (b[1][i] * nx * aspect) / focal + (b[2][i] * ny) / focal;
        });
        return { origin: view.eye.slice(), direction: d };
    }

    // Where a ray meets the horizontal plane at height y, as [x, y, z], or null if it never does.
    function hitPlaneY(ray, y) {
        var dy = ray.direction[1];
        if (Math.abs(dy) < 1e-9) return null;
        var t = (y - ray.origin[1]) / dy;
        if (t <= 0) return null;
        return [ray.origin[0] + ray.direction[0] * t, y, ray.origin[2] + ray.direction[2] * t];
    }

    function toBase64(bytes) {
        var s = '';
        for (var i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
        return typeof btoa === 'function' ? btoa(s) : Buffer.from(bytes).toString('base64');
    }

    return {
        MAJOR: MAJOR,
        MINOR: MINOR,
        KIND: KIND,
        PRIM: PRIM,
        ATTACH: ATTACH,
        parseCss: parseCss,
        blend: blend,
        rgba: rgba,
        shape: shape,
        encodeBoard: encodeBoard,
        encodePieces: encodePieces,
        encodeHud: encodeHud,
        encodeView: encodeView,
        decode: decode,
        sameBytes: sameBytes,
        orbitEye: orbitEye,
        project: project,
        rayAt: rayAt,
        hitPlaneY: hitPlaneY,
        toBase64: toBase64
    };
});
