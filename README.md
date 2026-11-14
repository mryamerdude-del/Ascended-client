# Ascend Client

Ascend Client is an independently developed Fabric client project. The repository now contains a minimal, buildable bootstrap for Minecraft Java Edition 1.21.11 and 26.3. It does **not** yet contain gameplay features, modules, settings, configuration, rendering, or GUI functionality.

The long-term product requirements are two selectable GUI layouts (an original, Ascend-branded Prestige-inspired layout and a dropdown layout) sharing the same module/settings behavior. Users must be able to switch layouts without restarting Minecraft, and the selected layout must persist between sessions. These remain requirements, not implemented features.

OpenPrestige material under `Refrence/` and `extracted/` is research material only. Its license has not been verified to permit reuse. Do not copy its code or assets into Ascend.

## Current implementation

Phase 1 establishes the build and compatibility foundation:

- Two independent Fabric Loom projects build separate client mod JARs.
- Each project has its own client entrypoint and `fabric.mod.json`.
- Both entrypoints call the same Minecraft-independent Java class from `shared/src/main/java`.
- Shared source is compiled independently in both target projects with Java 21 and packaged into both JARs.
- The 1.21.11 adapter compiles and launches with Java 21; the 26.3 adapter compiles and launches with Java 25.
- GitHub Actions builds both targets on pushes and pull requests.

Only the startup message is implemented. No renderer, input system, module registry, settings model, configuration, GUI, injector, or gameplay functionality has been started.

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
|-- shared/src/main/java/dev/ascend/client/shared/AscendBootstrap.java
`-- versions/
    |-- mc1_21_11/
    |   |-- build.gradle
    |   `-- src/
    |       |-- client/java/dev/ascend/client/AscendClient.java
    |       `-- main/resources/fabric.mod.json
    `-- mc26_3/
        |-- build.gradle
        `-- src/
            |-- client/java/dev/ascend/client/AscendClient.java
            `-- main/resources/fabric.mod.json
