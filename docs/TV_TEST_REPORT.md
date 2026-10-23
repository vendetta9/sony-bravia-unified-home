# TV verification report — updated 24 October 2026

Device: Sony BRAVIA VH22 / Android 12, `192.168.68.104:5555`.
Public UI names: **Glass UI**, **Minimalist UI**, **Android UI**.

## OTT switching and title treatment — 24 October

The final APK was rebuilt and installed after fixing the preview handoff:

- Old-provider rotation is gated by displayed-provider identity.
- Catalog-refresh cooldowns no longer prevent cached artwork/metadata recovery.
- Movie/series requests run concurrently; failed empty catalogs retry after
  10 seconds. Already cached stale catalogs retain the 15-minute refresh retry.
- Thumbnail and title logo are prepared before publishing the next card.
  Optional original-resolution background upgrades do not change its title/logo.

A temporary native UI capture driver completed the requested 16-screen flow:
Android UI, three-second Home chooser, Minimalist Netflix focus, Apple TV library
focus, Obsession query/details, then Glass Netflix series, Prime Awarapan 2,
Zee5 Obsession, Apple TV Tenzing, Sports, Music, Apps, Obsession query/details
and the grouped All Apps pane. The four provider/title pairs were checked
against the active displayed controller state before capture and visually reviewed.
Netflix's captured series was Birangana. No fixture artwork or invented matches
were inserted. Native keyboard and actual API responses were used.

The chooser was opened using the app's gesture controller, not a new physical
remote verification. The temporary driver package/APKs were removed and the
Home helper rebound after the run. All 16 new captures remain outside the
repository pending the user's image-publication approval. The previous capture
batch was deleted. The earlier 30-check naming suite was not rerun in this flow.

## Movie and TV-show preview update

The final APK was rebuilt and installed after adding separate movie/series
catalog queries and an alternating eight-title rotation to Glass UI. Live
provider responses were inspected for Netflix, Prime Video, Apple TV, Hotstar,
Sony LIV and Zee5: each supplied both movie and series results. Series responses
contained `tv/` TMDB IDs, supported by the existing artwork lookup.

The new catalog adapter uses daily caches per service/type/source, fills unused
slots from the available type, and labels popularity fallback cards separately
from official chart cards. It bypasses the prior combined cache without clearing
saved launcher state. The build and installation succeeded; the 30-check naming
suite below was run on the preceding naming APK, not rerun for this preview update.

## UI naming regression (preceding APK)

`scripts/test-tv.sh`: **30 checks passed** after building and installing the
renamed final APK. The test invokes the shared gesture controller and navigates
actual chooser buttons using DPAD/OK.

- Exactly three modes, with the exact requested labels in both the enum and
  the rendered chooser's text views.
- Legacy minimalist and Android IDs still resolve; unknown IDs fall back to Glass UI.
- Previously saved IDs are rewritten to canonical IDs without changing their UI.
- Minimalist preferences migrate even when the previous v1 migration already ran.
  Strings, integer values and string sets survive; existing destination values
  are preserved and the migration marker is recorded.
- Minimalist and Glass activities resume; each chooser selection saves its mode.
- No chooser before three seconds; one chooser after the threshold; release
  keeps the chooser open. Short Home routes to the saved UI.
- The chooser opens while Android UI is selected. Its buttons switch among
  all three UIs successfully.
- Direct developer activity launch preserves the saved Home choice.
- Temporary migration-test keys are removed. The original Home choice is
  restored synchronously; this run restored **Glass UI**.

After test cleanup, the TV reported:

- **Bound services: Sony Bravia Unified Home keys**.
- **Crashed services: empty** for Accessibility.
- Foreground: `ui.glass.GlassHomeActivity`.
- Temporary test runner `tv.bravia.unifiedtests`: absent.
- Installed APK SHA-256 matches the final file below.

The crash buffer contained an earlier Spotify foreground-service failure; it
showed no launcher fatal exception in the inspected entries. This run did not
exercise Spotify playback or diagnose that separate app failure.

## Physical remote verification

Before this naming update, the user confirmed that a physical three-second Home
hold opened the chooser with all three destinations. The earlier settings-menu
symptom was traced to an unbound accessibility helper after instrumentation.
Installation/test cleanup now use `enable-home-keys.sh`, preserve other
accessibility services, explicitly unbind/rebind this helper, and wait for an
actual bound-service record before reporting success.

The renamed chooser labels were verified on-device by instrumentation in this
run. A new physical-remote hold was not requested for this label-only update.

## Earlier visual smoke checks

These checks were performed before the naming update. UI names below refer to
the current names of the same layouts.

| Check | Result |
| --- | --- |
| Minimalist UI home and six-column Apps and Inputs | Opened and rendered |
| Glass UI home, categories and Apps library | Opened and rendered |
| Minimalist UI Interstellar search / result selection | Suggestions, movie card, release, rating, four cast members and providers displayed |
| Shared movie selection after switching to Glass UI | Same selected movie displayed |
| Glass UI Interstellar keyboard/result selection | Movie details displayed |
| Glass UI Sports | Three widgets rendered: restricted football, live cricket, live F1 |
| Header | Clock/date/greeting, Wi-Fi name and temperature visible in both custom UIs |

## Fixes established during verification

1. Selected search cards suppress unrelated Glass OTT previews until dismissed.
2. Home-key recovery checks actual binding after Android instrumentation stops
   the target process, instead of assuming enabled means running.
3. Test teardown commits the restored Home choice before instrumentation ends.
4. Naming migration preserves previous saved selections and minimalist ordering;
   ordinary UI code now uses canonical names and isolated preference stores.

## Integrity and scope

All **143 source/configuration/APK files** checked against the original-repository
snapshot are unchanged. The original ADB daemon log is excluded because the
running daemon writes to it. The two original repositories were not renamed.

This is integration and smoke coverage. Physical microphone input, every provider
deep link/subscription entitlement, continuous volume holds, actual uninstall
completion, all input hardware, long-duration/day-boundary refreshes and media
pause in every external app were not reverified. No user app was uninstalled.
Test runner APKs are temporary and removed; captures remain outside the repo.

## Final binary

SHA-256: `459fad4ded6e52cbb298ef3894cbc1189af7a41fcb85bd6a293df933e9540101`

The installed `base.apk` hash matches `outputs/sony-bravia-unified-home.apk`.
Only this final APK is retained in the repository.
