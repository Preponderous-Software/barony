// Tests for the 3D battlefield's simulation (battle-sim.js), its renderer rows (battle-render.js)
// and the avatar rules (battle-avatar.js). Run with: node --test web-client/src/test/js/
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const js = (f) => path.join(__dirname, '../../main/resources/static/js', f);
const Sim = require(js('battle-sim.js'));
const Render = require(js('battle-render.js'));
const Avatar = require(js('battle-avatar.js'));

function fight(options, input) {
    const b = Sim.create(options);
    let steps = 0;
    while (b.phase !== 'over' && steps < 20000) { Sim.step(b, input ? input(b) : {}); steps++; }
    return b;
}

function wins(sides, seeds) {
    let won = 0;
    for (let seed = 1; seed <= seeds; seed++) if (fight({ sides, seed }).winner === 0) won++;
    return won;
}

// ----------------------------------------------------------------------- the simulation

test('the same seed fights the same battle', () => {
    const opts = { sides: [{ soldiers: 30, playerId: 1 }, { soldiers: 28, playerId: 2 }], seed: 9 };
    const a = fight(opts), b = fight(opts);
    assert.equal(a.time, b.time);
    assert.equal(a.winner, b.winner);
    assert.deepEqual(a.units.map(u => [u.state, u.x.toFixed(4)]), b.units.map(u => [u.state, u.x.toFixed(4)]));
});

test('every battle ends, with a winner or a draw', () => {
    for (let seed = 1; seed <= 6; seed++) {
        const b = fight({ sides: [{ soldiers: 1 + seed * 7 }, { soldiers: 50 - seed * 6 }], seed });
        assert.equal(b.phase, 'over');
        assert.ok([0, 1, 'draw'].includes(b.winner));
    }
});

test('one figure per soldier up to the cap, then both sides share one scale', () => {
    const small = Sim.create({ sides: [{ soldiers: 12 }, { soldiers: 30 }] });
    assert.equal(small.units.filter(u => u.side === 0).length, 12);
    assert.equal(small.units.filter(u => u.side === 1).length, 30);

    const big = Sim.create({ sides: [{ soldiers: 900 }, { soldiers: 450 }] });
    const n0 = big.units.filter(u => u.side === 0).length, n1 = big.units.filter(u => u.side === 1).length;
    assert.equal(n0, Sim.FIGURE_CAP);
    assert.equal(n1, Sim.FIGURE_CAP / 2, 'the smaller army keeps its proportion, not the cap');
    assert.ok(Math.abs(big.sides[0].perFigure - big.sides[1].perFigure) < 1e-9);
});

test('a much larger army wins', () => {
    assert.equal(wins([{ soldiers: 60, playerId: 1 }, { soldiers: 30, playerId: 2 }], 8), 8);
    assert.equal(wins([{ soldiers: 200, playerId: 1 }, { soldiers: 150, playerId: 2 }], 6), 6,
        'an army above the figure cap keeps its advantage in numbers');
});

test('morale counts for player 1, as in Barony\'s turn rules', () => {
    const high = wins([{ soldiers: 30, morale: 160, playerId: 1 }, { soldiers: 30, playerId: 2 }], 8);
    const low = wins([{ soldiers: 30, morale: 30, playerId: 1 }, { soldiers: 30, playerId: 2 }], 8);
    assert.ok(high >= 7, 'high morale should win almost every even fight, won ' + high);
    assert.ok(low <= 1, 'low morale should lose almost every even fight, won ' + low);
    // The enemy's morale field is ignored, as CombatService ignores it.
    const a = fight({ sides: [{ soldiers: 20, playerId: 1 }, { soldiers: 20, morale: 200, playerId: 2 }], seed: 3 });
    const b = fight({ sides: [{ soldiers: 20, playerId: 1 }, { soldiers: 20, morale: 0, playerId: 2 }], seed: 3 });
    assert.equal(a.time, b.time);
});

