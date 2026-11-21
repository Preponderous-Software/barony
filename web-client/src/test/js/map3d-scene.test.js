// Node's built-in test runner. Run with: node --test web-client/src/test/js/
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const W = require('../../main/resources/static/js/grid-wire.js');
const S = require('../../main/resources/static/js/map3d-scene.js');
const logic = require('../../main/resources/static/js/game-logic.js');

// The game page's PALETTE and GROUND_TINT (game.html), uncoloured-blind.
const PALETTE = {
    player1Tint: 'rgba(100, 150, 255, 0.3)', player2Tint: 'rgba(255, 100, 100, 0.3)',
    neutralTint: 'rgba(200, 200, 200, 0.05)', player1Army: '#0066FF', player2Army: '#FF0000',
    selectionRing: '#FFD700', castle: '#888', castleBorder: '#aaa', captureBarTrack: 'rgba(0, 0, 0, 0.6)',
    village: '#a0724a', villageBorder: '#c0926a', grid: '#3a3228', background: '#1a1510', textWhite: '#FFF'
};
const GROUND_TINT = { water: 'rgba(60, 110, 150, 0.35)', forest: 'rgba(50, 100, 45, 0.30)' };

function tile(type, ownerId, extra) {
    return Object.assign({ type, ownerId, occupationTicks: 0, stability: 100, population: 100 }, extra || {});
}

// A 5 x 4 map: player 1's castle at (0, 0), player 2's at (4, 3), a neutral village at (2, 1),
// forest at (1, 2), water at (3, 0).
function game(armies) {
    const width = 5;
    const height = 4;
    const grid = [];
    for (let x = 0; x < width; x++) {
        grid.push([]);
        for (let y = 0; y < height; y++) grid[x].push(tile('EMPTY', 0));
    }
    grid[0][0] = tile('CASTLE', 1);
    grid[4][3] = tile('CASTLE', 2);
    grid[2][1] = tile('VILLAGE', 0);
    grid[1][2] = tile('EMPTY', 0, { ground: 'forest' });
    grid[3][0] = tile('EMPTY', 0, { ground: 'water' });
    return { width, height, grid, armies: armies || [], tickCount: 0 };
}

const army = (id, playerId, x, y, extra) => Object.assign({ id, playerId, x, y, soldiers: 10, morale: 100, loyalty: 100 }, extra || {});
const close = (a, b, eps = 1e-6) => Math.abs(a - b) <= eps;

test('tile (x, y) of the 2D map is centred at world (x + 0.5 - width/2, y + 0.5 - height/2)', () => {
    const g = game();
    assert.deepEqual(S.cellCenter(g, 0, 0), [-2, -1.5]);
    assert.deepEqual(S.cellCenter(g, 4, 3), [2, 1.5]);
    const back = S.worldToGrid(g, 2, 1.5);
    assert.deepEqual([Math.floor(back.gx), Math.floor(back.gy)], [4, 3]);
});

test('a tile is coloured as the 2D canvas composites it: background, ground tint, then owner tint', () => {
    const owned = S.tileColor(tile('EMPTY', 1), PALETTE, GROUND_TINT);
    const expected = W.blend(W.parseCss(PALETTE.background), W.parseCss(PALETTE.player1Tint));
    assert.deepEqual(owned, expected);
    const water = S.tileColor(tile('EMPTY', 0, { ground: 'water' }), PALETTE, GROUND_TINT);
    const waterExpected = W.blend(W.blend(W.parseCss(PALETTE.background), W.parseCss(GROUND_TINT.water)),
        W.parseCss(PALETTE.neutralTint));
    assert.deepEqual(water, waterExpected);
});

