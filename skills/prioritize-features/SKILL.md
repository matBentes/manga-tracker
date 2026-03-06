---
name: prioritize-features
description: "Prioritize candidate features or fixes for manga-tracker using a lightweight scoring framework tied to user value, engineering cost, and delivery risk. Use this skill when the user asks what to build next, which backlog item should come first, how to rank options, or whether a feature is worth doing now."
user-invocable: true
---

# Feature Prioritizer

Ranks feature ideas, bug fixes, and improvements for manga-tracker so the next implementation choice is explicit and defensible.


## The Job

1. Gather the candidate items to compare
2. Score each item against the criteria below
3. Produce a ranked list with clear reasoning
4. Save the result to `tasks/priorities-YYYY-MM-DD.md` when the output is substantial

Do NOT start implementing. This skill is for choosing what should happen next.


## Scoring Criteria

Use a simple 1-5 scale for each category:

- **User Value**: How much this helps the core user workflow
- **Strategic Fit**: How well this supports the product direction for manga tracking, notifications, reliability, or maintainability
- **Confidence**: How certain we are about the problem and solution
- **Effort**: Estimated implementation cost
- **Risk**: Likelihood of regressions, migration complexity, or operational instability

Recommended weighted score:

```text
Priority Score = (User Value * 3) + (Strategic Fit * 2) + Confidence - Effort - Risk
```

If the user already prefers another framework (RICE, ICE, MoSCoW), adapt to it.


## Repo-Specific Heuristics

- Prefer work that improves core flows first:
  - add manga
  - track reading progress
  - chapter updates
  - notification reliability
- Prefer changes that reduce CI or release instability when there is active delivery friction
- Penalize work that touches DB schema, CI, and frontend flow at the same time unless the payoff is high
- Penalize scraper work with low confidence or brittle parsing unless there is strong user demand
- Reward work that closes a known gap already surfaced by CI, review, or production risk


## Output Format

Use this structure:

```markdown
# Feature Priorities — YYYY-MM-DD

## Ranked List
1. [Item]
   Score: X
   Why now: ...

## Scoring Table
| Item | User Value | Strategic Fit | Confidence | Effort | Risk | Score |
|------|------------|---------------|------------|--------|------|-------|

## Recommendation
- Build now:
- Next:
- Defer:

## Assumptions
- ...
```


## Output

- **Format:** Markdown (`.md`)
- **Location:** `tasks/`
- **Filename:** `priorities-YYYY-MM-DD.md`
