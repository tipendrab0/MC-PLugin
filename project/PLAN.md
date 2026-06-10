# My Hero Academia Plugin - Architecture Plan

## Overview
A comprehensive Minecraft plugin that brings My Hero Academia's Quirk system to Paper servers (compatible with Paper, Purpur, Spigot 1.21+).

## Project Structure

```
src/main/java/com/mha/plugin/
├── MHAPlugin.java              # Main plugin class
├── quirk/
│   ├── Quirk.java              # Abstract base class
│   ├── QuirkManager.java       # Handles Quirk registration & lookup
│   ├── QuirkType.java          # Enum of all available Quirks
│   └── impl/
│       ├── ExplosionQuirk.java     # Bakugo's explosion ability
│       ├── IceFireQuirk.java       # Todoroki's half-cold half-hot
│       └── ZeroGravityQuirk.java   # Uraraka's zero gravity
├── stamina/
│   ├── StaminaManager.java     # Energy tracking & exhaustion
│   └── StaminaState.java       # Player stamina data holder
├── listener/
│   ├── QuirkActivationListener.java  # Handles ability triggers
│   └── PlayerConnectionListener.java # Load/save on join/quit
├── util/
│   └── ConfigManager.java      # YAML config handling
└── data/
    └── PlayerData.java         # Player Quirk assignment model

src/main/resources/
├── plugin.yml                  # Plugin metadata & permissions
└── config.yml                  # Default configuration
```

## Core Systems

### 1. Quirk System Architecture

```
                    ┌─────────────────┐
                    │   Quirk (abstract)
                    ├─────────────────┤
                    │ + id: String
                    │ + name: String
                    │ + cooldown: int
                    │ + staminaCost: int
                    ├─────────────────┤
                    │ + activate(Player): void
                    │ + deactivate(Player): void
                    │ + canUse(Player): boolean
                    │ + getCooldownRemaining(Player): int
                    └────────┬────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
┌────────▼───────┐  ┌───────▼────────┐  ┌───────▼────────┐
│ ExplosionQuirk │  │  IceFireQuirk  │  │ ZeroGravityQuirk│
└────────────────┘  └────────────────┘  └─────────────────┘
```

**Abstract Quirk Class Methods:**

| Method | Purpose |
|--------|---------|
| `activate(Player)` | Execute the Quirk ability |
| `deactivate(Player)` | Stop ongoing effects |
| `canUse(Player)` | Check cooldown + stamina requirements |
| `getCooldownRemaining(Player)` | Time left before next use |
| `onEnable()` | Called when Quirk is assigned to player |
| `onDisable()` | Called when Quirk is removed |

### 2. Stamina System

**StaminaManager Responsibilities:**
- Track stamina per-player (max 100, regenerates over time)
- Calculate physical toll based on Quirk usage
- Handle exhaustion state (stamina < 10%)
- Broadcast exhaustion warnings to players
- Regeneration rate configurable in config.yml

**Stamina States:**
```
FULL      (100-80%)  - Normal regeneration, no effects
NORMAL    (79-40%)   - Standard state
LOW       (39-20%)   - Warning messages, slower regen
EXHAUSTED (19-0%)    - Cannot use Quirks, slow movement
```

### 3. Quirk Activation Flow

```
Player triggers ability (right-click / shift-click / command)
         │
         ▼
┌─────────────────────┐
│ QuirkActivationListener
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐     NO    ┌──────────────┐
│ Player has Quirk?   ├──────────►│ Send message │
└─────────┬───────────┘            └──────────────┘
          │ YES
          ▼
┌─────────────────────┐     NO    ┌──────────────┐
│ canUse(player)?     ├──────────►│ Show cooldown│
│ (cooldown + stamina)│           │ or low stamina│
└─────────┬───────────┘            └──────────────┘
          │ YES
          ▼
┌─────────────────────┐
│ Deduct stamina     │
│ Start cooldown     │
│ quirk.activate()   │
└─────────────────────┘
```

### 4. Configuration Schema (config.yml)

```yaml
# MHA Plugin Configuration
settings:
  debug: false
  language: en_US

stamina:
  max: 100
  regen-rate: 1        # per second
  exhausted-threshold: 10

quirks:
  explosion:
    cooldown: 5000     # milliseconds
    stamina-cost: 15
    damage: 6.0
    range: 8.0
  ice-fire:
    cooldown: 3000
    stamina-cost: 10
    ice-duration: 5000
    fire-duration: 3000
  zero-gravity:
    cooldown: 2000
    stamina-cost: 8
    duration: 10000    # milliseconds player floats

players:               # Quirk assignments (UUID -> QuirkType)
  # Populated at runtime
```

### 5. Permissions Matrix

