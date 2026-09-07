---
name: decision-record
description: Write a new numbered "decision record" doc into sportshub-backend/docs following the repo's house style, and add it to the 00-README.md index.
---

<!-- twin file: this skill also lives at .claude/skills/decision-record/SKILL.md in
     the sibling dtfb-frontend-ng checkout — keep both copies in sync by hand. -->

# decision-record

Write a new numbered doc into `sportshub-backend/docs/` — following that repo's
existing, unwritten-but-consistent house style for "decision record" docs — and
add it to `docs/00-README.md`'s index table.

`args` (optional) is the topic of the decision, e.g. `/decision-record match
result reporting via captains`. If omitted, infer the topic from the current
conversation, or ask.

## 1. Locate the docs directory

Never hardcode a path — resolve it so this skill works from any checkout location:

1. Run `git rev-parse --show-toplevel` to get the current repo's root.
2. If `<root>/docs/00-README.md` exists, this *is* `sportshub-backend` — use
   `<root>/docs`.
3. Otherwise, look for a sibling checkout next to `<root>`: check
   `<root>/../sportshub-backend/docs/00-README.md`. If found, use
   `<root>/../sportshub-backend/docs`.
4. If neither resolves, ask the user for the path to their `sportshub-backend`
   checkout — don't guess further.

## 2. Pick the doc number

Read `docs/00-README.md`'s index table and find the highest existing number `N`.
The new doc is `N+1` by default — append at the end.

The repo's own rule (stated at the bottom of `00-README.md`): *"Numbers are a
reading/topic order, not a chronology — renumber on the rare insert."* If the new
topic clearly belongs earlier in the reading order (e.g. it's a foundational model
doc that later docs should read first), say so to the user and offer to renumber
the affected docs — but default to simple append unless they ask for that.

## 3. Gather the content

Pull as much as possible from the current conversation/session context first (this
skill is usually invoked right after a real decision was made or discussed). Ask
the user directly for anything still missing — never invent a Decision, a
rejected alternative, or a "watch for" trigger that wasn't actually discussed.

Needed:

- **Title + subtitle** for the `#` heading.
- **Kind**: one of `model`, `decision`, `ops`, or `model + decision`.
- **Status**: `discussion`, `proposed`, `decision`, or `implemented (YYYY-MM-DD)`.
- **Companion docs** to cross-link (by number).
- **The triggering question** and a one-line **short answer**.
- **Context** — the problem that prompted the doc.
- **Options considered**, each with a verdict, if there was a real menu of choices
  (skip this section if there was really only one viable path).
- **The decision** itself, stated as bullets.
- **Forward-looking triggers** — concrete conditions that would reopen the
  decision — and/or **open questions** still unresolved.
- If the decision is already implemented: what parts of the existing system
  *didn't* need to change (scopes the blast radius), and — if what shipped
  diverged from the original plan — a closing summary of those deviations.

## 4. Write `docs/NN-slug.md`

Follow this skeleton, a distillation of docs 04/06/08/09/11/15/16 in this repo.
Include only the sections that actually apply — don't pad with empty ones.

```markdown
# <Topic> — <subtitle>

> **<Kind/Status tag>.** <One-paragraph summary: what changed / what was decided,
> in the same terse, code-identifier-heavy style as the rest of docs/.>
>
> See also: [NN-other-doc.md](./NN-other-doc.md).
>
> Question raised: <the question that prompted this doc>
> Short answer: **<one-line answer>**

## Context
<or "## The problem" — why this needed deciding, what was true before.>

## What did NOT need to change
<Only for implemented decisions — scope the blast radius explicitly.>

## Options considered
| | How | Verdict |
|---|-----|---------|
| ... | ... | ... |
<Only if there was a real menu of alternatives. Name every option, including the
rejected ones, and say explicitly why each was rejected — not just why the
winner won.>

## Decision
- Now: ...
- Watch for: ...
- If triggered: ...

## Open questions
<Or "## Watch for / triggers to revisit" — forward-looking, falsifiable
conditions, not just a vague "revisit later".>

## Summary of deviations from the original design
<Only if this doc records something already implemented AND reality diverged
from the plan — itemize each deviation.>
```

Style notes (match the rest of `docs/`, don't drift into generic ADR boilerplate):
- Terse and technical. Reference real entity/method/endpoint names in backticks.
- State decisions as falsifiable claims with rationale, not hedged prose.
- Cross-link related docs with relative markdown links
  (`[NN-slug.md](./NN-slug.md)`); if this doc supersedes part of an older one, say
  so explicitly (e.g. *"Supersedes doc 01 §1.1"*) — ask the user which doc, if
  any, is superseded rather than assuming.

## 5. Update `docs/00-README.md`

Insert one new row into the index table, in the same style as the existing rows
(bold `**Implemented**`/`**Proposed**` prefix in the description where relevant),
just before the closing "Numbers are a reading/topic order..." line:

```markdown
| NN | [Title](./NN-slug.md) | <Kind> | <one-line summary, mirroring the tone of the other rows> |
```

## 6. Stop there

This skill only touches `docs/`. Never edit code, never run `git commit` or
`git add` — show the new file and the README diff and let the user review before
anything is committed or pushed, per this project's "only commit when explicitly
asked" convention.
