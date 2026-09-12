# Barony ASN.1 Schema Reference

This document describes each type defined in `protocol.asn` and maps it to the
corresponding Java source files in the Barony codebase.

## Overview

The Barony protocol uses a standard Spring Boot REST API with Jackson JSON
serialization. The ASN.1 schema in `protocol.asn` provides a machine-readable,
language-neutral contract that documents every data type exchanged between the
web-client and backend.

**Tier:** A (Schema Only) — the schema documents the existing wire format without
replacing the Jackson JSON serialization.

## Primitive Constrained Types

| ASN.1 Type | Range | Source |
|---|---|---|
| `PlayerId` | `INTEGER (0..2)` | `Tile.ownerId`, `Army.playerId` — 0=neutral, 1=player1, 2=player2 |
| `Coordinate` | `INTEGER (0..19)` | `Army.x`, `Army.y` — grid is 10×10 to 20×20 (`MapGenerator.java:18-19`) |
| `ArmyId` | `INTEGER (1..MAX)` | `Army.id` — auto-incrementing from `AtomicInteger` |
| `SoldierCount` | `INTEGER (0..MAX)` | `Army.soldiers`, `Command.splitAmount` |
| `TickCount` | `INTEGER (0..MAX)` | `GameState.tickCount` |
| `Population` | `INTEGER (0..MAX)` | `Tile.population` — no max limit |
| `Morale` | `INTEGER (0..200)` | `Army.morale` — clamped in `Army.setMorale()` |
| `Loyalty` | `INTEGER (0..110)` | `Army.loyalty` — clamped in `Army.setLoyalty()` |
| `Stability` | `INTEGER (0..110)` | `Tile.stability` — clamped in `Tile.setStability()` |
| `OccupationTicks` | `INTEGER (0..MAX)` | `Tile.occupationTicks` — castle capture progress |
| `PolicyTick` | `INTEGER (-15..MAX)` | `GameState.lastPolicyChangeTick` — initial value is -15 |
| `Username` | `UTF8String (SIZE (1..64))` | Login request username |
| `SessionId` | `UTF8String (SIZE (36))` | UUID format (e.g., `550e8400-e29b-41d4-a716-446655440000`) |

## Enumeration Types

### TileType

**Source:** `backend/src/main/java/com/barony/backend/model/TileType.java`

| Value | Description |
|---|---|
| `castle` | Castle tile — win condition (lose all castles = defeat) |
| `village` | Village tile — generates soldiers, captures via occupation |
| `empty` | Empty terrain — no special behavior |

### CommandType

**Source:** `backend/src/main/java/com/barony/backend/model/Command.java` (type field)

| Value | Java String | Description |
|---|---|---|
| `move` | `"MOVE"` | Move an army to target coordinates |
| `split` | `"SPLIT"` | Split an army, creating a new one with `splitAmount` soldiers |

### PolicyCategory

**Source:** `backend/src/main/java/com/barony/backend/model/RulerDecision.java`

| Value | Description |
|---|---|
| `economic` | Tax and infrastructure policies |
| `military` | Training and veteran policies |
| `population` | Growth and quality policies |

### EconomicPolicy

**Source:** `backend/src/main/java/com/barony/backend/model/RulerDecision.EconomicPolicy`

| Value | Java Name | Income | Stability |
|---|---|---|---|
| `heavyTaxation` | `HEAVY_TAXATION` | +20% | -10% |
| `balancedBudget` | `BALANCED_BUDGET` | 0% | 0% |
| `infrastructureInvestment` | `INFRASTRUCTURE_INVESTMENT` | -10% | +10% |

### MilitaryPolicy

**Source:** `backend/src/main/java/com/barony/backend/model/RulerDecision.MilitaryPolicy`

| Value | Java Name | Morale | Loyalty |
|---|---|---|---|
| `aggressiveTraining` | `AGGRESSIVE_TRAINING` | +10% | -5% |
| `standardService` | `STANDARD_SERVICE` | 0% | 0% |
| `veteranBenefits` | `VETERAN_BENEFITS` | -10% | +10% |

