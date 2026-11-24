## What and why

<!-- What does this change do and why is it needed? Link issues: Closes #123 -->

## How

<!-- Notable implementation details, trade-offs, screenshots/recordings for UI changes. -->

## Checklist

- [ ] Branch targets `develop` (or `master` for release/hotfix branches)
- [ ] Commits follow Conventional Commits
- [ ] Tests added/updated (bug fixes include a failing test first)
- [ ] `./gradlew spotlessCheck detekt testDebugUnitTest test assembleDebug` passes locally (in `mobile/`)
- [ ] UI strings added in English **and** Spanish; animations respect *reduce motion*
- [ ] No secrets, tokens or personal data in code, logs or fixtures
- [ ] New dependencies justified (purpose, GPL-3.0-compatible licence)
- [ ] Docs / ADRs updated if architecture or behaviour changed
