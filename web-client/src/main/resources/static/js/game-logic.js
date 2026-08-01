// Pure, DOM-free game-state calculations shared by the game page's inline script and by the
// Node test suite (web-client/src/test/js/game-logic.test.js). Loaded as a plain <script> before
// the page's inline script, so these are exposed as globals in the browser (matching the call
// sites already in game.html) and via module.exports under Node.
(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        var exported = factory();
        for (var key in exported) {
            if (Object.prototype.hasOwnProperty.call(exported, key)) {
                root[key] = exported[key];
            }
        }
    }
})(typeof window !== 'undefined' ? window : globalThis, function () {
    'use strict';

    // Counts the holdings both the status panel and the end-of-game summary need,
    // derived from the tiles already sent in the game state (no extra request).
    function summarizeHoldings(gameState) {
        var holdings = {
            castlesYours: 0, castlesEnemy: 0, castlesNeutral: 0,
            villagesYours: 0, villagesTotal: 0
        };
        for (var x = 0; x < gameState.width; x++) {
            for (var y = 0; y < gameState.height; y++) {
                var tile = gameState.grid[x][y];
                if (tile.type === 'CASTLE') {
                    if (tile.ownerId === 1) holdings.castlesYours++;
                    else if (tile.ownerId === 2) holdings.castlesEnemy++;
                    else holdings.castlesNeutral++;
                } else if (tile.type === 'VILLAGE') {
                    holdings.villagesTotal++;
                    if (tile.ownerId === 1) holdings.villagesYours++;
                }
            }
        }
        holdings.castlesTotal =
            holdings.castlesYours + holdings.castlesEnemy + holdings.castlesNeutral;
        return holdings;
    }

    function getStatClass(value) {
        if (value >= 90) return 'stat--good';
        if (value >= 70) return 'stat--warn';
        return 'stat--danger';
    }

    // Milestone beats: castles changing hands between two consecutive game states.
    // Returns null when there is nothing to announce (first load, or a reset that
    // rewinds the turn counter), otherwise the holdings plus how many castles were
    // captured/lost since `previous`.
    function diffCastleMilestones(previous, current) {
        if (!previous || current.tickCount <= previous.tickCount) return null;
        if (previous.width !== current.width || previous.height !== current.height) return null;

        var holdings = summarizeHoldings(current);
        var captured = 0, lost = 0;
        for (var x = 0; x < current.width; x++) {
            for (var y = 0; y < current.height; y++) {
                var now = current.grid[x][y];
                var before = previous.grid[x][y];
                if (now.type !== 'CASTLE' || before.type !== 'CASTLE') continue;
                if (now.ownerId === before.ownerId) continue;
                if (now.ownerId === 1) captured++;
                else if (before.ownerId === 1) lost++;
            }
        }

        return { holdings: holdings, captured: captured, lost: lost };
    }

    // Validates a proposed army split amount against the army's soldier count.
    // Returns { valid: true } or { valid: false, message: <reason> }.
    function validateSplitAmount(amount, max) {
        if (isNaN(amount) || amount < 1) {
            return { valid: false, message: 'Enter a valid amount (at least 1)' };
        }
        if (amount > max) {
            return { valid: false, message: 'Cannot exceed ' + max + ' soldiers' };
        }
        return { valid: true, message: null };
    }

    return {
        summarizeHoldings: summarizeHoldings,
        getStatClass: getStatClass,
        diffCastleMilestones: diffCastleMilestones,
        validateSplitAmount: validateSplitAmount
    };
});
