# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT-MAP.md`** at the repo root — it points at one `CONTEXT.md` per context. Read each one relevant to the topic.
- **`docs/agents/project-memory.md`** — cross-session product decisions and the current implementation direction.
- **`backend/CONTEXT.md`** — backend domain language.
- **`ios/CONTEXT.md`** — iOS domain language.
- **`docs/adr/README.md`** and ADR files in **`docs/adr/`** — system-wide architectural decisions.
- **`backend/docs/adr/README.md`** and **`ios/docs/adr/README.md`** — context-scoped decision indexes.

These files are part of the repo's baseline agent context. If one is missing, don't proceed silently: recreate the missing baseline file or ask before continuing if the content would require a real product or architecture decision.

## File structure

This repo is multi-context, with separate backend and iOS codebases:

```
/
├── CONTEXT-MAP.md
├── docs/adr/                          ← system-wide decisions
├── backend/
│   ├── CONTEXT.md
│   └── docs/adr/                      ← backend-specific decisions
└── ios/
    ├── CONTEXT.md
    └── docs/adr/                      ← iOS-specific decisions
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in the relevant `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal — either you're inventing language the project doesn't use (reconsider) or there's a real gap. Add a focused term to the relevant `CONTEXT.md` when the term is already clear from code or product docs; otherwise note it for `/grill-with-docs`.

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0007 (event-sourced orders) — but worth reopening because…_
