/* battle-render.js — turns a BattleSim battle into Strata primitive rows.
 *
 * No DOM and no engine, so it runs under node too. The page copies the rows into the wasm heap
 * each frame (bt_set_prims / bt_set_points / bt_set_lights in web-client/battle3d/main.cpp).
 *
 *   BattleRender.scenery(terrain, seed, ground) -> Float32Array of static rows (ground, trees, huts,
 *                                        walls, and the ground's woods, rises, stream and neighbours)
 *   BattleRender.frame(battle, opts)     -> {prims, points, lights}: this frame's figures and effects
 *   BattleRender.hexToLinear('#0066FF')  -> [r, g, b] in linear light (Strata takes linear colour)
 *
 * A row is 10 floats: kind, x, y, z, sx, sy, sz, r, g, b. Strata's primitives are axis-aligned
 * (cube 1 unit, capsule and cylinder 1 across and 2 tall, sphere 1 across), so a figure is posed
 * by where its parts sit, not by turning them: a shield sits on the side the figure faces.
 */
(function (root) {
    'use strict';

    var Ground = typeof module === 'object' && module.exports ? require('./battle-ground.js') : root.BattleGround;

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
    // dy lifts everything added while it is set: a figure standing on a rise.
    function Rows() { this.a = []; this.dy = 0; }
    Rows.prototype.add = function (kind, x, y, z, sx, sy, sz, c) {
        this.a.push(kind, x, y + this.dy, z, sx, sy, sz, c[0], c[1], c[2]);
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
    var HORSES = [hexToLinear('#6b4a2e'), hexToLinear('#3e2c1f'), hexToLinear('#8a6a48'), hexToLinear('#5a5550')];
    var FLETCH = hexToLinear('#e8e2d0');
    var WATER = hexToLinear('#3f6f8f');
    var UNDERGROWTH = hexToLinear('#3f5a2a');
    var RISE = [hexToLinear('#6c8a42'), hexToLinear('#728f46'), hexToLinear('#78944a'), hexToLinear('#7e994e'), hexToLinear('#849e52')];

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

    // How a real map's land looks: the colour of the ground and the field, and how many trees stand
    // around it compared with green open country.
    var COVER_LOOK = {
        forest: { grass: hexToLinear('#46682e'), field: hexToLinear('#5a7238'), trees: 2 },
        snow: { grass: hexToLinear('#e4eaee'), field: hexToLinear('#d3dadd'), trees: 0.8 },
        ice: { grass: hexToLinear('#cfe1ea'), field: hexToLinear('#c0d2dc'), trees: 0.3 },
        sand: { grass: hexToLinear('#d6c28d'), field: hexToLinear('#c9b27a'), trees: 0.25 },
        badland: { grass: hexToLinear('#b5714b'), field: hexToLinear('#a5653f'), trees: 0.2 },
        stone: { grass: hexToLinear('#8c897f'), field: hexToLinear('#7e7b71'), trees: 0.5 },
        farm: { grass: hexToLinear('#97984a'), field: hexToLinear('#a29f52'), trees: 0.5 },
        water: { grass: hexToLinear('#6c876a'), field: hexToLinear('#6d7c55'), trees: 0.6 },
        built: { grass: hexToLinear('#7c796d'), field: hexToLinear('#75725f'), trees: 0.4 }
    };

    // The ground and what stands around the field, by Barony tile type. The fight happens in the
    // middle (|x| < 25, |z| < 40); everything here keeps clear of it.
    function scenery(terrain, seed, ground) {
        var r = new Rows(), rand = rng((seed >>> 0) ^ 0x9e3779b9);
        var look = (ground && COVER_LOOK[ground.cover]) || null;
        var trees = look ? look.trees : 1;
        r.add(CUBE, 0, -0.5, 0, 600, 1, 600, look ? look.grass : GRASS);
        r.add(CUBE, 0, 0.005, 0, 60, 0.01, 90, look ? look.field : FIELD);
        // Trampled mud where the lines will meet.
        r.add(CUBE, 0, 0.012, 0, 34, 0.01, 18, MUD);
        var i, x, z;
        for (i = 0; i < Math.round(70 * trees); i++) {
            var side = rand() < 0.5 ? -1 : 1;
            x = side * (34 + rand() * 60);
            z = (rand() - 0.5) * 200;
            tree(r, x, z, 4 + rand() * 4);
        }
        for (i = 0; i < Math.round(24 * trees); i++) {
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
        if (ground) groundFeatures(r, ground, seed);
        return r.f32();
    }

    // The ground's woods, rises and stream, and the map's neighbouring villages and castles past
    // the field's edges. Terraces match BattleGround.riseHeight, so figures stand on them.
    function groundFeatures(r, g, seed) {
        var rand = rng((seed >>> 0) ^ 0x2545f491), i, k;
        if (g.stream) {
            r.add(CUBE, 0, 0.02, g.stream.z, 160, 0.02, g.stream.width, WATER);
            r.add(CUBE, 0, 0.015, g.stream.z, 160, 0.015, g.stream.width + 1.4, MUD);
        }
        for (i = 0; i < g.rises.length; i++) {
            var ri = g.rises[i], steps = Ground.RISE_STEPS;
            for (k = 1; k <= steps; k++) {
                var radius = ri.r - (k - 1) / steps * ri.r * 0.67, top = ri.h * k / steps;
                r.add(CYLINDER, ri.x, top / 2, ri.z, radius * 2, top / 2, radius * 2, RISE[(k - 1) % RISE.length]);
            }
        }
        for (i = 0; i < g.woods.length; i++) {
            var w = g.woods[i], base = Ground.at(g, w.x, w.z).height;
            r.add(CYLINDER, w.x, base + 0.03, w.z, w.r * 2, 0.03, w.r * 2, UNDERGROWTH);
            var n = Math.round(w.r * 1.8);
            for (k = 0; k < n; k++) {
                var a = rand() * Math.PI * 2, d = Math.sqrt(rand()) * (w.r - 0.8);
                var tx = w.x + Math.cos(a) * d, tz = w.z + Math.sin(a) * d;
                r.dy = Ground.at(g, tx, tz).height;
                tree(r, tx, tz, 4.5 + rand() * 3.5);
            }
            r.dy = 0;
        }
        var out = { east: [1, 0], west: [-1, 0], north: [0, 1], south: [0, -1] };
        for (i = 0; i < g.edges.length; i++) {
            var e = g.edges[i], o = out[e.dir];
            var cx = o[0] * 70, cz = o[1] * 72;
            if (e.type === 'VILLAGE') {
                for (k = 0; k < 4; k++) hut(r, cx + (o[1] ? (k - 1.5) * 9 : (rand() - 0.5) * 6), cz + (o[0] ? (k - 1.5) * 9 : (rand() - 0.5) * 6), 5, 4.5);
            } else {
                var along = o[0] ? [cx, cz - 18, cx, cz + 18] : [cx - 18, cz, cx + 18, cz];
                if (o[1]) wall(r, along[0], along[2], cz, 8);
                else r.add(CUBE, cx, 4, cz, 2.2, 8, 36, STONE);
                tower(r, along[0], along[1], 12);
                tower(r, along[2], along[3], 12);
            }
        }
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

    // A horse under a mounted figure: body and neck along the axis nearest its facing (Strata's
    // primitives do not turn), four legs that trot with the stride, and a dark mane and tail.
    function horse(r, u, coat, dead) {
        var a = axisOf(dead ? u.fallFacing : u.facing), x = u.x, z = u.z;
        var lx = a[0] !== 0 ? 1.7 : 0.62, lz = a[1] !== 0 ? 1.7 : 0.62;
        var mane = scale(coat, 0.45);
        if (dead) {
            r.add(CUBE, x - a[1] * 0.4, 0.32, z + a[0] * 0.4, lx, 0.62, lz, scale(coat, 0.8));
            r.add(CUBE, x + a[0] * 1.1 - a[1] * 0.4, 0.22, z + a[1] * 1.1 + a[0] * 0.4, a[0] !== 0 ? 0.7 : 0.3, 0.3, a[1] !== 0 ? 0.7 : 0.3, coat);
            return;
        }
        var trot = Math.sin(u.stride * 1.3) * Math.min(1, u.moving * 1.2);
        r.add(CUBE, x, 1.12, z, lx, 0.62, lz, coat);
        // neck and head, raised ahead
        r.add(CUBE, x + a[0] * 0.95, 1.55 + trot * 0.04, z + a[1] * 0.95, a[0] !== 0 ? 0.45 : 0.3, 0.75, a[1] !== 0 ? 0.45 : 0.3, coat);
        r.add(CUBE, x + a[0] * 1.3, 1.82 + trot * 0.04, z + a[1] * 1.3, a[0] !== 0 ? 0.62 : 0.26, 0.28, a[1] !== 0 ? 0.62 : 0.26, coat);
        r.add(CUBE, x + a[0] * 0.85, 1.75, z + a[1] * 0.85, a[0] !== 0 ? 0.5 : 0.1, 0.5, a[1] !== 0 ? 0.5 : 0.1, mane);
        r.add(CUBE, x - a[0] * 0.95, 1.05, z - a[1] * 0.95, 0.14, 0.7, 0.14, mane);       // tail
        var side = [-a[1], a[0]];
        for (var i = 0; i < 4; i++) {
            var fore = i < 2 ? 0.62 : -0.62, s = i % 2 ? 1 : -1;
            var swing = (i === 0 || i === 3 ? trot : -trot) * 0.22;
            r.add(CYLINDER, x + a[0] * (fore + swing) + side[0] * s * 0.2, 0.42, z + a[1] * (fore + swing) + side[1] * s * 0.2, 0.15, 0.42, 0.15, coat);
        }
    }

    // One figure. look: {tunic, trim, helmet, weapon, shield, tall, hideHead, mounted: coat colour}
    function figure(r, u, look, t) {
        var fx = Math.cos(u.facing), fz = Math.sin(u.facing);      // forward
        var rx = -fz, rz = fx;                                     // right
        var x = u.x, z = u.z;
        var k = look.tall || 1;
        var tunic = u.hurt > 0 ? mix(look.tunic, [1, 0.3, 0.25], u.hurt * 2.5) : look.tunic;
        var lift = look.mounted ? 0.95 : 0;                        // a rider sits this far up

        if (look.mounted) horse(r, u, look.mounted, u.state === 'dead');
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
        if (look.mounted) {
            // legs astride the horse
            r.add(CYLINDER, x + rx * 0.36, lift + 0.32, z + rz * 0.36, 0.15, 0.26, 0.15, LEATHER);
            r.add(CYLINDER, x - rx * 0.36, lift + 0.32, z - rz * 0.36, 0.15, 0.26, 0.15, LEATHER);
        } else {
            // legs
            r.add(CYLINDER, x + rx * 0.13 + fx * stride * 0.22, 0.24 * k, z + rz * 0.13 + fz * stride * 0.22, 0.17 * k, 0.24 * k, 0.17 * k, LEATHER);
            r.add(CYLINDER, x - rx * 0.13 - fx * stride * 0.22, 0.24 * k, z - rz * 0.13 - fz * stride * 0.22, 0.17 * k, 0.24 * k, 0.17 * k, LEATHER);
        }
        // body, leaning into a blow
        var bx = x + fx * lunge * 0.12, bz = z + fz * lunge * 0.12, by = (0.98 - crouch) * k + lift;
        r.add(CAPSULE, bx, by, bz, 0.52 * k, 0.34 * k, 0.52 * k, tunic);
        r.add(CUBE, bx, by - 0.12 * k, bz, 0.5 * k, 0.07 * k, 0.5 * k, LEATHER);   // belt
        var hy = by + 0.55 * k;
        if (!look.hideHead) {
            r.add(SPHERE, bx, hy, bz, 0.3 * k, 0.32 * k, 0.3 * k, SKIN);
            helmet(r, look.helmet, bx, hy, bz, k, look);
        }
        if (look.weapon === 'bow') {
            // The bow held out on the left, drawn back on the right when it looses; a quiver behind.
            var ox = bx - rx * 0.32 + fx * 0.42, oz = bz - rz * 0.32 + fz * 0.42;
            r.add(CYLINDER, ox, by + 0.1, oz, 0.05, 0.62 * k, 0.05, WOOD);
            r.add(SPHERE, ox, by + 0.72 * k, oz, 0.08, 0.08, 0.08, WOOD);
            r.add(SPHERE, ox, by - 0.52 * k, oz, 0.08, 0.08, 0.08, WOOD);
            var draw = 0.2 + lunge * 0.25;
            r.add(SPHERE, bx + rx * 0.2 + fx * (0.42 - draw), by + 0.12, bz + rz * 0.2 + fz * (0.42 - draw), 0.13, 0.13, 0.13, SKIN);
            r.add(CYLINDER, bx - fx * 0.3 + rx * 0.12, by + 0.25, bz - fz * 0.3 + rz * 0.12, 0.17, 0.3, 0.17, LEATHER);
            r.add(CUBE, bx - fx * 0.3 + rx * 0.12, by + 0.62, bz - fz * 0.3 + rz * 0.12, 0.14, 0.12, 0.14, FLETCH);
        } else {
            // weapon arm on the right
            var wx = bx + rx * 0.38 + fx * (0.1 + lunge * 0.55), wz = bz + rz * 0.38 + fz * (0.1 + lunge * 0.55);
            weapon(r, look.weapon, wx, by - 0.05 + lunge * 0.15, wz, k, fx, fz, lunge);
        }
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
            } else if (u.type === 'archer') {
                look = { tunic: mix(scale(side[u.side], 0.85 + (u.id % 5) * 0.04), LEATHER, 0.25), trim: trims[u.side], helmet: 'hood',
                         weapon: 'bow', shield: false, tall: 0.95 + (u.id % 9) * 0.01 };
            } else if (u.type === 'cavalry') {
                look = { tunic: scale(side[u.side], 0.9 + (u.id % 5) * 0.03), trim: trims[u.side], helmet: 'plume',
                         weapon: 'sword', shield: true, tall: 1, mounted: HORSES[u.id % HORSES.length] };
            } else {
                look = { tunic: scale(side[u.side], 0.85 + (u.id % 5) * 0.04), trim: trims[u.side], helmet: 'kettle',
                         weapon: u.id % 7 === 0 ? 'axe' : 'spear', shield: true, tall: 0.96 + (u.id % 9) * 0.01 };
            }
            look.hideHead = u.id === player && !!opts.firstPerson;
            r.dy = b.ground ? Ground.at(b.ground, u.x, u.z).height : 0;
            figure(r, u, look, t);
            r.dy = 0;
        }
        // Arrows in the air, on a shallow arc from bow to target: a shaft along the axis nearest
        // their flight, with pale fletching at the back.
        var arrows = b.arrows || [], now = b.time;
        for (var ai = 0; ai < arrows.length; ai++) {
            var ar = arrows[ai], p = clamp01((now - ar.t0) / (ar.t1 - ar.t0));
            var dist = Math.sqrt((ar.x1 - ar.x0) * (ar.x1 - ar.x0) + (ar.z1 - ar.z0) * (ar.z1 - ar.z0));
            var axz = axisOf(Math.atan2(ar.z1 - ar.z0, ar.x1 - ar.x0));
            var ax = ar.x0 + (ar.x1 - ar.x0) * p, az = ar.z0 + (ar.z1 - ar.z0) * p;
            var ay = 1.5 + Math.sin(p * Math.PI) * Math.min(9, dist * 0.22) - p * 0.4;
            r.add(CUBE, ax, ay, az, axz[0] !== 0 ? 0.9 : 0.07, 0.07, axz[1] !== 0 ? 0.9 : 0.07, WOOD);
            r.add(CUBE, ax - axz[0] * 0.33, ay, az - axz[1] * 0.33, axz[0] !== 0 ? 0.12 : 0.1, 0.1, axz[1] !== 0 ? 0.12 : 0.1, FLETCH);
        }
        // A ring under the player's figure, so it can be found in a crowd.
        var me = player != null ? findUnit(b, player) : null;
        var points = [], lights = [];
        if (me && me.state !== 'dead') {
            r.dy = b.ground ? Ground.at(b.ground, me.x, me.z).height : 0;
            r.add(CYLINDER, me.x, 0.025, me.z, me.type === 'cavalry' ? 1.6 : 0.95, 0.01, me.type === 'cavalry' ? 1.6 : 0.95, scale(GOLD, 0.6));
            lights.push([me.x, 2.6 + r.dy, me.z, 6, [1, 0.85, 0.5], 1.2]);
            r.dy = 0;
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

    function clamp01(v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    function findUnit(b, id) {
        for (var i = 0; i < b.units.length; i++) if (b.units[i].id === id) return b.units[i];
        return null;
    }

    var api = { STRIDE: STRIDE, CUBE: CUBE, CAPSULE: CAPSULE, CYLINDER: CYLINDER, SPHERE: SPHERE,
                hexToLinear: hexToLinear, scenery: scenery, sceneryLights: sceneryLights, frame: frame, figure: figure };
    if (typeof module === 'object' && module.exports) module.exports = api;
    else root.BattleRender = api;
})(this);
