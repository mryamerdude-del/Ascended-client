# Ascend Client

Ascend Client is an independently developed Fabric client project for Minecraft Java Edition 1.21.11 and 26.3. It has a dual-version bootstrap and an initial in-game GUI foundation with shared Minecraft-independent state and separate version adapters.

The product goal is two selectable GUI layouts (an original, Ascend-branded Prestige-inspired layout and a dropdown layout) sharing the same module/settings behavior. Users must be able to switch layouts without restarting Minecraft, and the selected layout must persist between sessions. Only the Ascend Primary layout exists today; the dropdown layout and layout switching are still planned.

OpenPrestige material under `Refrence/` and `extracted/` is research material only. Its license has not been verified to permit reuse. Do not copy its code or assets into Ascend.

## Current implementation

- Two independent Fabric Loom projects build separate client mod JARs.
- Each project has its own client entrypoint and `fabric.mod.json`.
- Shared source is compiled independently in both target projects with Java 21 and packaged into both JARs.
- The 1.21.11 adapter compiles and launches with Java 21; the 26.3 adapter compiles and launches with Java 25.
- GitHub Actions builds both targets on pushes and pull requests.
- The original Ascend Primary browser GUI has Overview, Modules, Settings, and Appearance pages. HTML, CSS, and JavaScript are bundled as mod resources and loaded by Rinku through a validated `ascend://` resource handler.
- A typed JavaScript-to-Java command bridge provides navigation and settings updates. Shared settings validation, page state, and JSON persistence live outside the Minecraft adapters. Interface scale, accent color, animations, background dimming, and close-on-Escape persist in `config/ascendclient/settings.json`.
- Each adapter registers a Right Shift opening key and supplies the Minecraft version, key name, screen lifecycle, rendering, input, and native bridge integration.

### Module framework (Phase B)

- A Minecraft-independent shared module framework lives in `shared/src/main/java/dev/ascend/client/shared/module/`: `AscendModule` (lifecycle, typed settings list), `ModuleCategory`, `ModuleSetting` (boolean, number with optional out-of-range tolerance, choice, text, hotbar slots; visibility rules that depend on other settings), and `ModuleRegistry` (registration, enable/disable with rollback on failure, validated setting writes, per-module error reporting, tick dispatch that isolates module failures, persistence capture/restore, and GUI snapshots).
- The command bridge understands `{"action":"module","id","enabled"}` and a new `{"action":"moduleSetting","module","setting","value"}` command; both are validated and applied through the registry. Failures are reported to the GUI as bridge errors and shown in the Modules page.
- Configuration schema version 2 adds a persisted `modules` block (enabled flag, key code, and every setting value per module id). Schema 1 files still load; unknown or invalid stored values are skipped and reported instead of failing startup.
- The Modules page renders real registered modules with enable switches, an expandable settings panel (switches, sliders, choice chips, hotbar slot chips, and text inputs), visibility-conditional settings, module status text, and module error messages.
- The first ported module is **Aim Assist** (`shared/.../combat/`): all seventeen reference settings (names, defaults, ranges, steps, units, choices, conditions, and grouping) with the reference targeting, FOV, priority, aim-point, and rotation-transition math in `AimMath`/`Rotation`/`AimAssistModule`. Shared code contains no Minecraft imports; each adapter implements `AimView` (candidate collection with interpolated and pick-radius-expanded hitboxes, eye/player positions, attack key, item and hotbar whitelists, outline raycasts, and rotation application).
- Silent rotations are implemented with two small mixins per adapter: `LocalPlayerMixin` spoofs outgoing movement packet rotations around `sendPosition`, and `EntityMixin` redirects the local player's look vector (crosshair picking and attack direction) to the silent rotation while the camera stays still. Without a screen or target the silent rotation returns to the player's view at 55% per tick, matching the reference constants.

Known differences from the reference client are documented in the Verification record under Phase B. The dropdown/live layout switching item remains unimplemented.

## Supported bootstrap targets and toolchain