```

The root Gradle build includes `:versions:mc1_21_11` and `:versions:mc26_3`. In each Loom project, a custom `shared` source set points to the same repository-level shared directory. It has no Minecraft, Fabric, Mixin, or version-specific dependency. The shared task compiles it with Java 21; each client source set compiles at its target Java version. Each target explicitly adds the shared output to its JAR. This avoids a cross-Loom project dependency and ensures both targets package the same shared implementation.

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

On macOS or Linux, use `./gradlew` with the same task names. `buildAll` builds both target projects. `verifyGradleDaemonJava` fails unless the daemon is Java 25; `verifyDevelopmentLaunchers` checks the configured `runClient` Java launcher for each target without starting a game window. The version-specific `runClient` tasks launch the development clients using their configured Java toolchains.

The distributable artifacts are:

- `versions/mc1_21_11/build/libs/ascend-client-mc1.21.11-0.1.0.jar`
- `versions/mc26_3/build/libs/ascend-client-mc26.3-0.1.0.jar`

## CI

`.github/workflows/build.yml` runs on every push and pull request. It installs Java 21 and Java 25, builds both targets, verifies the Gradle daemon and both client launchers, runs `verifyArtifacts` against the generated JARs, and uploads both mod JARs as workflow artifacts. A failure in either target or artifact check fails the job.

## Verification record

Verified locally with Gradle 9.8.2 and Java 25.0.5 as the daemon JVM:

- `.\gradlew.bat clean buildAll verifyGradleDaemonJava verifyDevelopmentLaunchers verifyArtifacts --warning-mode all` — **BUILD SUCCESSFUL in 4s; 18 actionable tasks (17 executed, 1 up-to-date)**. Both shared-source and client compilation tasks, both target builds, daemon/launcher checks, and artifact verification passed.
- The launcher checks selected Java 21.0.13 for Minecraft 1.21.11 and Java 25.0.5 for Minecraft 26.3.
- `verifyArtifacts` and a separate inspection of the resulting archives confirmed exactly one copy of each entrypoint and shared class, one root `fabric.mod.json`, the expected `ascendclient` mod ID, Minecraft version and client entrypoint, and no OpenPrestige/reference-project archive entries. The shared class is Java 21 bytecode (major 65) in both JARs; the target entrypoint is major 65 for 1.21.11 and major 69 for 26.3.
- Artifact details: `ascend-client-mc1.21.11-0.1.0.jar` — 2,902 bytes; `ascend-client-mc26.3-0.1.0.jar` — 2,797 bytes.
- Both development clients were launched. Fabric loaded Ascend Client, each logged its expected `Ascend Client bootstrap initialized for Minecraft <version>` message, and each reached the Minecraft title screen. The 1.21.11 client was also taken into a single-player world. No startup crash or entrypoint error occurred. The 26.3 client closed cleanly. One 1.21.11 run logged `Could not schedule ConsecutiveExecutor` / `RejectedExecutionException` during integrated-server shutdown under the `(Minecraft)` logger. Ascend has no shutdown hook, server callback, or executor use; a controlled repeat started and closed the same world without reproducing the exception. This is attributed to a transient Minecraft integrated-server shutdown path, not Ascend; the original stack trace did not include frames to isolate the internal caller.
- Both clients logged non-fatal warnings for missing empty source-set output directories. The development session also logged 401 user-properties and Realms authentication errors because its test account uses a placeholder token; these did not block startup or title-screen access.
- The Gradle 10 deprecation warning was traced to this build's resource-processing closures reading `project.version` during task execution. Capturing the version during configuration removed it; the final `--warning-mode all` build emitted no Gradle deprecation warning. Gradle only suggested enabling the optional configuration cache.
- There are no test sources yet; Gradle reported both test tasks as `NO-SOURCE`. The GitHub Actions workflow was updated, but no hosted CI run was performed for this change.

## Compatibility boundaries and open issues

- The bootstrap has been compiled and both development clients reached their title screens against the selected Minecraft/Fabric dependencies. This does not establish compatibility of future rendering or input implementations.
- No renderer is implemented. Before implementing one, inspect the exact resolved 26.3 Minecraft and Fabric API classes; documentation for 26.2 alone is not evidence of 26.3 compatibility.
- Keep rendering, input, events, and Minecraft/Fabric-specific behavior in the version adapters when the APIs differ. Shared code must remain Java-only and Minecraft-independent.
- Java 25 is required to run the Gradle daemon and the 26.3 development client. Java 21 is required for the 1.21.11 client and shared-source compilation.
- The final Gradle 9.8.2 build emits no Gradle deprecation warning; Gradle 10 compatibility has not been tested.

## Roadmap

1. **Foundation — complete:** dual Loom targets, shared Java 21 compilation, target entrypoints and metadata, wrapper/toolchains, CI, automated artifact verification, and local title-screen startup verification for both targets.
2. **Compatibility and contracts — next:** inspect exact 26.3 rendering, input, and event APIs and define version-adapter interfaces before adding Minecraft integration.
3. **Shared client model:** design and implement the shared module lifecycle, registry, typed settings, and configuration contracts without Minecraft imports.
4. **Presentation and persistence:** independently implement the Ascend-branded Prestige-inspired and dropdown layouts over the shared model; support runtime switching and persistent selection.
5. **Validation and release:** add focused tests, verify both target clients and feature parity, document compatibility, and prepare distribution only after requirements are established.

## Technical references

- [Fabric develop/version information](https://fabricmc.net/develop/)
- [Fabric example mod — Minecraft 1.21.11](https://github.com/FabricMC/fabric-example-mod/tree/1.21.11)
- [Fabric example mod — Minecraft 26.3](https://github.com/FabricMC/fabric-example-mod/tree/26.3)
- [Fabric Loom documentation](https://docs.fabricmc.net/develop/loom/)
- [Gradle Java compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)

## Documentation and handoff log

### 2026-11-13 — Phase 1 multi-version foundation

- **Changed:** Added a root Gradle build with two Loom targets, a Java 21 shared source set compiled independently in both targets, per-target client entrypoints and metadata, Gradle 9.8.2 wrapper/Java 25 daemon criteria, dual-target CI, and this implementation handoff.
- **Files affected:** `README.md`, `settings.gradle`, `build.gradle`, `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, `gradle/wrapper/`, `gradlew`, `gradlew.bat`, `.github/workflows/build.yml`, `shared/`, and `versions/`.
- **Verification:** Both target builds and launcher/daemon checks passed; generated JAR metadata, entrypoints, shared bytecode baseline, and reference exclusion were inspected. No tests exist yet. CI and interactive game startup were not run.
- **Issues:** Gradle reports a deprecation that may affect Gradle 10; exact source not isolated. Rendering/input compatibility beyond the bootstrap remains unverified, particularly for 26.3.
- **Status/roadmap:** Phase 1 is complete. Compatibility/interface research and all client functionality remain future work.
- **Architecture decision:** Keep one shared Minecraft-independent source directory compiled by each version-specific Loom project; do not introduce `:core` unless independent publishing/testing or multiple consumers later justify the added module and its dependency/remapping validation.

