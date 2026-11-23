# KFC project evidence - Yoonsoo

Prepared on 2026-11-24 for the second-half IC-PBL project. This is an evidence index and proposed follow-up plan, not a claim that the team milestone or individual submissions are complete.

## Why these artifacts are included

Lecture 10, pages 13-15, emphasizes the development process: agreed Git workflow, suitable Agile process, planning/estimation/tracking, quality and testing. CI/CD is optional. The lecture sets the final presentation for December 8. The supplied oral-feedback notes additionally ask for a README, clearer PR evidence, test documentation and a formal presentation; exact submission format and presentation/Q&A timing still need confirmation from the course announcement.

| Practice | Evidence | What it demonstrates |
| --- | --- | --- |
| Requirements and ownership | [Formation implementation](../endless-formations-yoonsoo.md), [PR #32](https://github.com/1322528197ldy-tech/KFC-Invaders-SDP-23621/pull/32) | Five formations, three movement styles, three firing patterns; preserves existing team Endless systems |
| Review and integration | [Approval](https://github.com/1322528197ldy-tech/KFC-Invaders-SDP-23621/pull/32#pullrequestreview-5868774561), [workflow analysis](workflow-review.md) | Conflict resolution, explicit review scope and coordinator-controlled merge |
| Planning | [Jira plan and risk register](jira-plan.md) | Existing completed work, remaining scope, proposed estimates/dates/dependencies |
| Verification | [Test slice and execution record](test-slice.md), [raw run summary](main-endless-validation.txt) | Reproducible automated checks and explicit human-test gaps |
| Collaboration | [Review on #40](https://github.com/1322528197ldy-tech/KFC-Invaders-SDP-23621/pull/40#issuecomment-7010563375), [review on #43](https://github.com/1322528197ldy-tech/KFC-Invaders-SDP-23621/pull/43#issuecomment-7103106930) | Specific test-coverage suggestions rather than unsupported approval |
| Communication | [Presentation outline](presentation-outline.md) | Links process choices to observed results and limitations |
| CI/CD | [CI](../../../.github/workflows/ci.yml), [release](../../../.github/workflows/release.yml) | Compile/startup smoke checks and packaged release workflow; not full Endless regression CI |

## Status and attribution

- PR #32 was approved and merged on November 12; merge commit `d097bd98b8a64c864510bb352c181c773172beaa`.
- Yoonsoo's formation/pattern work extends the team's existing Endless core, rewards and bosses. These earlier systems must not be attributed solely to Yoonsoo.
- [KAN-5](https://1322528197ldy.atlassian.net/browse/KAN-5) is Done. Its coordinator comment records Jeremie testing main through wave 9 without finding bugs. Early-wave difficulty is a follow-up; wave 10 onward was not covered by that human test.
- [KAN-6](https://1322528197ldy.atlassian.net/browse/KAN-6) is Done, but [KAN-8](https://1322528197ldy.atlassian.net/browse/KAN-8) remains In Progress. Do not equate a merged feature with completion of the full playable milestone.
- Automated checks below were rerun against main `35fcd2ccfe3025137509e5bcb22cf43c2934314b`. PR #43 was still open when checked; its settings/test-profile behavior is not the main baseline.
- Personal evaluations, reflection, LMS submission receipts and private communications are not included in this public repository evidence set.

## Submission assembly

Include this index, referenced source/tests/screenshots, PR/review links and execution evidence in the final handover. Before submission, replace provisional planning with agreed dates, record human playtest results and pin the final source/release commit. Confirm LMS format/deadline and instructor Jira access with the coordinator. A configured release workflow alone is not proof that the selected release succeeded.