| Target | Java toolchain/release and client JVM | Fabric Loader | Fabric API | Loom |
|---|---:|---|---|---|
| Minecraft 1.21.11 | 21 | 0.20.1 | `0.141.6+1.21.11` | 1.19.4, `net.fabricmc.fabric-loom-remap`, official Mojang mappings |
| Minecraft 26.3 | 25 | 0.20.1 | `0.166.2+26.3` | 1.19.4, `net.fabricmc.fabric-loom` (non-remapping) |

The Gradle wrapper is 9.8.2, and the Gradle daemon JVM is configured for Java 25. The shared source baseline is Java 21, the lowest of the two target Java requirements. Its compiler uses `--release 21`, independently of each target's Java compiler.

The selected stable Loom 1.19.4 plugin IDs configure and build both targets. The 1.21.11 project uses the remapping plugin and Mojang mappings; the 26.3 project uses the non-remapping plugin and native Minecraft names. Target versions and dependencies are declared in `gradle.properties` and the respective version build files.

## Project layout

```text
.
|-- .gitignore
|-- AGENTS.md
|-- README.md
|-- .github/workflows/build.yml
|-- settings.gradle
|-- build.gradle
|-- gradle.properties
|-- gradlew
|-- gradlew.bat
|-- gradle/
|   |-- gradle-daemon-jvm.properties
|   `-- wrapper/
|       |-- gradle-wrapper.jar
|       `-- gradle-wrapper.properties
|-- shared/
|   `-- src/main/
|       |-- java/dev/ascend/client/shared/
|       |   |-- AscendApplication.java
|       |   |-- AscendBootstrap.java
|       |   |-- GuiModule.java
|       |   |-- GuiPage.java
|       |   |-- GuiPreferences.java
|       |   |-- GuiSnapshot.java
|       |   |-- UiCommand.java
|       |   `-- UiResponse.java
|       `-- resources/assets/ascendclient/
|           |-- html/
|           |   |-- app.js
|           |   |-- index.html
|           |   `-- style.css
|           `-- lang/en_us.json
`-- versions/
    |-- mc1_21_11/
    |   |-- build.gradle
    |   `-- src/
    |       |-- client/java/dev/ascend/client/AscendClient.java
    |       |-- client/java/dev/ascend/client/AscendScreen.java
    |       |-- main/resources/fabric.mod.json
    |       `-- test/java/dev/ascend/client/shared/AscendApplicationTest.java
    `-- mc26_3/
        |-- build.gradle
        `-- src/
            |-- client/java/dev/ascend/client/AscendClient.java
            |-- client/java/dev/ascend/client/AscendScreen.java
            `-- main/resources/fabric.mod.json
