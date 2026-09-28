# Capture & Gallery — delegated to SwtFrontend

## Overview

Capture, recording and gallery are host capabilities. HylianBox contains no gallery Activity and
does not own new capture files. Its in-game menu and dock delegate to signature-protected
SwtFrontend actions so every module/game uses the same local collection.

## Delegated actions

- `br.com.redclaw.swt.capture.SCREENSHOT`
- `br.com.redclaw.swt.capture.RECORDING`
- `br.com.redclaw.swt.capture.GALLERY`

All intents target package `br.com.redclaw.swt`. The screenshot/recording actions include
`capture_source_id=br.com.redclaw.hylianbox:<hackId>`. Android's MediaProjection consent is owned
by the host, and recording is stopped from the host foreground-service notification.

## Security boundary

The actions are exported only with `br.com.redclaw.swt.permission.BIND_MODULE` (`signature`). The
module never receives a projection token, capture path or FileProvider URI. Media is private to the
host until the user explicitly chooses Share.

## Ownership

Implementation lives in `SwtFrontend/app/src/main/java/br/com/redclaw/swt/capture`. The removed
HylianBox `gallery/` package must not be recreated. Legacy private HylianBox media may be imported by
the future journaled data migration, but new files always belong to the host.

## Compatibility fallback

If the host action cannot be resolved, HylianBox shows `capture_failed` and continues gameplay. It
must not silently fall back to module-private recording, because that would reintroduce two owners.
