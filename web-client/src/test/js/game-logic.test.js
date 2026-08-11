// Node's built-in test runner (available since Node 18, no extra dependency required).
// Run with: node --test web-client/src/test/js/
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
    summarizeHoldings,
    getStatClass,
    diffCastleMilestones,
    validateSplitAmount,
    resolvePanelOpenState,
    resolvePanelOrder,
    movePanelInOrder,
    isPanelHidden,
    movePanelAmongVisible,
    canMovePanel,
    PREFERENCE_STORAGE_KEYS,
    mergePreferences,
    createPreferenceSync
} = require('../../main/resources/static/js/game-logic.js');

// The sidebar panels in markup order, as game.html captures them for DEFAULT_PANEL_ORDER.
const DEFAULT_PANELS = ['status', 'run-history', 'armies', 'policy', 'settings'];

function tile(type, ownerId, occupationTicks) {
    return { type: type, ownerId: ownerId === undefined ? null : ownerId, occupationTicks: occupationTicks || 0 };
}

// 2x2 grid: (0,0) player-1 castle, (1,0) player-2 castle, (0,1) neutral village, (1,1) owned village.
function makeGameState(overrides) {
    const state = {
        width: 2,
        height: 2,
        tickCount: 1,
        grid: [
            [tile('CASTLE', 1), tile('VILLAGE', null)],
            [tile('CASTLE', 2), tile('VILLAGE', 1)]
        ]
    };
    return Object.assign(state, overrides);
}

test('summarizeHoldings counts castles and villages by owner', () => {
    const holdings = summarizeHoldings(makeGameState());

    assert.equal(holdings.castlesYours, 1);
    assert.equal(holdings.castlesEnemy, 1);
    assert.equal(holdings.castlesNeutral, 0);
    assert.equal(holdings.castlesTotal, 2);
    assert.equal(holdings.villagesYours, 1);
    assert.equal(holdings.villagesTotal, 2);
});

test('summarizeHoldings counts a neutral castle', () => {
    const state = makeGameState({
        grid: [
            [tile('CASTLE', null), tile('VILLAGE', null)],
            [tile('CASTLE', 2), tile('VILLAGE', 1)]
        ]
    });

    const holdings = summarizeHoldings(state);

    assert.equal(holdings.castlesYours, 0);
    assert.equal(holdings.castlesEnemy, 1);
    assert.equal(holdings.castlesNeutral, 1);
    assert.equal(holdings.castlesTotal, 2);
});

test('getStatClass buckets values into good/warn/danger', () => {
    assert.equal(getStatClass(90), 'stat--good');
    assert.equal(getStatClass(95), 'stat--good');
    assert.equal(getStatClass(70), 'stat--warn');
    assert.equal(getStatClass(89.9), 'stat--warn');
    assert.equal(getStatClass(69.9), 'stat--danger');
    assert.equal(getStatClass(0), 'stat--danger');
});

test('diffCastleMilestones returns null on first load (no previous state)', () => {
    assert.equal(diffCastleMilestones(null, makeGameState()), null);
});

test('diffCastleMilestones returns null when tickCount has not advanced (e.g. after a reset)', () => {
    const previous = makeGameState({ tickCount: 5 });
    const current = makeGameState({ tickCount: 1 });

    assert.equal(diffCastleMilestones(previous, current), null);
});

test('diffCastleMilestones returns null when the grid dimensions changed', () => {
    const previous = makeGameState({ tickCount: 1, width: 2, height: 2 });
    const current = makeGameState({ tickCount: 2, width: 3, height: 3 });

    assert.equal(diffCastleMilestones(previous, current), null);
});

test('diffCastleMilestones counts a castle captured by the player', () => {
    const previous = makeGameState({ tickCount: 1 });
    const current = makeGameState({
        tickCount: 2,
        grid: [
            [tile('CASTLE', 1), tile('VILLAGE', null)],
            [tile('CASTLE', 1), tile('VILLAGE', 1)]
        ]
    });

    const diff = diffCastleMilestones(previous, current);

    assert.equal(diff.captured, 1);
    assert.equal(diff.lost, 0);
    assert.equal(diff.holdings.castlesYours, 2);
});