```

The root Gradle build includes `:versions:mc1_21_11` and `:versions:mc26_3`. In each Loom project, a custom `shared` source set points to the same repository-level shared directory. It imports no Minecraft, Fabric, Mixin, or version-specific APIs; its only external library use is Gson for configuration and protocol JSON. The shared task compiles it with Java 21; each client source set compiles at its target Java version. Each target adds the shared output and resources to its JAR. This avoids a cross-Loom project dependency and ensures both targets package the same shared implementation.

### Why there is no `:core` project

The shared-source-directory design keeps the build direct, avoids project dependency/remapping interactions between Loom builds, and compiles the same source independently for each target. Its trade-offs are compiling that source twice and enforcing a strict boundary: Minecraft/Fabric integration, rendering, input, and version-sensitive event handling belong in the adapters.

Consider a separate plain-Java `:core` only if shared code becomes substantial enough to benefit materially from independent lifecycle/API versioning, publication, multiple non-Loom consumers, or a separately valuable test/build boundary. Before migrating, prove that both Loom projects can consume the unremapped Java artifact without reintroducing cross-Loom remapping complications. Do not create the module merely to avoid sharing a source directory.

## Build and development

Install JDKs 21 and 25. The wrapper downloads Gradle 9.8.2; its daemon criteria select Java 25, while Gradle toolchains select Java 21 for the 1.21.11 target and shared source and Java 25 for the 26.3 target.

On Windows:

```powershell
.\gradlew.bat buildAll verifyGradleDaemonJava verifyDevelopmentLaunchers verifyArtifacts
.\gradlew.bat :versions:mc1_21_11:build
.\gradlew.bat :versions:mc26_3:build
.\gradlew.bat :versions:mc1_21_11:runClient
.\gradlew.bat :versions:mc26_3:runClient
```

On macOS or Linux, use `./gradlew` with the same task names. `buildAll` builds both target projects; their normal `build` tasks run the available tests. `verifyGradleDaemonJava` fails unless the daemon is Java 25; `verifyDevelopmentLaunchers` checks the configured `runClient` Java launcher for each target without starting a game window. The version-specific `runClient` tasks launch the development clients using their configured Java toolchains.

The distributable artifacts are:

- `versions/mc1_21_11/build/libs/ascend-client-mc1.21.11-0.1.0.jar`
- `versions/mc26_3/build/libs/ascend-client-mc26.3-0.1.0.jar`

## CI

`.github/workflows/build.yml` runs on every push and pull request. It installs Java 21 and Java 25, builds both targets, verifies the Gradle daemon and both client launchers, runs `verifyArtifacts` against the generated JARs, and uploads both mod JARs as workflow artifacts. A failure in either target or artifact check fails the job.

## Verification record

### Phase 2 GUI foundation

Verified locally with Gradle 9.8.2, Java 25.0.5 for the daemon and 26.3 client, and Java 21.0.13 for the 1.21.11 client:

- `.\gradlew.bat --no-daemon clean buildAll :versions:mc1_21_11:test verifyGradleDaemonJava verifyDevelopmentLaunchers verifyArtifacts --warning-mode all` â€” **BUILD SUCCESSFUL in 19s; 22 actionable tasks (21 executed, 1 up-to-date)**. No Gradle deprecation warning was emitted.
- All 3 shared-application tests passed (0 failures, 0 errors, 0 skipped). Both target builds, Java launcher checks, and `verifyArtifacts` passed. The verifier checked the expected classes and GUI resources, mod metadata, Java 21 shared bytecode and target entrypoint bytecode, and absence of reference-project paths in both JARs.
- `ascend-client-mc1.21.11-0.1.0.jar` is 48,436 bytes; `ascend-client-mc26.3-0.1.0.jar` is 48,056 bytes.
- Both development clients loaded Fabric and Ascend. The 1.21.11 GUI opened from the title screen, rendered the Modules page, and displayed a connected state without a bridge error. The 26.3 GUI opened from the title screen and rendered the Overview page with a connected state. Temporary test-only auto-open hooks were removed after these checks.
- `node --check shared\src\main\resources\assets\ascendclient\html\app.js` and `git diff --check` passed.
- Both runtime launches emitted a non-fatal FabricLoader warning for nonexistent empty development classpath directories. The placeholder development account also produced expected 401 user-properties/Realms authentication errors; neither prevented startup or GUI verification. No hosted CI run was performed.
- Runtime testing exposed a JavaScript/native bridge response-shape mismatch. The bridge returns the serialized GUI snapshot directly; the JavaScript handler now renders that snapshot directly and leaves close handling to the Minecraft adapter. Both target GUIs were relaunched and visually checked after the fix.

Verified locally with Gradle 9.8.2 and Java 25.0.5 as the daemon JVM:

- `.\gradlew.bat clean buildAll verifyGradleDaemonJava verifyDevelopmentLaunchers verifyArtifacts --warning-mode all` â€” **BUILD SUCCESSFUL in 4s; 18 actionable tasks (17 executed, 1 up-to-date)**. Both shared-source and client compilation tasks, both target builds, daemon/launcher checks, and artifact verification passed.
- The launcher checks selected Java 21.0.13 for Minecraft 1.21.11 and Java 25.0.5 for Minecraft 26.3.
- `verifyArtifacts` and a separate inspection of the resulting archives confirmed exactly one copy of each entrypoint and shared class, one root `fabric.mod.json`, the expected `ascendclient` mod ID, Minecraft version and client entrypoint, and no OpenPrestige/reference-project archive entries. The shared class is Java 21 bytecode (major 65) in both JARs; the target entrypoint is major 65 for 1.21.11 and major 69 for 26.3.
- Artifact details: `ascend-client-mc1.21.11-0.1.0.jar` â€” 2,902 bytes; `ascend-client-mc26.3-0.1.0.jar` â€” 2,797 bytes.
- Both development clients were launched. Fabric loaded Ascend Client, each logged its expected `Ascend Client bootstrap initialized for Minecraft <version>` message, and each reached the Minecraft title screen. The 1.21.11 client was also taken into a single-player world. No startup crash or entrypoint error occurred. The 26.3 client closed cleanly. One 1.21.11 run logged `Could not schedule ConsecutiveExecutor` / `RejectedExecutionException` during integrated-server shutdown under the `(Minecraft)` logger. Ascend has no shutdown hook, server callback, or executor use; a controlled repeat started and closed the same world without reproducing the exception. This is attributed to a transient Minecraft integrated-server shutdown path, not Ascend; the original stack trace did not include frames to isolate the internal caller.
- Both clients logged non-fatal warnings for missing empty source-set output directories. The development session also logged 401 user-properties and Realms authentication errors because its test account uses a placeholder token; these did not block startup or title-screen access.
- The Gradle 10 deprecation warning was traced to this build's resource-processing closures reading `project.version` during task execution. Capturing the version during configuration removed it; the final `--warning-mode all` build emitted no Gradle deprecation warning. Gradle only suggested enabling the optional configuration cache.
- At Phase 1 verification, test sources were not present. The GitHub Actions workflow was updated, but no hosted CI run was performed for that change.

## Compatibility boundaries and open issues

- The browser GUI and its version-specific screen/input adapters were exercised on both target versions. This does not establish compatibility of future gameplay event integrations or modules.
- The GUI's Content Security Policy permits inline scripts for the CEF query bridge and inline styles for runtime preferences, while disallowing external network resources. Review this policy if bridge or asset-loading behavior changes.
- Keep rendering, input, events, and Minecraft/Fabric-specific behavior in the version adapters when the APIs differ. Shared code must remain Java-only and Minecraft-independent.
- Java 25 is required to run the Gradle daemon and the 26.3 development client. Java 21 is required for the 1.21.11 client and shared-source compilation.
- The final Gradle 9.8.2 build emits no Gradle deprecation warning; Gradle 10 compatibility has not been tested.

## Roadmap

1. **Foundation â€” complete:** dual Loom targets, shared Java 21 compilation, target entrypoints and metadata, wrapper/toolchains, CI, automated artifact verification, and local title-screen startup verification for both targets.
2. **GUI foundation â€” in progress:** the Ascend Primary browser UI, version-specific browser/screen adapters, typed command bridge, shared validated preferences, local JSON persistence, the module registry with per-module settings UI, and live module toggles/settings are implemented. Remaining: the dropdown layout and runtime layout switching with persistent selection.
3. **Compatibility and shared client model â€” in progress:** the shared module lifecycle, registry, typed settings, configuration contracts, and the Aim Assist combat module are implemented without Minecraft imports; both adapters implement the shared `AimView` surface. Remaining: keep auditing ported modules against the reference implementation, add the reference `Targets` module (entity-type filters, keep target, anti-bot, team/friend filtering) that Aim Assist currently assumes absent, movement correction under silent rotations, and attack-cooldown tracking for the HurtTick priority.
4. **Validation and release:** add focused tests for new behavior, verify both target layouts and parity in both clients, document compatibility, and prepare distribution only after requirements are established.

## Technical references

- [Fabric develop/version information](https://fabricmc.net/develop/)
- [Fabric example mod â€” Minecraft 1.21.11](https://github.com/FabricMC/fabric-example-mod/tree/1.21.11)
- [Fabric example mod â€” Minecraft 26.3](https://github.com/FabricMC/fabric-example-mod/tree/26.3)
- [Fabric Loom documentation](https://docs.fabricmc.net/develop/loom/)
- [Gradle Java compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)

## Documentation and handoff log

### 2026-11-21 - Phase B shared module framework and Aim Assist

- **Changed:** Added the Minecraft-independent module framework (module/ModuleCategory, ModuleSetting, AscendModule, ModuleRegistry), typed GUI settings (GuiSetting, extended GuiModule/GuiSnapshot), the moduleSetting bridge command, configuration schema 2 with a persisted modules block (schema 1 still loads), and the Modules page settings panel (switches, sliders, choice chips, hotbar slot chips, text inputs, conditional visibility, status and error display).
- **Changed:** Ported Aim Assist as the first module. shared/.../combat/ holds Rotation, AimMath (aim-point selection, 3x5x3 visibility fallback grid, FOV, priority comparators, SNAP/EASED/NATURAL transitions with the reference velocity model), AimAssistModule (all seventeen reference settings with reference names, defaults, ranges, choices, conditions, and grouping), and the adapter-facing AimView. Both adapters implement AscendAimView and AscendRotations; silent rotations use per-adapter LocalPlayerMixin (outgoing packet rotation) and EntityMixin (local-player look vector) mixins with new scendclient-client.mixins.json configs referenced from both abric.mod.json files.
- **Audit results:** Formulas were compared line-by-line against the reference client. Fixed during the audit: target priority distance bases (Nearest measures player-feet to entity-feet; FOV/HurtTick secondaries measure eye to entity), FOV and priority now always use the real player rotation even while silent, and the module suffix reports the Aim Math selection like the reference. Verified identical: eased step formula, NATURAL smooth-velocity model and clamps, aim-point center-lerp weighting, randomize offsets, fallback grid dimensions, FOV half-angle math, priority tie-breaking (first candidate wins, Stream.min semantics), and speed multipliers (setting * 20). Documented deviations: item whitelist uses a text input (comma-separated ids) instead of the reference item-picker widget; the reference's cached occlusion retry is replaced by an equivalent grid search each tick; HurtTick priority currently always sees lastAttackTick 0 because the reference Targets module and its attack tracker are not ported yet; disable does not defer until the silent return finishes (the return still runs after disable).
- **Files affected:** shared/src/main/java/dev/ascend/client/shared/ (AscendApplication, UiCommand, GuiModule, GuiSetting, module/*, combat/*), shared/src/main/resources/assets/ascendclient/html/app.js, style.css, ersions/mc1_21_11/src/client/java/dev/ascend/client/ (AscendClient, AscendScreen, AscendRotations, AscendAimView, mixin/*), ersions/mc1_21_11/src/client/resources/ascendclient-client.mixins.json, ersions/mc1_21_11/src/main/resources/fabric.mod.json, ersions/mc1_21_11/src/test/java/dev/ascend/client/shared/ (ModuleRegistryTest, AimAssistMathTest, ModulePersistenceTest), the same adapter/mixin/metadata set for ersions/mc26_3, and uild.gradle artifact verification class lists.
- **Verification:** .\gradlew.bat build --console=plain - BUILD SUCCESSFUL (daemon Java 25.0.5, 1.21.11 client Java 21, 26.3 client Java 25). :versions:mc1_21_11:test - 38 tests passed, 0 failures (previous 5 application tests plus 33 new module-framework, Aim Assist math, and persistence tests). erifyArtifacts confirmed class lists, bytecode levels (shared major 65; entrypoints 65/69), metadata, GUI resources, mixin configs, and absence of reference-project paths in both JARs. 
ode --check app.js passed. Git is not available on this machine, so a git diff review was not possible; changes were reviewed file-by-file instead.
- **Not verified:** In-game behaviour (module ticking, rotations, silent packet spoofing, mixin application at runtime) was not launched in this phase; the mixin injection points were confirmed against the mapped Minecraft JARs with javap (sendPosition() exists in both targets; getViewVector(float) is final in both; calculateViewVector is instance-final in 1.21.11 and static in 26.3). No hosted CI run was performed.
- **Issues/blockers:** No git executable on PATH. The reference Targets module is required for full targeting parity (entity filters, keep target, anti-bot, teams/friends, attack-cooldown tracking); without it Aim Assist behaves as if no filters are configured, which matches the reference with Targets disabled. Silent-rotation movement correction (reference SilentAim internals) is not ported.
- **Status/roadmap:** Phase B is complete and verified at build/test level. Next recommended phase: port the Targets internal module and attack-cooldown tracking, then in-game verification of Aim Assist on both targets.

### 2026-11-13 â€” Phase 1 multi-version foundation

- **Changed:** Added a root Gradle build with two Loom targets, a Java 21 shared source set compiled independently in both targets, per-target client entrypoints and metadata, Gradle 9.8.2 wrapper/Java 25 daemon criteria, dual-target CI, and this implementation handoff.
- **Files affected:** `README.md`, `settings.gradle`, `build.gradle`, `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, `gradle/wrapper/`, `gradlew`, `gradlew.bat`, `.github/workflows/build.yml`, `shared/`, and `versions/`.
- **Verification:** Both target builds and launcher/daemon checks passed; generated JAR metadata, entrypoints, shared bytecode baseline, and reference exclusion were inspected. No tests exist yet. CI and interactive game startup were not run.
- **Issues:** Gradle reports a deprecation that may affect Gradle 10; exact source not isolated. Rendering/input compatibility beyond the bootstrap remains unverified, particularly for 26.3.
- **Status/roadmap:** Phase 1 is complete. Compatibility/interface research and all client functionality remain future work.
- **Architecture decision:** Keep one shared Minecraft-independent source directory compiled by each version-specific Loom project; do not introduce `:core` unless independent publishing/testing or multiple consumers later justify the added module and its dependency/remapping validation.