### PopulationPolicy

**Source:** `backend/src/main/java/com/barony/backend/model/RulerDecision.PopulationPolicy`

| Value | Java Name | Growth | Stability |
|---|---|---|---|
| `growthFocus` | `GROWTH_FOCUS` | +15% | -5% |
| `stablePopulation` | `STABLE_POPULATION` | 0% | 0% |
| `qualityOverQuantity` | `QUALITY_OVER_QUANTITY` | -10% | +10% |

## Composite Types

### Tile

**Source:** `backend/src/main/java/com/barony/backend/model/Tile.java`

```asn1
Tile ::= SEQUENCE {
    type            TileType,
    ownerId         PlayerId,
    occupationTicks OccupationTicks,
    stability       Stability,
    population      Population
}
```

### Army

**Source:** `backend/src/main/java/com/barony/backend/model/Army.java`

```asn1
Army ::= SEQUENCE {
    id             ArmyId,
    x              Coordinate,
    y              Coordinate,
    soldiers       SoldierCount,
    playerId       PlayerId,
    destinationX   Coordinate OPTIONAL,
    destinationY   Coordinate OPTIONAL,
    morale         Morale,
    loyalty        Loyalty
}
```

The `destinationX` and `destinationY` fields are `OPTIONAL` because they are
`Integer` (nullable) in the Java source — they are `null` when the army is
stationary and set when the army has a movement destination.

### GameState

**Source:** `backend/src/main/java/com/barony/backend/model/GameState.java`

```asn1
GameState ::= SEQUENCE {
    grid                Grid,
    armies              SEQUENCE OF Army,
    tickCount           TickCount,
    gameOver            BOOLEAN,
    winnerId            PlayerId OPTIONAL,
    aiEnabled           BOOLEAN,
    economicPolicy      EconomicPolicy,
    militaryPolicy      MilitaryPolicy,
    populationPolicy    PopulationPolicy,
    lastPolicyChangeTick PolicyTick
}
```

The `grid` is a 2D array represented as `SEQUENCE OF GridColumn` where each
`GridColumn` is `SEQUENCE OF Tile`. The `winnerId` is `OPTIONAL` because it is
`null` while the game is in progress.

## Request Types

### LoginRequest

**Source:** `backend/src/main/java/com/barony/backend/controller/AuthController.java`
**Endpoint:** `POST /api/auth/login`

```asn1
LoginRequest ::= SEQUENCE {
    username  Username
}
```

### Command

**Source:** `backend/src/main/java/com/barony/backend/model/Command.java`
**Endpoint:** `POST /command`, `POST /api/session/command`

```asn1
Command ::= SEQUENCE {
    type        CommandType,
    armyId      ArmyId,
    targetX     Coordinate,
    targetY     Coordinate,
    splitAmount SoldierCount
}
```

### RulerDecision

**Source:** `backend/src/main/java/com/barony/backend/model/RulerDecision.java`
**Endpoint:** `POST /api/decision`, `POST /api/session/decision`

```asn1
RulerDecision ::= SEQUENCE {
    category  PolicyCategory,
    choice    PolicyChoice
}

PolicyChoice ::= CHOICE {
    economic   EconomicPolicy,
    military   MilitaryPolicy,
    population PopulationPolicy
}
```

In the Java source, `choice` is a `String` field. The ASN.1 schema improves on this
by using a typed `CHOICE` union that constrains the choice value to valid options for
each policy category.

## Response Types

### LoginResponse

**Source:** `backend/src/main/java/com/barony/backend/model/Session.java`
**Endpoint:** `POST /api/auth/login` (response)

```asn1
LoginResponse ::= SEQUENCE {
    sessionId    SessionId,
    username     Username
}
```

### RulerStats

**Source:** `backend/src/main/java/com/barony/backend/model/RulerStats.java`
**Endpoint:** `GET /api/ruler-stats`, `GET /api/session/ruler-stats`