test('the beaten side breaks and is chased before the battle ends', () => {
    const b = Sim.create({ sides: [{ soldiers: 50, playerId: 1 }, { soldiers: 25, playerId: 2 }], seed: 4 });
    let sawPursuit = false;
    while (b.phase !== 'over') { Sim.step(b, {}); sawPursuit = sawPursuit || b.phase === 'pursuit'; }
    assert.ok(sawPursuit);
    assert.ok(b.routed[1]);
    assert.ok(b.log.some(l => /breaks and runs/.test(l.text)));
    const s = Sim.summary(b);
    assert.ok(s.sides[1].escaped + s.sides[1].routing > 0, 'some of the beaten side got away');
});

test('summary reports soldiers, not figures, for a scaled army', () => {
    const b = Sim.create({ sides: [{ soldiers: 270 }, { soldiers: 90 }] });
    const s = Sim.summary(b);
    assert.equal(s.sides[0].soldiersStanding, 270);
    assert.equal(s.sides[1].soldiersStanding, 90);
});

// ----------------------------------------------------------------------- the player

test('a taken-over soldier moves where the player steers it, relative to the camera', () => {
    const b = Sim.create({ sides: [{ soldiers: 5 }, { soldiers: 5 }], seed: 2 });
    const u = b.units.find(v => v.side === 0);
    assert.ok(Sim.embody(b, u.id));
    const x0 = u.x, z0 = u.z;
    // Heading +x (yaw 0), push forward for one second: it walks along +x.
    for (let i = 0; i < 30; i++) Sim.step(b, { forward: 1, yaw: 0 });
    assert.ok(u.x - x0 > 1.5, 'moved along +x');
    assert.ok(Math.abs(u.z - z0) < 0.6, 'did not drift along z');
    // Strafe right of a +x heading is +z.
    const z1 = u.z;
    for (let i = 0; i < 30; i++) Sim.step(b, { right: 1, yaw: 0 });
    assert.ok(u.z - z1 > 1.5);
});

test('the player strikes the enemy in front, within reach', () => {
    const b = Sim.create({ sides: [{ soldiers: 1 }, { soldiers: 1 }], seed: 5 });
    const me = b.units[0], foe = b.units[1];
    Sim.embody(b, me.id);
    me.x = 0; me.z = 0; foe.x = 1.2; foe.z = 0; foe.cooldown = 99;   // the foe never swings back
    const hp = foe.hp;
    for (let i = 0; i < 60; i++) {
        foe.x = 1.2; foe.z = 0; foe.cooldown = 99;
        Sim.step(b, { attack: true, yaw: 0 });
    }
    assert.ok(foe.hp < hp || foe.state === 'dead', 'the foe was hit');
});

test('a raised shield turns most of a blow from the front', () => {
    const hits = (block) => {
        const b = Sim.create({ sides: [{ soldiers: 1 }, { soldiers: 1 }], seed: 6 });
        const me = b.units[0], foe = b.units[1];
        Sim.embody(b, me.id);
        me.maxHp = me.hp = 1000;
        let lost = 0;
        for (let i = 0; i < 600; i++) {
            me.x = 0; me.z = 0; foe.x = 1.2; foe.z = 0;
            const before = me.hp;
            Sim.step(b, { block, yaw: 0 });
            lost += before - me.hp;
        }
        return lost;
    };
    const open = hits(false), shielded = hits(true);
    assert.ok(open > 0);
    assert.ok(shielded < open * 0.4, `shielded ${shielded} vs open ${open}`);
});

test('the player can let go, and a fallen figure is no longer the player\'s', () => {
    const b = Sim.create({ sides: [{ soldiers: 3 }, { soldiers: 3 }], seed: 7 });
    const u = b.units[0];
    Sim.embody(b, u.id);
    Sim.release(b);
    assert.equal(b.player, null);
    Sim.embody(b, u.id);
    u.hp = 0.1; u.state = 'dead';
    Sim.step(b, {});
    assert.equal(b.player, null);
    assert.equal(Sim.embody(b, u.id), false, 'the dead cannot be taken over');
});

