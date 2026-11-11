/* battle-sim.js — a field battle between two Barony armies, one figure per soldier.
 *
 * No DOM and no engine: the /battle page renders it with Strata, and
 * web-client/src/test/js/battle-sim.test.js runs it under node. The same seed
 * and the same inputs give the same battle, step for step.
 *
 *   const b = BattleSim.create({sides: [{soldiers, archers, cavalry, morale, playerId}, {...}], terrain, seed});
 *   BattleSim.step(b, input);            // advances BattleSim.DT seconds
 *   BattleSim.embody(b, unitId);         // the player takes over a living figure
 *   BattleSim.spawnAvatar(b, side, avatar);  // or enters as their own avatar
 *   BattleSim.release(b);                // and lets go again
 *   BattleSim.summary(b);                // counts, phase, winner
 *   BattleSim.mapOutcome(sides);         // what Barony's turn rules do with the same armies
 *
 * The 3D battle is a way to watch (or fight in) an engagement. It does not decide one: on the
 * map, Barony's CombatService still resolves armies that share a tile in a single exchange, and
 * mapOutcome() repeats that rule so the page can show both side by side.
 *
 * An army's soldiers are infantry, archers and cavalry (Barony's Army keeps the total and how many
 * of it are archers and cavalry; the rest are infantry). Infantry hold the front with spear and
 * shield, archers stand behind them and shoot, cavalry ride on the wings and charge. A side of
 * infantry alone fights exactly as it did before there were other types.
 *
 * Coordinates: metres on the ground plane, x across the field and z along it. Side 0 forms up at
 * negative z facing +z, side 1 at positive z facing -z. Facing is an angle in radians where the
 * unit vector is (cos f, sin f) in (x, z).
 */
