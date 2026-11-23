# Git workflow, review evidence and best-practice proposal

## Why this flow fits the project

The repository's [contribution rules](../../../CONTRIBUTING.md) use feature branches targeting team `main`, PR review, 1-2 reviewers and coordinator integration. Small tasks have different owners but share menu, drawing and gameplay files. A protected integration branch keeps independently changing features reviewable, lets CI check each proposed change and gives the coordinator one place to resolve dependencies. Short feature branches also make feedback easier to act on before more code depends on a decision.

For this team, a lightweight feature-branch/PR flow avoids maintaining extra long-lived integration/release branches. Its cost is integration delay and conflicts when dependent work stays separate too long. PR #32 demonstrates that cost: the base Endless integration and newer main fixes overlapped, producing conflicts that needed semantic resolution and connected regression checks.

## Actual example: branch complete is not Done

1. Yoonsoo extended existing team Endless code with layouts, movement, shooting rules and checks in `codex/endless-formations` (feature commit `8e4322b`).
2. After other PRs changed main, PR #32 received a conflict report. Resolution commit `540484b` integrated current main while preserving both formation behavior and newer menu/boss fixes.
3. The coordinator's [review](https://github.com/1322528197ldy-tech/KFC-Invaders-SDP-23621/pull/32#pullrequestreview-5868774561) states what compiled and which checks passed, and explicitly leaves human balance feedback separate.
4. The coordinator merged PR #32 on Nov 12 (`d097bd9`). Jira KAN-5/KAN-6 are Done, while KAN-8 remains open for the larger milestone.

The contribution policy recommends squash merging; this report does not assume every historical PR used that method. Describe the agreed policy separately from each actual integration record.

## CLI procedure for future changes

The following is a reproducible example, not a claim that every past operation used these exact commands. Start with a clean working tree; preserve any local work before switching branches.

```powershell
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c codex/short-task-name
# Edit one scoped change and run its relevant checks.
git diff --check
git add path/to/changed-file
git commit -m "docs: record project evidence"
git push -u origin codex/short-task-name
# If GitHub CLI is available:
gh pr create --base main --fill
```

If main advances, fetch and merge `origin/main` into the feature branch, resolve file contents, rerun relevant checks and push the same branch. Avoid discarding one side of shared-file conflicts blindly. CLI work preserves explicit version history; the PR supplies review and discussion evidence. CLI/API-created PRs still require team review before merging.

## More than one PR: collaboration evidence

| Evidence | Specific contribution / limitation |
| --- | --- |
| [PR #32](https://github.com/1322528197ldy-tech/KFC-Invaders-SDP-23621/pull/32) | Own feature, conflict resolution, reviewed integration and test evidence |
| [Comment on #40](https://github.com/1322528197ldy-tech/KFC-Invaders-SDP-23621/pull/40#issuecomment-7010563375) | Requested focused movement coverage and aligned Stage 3 expectations; code-only review |
| [Comment on #43](https://github.com/1322528197ldy-tech/KFC-Invaders-SDP-23621/pull/43#issuecomment-7103106930) | Ran isolated checks and suggested paused profile-switch regression; explicitly excluded native display/manual testing |

These are two comments on other members' PRs, not two formal GitHub approvals or independently authored features. Tool-assisted review records need the same factual scope and human accountability as other project evidence.

## Proposed best practice supported by this experience

**Make every integration decision traceable from task to reviewed change to reproducible checks, and keep human experience separate from automation.** PR #32's approval was useful because it named preserved behavior, checks and remaining balance work. Jira's wave-9 note was useful because it stated the actual human coverage limit. The remaining improvement is to put those links, explicit scenarios and follow-up dates in one place, rather than calling a merged branch the completed milestone.

For quality improvement, test high-risk shared transitions after merges and reserve human testing for fairness/readability that assertions cannot establish. Measure complexity or migrate selected checks to JUnit only if the quality owner chooses it; neither has been demonstrated by the evidence gathered here.
