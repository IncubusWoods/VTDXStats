# VTDXStats

Stat distribution plugin for VTuberDex ecosystem. Uses VTuberDexBridge for level sync and MythicLib for stat application.

## Features
- 6 VTuberDex-style stats, 5 points per level, max level 100 (500 points).
- Configurable stat attributes, max points, caps, and bonuses.
- MythicLib-backed stat application (command templates, configurable).
- Configurable GUI slots for overview and distribution menus.
- Admin GUI: player heads, per-player scale toggles, reset distribution.
- `/vtdxstats reset <player>` for MMOItems/console use.

## Dependencies
- VTuberDexBridge (required)
- MythicLib (required for stat application)
- PlaceholderAPI (optional)
