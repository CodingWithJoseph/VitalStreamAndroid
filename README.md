# VitalStream Android

Kotlin/Compose application with Room persistence, account-scoped upload queues, WorkManager retries, AppAuth OIDC/PKCE login, encrypted token storage, a foreground Health Connect resting-heart-rate importer, and baseline insight display.

## Development

Use Android Studio, SDK platform 37.0, build tools 36.0.0 and JDK 25. The wrapper uses Gradle 9.5.0. Start the companion [backend](https://github.com/CodingWithJoseph/VitalStreamBackend) Compose stack. Debug builds use emulator host `10.0.2.2`, API port 8000 and Keycloak port 8080. HTTP is restricted to local debug destinations; release endpoints are configurable through Gradle properties `vitalstream.apiUrl` and `vitalstream.issuer` and must use HTTPS.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

On Linux/macOS use `./gradlew`. CI performs build/unit/lint checks.

## Try the implemented features

1. Launch on an emulator. In Sources, load demo history; it persists offline and after restart.
2. Sign in/register through Keycloak. Load demo history again to associate it with the signed-in account. Preview records are kept separate.
3. Sync now uploads records, reads back server records and requests the selected source's baseline insight.
4. On a compatible device, grant Health Connect resting-heart-rate permission, then explicitly import. A companion app must actually write that data type.

The app implements contract 0.1.0 from the backend's generated `contracts/openapi.json`. Source metadata, stable IDs, revisions and UTC timestamps are preserved. Ordinary heart-rate samples are not relabeled as resting heart rate.

## Validation and unfinished work

As of November 4, 2026, debug assembly, unit tests, lint and both emulator instrumentation tests passed. The persistence test covers reopen/retry behavior, account isolation, stale acknowledgements and local tombstones. Lint still reports nonblocking warnings.

End-to-end login remains unverified: the companion host-side Keycloak smoke test returned HTTP 400 during login. Fix that and test the actual browser callback/refresh flow before relying on auth. No physical wearable has been tested. Health Connect currently imports snapshots; incremental changes/deletions are deliberately left as your assignment. Insights are fetched on manual sync and are not cached across process restarts yet.

## Your work

Read `YOUR_TASKS.md` and search source files for `JOSEPH-`. The important learning tasks include the history chart, incremental wearable sync and auth verification. This is a draft development foundation; web and production deployment are deferred.