test('an avatar joins a side, stronger than a soldier, and takes the player with it', () => {
    const b = Sim.create({ sides: [{ soldiers: 10 }, { soldiers: 10 }], seed: 8 });
    const a = Sim.spawnAvatar(b, 0, { name: 'Dan' });
    assert.equal(a.kind, 'avatar');
    assert.equal(a.side, 0);
    assert.equal(b.player, a.id);
    assert.ok(a.maxHp > Sim.KINDS.soldier.hp);
    assert.ok(b.log.some(l => l.text === 'Dan takes the field.'));
    // A second avatar replaces the first.
    const a2 = Sim.spawnAvatar(b, 1, null);
    assert.equal(a.state, 'escaped');
    assert.equal(a2.side, 1);
    // Avatars are not counted as the army's soldiers.
    assert.equal(Sim.summary(b).sides[1].figures, 10);
});

// ----------------------------------------------------------------------- the map's rule

test('mapOutcome repeats CombatService.resolveEngagement', () => {
    // Equal armies at normal morale destroy each other in one exchange.
    assert.deepEqual(Sim.mapOutcome([{ soldiers: 30, morale: 100, playerId: 1 }, { soldiers: 30, playerId: 2 }]).remaining, [0, 0]);
    // Player 1's strength is soldiers * morale / 100, rounded half up ((s * m + 50) / 100).
    const m = Sim.mapOutcome([{ soldiers: 25, morale: 150, playerId: 1 }, { soldiers: 30, playerId: 2 }]);
    assert.deepEqual(m.strength, [38, 30]);   // 25 * 150 = 3750 -> (3750 + 50) / 100 = 38
    assert.deepEqual(m.remaining, [0, 0]);
    // The enemy's morale never counts.
    assert.deepEqual(Sim.mapOutcome([{ soldiers: 40, morale: 100, playerId: 1 }, { soldiers: 30, morale: 10, playerId: 2 }]).remaining, [10, 0]);
    assert.equal(Sim.mapOutcome([{ soldiers: 40, playerId: 1 }, { soldiers: 30, playerId: 2 }]).winner, 0);
    assert.equal(Sim.mapOutcome([{ soldiers: 40, morale: 20, playerId: 1 }, { soldiers: 30, playerId: 2 }]).winner, 'both-stand');
});

// ----------------------------------------------------------------------- the renderer

test('rows are whole, finite, and of known primitive kinds', () => {
    const b = Sim.create({ sides: [{ soldiers: 20 }, { soldiers: 20 }], terrain: 'CASTLE', seed: 3 });
    Sim.spawnAvatar(b, 0, Avatar.clean({ helmet: 'plume', weapon: 'hammer' }));
    for (let i = 0; i < 600; i++) Sim.step(b, { attack: true });
    for (const rows of [Render.scenery('CASTLE', 3), Render.scenery('VILLAGE', 3), Render.frame(b, { time: 4 }).prims]) {
        assert.equal(rows.length % Render.STRIDE, 0);
        for (let i = 0; i < rows.length; i += Render.STRIDE) {
            assert.ok([0, 1, 2, 3].includes(rows[i]), 'kind ' + rows[i]);
            for (let k = 1; k < Render.STRIDE; k++) assert.ok(Number.isFinite(rows[i + k]));
        }
    }
});

test('first person hides the player\'s own head', () => {
    const b = Sim.create({ sides: [{ soldiers: 1 }, { soldiers: 1 }], seed: 1 });
    Sim.embody(b, b.units[0].id);
    const third = Render.frame(b, { firstPerson: false }).prims.length;
    const first = Render.frame(b, { firstPerson: true }).prims.length;
    assert.ok(first < third);
});

test('hexToLinear converts sRGB hex to linear light', () => {
    assert.deepEqual(Render.hexToLinear('#ffffff'), [1, 1, 1]);
    assert.deepEqual(Render.hexToLinear('#000000'), [0, 0, 0]);
    assert.ok(Math.abs(Render.hexToLinear('#808080')[0] - 0.2158) < 0.001);
    assert.deepEqual(Render.hexToLinear('not a colour'), [0.5, 0.5, 0.5]);
});

// ----------------------------------------------------------------------- the avatar

