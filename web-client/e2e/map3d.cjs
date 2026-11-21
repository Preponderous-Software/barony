// Plays the game page's 3D map in headless Chromium (WebGL 2 on SwiftShader) with Strata's real
// grid relay, against a locally running Barony (backend + web client), as a guest. Checks that
// the 3D map does what the 2D map does: it opens in 3D, hover and tap tooltips say what the 2D
// map says, clicks and taps select and move armies (the MOVE command the 2D map sends),
// right-click and Escape deselect, the page's keys still work (Space, A, S, Escape, and typing in
// the split box), the camera pans, turns, tilts and zooms by mouse, touch, keys and buttons, the
// 2D/3D switch is remembered, ?view=2d never downloads the relay, a browser without WebGL 2 keeps
// the 2D map with the reason, colour-blind palettes reach the 3D map, a phone gets a page with no
// sideways scroll, and nothing is logged as an error.
//
//   (backend on 127.0.0.1:18080, web client on 127.0.0.1:13000 with BACKEND_URL pointing at it)
//   NODE_PATH=<dir with playwright> node web-client/e2e/map3d.cjs http://127.0.0.1:13000 <screenshot dir>
//
// CHROMIUM may name a Chromium binary (else Playwright's own). Exits 1 on any failed check.
'use strict';
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const base = (process.argv[2] || 'http://127.0.0.1:13000').replace(/\/$/, '');
const shots = path.resolve(process.argv[3] || 'e2e-shots');
fs.mkdirSync(shots, { recursive: true });

let failures = 0;
function check(ok, what) {
    console.log((ok ? 'ok   ' : 'FAIL ') + what);
    if (!ok) failures++;
}

