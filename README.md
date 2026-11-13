# Ascend Client

Ascend Client is an intended Minecraft client project. This repository is currently a documentation and Git scaffold, not a verified working client implementation.

The GitHub repository is [`mryamerdude-del/Ascended-client`](https://github.com/mryamerdude-del/Ascended-client), on the `main` branch. The local project directory is `C:\Clients\Ascend Client`.

## START HERE — NEW AI DEVELOPER

Read this section and the rest of this README before starting work. The root `AGENTS.md` makes the required workflow portable to every AI agent and task.

### What Ascend is intended to become

Ascend is intended to become a Minecraft client with an Ascend-branded interface and two selectable GUI layouts:

- **Prestige-inspired layout:** the primary/default design direction. It may take broad presentation inspiration from the reference, but must be implemented independently, use Ascend branding, and must not copy OpenPrestige source code or assets.
- **Dropdown layout:** an alternative design selectable from within the client.

Both layouts are required to use the same underlying module and settings system. The user must be able to switch layouts without restarting Minecraft, and the selected layout must be remembered between sessions.

These are requirements, not implemented features.

### What currently exists

The tracked repository contains:

```text
C:\Clients\Ascend Client\
|-- .gitignore
|-- AGENTS.md
`-- README.md
```

`.git` is repository metadata, not an Ascend implementation directory. The working tree also has two ignored, local-only reference directories:

```text
C:\Clients\Ascend Client\
|-- Refrence\
|   |-- OpenPrestige-main (2).zip
|   `-- fresh_reference\openprestige\...   (reference extraction)
`-- extracted\
    `-- openprestige\...                   (reference extraction)
```

The two `openprestige` directories are reference material, not Ascend source. They are excluded from Git along with the archive. Their presence does not establish that Ascend has a source tree.

**Current implementation status:**

- No verified Ascend Client implementation has been uploaded.
- No Ascend source architecture has been verified.
- No Gradle build configuration has been verified for Ascend.
- No Minecraft version is established by the repository.
- Development has not reached the implementation stage.
- No Java, Fabric, or Minecraft implementation files are tracked.
- No Ascend dependencies, build commands, launch commands, or tests are currently confirmed.

The only existing README content before this handoff was the repository name, `# Ascended-client`. That identity has been preserved and clarified; no implementation details were present to retain.

### What is missing

There is no verified Ascend application entrypoint, mod metadata, Java/Kotlin source, module implementation, settings implementation, event system, GUI implementation, persistence layer, Gradle wrapper/build, dependency declaration, automated test suite, or Minecraft-version declaration in the tracked repository. These items must be verified when added; this list describes the current repository, not a promise that every possible component must be built.

### Where to start and what to investigate first

1. Re-read this full document and `AGENTS.md`; inspect the current Git status and tracked files.
2. Confirm project-owner requirements for the target Minecraft version, loader, Java version, supported platforms, distribution, licensing, and any gameplay scope. None is established here.
3. Decide and document the smallest supported project skeleton before adding dependencies. Verify the selected toolchain against official/current project requirements rather than guessing versions.
4. Propose the shared module/settings contract and layout-selection persistence before building either GUI. Avoid designing two independent module systems.
5. Implement only after the owner-approved technical choices are recorded. Keep the two layouts independently authored; do not copy the reference.
6. After each task, update this README with the actual files, changes, verification, issues, status, roadmap, and decisions.

### Decisions already established

- The project identity is **Ascend Client**; the GitHub repository slug remains `Ascended-client`.
- Two GUI layouts are required: a primary Ascend-branded, independently implemented Prestige-inspired layout and an alternative dropdown layout.
- The layouts must share the same module/settings system, be switchable in-client without restarting Minecraft, and remember the selected layout between sessions.
- The OpenPrestige ZIP and extracted copies are reference material only. Their license has not been established as permitting reuse; do not copy their code or assets.
- The current repository does not establish a Minecraft version, loader, dependency set, source architecture, or build process.
- Accuracy takes priority over presenting planned work as implemented.

## Project introduction and goals

The goal is to create an original Minecraft client under the Ascend identity. The confirmed product requirements in this repository are currently limited to the two GUI layouts and their shared module/settings behavior described above. No other gameplay capabilities or feature list has been specified or verified.

This README is the main technical handoff. It distinguishes repository facts from project requirements, recommendations, and reference observations so a future contributor can continue without access to earlier conversations.

## Repository and implementation status

The current branch is `main`. The GitHub repository already had an initial commit containing a minimal README; the initial Ascend Client import added the root `.gitignore`. The existing history was preserved.

The tracked project is a scaffold only. The local `Refrence/` and `extracted/` directories are ignored and are not part of the tracked Ascend repository. In particular, OpenPrestige files found under those directories must not be described as Ascend implementation.

The `.gitignore` currently excludes:

- `Refrence/` and `extracted/`
- `.gradle/`, `build/`, and `**/build/`
- `.env` and `.env.*`
- `*.keystore` and `*.jks`

These patterns are safeguards, not proof that all possible secrets or generated files are excluded. Review staged files before every commit.

## Verified directory structure

### Tracked repository

```text
C:\Clients\Ascend Client\
|-- .gitignore    # Excludes local references, build caches/outputs, and common secret files
|-- AGENTS.md     # Portable mandatory AI contribution workflow
`-- README.md     # Main project status, requirements, and handoff
```

Git also stores history and configuration under `.git/`. It is not application source.

### Local-only reference material

The following paths were present during this investigation. They are ignored by Git and are not Ascend source:

```text
Refrence\
|-- OpenPrestige-main (2).zip
`-- fresh_reference\
    `-- openprestige\...

extracted\
`-- openprestige\
    |-- .github\
    |-- .gitignore
    |-- .gradle\
    |-- build\
    |-- check\
    |-- docs\
    |-- gradle\
    |-- injector\
    |-- libs\
    |-- scripts\
    |-- src\
    |-- build-injector.ps1
    |-- build-injector.sh
    |-- build-release.bat
    |-- build.gradle
    |-- gradle.properties
    |-- gradlew
    |-- gradlew.bat
    |-- README.md
    |-- run.bat
    `-- settings.gradle
```

This is the verified immediate top-level inventory of `extracted/openprestige`, included only to identify the local reference tree’s role. `Refrence/fresh_reference/openprestige` is a comparison extraction with the same general project/source shape. These paths are not tracked, are not dependencies of Ascend, and are not appropriate source for copying. Their nested contents are intentionally not represented as an Ascend project structure.

## Existing files and their purposes

| File/path | Verified purpose |
|---|---|
| `README.md` | This project status, requirements, and AI handoff document. |
| `AGENTS.md` | Concise, repository-root instructions for AI contributors; requires the per-task README workflow. |
| `.gitignore` | Excludes the specified local reference directories, build paths, environment files, and keystore files. |
| `.git/` | Git metadata and history; not client implementation. |
| `Refrence/OpenPrestige-main (2).zip` | User-designated technical/design reference archive. Not tracked. |
| `Refrence/fresh_reference/` | Fresh comparison extraction made during earlier repository investigation. Not tracked. |
| `extracted/openprestige/` | Earlier extracted OpenPrestige reference tree. Not tracked and not Ascend source. |

No other tracked Ascend project files were found in the repository at the time of this documentation update.

## Architecture

**No Ascend source architecture has been verified.** There are no tracked source files from which to describe classes, packages, entrypoints, module lifecycles, event dispatch, settings storage, rendering, input handling, configuration, or networking. Do not infer Ascend architecture from the OpenPrestige reference or from the project name.

The only confirmed architectural requirement is a future shared module/settings system used by both GUI layouts. Its APIs, ownership, lifecycle, event model, and persistence format remain undecided.

## Planned functionality versus implemented functionality

| Area | Status | Confirmed detail |
|---|---|---|
| Ascend Minecraft client | Intended, not implemented | No source, mod metadata, or version is tracked. |
| Prestige-inspired layout | Planned requirement | Primary/default direction; Ascend-branded and independently implemented. |
| Dropdown layout | Planned requirement | Alternative layout, switchable from inside the client. |
| Shared modules and settings | Planned requirement | Both layouts must use the same underlying system; implementation/API is undecided. |
| In-client layout switching | Planned requirement | Must not require restarting Minecraft. |
| Remember selected layout | Planned requirement | Must persist between sessions; storage mechanism is undecided. |
| Other client/gameplay features | Unspecified | Do not invent a feature list. |
| Build, run, and tests | Not established | No Ascend build configuration or verified commands exist. |

## GUI design requirements

The two layouts are different presentations over one shared client state and one shared module/settings system; they are not separate feature implementations.

### Prestige-inspired layout

- Treat this as the primary/default design direction.
- Use Ascend branding and original implementation.
- Take inspiration from overall presentation only; do not reproduce source code or assets.
- Exact visual structure, interactions, accessibility, and styling remain to be designed and approved.

### Dropdown layout

- Provide it as an alternative selectable layout.
- Allow the user to select it while Minecraft is running.
- Define its detailed visual and interaction design before implementation; no such design exists in the repository yet.

### Shared behavior

- Keep module and settings state consistent when changing layouts.
- Switch layouts without restarting Minecraft.
- Persist the chosen layout between client sessions.
- Select and document a persistence mechanism only after the actual project platform and configuration architecture are established.

All items in this section are requirements, not claims about existing code.

## Dependencies and development requirements

No Ascend dependencies or exact development toolchain have been verified. In particular:

- Java is not declared as a repository requirement.
- Minecraft version is unknown.
- Fabric or any other loader is not confirmed for Ascend.
- No Gradle files or wrapper are tracked for Ascend.
- No dependency versions or repositories are declared for Ascend.

The project is intended to become a Minecraft client, but that does not establish its target version, loader, or build system. Choose and document these before implementation. Do not use the reference tree’s Gradle configuration as though it belonged to Ascend.

## Build and execution

There are currently **no verified Ascend build or execution instructions** because no Ascend build configuration or application code is present. Do not run `gradlew`, `run.bat`, or other commands from the ignored OpenPrestige reference tree and report their results as an Ascend build.

When an approved project skeleton is added, document the exact prerequisites and commands, then verify them against that skeleton. Until then, a successful OpenPrestige build would say nothing about Ascend.

## Technical decisions and design principles

Confirmed decisions:

1. Ascend is an independent project. OpenPrestige is a research reference only; reuse rights have not been established.
2. The two GUI layouts share modules and settings rather than duplicating behavior.
3. Layout selection must work at runtime and persist across sessions.
4. The Prestige-inspired direction must be expressed with Ascend branding and independent code/assets.
5. Documentation must state what is verified and must not turn a requirement or recommendation into an implementation claim.

Not yet decided: supported Minecraft/loader/Java versions; architecture and package structure; module lifecycle; settings types and validation; event model; GUI toolkit/rendering API; input abstraction; configuration format and storage location; distribution model; feature scope; test strategy; and license policy for any third-party material.

## Known issues and missing components

- No verified Ascend implementation has been uploaded.
- No source architecture, Minecraft version, loader, or dependencies are established.
- No Gradle build, launch configuration, or test suite exists in the tracked project.
- No module/settings system or GUI exists yet.
- No layout persistence mechanism is selected.
- No detailed GUI mockups or interaction specifications are present.
- The OpenPrestige reference license has not been verified to permit reuse. Keep its source and assets out of Ascend.
- The local reference material is excluded from Git; do not add it to the repository.

## Prioritized development roadmap

This roadmap is a recommendation based on the verified absence of implementation and the requirements above. It is not evidence that any step is complete.

1. **Establish project constraints.** Confirm supported Minecraft version, loader, Java/toolchain, platform scope, distribution, licensing requirements, and the intended non-GUI feature scope with the owner.
2. **Create and verify the project skeleton.** Select the build system and loader only after constraints are known. Add minimal metadata and a build/run path; verify on the selected target.
3. **Design the shared module/settings contract.** Define ownership, lifecycle, setting representation/validation, module state, and extension points before binding these to a GUI.
4. **Design state persistence.** Define how the selected layout is stored and restored, including behavior for missing or invalid values, consistent with the chosen project architecture.
5. **Implement layout switching and shared UI integration.** Build a minimal original Ascend-branded primary layout and alternative dropdown layout on the same module/settings model; verify switching without restart and persisted selection across sessions.
6. **Expand only approved functionality.** Add features when their requirements are explicit; keep status, architecture, and tests documented.
7. **Harden and release.** Add relevant automated/manual verification, build instructions, compatibility declarations, and distribution documentation based on the actual implementation.

## OpenPrestige reference policy

The owner identified `C:\Clients\Ascend Client\Refrence\OpenPrestige-main (2).zip` as a technical/design research reference. A copy was extracted under `extracted\openprestige\`, and an additional fresh comparison extraction exists under `Refrence\fresh_reference\openprestige\`.

These are local, ignored reference materials. Their code, names, assets, and architecture are not verified Ascend components. The reference license has not been established as permitting reuse. Do not copy code or assets into Ascend; implement independently and record any high-level design observations as observations, not as adopted implementation.

## Mandatory AI workflow — every user prompt

Every AI agent working on Ascend must perform this workflow **before and after every task**, including documentation-only tasks and prompts that result in no source changes. The root `AGENTS.md` repeats the rule in concise form.

### Before every task

1. Read the entire `README.md`.
2. Inspect relevant source code and existing project files.
3. Understand the current implementation state; verify README claims against files rather than relying on history or assumptions.
4. Check previous decisions and unfinished work in this README, Git history, and relevant project artifacts.
5. Identify which files and behavior are in scope. Avoid duplicating systems or unnecessary rewrites.

### After every task

1. Update `README.md` and save it.
2. Record what changed and which files were affected.
3. Record tests and verification results; state when no tests were applicable or no build/test setup exists.
4. Record errors, blockers, and unresolved issues.
5. Update implementation status and the roadmap.
6. Record important architectural decisions and discoveries, clearly distinguishing verified facts from plans.
7. For work with no source changes, document relevant discoveries or decisions rather than inventing modifications.
8. Verify the README update was actually saved and review its claims against the repository.

Never claim a README update, test, build, or implementation that did not actually occur.

## Documentation and handoff log

### 2026-11-13 — Repository investigation and initial AI handoff

- **What changed:** Expanded the original one-line README into this repository status, requirements, and AI handoff; added a root `AGENTS.md` with the mandatory portable workflow.
- **Files affected:** `README.md`, `AGENTS.md`.
- **Verified repository state:** `main` tracks only `.gitignore`, `README.md`, and this new `AGENTS.md` after the documentation commit. The local `Refrence/` and `extracted/` trees are ignored and untracked. No Ascend Java/Fabric/Minecraft code or Gradle configuration was found.
- **Verification:** Read the previous README and `.gitignore`; checked current branch, history, tracked files, root directory entries, reference directory roles, and remote `main`. Reviewed the documentation content and paths. No build or test was run because the tracked project has no implementation or build/test setup.
- **Errors and unresolved issues:** No documentation verification error remains. Implementation, toolchain/version, dependencies, architecture, and detailed GUI designs remain unresolved because the repository contains no supporting implementation or decisions.
- **Roadmap/status:** Implementation remains unstarted. The prioritized roadmap above records recommended next steps; it does not mark them complete.
- **Architectural decisions/discoveries:** Only the two-layout GUI requirements and their shared module/settings, runtime-switching, and persistence constraints are established. No implementation architecture has been selected.