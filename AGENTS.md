# Ascend Client AI contribution rules

This file is the portable workflow for every AI working in this repository. `README.md` is the main technical handoff and is authoritative for verified status, requirements, decisions, and roadmap.

## Before every task

1. Read the entire `README.md`.
2. Inspect relevant source code and existing project files; verify README claims against the actual repository.
3. Understand implementation status, prior decisions, and unfinished work in the README, Git history, and relevant artifacts.
4. Avoid duplicating existing systems, unnecessary rewrites, and regressions to working behavior.
5. Never invent classes, packages, features, dependencies, versions, architecture, build instructions, or completed work. Clearly distinguish facts, requirements, and recommendations.

## After every task

1. Update and save `README.md`, even if the task made no source changes.
2. Record what changed and which files were affected.
3. Record tests and verification results, including when tests were not applicable or no test/build setup exists.
4. Record errors, blockers, and unresolved issues.
5. Update implementation status and the roadmap.
6. Record important architectural decisions and discoveries.
7. For tasks with no source changes, document relevant discoveries or decisions instead of inventing modifications.
8. Verify the README was actually saved and that its claims match the repository.

Test relevant code changes and preserve existing working functionality. Do not claim a file was changed, documentation updated, or test/build run unless it actually happened.

## OpenPrestige reference restriction

`Refrence/` and `extracted/` contain local OpenPrestige research material, not Ascend implementation. Its license has not been verified to permit reuse. Do not copy its source code or assets into Ascend; implement independently.