test('the board has one tile per cell in its composited colour, plus castles, villages and the land', () => {
    const g = game();
    const shapes = S.boardShapes(g, PALETTE, GROUND_TINT, {});
    const tiles = shapes.filter((s) => s.kind === W.PRIM.CUBE && close(s.scale[0], 0.95) && close(s.scale[2], 0.95));
    assert.equal(tiles.length, 20);
    const castleTile = tiles.find((s) => close(s.position[0], -2) && close(s.position[2], -1.5));
    assert.deepEqual(castleTile.color, W.rgba(S.tileColor(g.grid[0][0], PALETTE, GROUND_TINT)));
    // Castles: grey walls and four towers in the castle colours, and the holder's flag.
    const grey = W.rgba(W.parseCss(PALETTE.castleBorder));
    assert.equal(shapes.filter((s) => s.kind === W.PRIM.CYLINDER && s.color.join() === grey.join()).length, 8);
    const blueFlag = shapes.filter((s) => s.color.join() === W.rgba(W.parseCss(PALETTE.player1Army)).join());
    assert.equal(blueFlag.length, 1, 'player 1\'s castle flies their colour');
    // The village stands on a disc in the village border colour.
    assert.ok(shapes.some((s) => s.kind === W.PRIM.CYLINDER && s.color.join() === W.rgba(W.parseCss(PALETTE.villageBorder)).join()));
    // Forest grows trees; water lies lower than land.
    const [fx, fz] = S.cellCenter(g, 1, 2);
    assert.ok(shapes.filter((s) => s.kind === W.PRIM.SPHERE && Math.abs(s.position[0] - fx) < 0.5 && Math.abs(s.position[2] - fz) < 0.5).length >= 2);
    const [wx, wz] = S.cellCenter(g, 3, 0);
    const waterTile = tiles.find((s) => close(s.position[0], wx) && close(s.position[2], wz));
    assert.ok(waterTile.position[1] + waterTile.scale[1] / 2 < 0, 'the water tile\'s top is below the land');
});

test('the player\'s destinations get a dashed outline in their army colour; the enemy\'s are not shown', () => {
    const g = game([
        army(1, 1, 0, 0, { destinationX: 2, destinationY: 2 }),
        army(2, 2, 4, 3, { destinationX: 1, destinationY: 1 })
    ]);
    const blue = W.rgba(W.parseCss(PALETTE.player1Army)).join();
    const shapes = S.boardShapes(g, PALETTE, GROUND_TINT, {});
    const [dx, dz] = S.cellCenter(g, 2, 2);
    const dashes = shapes.filter((s) => s.color.join() === blue && Math.abs(s.position[0] - dx) < 0.5 && Math.abs(s.position[2] - dz) < 0.5);
    assert.equal(dashes.length, 12, 'three dashes a side');
    const [ex, ez] = S.cellCenter(g, 1, 1);
    assert.equal(shapes.filter((s) => s.color.join() === blue && Math.abs(s.position[0] - ex) < 0.5 && Math.abs(s.position[2] - ez) < 0.5).length, 0);
});

test('a castle being captured has a progress bar filled in the capturer\'s colour', () => {
    const g = game([army(1, 1, 4, 3)]);
    g.grid[4][3].occupationTicks = 2;
    const shapes = S.boardShapes(g, PALETTE, GROUND_TINT, { captureTurns: 3 });
    const blue = W.rgba(W.parseCss(PALETTE.player1Army)).join();
    const fill = shapes.find((s) => s.color.join() === blue && s.position[1] > 0.9);
    assert.ok(fill, 'there is a filled bar over the castle');
    assert.ok(close(fill.scale[0], 0.8 * 2 / 3, 1e-6), 'filled two thirds of the way');
});

test('the hover outline appears on the hovered tile only', () => {
    const g = game();
    const plain = S.boardShapes(g, PALETTE, GROUND_TINT, {});
    const hovered = S.boardShapes(g, PALETTE, GROUND_TINT, { hover: { x: 1, y: 1 } });
    assert.equal(hovered.length - plain.length, 4);
    assert.equal(S.boardShapes(g, PALETTE, GROUND_TINT, { hover: { x: 9, y: 9 } }).length, plain.length,
        'a hover off the map draws nothing');
});

test('armies stand where the 2D map fans them out, one anchor each, sliding from where they were', () => {
    const g = game([army(1, 1, 2, 2), army(2, 1, 2, 2), army(3, 2, 4, 3)]);
    const placements = S.armyPlacements(g);
    const layout = logic.layoutArmiesOnTiles(g.armies);
    placements.forEach((pl, i) => {
        const [cx, cz] = S.cellCenter(g, pl.army.x, pl.army.y);
        assert.ok(close(pl.x, cx + layout[i].offsetX) && close(pl.z, cz + layout[i].offsetY));
        assert.ok(close(pl.radius, layout[i].radius));
    });
    const p = S.pieces(g, PALETTE, { from: { 3: [0, 0] }, tickSeconds: 0.45 });
    assert.equal(p.anchors.length, 3);
    const third = placements.findIndex((pl) => pl.army.id === 3);
    assert.deepEqual(p.anchors[third], [0, 0, placements[third].x, placements[third].z]);
    const first = placements.findIndex((pl) => pl.army.id === 1);
    assert.deepEqual(p.anchors[first].slice(0, 2), p.anchors[first].slice(2), 'an army with no earlier spot does not slide');
    assert.ok(p.shapes.every((s) => s.attach === W.ATTACH.PIECE && s.anchor >= 0 && s.anchor < 3));
});

