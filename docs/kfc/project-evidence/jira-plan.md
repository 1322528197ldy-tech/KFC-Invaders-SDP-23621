# Jira follow-up plan and risk register

Snapshot: KFC Jira board/list and KAN-5/KAN-8 details, inspected 2026-11-24. Board counts were To Do 1, In Progress 3, In Review 0 and Done 9; the list also exposes subtasks. Missing fields in the visible list do not prove the team never estimated work elsewhere.

## Existing work: retain history, avoid duplicate completion cards

| Item | Observed state / owner | Action |
| --- | --- | --- |
| KAN-5, formation review | Done / Yoonsoo | Keep Done; coordinator already records PR #32 merge and wave-9 human coverage |
| KAN-1, Endless playtest | Done / Jeremie | Keep the completed test scope; add later testing as a new follow-up |
| KAN-6, Endless integration | Done / Dongyuan | Preserve integration history |
| KAN-8, playable milestone | In Progress / Dongyuan | Coordinator checks unresolved bugs, run lifecycle and recorded evidence before closure |
| KAN-9, playtest PR #41 | To Do / Jeremie | Existing campaign test task; do not relabel as Endless testing |
| KAN-13 / KAN-20 | In Progress / Haozhe | Reconcile acceptance criteria with merged campaign PRs #40/#41/#42; merge alone is insufficient to close cards |

## Proposed WBS, estimates and schedule

Use relative story points (1, 2, 3, 5) for remaining work, considering complexity, uncertainty and verification. These are proposals made now, not historical estimates or hours already spent. Owners and dates for other members require team agreement. No past worklogs are invented.

| Ref | Work package / proposed owner | Points | Proposed target | Dependency / completion evidence |
| --- | --- | --- | --- | --- |
| F1 | Review early-wave difficulty / Yoonsoo | 3 | Nov 29 | KAN-5 feedback; compare wave 1-9 baseline; record decision and before/after values; any change goes through tests and PR |
| F2 | Human playtest wave 10-15 / Jeremie, proposed | 2 | Dec 1 | F1 candidate build, including bosses 10/15 and reward transitions; record build, environment, observations and reproducible issues |
| F3 | Prepare evidence index and test slice / Yoonsoo | 2 | Dec 3 | This documentation PR reviewed, links checked, automated run recorded, F2 gaps explicitly stated |
| F4 | Quality and CI coverage review / Seonghyeon, proposed | 2 | Dec 3 | Confirm current compile/smoke scope; decide whether adding Endless CI is worthwhile; record decision rather than claiming it exists |
| F5 | Final integration/release and evidence freeze / Dongyuan, proposed | 3 | Dec 5 | F2 blockers fixed/retested, F3/F4 reviewed, KAN-8 criteria met; pin tested release SHA |
| F6 | Formal presentation rehearsal / team, coordinator to assign | 2 | Dec 7 | F5 evidence, verified official time allocation, rehearsal and Q&A ownership |

Suggested flow: To Do -> In Progress -> In Review -> Done. Work enters In Review when code/docs, verification and links are ready. Done requires accepted evidence and resolved blockers, not just a finished branch. Keep WIP small (one active implementation task per person as a proposal), and leave unapproved estimates visibly provisional.

## Risk register

| Risk | Likelihood / impact | Response and trigger | Proposed owner |
| --- | --- | --- | --- |
| Shared-file merge conflicts | High / High | Refresh main before work/review; list shared files and dependencies; rerun connected checks after resolution | Yoonsoo + coordinator |
| Late-wave difficulty untested by humans | High / Medium | F2 covers waves 10-15; log player deaths, clear time, dodge space and item use; do not infer fairness from bounds checks | Jeremie + Yoonsoo |
| CI green but gameplay regressions remain | Medium / High | State compile/smoke limits; attach local full-suite logs; decide full-suite CI scope in F4 | Seonghyeon |
| Test-profile data leaks into normal saves | Medium / High | Isolated disposable saves, packaged-entry checks; separate PR #43 profile-switch review from main validation | Settings owner + reviewer |
| Submission lacks traceable evidence | Medium / High | Index requirement -> task -> PR -> test -> artifact; verify links and final commit before freeze | Yoonsoo + coordinator |
| Unconfirmed course format or access | Medium / High | Check current LMS instructions, instructor Jira access and submission receipts before handover | Coordinator / each student |

## Baseline and monitoring

Record this Nov 24 plan as a new proposed baseline. After team agreement, save agreed owners/points/dates and dependencies in Jira; compare remaining work against that snapshot at each check-in. Record date/scope changes with reasons rather than backdating estimates. Paid Marketplace baseline apps are not required by this plan.
