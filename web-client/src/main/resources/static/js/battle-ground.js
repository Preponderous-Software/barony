/* battle-ground.js — the lie of the land a field battle is fought on.
 *
 * No DOM and no engine, so it runs under node too. Barony's map only knows castles, villages and
 * open ground, so the battlefield's woods, rises and streams are worked out from where the battle
 * is: the same map tile always gives the same ground, and a custom battle takes its ground from
 * its seed. Neighbouring villages and castles on the map stand at the edges of the field.
 *
 *   BattleGround.forTile({x, y, type, grid})   -> ground for a battle on map tile (x, y)
 *   BattleGround.forSeed(seed, type)           -> ground for a custom battle
 *   BattleGround.at(ground, x, z)              -> {height, wood, water} at a point on the field
 *   BattleGround.describe(ground)              -> one sentence about the ground, for the page
 *
 * On a map built from a real place each tile also says what its land is (tile.ground: snow, sand,
 * forest...); that comes along as ground.cover and only changes how the field looks.
 *
 * The ground only matters on the 3D battlefield (battle-sim.js reads it): woods slow horses and
 * hide those in them from arrows, a rise lends its holders a stronger blow and a longer bowshot,
 * and a stream slows anyone wading it. The map's own rule ignores it, as it always has.
 *
 * Field coordinates as in battle-sim.js: x across, z along; side 0 forms up at negative z and
 * side 1 at positive z. The fight happens within |x| < 25 and |z| < 40.
 */
