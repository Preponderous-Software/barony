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
    resolvePanelOpenState
} = require('../../main/resources/static/js/game-logic.js');

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