```asn1
RulerStats ::= SEQUENCE {
    averageStability      INTEGER (0..110),
    averageMorale         INTEGER (0..200),
    averageLoyalty        INTEGER (0..110),
    totalPopulation       Population,
    economicPolicy        EconomicPolicy,
    militaryPolicy        MilitaryPolicy,
    populationPolicy      PopulationPolicy,
    ticksUntilNextDecision INTEGER (0..MAX)
}
```

### ErrorResponse

**Source:** Error JSON objects returned by controller exception handlers in
`GameController.java` and `WebController.java`.

```asn1
ErrorResponse ::= SEQUENCE {
    status  INTEGER (400..599),
    error   UTF8String (SIZE (1..256)),
    message UTF8String (SIZE (1..1024))
}
```

## Top-Level Dispatch Unions

### BaronyRequest

All possible request message types.

```asn1
BaronyRequest ::= CHOICE {
    login          LoginRequest,
    command        Command,
    rulerDecision  RulerDecision,
    ...
}
```

### BaronyResponse

All possible response message types.

```asn1
BaronyResponse ::= CHOICE {
    loginResponse    LoginResponse,
    gameState        GameState,
    rulerStats       RulerStats,
    errorResponse    ErrorResponse,
    ...
}
```

The `...` extension marker allows future message types to be added without breaking
backward compatibility.

## XER Examples

### LoginRequest

```xml
<LoginRequest>
    <username>testplayer</username>
</LoginRequest>
```

### Command (MOVE)

```xml
<Command>
    <type><move/></type>
    <armyId>1</armyId>
    <targetX>5</targetX>
    <targetY>3</targetY>
    <splitAmount>0</splitAmount>
</Command>
```

### RulerDecision

```xml
<RulerDecision>
    <category><economic/></category>
    <choice><economic><heavyTaxation/></economic></choice>
</RulerDecision>
```

### GameState (minimal 2×2 grid)

```xml
<GameState>
    <grid>
        <GridColumn>
            <Tile>
                <type><castle/></type>
                <ownerId>1</ownerId>
                <occupationTicks>0</occupationTicks>
                <stability>100</stability>
                <population>100</population>
            </Tile>
            <Tile>
                <type><empty/></type>
                <ownerId>0</ownerId>
                <occupationTicks>0</occupationTicks>
                <stability>100</stability>
                <population>0</population>
            </Tile>
        </GridColumn>
        <GridColumn>
            <Tile>
                <type><village/></type>
                <ownerId>1</ownerId>
                <occupationTicks>0</occupationTicks>
                <stability>95</stability>
                <population>150</population>
            </Tile>
            <Tile>
                <type><castle/></type>
                <ownerId>2</ownerId>
                <occupationTicks>0</occupationTicks>
                <stability>100</stability>
                <population>100</population>
            </Tile>
        </GridColumn>
    </grid>
    <armies>
        <Army>
            <id>1</id>
            <x>0</x>
            <y>0</y>
            <soldiers>10</soldiers>
            <playerId>1</playerId>
            <morale>100</morale>
            <loyalty>100</loyalty>
        </Army>
    </armies>
    <tickCount>0</tickCount>
    <gameOver><false/></gameOver>
    <aiEnabled><true/></aiEnabled>
    <economicPolicy><balancedBudget/></economicPolicy>
    <militaryPolicy><standardService/></militaryPolicy>
    <populationPolicy><stablePopulation/></populationPolicy>
    <lastPolicyChangeTick>-15</lastPolicyChangeTick>
</GameState>
```

## Building and Testing

### Prerequisites

1. Build the `asn1c` compiler from the submodule:
   ```bash
   cd asn1c
   test -f configure || autoreconf -iv
   ./configure
   make
   make install prefix=$(pwd)/install
   ```

2. Build the converter binaries:
   ```bash
   cd asn1
   make
   ```

3. Run the round-trip tests:
   ```bash
   cd asn1
   make test
   ```

The tests encode each XER sample to DER (binary) and then decode back to XER,
verifying that the round-trip produces identical output.