### 2026-11-13 — Phase 1 final verification

- **Changed:** Added `verifyArtifacts` to check both built JARs' metadata, class counts, bytecode levels, and reference-project exclusion; added it to the push/pull-request workflow. Captured each mod version during Gradle configuration to remove the Gradle 10 deprecation, ignored development-client `run/` data, and updated this README with the complete project tree and verified results.
- **Files affected:** `.gitignore`, `README.md`, `build.gradle`, `.github/workflows/build.yml`, `versions/mc1_21_11/build.gradle`, and `versions/mc26_3/build.gradle`.
- **Verification:** `.\gradlew.bat clean buildAll verifyGradleDaemonJava verifyDevelopmentLaunchers verifyArtifacts --warning-mode all` succeeded in 4s (18 actionable tasks; 17 executed, 1 up-to-date). Both artifacts were independently inspected. Both development clients loaded Ascend and reached their title screens; the 1.21.11 client additionally entered a single-player world. No startup crash or entrypoint error occurred.
- **Issues:** Non-fatal missing-empty-classpath warnings occurred at startup. The 1.21.11 integrated server logged a `RejectedExecutionException` during shutdown; both test clients emitted expected authentication/Realms errors for the placeholder development account. No hosted CI run or test-source execution occurred; the test tasks are `NO-SOURCE`.
- **Status/roadmap:** Phase 1 final verification is complete. Rendering/input compatibility research and all shared module, settings, configuration, layout, and gameplay functionality remain planned.
- **Gradle finding:** The removed Gradle 10 deprecation originated in this project's `processResources` closures, which accessed `project.version` during task execution. Final `--warning-mode all` output contains no deprecation warning; only Gradle's optional configuration-cache suggestion remains.

### 2026-11-14 — Pre-publish shutdown investigation

- **Changed:** Updated this README with the shutdown investigation and final publication checks.
- **Investigation:** The earlier 1.21.11 shutdown log tags `Could not schedule ConsecutiveExecutor` and `RejectedExecutionException` as `(Minecraft)` on the server thread. Ascend's only server-independent shared method formats the startup message, and its client initializer only logs that message; neither Ascend source set uses an executor or registers shutdown work. Repeating the 1.21.11 launch, entering the integrated single-player world, and closing it gracefully did not reproduce the exception. The warning is therefore attributed to a transient Minecraft integrated-server shutdown path, not Ascend. The original log did not contain stack frames, so the specific internal Minecraft caller could not be established.
- **Verification:** Confirmed the 1.21.11 Fabric/Ascend startup log and clean repeat shutdown; previously captured 26.3 runtime output confirms Fabric/Ascend initialization and title-screen startup. Both JARs passed `verifyArtifacts` and independent content inspection in the clean build recorded above. `git diff --check` passes.
- **Status:** No code change was warranted for the shutdown warning. Both target startup results and the warning attribution are documented; no CI run was performed locally.