test('diffCastleMilestones counts a castle lost by the player', () => {
    const previous = makeGameState({ tickCount: 1 });
    const current = makeGameState({
        tickCount: 2,
        grid: [
            [tile('CASTLE', 2), tile('VILLAGE', null)],
            [tile('CASTLE', 2), tile('VILLAGE', 1)]
        ]
    });

    const diff = diffCastleMilestones(previous, current);

    assert.equal(diff.captured, 0);
    assert.equal(diff.lost, 1);
    assert.equal(diff.holdings.castlesEnemy, 2);
});

test('diffCastleMilestones ignores a tile that is not a castle in both states', () => {
    // Village at (0,1) changes owner: not a CASTLE tile, so it must not count as captured/lost.
    const previous = makeGameState({ tickCount: 1 });
    const current = makeGameState({
        tickCount: 2,
        grid: [
            [tile('CASTLE', 1), tile('VILLAGE', 1)],
            [tile('CASTLE', 2), tile('VILLAGE', 1)]
        ]
    });

    const diff = diffCastleMilestones(previous, current);

    assert.equal(diff.captured, 0);
    assert.equal(diff.lost, 0);
});

test('validateSplitAmount rejects a non-numeric amount', () => {
    const result = validateSplitAmount(NaN, 10);

    assert.equal(result.valid, false);
    assert.equal(result.message, 'Enter a valid amount (at least 1)');
});

test('validateSplitAmount rejects an amount below 1', () => {
    const result = validateSplitAmount(0, 10);

    assert.equal(result.valid, false);
    assert.equal(result.message, 'Enter a valid amount (at least 1)');
});

test('validateSplitAmount rejects an amount above the max', () => {
    const result = validateSplitAmount(11, 10);

    assert.equal(result.valid, false);
    assert.equal(result.message, 'Cannot exceed 10 soldiers');
});

test('validateSplitAmount accepts an amount within range', () => {
    const result = validateSplitAmount(5, 10);

    assert.equal(result.valid, true);
    assert.equal(result.message, null);
});

test('validateSplitAmount accepts the maximum amount itself', () => {
    const result = validateSplitAmount(10, 10);

    assert.equal(result.valid, true);
});

test('resolvePanelOpenState uses the saved preference when one exists, even against the HTML default', () => {
    assert.equal(resolvePanelOpenState('settings', { settings: true }, false, false), true);
    assert.equal(resolvePanelOpenState('status', { status: false }, true, true), false);
});

test('resolvePanelOpenState forces panels open on desktop when there is no saved preference', () => {
    assert.equal(resolvePanelOpenState('policy', {}, true, false), true);
});

test('resolvePanelOpenState falls back to the HTML default on mobile when there is no saved preference', () => {
    assert.equal(resolvePanelOpenState('policy', {}, false, false), false);
    assert.equal(resolvePanelOpenState('armies', {}, false, true), true);
});

test('resolvePanelOpenState treats a missing saved-state object like no preference', () => {
    assert.equal(resolvePanelOpenState('status', undefined, true, false), true);
    assert.equal(resolvePanelOpenState('status', undefined, false, true), true);
});

test('resolvePanelOrder falls back to the default order when nothing is remembered', () => {
    assert.deepEqual(resolvePanelOrder([], DEFAULT_PANELS), DEFAULT_PANELS);
    assert.deepEqual(resolvePanelOrder(undefined, DEFAULT_PANELS), DEFAULT_PANELS);
});

test('resolvePanelOrder keeps a fully remembered arrangement', () => {
    const saved = ['armies', 'policy', 'status', 'settings', 'run-history'];

    assert.deepEqual(resolvePanelOrder(saved, DEFAULT_PANELS), saved);
});

test('resolvePanelOrder drops remembered panels that no longer exist', () => {
    const saved = ['armies', 'treasury', 'status', 'run-history', 'policy', 'settings'];

    assert.deepEqual(resolvePanelOrder(saved, DEFAULT_PANELS),
        ['armies', 'status', 'run-history', 'policy', 'settings']);
});

test('resolvePanelOrder collapses a duplicated panel id', () => {
    const saved = ['status', 'status', 'run-history', 'armies', 'policy', 'settings'];

    assert.deepEqual(resolvePanelOrder(saved, DEFAULT_PANELS), DEFAULT_PANELS);
});