### 2026-11-13 â€” Phase 1 final verification

- **Changed:** Added `verifyArtifacts` to check both built JARs' metadata, class counts, bytecode levels, and reference-project exclusion; added it to the push/pull-request workflow. Captured each mod version during Gradle configuration to remove the Gradle 10 deprecation, ignored development-client `run/` data, and updated this README with the complete project tree and verified results.
- **Files affected:** `.gitignore`, `README.md`, `build.gradle`, `.github/workflows/build.yml`, `versions/mc1_21_11/build.gradle`, and `versions/mc26_3/build.gradle`.
- **Verification:** `.\gradlew.bat clean buildAll verifyGradleDaemonJava verifyDevelopmentLaunchers verifyArtifacts --warning-mode all` succeeded in 4s (18 actionable tasks; 17 executed, 1 up-to-date). Both artifacts were independently inspected. Both development clients loaded Ascend and reached their title screens; the 1.21.11 client additionally entered a single-player world. No startup crash or entrypoint error occurred.
- **Issues:** Non-fatal missing-empty-classpath warnings occurred at startup. The 1.21.11 integrated server logged a `RejectedExecutionException` during shutdown; both test clients emitted expected authentication/Realms errors for the placeholder development account. No hosted CI run or test-source execution occurred; the test tasks are `NO-SOURCE`.
- **Status/roadmap:** Phase 1 final verification is complete. Rendering/input compatibility research and all shared module, settings, configuration, layout, and gameplay functionality remain planned.
- **Gradle finding:** The removed Gradle 10 deprecation originated in this project's `processResources` closures, which accessed `project.version` during task execution. Final `--warning-mode all` output contains no deprecation warning; only Gradle's optional configuration-cache suggestion remains.

