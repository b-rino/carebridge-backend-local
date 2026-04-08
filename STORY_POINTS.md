# Story Points – What Do They Mean?

Story points are **relative**, not absolute. They don't mean hours or days — they measure a combination of:

- **Effort** — how much work is involved
- **Complexity** — how difficult/tricky is the logic
- **Uncertainty** — how much is unknown going in

---

## The Fibonacci Scale in Practice

| Points | What it typically means |
|--------|------------------------|
| 1 | Trivial — change a label, fix a typo |
| 2 | Very small — add one field, write one simple endpoint |
| 3 | Small but real — a simple feature, maybe 2-3 files touched |
| 5 | Medium — clear scope, some moving parts, predictable |
| **8** | **Large — multiple layers touched, some unknowns, non-trivial design** |
| 13 | Very large — high complexity or high uncertainty, risky |
| 21 | Too big — should be broken into smaller stories |

The reason Fibonacci is used (instead of 1–10) is that **the gaps force honest conversation**. You can't say "6.5" — you have to commit to either 5 or 8, which surfaces disagreement between team members.

---

## Why "8" Fits Our 2FA User Story

Think of it this way: compared to a story like *"add a GET endpoint that returns a list of events"* (a 2 or 3), the 2FA story required:

- Designing a security flow the team hadn't done before
- Touching ~10 files across every layer (entity, DAO, service, controller, routes, tests)
- A non-trivial two-token strategy (temp tokens with scope enforcement)
- Discovering and fixing an unplanned bug during implementation
- Repairing existing tests that broke as a side effect

That's the kind of work where you can't just sit down and type — you have to **think** first, and things surprise you along the way. That's an 8.

---

## The Key Insight

Points only make sense **relative to each other within your team**. Once you've shipped a few stories and labelled them, you use those as anchors:

> *"Is this bigger or smaller than the 2FA story?"*

Over time your estimates get more consistent — that calibration is the whole point of Planning Poker.
