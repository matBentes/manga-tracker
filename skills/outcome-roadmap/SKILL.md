---
name: outcome-roadmap
description: "Turn a list of ideas, problems, or requests into an outcome-based roadmap for manga-tracker. Use this skill when the user wants a roadmap, release themes, quarter planning, milestone grouping, or a clearer sequence of product work beyond a flat backlog."
user-invocable: true
---

# Outcome Roadmap

Organizes future work around outcomes instead of disconnected features.


## The Job

1. Identify the target horizon (next release, month, quarter, etc.)
2. Group work by desired outcomes rather than implementation tasks
3. Map candidate features to those outcomes
4. Recommend sequencing and milestones
5. Save the roadmap to `tasks/roadmap-[period].md`

Do NOT turn this into an implementation plan unless the user explicitly asks for PRDs or stories afterward.


## Good Outcome Themes For This Repo

Prefer themes like:

- **Tracking Reliability**
  - stable add/update/delete flows
  - resilient scraper behavior
- **Notification Trust**
  - correct settings
  - reliable email delivery
  - fewer false positives
- **Delivery Confidence**
  - stronger CI
  - less flaky integration coverage
  - safer release automation
- **User Efficiency**
  - faster reading list updates
  - better visibility into chapter progress

Avoid roadmap buckets like:

- "Backend work"
- "Frontend cleanup"
- "Misc fixes"


## Roadmap Rules

- Each outcome must describe a user or business result
- Each outcome should have 2-5 supporting initiatives max
- Call out dependencies explicitly
- Separate:
  - **Now**
  - **Next**
  - **Later**
- Note what will intentionally not be pursued in the current period


## Output Format

```markdown
# Outcome Roadmap — [Period]

## Goal For The Period
- ...

## Now
### Outcome 1: [Outcome]
- Why it matters:
- Success signal:
- Supporting initiatives:

## Next
### Outcome 2: [Outcome]
- Why it matters:
- Success signal:
- Supporting initiatives:

## Later
### Outcome 3: [Outcome]
- Why it matters:
- Supporting initiatives:

## Dependencies
- ...

## Not In This Period
- ...
```


## Output

- **Format:** Markdown (`.md`)
- **Location:** `tasks/`
- **Filename:** `roadmap-[period].md`
