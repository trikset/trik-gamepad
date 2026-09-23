# Design decisions

Record of significant design decisions that shape the codebase — why we
chose what we did, what the alternatives were, and what trade-offs we
accepted. New entries go at the top.

## [2026-09-22] Center placeholder vs chip eye — synchronised scope

**Decision:** The center-of-screen crossed-eye glyph (`md-eye_off` from
`symbols_mono.ttf`) shows ONLY when the video stream is **DISABLED** (no URL
configured) or **UNAVAILABLE** (configured but dead/unreachable). For all other
states — LOADING, RECONNECTING, PLAYING — the center placeholder is hidden.

The corner chip (top-left eye glyph tinted to the precise status color) is the
**only status indicator** for the intermediate/transient states. The center badge
is a glance "no video content" signal that answers one binary question: *is
there a picture or not?*

**Rationale:** A user looking at the gamepad should see either:
1. A moving video frame (PLAYING) — nothing in center, the chip shows green eye.
2. A loading/reconnecting state (LOADING/RECONNECTING) — the spinner or "Reconnecting…"
   pill is the appropriate transient indicator, the chip shows amber eye.
3. No picture at all (DISABLED/UNAVAILABLE) — the crossed-eye in center gives an
   unambiguous "no content" signal without needing to locate the small chip.

The chip alone is too small and peripheral for this binary signal; the center
placeholder alone does not distinguish DISABLED from UNAVAILABLE. The pair
together covers both: center = binary "is there content?", chip = precise
status (with color + contentDescription).

**Implementation:** A private `setVideoStatus()` helper wraps both
`robotChip.setVideoStatus()` and `updateVideoPlaceholder()` so the two indicators
are locked together at every call site (`setVideoUrl`, `setVideoLoading`, the
stream-error listener, the first-frame listener, the video-retry `onTimeout`).
A bare `robotChip.setVideoStatus()` call without the helper would drift the two
indicators — the extraction prevents that class of bug at the type level.

**Alternatives considered:**
- Show center placeholder for DISABLED only (misses UNAVAILABLE — empty video
  area with no signal is confusing).
- Show center placeholder for every non-PLAYING state (distracting during
  LOADING/RECONNECTING — the transient spinner/pill is the right UX there).
- Use center placeholder as the *only* status indicator, dropping the chip
  eye (loses the status granularity and color coding that the driver needs).