test('Avatar.clean keeps a valid avatar and repairs anything else', () => {
    const good = { name: 'Lady Wren', tunic: '#1F4FBF', trim: '#e0a100', helmet: 'greathelm', weapon: 'axe', shield: false };
    assert.deepEqual(Avatar.clean(good), Object.assign({}, good, { tunic: '#1f4fbf' }));
    assert.deepEqual(Avatar.clean(null), Avatar.DEFAULT);
    assert.deepEqual(Avatar.clean('nonsense'), Avatar.DEFAULT);
    const bad = Avatar.clean({ name: '  \u0007  ', tunic: 'red', trim: '#12345', helmet: 'tophat', weapon: 'gun', shield: 'yes' });
    assert.deepEqual(bad, Avatar.DEFAULT);
});

test('Avatar names are single-spaced, printable and capped', () => {
    assert.equal(Avatar.clean({ name: '  Sir   Otto \n of\tthe Fens ' }).name, 'Sir Otto of the Fens');
    assert.equal(Avatar.clean({ name: 'x'.repeat(50) }).name.length, Avatar.NAME_MAX);
});

// ----------------------------------------------------------------------- the result the map keeps

test('fieldResult: the winner keeps who stands, the loser who got away, within what each started with', () => {
    for (let seed = 1; seed <= 8; seed++) {
        const b = fight({ sides: [{ soldiers: 40, playerId: 1 }, { soldiers: 30, playerId: 2 }], seed });
        const r = Sim.fieldResult(b), s = Sim.summary(b);
        assert.ok([0, 1, null].includes(r.winner));
        for (const side of [0, 1]) {
            assert.ok(r.survivors[side] >= 0 && r.survivors[side] <= b.sides[side].soldiers);
        }
        if (r.winner !== null) {
            const loser = 1 - r.winner;
            assert.equal(r.survivors[r.winner], s.sides[r.winner].standing);
            assert.equal(r.survivors[loser], s.sides[loser].routing + s.sides[loser].escaped);
            assert.ok(r.survivors[r.winner] >= 1, 'the backend refuses a winner with nobody left');
        }
    }
});

test('fieldResult reports a scaled army in soldiers, and leaves the avatar out', () => {
    const b = fight({ sides: [{ soldiers: 400, playerId: 1 }, { soldiers: 90, playerId: 2 }], seed: 3 });
    const r = Sim.fieldResult(b);
    assert.equal(r.winner, 0);
    assert.ok(r.survivors[0] > 90 && r.survivors[0] <= 400, 'counted in soldiers, not figures: ' + r.survivors[0]);

    const c = Sim.create({ sides: [{ soldiers: 3 }, { soldiers: 3 }], seed: 2 });
    Sim.spawnAvatar(c, 0, Avatar.clean(null));
    while (c.phase !== 'over') Sim.step(c, {});
    const rc = Sim.fieldResult(c);
    assert.ok(rc.survivors[0] <= 3, 'the avatar is not one of the army\'s soldiers');
});

// ----------------------------------------------------------------------- the avatar cannot hold the field alone (#141)

function killAll(b, side) {
    for (const u of b.units) if (u.side === side && u.kind === 'soldier') { u.hp = 0; u.state = 'dead'; }
}

function runOut(b) {
    let steps = 0;
    while (b.phase !== 'over' && steps < 20000) { Sim.step(b, {}); steps++; }
}

test('an avatar left alone does not hold the field, and no soldier is made from nothing', () => {
    const b = Sim.create({ sides: [{ soldiers: 4, playerId: 1 }, { soldiers: 2, playerId: 2 }], seed: 5 });
    const a = Sim.spawnAvatar(b, 0, Avatar.clean(null));
    killAll(b, 0);
    killAll(b, 1);
    runOut(b);
    assert.ok(Sim.alive(a), 'the avatar still stands');
    assert.equal(b.winner, 'draw');
    assert.deepEqual(Sim.fieldResult(b), { winner: null, survivors: [0, 0], survivorsByType: [[0, 0, 0], [0, 0, 0]] });
});

test('an avatar whose soldiers have all fallen loses to the enemy still standing', () => {
    const b = Sim.create({ sides: [{ soldiers: 4, playerId: 1 }, { soldiers: 2, playerId: 2 }], seed: 5 });
    const a = Sim.spawnAvatar(b, 0, Avatar.clean(null));
    killAll(b, 0);
    Sim.step(b, {});
    assert.ok(Sim.alive(a));
    assert.equal(b.winner, 1);
    runOut(b);
    const r = Sim.fieldResult(b);
    assert.equal(r.winner, 1);
    assert.equal(r.survivors[0], 0, 'side 0 kept no soldiers');
    assert.ok(r.survivors[1] >= 1);
});

