# Contributing

How work moves through this repository. The rules below are enforced by a
[GitHub ruleset](#what-is-enforced-on-main) on `main`, not by convention alone.

---

## One-time setup

After cloning, point Git at this repository's committed hooks:

```bash
git config core.hooksPath .githooks
```

Git normally looks for hooks in `.git/hooks`, which is never committed and so
can't be shared. Setting `core.hooksPath` tells Git to use the `.githooks`
directory instead, which *is* committed — so everyone gets the same checks.

This is per-clone and per-person; it is not something the repository can do for
you.

Then confirm you can run the tests:

```bash
./mvnw test
```

See [`docs/WORKFLOW.md`](./docs/WORKFLOW.md) for prerequisites and the full
development loop.

---

## Branch naming

```
<type>/<short-kebab-description>
```

*Kebab-case* means lowercase words joined by hyphens — `add-redirect-endpoint`,
not `addRedirectEndpoint` or `add_redirect_endpoint`.

| Type       | Use for                                        |
|------------|------------------------------------------------|
| `feat/`    | A new capability                               |
| `fix/`     | A bug fix                                      |
| `docs/`    | Documentation only                             |
| `test/`    | Tests only                                     |
| `refactor/`| Code change that doesn't alter behaviour       |
| `chore/`   | Tooling, config, housekeeping                  |

Examples:

```
feat/create-link-endpoint
fix/reject-oversized-url
docs/document-redirect-contract
chore/add-pr-template
```

This is a documented convention, not an enforced one. Keeping to it makes the
branch list readable at a glance; nothing breaks if you don't.

---

## The workflow

`main` is protected. All changes arrive through a pull request.

```bash
# 1. Start from an up-to-date main
git switch main
git pull

# 2. Branch
git switch -c feat/create-link-endpoint

# 3. Work, committing as you go
git add <files>
git commit -m "add the service method"

# 4. Push and open a pull request
git push -u origin feat/create-link-endpoint
```

Then open the PR on GitHub, fill in the template, and merge with **Squash and
merge**. The branch is deleted automatically once merged.

```bash
# 5. Return to main and pick up the merged change
git switch main
git pull
```

`-u` on the first push links your local branch to the remote one, so later
pushes are just `git push`.

---

## Pull request titles

**The PR title matters more than your commit messages.** Squash merging
collapses every commit on the branch into a single new commit on `main`, and
uses the PR title as its message. So `main`'s history is a list of PR titles.

Use [Conventional Commits](https://www.conventionalcommits.org) format:

```
<type>(<optional scope>): <description>
```

| Type       | Meaning                                   |
|------------|-------------------------------------------|
| `feat`     | A new capability                          |
| `fix`      | A bug fix                                 |
| `docs`     | Documentation only                        |
| `test`     | Tests only                                |
| `refactor` | Behaviour-preserving code change          |
| `chore`    | Tooling, config, housekeeping             |
| `build`    | Build system or dependencies              |
| `ci`       | CI configuration                          |
| `perf`     | Performance work                          |

Examples:

```
feat(links): add POST /api/v1/links
fix: reject aliases longer than 64 characters
docs: record the decision to skip local performance testing
```

Write the description in the imperative — "add the endpoint", not "added the
endpoint" — so it reads as an instruction the commit carries out.

### Commit messages on your branch

These get squashed away and never reach `main`, so they don't need the same
rigour. They remain visible inside the PR, so keep them clear enough to review
against. The same convention is encouraged, not required.

---

## What is enforced on `main`

A GitHub ruleset applies these to everyone, including the repository owner —
there is no bypass list.

| Rule                                  | Effect                                                        |
|---------------------------------------|---------------------------------------------------------------|
| Pull request required before merging  | No direct pushes to `main`                                    |
| Squash merge only                     | One commit on `main` per pull request                          |
| Linear history required               | No merge commits                                              |
| Signed commits required               | Every commit on `main` carries a verified signature           |
| Conversation resolution required      | Unresolved review comments block the merge                     |
| Force pushes blocked                  | Published history cannot be rewritten                          |
| Deletion restricted                   | `main` cannot be deleted                                       |

Required approvals are set to **0**. GitHub does not permit approving your own
pull request, so on a single-maintainer repository any higher value would block
every merge. The pull request mechanism is still mandatory. In a team setting
this would be at least 1.

Locally, a `pre-commit` hook refuses commits made directly on `main`. It is a
convenience that catches the "forgot to branch" mistake early — the ruleset is
the real control, since hooks can be skipped with `--no-verify` and only run on
your own machine.

---

## Before opening a pull request

- Tests pass: `./mvnw test`
- New behaviour has a test covering it
- Endpoints and schema match [`docs/DESIGN.md`](./docs/DESIGN.md)
- Any decision or trade-off is appended to [`docs/DECISIONS.md`](./docs/DECISIONS.md)
- [`docs/ROADMAP.md`](./docs/ROADMAP.md) reflects what is now done