test('resolvePanelOrder inserts a newly added panel after the panel it follows by default', () => {
    // The player arranged the sidebar before "run-history" existed, so it is missing from their
    // saved order — it belongs after "status", not at the bottom of the sidebar.
    const saved = ['status', 'armies', 'policy', 'settings'];

    assert.deepEqual(resolvePanelOrder(saved, DEFAULT_PANELS),
        ['status', 'run-history', 'armies', 'policy', 'settings']);
});

test('resolvePanelOrder puts a new first panel at the top rather than the bottom', () => {
    const saved = ['run-history', 'armies', 'policy', 'settings'];

    assert.deepEqual(resolvePanelOrder(saved, DEFAULT_PANELS), DEFAULT_PANELS);
});

test('resolvePanelOrder keeps consecutive new panels in their default relative order', () => {
    const saved = ['status', 'settings'];

    assert.deepEqual(resolvePanelOrder(saved, DEFAULT_PANELS), DEFAULT_PANELS);
});

test('movePanelInOrder moves a panel up and down without mutating the original', () => {
    const order = DEFAULT_PANELS.slice();

    assert.deepEqual(movePanelInOrder(order, 'armies', -1),
        ['status', 'armies', 'run-history', 'policy', 'settings']);
    assert.deepEqual(movePanelInOrder(order, 'armies', 1),
        ['status', 'run-history', 'policy', 'armies', 'settings']);
    assert.deepEqual(order, DEFAULT_PANELS);
});

test('movePanelInOrder is a no-op past either end of the sidebar', () => {
    assert.deepEqual(movePanelInOrder(DEFAULT_PANELS, 'status', -1), DEFAULT_PANELS);
    assert.deepEqual(movePanelInOrder(DEFAULT_PANELS, 'settings', 1), DEFAULT_PANELS);
});

test('movePanelInOrder is a no-op for a panel that is not in the order', () => {
    assert.deepEqual(movePanelInOrder(DEFAULT_PANELS, 'treasury', -1), DEFAULT_PANELS);
});

test('isPanelHidden reports only panels the player explicitly hid', () => {
    assert.equal(isPanelHidden('policy', { policy: true }), true);
    assert.equal(isPanelHidden('policy', { policy: false }), false);
    assert.equal(isPanelHidden('policy', {}), false);
    assert.equal(isPanelHidden('policy', undefined), false);
});

test('movePanelAmongVisible moves a panel past the hidden panel between it and the next visible one', () => {
    // With Run History hidden, one press of ▼ on Game Status has to put it below Armies — the
    // panel the player can actually see — rather than swapping it with the hidden panel.
    assert.deepEqual(movePanelAmongVisible(DEFAULT_PANELS, { 'run-history': true }, 'status', 1),
        ['armies', 'status', 'run-history', 'policy', 'settings']);
});

test('movePanelAmongVisible moves up past a hidden panel, mirroring the move down', () => {
    assert.deepEqual(movePanelAmongVisible(DEFAULT_PANELS, { 'run-history': true }, 'armies', -1),
        ['armies', 'status', 'run-history', 'policy', 'settings']);
});

test('movePanelAmongVisible keeps a hidden panel under the visible panel it sits below', () => {
    // Change Policy is hidden below Armies, so it follows Armies down and is still there to be
    // shown again in the same place.
    assert.deepEqual(movePanelAmongVisible(DEFAULT_PANELS, { policy: true }, 'armies', 1),
        ['status', 'run-history', 'settings', 'armies', 'policy']);
});

test('movePanelAmongVisible leaves a hidden panel that precedes every visible one at the top', () => {
    const order = ['run-history', 'status', 'armies', 'policy', 'settings'];

    assert.deepEqual(movePanelAmongVisible(order, { 'run-history': true }, 'status', 1),
        ['run-history', 'armies', 'status', 'policy', 'settings']);
});

test('movePanelAmongVisible steps a hidden panel through the full order', () => {
    // A hidden panel has nothing to move past on screen, so it moves one place in the order the
    // layout controls list — which is how the player positions it before showing it again.
    assert.deepEqual(movePanelAmongVisible(DEFAULT_PANELS, { 'run-history': true }, 'run-history', 1),
        ['status', 'armies', 'run-history', 'policy', 'settings']);
});

test('movePanelAmongVisible is a no-op past the last visible panel, whatever follows it hidden', () => {
    const order = ['status', 'armies', 'policy', 'settings', 'run-history'];

    assert.deepEqual(movePanelAmongVisible(order, { 'run-history': true }, 'settings', 1), order);
});

