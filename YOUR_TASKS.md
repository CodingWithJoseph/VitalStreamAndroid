# Joseph's Android assignments

## First: prove login end to end (JOSEPH-AUTH)

Use the backend's `YOUR_TASKS.md` authentication checklist. The host-side smoke test currently gets HTTP 400 from Keycloak's login POST. Separate a smoke-harness hostname/cookie issue from an actual Android issue. Verify browser redirect, state/nonce/PKCE, `/v1/me`, refresh, process restart and two-account isolation. Never bypass validation to make the test pass. Release endpoints currently default to invalid placeholders intentionally.

## History visualization (JOSEPH-M1)

Location: `MainActivity.kt`, Overview. Extract a pure mapper from observations to 7/28-day chart points. Choose and display the grouping timezone. Keep gaps instead of drawing zeros; filter by source so unrelated measurement methods do not merge. Preserve the existing reading list as the accessible detail view.

Acceptance: tests for no records, missing days, duplicate uploads, DST boundaries and source changes; TalkBack announces values/time range; changing the filter preserves navigation state. You can also move each tab into its own screen file while keeping business logic in ViewModels/repositories.

## Incremental Health Connect sync (JOSEPH-M2)

Location: `health/HealthConnectSource.kt`. Replace foreground 28-day snapshots with Changes tokens. Store the checkpoint per account and record type only after each page is durably committed. Implement upserts and deletion outbox entries; map deletion to the backend tombstone endpoint. Handle expired tokens with a controlled rescan and revoked permissions with a clear UI state.

Acceptance: process death halfway through a page does not lose records, replay does not duplicate them, revoking access stops reads, and deleting a source record removes it from both local and server views. Use Health Connect's fake client for edge cases, then validate with a real companion app/device. Do not advertise untested brands as supported.

## Finish the insight experience (JOSEPH-M4)

Location: `ui/MainViewModel.kt`. Add an account/source-keyed Room insight cache with method version, evidence window and generated time. Show cached results offline with an explicit stale label. Invalidate after corrections/deletions or source changes. Test that switching accounts cannot briefly show the previous account's insight.

## Other remaining work

- Conflict resolution and deletion/account-management screens; Health Connect revocation does not erase past uploads.
- Clear-cache controls on logout (currently credentials are removed and records remain isolated by owner).
- Real privacy policy, release HTTPS configuration, device coverage and accessibility review.
- Additional typed metrics, circadian analysis, symptom/outcome tracking and provider sharing come after this slice.

Backend/AI tasks and the trained-model evaluation assignment live in the companion repository. No web work is required for these assignments.