test('the selected army has a gold ring and marker; archers and cavalry have their marks', () => {
    const g = game([army(1, 1, 2, 2, { archers: 3 }), army(2, 1, 0, 0, { cavalry: 2 })]);
    const pl = S.armyPlacements(g);
    const gold = W.rgba(W.parseCss(PALETTE.selectionRing)).join();
    const white = W.rgba(W.parseCss('#ffffff')).join();
    const sel = S.armyShapes(pl[0], PALETTE, true);
    const plain = S.armyShapes(pl[0], PALETTE, false);
    assert.equal(sel.filter((s) => s.color.join() === gold).length, 2);
    assert.equal(plain.filter((s) => s.color.join() === gold).length, 0);
    const archerMarks = plain.filter((s) => s.color.join() === white);
    assert.ok(archerMarks.length >= 1 && archerMarks.every((s) => s.position[0] < 0), 'archers: back left, as top left in 2D');
    const cavalryMarks = S.armyShapes(pl[1], PALETTE, false).filter((s) => s.color.join() === white);
    assert.ok(cavalryMarks.length >= 1 && cavalryMarks.every((s) => s.position[0] > 0), 'cavalry: back right');
    const infantry = S.armyShapes(S.armyPlacements(game([army(5, 2, 1, 1)]))[0], PALETTE, false);
    assert.equal(infantry.filter((s) => s.color.join() === white).length, 0, 'an all-infantry army carries no marks');
});

for (const [name, w, h] of [['landscape', 800, 600], ['portrait phone', 384, 430], ['tall phone', 360, 700]]) {
    test('the default camera fits the whole board on a ' + name + ' canvas', () => {
        const g = game();
        const cam = S.defaultCamera();
        const view = S.viewOf(S.orbitOf(g, cam, w, h), PALETTE.background);
        for (const [x, y] of [[0, 0], [4, 0], [0, 3], [4, 3]]) {
            for (const [cx, cz] of [[-0.5, -0.5], [0.5, 0.5]]) {
                const [wx, wz] = S.cellCenter(g, x, y);
                const p = W.project(view, [wx + cx, 0, wz + cz], w, h);
                assert.ok(p && p.x >= 0 && p.x <= w && p.y >= 0 && p.y <= h, `corner of (${x}, ${y}) on screen`);
            }
        }
    });
}

test('a taller canvas backs the camera off further than a wide one', () => {
    const g = game();
    assert.ok(S.fitDistance(g, Math.PI / 2, 0.95, 360, 700) > S.fitDistance(g, Math.PI / 2, 0.95, 800, 600));
});

test('the camera stays in bounds: pitch, zoom, and its target over the board', () => {
    const g = game();
    const c = S.clampCamera(g, { yaw: 3, pitch: 5, zoom: 100, target: [50, 9, -50] });
    assert.equal(c.pitch, S.MAX_PITCH);
    assert.equal(c.zoom, S.MAX_ZOOM);
    assert.deepEqual(c.target, [2.5, 0, -2]);
    assert.equal(S.zoomBy(g, S.defaultCamera(), 0.001).zoom, S.MIN_ZOOM);
    assert.equal(S.rotate(g, S.defaultCamera(), 0, -5).pitch, S.MIN_PITCH);
});

test('dragging the map keeps the ground point under the pointer', () => {
    const g = game();
    const w = 800;
    const h = 600;
    const cam = S.defaultCamera();
    const view = S.viewOf(S.orbitOf(g, cam, w, h));
    const grabbed = W.hitPlaneY(W.rayAt(view, 300, 300, w, h), 0);
    const moved = S.panByDrag(g, cam, view, 300, 300, 360, 330, w, h);
    const after = S.viewOf(S.orbitOf(g, moved, w, h));
    const under = W.hitPlaneY(W.rayAt(after, 360, 330, w, h), 0);
    assert.ok(close(under[0], grabbed[0], 1e-4) && close(under[2], grabbed[2], 1e-4));
});

test('keyboard pans move the target across the ground as the camera faces', () => {
    const g = game();
    const cam = S.defaultCamera();  // looking north (-z) from the south
    const forward = S.panBySteps(g, cam, 0, 1);
    assert.ok(close(forward.target[0], 0) && close(forward.target[2], -1));
    const right = S.panBySteps(g, cam, 1, 0);
    assert.ok(close(right.target[0], 1) && close(right.target[2], 0));
});

