# Releasing Barony

Releases are made by the fleet's release automation. The arcade is separate: every push to `main`
keeps deploying to the arcade exactly as before, whether or not a release is made.

## Versions and tags

- The version lives in two files, which must always agree:
  - `backend/pom.xml` (the project `<version>`)
  - `web-client/pom.xml` (the project `<version>`)
- Tags are the bare version, `X.Y.Z` (for example `1.0.1`), annotated, on a commit of `main` whose
  poms carry that version.
- The automation opens and merges a pull request that sets both poms to the new version, checks
  the merge commit (CI green, a dry run of `release.yml`, the save checks below), then pushes the tag.

## What a tag builds

`.github/workflows/release.yml` runs on a pushed `X.Y.Z` tag. It checks that both pom versions equal
the tag, builds and tests the backend and the web client with JDK 17 (`mvn -B package`), and attaches
to the GitHub release `Barony X.Y.Z` (creating it if it does not exist yet):

- `barony-backend-X.Y.Z.jar` and `barony-backend-X.Y.Z.jar.sha256`
- `barony-web-client-X.Y.Z.jar` and `barony-web-client-X.Y.Z.jar.sha256`

Each `.sha256` is in `sha256sum` format (`<hex>  <filename>`); check a download with
`sha256sum -c barony-backend-X.Y.Z.jar.sha256`.

Run it by hand (Actions, Release, Run workflow) with an existing tag to rebuild that release's
downloads, or with no tag for a dry run of the selected branch: everything is built and checked and
the jars are kept as a workflow artifact, but nothing is attached. Pull requests that change
`release.yml` or a pom run the dry run too.

## Saved games

A saved game is the JSON of `GameState`, stored in the `saved_game` table (players) and the
`guest_game` table (guests). Its format number is `GameState.SAVE_VERSION`
(`backend/src/main/java/com/barony/backend/model/GameState.java`).

Fixtures of every format are kept in `backend/src/test/resources/saves/`:

- `v0-pre-unit-types.json`: the format from before formats were numbered (a save from before unit
  types), checked in detail by `PreUnitTypesSaveTest`.
- `v<N>-<name>.json`: saves written by the serializer of format N (see `SaveFixtureWriter`).

`backend/src/test/java/com/barony/backend/service/SaveFixturesTest.java` names every fixture and
loads each through the real player and guest load paths. When the stored format changes:

1. Bump `SAVE_VERSION`.
2. Make the load path read every older format exactly as before (a migration on load if needed).
3. Write fixtures of the new format (`mvn test -Dtest=SaveFixtureWriter -Dbarony.writeSaveFixtures=true`
   in `backend/` writes `v<N>-new-game.json`, `v<N>-mid-game-field-battle.json` and
   `v<N>-game-over.json`, never overwriting a file) and list them in `SaveFixturesTest`.

A fixture that has been released never changes, and `SAVE_VERSION` never goes down; the release
automation refuses to tag a commit that breaks either rule.
