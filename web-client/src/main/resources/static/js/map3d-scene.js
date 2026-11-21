// map3d-scene.js: what the 3D strategy map shows, as plain data. The game page draws it with
// Strata's grid relay (static/map3d/, WIRE.md 1.0) through grid-wire.js; this file holds no DOM
// and no WebGL, so web-client/src/test/js/map3d-scene.test.js checks it under Node.
//
// Every tile is one world unit, the map lying on the ground plane (y = 0) with its middle at
// the origin: tile (x, y) of the 2D map is centred at world (x + 0.5 - width / 2, y + 0.5 -
// height / 2), so the 2D map's x runs along world x and its y along world z, and the default
// camera looks from the south (+z) with row 0 at the far side, as the 2D map has it at the top.
//
// What the 2D canvas draws, and its 3D form here:
// - each tile's ground tint and owner tint, composited as the canvas does    -> the tile's colour
// - the grid lines                                                           -> gaps between tiles
// - a castle (grey square) / a village (brown circle)                        -> a keep with towers / huts on a green
// - the player's armies' destinations (dashed outline, faint fill)           -> a dashed outline and a tinted slab
// - each army's circle, fanned out on a shared tile (layoutArmiesOnTiles)    -> a base in the army's colour, a soldier and a banner, at the same offsets
// - the selection ring                                                       -> a gold ring under the army and a gold marker over it
// - archer / cavalry marks (arrowhead top left, diamond top right)            -> a white bow-stave (back left) / a white horse (back right)
// - the soldier count on the circle                                          -> a screen-space label over the army
// - a castle's capture progress bar                                          -> a bar over the castle
// Plus, as on the 2D map since this change, a light outline on the tile under the pointer.
(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory(require('./grid-wire.js'), require('./game-logic.js'));
    } else {
        root.Map3DScene = factory(root.GridWire, root);
    }
})(typeof window !== 'undefined' ? window : globalThis, function (W, logic) {
    'use strict';

    var P = W.PRIM;

    var FOV_DEGREES = 40;
    var NEAR = 0.1;
    var FAR = 300;
    var SUN = [-0.45, 0.85, 0.35];
    var TILE_SIZE = 0.95;
    var TILE_DEPTH = 0.2;
    var WATER_DROP = 0.06;
    var FRAME_COLOR = '#8b7355';   // the 2D canvas's border
    var HOVER_COLOR = '#f5e6c8';
    var SKIN_COLOR = '#e0c39a';
    var POLE_COLOR = '#5a4630';
    var TRUNK_COLOR = '#5b4126';
    var CANOPY_COLOR = '#2f5a2a';
    var ROCK_COLOR = '#8f8a80';
    var RIM_COLOR = '#000000';
    var MARK_COLOR = '#ffffff';

    // The camera's limits: never under the board's horizon nor straight down (where look-at has
    // no up), and zoom kept between a close look at a few tiles and the whole map with room.
    var MIN_PITCH = 0.35;
    var MAX_PITCH = 1.45;
    var MIN_ZOOM = 0.2;
    var MAX_ZOOM = 1.6;
    var DEFAULT_PITCH = 1.05;
    var DEFAULT_YAW = Math.PI / 2;  // the eye due south (+z) of the target
    var TOP_RESERVE_PX = 60;        // the camera buttons' row, kept clear when the whole map is fitted

    // --- placing things

    function cellCenter(state, x, y) {
        return [x + 0.5 - state.width / 2, y + 0.5 - state.height / 2];
    }

    // World (x, z) to 2D grid coordinates (fractional; floor them for the tile).
    function worldToGrid(state, wx, wz) {
        return { gx: wx + state.width / 2, gy: wz + state.height / 2 };
    }

    function onBoard(state, gx, gy) {
        return gx >= 0 && gy >= 0 && gx < state.width && gy < state.height;
    }

    function srgb(css) { return W.parseCss(css); }
    function lin(css, shade) { return W.rgba(srgb(css), shade); }

    // A tile's colour as the 2D canvas paints it: the background, the ground tint (a real map's
    // land), then the owner's tint, each composited over the last. sRGB, opaque.
    function tileColor(tile, palette, groundTint) {
        var c = srgb(palette.background);
        if (groundTint && tile.ground && groundTint[tile.ground]) c = W.blend(c, srgb(groundTint[tile.ground]));
        var owner = tile.ownerId === 1 ? palette.player1Tint
            : tile.ownerId === 2 ? palette.player2Tint : palette.neutralTint;
        return W.blend(c, srgb(owner));
    }

    function armyColor(palette, playerId) {
        return playerId === 1 ? palette.player1Army : palette.player2Army;
    }

    function cube(pos, scale, color) { return W.shape(P.CUBE, pos, scale, color); }
    function cyl(pos, scale, color) { return W.shape(P.CYLINDER, pos, scale, color); }

    // The board's top surface on a tile: water lies a little lower.
    function tileTop(tile) {
        return tile.ground === 'water' ? -WATER_DROP : 0;
    }

    function castleShapes(cx, cz, tile, palette) {
        var body = lin(palette.castle);
        var trim = lin(palette.castleBorder);
        var out = [cube([cx, 0.15, cz], [0.5, 0.3, 0.5], body)];
        [[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(function (c) {
            out.push(cyl([cx + c[0] * 0.25, 0.225, cz + c[1] * 0.25], [0.14, 0.45, 0.14], trim));
        });
        out.push(cube([cx, 0.27, cz], [0.24, 0.54, 0.24], body));
        if (tile.ownerId === 1 || tile.ownerId === 2) {
            // The holder's colour flies over the keep: the tile tint under the walls is hard to
            // see from a low camera.
            out.push(cyl([cx, 0.66, cz], [0.025, 0.24, 0.025], lin(POLE_COLOR)));
            out.push(cube([cx + 0.08, 0.72, cz], [0.15, 0.1, 0.02], lin(armyColor(palette, tile.ownerId))));
        }
        return out;
    }

    function villageShapes(cx, cz, palette) {
        var walls = lin(palette.village);
        var roof = lin(palette.village, 0.45);
        var out = [cyl([cx, 0.015, cz], [0.5, 0.03, 0.5], lin(palette.villageBorder))];
        [[-0.09, -0.06], [0.1, 0.07]].forEach(function (o) {
            out.push(cube([cx + o[0], 0.1, cz + o[1]], [0.15, 0.14, 0.15], walls));
            out.push(cube([cx + o[0], 0.2, cz + o[1]], [0.18, 0.06, 0.18], roof));
        });
        return out;
    }

    function groundDetail(cx, cz, tile) {
        var out = [];
        if (tile.ground === 'forest') {
            [[-0.32, -0.3], [0.31, -0.27], [-0.3, 0.32]].forEach(function (o) {
                out.push(cyl([cx + o[0], 0.06, cz + o[1]], [0.05, 0.12, 0.05], lin(TRUNK_COLOR)));
                out.push(W.shape(P.SPHERE, [cx + o[0], 0.2, cz + o[1]], [0.2, 0.24, 0.2], lin(CANOPY_COLOR)));
            });
        } else if (tile.ground === 'stone' || tile.ground === 'badland') {
            out.push(W.shape(P.SPHERE, [cx - 0.3, 0.04, cz - 0.3], [0.16, 0.1, 0.13], lin(ROCK_COLOR)));
        }
        return out;
    }

    // A dashed square outline inset into a tile, three dashes a side, as the 2D map's
    // destination marker; `y` is the tile's top.
    function dashedOutline(cx, cz, y, color) {
        var out = [];
        var half = 0.44;
        var dash = 0.2;
        var t = 0.05;
        [-0.29, 0, 0.29].forEach(function (o) {
            out.push(cube([cx + o, y + 0.012, cz - half], [dash, 0.024, t], color));
            out.push(cube([cx + o, y + 0.012, cz + half], [dash, 0.024, t], color));
            out.push(cube([cx - half, y + 0.012, cz + o], [t, 0.024, dash], color));
            out.push(cube([cx + half, y + 0.012, cz + o], [t, 0.024, dash], color));
        });
        return out;
    }

    function solidOutline(cx, cz, y, color) {
        var half = TILE_SIZE / 2 - 0.02;
        var t = 0.04;
        var len = TILE_SIZE - 0.04;
        return [
            cube([cx, y + 0.015, cz - half], [len, 0.03, t], color),
            cube([cx, y + 0.015, cz + half], [len, 0.03, t], color),
            cube([cx - half, y + 0.015, cz], [t, 0.03, len], color),
            cube([cx + half, y + 0.015, cz], [t, 0.03, len], color)
        ];
    }

    // BOARD shapes: the frame, the tiles, castles, villages, the land's detail, the player's
    // destinations, castles' capture bars and the hover outline (options.hover = {x, y} or null).
    function boardShapes(state, palette, groundTint, options) {
        var o = options || {};
        var shapes = [];
        var w = state.width;
        var h = state.height;
        shapes.push(cube([0, -0.22, 0], [w + 0.5, 0.16, h + 0.5], lin(FRAME_COLOR)));
        shapes.push(cube([0, -0.12, 0], [w + 0.1, 0.2, h + 0.1], lin(palette.grid)));
        var colors = {};
        for (var x = 0; x < w; x++) {
            for (var y = 0; y < h; y++) {
                var tile = state.grid[x][y];
                var c = cellCenter(state, x, y);
                var top = tileTop(tile);
                var color = tileColor(tile, palette, groundTint);
                colors[x + ',' + y] = color;
                shapes.push(cube([c[0], top - TILE_DEPTH / 2, c[1]], [TILE_SIZE, TILE_DEPTH, TILE_SIZE], W.rgba(color)));
                if (tile.type === 'CASTLE') shapes = shapes.concat(castleShapes(c[0], c[1], tile, palette));
                else if (tile.type === 'VILLAGE') shapes = shapes.concat(villageShapes(c[0], c[1], palette));
                else shapes = shapes.concat(groundDetail(c[0], c[1], tile));
            }
        }
        var destColor = lin(palette.player1Army);
        var p1 = srgb(palette.player1Army);
        logic.listArmyDestinations(state.armies, 1).forEach(function (d) {
            if (!onBoard(state, d.x, d.y)) return;
            var c = cellCenter(state, d.x, d.y);
            var top = tileTop(state.grid[d.x][d.y]);
            var fill = W.blend(colors[d.x + ',' + d.y], [p1[0], p1[1], p1[2], 0.2]);
            shapes.push(cube([c[0], top + 0.004, c[1]], [0.9, 0.008, 0.9], W.rgba(fill)));
            shapes = shapes.concat(dashedOutline(c[0], c[1], top, destColor));
        });
        logic.listCaptureProgress(state, o.captureTurns || 3).forEach(function (p) {
            var c = cellCenter(state, p.x, p.y);
            var color = p.playerId === 1 ? palette.player1Army
                : p.playerId === 2 ? palette.player2Army : palette.castleBorder;
            shapes.push(cube([c[0], 0.95, c[1]], [0.84, 0.11, 0.06], lin(palette.castleBorder)));
            shapes.push(cube([c[0], 0.95, c[1]], [0.8, 0.08, 0.07], lin('#000000')));
            var fw = 0.8 * p.fraction;
            if (fw > 0) shapes.push(cube([c[0] - 0.4 + fw / 2, 0.95, c[1]], [fw, 0.09, 0.08], lin(color)));
        });
        if (o.hover && onBoard(state, o.hover.x, o.hover.y)) {
            var hc = cellCenter(state, o.hover.x, o.hover.y);
            shapes = shapes.concat(solidOutline(hc[0], hc[1], tileTop(state.grid[o.hover.x][o.hover.y]), lin(HOVER_COLOR)));
        }
        return shapes;
    }

    // --- armies

    // Where each army stands: layoutArmiesOnTiles' fan (game-logic.js, the 2D map's own), in
    // world units, with the figure's size. One entry per army, in layout order.
    function armyPlacements(state) {
        return logic.layoutArmiesOnTiles(state.armies || []).map(function (p) {
            var a = p.army;
            var c = cellCenter(state, a.x, a.y);
            var top = onBoard(state, a.x, a.y) ? tileTop(state.grid[a.x][a.y]) : 0;
            return {
                army: a,
                x: c[0] + p.offsetX,
                z: c[1] + p.offsetY,
                base: top,
                radius: p.radius,
                scale: p.scale,
                height: top + 0.1 + 0.8 * p.scale  // the banner's top
            };
        });
    }

    // One army's shapes, measured from its anchor (x and z offsets; y absolute).
    function armyShapes(pl, palette, selected) {
        var a = pl.army;
        var r = pl.radius;
        var s = pl.scale;
        var y0 = pl.base;
        var color = lin(armyColor(palette, a.playerId));
        var out = [];
        if (selected) {
            var gap = Math.min(0.06, 0.05);
            out.push(cyl([0, y0 + 0.012, 0], [2 * (r + gap), 0.024, 2 * (r + gap)], lin(palette.selectionRing)));
            out.push(W.shape(P.SPHERE, [0, pl.height + 0.14 * s + 0.06, 0], [0.12 * s + 0.04, 0.12 * s + 0.04, 0.12 * s + 0.04],
                lin(palette.selectionRing)));
        }
        out.push(cyl([0, y0 + 0.025, 0], [2 * r + 0.04, 0.05, 2 * r + 0.04], lin(RIM_COLOR)));
        out.push(cyl([0, y0 + 0.05, 0], [2 * r, 0.06, 2 * r], color));
        var feet = y0 + 0.08;
        out.push(W.shape(P.CAPSULE, [0, feet + 0.19 * s, 0], [0.17 * s, 0.38 * s, 0.17 * s], color));
        out.push(W.shape(P.SPHERE, [0, feet + 0.44 * s, 0], [0.13 * s, 0.13 * s, 0.13 * s], lin(SKIN_COLOR)));
        // The banner: a pole beside the soldier with a flag in the army's colour.
        var px = 0.16 * s;
        out.push(cyl([px, feet + 0.36 * s, 0], [0.025, 0.72 * s, 0.025], lin(POLE_COLOR)));
        out.push(cube([px + 0.09 * s, feet + 0.64 * s, 0], [0.18 * s, 0.12 * s, 0.02], color));
        // Unit-type marks, where the 2D map puts them: archers back left, cavalry back right.
        var marks = logic.armyUnitMarks(a);
        var mark = lin(MARK_COLOR);
        var m = r * 0.72;
        if (marks.indexOf('archers') >= 0) {
            out.push(cyl([-m, feet + 0.15 * s, -m], [0.035, 0.3 * s, 0.035], mark));
            out.push(W.shape(P.SPHERE, [-m, feet + 0.31 * s, -m], [0.08 * s, 0.08 * s, 0.08 * s], mark));
        }
        if (marks.indexOf('cavalry') >= 0) {
            out.push(cube([m, feet + 0.1 * s, -m], [0.24 * s, 0.11 * s, 0.1 * s], mark));
            out.push(cube([m + 0.13 * s, feet + 0.18 * s, -m], [0.07 * s, 0.13 * s, 0.07 * s], mark));
        }
        return out;
    }

    // PIECES: one anchor per army, sliding from where it stood (`from`, by army id: [x, z]) to
    // where it stands now; an army with no earlier spot appears in place.
    function pieces(state, palette, options) {
        var o = options || {};
        var from = o.from || {};
        var anchors = [];
        var shapes = [];
        armyPlacements(state).forEach(function (pl, i) {
            var start = from[pl.army.id] || [pl.x, pl.z];
            anchors.push([start[0], start[1], pl.x, pl.z]);
            armyShapes(pl, palette, o.selectedId === pl.army.id).forEach(function (s) {
                s.attach = W.ATTACH.PIECE;
                s.anchor = i;
                shapes.push(s);
            });
        });
        return { tickSeconds: o.tickSeconds || 0, anchors: anchors, shapes: shapes };
    }

    // --- the camera

    function boardCorners(state) {
        var hw = state.width / 2 + 0.25;
        var hh = state.height / 2 + 0.25;
        var out = [];
        [-hw, hw].forEach(function (x) {
            [-hh, hh].forEach(function (z) {
                out.push([x, -0.3, z]);
                out.push([x, 1.0, z]);
            });
        });
        return out;
    }

    // The distance at which the whole board (and what stands on it) fits a width x height
    // canvas from this yaw and pitch, with a margin: found by halving, so a tall phone canvas
    // backs the camera off and a wide one brings it in.
    function fitDistance(state, yaw, pitch, width, height) {
        var corners = boardCorners(state);
        var margin = 0.05;
        // The camera buttons sit along the canvas's top edge (style.css), so the board keeps clear.
        var top = Math.max(height * margin, TOP_RESERVE_PX);
        var fits = function (dist) {
            var view = viewOf({ yaw: yaw, pitch: pitch, dist: dist, target: [0, 0, 0] });
            return corners.every(function (c) {
                var p = W.project(view, c, width, height);
                return p && p.x >= width * margin && p.x <= width * (1 - margin)
                    && p.y >= top && p.y <= height * (1 - margin);
            });
        };
        var lo = 0.5;
        var hi = 400;
        for (var i = 0; i < 40; i++) {
            var mid = (lo + hi) / 2;
            if (fits(mid)) hi = mid; else lo = mid;
        }
        return hi;
    }

    // The camera the map opens with, as an orbit plus the zoom it is at (1 = the whole map).
    function defaultCamera() {
        return { yaw: DEFAULT_YAW, pitch: DEFAULT_PITCH, zoom: 1, target: [0, 0, 0] };
    }

    // A camera kept in bounds: pitch, zoom, and the target over the board.
    function clampCamera(state, cam) {
        var hw = state.width / 2;
        var hh = state.height / 2;
        return {
            yaw: cam.yaw,
            pitch: Math.max(MIN_PITCH, Math.min(MAX_PITCH, cam.pitch)),
            zoom: Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, cam.zoom)),
            target: [Math.max(-hw, Math.min(hw, cam.target[0])), 0, Math.max(-hh, Math.min(hh, cam.target[2]))]
        };
    }

    // The orbit (yaw, pitch, dist, target) a camera means on a canvas of this size.
    function orbitOf(state, cam, width, height) {
        return {
            yaw: cam.yaw,
            pitch: cam.pitch,
            dist: fitDistance(state, cam.yaw, cam.pitch, width, height) * cam.zoom,
            target: cam.target
        };
    }

    // A VIEW record for an orbit: eye, target, projection, the clear colour, the sun.
    function viewOf(orbit, background) {
        return {
            eye: W.orbitEye(orbit),
            target: orbit.target.slice(),
            fovDegrees: FOV_DEGREES,
            near: NEAR,
            far: FAR,
            clear: background ? lin(background) : [0, 0, 0, 1],
            sun: SUN
        };
    }

    // Moves the camera so the ground point under (x0, y0) comes under (x1, y1): a drag that
    // holds on to the map. Unchanged when either point is off the ground (above the horizon).
    function panByDrag(state, cam, view, x0, y0, x1, y1, width, height) {
        var a = W.hitPlaneY(W.rayAt(view, x0, y0, width, height), 0);
        var b = W.hitPlaneY(W.rayAt(view, x1, y1, width, height), 0);
        if (!a || !b) return cam;
        return clampCamera(state, Object.assign({}, cam,
            { target: [cam.target[0] + a[0] - b[0], 0, cam.target[2] + a[2] - b[2]] }));
    }

    // Moves the target along the ground, `right` and `forward` tiles as the camera faces.
    function panBySteps(state, cam, right, forward) {
        var fx = -Math.cos(cam.yaw);
        var fz = -Math.sin(cam.yaw);
        // right = forward x up, on the ground: (-fz, fx)
        var rx = -fz;
        var rz = fx;
        return clampCamera(state, Object.assign({}, cam, {
            target: [cam.target[0] + rx * right + fx * forward, 0, cam.target[2] + rz * right + fz * forward]
        }));
    }

    function rotate(state, cam, dYaw, dPitch) {
        return clampCamera(state, Object.assign({}, cam, { yaw: cam.yaw + dYaw, pitch: cam.pitch + dPitch }));
    }

    function zoomBy(state, cam, factor) {
        return clampCamera(state, Object.assign({}, cam, { zoom: cam.zoom * factor }));
    }

    // --- picking

    function distanceToSegment(px, py, ax, ay, bx, by) {
        var dx = bx - ax;
        var dy = by - ay;
        var len = dx * dx + dy * dy;
        var t = len > 0 ? Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len)) : 0;
        var qx = ax + t * dx - px;
        var qy = ay + t * dy - py;
        return Math.sqrt(qx * qx + qy * qy);
    }

    // What a press at canvas point (x, y) is on: {gridX, gridY, army}. An army's figure (its
    // base up to its banner, as drawn) is hit first, nearest the camera first among those under
    // the point; otherwise the ray is followed to the ground and the tile there is used, with
    // the 2D map's own findArmyAtPoint choosing among the armies on it. `matches` narrows the
    // armies that count, as on the 2D map (selection takes only the player's own). `minPx` is
    // the least radius a figure is hit within (a finger is broader than a mouse).
    function pick(state, view, x, y, width, height, placements, matches, minPx) {
        var best = null;
        (placements || armyPlacements(state)).forEach(function (pl) {
            if (matches && !matches(pl.army)) return;
            var base = W.project(view, [pl.x, pl.base + 0.05, pl.z], width, height);
            var top = W.project(view, [pl.x, pl.height, pl.z], width, height);
            var edge = W.project(view, [pl.x + pl.radius, pl.base + 0.05, pl.z], width, height);
            if (!base || !top || !edge) return;
            var radius = Math.max(minPx || 0, Math.abs(edge.x - base.x), pl.radius * 0.6 * height / base.depth);
            var d = distanceToSegment(x, y, base.x, base.y, top.x, top.y);
            if (d > radius) return;
            if (!best || base.depth < best.depth - 0.05 || (Math.abs(base.depth - best.depth) <= 0.05 && d < best.d)) {
                best = { army: pl.army, depth: base.depth, d: d };
            }
        });
        if (best) return { gridX: best.army.x, gridY: best.army.y, army: best.army };
        var hit = W.hitPlaneY(W.rayAt(view, x, y, width, height), 0);
        if (!hit) return null;
        var g = worldToGrid(state, hit[0], hit[2]);
        var gx = Math.floor(g.gx);
        var gy = Math.floor(g.gy);
        if (!onBoard(state, gx, gy)) return null;
        return { gridX: gx, gridY: gy, army: logic.findArmyAtPoint(state.armies || [], g.gx, g.gy, 1, 1, matches) };
    }

    // --- the HUD: soldier counts over the armies

    // A label (and its backing panel) per army whose banner top is on the canvas, at `at`
    // (by army id: [x, z], for armies part-way through a slide) or where it stands.
    function hud(state, palette, view, width, height, placements, options) {
        var o = options || {};
        var at = o.at || {};
        var panels = [];
        var labels = [];
        (placements || armyPlacements(state)).forEach(function (pl) {
            var pos = at[pl.army.id] || [pl.x, pl.z];
            var p = W.project(view, [pos[0], pl.height + 0.06, pos[1]], width, height);
            if (!p || p.x < 0 || p.y < 0 || p.x > width || p.y > height) return;
            var text = String(pl.army.soldiers);
            var size = pl.scale >= 1 ? 16 : 8 * Math.max(1, Math.round(pl.scale * 2));
            var pad = 3;
            var w = text.length * size + 2 * pad;
            var h = size + 2 * pad;
            var left = Math.round(p.x - w / 2);
            var top = Math.round(p.y - h - 4);
            if (o.selectedId === pl.army.id) {
                panels.push({ x: left - 2, y: top - 2, width: w + 4, height: h + 4, color: lin(palette.selectionRing) });
            }
            panels.push({ x: left, y: top, width: w, height: h, color: lin(armyColor(palette, pl.army.playerId), 0.55) });
            labels.push({ x: left + pad, y: top + pad, size: size, color: lin(palette.textWhite), text: text });
        });
        return { panels: panels, labels: labels };
    }

    return {
        FOV_DEGREES: FOV_DEGREES,
        MIN_PITCH: MIN_PITCH,
        MAX_PITCH: MAX_PITCH,
        MIN_ZOOM: MIN_ZOOM,
        MAX_ZOOM: MAX_ZOOM,
        cellCenter: cellCenter,
        worldToGrid: worldToGrid,
        tileColor: tileColor,
        boardShapes: boardShapes,
        armyPlacements: armyPlacements,
        armyShapes: armyShapes,
        pieces: pieces,
        fitDistance: fitDistance,
        defaultCamera: defaultCamera,
        clampCamera: clampCamera,
        orbitOf: orbitOf,
        viewOf: viewOf,
        panByDrag: panByDrag,
        panBySteps: panBySteps,
        rotate: rotate,
        zoomBy: zoomBy,
        pick: pick,
        hud: hud
    };
});
