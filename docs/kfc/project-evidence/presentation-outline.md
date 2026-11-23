# Formal presentation preparation

Lecture 10 names December 8 (Week 15). The student's oral notes mention 20 minutes and graded Q&A; verify whether that means presentation only or includes Q&A. This is a proposed 20-minute content budget, not an official timing rule or a finished team deck.

| Time | Topic | Evidence / intended claim |
| --- | --- | --- |
| 0-2 min | Problem, scope and roles | Existing team game -> Endless priority; distinguish each owner's work |
| 2-5 min | Process and Git choice | Feature branches/PR review, why they suit shared files; one conflict-resolution example |
| 5-8 min | Project management | WBS, estimates, risks, dependencies and Jira history; distinguish current plan from original baseline |
| 8-11 min | Implementation demonstration | Five formations, movement/attack variety; concise before/after with a pinned build |
| 11-15 min | Testing and quality | E01-E06 scenarios, actual run summary, code-review feedback and human-test gaps |
| 15-17 min | CI/CD and collaboration | Exact build/smoke/release scope; review comments; agreed team communication evidence |
| 17-20 min | Results, limitations and best practice | Evidence-based recommendation, unresolved risks and lessons |

If Q&A is included in the 20 minutes, shorten the content budget to reserve the required Q&A time after checking the announcement.

## Suggested English explanation of workflow choice

We use short feature branches and pull requests into the team main branch because several members change shared gameplay and UI code. A pull request makes the dependency, test evidence and reviewer feedback visible before integration. PR #32 showed why this matters: newer main changes conflicted with the formation work, so we preserved both behaviors and reran connected checks before the coordinator merged it. The trade-off is review delay and conflict risk, which we reduce with smaller changes and earlier synchronization.

## Q&A preparation

- **Why is CI green not enough?** Our workflow checks compilation and startup. Local Endless regression checks and human playtesting address additional behaviors; the evidence reports each scope separately.
- **Did Yoonsoo build the whole Endless mode?** No. The team supplied the core/rewards/boss systems; Yoonsoo extended ordinary-enemy layouts, movement and attacks.
- **What proves quality beyond screenshots?** Reproducible check runners, a pinned baseline, review feedback and recorded human coverage. Screenshots demonstrate appearance, not all runtime properties.
- **What is unfinished?** The documented follow-ups include early-wave balance assessment and human testing beyond wave 9. Current Jira milestone status should be rechecked before presenting.
- **Which best practice can another team reuse?** Link task, PR, explicit test scope and artifact; preserve honest limits rather than treating branch completion as validated delivery.

## Final handover checklist

- [ ] Confirm current LMS submission instructions, deadline and presentation timing.
- [ ] Coordinator confirms instructor/TA Jira access and required repository/board link submissions.
- [ ] Record agreed plan, final scope changes and human-test results in Jira.
- [ ] Pin final source commit, successful CI/release run and runnable artifact.
- [ ] Include README/evidence index, test scenarios/results, risks, review examples and permitted screenshots.
- [ ] Prepare inter-team/team-member evaluations using actual observations and official forms; each student owns their ratings.
- [ ] Each student checks their Problem Analysis Sheet submission receipt (lecture deadline was Nov 10, 08:59).
- [ ] Each student verifies and completes an honest personal reflection; no fabricated experiences or retroactive pre-development claims.