### 2026-11-14 â€” Pre-publish shutdown investigation

- **Changed:** Updated this README with the shutdown investigation and final publication checks.
- **Investigation:** The earlier 1.21.11 shutdown log tags `Could not schedule ConsecutiveExecutor` and `RejectedExecutionException` as `(Minecraft)` on the server thread. Ascend's only server-independent shared method formats the startup message, and its client initializer only logs that message; neither Ascend source set uses an executor or registers shutdown work. Repeating the 1.21.11 launch, entering the integrated single-player world, and closing it gracefully did not reproduce the exception. The warning is therefore attributed to a transient Minecraft integrated-server shutdown path, not Ascend. The original log did not contain stack frames, so the specific internal Minecraft caller could not be established.
- **Verification:** Confirmed the 1.21.11 Fabric/Ascend startup log and clean repeat shutdown; previously captured 26.3 runtime output confirms Fabric/Ascend initialization and title-screen startup. Both JARs passed `verifyArtifacts` and independent content inspection in the clean build recorded above. `git diff --check` passes.
- **Status:** No code change was warranted for the shutdown warning. Both target startup results and the warning attribution are documented; no CI run was performed locally.

### 2026-11-14 â€” Phase 2 GUI foundation

- **Changed:** Added a shared GUI state/command model, validated preferences and JSON persistence, bundled Ascend Primary HTML/CSS/JavaScript resources, and version-specific Rinku browser screens with a restricted classpath resource handler and native UI bridge. Updated the artifact verifier to require the GUI classes and resources. Corrected JavaScript to consume the snapshot response actually sent by the bridge and removed temporary test-only auto-open hooks.
- **Files affected:** `README.md`, `build.gradle`, `gradle.properties`, both version `build.gradle` files, both client entrypoints and mod metadata, both `AscendScreen.java` adapters, `shared/src/main/java/dev/ascend/client/shared/`, `shared/src/main/resources/assets/ascendclient/`, and the 1.21.11 shared-application tests.
- **Verification:** Clean dual-target build, three unit tests, launcher checks, artifact verification, JavaScript syntax check, and `git diff --check` passed. Both clients rendered the GUI from the title screen with the native bridge connected. No hosted CI run was performed.
- **Issues:** Development clients still log non-fatal missing-empty-classpath warnings and placeholder-account authentication errors. The dropdown layout, live layout selection, module registry, and gameplay modules remain unimplemented.
- **Status/roadmap:** GUI foundation is in progress; the module system and second layout are next. The approved shared-source/two-Loom architecture is unchanged.

