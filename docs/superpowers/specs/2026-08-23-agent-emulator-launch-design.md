# Agent Emulator Launch Instructions Design

## Goal

Add a repository-root `AGENTS.md` that tells coding agents to launch the `Pixel_8_Pro` AVD as a standalone Android Emulator window instead of embedding it in Android Studio.

## Scope

The instruction will:

- use the Android SDK emulator binary at `/Users/yg02/Library/Android/sdk/emulator/emulator`;
- launch `Pixel_8_Pro` with `-netdelay none -netspeed full`;
- require a persistent foreground execution session so the emulator is not terminated with a short-lived shell;
- check for an already running emulator before starting another instance;
- avoid Android Studio embedding flags such as `-qt-hide-window`, `-grpc-use-token`, and `-idle-grpc-timeout`;
- prohibit `Wipe Data`, App-data clearing, and duplicate emulator instances unless the user explicitly requests them.

## Placement

Create only `/AGENTS.md` for the implementation. Do not duplicate these agent-only instructions in `README.md` or modify application code.

## Verification

- Confirm `AGENTS.md` is at the repository root.
- Confirm the documented command exactly targets `Pixel_8_Pro` and opens a standalone window.
- Confirm the file distinguishes standalone launch from Android Studio embedded launch.
- Run `git diff --check` and verify unrelated working-tree files remain untouched.
