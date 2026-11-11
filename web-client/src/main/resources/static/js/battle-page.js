/* battle-page.js — the /battle page: loads the Strata engine, runs BattleSim, and lets the
 * player watch, take over a soldier, or fight as their own avatar.
 *
 * The battle does not change the player's game: Barony's turn rules still resolve the
 * engagement on the map. The result card shows both outcomes side by side.
 */
(function () {
    'use strict';

    var Sim = window.BattleSim, Render = window.BattleRender, Avatar = window.BattleAvatar;
    var $ = function (id) { return document.getElementById(id); };
    var params = new URLSearchParams(location.search);
    var playerMode = (function () { try { return localStorage.getItem('barony_mode') === 'guest' ? 'guest' : 'account'; } catch (e) { return 'account'; } })();
    var coarse = matchMedia('(pointer: coarse)').matches;
    if (coarse) document.body.classList.add('bt-coarse');

    // Keeps what floats over the bottom of the screen clear of the control bar, whose height
    // changes as its buttons wrap: the touch stick and buttons, and the loader's slow-device bar.
    function layoutOverlays() {
        var bar = document.querySelector('.bt-controls');
        if (!bar) return;
        var h = bar.offsetHeight;
        $('touch').style.bottom = (h + 8) + 'px';
        var slowBar = document.querySelector('.sk-slow');
        if (slowBar) slowBar.style.bottom = (h + 8 + ($('touch').hidden ? 0 : 140)) + 'px';
    }
    addEventListener('resize', layoutOverlays);
    setInterval(layoutOverlays, 500);

    // Barony's army colours, following the colourblind setting on the game page.
    var PALETTES = {
        none: ['#0066FF', '#FF0000'], deuteranopia: ['#0072B2', '#E69F00'],
        protanopia: ['#F0E442', '#D55E00'], tritanopia: ['#CC79A7', '#009E73']
    };
    var colours = (function () {
        try {
            var s = JSON.parse(localStorage.getItem('barony_settings') || '{}');
            return PALETTES[s.colorblind] || PALETTES.none;
        } catch (e) { return PALETTES.none; }
    })();
    document.documentElement.style.setProperty('--side0', colours[0]);
    document.documentElement.style.setProperty('--side1', colours[1]);

    function headers() { return { 'Content-Type': 'application/json', 'X-Barony-Player': playerMode }; }

    // ------------------------------------------------------------------ the avatar

    var avatar = Avatar.clean(null);
    try {
        var stored = localStorage.getItem('barony_avatar');
        if (stored) avatar = Avatar.clean(JSON.parse(stored));
    } catch (e) { /* keep the default */ }

    function loadAccountAvatar() {
        return fetch('/api/session/preferences', { headers: headers() })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (prefs) {
                if (prefs && prefs.avatar) {
                    avatar = Avatar.clean(prefs.avatar);
                    try { localStorage.setItem('barony_avatar', JSON.stringify(avatar)); } catch (e) { /* ignore */ }
                }
            })
            .catch(function () { /* the avatar in this browser is used */ });
    }

    // The account's preferences are replaced whole on save, so read them, set the avatar, write back.
    function saveAvatar(a) {
        avatar = Avatar.clean(a);
        try { localStorage.setItem('barony_avatar', JSON.stringify(avatar)); } catch (e) { /* ignore */ }
        if (playerMode === 'guest') return Promise.resolve('Saved in this browser.');
        return fetch('/api/session/preferences', { headers: headers() })
            .then(function (r) {
                if (r.status === 401) throw new Error('signed-out');
                return r.ok ? r.json() : {};
            })
            .then(function (prefs) {
                prefs = prefs && typeof prefs === 'object' ? prefs : {};
                prefs.avatar = avatar;
                return fetch('/api/session/preferences', { method: 'PUT', headers: headers(), body: JSON.stringify(prefs) });
            })
            .then(function (r) { return r.ok ? 'Saved to your account.' : 'Saved in this browser. Your account could not be reached.'; })
            .catch(function (e) {
                return e && e.message === 'signed-out' ? 'Saved in this browser. Sign in to keep it with your account.'
                    : 'Saved in this browser. Your account could not be reached.';
            });
    }

    // ------------------------------------------------------------------ setup from the game

    var engagements = [];
    // Field battles: /battle?pending=<id> fights a battle the game is waiting on, and its result
    // decides the map. The battle is locked to the real armies and seed: no setup, no restart.
    var pendingId = params.get('pending');
    var pending = null, posted = false;

    function engagementsFrom(state) {
        var mine = (state.armies || []).filter(function (a) { return a.playerId === 1; });
        var theirs = (state.armies || []).filter(function (a) { return a.playerId !== 1; });
        var out = [];
        mine.forEach(function (a) {
            var best = null, bestD = Infinity;
            theirs.forEach(function (e) {
                var d = Math.abs(a.x - e.x) + Math.abs(a.y - e.y);
                if (d < bestD) { bestD = d; best = e; }
            });
            if (!best) return;
            var tile = state.grid && state.grid[best.x] && state.grid[best.x][best.y];
            out.push({ mine: a, theirs: best, distance: bestD, terrain: tile && tile.type ? tile.type : 'EMPTY' });
        });
        out.sort(function (p, q) { return p.distance - q.distance; });
        return out;
    }

    var TERRAIN_NAME = { EMPTY: 'open field', VILLAGE: 'a village', CASTLE: 'a castle' };

    function loadGame() {
        var sel = $('engagement');
        return fetch('/api/session/state', { headers: headers() })
            .then(function (r) {
                // 409 "unreadable-save": the account's saved game exists but cannot be read. The
                // map explains it and offers a fresh start; here only a custom battle is possible.
                if (r.status === 409) return r.json().catch(function () { return {}; }).then(function (body) {
                    var e = new Error('status 409');
                    e.unreadableSave = !!body && body.error === 'unreadable-save';
                    throw e;
                });
                if (!r.ok) throw new Error('status ' + r.status);
                return r.json();
            })
            .then(function (state) {
                engagements = engagementsFrom(state);
                sel.textContent = '';
                var custom = new Option('Custom battle', '');
                sel.add(custom);
                engagements.forEach(function (g, i) {
                    var label = g.mine.soldiers + ' at (' + g.mine.x + ',' + g.mine.y + ') vs ' + g.theirs.soldiers +
                        ' at (' + g.theirs.x + ',' + g.theirs.y + '), ' + TERRAIN_NAME[g.terrain] +
                        (g.distance === 0 ? ', engaged now' : ', ' + g.distance + ' tiles apart');
                    sel.add(new Option(label, String(i)));
                });
                $('engagementNote').textContent = engagements.length
                    ? 'Each of your armies against the nearest enemy army, with real soldier counts and morale.'
                    : 'No armies to match up right now. Set up a custom battle below.';
                if (pendingId) {
                    pending = (state.pendingBattles || []).filter(function (b) { return String(b.id) === pendingId; })[0] || null;
                    if (pending) { usePending(); return true; }
                    $('engagementNote').textContent = 'That battle has already been decided. Here is a custom battle instead.';
                    pendingId = null;
                }
                var wanted = params.get('army');
                var idx = wanted ? engagements.findIndex(function (g) { return String(g.mine.id) === wanted; }) : -1;
                if (idx >= 0) { sel.value = String(idx); useEngagement(idx); return true; }
                return false;
            })
            .catch(function (e) {
                sel.textContent = '';
                sel.add(new Option('Custom battle', ''));
                var note = $('engagementNote');
                if (e && e.unreadableSave) {
                    pendingId = null;
                    note.textContent = 'Your saved game couldn’t be read, so no battles can be loaded from it. It has been kept. ';
                    var back = document.createElement('a');
                    back.href = '/game';
                    back.textContent = 'Go back to the map';
                    note.appendChild(back);
                    note.appendChild(document.createTextNode(' to start a fresh game, or set up a custom battle below.'));
                } else {
                    note.textContent = 'Sign in, or play as a guest on the map, to load battles from your game.';
                }
                return false;
            });
    }

    function usePending() {
        $('soldiers0').value = pending.side0Soldiers;
        $('morale0').value = pending.side0Morale;
        $('soldiers1').value = pending.side1Soldiers;
        $('terrain').value = pending.terrain || 'EMPTY';
        $('seed').value = pending.seed;
        document.body.classList.add('bt-pending');
        $('setupPanel').hidden = true;
        $('restartBtn').hidden = true;
        $('autoBtn').hidden = false;
        $('resultAgain').hidden = true;
        setup();
        hint('Battle at (' + pending.x + ', ' + pending.y + '). This one counts: the map keeps what happens here.');
    }

    // Sends the result to the game; the map then shows it. AUTO lets the map's own rule decide.
    function postResult(body) {
        return fetch('/api/session/battle', { method: 'POST', headers: headers(), body: JSON.stringify(body) })
            .then(function (r) {
                if (r.status === 401) throw new Error('Your sign-in has lapsed. Sign in again from the map.');
                if (r.status === 400) return r.json().catch(function () { return {}; }).then(function (e) {
                    throw new Error(e.message || 'The game no longer has this battle waiting. It may have been decided from the map.');
                });
                if (!r.ok) throw new Error('The game could not be reached (' + r.status + ').');
                return r.json();
            });
    }

    function mapNow(state) {
        // What the map shows on and around the battle tile after the result.
        var mine = 0, theirs = 0;
        (state.armies || []).forEach(function (a) {
            if (Math.abs(a.x - pending.x) + Math.abs(a.y - pending.y) > 1) return;
            if (a.playerId === 1) mine += a.soldiers; else theirs += a.soldiers;
        });
        return 'The map now has ' + mine + ' of yours and ' + theirs + ' of the enemy\u2019s on and around (' + pending.x + ', ' + pending.y + ').';
    }

    function useEngagement(i) {
        var g = engagements[i];
        if (!g) return;
        $('soldiers0').value = g.mine.soldiers;
        $('morale0').value = g.mine.morale != null ? g.mine.morale : 100;
        $('soldiers1').value = g.theirs.soldiers;
        $('terrain').value = g.terrain;
        $('seed').value = (g.mine.id * 7919 + g.theirs.id * 104729) % 999983 || 42;
        setup();
    }

    // ------------------------------------------------------------------ the battle

    var battle = null, running = false, speed = 1, accum = 0, scenery = null, sceneryLights = [];
    var resultShown = false, lastLogShown = 0;

    function setup() {
        var n0 = parseInt($('soldiers0').value, 10), n1 = parseInt($('soldiers1').value, 10);
        var m0 = parseInt($('morale0').value, 10), seed = parseInt($('seed').value, 10);
        var terrain = $('terrain').value;
        battle = Sim.create({
            sides: [
                { soldiers: n0, morale: m0, playerId: 1, label: 'Your army' },
                { soldiers: n1, playerId: 2, label: 'The enemy' }
            ],
            terrain: terrain, seed: seed
        });
        scenery = Render.scenery(battle.terrain, battle.seed);
        sceneryLights = Render.sceneryLights(battle.terrain);
        running = false; accum = 0; resultShown = false; lastLogShown = 0;
        $('result').hidden = true;
        $('playBtn').textContent = 'Sound the advance';
        $('side0Name').textContent = 'Your army · ' + battle.sides[0].soldiers;
        $('side1Name').textContent = 'The enemy · ' + battle.sides[1].soldiers;
        $('playBtn').disabled = false;
        if (kit) watchCamera();
        updateControls();
        var per = battle.sides[0].perFigure;
        hint(per > 1.05 ? 'Each figure stands for about ' + per.toFixed(1) + ' soldiers. Tap a soldier to take it over.'
            : 'Tap or click any soldier to take it over, or enter as your avatar.');
    }

    function input() {
        var f = 0, r = 0;
        if (keys.KeyW) f += 1; if (keys.KeyS) f -= 1;
        if (keys.KeyD) r += 1; if (keys.KeyA) r -= 1;
        f += stick.y; r += stick.x;
        return {
            forward: Math.max(-1, Math.min(1, f)), right: Math.max(-1, Math.min(1, r)),
            yaw: kit ? kit.cam.yaw + Math.PI : 0,
            run: !!(keys.ShiftLeft || keys.ShiftRight) || Math.hypot(stick.x, stick.y) > 0.92,
            attack: !!(keys.Space || mouse.attack || touchAttack),
            block: !!(keys.KeyF || mouse.block || touchBlock)
        };
    }

    function advance(dt) {
        if (!battle) return [];
        var events = [];
        if (!running) return events;
        accum += dt * speed;
        var steps = 0;
        while (accum >= Sim.DT && steps < 12) {
            Sim.step(battle, input());
            events = events.concat(battle.events);
            accum -= Sim.DT;
            steps++;
        }
        if (steps === 12) accum = 0;   // a slow device drops time rather than spiralling
        return events;
    }

    // ------------------------------------------------------------------ the engine

    var mod = null, kit = null, buf = { ptr: 0, cap: 0 }, ptBuf = { ptr: 0, cap: 0 }, lightBuf = { ptr: 0, cap: 0 };

    function upload(b, arr, fn, stride) {
        var n = arr.length / stride;
        if (arr.length > b.cap) {
            if (b.ptr) mod._free(b.ptr);
            b.cap = Math.max(arr.length, b.cap * 2, 1024);
            b.ptr = mod._malloc(b.cap * 4);
        }
        mod.HEAPF32.set(arr, b.ptr >> 2);
        fn(b.ptr, n);
    }

    var allPrims = new Float32Array(0);

    function draw(t, events) {
        var me = battle.player != null ? Sim.unit(battle, battle.player) : null;
        var fr = Render.frame(battle, { colours: colours, time: t, firstPerson: kit.cam.dist < 0.5 && !!me, events: events });
        var need = scenery.length + fr.prims.length;
        if (allPrims.length !== need) allPrims = new Float32Array(need);
        allPrims.set(scenery, 0);
        allPrims.set(fr.prims, scenery.length);
        upload(buf, allPrims, mod._bt_set_prims, Render.STRIDE);

        // Sparks linger a moment.
        sparks = sparks.filter(function (s) { s.life -= 1 / 60; return s.life > 0; });
        fr.points.forEach(function (p) { sparks.push({ p: p, life: 0.25 }); });
        var pts = new Float32Array(sparks.length * 8);
        sparks.forEach(function (s, i) {
            pts.set([s.p[0], s.p[1] + (0.25 - s.life) * 1.5, s.p[2], s.p[3], s.p[4], s.p[5], s.p[6], s.life * 3.5], i * 8);
        });
        upload(ptBuf, pts, mod._bt_set_points, 8);

        var ls = fr.lights.concat(sceneryLights);
        var lr = new Float32Array(ls.length * 8);
        ls.forEach(function (l, i) {
            var flicker = l === fr.lights[0] ? 1 : 0.85 + 0.15 * Math.sin(t * 13 + i * 2.1);
            lr.set([l[0], l[1], l[2], l[3], l[4][0], l[4][1], l[4][2], l[5] * flicker], i * 8);
        });
        upload(lightBuf, lr, mod._bt_set_lights, 8);
    }
    var sparks = [];

    // ------------------------------------------------------------------ cameras

    function watchCamera() {
        kit.limits.minDist = 6; kit.limits.maxDist = 150; kit.limits.minPitch = 0.12; kit.limits.maxPitch = 1.45;
        var c = kit.cam;
        // Looking across the field from the side, a little behind your army.
        c.yaw = -2.2; c.pitch = 0.42; c.dist = 44; c.tx = 0; c.ty = 0; c.tz = -4;
        kit.selected = null;
    }

    function followCamera(dt) {
        var me = battle.player != null ? Sim.unit(battle, battle.player) : null;
        var c = kit.cam;
        if (!me) return;
        var first = c.dist < 0.5;
        var tall = me.kind === 'avatar' ? 1.12 : 1;
        var head = (first ? 1.58 : 2.0) * tall;
        // Ease the target so a push in the crowd does not shake the view.
        var k = Math.min(1, dt * 14);
        c.tx += (me.x - c.tx) * k; c.ty += (head - c.ty) * k; c.tz += (me.z - c.tz) * k;
        kit.limits.minDist = 0.06; kit.limits.maxDist = 14;
        kit.limits.minPitch = first ? -1.1 : -0.25; kit.limits.maxPitch = 1.3;
        if (c.pitch < kit.limits.minPitch) c.pitch = kit.limits.minPitch;
        if (c.pitch > kit.limits.maxPitch) c.pitch = kit.limits.maxPitch;
    }

    function takeOver(id) {
        if (!battle || !Sim.embody(battle, id)) return false;
        var me = Sim.unit(battle, id);
        var c = kit.cam;
        c.yaw = me.facing + Math.PI; c.pitch = 0.3; c.dist = 5.5;
        c.tx = me.x; c.ty = 1.75; c.tz = me.z;
        kit.selected = 'embodied';
        if (!running && battle.phase !== 'over') start();
        updateControls();
        hint(coarse ? 'Left stick to move, drag to look. Strike and Shield are on the right.'
            : 'WASD to move, Shift to run. Click to lock the mouse, then click to strike and hold the right button (or F) to raise your shield. V for first person, Esc to let go.');
        return true;
    }

    function leaveBody() {
        if (!battle) return;
        Sim.release(battle);
        if (document.pointerLockElement) document.exitPointerLock();
        var c = kit.cam;
        c.dist = 30; c.pitch = 0.6;
        kit.limits.minDist = 6; kit.limits.maxDist = 150; kit.limits.minPitch = 0.12;
        kit.selected = null;
        updateControls();
        hint('Watching. Tap any soldier to take it over.');
    }

    // ------------------------------------------------------------------ the HUD

    function fmtTime(s) { s = Math.floor(s); return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); }
    var PHASES = { hold: 'Forming up', advance: 'Advancing', melee: 'Melee', pursuit: 'Pursuit', over: 'Over' };

    function hud() {
        if (!battle) return;
        var s = Sim.summary(battle);
        for (var i = 0; i < 2; i++) {
            var side = s.sides[i], total = side.figures || 1;
            $('side' + i + 'Fill').style.width = (100 * side.standing / total) + '%';
            $('side' + i + 'Run').style.width = (100 * side.routing / total) + '%';
            $('side' + i + 'Count').textContent = side.soldiersStanding;
        }
        $('phase').textContent = running || battle.phase === 'over' ? PHASES[battle.phase] : (battle.time > 0 ? 'Paused' : 'Ready');
        $('clock').textContent = fmtTime(battle.time);
        while (lastLogShown < battle.log.length) {
            hint(battle.log[lastLogShown++].text);
        }
        var me = battle.player != null ? Sim.unit(battle, battle.player) : null;
        if (battle.player == null && kit.selected === 'embodied') {
            // The player's figure fell.
            kit.selected = null;
            updateControls();
            hint('You have fallen. Tap another soldier to fight on, or enter as your avatar.');
            if (document.pointerLockElement) document.exitPointerLock();
            var c = kit.cam; c.dist = 9; c.pitch = 0.5;
            kit.limits.minDist = 6; kit.limits.minPitch = 0.12;
        }
        if (battle.phase === 'over' && !resultShown) showResult();
        if (me) $('viewBtn').textContent = kit.cam.dist < 0.5 ? 'Third person' : 'First person';
    }

    function showResult() {
        resultShown = true;
        running = false;
        var s = Sim.summary(battle), m = Sim.mapOutcome(battle.sides);
        var title = battle.winner === 0 ? 'Your army holds the field' : battle.winner === 1 ? 'The enemy holds the field' : 'Neither side holds the field';
        $('resultTitle').textContent = title;
        $('resultField').textContent = 'On the field, after ' + fmtTime(battle.time) + ': you lost ' + s.sides[0].soldiersLost +
            ' and ' + s.sides[0].soldiersFled + ' fled; the enemy lost ' + s.sides[1].soldiersLost + ' and ' + s.sides[1].soldiersFled + ' fled.';
        var mapLine = { 0: 'your army wins with ' + m.remaining[0] + ' left', 1: 'the enemy wins with ' + m.remaining[1] + ' left',
                        'both-destroyed': 'both armies are destroyed', 'both-stand': 'both armies survive (' + m.remaining[0] + ' and ' + m.remaining[1] + ' left)' }[m.winner];
        $('resultMap').textContent = 'On the map, Barony’s turn rules decide this engagement in one exchange: ' + mapLine +
            '. This practice battle does not change your game: battles opened from the map do.';
        $('result').hidden = false;
        $('playBtn').textContent = 'Fight it again';
        if (pending) {
            $('playBtn').hidden = true;
            $('avatarBtn').hidden = true;
            $('autoBtn').hidden = true;
            $('resultMap').textContent = 'Sending the result to your game\u2026';
            if (posted) return;
            posted = true;
            var r = Sim.fieldResult(battle);
            postResult({ battleId: pending.id, result: 'FIELD', winner: r.winner, survivors: r.survivors })
                .then(function (state) {
                    $('resultMap').textContent = 'This battle decided the map. You kept ' + r.survivors[0] + ', the enemy ' +
                        r.survivors[1] + (r.winner === 0 ? ' (they fell back).' : r.winner === 1 ? ' (you fell back).' : '.') + ' ' + mapNow(state);
                    nextOrBack(state);
                })
                .catch(function (e) { $('resultMap').textContent = e.message; });
        }
    }

    // After a result: on to the turn's next battle, or back to the map.
    function nextOrBack(state) {
        var next = (state.pendingBattles || [])[0];
        var back = document.querySelector('#result .bt-link');
        if (next && state.fieldBattles !== false) {
            back.textContent = 'Next battle';
            back.href = '/battle?pending=' + encodeURIComponent(next.id);
        } else {
            back.textContent = 'Back to the map';
            back.href = '/game';
        }
    }

    function hint(text) {
        var h = $('hint');
        h.textContent = text;
        h.classList.remove('bt-hint-on');
        void h.offsetWidth;
        h.classList.add('bt-hint-on');
    }

    function updateControls() {
        var embodied = battle && battle.player != null;
        $('leaveBtn').hidden = !embodied;
        $('viewBtn').hidden = !embodied;
        $('avatarBtn').hidden = !!embodied;
        $('touch').hidden = !(embodied && coarse);
        document.body.classList.toggle('bt-embodied', !!embodied);
        layoutOverlays();
    }

    function start() {
        if (!battle) return;
        if (battle.phase === 'over') { setup(); }
        running = true;
        $('playBtn').textContent = 'Pause';
        $('setupDetails').open = false;
    }

    // ------------------------------------------------------------------ input

    var keys = {}, mouse = { attack: false, block: false }, stick = { x: 0, y: 0 }, touchAttack = false, touchBlock = false;

    addEventListener('keydown', function (e) {
        if (e.target && /INPUT|SELECT|TEXTAREA/.test(e.target.tagName)) return;
        if ($('avatarDialog').open) return;
        keys[e.code] = true;
        if (battle && battle.player != null && (e.code === 'Space' || /Key[WASDF]/.test(e.code))) e.preventDefault();
        if (e.code === 'KeyV' && battle && battle.player != null) toggleView();
        if (e.code === 'Escape' && battle && battle.player != null && !document.pointerLockElement) leaveBody();
        if (e.code === 'KeyP') $('playBtn').click();
    });
    addEventListener('keyup', function (e) { keys[e.code] = false; });
    addEventListener('blur', function () { keys = {}; mouse.attack = mouse.block = false; });

    function toggleView() {
        var c = kit.cam;
        c.dist = c.dist < 0.5 ? 5.5 : 0.06;
        c.pitch = c.dist < 0.5 ? 0.05 : 0.3;
    }

    function wireMouse(canvas) {
        canvas.addEventListener('mousedown', function (e) {
            if (!battle || battle.player == null || coarse) return;
            if (document.pointerLockElement !== canvas) {
                if (canvas.requestPointerLock) {
                    try { var p = canvas.requestPointerLock(); if (p && p.catch) p.catch(function () {}); } catch (err) { /* drag to look instead */ }
                }
                return;
            }
            if (e.button === 0) mouse.attack = true;
            if (e.button === 2) mouse.block = true;
        });
        addEventListener('mouseup', function (e) {
            if (e.button === 0) mouse.attack = false;
            if (e.button === 2) mouse.block = false;
        });
        canvas.addEventListener('contextmenu', function (e) { if (battle && battle.player != null) e.preventDefault(); });
        document.addEventListener('mousemove', function (e) {
            if (document.pointerLockElement !== canvas || !kit) return;
            kit.cam.yaw += e.movementX * 0.0026;
            kit.cam.pitch += e.movementY * 0.0021;
        });
        document.addEventListener('pointerlockchange', function () {
            if (document.pointerLockElement !== canvas) { mouse.attack = mouse.block = false; }
        });
    }

    function wireTouch() {
        var stickEl = $('stick'), knob = $('knob'), id = null, cx = 0, cy = 0, R = 46;
        stickEl.addEventListener('pointerdown', function (e) {
            id = e.pointerId; stickEl.setPointerCapture(id);
            var r = stickEl.getBoundingClientRect(); cx = r.left + r.width / 2; cy = r.top + r.height / 2;
            move(e);
        });
        function move(e) {
            if (e.pointerId !== id) return;
            var dx = e.clientX - cx, dy = e.clientY - cy, m = Math.hypot(dx, dy), k = m > R ? R / m : 1;
            dx *= k; dy *= k;
            knob.style.transform = 'translate(' + dx + 'px,' + dy + 'px)';
            stick.x = dx / R; stick.y = -dy / R;
        }
        function end(e) {
            if (e.pointerId !== id) return;
            id = null; stick.x = stick.y = 0; knob.style.transform = '';
        }
        stickEl.addEventListener('pointermove', move);
        stickEl.addEventListener('pointerup', end);
        stickEl.addEventListener('pointercancel', end);
        function hold(btn, set) {
            btn.addEventListener('pointerdown', function (e) { e.preventDefault(); set(true); });
            ['pointerup', 'pointercancel', 'pointerleave'].forEach(function (n) { btn.addEventListener(n, function () { set(false); }); });
        }
        hold($('attackBtn'), function (v) { touchAttack = v; });
        hold($('blockBtn'), function (v) { touchBlock = v; });
    }

    // Tap a soldier: the nearest figure's chest on screen, within the radius.
    function pick(x, y, radius) {
        if (!battle) return null;
        var best = null, bestD = radius * radius * 2.2;
        for (var i = 0; i < battle.units.length; i++) {
            var u = battle.units[i];
            if (!Sim.fighting(u) && u.state !== 'routing') continue;
            var p = kit.project(u.x, 1.1, u.z);
            if (!p) continue;
            var d = (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y);
            if (d < bestD) { bestD = d; best = u; }
        }
        return best;
    }

    // ------------------------------------------------------------------ the avatar editor

    function swatches(boxId, current, onPick) {
        var box = $(boxId);
        box.textContent = '';
        Avatar.SWATCHES.forEach(function (c) {
            var b = document.createElement('button');
            b.type = 'button'; b.className = 'bt-swatch'; b.style.background = c;
            b.setAttribute('aria-label', c); b.setAttribute('aria-pressed', String(c === current));
            b.addEventListener('click', function () {
                box.querySelectorAll('.bt-swatch').forEach(function (o) { o.setAttribute('aria-pressed', 'false'); });
                b.setAttribute('aria-pressed', 'true'); onPick(c);
            });
            box.appendChild(b);
        });
    }

    var draft = null;
    function openEditor() {
        draft = Avatar.clean(avatar);
        $('avName').value = draft.name;
        $('avHelmet').value = draft.helmet;
        $('avWeapon').value = draft.weapon;
        $('avShield').checked = draft.shield;
        $('avSaved').textContent = playerMode === 'guest' ? 'As a guest, your avatar is kept in this browser.' : '';
        swatches('avTunic', draft.tunic, function (c) { draft.tunic = c; });
        swatches('avTrim', draft.trim, function (c) { draft.trim = c; });
        var d = $('avatarDialog');
        if (typeof d.showModal === 'function') d.showModal(); else d.setAttribute('open', '');
    }

    $('avatarForm').addEventListener('submit', function (e) {
        var how = e.submitter ? e.submitter.value : 'save';
        if (how !== 'save') return;
        e.preventDefault();
        draft.name = $('avName').value;
        draft.helmet = $('avHelmet').value;
        draft.weapon = $('avWeapon').value;
        draft.shield = $('avShield').checked;
        $('avSave').disabled = true;
        saveAvatar(draft).then(function (msg) {
            $('avSave').disabled = false;
            $('avSaved').textContent = msg;
            // An avatar on the field takes the new look at once.
            if (battle) battle.units.forEach(function (u) { if (u.kind === 'avatar') u.avatar = avatar; });
            setTimeout(function () { var d = $('avatarDialog'); if (d.open) d.close(); }, 700);
        });
    });

    // ------------------------------------------------------------------ buttons

    $('playBtn').addEventListener('click', function () {
        if (!battle) return;
        if (battle.phase === 'over') { if (pending) return; setup(); start(); return; }
        if (running) { running = false; $('playBtn').textContent = 'Resume'; } else start();
    });
    $('speedBtn').addEventListener('click', function () {
        speed = speed === 1 ? 2 : speed === 2 ? 4 : speed === 4 ? 0.5 : 1;
        $('speedBtn').textContent = (speed === 0.5 ? '½' : speed) + '×';
    });
    $('restartBtn').addEventListener('click', function () { setup(); });
    $('setupApply').addEventListener('click', function () { setup(); });
    $('engagement').addEventListener('change', function () {
        if ($('engagement').value !== '') useEngagement(parseInt($('engagement').value, 10));
    });
    $('avatarBtn').addEventListener('click', function () {
        if (!battle) return;
        if (battle.phase === 'over') { if (pending) return; setup(); }
        var a = Sim.spawnAvatar(battle, 0, avatar);
        takeOver(a.id);
    });
    $('leaveBtn').addEventListener('click', leaveBody);
    $('viewBtn').addEventListener('click', toggleView);
    $('editAvatarBtn').addEventListener('click', openEditor);
    $('resultAgain').addEventListener('click', function () { if (!pending) { setup(); start(); } });
    $('autoBtn').addEventListener('click', function () {
        if (!pending || posted) return;
        posted = true;
        running = false;
        $('autoBtn').disabled = true;
        postResult({ battleId: pending.id, result: 'AUTO' })
            .then(function (state) {
                var m = Sim.mapOutcome(battle.sides);
                $('resultTitle').textContent = 'The map decided this battle';
                $('resultField').textContent = 'Both sides lost the other\u2019s strength in one exchange: you kept ' + m.remaining[0] +
                    ', the enemy ' + m.remaining[1] + '.';
                $('resultMap').textContent = mapNow(state);
                $('resultAgain').hidden = true;
                $('result').hidden = false;
                $('playBtn').hidden = true;
                resultShown = true;
                nextOrBack(state);
            })
            .catch(function (e) { posted = false; $('autoBtn').disabled = false; hint(e.message); });
    });
    $('resultClose').addEventListener('click', function () { $('result').hidden = true; });

    // ------------------------------------------------------------------ boot

    var assets = $('battleAssets').dataset;
    (async function () {
        var engineJs = assets.engineJs || '/battle3d/battle-engine.js', engineWasm = assets.engineWasm || '/battle3d/battle-engine.wasm';
        mod = await StrataLoader.load({ script: engineJs, factory: 'createBattleEngine', wasm: engineWasm,
            title: 'the battlefield', fallback: { href: '/game', text: 'Back to the map' } });
        var canvas = $('view');
        kit = StrataKit.create({
            module: mod, canvas: canvas,
            camera: { yaw: -2.2, pitch: 0.42, dist: 44, tx: 0, ty: 0, tz: -4 },
            idleOrbit: { afterMs: 25000, rate: 0.0004 },
            frame: function (t, now, dt) {
                if (!battle) return;
                var events = kit.driven ? (Sim.step(battle, input()), battle.events) : advance(dt);
                if (battle.player != null) followCamera(dt || 1 / 60);
                draw(t, events);
            },
            after: function () { hud(); },
            pick: pick,
            idOf: function (u) { return u ? 'unit-' + u.id : null; },
            onTap: function (u) {
                if (!u || !battle) return;
                if (battle.player != null && (document.pointerLockElement || coarse)) return;
                takeOver(u.id);
            },
            onSlow: function () { StrataLoader.slow(); },
            onFatal: function (c, m) { StrataLoader.fallback(c, m); }
        });
        // Late afternoon in summer: long shadows across the field.
        kit.setSun(Date.UTC(2026, 5, 21, 15, 30) / 1000, 47, 8);
        wireMouse(canvas);
        wireTouch();
        setup();
        kit.start();
        StrataLoader.done();
        window.__battle = { get battle() { return battle; }, takeOver: takeOver, leaveBody: leaveBody, start: start, setup: setup,
                            get kit() { return kit; } };
        if (playerMode !== 'guest') loadAccountAvatar();
        await loadGame();
        if (params.get('play') === '1') start();
    })().catch(function (e) { console.error('battle:', e); });
})();