(function (root) {
    'use strict';

    var RISE_STEPS = 5;              // a rise is drawn, and stood on, as this many terraces

    // What a real map's tile can say its land is, and the word the page's hint opens with for it
    // (short, so the hint still fits a phone).
    var COVERS = {
        plain: '', forest: 'Forest.', snow: 'Snow.', sand: 'Sand.', stone: 'Stony.', badland: 'Badlands.',
        ice: 'Ice.', water: 'Marsh.', built: 'Town.', farm: 'Farmland.'
    };

    function isCover(c) { return typeof c === 'string' && Object.prototype.hasOwnProperty.call(COVERS, c); }

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

    function clamp(v, lo, hi) { return v < lo ? lo : v > hi ? hi : v; }

    // A stable number for a map tile, so its ground never changes between battles or visits.
    function tileSeed(x, y) {
        var h = Math.imul((x | 0) + 0x9e37, 0x85ebca6b) ^ Math.imul((y | 0) + 0x79b9, 0xc2b2ae35);
        h ^= h >>> 16; h = Math.imul(h, 0x7feb352d); h ^= h >>> 15;
        return h >>> 0;
    }

    function empty() {
        return { woods: [], rises: [], stream: null, edges: [] };
    }

    // The features themselves. Woods keep to the flanks so the lines can always meet in the
    // middle; a rise sits on one side of the field or the other; a stream, when there is one,
    // runs across the field between the two lines. A castle's ground stays open in front of its
    // gate (the walls stand at z = 52), and a village keeps its well clear.
    function generate(rand, type) {
        var g = empty();
        var clumps = 1 + Math.floor(rand() * 3);                       // 1..3 woods
        for (var i = 0; i < clumps; i++) {
            var flank = rand() < 0.5 ? -1 : 1;
            g.woods.push({
                x: flank * (12 + rand() * 10),
                z: (rand() - 0.5) * 44,
                r: 4.5 + rand() * 3.5
            });
        }
        if (rand() < 0.7) {
            var towards = rand() < 0.5 ? -1 : 1;                        // which side's half it is in
            g.rises.push({
                x: (rand() - 0.5) * 26,
                z: towards * (8 + rand() * 12),
                r: 9 + rand() * 6,
                h: 1.6 + rand() * 1.6
            });
        }
        if (type !== 'CASTLE' && rand() < 0.35) {
            g.stream = { z: (rand() - 0.5) * 10, width: 2.6 + rand() * 1.2 };
        }
        return g;
    }

    // What stands beside the battle tile on the map, by compass side, drawn past the field's
    // edges: map +x is the field's +x, and map +y is the field's -z.
    function edgesFrom(grid, x, y) {
        var out = [];
        if (!grid) return out;
        var dirs = [[1, 0, 'east'], [-1, 0, 'west'], [0, 1, 'south'], [0, -1, 'north']];
        for (var i = 0; i < dirs.length; i++) {
            var col = grid[x + dirs[i][0]], tile = col && col[y + dirs[i][1]];
            if (tile && (tile.type === 'VILLAGE' || tile.type === 'CASTLE')) {
                out.push({ dir: dirs[i][2], type: tile.type, ownerId: tile.ownerId | 0 });
            }
        }
        return out;
    }

    function forTile(tile) {
        tile = tile || {};
        var type = tile.type === 'VILLAGE' || tile.type === 'CASTLE' ? tile.type : 'EMPTY';
        var g = generate(rng(tileSeed(tile.x, tile.y)), type);
        g.edges = edgesFrom(tile.grid, tile.x | 0, tile.y | 0);
        var col = tile.grid && tile.grid[tile.x | 0], here = col && col[tile.y | 0];
        if (here && isCover(here.ground)) g.cover = here.ground;
        return g;
    }

    function forSeed(seed, type) {
        return generate(rng((seed >>> 0) ^ 0x5bd1e995), type === 'VILLAGE' || type === 'CASTLE' ? type : 'EMPTY');
    }

    // Anything else is no ground at all, so a battle given nothing fights on a bare field.
    function clean(g) {
        if (!g || typeof g !== 'object') return null;
        var out = empty();
        function num(v, lo, hi, d) { var n = Number(v); return Number.isFinite(n) ? clamp(n, lo, hi) : d; }
        (Array.isArray(g.woods) ? g.woods : []).slice(0, 8).forEach(function (w) {
            out.woods.push({ x: num(w.x, -60, 60, 0), z: num(w.z, -60, 60, 0), r: num(w.r, 1, 15, 5) });
        });
        (Array.isArray(g.rises) ? g.rises : []).slice(0, 3).forEach(function (r) {
            out.rises.push({ x: num(r.x, -60, 60, 0), z: num(r.z, -60, 60, 0), r: num(r.r, 3, 25, 10), h: num(r.h, 0.5, 5, 2) });
        });
        if (g.stream && typeof g.stream === 'object') out.stream = { z: num(g.stream.z, -30, 30, 0), width: num(g.stream.width, 1, 8, 3) };
        (Array.isArray(g.edges) ? g.edges : []).slice(0, 4).forEach(function (e) {
            if (e && ['east', 'west', 'north', 'south'].indexOf(e.dir) >= 0 && (e.type === 'VILLAGE' || e.type === 'CASTLE')) {
                out.edges.push({ dir: e.dir, type: e.type, ownerId: e.ownerId | 0 });
            }
        });
        if (isCover(g.cover)) out.cover = g.cover;
        return out;
    }

    // The height of a rise at a distance from its centre: a flat top over its middle third, then
    // RISE_STEPS terraces down to the plain. Drawn with the same steps, so figures stand on them.
    function riseHeight(r, d) {
        if (d >= r.r) return 0;
        var t = clamp((r.r - d) / (r.r * 0.67), 0, 1);             // 0 at the foot, 1 on the top
        return r.h * Math.ceil(t * RISE_STEPS - 1e-9) / RISE_STEPS;
    }

    function at(g, x, z) {
        var out = { height: 0, wood: false, water: false };
        if (!g) return out;
        for (var i = 0; i < g.rises.length; i++) {
            var r = g.rises[i], dx = x - r.x, dz = z - r.z;
            out.height = Math.max(out.height, riseHeight(r, Math.sqrt(dx * dx + dz * dz)));
        }
        for (var j = 0; j < g.woods.length; j++) {
            var w = g.woods[j], wx = x - w.x, wz = z - w.z;
            if (wx * wx + wz * wz < w.r * w.r) { out.wood = true; break; }
        }
        if (g.stream && Math.abs(z - g.stream.z) < g.stream.width / 2 && out.height === 0) out.water = true;
        return out;
    }

    // A short sentence about the ground, for the page's hint (the player's guide says what each
    // feature does): "Woods on both flanks, a rise on your side, a stream between the lines."
    function describe(g) {
        if (!g) return '';
        var parts = [];
        if (g.woods.length) {
            var left = g.woods.some(function (w) { return w.x < 0; }), right = g.woods.some(function (w) { return w.x > 0; });
            parts.push((g.woods.length === 1 ? 'a wood' : 'woods') + ' on ' + (left && right ? 'both flanks' : 'one flank'));
        }
        if (g.rises.length) parts.push('a rise on ' + (g.rises[0].z < 0 ? 'your' : 'the enemy\u2019s') + ' side');
        if (g.stream) parts.push('a stream between the lines');
        var lead = isCover(g.cover) && COVERS[g.cover] ? COVERS[g.cover] + ' ' : '';
        if (!parts.length) return lead + 'Open ground.';
        var s = parts.join(', ') + '.';
        s = s.charAt(0).toUpperCase() + s.slice(1);
        return (lead + s).length <= 80 ? lead + s : s;      // the land gives way when the hint is full
    }

    var api = { RISE_STEPS: RISE_STEPS, forTile: forTile, forSeed: forSeed, clean: clean, at: at,
                riseHeight: riseHeight, describe: describe, tileSeed: tileSeed };
    if (typeof module === 'object' && module.exports) module.exports = api;
    else root.BattleGround = api;
})(this);