const LAUNCH = {
    executablePath: process.env.CHROMIUM || undefined,
    args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist']
};
const DESKTOP = { viewport: { width: 1280, height: 900 } };
const PHONE = { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true };
const PHONE_LANDSCAPE = { viewport: { width: 844, height: 390 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true };
const SLOW = 90000;  // SwiftShader on a busy machine draws a frame in up to a few seconds

// The guest cookie of the first game, carried into every later context, so a run starts one
// guest game and resumes it after (the backend allows a client address 5 new guests an hour).
const shared = { cookies: null };

async function open(browser, device, setup) {
    const context = await browser.newContext(device);
    if (shared.cookies) await context.addCookies(shared.cookies);
    const page = await context.newPage();
    const errors = [];
    const wasm = [];
    page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', (e) => errors.push(String(e)));
    // The module's download (the size gate's HEAD before it is not one).
    page.on('request', (r) => { if (/grid-relay-scene.*\.wasm$/.test(r.url()) && r.method() === 'GET') wasm.push(r.url()); });
    if (setup) await setup(page, context);
    return { page, context, errors, wasm };
}

// The run's guest game (started in the first context, resumed in the others, each with its own
// localStorage), with field battles off so a turn never leaves the page for the battlefield.
async function guestGame(page, query) {
    await page.goto(base + '/login', { waitUntil: 'domcontentloaded', timeout: SLOW });
    await page.evaluate(() => playAsGuest());
    await page.waitForURL('**/game', { timeout: SLOW, waitUntil: 'commit' });
    if (query) await page.goto(base + '/game' + query, { waitUntil: 'domcontentloaded', timeout: SLOW });
    await page.waitForFunction(() => typeof lastGameState !== 'undefined' && lastGameState, null, { timeout: SLOW });
    await page.evaluate(() => setFieldBattles(false));
    await page.waitForFunction(() => lastGameState.fieldBattles === false, null, { timeout: SLOW });
    if (!shared.cookies) shared.cookies = await page.context().cookies();
}

const on3D = (page) => page.waitForFunction(() => map3d && map3d.status() === 'on', null, { timeout: SLOW });
const frames = (page, n) => page.evaluate((k) => new Promise((done) => {
    let left = k;
    const step = () => (--left <= 0 ? done() : requestAnimationFrame(step));
    requestAnimationFrame(step);
}), n || 3);

async function showStage(page) {
    await page.evaluate(() => document.getElementById('gameCanvas').scrollIntoView({ block: 'center' }));
    await frames(page, 2);
}

// Page coordinates of an army's figure (own: player 1's first army) or a tile's centre on the 3D map.
function armyPoint(page, own) {
    return page.evaluate((mine) => {
        const pl = Map3DScene.armyPlacements(lastGameState).find((p) => (p.army.playerId === 1) === mine);
        const s = map3d.screenOf(pl.x, (pl.base + pl.height) / 2, pl.z);
        const r = document.getElementById('mapRelay').getBoundingClientRect();
        return { x: r.left + s.x, y: r.top + s.y, id: pl.army.id, gx: pl.army.x, gy: pl.army.y };
    }, own);
}

function tilePoint(page, gx, gy) {
    return page.evaluate(([x, y]) => {
        const c = Map3DScene.cellCenter(lastGameState, x, y);
        const s = map3d.screenOf(c[0], 0, c[1]);
        const r = document.getElementById('mapRelay').getBoundingClientRect();
        return { x: r.left + s.x, y: r.top + s.y };
    }, [gx, gy]);
}

// A tile a few steps from (gx, gy) toward the middle of the map, with no army on it.
function freeTileNear(page, gx, gy) {
    return page.evaluate(([x0, y0]) => {
        const st = lastGameState;
        const dx = x0 < st.width / 2 ? 1 : -1;
        const dy = y0 < st.height / 2 ? 1 : -1;
        for (let k = 3; k > 0; k--) {
            const x = x0 + dx * k;
            const y = y0 + dy * Math.min(k, 2);
            if (x >= 0 && y >= 0 && x < st.width && y < st.height && !st.armies.some((a) => a.x === x && a.y === y)) {
                return { gx: x, gy: y };
            }
        }
        return { gx: x0 + dx, gy: y0 };
    }, [gx, gy]);
}

const camera = (page) => page.evaluate(() => map3d.cameraState());
const tooltip = (page) => page.evaluate(() => {
    const t = document.getElementById('canvasTooltip');
    return t.style.display === 'block' ? t.textContent : null;
});

function moveCommand(page) {
    return page.waitForRequest((r) => r.url().endsWith('/api/session/command') && r.method() === 'POST', { timeout: SLOW });
}

// Touch through the DevTools protocol: pointer events with pointerType "touch", as a phone makes.
async function touch(cdp, type, points) {
    await cdp.send('Input.dispatchTouchEvent', { type, touchPoints: points.map((p, i) => ({ x: p.x, y: p.y, id: p.id || i + 1 })) });
}

async function desktop(browser) {
    console.log('-- desktop, 1280 x 900');
    const { page, context, errors, wasm } = await open(browser, DESKTOP);
    await guestGame(page);
    await on3D(page);
    check(true, 'a guest game opens on the 3D map');
    check(wasm.length === 1, 'the relay module is downloaded once (' + wasm.length + ')');
    const shown = await page.evaluate(() => ({
        canvas2d: getComputedStyle(document.getElementById('mapCanvas')).display,
        stage: !document.getElementById('map3dStage').hidden,
        pressed: document.getElementById('mapView3d').getAttribute('aria-pressed'),
        hint: !document.getElementById('map3dHint').hidden,
        tiles: map3d.debug().sent.board.shapes.filter((s) => Math.abs(s.scale[0] - 0.95) < 1e-4).length,
        cells: lastGameState.width * lastGameState.height,
        armies: map3d.debug().sent.pieces.anchors.length,
        armiesInState: lastGameState.armies.length,
        labels: map3d.debug().sent.hud.labels.map((l) => l.text).sort().join(','),
        counts: lastGameState.armies.map((a) => String(a.soldiers)).sort().join(',')
    }));
    check(shown.canvas2d === 'none' && shown.stage && shown.pressed === 'true' && shown.hint,
        'the 2D canvas steps aside for the 3D stage, the 3D button is pressed and the hint shows');
    check(shown.tiles === shown.cells, 'one 3D tile per map cell (' + shown.tiles + ')');
    check(shown.armies === shown.armiesInState, 'one 3D piece per army');
    check(shown.labels === shown.counts, 'each army\'s soldier count is labelled (' + shown.labels + ')');
    await showStage(page);
    await page.screenshot({ path: path.join(shots, 'desktop-3d.png') });

    // Hover: the same tooltip text as the 2D map, and the tile outlined.
    const own = await armyPoint(page, true);
    await page.mouse.move(own.x - 3, own.y);
    await page.mouse.move(own.x, own.y);
    await page.waitForFunction(() => document.getElementById('canvasTooltip').style.display === 'block', null, { timeout: SLOW });
    const expected = await page.evaluate((id) => {
        const a = lastGameState.armies.find((x) => x.id === id);
        return tooltipTextAt(a.x, a.y, lastGameState, a);
    }, own.id);
    check((await tooltip(page)) === expected, 'hovering an army shows the 2D map\'s tooltip: ' + expected);
    const hover = await page.evaluate(() => map3d.debug().hover);
    check(hover && hover.x === own.gx && hover.y === own.gy, 'and outlines its tile');

    // Click to select, click a tile to move: the 2D map's own MOVE command.
    await page.mouse.click(own.x, own.y);
    await page.waitForFunction((id) => selectedArmy && selectedArmy.id === id, own.id, { timeout: SLOW });
    const toast = await page.locator('#toast-container').textContent();
    check(toast.includes('Army #' + own.id + ' selected'), 'a click on your army selects it, with the 2D map\'s toast');
    await page.waitForFunction(() => map3d.debug().sent.hud.panels.length > lastGameState.armies.length, null, { timeout: SLOW });
    check(true, 'the selected army gets its gold edge');
    await page.screenshot({ path: path.join(shots, 'desktop-3d-selected.png') });
    const target = await freeTileNear(page, own.gx, own.gy);
    const tp = await tilePoint(page, target.gx, target.gy);
    const sent = moveCommand(page);
    await page.mouse.click(tp.x, tp.y);
    const body = (await sent).postDataJSON();
    check(body.type === 'MOVE' && body.armyId === own.id && body.targetX === target.gx && body.targetY === target.gy,
        'a click on a tile orders the selected army there: ' + JSON.stringify(body));
    await page.waitForFunction(() => selectedArmy === null, null, { timeout: SLOW });
    await page.waitForFunction(([x, y]) => {
        const blue = GridWire.rgba(GridWire.parseCss(PALETTE.player1Army));
        const c = Map3DScene.cellCenter(lastGameState, x, y);
        return map3d.debug().sent.board.shapes.filter((s) => s.color.every((v, i) => Math.abs(v - blue[i]) < 1e-4)
            && Math.abs(s.position[0] - c[0]) < 0.5 && Math.abs(s.position[2] - c[1]) < 0.5).length === 12;
    }, [target.gx, target.gy], { timeout: SLOW });
    check(true, 'the destination gets the dashed outline in the army\'s colour');

    // Right-click and Escape deselect.
    const again = await armyPoint(page, true);
    await page.mouse.click(again.x, again.y);
    await page.waitForFunction(() => selectedArmy !== null, null, { timeout: SLOW });
    await page.mouse.click(again.x, again.y, { button: 'right' });
    await page.waitForFunction(() => selectedArmy === null, null, { timeout: SLOW });
    check(true, 'a right-click deselects');
    await page.mouse.click(again.x, again.y);
    await page.waitForFunction(() => selectedArmy !== null, null, { timeout: SLOW });

    // The page's keys, with the 3D map showing: S focuses the split box, typing (Backspace too)
    // works there, Escape deselects, Space advances the turn, A toggles auto play.
    await page.keyboard.press('s');
    const focused = await page.evaluate(() => document.activeElement && document.activeElement.id);
    check(focused === 'panelSplitAmount', 'S focuses the split box');
    await page.keyboard.type('5');
    await page.keyboard.press('Backspace');
    await page.keyboard.type('3');
    const typed = await page.inputValue('#panelSplitAmount');
    check(typed === '3', 'typing and Backspace work in the split box under the 3D map (' + typed + ')');
    await page.evaluate(() => document.activeElement.blur());
    await page.keyboard.press('Escape');
    await page.waitForFunction(() => selectedArmy === null, null, { timeout: SLOW });
    check(true, 'Escape deselects');
    const turn = await page.evaluate(() => lastGameState.tickCount);
    await page.keyboard.press(' ');
    await page.waitForFunction((t) => lastGameState.tickCount > t, turn, { timeout: SLOW });
    check(true, 'Space advances the turn');
    await page.keyboard.press('a');
    check((await page.locator('#autoPlayToggle').textContent()) === 'Stop Auto', 'A starts auto play');
    await page.keyboard.press('a');
    check((await page.locator('#autoPlayToggle').textContent()) === 'Auto Play', 'and A stops it');
    const moved = await page.evaluate(() => map3d.debug().sent.pieces.anchors.some((a) => a[0] !== a[2] || a[1] !== a[3]));
    check(moved, 'a turn that moves an army slides its piece');

    // The camera: wheel, drag, right-drag, keys and buttons.
    await showStage(page);
    const box = await page.locator('#mapRelay').boundingBox();
    const mid = { x: box.x + box.width / 2, y: box.y + box.height * 0.6 };
    let c0 = await camera(page);
    await page.mouse.move(mid.x, mid.y);
    await page.mouse.wheel(0, -300);
    await page.waitForFunction((z) => map3d.cameraState().zoom < z, c0.zoom, { timeout: SLOW });
    check(true, 'the wheel zooms');
    c0 = await camera(page);
    await page.mouse.move(mid.x, mid.y);
    await page.mouse.down();
    await page.mouse.move(mid.x + 60, mid.y + 40, { steps: 4 });
    await page.mouse.up();
    await page.waitForFunction((t) => map3d.cameraState().target[0] !== t[0], c0.target, { timeout: SLOW });
    check(!(await page.evaluate(() => selectedArmy)), 'a drag pans the map and selects nothing');
    c0 = await camera(page);
    await page.mouse.move(mid.x, mid.y);
    await page.mouse.down({ button: 'right' });
    await page.mouse.move(mid.x + 120, mid.y, { steps: 4 });
    await page.mouse.up({ button: 'right' });
    await page.waitForFunction((y) => Math.abs(map3d.cameraState().yaw - y) > 0.2, c0.yaw, { timeout: SLOW });
    check(true, 'a right-drag turns the map');
    for (const [key, field] of [['q', 'yaw'], ['e', 'yaw'], ['PageUp', 'pitch'], ['PageDown', 'pitch'], ['=', 'zoom'], ['-', 'zoom']]) {
        const before = await camera(page);
        await page.keyboard.press(key);
        const after = await camera(page);
        check(after[field] !== before[field], 'key ' + key + ' changes the camera\'s ' + field);
    }
    const t0 = await camera(page);
    await page.keyboard.press('ArrowUp');
    check((await camera(page)).target.join() !== t0.target.join(), 'the arrows move the camera');
    await page.click('.map3d-controls button[aria-label="Turn left"]');
    await page.click('.map3d-controls button[aria-label="Zoom in"]');
    await frames(page, 3);
    await page.screenshot({ path: path.join(shots, 'desktop-3d-turned.png') });
    await page.keyboard.press('0');
    const reset = await camera(page);
    check(reset.zoom === 1 && reset.target.every((v) => v === 0), '0 shows the whole map again');

    // Colour-blind palettes reach the 3D map.
    await page.selectOption('#colorblindSelect', 'deuteranopia');
    await page.waitForFunction(() => {
        const want = GridWire.rgba(GridWire.parseCss('#0072B2'));
        return map3d.debug().sent.pieces.shapes.some((s) => s.color.every((v, i) => Math.abs(v - want[i]) < 1e-4));
    }, null, { timeout: SLOW });
    check(true, 'a colour-blind palette recolours the 3D armies');
    await frames(page, 3);
    await page.screenshot({ path: path.join(shots, 'desktop-3d-deuteranopia.png') });
    await page.selectOption('#colorblindSelect', 'none');

    // The switch: to 2D (remembered, V toggles), and a reload opens where it was left.
    await page.click('#mapView2d');
    await page.waitForFunction(() => getComputedStyle(document.getElementById('mapCanvas')).display !== 'none', null, { timeout: SLOW });
    const stored = await page.evaluate(() => localStorage.getItem('barony_map_view'));
    check(stored === '"2d"', '2D is remembered (' + stored + ')');
    check(await page.evaluate(() => document.getElementById('map3dStage').hidden), 'the 3D stage is hidden in 2D');
    await page.evaluate(() => document.getElementById('gameCanvas').scrollIntoView({ block: 'center' }));
    await page.screenshot({ path: path.join(shots, 'desktop-2d.png') });
    await page.reload({ waitUntil: 'domcontentloaded', timeout: SLOW });
    await page.waitForFunction(() => lastGameState, null, { timeout: SLOW });
    check(await page.evaluate(() => map3d.status() === 'off' && document.getElementById('mapView2d').getAttribute('aria-pressed') === 'true'),
        'a reload opens on the remembered 2D map');
    await page.keyboard.press('v');
    await on3D(page);
    check(true, 'V switches to 3D');
    await page.keyboard.press('v');
    await page.waitForFunction(() => map3d.status() === 'off', null, { timeout: SLOW });
    check(true, 'and back to 2D');
    await page.click('#mapView3d');
    await on3D(page);

    await frames(page, 2);
    const real = errors.filter((e) => !/favicon/.test(e) && !/status of 404/.test(e));
    check(real.length === 0, 'no errors logged' + (real.length ? ': ' + real.join(' | ') : ''));
    await context.close();
}

async function addressAndFallback(browser) {
    console.log('-- ?view=2d and no WebGL 2');
    {
        const { page, context, wasm } = await open(browser, DESKTOP);
        await guestGame(page, '?view=2d');
        await frames(page, 5);
        check(await page.evaluate(() => map3d.status() === 'off'), '?view=2d opens the 2D map');
        check(wasm.length === 0, 'and never downloads the relay module');
        check(await page.evaluate(() => localStorage.getItem('barony_map_view') === null), 'without changing what is remembered');
        await context.close();
    }
    {
        const { page, context, wasm } = await open(browser, DESKTOP, async (p) => {
            await p.addInitScript(() => {
                const get = HTMLCanvasElement.prototype.getContext;
                HTMLCanvasElement.prototype.getContext = function (kind, o) {
                    return kind === 'webgl2' ? null : get.call(this, kind, o);
                };
            });
        });
        await guestGame(page);
        await page.waitForFunction(() => map3d.status() === 'failed', null, { timeout: SLOW });
        const note = await page.locator('#mapViewNote').textContent();
        check(note === '3D map unavailable: this browser has no WebGL 2. Showing the 2D map.', 'no WebGL 2: the note says why (' + note + ')');
        check(await page.locator('#mapView3d').isDisabled(), 'the 3D button is disabled');
        check(await page.evaluate(() => getComputedStyle(document.getElementById('mapCanvas')).display !== 'none'), 'the 2D map shows');
        check(wasm.length === 0, 'and nothing was downloaded');
        await page.evaluate(() => window.scrollTo(0, 0));
        await page.screenshot({ path: path.join(shots, 'desktop-no-webgl2.png') });
        await context.close();
    }
}

async function phone(browser) {
    console.log('-- phone, 390 x 844, touch');
    const { page, context, errors } = await open(browser, PHONE);
    const cdp = await context.newCDPSession(page);
    await guestGame(page);
    await on3D(page);
    const sideways = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    check(sideways <= 0, 'no sideways scroll (' + sideways + ' px over)');
    const box = await page.locator('#mapRelay').boundingBox();
    const room = await page.evaluate(() => document.getElementById('gameCanvas').clientWidth);
    // The stage's 3 px border on each side is all that stands between the canvas and the box.
    check(box.width >= room - 8 && box.height >= 234, 'the 3D map fills the phone\'s width (' + Math.round(box.width)
        + ' x ' + Math.round(box.height) + ' in a ' + room + ' px box)');
    const fits = await page.evaluate(() => {
        const st = lastGameState;
        const r = document.getElementById('mapRelay').getBoundingClientRect();
        const corners = [[0, 0], [st.width - 1, 0], [0, st.height - 1], [st.width - 1, st.height - 1]];
        return corners.every(([x, y]) => {
            const c = Map3DScene.cellCenter(st, x, y);
            const s = map3d.screenOf(c[0], 0, c[1]);
            return s && s.x >= 0 && s.y >= 0 && s.x <= r.width && s.y <= r.height;
        });
    });
    check(fits, 'the whole map fits the portrait canvas');
    await showStage(page);
    await page.screenshot({ path: path.join(shots, 'phone-3d.png') });

    const own = await armyPoint(page, true);
    await page.touchscreen.tap(own.x, own.y);
    await page.waitForFunction((id) => selectedArmy && selectedArmy.id === id, own.id, { timeout: SLOW });
    check(true, 'a tap on your army selects it');
    check(((await tooltip(page)) || '').startsWith('Army #' + own.id), 'and shows its tooltip, as a tap on the 2D map does');
    await frames(page, 2);
    await page.screenshot({ path: path.join(shots, 'phone-3d-selected.png') });
    const target = await freeTileNear(page, own.gx, own.gy);
    const tp = await tilePoint(page, target.gx, target.gy);
    const sent = moveCommand(page);
    await page.touchscreen.tap(tp.x, tp.y);
    const body = (await sent).postDataJSON();
    check(body.type === 'MOVE' && body.targetX === target.gx && body.targetY === target.gy, 'a tap on a tile moves it there');

    // One finger pans, two pinch and twist, and the buttons work by tap.
    const mid = { x: box.x + box.width / 2, y: box.y + box.height * 0.6 };
    let c0 = await camera(page);
    await touch(cdp, 'touchStart', [mid]);
    for (let i = 1; i <= 4; i++) await touch(cdp, 'touchMove', [{ x: mid.x + 15 * i, y: mid.y + 10 * i }]);
    await touch(cdp, 'touchEnd', []);
    await page.waitForFunction((t) => map3d.cameraState().target.join() !== t.join(), c0.target, { timeout: SLOW });
    check(true, 'a one-finger drag pans the map');
    c0 = await camera(page);
    const a = { x: mid.x - 40, y: mid.y, id: 1 };
    const b = { x: mid.x + 40, y: mid.y, id: 2 };
    await touch(cdp, 'touchStart', [a, b]);
    for (let i = 1; i <= 4; i++) {
        await touch(cdp, 'touchMove', [{ x: a.x - 15 * i, y: a.y + 6 * i, id: 1 }, { x: b.x + 15 * i, y: b.y - 6 * i, id: 2 }]);
    }
    await touch(cdp, 'touchEnd', []);
    await page.waitForFunction((z) => map3d.cameraState().zoom < z, c0.zoom, { timeout: SLOW });
    const after = await camera(page);
    check(true, 'spreading two fingers zooms in');
    check(Math.abs(after.yaw - c0.yaw) > 0.05, 'and twisting them turns the map');
    const z1 = after.zoom;
    await page.locator('.map3d-controls button[aria-label="Zoom out"]').tap();
    check((await camera(page)).zoom > z1, 'the zoom-out button works by tap');
    await page.locator('.map3d-controls button[aria-label="Show the whole map"]').tap();
    await frames(page, 3);
    await page.screenshot({ path: path.join(shots, 'phone-3d-after-move.png') });

    await page.locator('#mapView2d').tap();
    await page.waitForFunction(() => map3d.status() === 'off', null, { timeout: SLOW });
    await page.evaluate(() => document.getElementById('gameCanvas').scrollIntoView({ block: 'center' }));
    await page.screenshot({ path: path.join(shots, 'phone-2d.png') });
    check(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'the 2D map has no sideways scroll either');

    const real = errors.filter((e) => !/favicon/.test(e) && !/status of 404/.test(e));
    check(real.length === 0, 'no errors logged on the phone' + (real.length ? ': ' + real.join(' | ') : ''));
    await context.close();

    console.log('-- phone, landscape 844 x 390');
    const land = await open(browser, PHONE_LANDSCAPE);
    await guestGame(land.page);
    await on3D(land.page);
    const fitsLandscape = await land.page.evaluate(() => {
        const st = lastGameState;
        const r = document.getElementById('mapRelay').getBoundingClientRect();
        return [[0, 0], [st.width - 1, st.height - 1]].every(([x, y]) => {
            const c = Map3DScene.cellCenter(st, x, y);
            const s = map3d.screenOf(c[0], 0, c[1]);
            return s && s.x >= 0 && s.y >= 0 && s.x <= r.width && s.y <= r.height;
        }) && r.height <= window.innerHeight;
    });
    check(fitsLandscape, 'landscape: the 3D map fits the short screen and the whole map fits it');
    await showStage(land.page);
    await land.page.screenshot({ path: path.join(shots, 'phone-landscape-3d.png') });
    await land.context.close();
}

(async () => {
    const browser = await chromium.launch(LAUNCH);
    try {
        // E2E_ONLY=desktop,fallback,phone runs some of the parts.
        const only = (process.env.E2E_ONLY || 'desktop,fallback,phone').split(',');
        if (only.includes('desktop')) await desktop(browser);
        if (only.includes('fallback')) await addressAndFallback(browser);
        if (only.includes('phone')) await phone(browser);
    } catch (e) {
        console.log('FAIL ' + (e && e.stack || e));
        failures++;
    } finally {
        await browser.close();
    }
    console.log(failures ? failures + ' check(s) failed' : 'all checks passed');
    process.exit(failures ? 1 : 0);
})();
