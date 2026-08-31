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

    // How far from the cell centre a fanned-out army circle sits, and how much of the usual
    // radius it is drawn at, both as fractions of the cell. A single army keeps the full size at
    // dead centre; a crowd is shrunk further so the ring still fits inside its own cell. The
    // margin is what a fanned circle leaves free at the cell edge, so the selection ring the
    // caller draws just outside a circle has somewhere to go without crossing into the next cell.
    var ARMY_RADIUS_FRACTION = 0.3;
    var ARMY_CELL_MARGIN = 0.06;

    function armyFanScale(count) {
        if (count <= 1) return 1;
        if (count <= 4) return 0.6;
        return 0.42;
    }

    // Places every army for one render pass. Armies sharing a tile were drawn at the exact centre
    // of the cell, one directly on top of another, so a tile read as whichever army happened to be
    // drawn last — which is what a split (parent and child co-located until the next turn) and a
    // fight (attacker and defender on one tile) both leave on screen. Co-located armies are fanned
    // evenly around the cell centre instead, smallest army id first from the top so the
    // arrangement is stable from tick to tick rather than following the order the backend sends.
    //
    // Offsets and radii come back as fractions of the cell's smaller side, so the caller multiplies
    // by one cell size and the fan stays circular on a non-square grid.
    function layoutArmiesOnTiles(armies) {
        var byTile = {};
        var tileOrder = [];
        (armies || []).forEach(function (army) {
            var key = army.x + ',' + army.y;
            if (!Object.prototype.hasOwnProperty.call(byTile, key)) {
                byTile[key] = [];
                tileOrder.push(key);
            }
            byTile[key].push(army);
        });

        var placements = [];
        tileOrder.forEach(function (key) {
            var occupants = byTile[key].slice().sort(function (a, b) { return a.id - b.id; });
            var count = occupants.length;
            var scale = armyFanScale(count);
            var ring = count <= 1
                ? 0
                : 0.5 - ARMY_RADIUS_FRACTION * scale - ARMY_CELL_MARGIN;
            occupants.forEach(function (army, index) {
                var angle = -Math.PI / 2 + (2 * Math.PI * index) / count;
                placements.push({
                    army: army,
                    offsetX: count <= 1 ? 0 : ring * Math.cos(angle),
                    offsetY: count <= 1 ? 0 : ring * Math.sin(angle),
                    radius: ARMY_RADIUS_FRACTION * scale,
                    scale: scale,
                    stackSize: count
                });
            });
        });
        return placements;
    }

    // Resolves a point on the map canvas to the army the player is pointing at, or null when the
    // tile under the point holds no army the caller is interested in. `x` and `y` are in canvas
    // pixels, as the cell sizes are.
    //
    // Resolving to the tile alone picked whichever army the backend listed first, so on a tile
    // holding more than one — a fresh split, or a fight — the others could not be selected or
    // inspected at all, even though each is now drawn as its own circle. The nearest circle wins
    // instead, using the very placements `layoutArmiesOnTiles` gave the renderer, so the army the
    // player is pointing at is the one they are looking at. Candidates are still limited to the
    // tile under the point, so a click anywhere in a cell holding one army selects it exactly as
    // before rather than reaching into a neighbouring cell.
    //
    // `matches` optionally narrows which armies may be picked (the click handler only selects the
    // player's own). The fan is laid out from every army on the tile either way, because that is
    // what is on screen: an enemy army the player cannot select still moves their own circle.
    function findArmyAtPoint(armies, x, y, cellWidth, cellHeight, matches) {
        var gridX = Math.floor(x / cellWidth);
        var gridY = Math.floor(y / cellHeight);
        var cellSize = Math.min(cellWidth, cellHeight);

        var nearest = null;
        var nearestDistance = Infinity;
        layoutArmiesOnTiles(armies).forEach(function (placement) {
            var army = placement.army;
            if (army.x !== gridX || army.y !== gridY) return;
            if (matches && !matches(army)) return;
            var dx = x - (army.x * cellWidth + cellWidth / 2 + placement.offsetX * cellSize);
            var dy = y - (army.y * cellHeight + cellHeight / 2 + placement.offsetY * cellSize);
            var distance = Math.sqrt(dx * dx + dy * dy);
            if (distance < nearestDistance) {
                nearest = army;
                nearestDistance = distance;
            }
        });
        return nearest;
    }

    // The text the canvas tooltip shows for one grid cell, or null when the cell is off the map.
    // `captureTurns` is how many consecutive turns holding a castle takes, which the page owns.
    //
    // An army on the tile is described first, because that is what the player is pointing at. A
    // castle being taken, though, always has the taking army standing on it — `occupationTicks`
    // only ever rises while exactly one player occupies the tile — so the progress is appended to
    // the army's own line rather than left in a castle branch nothing can reach.
    //
    // `hoveredArmy` says which of the armies on the tile is being pointed at, as
    // `findArmyAtPoint` resolves it; without it the first army on the tile is described, which is
    // all a caller holding no pointer geometry can say.
    function getTooltipText(gridX, gridY, gameState, captureTurns, hoveredArmy) {
        if (!gameState || !gameState.grid) return null;
        if (gridX < 0 || gridX >= gameState.width || gridY < 0 || gridY >= gameState.height) return null;

        var tile = gameState.grid[gridX][gridY];

        var army = hoveredArmy && hoveredArmy.x === gridX && hoveredArmy.y === gridY
            ? hoveredArmy
            : (gameState.armies || []).find(function (a) {
                return a.x === gridX && a.y === gridY;
            });
        if (army) {
            var text = 'Army #' + army.id + ' (Player ' + army.playerId + ') \u2014 Soldiers: '
                + army.soldiers + ' | Morale: ' + army.morale + ' | Loyalty: ' + army.loyalty;
            if (army.destinationX !== undefined && army.destinationX !== null) {
                text += ' | Moving to: (' + army.destinationX + ', ' + army.destinationY + ')';
            }
            if (tile.type === 'CASTLE' && tile.occupationTicks > 0) {
                text += ' | Capturing castle: ' + tile.occupationTicks + '/' + captureTurns;
            }
            return text;
        }

        if (tile.type === 'CASTLE') {
            if (tile.occupationTicks > 0) {
                var holder = tile.ownerId === 0 ? 'neutral' : 'Player ' + tile.ownerId;
                return 'Castle \u2014 ' + holder + '. Capture progress: ' + tile.occupationTicks + '/' + captureTurns;
            }
            if (tile.ownerId === 0) {
                return 'Castle \u2014 neutral. Occupy for ' + captureTurns + ' consecutive turns to capture.';
            }
            return 'Castle \u2014 Player ' + tile.ownerId + '. Hold for ' + captureTurns + ' consecutive turns to capture.';
        }

        if (tile.type === 'VILLAGE') {
            if (tile.ownerId === 0) {
                return 'Village \u2014 neutral. Occupy with an army to capture and generate soldiers.';
            }
            return 'Village \u2014 Player ' + tile.ownerId + '. Generating +1 soldier/turn for stationed armies.';
        }

        return 'Empty \u2014 move an army here to occupy.';
    }

    // The interface preferences the game page keeps, and the localStorage key each is kept under.
    // The same names are what a signed-in player's preferences are stored against their account
    // as, so the page can move a preference between the two without a second naming scheme.
    var PREFERENCE_STORAGE_KEYS = {
        settings: 'barony_settings',
        panelLayout: 'barony_panel_layout',
        panelState: 'barony_panel_state'
    };

    // Reconciles the preferences stored against a player's account with the ones this browser
    // holds. What the account holds wins — following the player across browsers and devices is
    // the point of storing them there — while a preference only this browser knows about is kept
    // and reported as needing an upload, so arranging the sidebar before ever signing in (or
    // while the backend was unreachable) isn't thrown away on the next load.
    function mergePreferences(remote, local) {
        var merged = {};
        var uploadNeeded = false;
        Object.keys(PREFERENCE_STORAGE_KEYS).forEach(function (name) {
            var fromAccount = remote ? remote[name] : undefined;
            if (fromAccount !== undefined && fromAccount !== null) {
                merged[name] = fromAccount;
                return;
            }
            var fromBrowser = local ? local[name] : undefined;
            if (fromBrowser !== undefined && fromBrowser !== null) {
                merged[name] = fromBrowser;
                uploadNeeded = true;
            }
        });
        return { preferences: merged, uploadNeeded: uploadNeeded };
    }

    // The state machine that decides *when* a signed-in player's preferences may be sent to their
    // account, kept apart from the reads, writes and requests it drives so the orderings that carry
    // the risk can be tested without a browser. The caller supplies those as callbacks:
    //
    //   readLocal()             the preferences this browser holds, as mergePreferences takes them
    //   writeLocal(prefs)       put the reconciled copy back into this browser
    //   applyPreferences()      re-apply what is now stored to the page
    //   fetchRemote()           a promise for the account copy, or null when there is none
    //   sendRemote(prefs)       upload; expected to handle its own failure
    //   isSignedIn()            whether there is an account to sync against at all
    //   onError(e)              report a failed load
    //   schedule/cancel         setTimeout/clearTimeout, injectable so tests can drive the clock
    //   uploadDelayMs           how long changes are coalesced for before one upload is sent
    //
    // Two rules do the work. Nothing may be uploaded before the account copy has been reconciled,
    // because an upload sent while the load is still in flight would put this browser's copy over
    // the account's and lose the arrangement the response was about to bring back; a change made in
    // that window is remembered and sent once the merge has happened, so it is delayed rather than
    // dropped. And applying the account copy is not itself a change: it writes storage and opens or
    // closes panels, which would otherwise echo straight back up as something the player never did.
    function createPreferenceSync(options) {
        var schedule = options.schedule || function (fn, delay) { return setTimeout(fn, delay); };
        var cancel = options.cancel || function (handle) { clearTimeout(handle); };
        var onError = options.onError || function () {};
        var uploadDelayMs = options.uploadDelayMs === undefined ? 500 : options.uploadDelayMs;

        var uploadTimer = null;
        var applying = false;
        var reconciled = false;
        var uploadDeferred = false;

        function upload() {
            return options.sendRemote(options.readLocal());
        }

        // Coalesces the writes a single change fans out into (a layout change re-saves the order,
        // the visibility and the open/closed state) into one request.
        function queueUpload() {
            if (applying || !options.isSignedIn()) return;
            if (!reconciled) {
                uploadDeferred = true;
                return;
            }
            cancel(uploadTimer);
            uploadTimer = schedule(upload, uploadDelayMs);
        }

        function applyWithoutEcho() {
            applying = true;
            try {
                options.applyPreferences();
            } finally {
                // Opening or closing a panel raises its `toggle` event asynchronously, so the
                // guard has to outlive this call for those echoes to be caught by it.
                schedule(function () { applying = false; }, 0);
            }
        }

        // Opens the gate once the account copy has been dealt with — whether it was applied or the
        // request failed — and sends anything the player changed while it was shut.
        function finishReconciliation(uploadNeeded) {
            reconciled = true;
            if (uploadNeeded || uploadDeferred) {
                uploadDeferred = false;
                upload();
            }
        }

        function load() {
            return options.fetchRemote()
                .then(function (stored) {
                    var merged = mergePreferences(stored, options.readLocal());
                    options.writeLocal(merged.preferences);
                    applyWithoutEcho();
                    finishReconciliation(merged.uploadNeeded);
                })
                .catch(function (e) {
                    onError(e);
                    // The account copy could not be read, so this browser's is all there is; let it
                    // be uploaded rather than leaving the player unable to save at all.
                    finishReconciliation(false);
                });
        }

        return { load: load, queueUpload: queueUpload };
    }

    return {
        summarizeHoldings: summarizeHoldings,
        getStatClass: getStatClass,
        diffCastleMilestones: diffCastleMilestones,
        validateSplitAmount: validateSplitAmount,
        layoutArmiesOnTiles: layoutArmiesOnTiles,
        findArmyAtPoint: findArmyAtPoint,
        getTooltipText: getTooltipText,
        resolvePanelOpenState: resolvePanelOpenState,
        resolvePanelOrder: resolvePanelOrder,
        movePanelInOrder: movePanelInOrder,
        isPanelHidden: isPanelHidden,
        movePanelAmongVisible: movePanelAmongVisible,
        canMovePanel: canMovePanel,
        PREFERENCE_STORAGE_KEYS: PREFERENCE_STORAGE_KEYS,
        mergePreferences: mergePreferences,
        createPreferenceSync: createPreferenceSync
    };
});