| Permission | Description | Default |
|------------|-------------|---------|
| `mha.use.quirk` | Use any assigned Quirk | true |
| `mha.admin` | Admin commands (assign/remove) | op |
| `mha.admin.reload` | Reload configuration | op |
| `mha.admin.setquirk` | Set player's Quirk | op |
| `mha.admin.resetstamina` | Reset player stamina | op |

## Quirk Implementations

### Explosion Quirk (Bakugo-style)
- **Activation:** Right-click with empty hand
- **Effect:** Creates an explosion at target location
- **Mechanics:** 
  - Launches player in opposite direction (rocket jump)
  - Damages nearby entities
  - Breaks weak blocks in radius

### Ice/Fire Quirk (Todoroki-style)
- **Activation:** 
  - Right-click = Ice (freezes target)
  - Left-click = Fire (ignites target)
- **Mechanics:**
  - Ice: Creates ice blocks, slows/freezes entities
  - Fire: Creates fire trail, ignites entities
  - Dual-wielding possible with both hands

### Zero Gravity Quirk (Uraraka-style)
- **Activation:** Punch an entity or block
- **Effect:** Target floats for duration
- **Mechanics:**
  - Floating entities cannot move normally
  - Touching ground ends effect early
  - Can stack multiple targets

## Data Persistence

- **Player Quirk assignments:** Stored in config.yml under `players:` section
- **Runtime stamina:** In-memory with periodic saves
- **Cooldowns:** In-memory per-player timestamps

## Modularity Design

The Quirk system is designed for easy extension:

1. **Create new class** extending `Quirk` in `impl/` package
2. **Add entry** to `QuirkType` enum
3. **Register** in `QuirkManager.registerQuirks()`
4. **Add config** section in config.yml

No modifications to core classes needed when adding new Quirks.

## Compatibility Notes

- Uses Paper API (1.21+) for modern event handling
- Falls back gracefully on Spigot/Purpur
- No NMS/NBT dependencies (pure API)
- Configurable version checks disabled for fork support

## Commands

| Command | Description | Permission |
|---------|-------------|-----------|
| `/mha` | Show plugin info | mha.use.quirk |
| `/mha quirks` | List available Quirks | mha.use.quirk |
| `/mha stamina` | Show your stamina | mha.use.quirk |
| `/mha reload` | Reload config | mha.admin.reload |
| `/mha setquirk <player> <quirk>` | Assign Quirk | mha.admin.setquirk |
| `/mha resetstamina <player>` | Reset stamina | mha.admin.resetstamina |

## Advanced Features

### 1. QTE (Quick Time Event) System
- **Activation:** Sneak + Right-click to trigger Ultimate Move
- **Mechanics:** Player must match a sequence of WASD keys within 1.5 seconds
- **Rewards:** 2x damage multiplier, no cooldown on success
- **Rank Requirement:** Only available to Hero rank or higher

### 2. Quirk Synergy Combos
- **Trigger:** Two different players hit same target within 2 seconds
- **Combos:**
  - Fire + Wind = Firestorm (15 damage, 8 radius)
  - Ice + Wind = Ice Storm (12 damage, slow effect)
  - Explosion + Lightning = Thunderclap (20 damage, 12 radius)
  - Fire + Ice = Steam Eruption (18 damage, knockback)
  - Zero Gravity + Explosion = Gravity Well (25 damage, pull)

### 3. Hero Society Reputation System
- **Hero Points:** Gained by killing hostile mobs
  - Zombies/Skeletons: 1 point
  - Creepers/Endermen: 2 points
  - Bosses: 25-50 points
- **Villain Points:** Gained by attacking villagers or players
- **Ranks:**
  - Civilian (Neutral)
  - Hero (50+ points) - Blue nametag
  - Pro Hero (200+ points) - Gold nametag, Ultimate access
  - Villain (-30 points) - Red nametag
  - Supervillain (-100 points) - Dark red nametag

### 4. Environmental Destruction
- **Automatic Snapshots:** Blocks destroyed by Quirks are recorded
- **Timed Restoration:** Blocks restore after 5 minutes
- **Protected Areas:** Admins can mark blocks as protected
- **Cleanup Command:** `/mha cleanup` forces immediate restoration

## Project Status

### Completed Features
- ✅ Core Quirk system (Explosion, Ice/Fire, Zero Gravity)
- ✅ Stamina management with regeneration
- ✅ Cooldown system per Quirk
- ✅ YAML configuration system
- ✅ Player Quirk assignments (persistent)
- ✅ QTE system for Ultimate Moves
- ✅ Quirk Synergy combo system
- ✅ Hero Society reputation mechanics
- ✅ Environmental destruction with restoration
- ✅ Admin commands and permissions

### Build Requirements
- Java 21
- Maven 3.8+
- Paper API 1.21+ (included as dependency)

### Build Command
```bash
mvn clean package
```