function screenOf(g, view, w, h, x, y, height) {
    const [wx, wz] = S.cellCenter(g, x, y);
    return W.project(view, [wx, height || 0, wz], w, h);
}

test('a press on a tile picks that tile; off the board picks nothing', () => {
    const g = game();
    const w = 800;
    const h = 600;
    const view = S.viewOf(S.orbitOf(g, S.defaultCamera(), w, h));
    for (const [x, y] of [[0, 0], [3, 2], [4, 3]]) {
        const p = screenOf(g, view, w, h, x, y);
        const hit = S.pick(g, view, p.x, p.y, w, h);
        assert.deepEqual([hit.gridX, hit.gridY, hit.army], [x, y, null]);
    }
    assert.equal(S.pick(g, view, 2, 2, w, h), null, 'the sky above the board');
});

test('a press on an army\'s figure picks that army, even where the ray meets the ground behind it', () => {
    const g = game([army(1, 1, 2, 2), army(2, 2, 2, 1)]);
    const w = 800;
    const h = 600;
    const view = S.viewOf(S.orbitOf(g, S.defaultCamera(), w, h));
    const pl = S.armyPlacements(g).find((p) => p.army.id === 1);
    const banner = W.project(view, [pl.x, pl.height - 0.05, pl.z], w, h);
    const hit = S.pick(g, view, banner.x, banner.y, w, h);
    assert.equal(hit.army.id, 1);
    assert.deepEqual([hit.gridX, hit.gridY], [2, 2]);
    const own = S.pick(g, view, banner.x, banner.y, w, h, null, (a) => a.playerId === 1);
    assert.equal(own.army.id, 1);
    const enemyOnly = S.pick(g, view, banner.x, banner.y, w, h, null, (a) => a.playerId === 2);
    assert.notEqual(enemyOnly && enemyOnly.army && enemyOnly.army.id, 1, 'an army the caller does not want is passed over');
});

test('on a shared tile, the press goes to the nearest fanned-out army, as on the 2D map', () => {
    const g = game([army(1, 1, 2, 2), army(2, 1, 2, 2), army(3, 1, 2, 2)]);
    const w = 800;
    const h = 600;
    const view = S.viewOf(S.orbitOf(g, S.defaultCamera(), w, h));
    S.armyPlacements(g).forEach((pl) => {
        const base = W.project(view, [pl.x, pl.base + 0.05, pl.z], w, h);
        assert.equal(S.pick(g, view, base.x, base.y, w, h).army.id, pl.army.id);
    });
});

test('the HUD labels each army\'s soldier count above it, gold-edged when selected', () => {
    const g = game([army(1, 1, 2, 2, { soldiers: 120 }), army(2, 2, 4, 3, { soldiers: 7 })]);
    const w = 800;
    const h = 600;
    const view = S.viewOf(S.orbitOf(g, S.defaultCamera(), w, h));
    const out = S.hud(g, PALETTE, view, w, h, null, { selectedId: 1 });
    assert.deepEqual(out.labels.map((l) => l.text).sort(), ['120', '7']);
    const label = out.labels.find((l) => l.text === '120');
    assert.equal(label.size, 16);
    const pl = S.armyPlacements(g).find((p) => p.army.id === 1);
    const top = W.project(view, [pl.x, pl.height, pl.z], w, h);
    assert.ok(Math.abs(label.x + (3 * 16) / 2 - top.x) < 2, 'centred over the army');
    assert.ok(label.y < top.y, 'above it');
    assert.equal(out.panels.length, 3, 'a panel per label and a gold edge for the selected army');
    assert.deepEqual(out.panels[0].color, W.rgba(W.parseCss(PALETTE.selectionRing)));
});

test('every message the scene makes is accepted by the wire encoder', () => {
    const g = game([army(1, 1, 2, 2, { archers: 1, cavalry: 1, destinationX: 0, destinationY: 3 })]);
    const board = W.decode(W.encodeBoard(S.boardShapes(g, PALETTE, GROUND_TINT, { hover: { x: 0, y: 0 } })));
    assert.ok(board.shapes.length > 20);
    const pieces = W.decode(W.encodePieces(S.pieces(g, PALETTE, { selectedId: 1 })));
    assert.equal(pieces.anchors.length, 1);
    for (const s of board.shapes.concat(pieces.shapes)) {
        assert.ok(s.kind <= 3 && s.color.every((c) => c >= 0 && c <= 1) && s.scale.every((v) => v > 0));
    }
});
