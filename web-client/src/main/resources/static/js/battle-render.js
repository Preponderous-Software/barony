/* battle-render.js — turns a BattleSim battle into Strata primitive rows.
 *
 * No DOM and no engine, so it runs under node too. The page copies the rows into the wasm heap
 * each frame (bt_set_prims / bt_set_points / bt_set_lights in web-client/battle3d/main.cpp).
 *
 *   BattleRender.scenery(terrain, seed)  -> Float32Array of static rows (ground, trees, huts, walls)
 *   BattleRender.frame(battle, opts)     -> {prims, points, lights}: this frame's figures and effects
 *   BattleRender.hexToLinear('#0066FF')  -> [r, g, b] in linear light (Strata takes linear colour)
 *
 * A row is 10 floats: kind, x, y, z, sx, sy, sz, r, g, b. Strata's primitives are axis-aligned
 * (cube 1 unit, capsule and cylinder 1 across and 2 tall, sphere 1 across), so a figure is posed
 * by where its parts sit, not by turning them: a shield sits on the side the figure faces.
 */
(function (root) {
    'use strict';

    var CUBE = 0, CAPSULE = 1, CYLINDER = 2, SPHERE = 3;
    var STRIDE = 10;

    function srgbToLinear(c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    function hexToLinear(hex) {
        var m = /^#?([0-9a-f]{6})$/i.exec(String(hex || ''));
        if (!m) return [0.5, 0.5, 0.5];
        var n = parseInt(m[1], 16);
        return [srgbToLinear(((n >> 16) & 255) / 255), srgbToLinear(((n >> 8) & 255) / 255), srgbToLinear((n & 255) / 255)];
    }

    function mix(a, b, t) { return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]; }
    function scale(c, k) { return [c[0] * k, c[1] * k, c[2] * k]; }

    // A growable list of rows.
    function Rows() { this.a = []; }
    Rows.prototype.add = function (kind, x, y, z, sx, sy, sz, c) {
        this.a.push(kind, x, y, z, sx, sy, sz, c[0], c[1], c[2]);
    };
    Rows.prototype.f32 = function () { return new Float32Array(this.a); };

    var STEEL = hexToLinear('#9aa3ad');
    var DARK_STEEL = hexToLinear('#5d646c');
    var WOOD = hexToLinear('#7a5634');
    var SKIN = hexToLinear('#d9a882');
    var LEATHER = hexToLinear('#5a3d24');
    var GOLD = hexToLinear('#d8a92a');
    var GRASS = hexToLinear('#5d7f3a');
    var FIELD = hexToLinear('#6f7d45');
    var MUD = hexToLinear('#6b5a3e');
    var STONE = hexToLinear('#8b8780');
    var DARK_STONE = hexToLinear('#6a665f');
    var THATCH = hexToLinear('#b8954d');
    var PLASTER = hexToLinear('#d6cbb5');
    var LEAF = hexToLinear('#3d6a2c');
    var LEAF2 = hexToLinear('#4f7a33');
    var BARK = hexToLinear('#5b4330');
    var BLOOD = hexToLinear('#7a1712');

    // ------------------------------------------------------------- scenery

    function rng(seed) {
        var a = seed >>> 0;
        return function () {
            a = (a + 0x6D2B79F5) >>> 0;
            var t = a;
            t = Math.imul(t ^ (t >>> 15), t | 1);
            t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
            return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
        };
    }

    function tree(r, x, z, h) {
        r.add(CYLINDER, x, h * 0.25, z, 0.35, h * 0.25, 0.35, BARK);
        r.add(SPHERE, x, h * 0.62, z, h * 0.55, h * 0.6, h * 0.55, LEAF);
        r.add(SPHERE, x + 0.4, h * 0.82, z - 0.3, h * 0.4, h * 0.42, h * 0.4, LEAF2);
    }

    function hut(r, x, z, w, d) {
        r.add(CUBE, x, 1.3, z, w, 2.6, d, PLASTER);
        r.add(CUBE, x, 0.15, z, w + 0.1, 0.3, d + 0.1, DARK_STONE);
        // A pitched roof from stepped slabs, narrowing upwards along the ridge.
        for (var k = 0; k < 4; k++) r.add(CUBE, x, 2.75 + k * 0.42, z, w + 0.6 - k * (w / 4), 0.44, d + 0.6, THATCH);
        r.add(CUBE, x, 1.0, z + d / 2 + 0.02, 0.9, 1.8, 0.08, WOOD);
    }

    function tower(r, x, z, h) {
        r.add(CYLINDER, x, h / 2, z, 4.2, h / 2, 4.2, STONE);
        for (var k = 0; k < 8; k++) {
            var a = k / 8 * Math.PI * 2;
            r.add(CUBE, x + Math.cos(a) * 1.9, h + 0.45, z + Math.sin(a) * 1.9, 0.7, 0.9, 0.7, DARK_STONE);
        }
    }

    function wall(r, x0, x1, z, h) {
        r.add(CUBE, (x0 + x1) / 2, h / 2, z, Math.abs(x1 - x0), h, 2.2, STONE);
        for (var x = Math.min(x0, x1) + 0.6; x < Math.max(x0, x1); x += 1.6) r.add(CUBE, x, h + 0.4, z, 0.8, 0.8, 2.2, DARK_STONE);
    }

    // The ground and what stands around the field, by Barony tile type. The fight happens in the
    // middle (|x| < 25, |z| < 40); everything here keeps clear of it.
    function scenery(terrain, seed) {
        var r = new Rows(), rand = rng((seed >>> 0) ^ 0x9e3779b9);
        r.add(CUBE, 0, -0.5, 0, 600, 1, 600, GRASS);
        r.add(CUBE, 0, 0.005, 0, 60, 0.01, 90, FIELD);
        // Trampled mud where the lines will meet.
        r.add(CUBE, 0, 0.012, 0, 34, 0.01, 18, MUD);
        var i, x, z;
        for (i = 0; i < 70; i++) {
            var side = rand() < 0.5 ? -1 : 1;
            x = side * (34 + rand() * 60);
            z = (rand() - 0.5) * 200;
            tree(r, x, z, 4 + rand() * 4);
        }
        for (i = 0; i < 24; i++) {
            x = (rand() - 0.5) * 160;
            z = (rand() < 0.5 ? -1 : 1) * (60 + rand() * 60);
            tree(r, x, z, 4 + rand() * 3);
        }
        if (terrain === 'VILLAGE') {
            var spots = [[-36, -12], [-38, 4], [-35, 18], [36, -8], [38, 9], [35, 24], [-30, 32], [30, -30]];
            for (i = 0; i < spots.length; i++) hut(r, spots[i][0], spots[i][1], 5 + rand() * 2, 4 + rand() * 2);
            // A well and fences.
            r.add(CYLINDER, -28, 0.45, 0, 1.6, 0.45, 1.6, STONE);
            r.add(CUBE, -28, 2.3, 0, 2.2, 0.15, 2.2, WOOD);
            for (z = -30; z <= 30; z += 3) {
                r.add(CUBE, -31, 0.5, z, 0.15, 1.0, 0.15, WOOD);
                r.add(CUBE, 31, 0.5, z, 0.15, 1.0, 0.15, WOOD);
            }
            r.add(CUBE, -31, 0.75, 0, 0.08, 0.1, 60, WOOD);
            r.add(CUBE, 31, 0.75, 0, 0.08, 0.1, 60, WOOD);
        } else if (terrain === 'CASTLE') {
            // The defenders (side 1) fight in front of their walls.
            var zw = 52;
            wall(r, -40, -6, zw, 9);
            wall(r, 6, 40, zw, 9);
            tower(r, -42, zw, 13);
            tower(r, 42, zw, 13);
            tower(r, -7.5, zw, 11);
            tower(r, 7.5, zw, 11);
            r.add(CUBE, 0, 3.2, zw + 0.3, 4.8, 6.4, 1.6, WOOD);     // the gate
            r.add(CUBE, 0, 8.2, zw, 12, 3.6, 2.2, STONE);            // over the gate
            r.add(CYLINDER, 0, 14, zw + 8, 6, 14, 6, STONE);         // the keep behind
            r.add(CUBE, 0, 29, zw + 8, 0.2, 2, 0.2, WOOD);
        }
        return r.f32();
    }

    // Torches on the castle walls and by the village well: point lights the page keeps each frame.
    function sceneryLights(terrain) {
        var warm = [1.0, 0.62, 0.28];
        if (terrain === 'CASTLE') return [[-7.5, 12, 49, 16, warm, 2.2], [7.5, 12, 49, 16, warm, 2.2], [0, 5, 50.5, 12, warm, 2.6]];
        if (terrain === 'VILLAGE') return [[-28, 2.6, 0, 9, warm, 1.6]];
        return [];
    }

    // ------------------------------------------------------------- figures

    // Which of the four axis directions a facing is closest to, as unit (x, z).
    function axisOf(f) {
        var c = Math.cos(f), s = Math.sin(f);
        return Math.abs(c) >= Math.abs(s) ? [c > 0 ? 1 : -1, 0] : [0, s > 0 ? 1 : -1];
    }

    // One figure. look: {tunic, trim, helmet, weapon, shield, tall, hideHead}
    function figure(r, u, look, t) {
        var fx = Math.cos(u.facing), fz = Math.sin(u.facing);      // forward
        var rx = -fz, rz = fx;                                     // right
        var x = u.x, z = u.z;
        var k = look.tall || 1;
        var tunic = u.hurt > 0 ? mix(look.tunic, [1, 0.3, 0.25], u.hurt * 2.5) : look.tunic;

        if (u.state === 'dead') {
            // Fallen, along the axis nearest the blow that felled it.
            var a = axisOf(u.fallFacing), along = 0.85 * k;
            var lx = a[0] !== 0 ? along : 0.42, lz = a[1] !== 0 ? along : 0.42;
            r.add(CUBE, x + a[0] * 0.2, 0.17, z + a[1] * 0.2, lx * 1.6, 0.3, lz * 1.6, scale(tunic, 0.75));
            r.add(SPHERE, x + a[0] * 0.95 * k, 0.18, z + a[1] * 0.95 * k, 0.3, 0.3, 0.3, look.helmet === 'none' ? SKIN : STEEL);
            r.add(CUBE, x - a[1] * 0.5, 0.03, z + a[0] * 0.5, 0.8, 0.02, 0.6, BLOOD);
            return;
        }

        var stride = Math.sin(u.stride) * Math.min(1, u.moving * 1.6);
        var lunge = u.swing > 0 ? Math.sin((1 - u.swing / 0.45) * Math.PI) : 0;   // 0..1..0 through a swing
        var crouch = u.blocking ? 0.08 : 0;
        // legs
        r.add(CYLINDER, x + rx * 0.13 + fx * stride * 0.22, 0.24 * k, z + rz * 0.13 + fz * stride * 0.22, 0.17 * k, 0.24 * k, 0.17 * k, LEATHER);
        r.add(CYLINDER, x - rx * 0.13 - fx * stride * 0.22, 0.24 * k, z - rz * 0.13 - fz * stride * 0.22, 0.17 * k, 0.24 * k, 0.17 * k, LEATHER);
        // body, leaning into a blow
        var bx = x + fx * lunge * 0.12, bz = z + fz * lunge * 0.12, by = (0.98 - crouch) * k;
        r.add(CAPSULE, bx, by, bz, 0.52 * k, 0.34 * k, 0.52 * k, tunic);
        r.add(CUBE, bx, by - 0.12 * k, bz, 0.5 * k, 0.07 * k, 0.5 * k, LEATHER);   // belt
        var hy = by + 0.55 * k;
        if (!look.hideHead) {
            r.add(SPHERE, bx, hy, bz, 0.3 * k, 0.32 * k, 0.3 * k, SKIN);
            helmet(r, look.helmet, bx, hy, bz, k, look);
        }
        // weapon arm on the right
        var wx = bx + rx * 0.38 + fx * (0.1 + lunge * 0.55), wz = bz + rz * 0.38 + fz * (0.1 + lunge * 0.55);
        weapon(r, look.weapon, wx, by - 0.05 + lunge * 0.15, wz, k, fx, fz, lunge);
        // shield on the left, in front; square to the axis nearest the facing
        if (look.shield && u.state !== 'routing') {
            var ax = axisOf(u.facing), out = u.blocking ? 0.42 : 0.3;
            var sx = bx - rx * (u.blocking ? 0.05 : 0.3) + fx * out, sz = bz - rz * (u.blocking ? 0.05 : 0.3) + fz * out;
            var w = 0.62 * k, th = 0.07;
            var shieldY = by + (u.blocking ? 0.12 : 0);
            r.add(CUBE, sx, shieldY, sz, ax[0] !== 0 ? th : w, 0.72 * k, ax[1] !== 0 ? th : w, look.shieldFace || scale(look.tunic, 0.7));
            // The boss: a light disc in the middle of the face.
            r.add(SPHERE, sx + ax[0] * 0.05, shieldY, sz + ax[1] * 0.05, 0.16 * k, 0.16 * k, 0.16 * k, look.trim);
        }
    }

    function helmet(r, kind, x, y, z, k, look) {
        switch (kind) {
            case 'none': return;
            case 'hood':
                r.add(SPHERE, x, y + 0.04 * k, z, 0.36 * k, 0.36 * k, 0.36 * k, scale(look.tunic, 0.6));
                return;
            case 'crown':
                r.add(CYLINDER, x, y + 0.2 * k, z, 0.34 * k, 0.07 * k, 0.34 * k, GOLD);
                for (var i = 0; i < 4; i++) {
                    var a = i * Math.PI / 2 + 0.4;
                    r.add(CUBE, x + Math.cos(a) * 0.15 * k, y + 0.3 * k, z + Math.sin(a) * 0.15 * k, 0.06 * k, 0.1 * k, 0.06 * k, GOLD);
                }
                return;
            case 'greathelm':
                r.add(CYLINDER, x, y + 0.03 * k, z, 0.38 * k, 0.21 * k, 0.38 * k, STEEL);
                r.add(CUBE, x, y + 0.05 * k, z, 0.39 * k, 0.04 * k, 0.39 * k, DARK_STEEL);  // the eye slit
                return;
            case 'plume':
                r.add(SPHERE, x, y + 0.08 * k, z, 0.38 * k, 0.24 * k, 0.38 * k, STEEL);
                r.add(SPHERE, x, y + 0.34 * k, z, 0.14 * k, 0.3 * k, 0.14 * k, look.trim);
                return;
            default:  // kettle hat
                r.add(SPHERE, x, y + 0.1 * k, z, 0.36 * k, 0.2 * k, 0.36 * k, STEEL);
                r.add(CYLINDER, x, y + 0.05 * k, z, 0.5 * k, 0.015 * k, 0.5 * k, DARK_STEEL);
        }
    }

    function weapon(r, kind, x, y, z, k, fx, fz, lunge) {
        switch (kind) {
            case 'sword':
                r.add(CUBE, x, y + 0.45 * k, z, 0.06, 0.95 * k, 0.06, STEEL);
                r.add(CUBE, x, y - 0.05 * k, z, 0.28, 0.05, 0.28, GOLD);
                return;
            case 'axe':
                r.add(CYLINDER, x, y + 0.25 * k, z, 0.07, 0.5 * k, 0.07, WOOD);
                r.add(CUBE, x + fx * 0.14, y + 0.62 * k, z + fz * 0.14, 0.12 + Math.abs(fx) * 0.18, 0.28, 0.12 + Math.abs(fz) * 0.18, DARK_STEEL);
                return;
            case 'hammer':
                r.add(CYLINDER, x, y + 0.25 * k, z, 0.07, 0.5 * k, 0.07, WOOD);
                r.add(CUBE, x, y + 0.7 * k, z, 0.3, 0.18, 0.3, DARK_STEEL);
                return;
            default:  // spear, levelled forward in a thrust
                var level = lunge * 0.6;
                r.add(CYLINDER, x + fx * level, y + 0.4 * k - level * 0.4, z + fz * level, 0.05, 1.05 * k, 0.05, WOOD);
                r.add(SPHERE, x + fx * level * 1.6, y + 1.48 * k - level * 0.9, z + fz * level * 1.6, 0.08, 0.2, 0.08, STEEL);
        }
    }

    // A side's standard, behind its starting line.
    function banner(r, side, colour, t) {
        var z = side === 0 ? -30 : 30, wave = Math.sin(t * 2.2) * 0.12;
        r.add(CYLINDER, 0, 2.5, z, 0.12, 2.5, 0.12, WOOD);
        r.add(CUBE, 0.75, 4.3 + wave * 0.3, z, 1.4, 0.95, 0.06, colour);
        r.add(SPHERE, 0, 5.1, z, 0.22, 0.22, 0.22, GOLD);
    }

    // opts: {colours: [hex, hex], time, player: unit id or null, firstPerson, avatar}
    function frame(b, opts) {
        opts = opts || {};
        var r = new Rows();
        var side = [hexToLinear(opts.colours ? opts.colours[0] : '#0066FF'), hexToLinear(opts.colours ? opts.colours[1] : '#FF0000')];
        var trims = [mix(side[0], [1, 1, 1], 0.45), mix(side[1], [1, 1, 1], 0.45)];
        var t = opts.time || 0;
        banner(r, 0, side[0], t);
        banner(r, 1, side[1], t + 1.3);
        var player = b.player;
        for (var i = 0; i < b.units.length; i++) {
            var u = b.units[i];
            if (u.state === 'escaped') continue;
            var look;
            if (u.kind === 'avatar' && u.avatar) {
                var av = u.avatar;
                look = { tunic: hexToLinear(av.tunic), trim: hexToLinear(av.trim), shieldFace: hexToLinear(av.trim), helmet: av.helmet, weapon: av.weapon,
                         shield: av.shield !== false, tall: 1.12 };
            } else {
                look = { tunic: scale(side[u.side], 0.85 + (u.id % 5) * 0.04), trim: trims[u.side], helmet: 'kettle',
                         weapon: u.id % 7 === 0 ? 'axe' : 'spear', shield: true, tall: 0.96 + (u.id % 9) * 0.01 };
            }
            look.hideHead = u.id === player && !!opts.firstPerson;
            figure(r, u, look, t);
        }
        // A ring under the player's figure, so it can be found in a crowd.
        var me = player != null ? findUnit(b, player) : null;
        var points = [], lights = [];
        if (me && me.state !== 'dead') {
            r.add(CYLINDER, me.x, 0.025, me.z, 0.95, 0.01, 0.95, scale(GOLD, 0.6));
            lights.push([me.x, 2.6, me.z, 6, [1, 0.85, 0.5], 1.2]);
        }
        // Dust from the moving and sparks from blows this frame.
        for (var j = 0; j < (opts.events || []).length; j++) {
            var e = opts.events[j];
            if (e.type === 'hit') {
                for (var q = 0; q < 3; q++) points.push([e.x + (q - 1) * 0.15, 1.1 + q * 0.12, e.z, 0.18, 1, e.blocked ? 0.95 : 0.55, e.blocked ? 0.6 : 0.35, 0.9]);
            }
        }
        return { prims: r.f32(), points: points, lights: lights };
    }

    function findUnit(b, id) {
        for (var i = 0; i < b.units.length; i++) if (b.units[i].id === id) return b.units[i];
        return null;
    }

    var api = { STRIDE: STRIDE, CUBE: CUBE, CAPSULE: CAPSULE, CYLINDER: CYLINDER, SPHERE: SPHERE,
                hexToLinear: hexToLinear, scenery: scenery, sceneryLights: sceneryLights, frame: frame, figure: figure };
    if (typeof module === 'object' && module.exports) module.exports = api;
    else root.BattleRender = api;
})(this);
