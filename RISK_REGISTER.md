# MCMetal Risk Register

Status: Active  
Last updated: 2026-02-19  
Review cadence: Bi-weekly (or immediately when severity changes)

## Risk scoring model

- Likelihood: Low, Medium, High
- Impact: Low, Medium, High
- Priority: Determined by likelihood x impact and release timing sensitivity

## Active risks

| ID | Risk | Likelihood | Impact | Priority | Mitigation plan | Mitigation owner | Trigger/indicator | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R-001 | OpenGL implicit state does not translate cleanly to Metal explicit state model. | Medium | High | High | Build normalized state tracker, deterministic keys, and transition assertions in debug mode. | Rendering Lead | Frequent pipeline mismatches, visual parity failures, validation warnings. | Open |
| R-002 | GLSL -> SPIR-V -> MSL translation edge cases break shader coverage. | Medium | High | High | Maintain shader corpus tests, reflection validation, and detailed compile diagnostics. | Shader Lead | Unsupported constructs in translated shaders, shader compile failures. | Open |
| R-003 | Minecraft/Fabric updates break mixin hooks or mappings. | High | Medium | High | Keep adaptation layer centralized, minimize invasive hooks, and run version smoke checks per update. | Java Integration Lead | Build failures or runtime hook errors after dependency updates. | Open |
| R-004 | Resource lifetime bugs cause leaks, use-after-free, or GPU stalls. | Medium | High | High | Enforce deferred destruction queues, frame completion fences, and lifetime-focused tests. | Native Lead | Memory growth over long sessions, GPU timeout/stall telemetry. | Open |
| R-005 | Rendering-overhaul mod conflicts create undefined behavior or crashes. | High | Medium | High | Detect incompatible mods at startup and safe-disable with actionable messaging. | Compatibility Lead | Crash reports with known conflicting mods loaded. | Open |
| R-006 | Intel macOS support diverges from Apple Silicon behavior/performance. | Medium | Medium | Medium | Track architecture-specific regressions and require Intel matrix validation before stable release. | QA Lead | Intel-only rendering artifacts or unacceptable frame pacing. | Open |
| R-007 | Build and packaging process for native artifacts becomes non-reproducible. | Medium | High | High | Pin toolchains, document prerequisites, and enforce reproducibility checks in CI. | Build/CI Lead | Non-deterministic binary outputs, release artifact mismatch. | Open |

## Ownership and maintenance rules

1. Each risk must always have one mitigation owner.
2. A risk owner is responsible for status updates and mitigation progress notes.
3. High-priority risks must be reviewed every bi-weekly review meeting.
4. Closed risks are retained for audit trail and postmortem learning.
