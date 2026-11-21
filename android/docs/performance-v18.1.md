# v18.1 Performance and ANR diagnosis

Scope: the repeated "System UI isn't responding" dialog reported on the development emulator
(about every 20 s), the native Cosmic Core, and the distraction monitor. Code and measurements
are from the v18.1 branch; v18 means commit `b742e62`.

## 1. Who owns the ANR?

The emulator logs that produced the user report were **not available** in this session (the
user's machine was not connected). Everything below is either measured on the emulator this
session started itself, or read from code. Nothing here claims to fix Android System UI.

### Controlled emulator (this session)

- Host: cloud container, 4 vCPU, 16 GB, **no KVM** (`/dev/kvm` absent; `emulator -accel-check`:
  "KVM requires a CPU that supports vmx or svm"). The emulator ran with software CPU emulation
  (TCG, forced with `ANDROID_I_WANT_MY_TCG=yes`), `-gpu swiftshader_indirect`, image
  `system-images;android-37.0;default;x86_64`, 4 cores, 4 GB RAM.
- Boot took about 29 minutes. **Focus Mode+ was not installed** until 15:51 (about 40 minutes
  after the emulator started), and its process never started afterwards (no `am_proc_start` for it).
- `adb logcat -b events` during that window (excerpt, pids change because `system_server` restarts):

```
15:19:31 I watchdog: Blocked in handler on main thread (main) for 62s
15:19:52 I am_anr : [0,1242,com.android.systemui,...,failed to complete startup]
15:22:48 I watchdog: Blocked in handler on main thread (main) for 67s
15:22:52 I am_anr : [0,1485,system,...,executing service com.android.server.telecom/.components.TelecomService, waited 22785ms]
15:25:58 I am_anr : [0,2449,com.android.systemui,...,failed to complete startup]
15:26:36 I am_anr : [0,2148,system,...,TelecomService, waited 20016ms]
  ... the same pair repeats roughly every 3 minutes ...
15:44:40 I am_anr : [0,6133,system,...,executing service com.android.location.fused/.FusedLocationService, waited 24415ms]
15:51:48 I am_anr : [0,8109,com.android.systemui,...,failed to complete startup]
15:52:51 I am_anr : [0,8302,com.android.networkstack.process,...,TetheringService, waited 21614ms]
```

- 15 `am_anr` events between 15:19 and 15:49, before the APK was installed, and 3 more after the
  install while the app had never been started: 18 in total, every one owned by
  `com.android.systemui`, `system` (system_server) or `com.android.networkstack.process`. `system_server` was restarted by
  its watchdog about every 3 minutes (main thread blocked 62–67 s), which also restarts System UI.
- `top` at 15:40: `system_server` 112 % CPU, `com.android.systemui` 52 %, the rest idle; the
  emulator itself is the bottleneck.
- After installing the debug APK (4th attempt, the package service was gone during restarts),
  launching the app failed repeatedly because the package manager disappeared with each
  `system_server` restart. **No ANR in this emulator was attributed to Focus Mode+.** A stable
  in-app measurement (frame stats, CPU share during a Session) could not be taken on it.

**Classification for this emulator: C (emulator-wide starvation).** System UI and system_server
ANR without the app installed; the app is not the ANR owner.

### The user's emulator

The reported cadence ("about every 20 s") matches two things: the dialog Android re-shows while
System UI stays unresponsive, and the distraction monitor's 20 s poll. The poll only runs during
a *protected* Session and talks to `system_server`, never to System UI, so it cannot be the ANR
owner; it can only add load (see §2). To classify the user's case, run on that emulator:

```bash
adb logcat -d -b events | grep am_anr        # 3rd field = ANR process
adb shell dumpsys activity processes | grep -i anr
adb shell top -b -n 1 -m 10                  # who uses the CPU
adb shell dumpsys gfxinfo io.github.illiapohukai.focusmode.debug   # app frame stats
adb bugreport anr.zip                        # /data/anr traces
```

Decision table:

| Evidence | Class | What it means |
|---|---|---|
| `am_anr ... com.android.systemui` with the app closed or on Home | A / C | Android or emulator; not caused by the app |
| the same, but only while the Focus screen is visible | A + app load | the app's rendering adds load on a weak emulator (see §2) |
| `am_anr ... io.github.illiapohukai.focusmode` | B | app ANR: the main-thread stack in the bugreport names the operation |
| no ANR, but `Janky frames` high in `gfxinfo` | D | rendering jank only |

Typical emulator causes of class A/C: no hardware acceleration (KVM/HAXM/Hypervisor framework off),
too little RAM for an API 35+ image, `-gpu swiftshader` (software GPU shared with the host CPU),
several emulators at once, or a host under load. Use a hardware-accelerated x86_64 image with
`-gpu host` and ≥ 4 GB RAM before judging app performance.

## 2. Confirmed application bottlenecks (fixed)

These were found in code and verified against the library behavior, not assumed. None of them is a
Focus Mode+ ANR, but each adds main-thread or render load that a starved emulator feels first.

1. **The Cosmic Core re-recorded the whole Focus screen on every animation frame.** In v18 the Core
   drew from a plain `Box.drawWithCache` without a layer of its own, and its clock state was read in
   the draw block 60 times per second. Compose UI 1.13.0 `NodeCoordinator.invalidateLayer()`
   (checked in the shipped `ui-android-1.13.0.aar` bytecode) invalidates the node's own layer if it
   has one and otherwise walks up to the parent, so each frame invalidated the nearest ancestor
   layer: the Focus destination, which re-executed the draw code of the header, task card, end
   action and timer every frame. The same applied in reverse: the per-second timer text change
   re-recorded the whole Core even in reduced motion.
   *Fix:* the Core now draws in two layers: a static deep field recorded once per size, and the
   animated system in its own `graphicsLayer`. The timer text is outside both.
2. **Per-frame CPU work in the Core.** v18 projected every particle twice per frame (rear pass and
   front pass), each with `cos`, `sin`, `sqrt`, and `cos`/`sin` of the orbit roll: about
   263 × 2 × 5 = 2 630 transcendental calls per frame at HIGH, plus a `Stroke` allocation per orbit
   per frame and a `clipPath(Difference)` (an expensive path clip, worse on software rendering).
   *Fix:* orbit constants (roll, depth factor, angular speed) are precomputed; each particle is
   projected once per frame into preallocated arrays (526 calls at HIGH); strokes are cached; the
   front orbit paths are built once per size with the timer zone already cut out, and front
   particles inside the zone are skipped by a rectangle test, so there is no clip at all.
3. **The core sphere** is now baked once per size into a bitmap off the main thread
   (`Dispatchers.Default`), exactly like the PWA bakes its core sprite; per frame it is one image.
4. **Distraction-monitor binder calls ran on the main thread.** The monitor was launched in the
   foreground service's `lifecycleScope` (main dispatcher), so `AppOpsManager` checks,
   `UsageStatsManager.queryEvents`, `PowerManager.isInteractive` and hint notifications ran on the
   app's main thread every 20 s. *Fix:* the job now runs on `Dispatchers.Default`.
5. **Monitor restart loop.** Every minute checkpoint re-emits the running Session. When the monitor
   had stopped (monitoring off, no apps, or Usage Access revoked), v18 started it again each minute,
   and each start forced a full protection re-read (`markUsageRevoked(false)` → `refresh()`); with
   revoked access the "revoked" flag flapped false/true every minute. *Fix:* see §4.
6. **Screen off.** v18 woke every 20 s with the screen off just to find it off. *Fix:* the monitor
   now suspends until `ACTION_SCREEN_ON` (no periodic wakeups).
7. **Notification channels** were re-created (`createNotificationChannels`, a system-server round
   trip) on every minute-cadence notification update. *Fix:* once per process.
8. **Per-second work outside a running Session.** The Focus ViewModel ticker emitted every second
   also on the Recovery screen. *Fix:* it ticks only while a Session runs. The Focus screen passes
   only the fields each part needs, so the per-second change recomposes the timer text, not the
   header, badge or task card.
9. **Route motion ignored reduced motion.** The NavHost used the library default (700 ms fades) for
   every route. *Fix:* PWA motion tokens; reduced motion uses short fades only.

Checked and **not** a bottleneck (left unchanged): Room access is suspend/Flow (no
`allowMainThreadQueries`, no `runBlocking`); the ongoing notification updates at minute cadence
without a chronometer or progress bar; the Session is checkpointed once per minute; WorkManager
work is debounced and diff-based; the installed-app catalog is queried only on the picker screen
(on `Dispatchers.IO`); Home recomputes its state every 15 s but emits only on change.

## 3. Cosmic Core quality strategy

| Tier | Particles | Orbit lines | Cadence | Glow | Use |
|---|---|---|---|---|---|
| HIGH | 263 | 4 + stream | display rate | primary ring glow | default |
| BALANCED | 152 | 4 + stream | display rate | yes | `memoryClass` < 192 MB, or stepped down |
| LOW | 70 | primary ring only | ~30 fps (33 ms) | no | low-RAM device, battery saver, or stepped down |

- The sphere, the timer, the readability zone and the primary ring are identical in every tier.
- `CosmicQualityGovernor` (pure, unit-tested): over windows of 120 frames, more than 40 frames
  slower than the tier's expected interval + 24 ms steps down one tier. It never steps up during a
  visit; pauses longer than 250 ms (screen off, background) are ignored. A step-down is remembered
  for the process (`processFloor`), so a weak device does not jank again on every visit.
- The frame loop runs only while the screen is at least STARTED, animation is allowed and reduced
  motion is off (`cosmicFrameLoopRuns`). Reduced motion draws one static frame and never schedules
  frames. No Room, DataStore or StateFlow is touched per frame; the clock is a local `FloatState`.
- Changing tier recreates only the particle arrays; the clock, the birth animation, the Session and
  every ViewModel are untouched (Session timing never depended on the Core).

## 4. Usage Access polling ownership

`DistractionMonitorOwner` (core:protection) is the single owner of the monitor job:

- `start(scope, session)` is called by `FocusSessionService` on every running emission. Same Session
  and job alive → no-op. Different Session → the old job is cancelled first. Standard Session →
  nothing runs.
- The job (on `Dispatchers.Default`) re-plans only when the relevant protection state changes
  (`MonitorPolicy.plan`: protected, monitoring on, Usage Access granted, ≥ 1 installed selected app).
  No plan → no polling, but the job stays to resume if access is granted during the same Session.
- Revoked access → the poll returns `AccessRevoked`, the capability owner re-reads the platform once,
  the plan becomes null, polling stops. No restart loop.
- `stop()` on Session end, service shutdown and `onDestroy`; service-scope cancellation (process
  death) also ends it. A restored process starts exactly one new job.
- Screen off → suspended until screen on.
- Tests: `DistractionMonitorOwnerTest` (one job per Session, cleanup after completion, cancellation
  and process restoration, permission revoked, screen off, Session not protected, a new Session
  replaces the job).

## 5. Remaining limitations

- No measurement of frame times or CPU share on real hardware or on an accelerated emulator; the
  only emulator available here could not keep `system_server` alive long enough.
- The Robolectric suite cannot measure rendering: it does not draw the view hierarchy per frame.
- The governor reacts to slow frames after about two seconds; the first seconds on a very weak
  device still run at the initial tier.
- On software-rendered emulators even the LOW tier draws ~80 primitives per frame; reduced motion
  (system "Remove animations" or Settings → Bewegung → Reduziert) gives a fully static Core.