test('an avatar fighting beside its soldiers still helps them win', () => {
    let wonWith = 0, wonWithout = 0;
    for (let seed = 1; seed <= 12; seed++) {
        const opts = { sides: [{ soldiers: 6, playerId: 1 }, { soldiers: 6, playerId: 2 }], seed };
        if (fight(opts).winner === 0) wonWithout++;
        const b = Sim.create(opts);
        Sim.spawnAvatar(b, 0, Avatar.clean(null));
        Sim.release(b);   // the avatar fights on its own, like any figure the player is not driving
        runOut(b);
        if (b.winner === 0) {
            wonWith++;
            const r = Sim.fieldResult(b);
            assert.equal(r.winner, 0);
            assert.equal(r.survivors[0], Sim.summary(b).sides[0].standing, 'the winner keeps only real soldiers');
            assert.ok(r.survivors[0] >= 1);
        }
    }
    assert.ok(wonWith > 0, 'a side with its avatar can win');
    assert.ok(wonWith >= wonWithout, 'the avatar helps: ' + wonWith + ' vs ' + wonWithout);
});

test('an avatar joining a beaten side with no soldiers left does not reopen the battle', () => {
    const b = Sim.create({ sides: [{ soldiers: 4 }, { soldiers: 2 }], seed: 5 });
    killAll(b, 0);
    runOut(b);
    assert.equal(b.winner, 1);
    const a = Sim.spawnAvatar(b, 0, Avatar.clean(null));
    assert.equal(b.phase, 'over');
    assert.equal(b.winner, 1);
    Sim.step(b, {});
    assert.equal(b.winner, 1);
    assert.equal(Sim.fieldResult(b).winner, 1);
    assert.ok(Sim.alive(a));
});

test('an avatar joining before the lines meet fights alongside its soldiers as usual', () => {
    const b = Sim.create({ sides: [{ soldiers: 5 }, { soldiers: 5 }], seed: 4 });
    Sim.spawnAvatar(b, 0, Avatar.clean(null));
    assert.equal(b.phase, 'hold');
    Sim.step(b, {});
    assert.equal(b.winner, null, 'the battle goes on');
    runOut(b);
    assert.ok([0, 1, 'draw'].includes(b.winner));
});

test('a winner whose last soldier falls during the pursuit holds nothing: the result is a draw', () => {
    const b = Sim.create({ sides: [{ soldiers: 4 }, { soldiers: 2 }], seed: 5 });
    const a = Sim.spawnAvatar(b, 0, Avatar.clean(null));
    killAll(b, 0);
    const runner = b.units.find(u => u.side === 0 && u.kind === 'soldier');
    runner.hp = runner.maxHp; runner.state = 'routing';   // one runner keeps the pursuit going
    Sim.step(b, {});
    assert.equal(b.winner, 1);
    assert.equal(b.phase, 'pursuit');
    killAll(b, 1);   // as if the avatar cut them down while the pursuit ran
    Sim.step(b, {});
    assert.ok(Sim.alive(a));
    assert.equal(b.winner, 'draw');
    assert.equal(b.phase, 'over');
    assert.deepEqual(Sim.fieldResult(b), { winner: null, survivors: [0, 0], survivorsByType: [[0, 0, 0], [0, 0, 0]] });
});

// ----------------------------------------------------------------------- unit types

const MIXED = [{ soldiers: 40, archers: 10, cavalry: 8, morale: 100, playerId: 1 }, { soldiers: 40, archers: 6, cavalry: 12, playerId: 2 }];

test('infantry alone: every figure is infantry and the result is all infantry', () => {
    const b = fight({ sides: [{ soldiers: 40, playerId: 1 }, { soldiers: 35, playerId: 2 }], seed: 42 });
    assert.ok(b.units.filter(u => u.kind === 'soldier').every(u => u.type === 'infantry'));
    const r = Sim.fieldResult(b);
    assert.deepEqual(r.survivorsByType.map(t => t.slice(1)), [[0, 0], [0, 0]]);
    assert.deepEqual(r.survivorsByType.map(t => t[0]), r.survivors);
});

