# Car Launcher Map API Compatibility Task

## Goal

Provide only the OsmAnd-derived map capabilities that Car Launcher actually uses, backed by CoMaps APIs. Keep every adapter and launcher-specific model inside the `carlauncher` source set; do not add compatibility code to upstream CoMaps sources.

## Required capabilities

- Current GPS position, speed, altitude and bearing.
- Current address and a best-effort nearby street name.
- Navigation state, current/next streets, turn, remaining distance/time and speed limit.
- Bookmark categories and bookmark coordinates for favorite-place selection.
- Map coordinate selection for antenna source/target points.
- Antenna A/B point persistence, bearing/distance calculation and map visualization.
- Home/work/favorite lookup used by voice commands.

## Current status

- [x] CoMaps-native navigation telemetry via `RoutingController`/`RoutingInfo`.
- [x] CoMaps-native reverse geocoding via `Framework.nativeGetAddress`.
- [x] Flavor-local `CarMapApi` contract and CoMaps adapter.
- [x] Bookmark category/point read adapter.
- [x] Port the persistent, map-engine-independent antenna manager.
- [ ] Move antenna picker UI to `CarMapApi`.
- [ ] Implement CoMaps map-pick callback and antenna overlay.
- [ ] Enable antenna panel/widget registrations currently commented out.
- [ ] Move voice home/work/favorite lookup to `CarMapApi`.

## Design rules

- Never report dummy success. Missing capabilities return an empty result or an explicit unsupported result.
- JNI/core calls run on the main thread after CoMaps core initialization.
- Public OsmAnd AIDL compatibility is out of scope unless a real external consumer requires it.
- Do not modify `android/app/src/main` or `android/sdk`; use their public APIs from the flavor adapter.
