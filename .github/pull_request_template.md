## Problem and resulting behavior

<!-- What is wrong today, and what changes for the user once this merges. -->

## Validation

List the checks actually run and their results. State which checks you did not
run and why. The commands are in the verification section of `AGENTS.md`:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Risk and documentation

Explain relevant compatibility, permissions, data and documentation changes.
Follow `AGENTS.md` and `.github/engineering.md`.

## Checklist

- [ ] The change is focused and contains no unrelated edits.
- [ ] Examples, logs, and fixtures contain no Microsoft Graph tokens, account
      identifiers, file names, or photo content.
- [ ] Behavior changes are reflected in `README.md` and `README.zh-CN.md` in the
      same pull request.
- [ ] Changes to what is selected for deletion, what is skipped, or how a
      deletion is confirmed are stated above rather than implied.

## AI assistance

Which AI tool and model were used? Write `None` if no AI was used.

## What changed

Describe the change and its purpose.
