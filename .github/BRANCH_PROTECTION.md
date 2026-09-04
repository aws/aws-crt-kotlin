# Branch protection configuration

This repo runs CI from two origins, and branch protection must cover both with a
single required status context.

## The one required status context

Configure exactly **one** required status check on the protected branch:

- `required`

Both the privileged fork-PR entrypoint (`external-pr-ci.yml`) and the same-repo
aggregate (`ci-required.yml`) publish a job named `required`, so a single
branch-protection rule covers both origins.

## Why only `required`

Fork PRs run through the privileged entrypoint (`external-pr-ci.yml`), which
calls the reusable workflows (`ci.yaml`, `changelog-verification.yml`,
`kat-transform.yml`) as `workflow_call` jobs. Because they run as *called*
workflows, every job context is **prefixed by the caller job name**. Fork-PR
contexts therefore look like:

- `ci / jvm (17)`  *(jvm/macos/windows run only on the unprivileged path — see below)*
- `ci / linux`
- `ci / linux-native (x64, al2)`
- `ci / linux-native (x64, al2023)`
- `ci / linux-native (x64, ubuntu-22.04)`
- `ci / linux-native (arm64, al2)`
- `ci / linux-native (arm64, al2023)`
- `ci / linux-native (arm64, ubuntu-22.04)`
- `ci / required` *(ci.yaml has no internal aggregate; the entrypoint's own `required` job is what gates)*
- `changelog / changelog-verification`
- `kat-transform / verify-transform`
- `required`  *(the entrypoint aggregate — the one to require)*

Same-repo PRs run the workflows directly via `pull_request`, producing
**unprefixed** contexts:

- `jvm (8)`, `jvm (11)`, `jvm (17)`, `jvm (21)`
- `macos (...)`
- `linux`
- `linux-native (x64, al2)` … etc.
- `windows`
- `changelog-verification`
- `verify-transform`
- `required`  *(the same-repo aggregate from `ci-required.yml` — same name)*

Notes on job behavior:

- `jvm`, `macos`, and `windows` in `ci.yaml` carry
  `if: ${{ github.event_name != 'workflow_call' }}`, so on the privileged
  fork path they are **skipped** — they run only on the unprivileged same-repo
  path (fork code needs no credentials for these, and they already pass on
  forks). Their fork-PR contexts above are listed for completeness but are
  skipped under `workflow_call`.
- `linux` is the only credentialed job (`CI_AWS_ROLE_ARN` for the Docker image
  build) and is **ungated**, so it runs on both paths.
- `linux-native` **depends on `linux`** (`needs: linux`) and consumes its
  `native-test-binaries` artifact. It has no checkout and no event guard, so it
  runs whenever `linux` runs — including the privileged fork path.

## Caveat

Do **not** mark any prefixed (`ci / …`, `changelog / …`, `kat-transform / …`)
or unprefixed individual context as "required". Each of those can be produced by
only **one** origin — requiring one would block PRs from the other origin
forever (the context would never appear). Only the single, same-named `required`
aggregate is produced by both origins, so it is the only safe required context.

## Limitation of `ci-required.yml`

The same-repo aggregate (`ci-required.yml`) only *produces the `required`
context name* on the same-repo path — it does **not** itself gate or depend on
the individual same-repo checks. Those same-repo checks (`jvm`, `macos`,
`linux`, `linux-native`, `windows`, `changelog-verification`,
`verify-transform`) remain independently required by virtue of running on the
same-repo `pull_request` path; if you want them enforced, they must be trusted
via normal same-repo CI, not through this aggregate. The aggregate exists solely
so that a single branch-protection rule named `required` is satisfiable from
both origins.