test('a side never has more archers and cavalry than soldiers', () => {
    const b = Sim.create({ sides: [{ soldiers: 10, archers: 8, cavalry: 7 }, { soldiers: 5, archers: -3, cavalry: 'x' }], seed: 1 });
    assert.deepEqual([b.sides[0].infantry, b.sides[0].archers, b.sides[0].cavalry], [0, 3, 7]);
    assert.deepEqual([b.sides[1].infantry, b.sides[1].archers, b.sides[1].cavalry], [5, 0, 0]);
});

test('figures divide between types in proportion, with at least one for every type a side has', () => {
    const side = { soldiers: 1000, infantry: 990, archers: 6, cavalry: 4 };
    const t = Sim.typeFigures(side, 90);
    assert.equal(t.infantry.figures + t.archer.figures + t.cavalry.figures, 90);
    assert.ok(t.archer.figures >= 1 && t.cavalry.figures >= 1);
    assert.equal(t.archer.figures * t.archer.perFigure, 6);
    const even = Sim.typeFigures({ soldiers: 60, infantry: 30, archers: 20, cavalry: 10 }, 60);
    assert.deepEqual([even.infantry.figures, even.archer.figures, even.cavalry.figures], [30, 20, 10]);
});

test('infantry form the front, archers stand behind them and cavalry on the wings', () => {
    const b = Sim.create({ sides: MIXED, seed: 3 });
    const mine = b.units.filter(u => u.side === 0);
    const front = (type) => mine.filter(u => u.type === type).map(u => u.z);
    assert.ok(Math.min(...front('infantry')) > Math.max(...front('archer')), 'archers are behind the infantry (side 0 faces +z)');
    const infX = Math.max(...mine.filter(u => u.type === 'infantry').map(u => Math.abs(u.x)));
    assert.ok(mine.filter(u => u.type === 'cavalry').every(u => Math.abs(u.x) > infX), 'cavalry are outside the infantry\'s files');
});

test('archers shoot arrows that land and wound before the lines meet', () => {
    const b = Sim.create({ sides: [{ soldiers: 30, archers: 30, playerId: 1 }, { soldiers: 30, playerId: 2 }], seed: 5 });
    let shots = 0, arrowHits = 0, firstMeleeAt = null, firstArrowHitAt = null;
    for (let i = 0; i < 30 * 60 && b.phase !== 'over'; i++) {
        Sim.step(b, {});
        for (const e of b.events) {
            if (e.type === 'shot') shots++;
            if (e.type === 'hit' && e.ranged) { arrowHits++; if (firstArrowHitAt == null) firstArrowHitAt = b.time; }
            if (e.type === 'hit' && !e.ranged && firstMeleeAt == null) firstMeleeAt = b.time;
        }
    }
    assert.ok(shots > 30 && arrowHits > 10, shots + ' shots, ' + arrowHits + ' hits');
    assert.ok(firstArrowHitAt < (firstMeleeAt == null ? Infinity : firstMeleeAt), 'arrows strike first');
});

test('cavalry charge: the first blow at the gallop lands twice as hard', () => {
    const b = fight({ sides: [{ soldiers: 30, cavalry: 30, playerId: 1 }, { soldiers: 30, playerId: 2 }], seed: 9 }, null);
    assert.ok(b.winner === 0);
    let charges = 0;
    const c = Sim.create({ sides: [{ soldiers: 30, cavalry: 30, playerId: 1 }, { soldiers: 30, playerId: 2 }], seed: 9 });
    for (let i = 0; i < 30 * 60 && c.phase !== 'over'; i++) { Sim.step(c, {}); charges += c.events.filter(e => e.type === 'hit' && e.charge).length; }
    assert.ok(charges > 5, charges + ' charges');
});