test('movePanelAmongVisible matches movePanelInOrder when no panel is hidden', () => {
    assert.deepEqual(movePanelAmongVisible(DEFAULT_PANELS, {}, 'armies', -1),
        movePanelInOrder(DEFAULT_PANELS, 'armies', -1));
    assert.deepEqual(movePanelAmongVisible(DEFAULT_PANELS, undefined, 'armies', 1),
        movePanelInOrder(DEFAULT_PANELS, 'armies', 1));
});

test('movePanelAmongVisible is a no-op for a panel that is not in the order, and never mutates it', () => {
    const order = DEFAULT_PANELS.slice();

    assert.deepEqual(movePanelAmongVisible(order, { 'run-history': true }, 'treasury', 1),
        DEFAULT_PANELS);
    movePanelAmongVisible(order, { 'run-history': true }, 'status', 1);
    assert.deepEqual(order, DEFAULT_PANELS);
});

test('canMovePanel disables ▲ on the first visible panel even when a hidden panel precedes it', () => {
    assert.equal(canMovePanel(DEFAULT_PANELS, { 'run-history': true }, 'status', -1), false);
    assert.equal(
        canMovePanel(['run-history', 'status', 'armies', 'policy', 'settings'],
            { 'run-history': true }, 'status', -1),
        false);
});

test('canMovePanel disables ▼ on the last visible panel even when hidden panels follow it', () => {
    assert.equal(
        canMovePanel(['status', 'armies', 'policy', 'settings', 'run-history'],
            { 'run-history': true }, 'settings', 1),
        false);
});

test('canMovePanel offers both directions to a visible panel with visible panels either side', () => {
    assert.equal(canMovePanel(DEFAULT_PANELS, { 'run-history': true }, 'armies', -1), true);
    assert.equal(canMovePanel(DEFAULT_PANELS, { 'run-history': true }, 'armies', 1), true);
});

test('canMovePanel judges a hidden panel by the ends of the full order', () => {
    assert.equal(canMovePanel(DEFAULT_PANELS, { 'run-history': true }, 'run-history', -1), true);
    assert.equal(canMovePanel(DEFAULT_PANELS, { status: true }, 'status', -1), false);
});

test('canMovePanel reports no move for a panel that is not in the order', () => {
    assert.equal(canMovePanel(DEFAULT_PANELS, {}, 'treasury', -1), false);
});

// mergePreferences reconciles the copy stored against the player's account with this browser's.

test('mergePreferences prefers what the account holds, so the arrangement travels between browsers', () => {
    const merged = mergePreferences(
        { settings: { theme: 'high-contrast' }, panelState: { armies: false } },
        { settings: { theme: 'classic' }, panelState: { armies: true } });

    assert.deepEqual(merged.preferences,
        { settings: { theme: 'high-contrast' }, panelState: { armies: false } });
    assert.equal(merged.uploadNeeded, false);
});

test('mergePreferences keeps a preference only this browser has, and asks for it to be uploaded', () => {
    const merged = mergePreferences(
        { settings: { theme: 'high-contrast' } },
        { panelLayout: { order: ['armies', 'status'], hidden: {} } });

    assert.deepEqual(merged.preferences, {
        settings: { theme: 'high-contrast' },
        panelLayout: { order: ['armies', 'status'], hidden: {} }
    });
    assert.equal(merged.uploadNeeded, true);
});

test('mergePreferences uploads everything when the account holds nothing yet', () => {
    const local = { settings: { theme: 'classic' }, panelState: { armies: false } };

    [null, undefined, {}].forEach((stored) => {
        const merged = mergePreferences(stored, local);
        assert.deepEqual(merged.preferences, local);
        assert.equal(merged.uploadNeeded, true);
    });
});

test('mergePreferences asks for no upload when neither side has anything', () => {
    const merged = mergePreferences({}, {});

    assert.deepEqual(merged.preferences, {});
    assert.equal(merged.uploadNeeded, false);
});

test('mergePreferences carries only the preferences the page knows about', () => {
    const merged = mergePreferences({ settings: { theme: 'classic' }, injected: 'value' },
        { alsoInjected: 'value' });

    assert.deepEqual(Object.keys(merged.preferences), ['settings']);
    assert.equal(merged.uploadNeeded, false);
});