(function (root) {
    'use strict';

    var DT = 1 / 30;                 // seconds per step
    var FIGURE_CAP = 90;             // figures per side; above it each figure stands for several soldiers
    var FILE_SPACING = 1.25;         // metres between figures in a rank
    var RANK_SPACING = 1.5;          // metres between ranks
    var FILE_WIDTH = 18;             // figures per rank
    var START_GAP = 34;              // metres between the two front ranks at the start
    var HOLD_SECONDS = 2.5;          // both sides stand before the advance
    var TIME_LIMIT = 240;            // seconds; then the battle is a draw
    var REACH = 1.45;                // melee reach, metres (centre to centre)
    var BODY_RADIUS = 0.36;          // figures push apart closer than twice this
    var WALK = 1.5, CHARGE = 3.3, FLEE = 3.9;   // m/s
    var CHARGE_RANGE = 9;            // start running at the nearest enemy inside this
    var RETARGET_SECONDS = 0.5;
    var SWING_SECONDS = 0.45;        // the visible part of an attack
    var PURSUIT_SECONDS = 15;        // the winners chase the beaten side this long at most
    var FIELD_HALF_LENGTH = 40;      // a routed figure that gets this far from the centre has escaped

    var ARROW_SPEED = 38;            // m/s along the ground
    var ARCHER_KEEP_CLEAR = 5;       // an archer with an enemy this close stops shooting and fights

    // A figure's numbers: an avatar by its kind, a soldier by its type.
    var KINDS = {
        soldier: { hp: 10, damage: [2, 5], cooldown: 1.1, speed: 1.0 },
        avatar:  { hp: 32, damage: [4, 8], cooldown: 0.8, speed: 1.12 }
    };
    // Tuned so a mixed army is worth about what the map's rule says it is (archers 1.25, cavalry
    // 1.5 infantry): 40 with 15 archers beats 40 infantry about 70% of the time, 40 with 15
    // cavalry about 75%, and cavalry ride down archers more often than not.
    var TYPES = ['infantry', 'archer', 'cavalry'];
    var TYPE_STATS = {
        infantry: KINDS.soldier,
        // Light armour and a short blade; their bow is the point of them.
        archer:  { hp: 8, damage: [1, 3], cooldown: 1.2, speed: 1.0, range: 30, shot: [3, 6], reload: 2.2, aim: 0.72 },
        // Horse and rider: tough, fast, and the first blow of a charge lands twice as hard.
        cavalry: { hp: 16, damage: [3, 6.5], cooldown: 1.0, speed: 1.9, reach: 1.75, radius: 0.55 }
    };

    // ---------------------------------------------------------------- helpers

    // mulberry32: a small seeded generator, so battles repeat for a seed.
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

    function toInt(v, fallback) {
        var n = Number(v);
        return Number.isFinite(n) ? Math.round(n) : fallback;
    }

    function angleTo(fromX, fromZ, toX, toZ) { return Math.atan2(toZ - fromZ, toX - fromX); }

    function wrapAngle(a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    // Turn `from` towards `to` by at most `max` radians.
    function turnTowards(from, to, max) {
        var d = wrapAngle(to - from);
        return from + clamp(d, -max, max);
    }

    // ---------------------------------------------------------------- setup

    function cleanSide(s, index) {
        s = s || {};
        var soldiers = clamp(toInt(s.soldiers, 20), 1, 5000);
        var cavalry = clamp(toInt(s.cavalry, 0), 0, soldiers);
        var archers = clamp(toInt(s.archers, 0), 0, soldiers - cavalry);
        return {
            soldiers: soldiers,
            infantry: soldiers - archers - cavalry,
            archers: archers,
            cavalry: cavalry,
            // Barony keeps morale in 0..200 with 100 as normal (Army.setMorale).
            morale: clamp(toInt(s.morale, 100), 0, 200),
            playerId: toInt(s.playerId, index + 1),
            label: typeof s.label === 'string' ? s.label.slice(0, 40) : (index === 0 ? 'Your army' : 'Enemy army'),
            // Barony's turn rules apply morale to player 1's armies only (CombatService).
            moraleCounts: s.moraleCounts != null ? !!s.moraleCounts : toInt(s.playerId, index + 1) === 1
        };
    }

    // How many figures stand for a side's soldiers, and how many soldiers each one is. Both sides
    // share one scale (set by the larger army), so a capped army keeps its advantage in numbers.
    function figuresFor(soldiers, largest) {
        var scale = Math.max(1, (largest || soldiers) / FIGURE_CAP);
        var figures = Math.max(1, Math.round(soldiers / scale));
        return { figures: figures, perFigure: soldiers / figures };
    }

    // How a side's figures divide between its types, in proportion to its soldiers (largest
    // remainder, ties to infantry, then archers, then cavalry), with at least one figure for every
    // type the side has whenever there are figures enough. Each type's figures then stand for that
    // type's soldiers alone, so what survives converts back to soldiers type by type.
    function typeFigures(side, figures) {
        var counts = [side.infantry, side.archers, side.cavalry], out = [0, 0, 0], rem = [], used = 0;
        for (var t = 0; t < 3; t++) {
            var exact = figures * counts[t] / side.soldiers;
            out[t] = Math.floor(exact);
            used += out[t];
            rem.push([exact - out[t], t]);
        }
        rem.sort(function (p, q) { return q[0] - p[0] || p[1] - q[1]; });
        for (var i = 0; used < figures; i++, used++) out[rem[i % 3][1]]++;
        for (t = 0; t < 3; t++) {
            if (counts[t] > 0 && out[t] === 0) {
                var big = out.indexOf(Math.max.apply(null, out));
                if (out[big] > 1) { out[big]--; out[t]++; }
            }
        }
        var types = {};
        for (t = 0; t < 3; t++) {
            types[TYPES[t]] = { soldiers: counts[t], figures: out[t], perFigure: out[t] ? counts[t] / out[t] : 0 };
        }
        return types;
    }

    function stats(u) { return u.kind === 'avatar' ? KINDS.avatar : TYPE_STATS[u.type]; }
    function reachOf(u) { return stats(u).reach || REACH; }
    function radiusOf(u) { return stats(u).radius || BODY_RADIUS; }

    function makeUnit(b, side, kind, x, z, type) {
        var k = kind === 'avatar' ? KINDS.avatar : TYPE_STATS[type || 'infantry'];
        var u = {
            id: b.nextId++,
            side: side,
            kind: kind,
            type: kind === 'avatar' ? null : (type || 'infantry'),
            x: x, z: z,
            facing: side === 0 ? Math.PI / 2 : -Math.PI / 2,
            hp: k.hp, maxHp: k.hp,
            state: 'ready',          // ready | fighting | routing | dead | escaped
            target: null,
            retarget: b.random() * RETARGET_SECONDS,
            cooldown: 0.3 + b.random() * k.cooldown,
            swing: 0,                // seconds left of the current swing (for the renderer)
            hurt: 0,                 // seconds left of the hit flash
            stride: b.random() * 6.28, // walk-cycle phase
            moving: 0,               // 0..1, how fast it moved this step, for the walk cycle
            blocking: false,
            diedAt: null,
            fallFacing: 0,
            reload: 0                // an archer's seconds until it can shoot again
        };
        b.units.push(u);
        return u;
    }

    function create(options) {
        options = options || {};
        var sides = [cleanSide(options.sides && options.sides[0], 0), cleanSide(options.sides && options.sides[1], 1)];
        var terrain = options.terrain === 'VILLAGE' || options.terrain === 'CASTLE' ? options.terrain : 'EMPTY';
        var seed = toInt(options.seed, 1) >>> 0;
        var b = {
            seed: seed,
            random: rng(seed),
            terrain: terrain,
            sides: sides,
            units: [],
            nextId: 1,
            time: 0,
            phase: 'hold',            // hold | advance | melee | pursuit | over
            winner: null,             // 0, 1, or 'draw'
            routed: [false, false],
            player: null,             // the unit id the player controls, or null
            events: [],               // this step's hits and deaths, for sparks and sound
            arrows: [],               // arrows in the air: {x0, z0, x1, z1, t0, t1, side, on, hit, by}
            log: []                   // the battle's notable moments, for the page's ticker
        };
        var largest = Math.max(sides[0].soldiers, sides[1].soldiers);
        for (var side = 0; side < 2; side++) {
            var f = figuresFor(sides[side].soldiers, largest);
            sides[side].figures = f.figures;
            sides[side].perFigure = f.perFigure;
            sides[side].types = typeFigures(sides[side], f.figures);
            sides[side].mixed = sides[side].archers + sides[side].cavalry > 0;
            formUp(b, side);
        }
        return b;
    }

    // Infantry in ranks at the front, archers in ranks behind them, cavalry in columns on both
    // wings level with the front rank. A side of infantry alone forms up exactly as it always has.
    function formUp(b, side) {
        var types = b.sides[side].types;
        var dir = side === 0 ? -1 : 1;            // which way is "back" for this side
        var depth = block(b, side, 'infantry', types.infantry.figures, 0, dir);
        var archerRow = depth > 0 ? depth + 2.5 : 0;
        block(b, side, 'archer', types.archer.figures, archerRow, dir);
        var n = types.cavalry.figures;
        if (n > 0) {
            var frontWidth = Math.min(FILE_WIDTH, Math.max(types.infantry.figures, types.archer.figures, 1)) * FILE_SPACING;
            var cols = 3, wingX = frontWidth / 2 + 4;
            for (var i = 0; i < n; i++) {
                var wing = i % 2 === 0 ? 1 : -1, j = Math.floor(i / 2);
                var x = wing * (wingX + (j % cols) * 1.9) + (b.random() - 0.5) * 0.25;
                var z = dir * (START_GAP / 2 + Math.floor(j / cols) * 2.6) + (b.random() - 0.5) * 0.25;
                makeUnit(b, side, 'soldier', x, z, 'cavalry');
            }
        }
    }

    // Ranks of one type, starting `back` metres behind the front line; returns how deep they go.
    function block(b, side, type, figures, back, dir) {
        if (figures <= 0) return 0;
        var ranks = Math.ceil(figures / FILE_WIDTH);
        for (var i = 0; i < figures; i++) {
            var rank = Math.floor(i / FILE_WIDTH);
            var inRank = rank === ranks - 1 ? figures - rank * FILE_WIDTH : FILE_WIDTH;
            var file = i % FILE_WIDTH;
            var x = (file - (inRank - 1) / 2) * FILE_SPACING + (b.random() - 0.5) * 0.25;
            var z = dir * (START_GAP / 2 + back + rank * RANK_SPACING) + (b.random() - 0.5) * 0.25;
            makeUnit(b, side, 'soldier', x, z, type);
        }
        return ranks * RANK_SPACING;
    }

    // ---------------------------------------------------------------- queries

    function unit(b, id) {
        for (var i = 0; i < b.units.length; i++) if (b.units[i].id === id) return b.units[i];
        return null;
    }

    function alive(u) { return u.state !== 'dead' && u.state !== 'escaped'; }
    function fighting(u) { return u.state === 'ready' || u.state === 'fighting'; }

    function nearestEnemy(b, u, maxDist) {
        var best = null, bestD = maxDist != null ? maxDist * maxDist : Infinity;
        for (var i = 0; i < b.units.length; i++) {
            var o = b.units[i];
            if (o.side === u.side || !alive(o)) continue;
            var dx = o.x - u.x, dz = o.z - u.z, d = dx * dx + dz * dz;
            // A fleeing enemy is a worse target than one still fighting.
            if (o.state === 'routing') d *= 2.5;
            if (d < bestD) { bestD = d; best = o; }
        }
        return best;
    }

    function summary(b) {
        var out = { time: b.time, phase: b.phase, winner: b.winner, routed: b.routed.slice(), sides: [] };
        for (var s = 0; s < 2; s++) {
            var c = { figures: b.sides[s].figures, perFigure: b.sides[s].perFigure, standing: 0, routing: 0, dead: 0, escaped: 0, types: {} };
            for (var t = 0; t < 3; t++) {
                var tf = b.sides[s].types[TYPES[t]];
                c.types[TYPES[t]] = { figures: tf.figures, perFigure: tf.perFigure, standing: 0, routing: 0, dead: 0, escaped: 0 };
            }
            for (var i = 0; i < b.units.length; i++) {
                var u = b.units[i];
                if (u.side !== s || u.kind === 'avatar') continue;
                var key = fighting(u) ? 'standing' : u.state === 'routing' ? 'routing' : u.state === 'dead' ? 'dead' : 'escaped';
                c[key]++;
                c.types[u.type][key]++;
            }
            // In soldiers, so a capped army still reads in Barony's own numbers; each type's
            // figures count at that type's own scale.
            var st = 0, lo = 0, fl = 0;
            for (t = 0; t < 3; t++) {
                var ty = c.types[TYPES[t]];
                st += ty.standing * ty.perFigure;
                lo += ty.dead * ty.perFigure;
                fl += (ty.routing + ty.escaped) * ty.perFigure;
            }
            c.soldiersStanding = Math.round(st);
            c.soldiersLost = Math.round(lo);
            c.soldiersFled = Math.round(fl);
            out.sides.push(c);
        }
        return out;
    }

    // ---------------------------------------------------------------- morale

    // The share of a side's figures it can lose before it breaks. Steadier armies hold longer:
    // about 45% at Barony's normal morale of 100, 25% at 0, 70% at 200.
    function breakingPoint(side) {
        var morale = side.moraleCounts ? side.morale : 100;
        return clamp(0.25 + 0.2 * (morale / 100), 0.25, 0.7);
    }

    // How likely a swing is to land, by the side's morale (only where Barony's rules count it).
    function hitChance(side) {
        var morale = side.moraleCounts ? side.morale : 100;
        return clamp(0.55 * Math.sqrt(Math.max(morale, 5) / 100), 0.2, 0.85);
    }

    // ---------------------------------------------------------------- combat

    // How much harder one type hits another: spears against horses, horses against archers.
    function matchup(attacker, victim) {
        if (attacker.type === 'infantry' && victim.type === 'cavalry') return 1.3;
        if (attacker.type === 'cavalry' && victim.type === 'archer') return 1.5;
        return 1;
    }

    // opts: {ranged: an arrow rather than a blow, charge: the first blow of a cavalry charge}
    function damage(b, attacker, victim, opts) {
        var k = stats(attacker), range = opts && opts.ranged ? k.shot : k.damage;
        var dmg = range[0] + b.random() * (range[1] - range[0]);
        if (!(opts && opts.ranged)) dmg *= matchup(attacker, victim);
        if (opts && opts.charge) dmg *= 2;
        if (victim.blocking) {
            // A raised shield turns most of a blow from the front.
            var from = angleTo(victim.x, victim.z, attacker.x, attacker.z);
            if (Math.abs(wrapAngle(from - victim.facing)) < 1.2) dmg *= 0.25;
        }
        if (victim.state === 'routing') dmg *= 1.5;     // a back turned to the enemy
        victim.hp -= dmg;
        victim.hurt = 0.25;
        b.events.push({ type: 'hit', x: victim.x, z: victim.z, side: victim.side, by: attacker.id, on: victim.id,
                        blocked: !!victim.blocking, ranged: !!(opts && opts.ranged), charge: !!(opts && opts.charge) });
        if (victim.hp <= 0) kill(b, victim, attacker);
    }

    function kill(b, victim, by) {
        victim.hp = 0;
        victim.state = 'dead';
        victim.diedAt = b.time;
        victim.fallFacing = by ? angleTo(by.x, by.z, victim.x, victim.z) : victim.facing;
        victim.swing = 0;
        b.events.push({ type: 'death', x: victim.x, z: victim.z, side: victim.side, id: victim.id });
        if (victim.id === b.player) b.log.push({ t: b.time, text: victim.kind === 'avatar' ? 'You have fallen.' : 'Your soldier has fallen.' });
    }

    function trySwing(b, u, victim) {
        if (u.cooldown > 0 || u.blocking) return false;
        var k = stats(u);
        u.cooldown = k.cooldown * (0.85 + b.random() * 0.3);
        u.swing = SWING_SECONDS;
        // A horse still at the gallop when it reaches the enemy: the charge.
        var charge = u.type === 'cavalry' && u.moving > 1.2;
        if (victim && b.random() < (u.id === b.player ? Math.max(hitChance(b.sides[u.side]), 0.75) : hitChance(b.sides[u.side]))) {
            damage(b, u, victim, charge ? { charge: true } : null);
        } else if (victim) {
            b.events.push({ type: 'miss', x: victim.x, z: victim.z, by: u.id, on: victim.id });
        }
        return true;
    }

    // An archer looses an arrow at a victim. Whether it hits is settled now, by the same chance as
    // a blow, scaled by the archer's aim; the damage lands when the arrow does, if the victim is
    // still alive (a dead archer's arrows still land). It is aimed where the victim stands now.
    function shoot(b, u, victim) {
        var k = stats(u);
        if (u.reload > 0 || u.blocking || !victim) return false;
        u.reload = k.reload * (0.85 + b.random() * 0.3);
        u.cooldown = Math.max(u.cooldown, 0.4);
        u.swing = SWING_SECONDS;
        var chance = hitChance(b.sides[u.side]) * k.aim * (u.id === b.player ? 1.6 : 1);
        var hit = b.random() < chance;
        var dx = victim.x - u.x, dz = victim.z - u.z, d = Math.sqrt(dx * dx + dz * dz);
        var miss = hit ? 0 : 1.5 + b.random() * 2.5, ma = b.random() * Math.PI * 2;
        b.arrows.push({ x0: u.x, z0: u.z, x1: victim.x + Math.cos(ma) * miss, z1: victim.z + Math.sin(ma) * miss,
                        t0: b.time, t1: b.time + Math.max(0.25, d / ARROW_SPEED), side: u.side, by: u.id, on: victim.id, hit: hit });
        b.events.push({ type: 'shot', x: u.x, z: u.z, by: u.id, on: victim.id });
        return true;
    }

    function landArrows(b) {
        var still = [];
        for (var i = 0; i < b.arrows.length; i++) {
            var a = b.arrows[i];
            if (b.time < a.t1) { still.push(a); continue; }
            var victim = a.hit ? unit(b, a.on) : null;
            if (victim && alive(victim)) {
                var by = unit(b, a.by) || { id: a.by, x: a.x0, z: a.z0, type: 'archer', kind: 'soldier' };
                damage(b, by, victim, { ranged: true });
            } else {
                b.events.push({ type: 'arrow', x: a.x1, z: a.z1 });
            }
        }
        b.arrows = still;
    }

    // The nearest enemy an archer can see in a narrow cone ahead, for the player's shot.
    function aimedVictim(b, u) {
        var best = null, bestD = stats(u).range * stats(u).range;
        for (var i = 0; i < b.units.length; i++) {
            var o = b.units[i];
            if (o.side === u.side || !alive(o)) continue;
            var dx = o.x - u.x, dz = o.z - u.z, d = dx * dx + dz * dz;
            if (d > bestD) continue;
            if (Math.abs(wrapAngle(Math.atan2(dz, dx) - u.facing)) > 0.3) continue;
            best = o; bestD = d;
        }
        return best;
    }

    // The player's swing: the nearest living enemy inside reach in a cone ahead.
    function playerVictim(b, u) {
        var best = null, bestD = (reachOf(u) + 0.45) * (reachOf(u) + 0.45);
        for (var i = 0; i < b.units.length; i++) {
            var o = b.units[i];
            if (o.side === u.side || !alive(o)) continue;
            var dx = o.x - u.x, dz = o.z - u.z, d = dx * dx + dz * dz;
            if (d > bestD) continue;
            if (Math.abs(wrapAngle(Math.atan2(dz, dx) - u.facing)) > 1.05) continue;
            best = o; bestD = d;
        }
        return best;
    }

    // ---------------------------------------------------------------- movement

    function moveTowards(u, x, z, speed, dt) {
        var dx = x - u.x, dz = z - u.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-6) return;
        var step = Math.min(d, speed * dt);
        u.x += dx / d * step;
        u.z += dz / d * step;
        u.moving = Math.max(u.moving, step / dt / CHARGE);
    }

    // Figures closer than two body radii push apart, so a melee does not collapse into a point.
    // The dead are underfoot, not in the way. The player's figure pushes but is pushed less.
    function separate(b) {
        var us = b.units;
        for (var i = 0; i < us.length; i++) {
            var a = us[i];
            if (!alive(a)) continue;
            var ra = radiusOf(a);
            for (var j = i + 1; j < us.length; j++) {
                var c = us[j];
                if (!alive(c)) continue;
                var min = ra + radiusOf(c), min2 = min * min;
                var dx = c.x - a.x, dz = c.z - a.z, d2 = dx * dx + dz * dz;
                if (d2 >= min2) continue;
                var d = Math.sqrt(d2) || 1e-3;
                if (d2 === 0) { dx = 1e-3; }
                var push = (min - d) / 2;
                var ka = a.id === b.player ? 0.3 : 1, kc = c.id === b.player ? 0.3 : 1;
                a.x -= dx / d * push * ka; a.z -= dz / d * push * ka;
                c.x += dx / d * push * kc; c.z += dz / d * push * kc;
            }
        }
    }

    // ---------------------------------------------------------------- the AI

    function think(b, u, dt) {
        var k = stats(u);
        if (u.state === 'routing') {
            // Run for the side's own back edge.
            var away = u.side === 0 ? -1 : 1;
            moveTowards(u, u.x * 1.05, away * (FIELD_HALF_LENGTH + 5), FLEE * k.speed, dt);
            u.facing = turnTowards(u.facing, away > 0 ? Math.PI / 2 : -Math.PI / 2, 6 * dt);
            if (Math.abs(u.z) > FIELD_HALF_LENGTH) u.state = 'escaped';
            return;
        }
        if (b.phase === 'hold') return;

        u.retarget -= dt;
        var t = u.target != null ? unit(b, u.target) : null;
        if (!t || !alive(t) || u.retarget <= 0) {
            t = nearestEnemy(b, u);
            u.target = t ? t.id : null;
            u.retarget = RETARGET_SECONDS;
        }
        if (!t) return;
        var dx = t.x - u.x, dz = t.z - u.z, d = Math.sqrt(dx * dx + dz * dz);
        u.facing = turnTowards(u.facing, Math.atan2(dz, dx), 5 * dt);
        if (u.type === 'archer' && d > ARCHER_KEEP_CLEAR) {
            // Close enough to shoot: stand and loose. Otherwise walk up to range.
            if (d <= k.range) {
                u.state = 'ready';
                shoot(b, u, t);
            } else {
                u.state = 'ready';
                moveTowards(u, t.x, t.z, WALK * k.speed, dt);
            }
            return;
        }
        if (d > reachOf(u)) {
            u.state = 'ready';
            var speed = (d < CHARGE_RANGE ? CHARGE : WALK) * k.speed;
            moveTowards(u, t.x, t.z, speed, dt);
        } else {
            u.state = 'fighting';
            trySwing(b, u, t);
        }
    }

    // ---------------------------------------------------------------- the player

    // input: {forward, right, yaw, run, attack, block}; forward/right in -1..1 relative to yaw,
    // the camera's heading (the direction the camera looks along the ground, as a facing angle).
    function drive(b, u, input, dt) {
        var k = stats(u);
        u.blocking = !!input.block;
        var f = clamp(Number(input.forward) || 0, -1, 1), r = clamp(Number(input.right) || 0, -1, 1);
        var yaw = Number.isFinite(input.yaw) ? input.yaw : u.facing;
        var mag = Math.min(1, Math.sqrt(f * f + r * r));
        if (mag > 0.01) {
            // Right of the heading (cos y, sin y) is (-sin y, cos y) in (x, z).
            var mx = Math.cos(yaw) * f - Math.sin(yaw) * r;
            var mz = Math.sin(yaw) * f + Math.cos(yaw) * r;
            var ml = Math.sqrt(mx * mx + mz * mz) || 1;
            var speed = (input.run ? CHARGE : WALK * 1.3) * k.speed * (u.blocking ? 0.5 : 1) * mag;
            u.x += mx / ml * speed * dt;
            u.z += mz / ml * speed * dt;
            u.moving = speed / CHARGE;
        }
        // The figure faces where the player looks.
        u.facing = turnTowards(u.facing, yaw, 12 * dt);
        u.x = clamp(u.x, -FIELD_HALF_LENGTH, FIELD_HALF_LENGTH);
        u.z = clamp(u.z, -FIELD_HALF_LENGTH, FIELD_HALF_LENGTH);
        u.state = 'fighting';
        if (input.attack) {
            // An archer shoots at what it aims at, unless an enemy is close enough to strike.
            var close = playerVictim(b, u);
            if (u.type === 'archer' && !close) shoot(b, u, aimedVictim(b, u));
            else trySwing(b, u, close);
        }
    }

    // ---------------------------------------------------------------- the step

    function checkRout(b, side) {
        if (b.routed[side]) return;
        // Counted in soldiers where the types' figures stand for different numbers of them.
        var total = 0, lost = 0, s = b.sides[side];
        for (var i = 0; i < b.units.length; i++) {
            var u = b.units[i];
            if (u.side !== side || u.kind === 'avatar') continue;
            var w = s.mixed ? s.types[u.type].perFigure : 1;
            total += w;
            if (u.state === 'dead') lost += w;
        }
        if (total === 0 || lost / total < breakingPoint(b.sides[side])) return;
        b.routed[side] = true;
        b.log.push({ t: b.time, text: b.sides[side].label + ' breaks and runs!' });
        for (var j = 0; j < b.units.length; j++) {
            var v = b.units[j];
            if (v.side === side && fighting(v) && v.id !== b.player) {
                v.state = 'routing';
                v.target = null;
            }
        }
    }

    // Whether a side still holds the field. Only soldiers count: the avatar cannot hold the
    // field alone, so once every soldier of a side has fallen or fled that side has lost.
    function standing(b, side) {
        for (var i = 0; i < b.units.length; i++) {
            var u = b.units[i];
            if (u.side === side && u.kind === 'soldier' && fighting(u)) return true;
        }
        return false;
    }

    function step(b, input) {
        b.events = [];
        if (b.phase === 'over') return b;
        var dt = DT;
        b.time += dt;
        if (b.phase === 'hold' && b.time >= HOLD_SECONDS) {
            b.phase = 'advance';
            b.log.push({ t: b.time, text: 'The armies advance.' });
        }

        var me = b.player != null ? unit(b, b.player) : null;
        if (me && !alive(me)) { b.player = null; me = null; }

        for (var i = 0; i < b.units.length; i++) {
            var u = b.units[i];
            u.cooldown = Math.max(0, u.cooldown - dt);
            if (u.reload > 0) u.reload = Math.max(0, u.reload - dt);
            u.swing = Math.max(0, u.swing - dt);
            u.hurt = Math.max(0, u.hurt - dt);
            u.moving *= 0.8;
            if (!alive(u)) continue;
            if (u === me) drive(b, u, input || {}, dt);
            else { u.blocking = false; think(b, u, dt); }
            u.stride += u.moving * dt * 9;
        }
        separate(b);
        if (b.arrows.length) landArrows(b);

        if (b.phase === 'advance') {
            for (var j = 0; j < b.units.length; j++) {
                if (b.units[j].state === 'fighting') {
                    b.phase = 'melee';
                    b.log.push({ t: b.time, text: 'The lines meet.' });
                    break;
                }
            }
        }
        checkRout(b, 0);
        checkRout(b, 1);

        var a = standing(b, 0), c = standing(b, 1);
        if (b.phase !== 'pursuit' && (!a || !c || b.time >= TIME_LIMIT)) {
            // The field is decided; the beaten side's runners are chased off it before the end.
            b.winner = a && !c ? 0 : c && !a ? 1 : 'draw';
            b.log.push({ t: b.time, text: b.winner === 'draw' ? 'Neither side holds the field.'
                : b.sides[b.winner].label + ' holds the field.' });
            b.phase = 'pursuit';
            b.pursuitEnds = b.time + PURSUIT_SECONDS;
        }
        if (b.phase === 'pursuit' && b.winner !== 'draw' && !(b.winner === 0 ? a : c)) {
            // The beaten side's avatar cut down the winner's last soldier during the pursuit.
            b.winner = 'draw';
            b.log.push({ t: b.time, text: 'Neither side holds the field.' });
        }
        if (b.phase === 'pursuit') {
            var running = false;
            for (var r = 0; r < b.units.length; r++) if (b.units[r].state === 'routing') { running = true; break; }
            if (b.winner === 'draw' || !running || b.time >= b.pursuitEnds) b.phase = 'over';
        }
        return b;
    }

    // ---------------------------------------------------------------- the player's figure

    function embody(b, id) {
        var u = unit(b, id);
        if (!u || !fighting(u) && u.state !== 'routing') return false;
        if (u.state === 'routing') u.state = 'fighting';   // the player rallies it
        b.player = u.id;
        return true;
    }

    function release(b) {
        var u = b.player != null ? unit(b, b.player) : null;
        b.player = null;
        if (u && u.state === 'fighting') u.state = 'ready';
        if (u) u.blocking = false;
    }

    // The player's own avatar joins a side: in front of the middle of its first rank, or, once
    // the battle has started, behind the side's figures still fighting.
    function spawnAvatar(b, side, avatar) {
        side = side === 1 ? 1 : 0;
        for (var i = 0; i < b.units.length; i++) {
            var old = b.units[i];
            if (old.kind === 'avatar' && alive(old)) { old.state = 'escaped'; }  // one avatar at a time
        }
        var dir = side === 0 ? 1 : -1, x = 0, z;
        if (b.phase === 'hold') {
            z = -dir * (START_GAP / 2 - 2.2);
        } else {
            var sx = 0, sz = 0, n = 0;
            for (var j = 0; j < b.units.length; j++) {
                var u = b.units[j];
                if (u.side === side && fighting(u)) { sx += u.x; sz += u.z; n++; }
            }
            x = n ? sx / n : 0;
            z = n ? sz / n - dir * 6 : -dir * (START_GAP / 2);
        }
        var a = makeUnit(b, side, 'avatar', x, z);
        a.avatar = avatar || null;
        b.player = a.id;
        b.log.push({ t: b.time, text: ((avatar && avatar.name) || 'Your avatar') + ' takes the field.' });
        // Joining the side that lost reopens the fight only while that side still has a soldier
        // standing (a draw at the time limit, say): the avatar cannot hold the field alone.
        if ((b.phase === 'over' || b.phase === 'pursuit') && side !== b.winner && standing(b, side)) {
            b.phase = 'melee';
            b.winner = null;
        }
        return a;
    }

    // ---------------------------------------------------------------- the map's rule

    // What Barony's turn rules do with the same two armies (CombatService.resolveEngagement):
    // both sides lose the other's effective strength at once. Strength weighs infantry 1, archers
    // 1.25 and cavalry 1.5 (in hundredths, to stay in whole numbers); a player-1 army's is then
    // scaled by morale / 100, both rounded half up. Infantry alone: soldiers * morale / 100, and
    // anyone else's is its soldier count, as before there were types.
    function mapOutcome(sides) {
        var s = [cleanSide(sides && sides[0], 0), cleanSide(sides && sides[1], 1)];
        function strength(side) {
            var w = side.infantry * 100 + side.archers * 125 + side.cavalry * 150;
            return side.playerId === 1 ? Math.floor((w * side.morale + 5000) / 10000) : Math.floor((w + 50) / 100);
        }
        var str = [strength(s[0]), strength(s[1])];
        var left = [Math.max(0, s[0].soldiers - str[1]), Math.max(0, s[1].soldiers - str[0])];
        return {
            strength: str,
            remaining: left,
            lost: [s[0].soldiers - left[0], s[1].soldiers - left[1]],
            winner: left[0] > 0 && left[1] === 0 ? 0 : left[1] > 0 && left[0] === 0 ? 1 : left[0] === 0 ? 'both-destroyed' : 'both-stand'
        };
    }

    // ---------------------------------------------------------------- the result the map keeps

    // What a finished battle hands back to the game when field battles decide the map (the
    // backend's BattleResult): the winner, 0, 1 or null for a draw, and how many soldiers each
    // side keeps. The winner keeps its soldiers still standing; the loser keeps only those who
    // ran and got away (the backend moves them off the field). In a draw both keep who stands.
    // In soldiers, never more than a side started with. The avatar is not a soldier.
    //
    // survivorsByType splits each side's survivors into [infantry, archers, cavalry], each counted
    // at its own type's scale and never more than the side brought of it; survivors is their sum.
    function fieldResult(b) {
        var s = summary(b);
        var winner = b.winner === 0 || b.winner === 1 ? b.winner : null;
        var keep = [0, 0], byType = [[0, 0, 0], [0, 0, 0]];
        for (var side = 0; side < 2; side++) {
            var c = s.sides[side], start = [b.sides[side].infantry, b.sides[side].archers, b.sides[side].cavalry];
            for (var t = 0; t < 3; t++) {
                var ty = c.types[TYPES[t]];
                var figures = winner === null || winner === side ? ty.standing : ty.routing + ty.escaped;
                byType[side][t] = Math.max(0, Math.min(start[t], Math.round(figures * ty.perFigure)));
                keep[side] += byType[side][t];
            }
        }
        // Defensive only: the winner always has a soldier standing (standing() ignores the avatar,
        // and a winner who loses its last soldier in the pursuit becomes a draw), so this never
        // lifts a real result. It stays so the backend never sees a winner with nobody left.
        if (winner !== null && keep[winner] < 1) {
            var bring = [b.sides[winner].infantry, b.sides[winner].archers, b.sides[winner].cavalry];
            byType[winner][bring[0] > 0 ? 0 : bring[1] > 0 ? 1 : 2] += 1;
            keep[winner] = 1;
        }
        return { winner: winner, survivors: keep, survivorsByType: byType };
    }

    var api = {
        DT: DT, FIGURE_CAP: FIGURE_CAP, REACH: REACH, KINDS: KINDS, TYPES: TYPES, TYPE_STATS: TYPE_STATS,
        FIELD_HALF_LENGTH: FIELD_HALF_LENGTH, typeFigures: typeFigures, stats: stats,
        create: create, step: step, embody: embody, release: release, spawnAvatar: spawnAvatar,
        summary: summary, mapOutcome: mapOutcome, fieldResult: fieldResult, unit: unit, alive: alive, fighting: fighting,
        figuresFor: figuresFor, breakingPoint: breakingPoint, hitChance: hitChance, rng: rng, wrapAngle: wrapAngle
    };
    if (typeof module === 'object' && module.exports) module.exports = api;
    else root.BattleSim = api;
})(this);
