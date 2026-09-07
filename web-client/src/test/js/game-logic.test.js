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
    canMovePanel
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