test('PREFERENCE_STORAGE_KEYS names the localStorage key each preference is kept under', () => {
    assert.deepEqual(PREFERENCE_STORAGE_KEYS, {
        settings: 'barony_settings',
        panelLayout: 'barony_panel_layout',
        panelState: 'barony_panel_state'
    });
});

// createPreferenceSync decides when what this browser holds may be sent to the player's account.
// The browser it drives is stood in for below: an in-memory store for localStorage, a clock the
// test advances by hand, an account copy the test resolves when it chooses, and a record of every
// upload that was sent.

// setTimeout/clearTimeout the test drives, so a debounce or a deferred guard is observed rather
// than waited out. Callbacks run in due order when the clock is advanced past their delay.
function fakeClock() {
    let now = 0;
    let nextHandle = 1;
    const scheduled = new Map();
    return {
        schedule(callback, delay) {
            const handle = nextHandle++;
            scheduled.set(handle, { at: now + delay, callback: callback });
            return handle;
        },
        cancel(handle) {
            scheduled.delete(handle);
        },
        advance(ms) {
            now += ms;
            Array.from(scheduled.entries())
                .filter(([, timer]) => timer.at <= now)
                .sort((a, b) => a[1].at - b[1].at)
                .forEach(([handle, timer]) => {
                    scheduled.delete(handle);
                    timer.callback();
                });
        }
    };
}

const UPLOAD_DELAY_MS = 500;

function makeSync(options) {
    options = options || {};
    const clock = fakeClock();
    const store = Object.assign({}, options.local || {});
    const harness = {
        clock: clock,
        store: store,
        uploads: [],
        applied: 0,
        errors: [],
        signedIn: options.signedIn !== false,
        resolveAccountCopy: null,
        rejectAccountCopy: null
    };
    const accountCopy = new Promise((resolve, reject) => {
        harness.resolveAccountCopy = resolve;
        harness.rejectAccountCopy = reject;
    });

    harness.sync = createPreferenceSync({
        readLocal: () => Object.assign({}, store),
        writeLocal: (preferences) => Object.assign(store, preferences),
        applyPreferences: () => {
            harness.applied++;
            if (options.onApply) options.onApply(harness);
        },
        fetchRemote: () => accountCopy,
        sendRemote: (preferences) => {
            harness.uploads.push(preferences);
            return Promise.resolve();
        },
        isSignedIn: () => harness.signedIn,
        onError: (e) => harness.errors.push(e),
        schedule: clock.schedule,
        cancel: clock.cancel,
        uploadDelayMs: UPLOAD_DELAY_MS
    });
    return harness;
}

// The defect found by hand during the review of #85: `username` is set before the account copy is
// requested, so a preference changed while that request was in flight used to schedule an upload
// that could beat the response and put this browser's stale copy over the account's, losing the
// stored arrangement for good. Removing the gate in createPreferenceSync fails this test.
test('a change made while the account copy is in flight is not uploaded ahead of the response', async () => {
    const browserLayout = { order: ['armies', 'status'], hidden: {} };
    const accountLayout = { order: ['status', 'armies'], hidden: { policy: true } };
    const harness = makeSync({ local: { panelLayout: browserLayout } });

    const loaded = harness.sync.load();
    harness.sync.queueUpload();
    harness.clock.advance(UPLOAD_DELAY_MS * 4);

    assert.deepEqual(harness.uploads, []);

    harness.resolveAccountCopy({ panelLayout: accountLayout });
    await loaded;

    assert.equal(harness.uploads.length, 1);
    assert.deepEqual(harness.uploads[0].panelLayout, accountLayout);
    assert.deepEqual(harness.store.panelLayout, accountLayout);
});

test('a change made while the account copy is in flight is sent once the merge has happened', async () => {
    const harness = makeSync({ local: { settings: { theme: 'classic' } } });

    const loaded = harness.sync.load();
    harness.sync.queueUpload();
    harness.resolveAccountCopy({ settings: { theme: 'high-contrast' }, panelState: { armies: false } });
    await loaded;

    assert.equal(harness.uploads.length, 1);
    assert.deepEqual(harness.uploads[0], {
        settings: { theme: 'high-contrast' },
        panelState: { armies: false }
    });
});

test('a load that finds nothing this browser alone holds uploads nothing', async () => {
    const harness = makeSync({ local: { settings: { theme: 'classic' } } });

    const loaded = harness.sync.load();
    harness.resolveAccountCopy({ settings: { theme: 'high-contrast' } });
    await loaded;

    assert.deepEqual(harness.uploads, []);
    assert.equal(harness.applied, 1);
    assert.deepEqual(harness.store.settings, { theme: 'high-contrast' });
});

