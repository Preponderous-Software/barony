/* battle-avatar.js — the player's own avatar for the 3D battlefield.
 *
 * Kept with the player's other preferences under the name `avatar` (game-logic.js lists it in
 * PREFERENCE_STORAGE_KEYS), so a signed-in player's avatar follows their account and a guest's
 * stays in this browser. The stored value comes back from the server as the player last saved it,
 * so clean() is the one place that decides what a valid avatar is: anything unknown or out of
 * range falls back to the default rather than reaching the renderer.
 */
(function (root) {
    'use strict';

    var HELMETS = ['kettle', 'greathelm', 'plume', 'crown', 'hood', 'none'];
    var WEAPONS = ['sword', 'spear', 'axe', 'hammer'];
    // Tunic and trim colours offered in the editor (any #rrggbb is accepted from storage).
    var SWATCHES = ['#1f4fbf', '#b3261e', '#2e7d32', '#6a1b9a', '#e0a100', '#263238', '#eceff1', '#8d5524'];
    var NAME_MAX = 20;

    var DEFAULT = { name: 'Your avatar', tunic: '#6a1b9a', trim: '#e0a100', helmet: 'crown', weapon: 'sword', shield: true };

    function isColour(v) { return typeof v === 'string' && /^#[0-9a-fA-F]{6}$/.test(v); }

    function cleanName(v) {
        if (typeof v !== 'string') return DEFAULT.name;
        // Printable text only, single spaces, trimmed and capped.
        var s = v.replace(/\s+/g, ' ').replace(/[\u0000-\u001f\u007f]/g, '').trim().slice(0, NAME_MAX);
        return s || DEFAULT.name;
    }

    function clean(a) {
        a = a && typeof a === 'object' ? a : {};
        return {
            name: cleanName(a.name),
            tunic: isColour(a.tunic) ? a.tunic.toLowerCase() : DEFAULT.tunic,
            trim: isColour(a.trim) ? a.trim.toLowerCase() : DEFAULT.trim,
            helmet: HELMETS.indexOf(a.helmet) >= 0 ? a.helmet : DEFAULT.helmet,
            weapon: WEAPONS.indexOf(a.weapon) >= 0 ? a.weapon : DEFAULT.weapon,
            shield: typeof a.shield === 'boolean' ? a.shield : DEFAULT.shield
        };
    }

    var api = { HELMETS: HELMETS, WEAPONS: WEAPONS, SWATCHES: SWATCHES, NAME_MAX: NAME_MAX, DEFAULT: DEFAULT, clean: clean };
    if (typeof module === 'object' && module.exports) module.exports = api;
    else root.BattleAvatar = api;
})(this);
