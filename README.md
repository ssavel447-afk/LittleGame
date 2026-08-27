# LittleGame

Paper/Purpur plugin for Minecraft 1.21.11 battle royale events.

## Features

- `/start` launches the event from a 10x10 spawn border.
- 5-minute player gathering phase with a boss bar timer.
- 45-minute development phase: border expands to 1000x1000, PvP is disabled, and every online player receives 20 carrots.
- 30-minute fighting phase: PvP is enabled and the border shrinks back to 10x10.
- Deathmatch boss bar after the timer ends.
- Pretty death messages.
- `/teams` UI-style command help for team creation, invites, leaving, info, and admin team-size limits.
- Team prefixes in chat, tab list, and above players through scoreboard teams.
- Friendly fire is always disabled for teammates.

## Build

```bash
gradle build
```

The plugin jar is produced in `build/libs/`.