test('a mixed army is worth more than the same number of infantry, and cavalry ride down archers', () => {
    assert.ok(wins([{ soldiers: 40, cavalry: 15, playerId: 1 }, { soldiers: 40, playerId: 2 }], 12) >= 7);
    assert.ok(wins([{ soldiers: 40, archers: 15, playerId: 1 }, { soldiers: 40, playerId: 2 }], 12) >= 6);
    assert.ok(wins([{ soldiers: 40, cavalry: 40, playerId: 1 }, { soldiers: 40, archers: 40, playerId: 2 }], 12) >= 7);
});

test('fieldResult by type: each within what the side brought, summing to its survivors', () => {
    for (let seed = 1; seed <= 8; seed++) {
        const b = fight({ sides: MIXED, seed });
        const r = Sim.fieldResult(b);
        for (const side of [0, 1]) {
            const brought = [b.sides[side].infantry, b.sides[side].archers, b.sides[side].cavalry];
            r.survivorsByType[side].forEach((n, t) => assert.ok(n >= 0 && n <= brought[t], `side ${side} type ${t}: ${n} of ${brought[t]}`));
            assert.equal(r.survivorsByType[side].reduce((a, c) => a + c, 0), r.survivors[side]);
        }
    }
});

test('fieldResult counts a scaled mixed army in soldiers, type by type', () => {
    const b = fight({ sides: [{ soldiers: 600, archers: 150, cavalry: 90, playerId: 1 }, { soldiers: 100, playerId: 2 }], seed: 4 });
    assert.equal(b.winner, 0);
    const r = Sim.fieldResult(b);
    assert.ok(r.survivorsByType[0][1] > 30 && r.survivorsByType[0][2] > 20, JSON.stringify(r.survivorsByType[0]));
});

test('mapOutcome weighs archers 1.25 and cavalry 1.5, as CombatService does', () => {
    // 28 infantry + 8 archers + 4 cavalry = 2800 + 1000 + 600 = 4400 hundredths.
    const army = { soldiers: 40, archers: 8, cavalry: 4 };
    assert.equal(Sim.mapOutcome([Object.assign({ morale: 100, playerId: 1 }, army), { soldiers: 1, playerId: 2 }]).strength[0], 44);
    assert.equal(Sim.mapOutcome([Object.assign({ morale: 150, playerId: 1 }, army), { soldiers: 1, playerId: 2 }]).strength[0], 66);
    assert.equal(Sim.mapOutcome([{ soldiers: 1, playerId: 1 }, Object.assign({ playerId: 2 }, army)]).strength[1], 44);
    // 3 archers: 375 hundredths, rounded half up to 4.
    assert.equal(Sim.mapOutcome([{ soldiers: 1, playerId: 1 }, { soldiers: 3, archers: 3, playerId: 2 }]).strength[1], 4);
});

test('a player who takes over an archer shoots what they aim at', () => {
    const b = Sim.create({ sides: [{ soldiers: 10, archers: 10, playerId: 1 }, { soldiers: 10, playerId: 2 }], seed: 2 });
    const me = b.units.find(u => u.side === 0);
    assert.ok(Sim.embody(b, me.id));
    const enemy = b.units.find(u => u.side === 1);
    enemy.x = me.x; enemy.z = me.z + 15;          // well inside bow range
    const yaw = Math.atan2(enemy.z - me.z, enemy.x - me.x);
    me.facing = yaw;
    Sim.step(b, { yaw, attack: true });
    assert.equal(b.arrows.length, 1);
    assert.equal(b.arrows[0].by, me.id);
});

test('rows for archers, cavalry and arrows are whole and finite', () => {
    const b = Sim.create({ sides: MIXED, seed: 6 });
    for (let i = 0; i < 30 * 12; i++) Sim.step(b, {});
    assert.ok(b.arrows.length > 0, 'arrows in the air');
    const rows = Render.frame(b, {}).prims;
    assert.equal(rows.length % Render.STRIDE, 0);
    assert.ok(rows.every(Number.isFinite));
    const plain = Sim.create({ sides: [{ soldiers: 40 }, { soldiers: 40 }], seed: 6 });
    assert.ok(Render.frame(b, {}).prims.length > Render.frame(plain, {}).prims.length, 'horses and bows add rows');
});