### Canonical directory synchronization and launch

- **Synchronization:** The separate clone at `C:\Clients\Ascend Client` fetched and checked out `feature/prestige-web-gui` from `origin`. The commit is `e4e1932cf80c7c603f0b73b51fa5022cfc50da29`; it follows the Phase 1 commit `f69377fd95c71d71fe34e250f51392f9a8006704`. The remote branch resolved to the same commit.
- **Canonical build:** From `C:\Clients\Ascend Client`, `.\gradlew.bat --no-daemon clean buildAll :versions:mc1_21_11:test verifyGradleDaemonJava verifyDevelopmentLaunchers verifyArtifacts --warning-mode all` succeeded in 2m 12s (22 actionable tasks: 20 executed, 2 up-to-date). All 3 shared-application tests passed. Both client launcher checks and both artifact checks passed. The JARs were 48,436 bytes (1.21.11) and 48,056 bytes (26.3); both contain the GUI resources, correct metadata, and Java 21 shared bytecode. The 1.21.11 Loom configuration emitted the non-fatal message `Cannot remap modifiers because it does not exist in any of the targets [] or their parents.`; it did not prevent compilation or packaging.
- **Canonical runtime:** `:versions:mc1_21_11:runClient` was launched from the canonical directory using Temurin Java 21.0.13. Fabric loaded Ascend, Rinku 3.0.4 initialized, and the bundled GUI rendered in the Minecraft window with its bridge connected. Settings and Appearance views rendered, and the GUI was closed and reopened with its keybind. The live game was left running for owner testing. Its log is `versions/mc1_21_11/run/logs/latest.log`.
- **Remaining runtime messages:** FabricLoader warned about absent empty development classpath directories. The placeholder development account generated 401 user-properties/Realms errors. Neither prevented the client or GUI from running. The canonical Git working tree was clean after the build; generated build and run data are ignored.
