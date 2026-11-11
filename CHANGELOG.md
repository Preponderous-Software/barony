# Changelog

All notable changes to the Barony Prototype MVP are documented in this file.

## [Unreleased]

### Backend

- ✅ **Unit types: archers and cavalry alongside infantry** (ROADMAP §1.2.1): an `Army` keeps
  `soldiers` as its total and gains `archers` and `cavalry` (infantry is the rest, exposed read-only
  as `infantry`). Saves without them load as all infantry. A new `TRAIN` command converts infantry
  one for one, archers at a village the player holds (2 a turn) and cavalry at a castle the player
  holds (1 a turn), worked through over turns as `trainingType`/`trainingRemaining` and dropped if the
  army marches off, loses the tile or runs out of infantry; refusals are `400` or `409`. Every rule
  that shrinks an army (combat, desertion, field results, a retreat with nowhere to go, AI and player
  splits) now goes through `Army.loseSoldiers`, which takes losses across the types in proportion by
  largest remainder (ties: infantry, archers, cavalry; `Army.shareLosses` is the pure rule). Merges
  add the types up, and `SPLIT` takes a proportional share or exact `splitArchers`/`splitCavalry`.
  The map's rule weighs infantry 1.0, archers 1.25 and cavalry 1.5 in exact hundredths, which for an
  all-infantry army is the old strength to the soldier. `PendingBattle` gains `side0Archers`,
  `side0Cavalry`, `side1Archers` and `side1Cavalry`, and a field result may send `survivorsByType`
  (checked against each side's starting mix and its `survivors`, then shared per type). The AI trains
  a modest share when idle on its villages and castles, only in games started since unit types
  (`unitTypes: true`), so an older save's AI plays as it did. `PreUnitTypesSaveTest` loads a save
  written by the previous code and replays it, and six seeded games, against digests recorded on
  that code: an all-infantry game is unchanged bit for bit.

- ✅ **Signed-in players stay signed in**: a session used to end when the one-hour access token did,
  sending the player back to the login page. Login now also keeps UserAuth's refresh token in an
  HttpOnly `barony_refresh` cookie, and `AccountSessionFilter` renews the session on the next
  per-player request, so it lasts until 30 days pass without a visit. `RefreshSessions` makes
  overlapping requests spend the single-use token once, since UserAuth ends a session when a spent
  token is presented. A UserAuth outage gives a 503 and keeps the cookies, so the player is not
  signed out by it, and logging out with only the refresh cookie still revokes the session. The web
  client relays the renewed cookies. Sessions started earlier get a refresh cookie at their next
  login.
- ✅ **A token with an impossible expiry no longer throws**: `JwtExpiry` let `DateTimeException`
  escape when a token's unverified `exp` claim was too large or too small for an `Instant`, so a
  hand-made `barony_token` cookie broke the request instead of being refused. Such a token now
  counts as unexpired like any other unreadable one, and UserAuth rejects it with the usual `401`.
  `JwtExpiryTest` covers the class, which had no tests of its own.
- ✅ **Field battles decide the map** (#140): when player 1's armies end up on a tile with the
  enemy's, the turn now pauses just before combat and records a pending battle (`pendingBattles` in
  the game state: the armies on each side, their soldiers, player 1's morale, the ground and a seed).
  Captures, the win check, desertion and respawns run once every pending battle is resolved.
  `POST /api/session/battle` resolves one, either with the fought result
  (`{"battleId", "result": "FIELD", "winner", "survivors"}`) or by the map's classic rule
  (`"result": "AUTO"`). A result in which a side keeps more soldiers than it started with, or the
  winner has nobody left, is refused with `400`, as is an unknown or already-resolved battle. The
  winner keeps the soldiers still standing; the loser keeps those who got away, falls back to a
  neighbouring tile and stops marching, or is lost with nowhere to go. Survivors are shared among a
  side's armies by size. `POST /api/session/field-battles` (`{"enabled": true|false}`) turns the
  setting on or off, and turning it off settles anything pending by the classic rule. Field battles
  are on by default for players; the engine default is off, so the single-game endpoints play as
  before, and a tick with a battle still pending lets the map decide it first. Splitting an army
  that is in a pending battle returns `409`. Saves only gain fields (`pendingBattles`,
  `nextBattleId`, `fieldBattles`): a save from before this change loads with no pending battles and
  takes the default setting, and a reset keeps the player's choice.
- ✅ **A beaten army can no longer take ground by retreating** (#145): a loser could fall back onto
  an empty enemy village and capture it in the same turn, or start the capture of an empty enemy
  castle. A beaten army now falls back only onto open ground or its own villages and castles, never
  onto an enemy or neutral village or castle; with nowhere allowed to go, it is lost.
- ✅ **A guest's unreadable run history is no longer overwritten** (#146): when a guest's stored run
  history could not be read, it was treated as empty without a log line, and the next finished game
  was written over it. The unreadable history is now kept and logged, and the new run is not
  recorded in that case. The win and loss counts are left alone too, so they stay in step with the
  history, and the run is still recorded on a later save once the history can be read.
- ✅ **An account's unreadable saved game is no longer overwritten**: when a player's stored game
  could not be read, `SessionService` logged it and made a fresh game that was saved over it, so the
  real game was lost. The unreadable save is now left untouched and logged (username and parse
  error, never the save's text), and `UnreadableSaveException` is answered in one place
  (`UnreadableSaveHandler`) with `409 {"error": "unreadable-save", "message", "status"}` from every
  endpoint that needs the game, so no tick, command, reset, battle or setting change can save over
  it, and a guest claim into that account is refused as before. The new
  `POST /api/session/start-fresh` is the only way past it: it copies the unreadable save's exact
  text into the new insert-only `saved_game_backup` table (created by `ddl-auto=update`; the
  `saved_game` table and existing saves are unchanged), then writes a fresh game. With a readable
  save it returns that game unchanged, so a repeated click cannot wipe a game. Guest games are
  unchanged.

### Web Client

- ✅ **Archers and cavalry on the map**: the selected army's panel, its tooltip and the Armies list
  show its mix ("24 soldiers: 16 infantry · 5 archers · 3 cavalry"; an all-infantry army reads as
  before), and the map marks archers with an arrowhead and cavalry with a diamond on the army's
  circle. On a village or castle you hold the panel offers **Train archers** or **Train cavalry**,
  shows the order in progress and has a **Stop training** button, sized for touch at phone width.
  The proxy's models carry the new fields so nothing is dropped on the way to the page.
- ✅ **An unreadable saved game is shown, not replaced**: when the state load answers
  `409 "unreadable-save"`, the game page hides the board and says the saved game couldn't be read
  and has been kept, with a **Start a fresh game** button that asks for confirmation before calling
  the new `POST /api/session/start-fresh` (proxied here like the other session routes). Game actions
  and keyboard shortcuts do nothing meanwhile. The proxy relays that 409's JSON body unchanged so
  the page sees the same answer as behind the gateway. The battlefield falls back to custom battles
  and links back to the map. `isUnreadableSave`, `readUnreadableSave` and `startFreshGame` live in
  `game-logic.js` with Node tests.
- ✅ **Search and link-preview metadata on the landing page**: the login page that
  barony.preponderous.org lands on was titled "Barony Game - Login" and had no description or
  Open Graph tags. It is now titled "Barony" and carries a meta description, a canonical link,
  `og:type`/`og:title`/`og:description`/`og:url` and a summary Twitter card, all on the
  production origin; the register page gets its own title, description and canonical link.
  `ShareMetadataTest` renders both pages and checks the tags.
- ✅ **Link-preview image**: the landing page now carries `og:image` and `twitter:image`
  (`https://barony.preponderous.org/images/og.png`) with its type, width (1200), height (630) and
  alt text, and the Twitter card is `summary_large_image`. The image,
  `web-client/src/main/resources/static/images/og.png`, is a 1200×630 PNG of the name, the existing
  description and the domain in the site's colours. `ShareMetadataTest` checks the tags and that
  `/images/og.png` is served as a 1200×630 PNG.
- ✅ **"Exit guest game" names the right button**: the notice shown on the login page after leaving a
  guest game said to choose "Play as guest", but with a guest game saved that button reads "Continue
  guest game". The notice now names that label, and `GuestExitWordingTest` reads both pages to keep
  the two in step.
- ✅ **Army destinations and castle capture progress are drawn on the map** (#89): both were only
  reachable by hovering. The tile each of your moving armies is headed for is now marked with a
  faint fill and a dashed outline in your army colour, until an army arrives there; the enemy's
  destinations are not shown. A castle part-way through being captured gets a progress bar along
  the bottom of its tile, filled a third per turn of occupation in the capturing side's colour, so
  the enemy's sieges on your castles are as visible as yours on theirs. Both follow the colourblind
  palette. Which tiles are marked and how full each bar is come from `listArmyDestinations` and
  `listCaptureProgress` in `game-logic.js`, covered by the Node test suite; the canvas drawing
  itself has no automated coverage.
- ✅ **Site footer backlink**: every page (login, register and the game) now ends with a small, muted "More by Daniel Stephenson → danielstephenson.dev" line, served from one shared Thymeleaf fragment (`templates/fragments/footer.html`) and covered by `SiteFooterTest`.
- ✅ **3D battlefield** (#139): any engagement can be fought out as a 3D field battle at `/battle`,
  drawn by the [Strata](https://github.com/Preponderous-Software/strata) engine in WebGL 2, with one
  figure per soldier up to 90 a side and one shared scale above that. Watch from an orbit camera at
  ½× to 4× speed, take over any soldier in third or first person (keyboard and mouse, or a stick and
  Strike/Shield buttons on a phone), or enter as your own avatar, saved with the account's
  preferences (a guest's stays in the browser). The engine is a thin C++ host built with Emscripten
  against Strata's pinned v0.7.0 wasm release; the outputs are committed, and CI rebuilds them and
  fails if the bytes differ. The battle itself is plain JavaScript, tested under Node.
- ✅ **The game page takes the player to a waiting battle** (#140): after a turn that leaves a battle
  pending, the page opens `/battle?pending=<id>` and stops Auto Play, and a banner above the map
  offers **Fight it in 3D** or **Let the map decide**. The battle page locks a pending battle to the
  real armies, ground and seed, posts the result when it ends, and offers **Next battle** when the
  turn brought on more than one. **Fight battles in 3D** in Settings turns field battles off. The
  web client proxies the two new endpoints and relays the new state fields.
- ✅ **The avatar cannot hold the field alone** (#141): when every soldier of a side had fallen and
  only the player's avatar stood, that side won and the result lifted it to one surviving soldier,
  so an army wiped out on the field survived on the map. Only soldiers now decide who holds the
  field: once all of a side's soldiers have fallen or fled, that side has lost, even if the avatar
  still stands. If the beaten side's avatar cuts down the winner's last soldier during the pursuit,
  the battle ends as a draw, so the map is never sent a winner with nobody left.
- ✅ **The field-battle wiring is covered by the web client's tests** (#143): the proxy-route check
  now also reads the battle page's script, so its API calls must have a proxy; the two field-battle
  proxies (`POST /api/session/battle` and `POST /api/session/field-battles`) have forwarding tests;
  and the relayed game state is checked to keep `pendingBattles`, `nextBattleId` and
  `fieldBattles`.
- ✅ **The selected army's 3D link says whether it counts** (#144): it read **⚔ Fight in 3D**, almost
  the banner's **⚔ Fight it in 3D**, but opened a practice battle that changes nothing. It now reads
  **⚔ Practice in 3D**. While the selected army is in a battle waiting this turn, it reads
  **⚔ Fight it in 3D** instead and opens that battle. `BattlePageTest` checks both.

### Usage reporting

- The usage-reporting "Details" link (startup notice, docs and config comments) now points at https://danielstephenson.dev/usage-reporting, a public page, instead of a link into a private repository that answered 404. The vendored `TraceClient` is trace-client-java 0.6.1, whose server-wide switch file comment carries the same link.
- ✅ The **web client reports page views** to the [trace](https://danielstephenson.dev/usage-reporting) usage service: one `page-view` event per HTML page served, carrying only the request path (`/login`, `/register`, `/game` — no query string, capped at 200 characters) and the web client's version. Nothing about the visitor is recorded — no IP address, user agent, cookie, session, account or referrer — so the result is a count of page loads, not of visitors. API calls, static assets, redirects, error pages and requests from crawlers, monitors and scripted clients are not counted. The web client also now sends the same one `startup` event the backend does (program name `barony`, version, `service=true`). Reports are queued to the client's own daemon thread and never slow a request; a trace server that is down costs nothing beyond a dropped report. Configured through `usage-reporting.enabled` / `usage-reporting.endpoint` / `usage-reporting.key` in `web-client/src/main/resources/application.yml` (the same key the backend uses), each overridable by environment variable; `USAGE_REPORTING_ENABLED=false` turns page views and the startup event off together. The client is [trace-client-java](https://github.com/Stephenson-Software/trace-client-java) vendored as one file under `com.barony.webclient.trace`; which requests count is decided by `PageViewPolicy` and covered by unit tests plus an end-to-end test against a loopback stub.

### Gameplay

- ✅ **MOVE commands can no longer redirect the enemy's armies** (#112): `POST /command` and
  `POST /api/session/command` accepted a `MOVE` naming one of the AI's armies and set its
  destination. A `MOVE` for an army you do not own is now ignored, as `SPLIT` already was. The web
  client only ever lets you select your own armies, so this only closes the direct-API path.

### Documentation

- ✅ `PLAYER_GUIDE.md` no longer describes the map as a fixed 10x10 grid with the enemy castle at
  (9,9). `MapGenerator` picks each side at random between 10 and 20 tiles for every game, with the
  castles at opposite corners, so the guide now says that, and its "Armies won't move"
  troubleshooting tip no longer tells players to keep moves within a 10x10 grid.
- ✅ `README.md`, `PLAYER_GUIDE.md` and `MVP.md` no longer say a village generates a flat +1
  soldier per turn. `PolicyService` generates one soldier per 100 of the village's population
  (scaled by stability for Player 1), and Player 1's villages grow about 1% a turn, so generation
  rises over a game. The docs now say that, and `MVP.md` drops a population cap that does not
  exist.
- ✅ `PLAYER_GUIDE.md` no longer says a captured village "immediately" generates soldiers.
  `GameService.tick` generates soldiers before it settles village ownership, so a village first
  pays out on the turn after it is taken. The guide also now documents three rules it left out:
  each side starts with one 10-soldier army on its castle (`MapGenerator`); armies walk the
  horizontal distance first and then the vertical, never diagonally (`moveOneStep`); and a side
  that ends a turn with no armies gets a fresh 1-soldier army on one of its castles
  (`spawnArmyIfNoneRemaining`), so losing every army is not a defeat.
- ✅ `PLAYER_GUIDE.md` now says the 15-turn policy cooldown is shared by all three categories.
  `GameState` keeps a single `lastPolicyChangeTick`, so a change in one category locks the other
  two. Re-applying the policy already in force restarts the cooldown, and the first change is
  available from turn 0. `README.md` now lists the valid `category`/`choice` values for
  `POST /api/decision` and the `400`/`409` responses it returns.
- ✅ `PLAYER_GUIDE.md` no longer says a move can't be undone. A new `MOVE` replaces the army's
  destination, and a `MOVE` to the tile the army stands on clears it (`executeMoveCommand`); in the
  web client that is selecting the army and clicking another tile or its own. The "Friendly Armies
  Merge" tips now also say when merging happens (each turn, right after movement, so an unmoved
  split rejoins its parent) and what the combined army keeps (`CombatService.mergeFriendlyArmies`):
  the lowest-numbered army's ID, morale and loyalty, and its destination, or failing that one from
  an absorbed army. `CombatServiceTest` now pins the morale and loyalty part.
- ✅ **`demo-api.sh` plays the game as it now works** (#132): the script moved the AI's army, which
  has been ignored since #112, and assumed instant movement, a village at (3,3) and a fight after
  two turns. It now starts a new game, reads player 1's army and the nearest village from the state,
  advances turns until the army arrives, and takes one more turn to show the capture and the
  soldiers recruited. `README.md` now describes it.
- ✅ `README.md`'s authentication docs now match the refresh-token flow. An expired token is renewed
  rather than refused when a refresh cookie is present, a UserAuth outage answers `503`, and the
  auth cookies are `Secure` only when `AUTH_COOKIE_SECURE` is set. It also now documents that
  setting, `AUTH_REFRESH_COOKIE_DAYS`, the auth and `/api/guest/claim` error responses, and the JSON
  returned by the game endpoints, which `DOCS.md` already promised and a pointer to "technical
  sections below" never delivered. `MVP.md`'s API and model listings now show what was built rather
  than endpoints and fields that never landed, and no longer say the player is prompted every 15
  ticks.
- ✅ `PLAYER_GUIDE.md` now explains that policies take effect gradually. `PolicyService` moves each
  stat a fixed step per turn toward the policy's target: stability 2, morale 1 and loyalty 2. A
  policy change therefore takes several turns to show. The guide also says that policies apply only
  to the player's side: the AI's armies fight at their raw soldier count and never desert, and its
  villages do not grow. `MVP.md` no longer says morale and loyalty always return to 100%. They settle
  at the military policy's target, which is 75% loyalty under Aggressive Training.
- ✅ **The docs describe field battles** (#142): `README.md` lists `POST /api/session/battle` and
  `POST /api/session/field-battles` with the per-player endpoints, documents `pendingBattles`,
  `nextBattleId` and `fieldBattles` in the response structure along with a turn that returns paused
  on a pending battle, and its rules overview says battles are fought on the 3D battlefield by
  default. `PLAYER_GUIDE.md`'s **Reading the Interface** now covers the battle banner and the
  selected army's practice / fight link. Both describe the retreat rule from #145 and the avatar
  rule from #141, and use the link naming from #144.

## [2.0.0-SNAPSHOT-8-8-2026] – 2026-08-08

### Changed
- barony is now developed AI-first. Day-to-day feature work, grooming, review and maintenance run through AI agents working directly against this repository, with the maintainers setting direction and approving what lands. The major version bump marks that change in how the project is built — it is not a break in behaviour, configuration or stored data, and existing installations can upgrade in place. Released as `2.0.0-SNAPSHOT-8-8-2026`: the AI-first line has not yet been verified in live operation, and the dated snapshot designation stays until it has.

### Web Client

- ✅ **Every army on a shared tile can be clicked and hovered** (#96): the circles were drawn apart
  (#90), but a click or a hover was still resolved to the tile, which answered with whichever army
  the backend listed first. The newly split-off half of an army could therefore not be selected or
  inspected at all until the next turn moved it away, and neither could the defender in a fight.
  A click or hover now picks the nearest of the circles actually drawn in the cell, so aiming at an
  army selects and describes that army; a tile holding one army still answers to a click anywhere
  in the cell, exactly as before. Selection stays limited to the player's own armies, while the
  tooltip describes whichever army is pointed at, including the enemy's. The resolution rule
  (`findArmyAtPoint` in `game-logic.js`) is covered by the Node test suite.
- ✅ **Armies sharing a tile are all visible** (#90): co-located armies were drawn at the exact
  centre of the cell, one on top of another, so a tile showed whichever army happened to be drawn
  last — which is what the player sees after a split (parent and child sit together until the next
  turn) and during a fight (attacker and defender share the tile). Armies on one tile are now drawn
  smaller and fanned evenly around the centre of the cell, ordered by army id so the arrangement
  does not reshuffle from turn to turn. The gold selection ring is drawn a little closer to its
  circle to suit, and its gap now scales with the cell rather than sitting at a flat 4px, so it
  stays inside the cell on the smaller grid the mobile layout uses. The placement rule
  (`layoutArmiesOnTiles` in `game-logic.js`) is covered by the Node test suite.
- ✅ **Castle capture progress is readable while a capture is happening** (#94): the tooltip's
  `Capture progress: N/3` line could not be reached in play. It was only offered for a tile with no
  army on it, and a capture in progress always has the capturing army standing there — the backend
  zeroes the counter the moment the tile is empty, contested or held by its owner. The progress is
  now appended to the occupying army's own tooltip (`… | Capturing castle: 2/3`), and a castle that
  still carries progress with nobody on it reports it whether it is owned or neutral. The tooltip
  text moved into `game-logic.js` and is covered by the Node test suite.
- ✅ **Interface preferences follow the account** (#55): the display settings and the sidebar
  arrangement — order, which panels are shown, and which are open — are now stored against the
  signed-in player's account through a new `GET`/`PUT /api/session/preferences`, so signing in on
  another browser or device brings the arrangement along instead of starting from the defaults.
  `localStorage` stays the working copy: it is seeded from the account copy on load and receives
  every change afterwards, so an unreachable backend costs nothing beyond the arrangement not
  travelling. Where the two copies differ the account's wins; a preference the account has never
  held — chosen before signing in, or while the backend was down — is uploaded rather than
  discarded. The rule for reconciling the two copies (`mergePreferences`) and the gate deciding
  when an upload may be sent (`createPreferenceSync`) both live in `game-logic.js` and are covered
  by the Node test suite.
- ✅ **The Run History panel is filled in again** (#82): the game page's request for the player's
  win/loss tally and recent runs was made against the web client, which had no route for it, so it
  was answered with a 404 and the panel stayed empty wherever `/api/*` is not routed straight to
  the backend — including the documented `docker-compose` / `localhost:3000` setup. `GET
  /api/session/runs` is now proxied like every other per-player endpoint, forwarding the auth
  cookie and passing the backend's status back so a 401 still triggers the re-login flow. A new
  `ProxyRouteCoverageTest` reads the API calls out of every rendered page and fails if any of them
  has no route on the web client, so the same gap cannot reopen for the next endpoint either.
- ✅ **Sidebar moves always move something on screen** (#80): the ▲/▼ buttons now move a shown
  panel past the next panel the player can see, instead of swapping it with a hidden panel and
  looking like nothing happened. Hidden panels travel with the shown panel they sit under, so
  showing one again puts it back where it was rather than at the bottom of the sidebar, and a
  ▲/▼ is disabled once the panel reaches the top or bottom of what is shown. A hidden panel still
  moves one place at a time in the layout list, so it can be positioned before being shown. The
  rules are pure functions (`movePanelAmongVisible`, `canMovePanel` in `game-logic.js`) covered by
  the Node test suite.
- ✅ **The sidebar can be rearranged** (progress on #55): a **Panels** block in the Settings panel
  lets each sidebar panel be shown or hidden with a checkbox and moved with ▲/▼ buttons, plus a
  **Reset Panel Layout** button that restores the default arrangement. The chosen order and
  visibility are remembered per browser (`barony_panel_layout`) and applied before first paint.
  Panels are moved in the DOM rather than restyled with CSS `order`, so keyboard tab order follows
  what is on screen; as a side effect the Run History panel now sits under Game Status as
  documented, instead of above it. The Settings panel cannot be hidden, as it holds these
  controls. The order/visibility rules are pure functions (`resolvePanelOrder`,
  `movePanelInOrder`, `isPanelHidden` in `game-logic.js`) covered by the Node test suite.
  Persisting the layout server-side per account is still open (see #55).
- ✅ **Sidebar panels remember their open/closed state** (progress on #55): each collapsible
  panel (Game Status, Run History, Change Policy, Settings, Armies) now persists whether the
  player left it open or collapsed, per browser, instead of resetting to the defaults on every
  reload. The open/closed decision is a pure function (`resolvePanelOpenState` in
  `game-logic.js`) covered by the Node test suite.
- ✅ **Run history across games** (#70): finishing a run now writes a durable record (result, turns
  played, castles/villages held, armies and soldiers remaining) that survives a reset and a backend
  restart, via a new `GET /api/session/runs` endpoint. The sidebar's new Run History panel shows the
  player's win/loss tally and their recent runs.
- ✅ **Keyboard shortcuts** for the most common actions (#63): `Space` advances a turn, `R` resets
  the game, `S` jumps focus to the split-amount box for the selected army, `A` toggles Auto Play,
  and `Escape` deselects the current army (same as right-click). Ignored while typing in a form
  field or with a modifier key held, so browser/input shortcuts still work.
- ✅ **Castle objective progress** in the Game Status panel (progress toward #53): shows how many
  castles you control, how many the enemy controls, and how many are neutral, derived from the
  game state already sent to the client (no extra request).
- ✅ **End-of-game run summary** (#53): the game-over banner now reports turns played, castles and
  villages held out of the map total, and the armies and soldiers you finished with, instead of a
  bare win/lose line. Every figure is derived from the final game state, so it costs no extra
  request.
- ✅ **Milestone notifications** (#53): capturing or losing a castle raises a toast with the
  updated castle count, and a siege on the last castle of either side counts down the turns until
  it flips. Nothing is announced on first load or after a reset. Its castle-diffing logic is now
  covered by an automated test (see below, #71) rather than by a rendered-markup test plus manual
  play alone.
- ✅ **Game page logic now has automated tests** (#71): the holdings/castle-count arithmetic, the
  stat-class thresholds, the castle-capture diffing behind milestone toasts, and the army
  split-amount validation are extracted from the inline `<script>` into
  `web-client/src/main/resources/static/js/game-logic.js` and exercised by a Node test suite
  (`web-client/src/test/js/game-logic.test.js`), run in CI. This is a refactor only — the game
  page's behaviour is unchanged; option 2 from #71 (a headless-browser test of the click/render
  paths) is still open.

### Gameplay

- ✅ **Army desertion actually happens now** (#60) — it was inert in every reachable game state: the only policy that lowered loyalty targeted 95%, and `(100 - 95) / 20` truncated to a 0% desertion rate for any army size
- ✅ Desertion is tracked in basis points with the fraction under one soldier carried between turns, so the documented `(100 - loyalty) / 20`% rate bites on the few-dozen-soldier armies the game actually produces instead of rounding to nothing
- ✅ Restoring an army to 100% loyalty clears whatever desertion was still pending
- ✅ **Aggressive Training is now a real trade-off**: loyalty modifier changed from -5% to -25%, putting its target at 75% — inside the "armies may lose soldiers" band the player guide has always described
- ✅ A totally disloyal army can finally shrink to nothing and be removed; previously that path was unreachable
- ✅ **Split armies now inherit the parent's morale and loyalty** instead of arriving as fresh recruits at 100/100 — otherwise splitting would be a free one-click reset of a disloyal army's desertion

### Documentation

- ✅ `PLAYER_GUIDE.md` no longer sends players looking for two indicators the map never draws (#88):
  neither the "light blue square" marking an army's destination nor the "red progress bar" over a
  castle being captured exists. An army's destination is read off its hover tooltip, so the guide
  points there; a castle's capture progress is reachable nowhere at all while the capture is under
  way, because the occupying army's tooltip shadows the castle's (#94), so the guide now says to
  count the turns and notes the siege toasts that cover either side's last castle. The **Ruler
  Stats** warning thresholds were wrong too (below 70% for stability, below 80% for morale and
  loyalty); all three stats use the same bands the page applies and `README.md` already documented
  — green at 90 and above, amber from 70 to 89, red below 70. Drawing the missing indicators is
  tracked on #89.
- ✅ `PLAYER_GUIDE.md` now documents the controls the web client actually has (Advance Turn / Reset Game / Auto Play buttons, the split panel under the map, the policy dropdowns) instead of keyboard shortcuts that were never implemented
- ✅ "Reading the Interface" rewritten to match the real layout (sidebar panels, selected-army panel) — the old top bar, side panel, and event log it described don't exist
- ✅ Removed the `[Unreleased]` bullet that said the auth token is stored client-side; it contradicted the **Security** entries above it (#61)
- ✅ `MVP.md`'s "Out of Scope" list no longer flatly says save/load doesn't exist (#53) — games *are* saved per account and restored across restarts; what's still missing is player-managed save slots
- ✅ `MVP.md`'s toast-notification and game-over descriptions now match what the web client actually reports

### Persistence

- ✅ Each player's game is now **saved per account and restored after backend restarts/redeploys** instead of being held only in memory (and lost on every restart)
- ✅ Storage is an embedded **H2** database persisted to `./data` (mount as a volume in production); `DB_URL` switches it to Postgres
- ✅ Game state is serialized as JSON; a fresh game is persisted on creation and re-saved after every turn, command, reset, and policy change
- ✅ Army id counter advances past restored armies on load, so a split after reload can't reuse an existing id
- ✅ Each player's **interface preferences are saved per account** (#55) in a new `user_preferences` table, alongside their saved game; the payload is stored as opaque JSON so a preference added to the game page needs no backend change, capped at 8192 characters so an account can't be used as unbounded storage

### Usage reporting

- ✅ The backend reports **one `startup` event** to the [trace](https://github.com/Stephenson-Software/trace) usage service once it is ready, carrying only the program name (`barony`), the backend version and the tag `service=true` — nothing per request, and nothing about players, accounts, saved games or the host. The send runs on the client's own daemon thread, never throws, and a trace server that is down costs nothing beyond a dropped report. Configured through `usage-reporting.enabled` / `usage-reporting.endpoint` / `usage-reporting.key` in `backend/src/main/resources/application.properties`, each overridable by environment variable; `USAGE_REPORTING_ENABLED=false` turns it off. The client is [trace-client-java](https://github.com/Stephenson-Software/trace-client-java) vendored as one file under `com.barony.backend.trace`.

### Security

- ✅ Auth token moved from `localStorage` into an **HttpOnly, Secure, SameSite=Lax cookie** (`barony_token`), so browser JavaScript can no longer read it and an XSS can't exfiltrate the session (#46)
- ✅ Login sets the cookie and no longer returns the JWT in the response body; logout revokes the token and clears the cookie
- ✅ Backend reads the token from the cookie (with a `Bearer` header fallback for CLI/API clients); the web client transparently forwards the cookie and relays `Set-Cookie`

### Authentication (UserAuth integration)

- ✅ Player accounts via the standalone [UserAuth](https://github.com/Preponderous-Software/UserAuth) service (registration, login, logout)
- ✅ Register and login screens in the web client; logout revokes the token server-side
- ✅ Login issues a signed JWT via UserAuth, which the backend validates on every authenticated game request (see **Security** above for how the token is carried)
- ✅ Backend proxies `/api/auth/register`, `/api/auth/login`, `/api/auth/logout` to UserAuth and validates the token on every authenticated request
- ✅ Per-player game endpoints (`/api/session/*`) reject missing, invalid, expired, or revoked tokens with `401`
- ✅ Game state is keyed by the authenticated username instead of an anonymous session id
- ✅ `docker-compose` now starts UserAuth and its Postgres alongside Barony (configurable via `JWT_SECRET`, `USERAUTH_PATH`, `ALLOWED_ORIGINS`)

## [MVP v1.0.0] - 2026-02-11

### Core Game Features

#### Army Movement & Pathfinding
- ✅ Armies move 1 tile per tick toward destinations using Manhattan distance pathfinding
- ✅ Horizontal movement prioritized first, then vertical movement
- ✅ Movement visualization with destination indicators (light blue for Player 1, light red for Player 2)
- ✅ Movement cancellation via new MOVE commands
- ✅ Stable army identification using unique IDs (AtomicInteger-based)

#### Army Management & Composition
- ✅ Army splitting: divide armies into multiple units with SPLIT command
- ✅ Automatic army merging: friendly armies at same location merge automatically
- ✅ Minimum army size validation (1 soldier minimum per army)
- ✅ Visual soldier count display with dot patterns (up to 10 dots shown)
- ✅ Multiple army rendering with circular offsets

#### Territory Control & Village Mechanics
- ✅ Village ownership system (neutral, Player 1, or Player 2 owned)
- ✅ Persistent ownership: villages retain ownership until captured
- ✅ Village capture occurs after combat resolution
- ✅ Contested villages become neutral if multiple players occupy after combat
- ✅ Color-coded villages: blue tint (Player 1), red tint (Player 2), brown (neutral)
- ✅ Income system: owned villages generate +1 soldier/tick for armies on village

#### Castle Capture & Win Conditions
- ✅ Castle ownership system with initial Player 1/Player 2 assignments
- ✅ Castle capture requires 3 consecutive ticks of enemy occupation
- ✅ Capture progress tracking with visual progress bars
- ✅ Victory condition: capture all enemy castles
- ✅ Defeat condition: lose all your castles
- ✅ Game over state with win/loss overlay
- ✅ Game reset functionality via POST /api/reset endpoint

#### AI Opponent System
- ✅ Rule-based AI controlling Player 2 armies
- ✅ Priority-based decision making:
  1. Defend owned villages from nearby threats (within 3 tiles)
  2. Capture neutral villages (nearest first, if safe)
  3. Attack weak enemy positions (1.5x force multiplier required)
  4. Attack enemy castle (2x force multiplier required)
  5. Build up forces if no better action available
- ✅ Strategic validation: AI never makes suicidal attacks
- ✅ Multi-army coordination for simultaneous objective captures
- ✅ AI enabled by default with configurable difficulty

#### Ruler Decision System (CK-Lite Layer)
- ✅ Three policy categories with three options each:
  - **Economic Policies**: Heavy Taxation (+20% income/-10% stability), Balanced Budget (neutral), Infrastructure Investment (-10% income/+10% stability)
  - **Military Policies**: Aggressive Training (+10% morale/-5% loyalty), Standard Service (neutral), Veteran Benefits (-10% morale/+10% loyalty)
  - **Population Policies**: Growth Focus (+15% growth/-5% stability), Stable Population (neutral), Quality Over Quantity (-10% growth/+10% stability)
- ✅ Village stats: stability (affects soldier generation), population (affects generation capacity)
- ✅ Army stats: morale (affects combat effectiveness), loyalty (affects desertion rate)
- ✅ Gradual stat recovery/decay: stability (±2/tick), morale (±1/tick), loyalty (±2/tick)
- ✅ Policy cooldown: 15-tick minimum between changes
- ✅ Player 1 exclusive: AI does not use ruler decisions
- ✅ Policy UI with keyboard controls (P to open, E/M/O for categories, 1-3 for choices)

### User Interface & Experience

#### Mouse Controls
- ✅ Left-click army selection with glowing highlight effect
- ✅ Left-click tile (with army selected) to move army
- ✅ Right-click to deselect army
- ✅ Hover tooltips with 500ms delay showing tile/army information

#### Visual Feedback
- ✅ Pulsing selection highlights (blue for Player 1, orange for contested)
- ✅ Movement preview: faint ghost circle at destination
- ✅ Capture progress bars on contested castles (red fill)
- ✅ Color-coded ownership indicators for all tiles

#### HUD Elements
- ✅ Top bar: tick count, armies, castles, villages, income per tick (with proportional bars)
- ✅ Side panel: selected army details (ID, player, soldiers, position, destination)
- ✅ Bottom bar: game event log showing last 10 actions
- ✅ Ruler stats panel: displays current policies, cooldown, stability, morale, loyalty, population

#### Keyboard Controls
- ✅ SPACE - Advance game by one tick
- ✅ M - Move first army to (5,5)
- ✅ 1-4 - Move first army to preset locations
- ✅ S - Split army (interactive console input)
- ✅ P - Open policy menu
- ✅ R - Reset game (when game over)
- ✅ ESC - Close window

### Backend API

#### REST Endpoints
- ✅ GET /state - Retrieve current game state
- ✅ POST /tick - Advance game by one tick
- ✅ POST /command - Send MOVE or SPLIT command
- ✅ POST /api/reset - Reset game to initial state
- ✅ POST /api/decision - Change ruler policy (with cooldown)
- ✅ GET /api/ruler-stats - Get realm statistics

#### Game Mechanics
- ✅ Thread-safe game state management with synchronized access
- ✅ Integer-based combat with simultaneous damage
- ✅ Village soldier generation based on population and stability
- ✅ Combat strength modified by morale (Player 1)
- ✅ Desertion mechanics based on loyalty (Player 1)
- ✅ Population growth system (1% base per tick, policy-modified)

### Technical Features

#### Web Client
- ✅ Spring Boot + Thymeleaf web application
- ✅ HTML5 Canvas rendering
- ✅ RESTful backend communication
- ✅ UTF-8 encoding for all network communication

#### Backend
- ✅ Spring Boot REST API (Java 17)
- ✅ In-memory game state (stateless server)
- ✅ Defensive copying for thread safety
- ✅ CORS configuration for localhost origins
- ✅ Comprehensive test suite with unit and integration tests

### Testing

#### Test Coverage
- ✅ Backend: Model tests, game service tests, and integration scenarios
- ✅ Web Client: Tests for all game entities
- ✅ All tests passing

#### Test Coverage
- ✅ Army movement and pathfinding
- ✅ Combat mechanics and army removal
- ✅ Village soldier generation with ownership
- ✅ Castle capture with 3-tick timer
- ✅ Win/loss conditions
- ✅ Army splitting and merging
- ✅ Ruler policy effects and stat recovery
- ✅ AI decision making

### Documentation
- ✅ Comprehensive README.md (745 lines)
- ✅ Detailed MVP.md planning document (568 lines)
- ✅ Policy UI guide (POLICY_UI_GUIDE.md)
- ✅ Quick start scripts (Unix and Windows)
- ✅ CI/CD pipeline with GitHub Actions

### Balance Parameters

#### Soldier Generation
- Base generation: population / 100 (villages with pop < 100 generate 0/tick)
- Player 1 modifier: (base * stability + 50) / 100
- Player 2 (AI): base generation only

#### Castle Capture
- Timer: 3 consecutive ticks of enemy occupation
- Progress resets if: multiple players present or friendly army occupies

#### AI Force Multipliers
- Castle attack threshold: 2x enemy force
- Village attack threshold: 1.5x enemy force
- Defensive range: 3 tiles

#### Stat Recovery/Decay Rates
- Stability: ±2 points/tick toward policy-modified target (max 110)
- Morale: ±1 point/tick toward policy-modified target
- Loyalty: ±2 points/tick toward policy-modified target (max 110)
- Population growth: 1% base/tick, modified by policy (±15%, -10%, 0%)

#### Desertion Rate
- Formula: max(0, (100 - loyalty) / 20)%
- Loyalty ≥ 100 results in 0% desertion
- Applied per tick to army soldier counts

#### Combat
- Base: 1 soldier kills 1 enemy soldier (simultaneous damage)
- Morale modifier (Player 1): strength = (soldiers * morale + 50) / 100

### Performance Characteristics
- 60 FPS rendering target
- Efficient O(n) army merging with grouped processing
- Cached HUD statistics to avoid per-frame recalculation
- Batched text rendering for optimal performance
- Optimized grid rendering with minimal state changes

### Known Limitations
- Single-player only (no multiplayer/networking)
- No save/load functionality (in-memory state only)
- No fog of war or limited visibility
- No terrain effects (mountains, rivers, etc.)
- No sound effects or music
- Console input for army splitting (blocks render loop temporarily)
- Headless environments require X11/Wayland for UI rendering
- No advanced AI difficulty levels (single balanced difficulty)

### Out of Scope (Future Versions)
This MVP explicitly excludes:
- ❌ Multiple unit types (knights, archers, etc.)
- ❌ Technology/research tree
- ❌ Dynasties, succession, or character development
- ❌ Diplomacy system (alliances, treaties)
- ❌ Narrative events or story elements
- ❌ Religion or culture systems
- ❌ Advanced graphics (animations, particles, 3D models)
- ❌ Modding support
- ❌ Localization

---

## Development Notes

### Repository
- GitHub: dmccoystephenson/barony-prototype
- License: Stephenson-NC (See LICENSE.md)
- Java Version: 17
- Build Tool: Maven 3.x with wrapper included

### Quick Start
```bash
# Backend
cd backend && ./mvnw spring-boot:run

# Web Client (separate terminal)
cd web-client && ./mvnw spring-boot:run
```

### Contributing
This is an MVP prototype. See TICKETS.md for planned enhancements.

---

**Last Updated:** 2026-02-11
