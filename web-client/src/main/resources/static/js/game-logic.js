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

    // Decides whether a sidebar panel should render open, given a per-panel preference
    // remembered from a previous visit (`savedState`, keyed by panel id). A remembered
    // preference always wins; otherwise falls back to forcing every panel open on desktop,
    // or the panel's own HTML default on mobile.
    function resolvePanelOpenState(panelId, savedState, isDesktop, defaultOpen) {
        if (savedState && Object.prototype.hasOwnProperty.call(savedState, panelId)) {
            return !!savedState[panelId];
        }
        return isDesktop ? true : !!defaultOpen;
    }

    // Reconciles a remembered sidebar order against the panels actually on the page.
    // Remembered ids that no longer exist are dropped, duplicates are collapsed, and a panel
    // the player has never arranged (one added since they last touched the layout) is inserted
    // just after whichever default-order panel precedes it, rather than silently sinking to the
    // bottom of the sidebar.
    function resolvePanelOrder(savedOrder, defaultOrder) {
        var resolved = [];
        var placed = {};
        (savedOrder || []).forEach(function (panelId) {
            if (defaultOrder.indexOf(panelId) === -1) return;
            if (placed[panelId]) return;
            placed[panelId] = true;
            resolved.push(panelId);
        });
        defaultOrder.forEach(function (panelId, defaultIndex) {
            if (placed[panelId]) return;
            var insertAt = 0;
            for (var i = defaultIndex - 1; i >= 0; i--) {
                var predecessorAt = resolved.indexOf(defaultOrder[i]);
                if (predecessorAt !== -1) {
                    insertAt = predecessorAt + 1;
                    break;
                }
            }
            resolved.splice(insertAt, 0, panelId);
            placed[panelId] = true;
        });
        return resolved;
    }

    // Moves one panel up (-1) or down (+1) in the order, returning a new array. A move past
    // either end, or of a panel that isn't in the order, is a no-op, so the caller can wire the
    // buttons up without bounds checks of its own.
    function movePanelInOrder(order, panelId, delta) {
        var moved = (order || []).slice();
        var from = moved.indexOf(panelId);
        if (from === -1) return moved;
        var to = from + delta;
        if (to < 0 || to >= moved.length) return moved;
        moved.splice(to, 0, moved.splice(from, 1)[0]);
        return moved;
    }

    // A panel is visible unless the player has explicitly hidden it, so a panel added later
    // starts out shown rather than inheriting some other panel's preference.
    function isPanelHidden(panelId, hiddenState) {
        return !!(hiddenState && hiddenState[panelId] === true);
    }

    // Moves a visible panel past the next panel the player can actually see, so one press of ▲/▼
    // always produces a visible move even when hidden panels sit in between. Hidden panels travel
    // with the visible panel they sit under, so their place in the sidebar survives the move and
    // showing them again puts them back where they were rather than at the bottom. A hidden panel
    // has nothing to move past on screen, so it moves one step through the full order instead —
    // which is what the layout controls, where every panel is listed, show.
    function movePanelAmongVisible(order, hiddenState, panelId, delta) {
        var current = (order || []).slice();
        if (current.indexOf(panelId) === -1) return current;
        if (isPanelHidden(panelId, hiddenState)) {
            return movePanelInOrder(current, panelId, delta);
        }

        var visible = [];
        var leading = [];
        var followers = {};
        var anchor = null;
        current.forEach(function (id) {
            if (!isPanelHidden(id, hiddenState)) {
                visible.push(id);
                anchor = id;
                return;
            }
            if (anchor === null) {
                leading.push(id);
                return;
            }
            if (!Object.prototype.hasOwnProperty.call(followers, anchor)) followers[anchor] = [];
            followers[anchor].push(id);
        });

        var merged = leading.slice();
        movePanelInOrder(visible, panelId, delta).forEach(function (id) {
            merged.push(id);
            if (!Object.prototype.hasOwnProperty.call(followers, id)) return;
            followers[id].forEach(function (hiddenId) {
                merged.push(hiddenId);
            });
        });
        return merged;
    }

    // Whether a ▲/▼ button should be offered at all: a move that would leave the order untouched
    // is a dead button, so the caller can disable it without repeating the end-of-sidebar rules
    // (which differ for a visible panel and a hidden one).
    function canMovePanel(order, hiddenState, panelId, delta) {
        var current = order || [];
        var moved = movePanelAmongVisible(current, hiddenState, panelId, delta);
        return moved.some(function (id, index) {
            return id !== current[index];
        });
    }

    return {
        summarizeHoldings: summarizeHoldings,
        getStatClass: getStatClass,
        diffCastleMilestones: diffCastleMilestones,
        validateSplitAmount: validateSplitAmount,
        resolvePanelOpenState: resolvePanelOpenState,
        resolvePanelOrder: resolvePanelOrder,
        movePanelInOrder: movePanelInOrder,
        isPanelHidden: isPanelHidden,
        movePanelAmongVisible: movePanelAmongVisible,
        canMovePanel: canMovePanel
    };
});
