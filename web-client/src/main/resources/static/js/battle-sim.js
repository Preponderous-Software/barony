/* battle-sim.js — a field battle between two Barony armies, one figure per soldier.
 *
 * No DOM and no engine: the /battle page renders it with Strata, and
 * web-client/src/test/js/battle-sim.test.js runs it under node. The same seed
 * and the same inputs give the same battle, step for step.
 *
 *   const b = BattleSim.create({sides: [{soldiers, morale, playerId}, {...}], terrain, seed});
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

    var KINDS = {
        soldier: { hp: 10, damage: [2, 5], cooldown: 1.1, speed: 1.0 },
        avatar:  { hp: 32, damage: [4, 8], cooldown: 0.8, speed: 1.12 }
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
        return {
            soldiers: clamp(toInt(s.soldiers, 20), 1, 5000),
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

    function makeUnit(b, side, kind, x, z) {
        var k = KINDS[kind];
        var u = {
            id: b.nextId++,
            side: side,
            kind: kind,
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
            fallFacing: 0
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
            log: []                   // the battle's notable moments, for the page's ticker
        };
        var largest = Math.max(sides[0].soldiers, sides[1].soldiers);
        for (var side = 0; side < 2; side++) {
            var f = figuresFor(sides[side].soldiers, largest);
            sides[side].figures = f.figures;
            sides[side].perFigure = f.perFigure;
            var dir = side === 0 ? -1 : 1;            // which way is "back" for this side
            var ranks = Math.ceil(f.figures / FILE_WIDTH);
            for (var i = 0; i < f.figures; i++) {
                var rank = Math.floor(i / FILE_WIDTH);
                var inRank = rank === ranks - 1 ? f.figures - rank * FILE_WIDTH : FILE_WIDTH;
                var file = i % FILE_WIDTH;
                var x = (file - (inRank - 1) / 2) * FILE_SPACING + (b.random() - 0.5) * 0.25;
                var z = dir * (START_GAP / 2 + rank * RANK_SPACING) + (b.random() - 0.5) * 0.25;
                makeUnit(b, side, 'soldier', x, z);
            }
        }
        return b;
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
            var c = { figures: b.sides[s].figures, perFigure: b.sides[s].perFigure, standing: 0, routing: 0, dead: 0, escaped: 0 };
            for (var i = 0; i < b.units.length; i++) {
                var u = b.units[i];
                if (u.side !== s || u.kind === 'avatar') continue;
                if (fighting(u)) c.standing++;
                else if (u.state === 'routing') c.routing++;
                else if (u.state === 'dead') c.dead++;
                else c.escaped++;
            }
            // In soldiers, so a capped army still reads in Barony's own numbers.
            c.soldiersStanding = Math.round(c.standing * c.perFigure);
            c.soldiersLost = Math.round(c.dead * c.perFigure);
            c.soldiersFled = Math.round((c.routing + c.escaped) * c.perFigure);
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

    function damage(b, attacker, victim) {
        var k = KINDS[attacker.kind];
        var dmg = k.damage[0] + b.random() * (k.damage[1] - k.damage[0]);
        if (victim.blocking) {
            // A raised shield turns most of a blow from the front.
            var from = angleTo(victim.x, victim.z, attacker.x, attacker.z);
            if (Math.abs(wrapAngle(from - victim.facing)) < 1.2) dmg *= 0.25;
        }
        if (victim.state === 'routing') dmg *= 1.5;     // a back turned to the enemy
        victim.hp -= dmg;
        victim.hurt = 0.25;
        b.events.push({ type: 'hit', x: victim.x, z: victim.z, side: victim.side, by: attacker.id, on: victim.id,
                        blocked: !!victim.blocking });
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
        var k = KINDS[u.kind];
        u.cooldown = k.cooldown * (0.85 + b.random() * 0.3);
        u.swing = SWING_SECONDS;
        if (victim && b.random() < (u.id === b.player ? Math.max(hitChance(b.sides[u.side]), 0.75) : hitChance(b.sides[u.side]))) {
            damage(b, u, victim);
        } else if (victim) {
            b.events.push({ type: 'miss', x: victim.x, z: victim.z, by: u.id, on: victim.id });
        }
        return true;
    }

    // The player's swing: the nearest living enemy inside reach in a cone ahead.
    function playerVictim(b, u) {
        var best = null, bestD = (REACH + 0.45) * (REACH + 0.45);
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
        var us = b.units, min = BODY_RADIUS * 2, min2 = min * min;
        for (var i = 0; i < us.length; i++) {
            var a = us[i];
            if (!alive(a)) continue;
            for (var j = i + 1; j < us.length; j++) {
                var c = us[j];
                if (!alive(c)) continue;
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
        var k = KINDS[u.kind];
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
        if (d > REACH) {
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
        var k = KINDS[u.kind];
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
        if (input.attack) trySwing(b, u, playerVictim(b, u));
    }

    // ---------------------------------------------------------------- the step

    function checkRout(b, side) {
        if (b.routed[side]) return;
        var total = 0, lost = 0;
        for (var i = 0; i < b.units.length; i++) {
            var u = b.units[i];
            if (u.side !== side || u.kind === 'avatar') continue;
            total++;
            if (u.state === 'dead') lost++;
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
            u.swing = Math.max(0, u.swing - dt);
            u.hurt = Math.max(0, u.hurt - dt);
            u.moving *= 0.8;
            if (!alive(u)) continue;
            if (u === me) drive(b, u, input || {}, dt);
            else { u.blocking = false; think(b, u, dt); }
            u.stride += u.moving * dt * 9;
        }
        separate(b);

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
    // both sides lose the other's effective strength at once, where a player-1 army's strength is
    // soldiers * morale / 100, rounded half up, and anyone else's is its soldier count.
    function mapOutcome(sides) {
        var s = [cleanSide(sides && sides[0], 0), cleanSide(sides && sides[1], 1)];
        function strength(side) {
            return side.playerId === 1 ? Math.floor((side.soldiers * side.morale + 50) / 100) : side.soldiers;
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
    function fieldResult(b) {
        var s = summary(b);
        var start = [b.sides[0].soldiers, b.sides[1].soldiers];
        var winner = b.winner === 0 || b.winner === 1 ? b.winner : null;
        var keep = [0, 0];
        for (var side = 0; side < 2; side++) {
            var c = s.sides[side];
            var figures = winner === null || winner === side ? c.standing : c.routing + c.escaped;
            keep[side] = Math.max(0, Math.min(start[side], Math.round(figures * c.perFigure)));
        }
        // Defensive only: the winner always has a soldier standing (standing() ignores the avatar,
        // and a winner who loses its last soldier in the pursuit becomes a draw), so this never
        // lifts a real result. It stays so the backend never sees a winner with nobody left.
        if (winner !== null && keep[winner] < 1) keep[winner] = 1;
        return { winner: winner, survivors: keep };
    }

    var api = {
        DT: DT, FIGURE_CAP: FIGURE_CAP, REACH: REACH, KINDS: KINDS, FIELD_HALF_LENGTH: FIELD_HALF_LENGTH,
        create: create, step: step, embody: embody, release: release, spawnAvatar: spawnAvatar,
        summary: summary, mapOutcome: mapOutcome, fieldResult: fieldResult, unit: unit, alive: alive, fighting: fighting,
        figuresFor: figuresFor, breakingPoint: breakingPoint, hitChance: hitChance, rng: rng, wrapAngle: wrapAngle
    };
    if (typeof module === 'object' && module.exports) module.exports = api;
    else root.BattleSim = api;
})(this);