test('a preference only this browser holds is uploaded as soon as the load reconciles it', async () => {
    const layout = { order: ['armies', 'status'], hidden: {} };
    const harness = makeSync({ local: { panelLayout: layout } });

    const loaded = harness.sync.load();
    harness.resolveAccountCopy({ settings: { theme: 'high-contrast' } });
    await loaded;

    assert.equal(harness.uploads.length, 1);
    assert.deepEqual(harness.uploads[0], {
        settings: { theme: 'high-contrast' },
        panelLayout: layout
    });
});

test('a load that fails opens the gate, so this browser can still save afterwards', async () => {
    const harness = makeSync({ local: { settings: { theme: 'classic' } } });
    const failure = new Error('backend unreachable');

    const loaded = harness.sync.load();
    harness.rejectAccountCopy(failure);
    await loaded;

    assert.deepEqual(harness.errors, [failure]);
    assert.equal(harness.applied, 0, 'there is no account copy to apply');
    assert.deepEqual(harness.uploads, [], 'a failed load is not itself a change to send');

    harness.sync.queueUpload();
    harness.clock.advance(UPLOAD_DELAY_MS);

    assert.equal(harness.uploads.length, 1);
});

test('a change made while a failed load was in flight is still sent once it fails', async () => {
    const harness = makeSync({ local: { settings: { theme: 'classic' } } });

    const loaded = harness.sync.load();
    harness.sync.queueUpload();
    harness.rejectAccountCopy(new Error('backend unreachable'));
    await loaded;

    assert.deepEqual(harness.uploads, [{ settings: { theme: 'classic' } }]);
});

test('applying the account copy does not echo back as a change the player made', async () => {
    // Applying opens and closes panels, each of which reports itself as a change.
    const harness = makeSync({
        local: {},
        onApply: (state) => {
            state.sync.queueUpload();
            state.sync.queueUpload();
        }
    });

    const loaded = harness.sync.load();
    harness.resolveAccountCopy({ panelState: { armies: false } });
    await loaded;
    harness.clock.advance(UPLOAD_DELAY_MS);

    assert.deepEqual(harness.uploads, []);
});

test('the echo guard outlives the apply, catching a toggle raised after it returns', async () => {
    const harness = makeSync({ local: {} });

    const loaded = harness.sync.load();
    harness.resolveAccountCopy({ panelState: { armies: false } });
    await loaded;

    // The `toggle` event a panel raises when it is opened arrives after applying has returned.
    harness.sync.queueUpload();
    harness.clock.advance(UPLOAD_DELAY_MS);
    assert.deepEqual(harness.uploads, []);

    // A change the player makes afterwards is a real one.
    harness.sync.queueUpload();
    harness.clock.advance(UPLOAD_DELAY_MS);
    assert.equal(harness.uploads.length, 1);
});

test('the changes one player action fans out into are coalesced into a single upload', async () => {
    const harness = makeSync({ local: {} });

    const loaded = harness.sync.load();
    harness.resolveAccountCopy({});
    await loaded;
    harness.clock.advance(0);

    harness.store.panelState = { armies: false };
    harness.sync.queueUpload();
    harness.clock.advance(UPLOAD_DELAY_MS - 1);
    harness.sync.queueUpload();
    harness.store.panelLayout = { order: ['armies'], hidden: {} };
    harness.sync.queueUpload();
    harness.clock.advance(UPLOAD_DELAY_MS);

    // One request, carrying what was stored by the time it was sent rather than when it was queued.
    assert.equal(harness.uploads.length, 1);
    assert.deepEqual(harness.uploads[0], {
        panelState: { armies: false },
        panelLayout: { order: ['armies'], hidden: {} }
    });
});

test('a change is not uploaded when there is no signed-in account to upload it to', async () => {
    const harness = makeSync({ local: {}, signedIn: false });

    const loaded = harness.sync.load();
    harness.resolveAccountCopy(null);
    await loaded;
    harness.clock.advance(0);

    harness.store.panelState = { armies: false };
    harness.sync.queueUpload();
    harness.clock.advance(UPLOAD_DELAY_MS);

    assert.deepEqual(harness.uploads, []);
});
