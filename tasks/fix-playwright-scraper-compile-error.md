# Fix: Playwright Scraper Compile Error

## Source
- **Plan:** `tasks/plan-cloudflare-bypass-scraper.md`
- **Supervision report:** Claude independent review (2026-03-07)

## Problem

`./gradlew test` fails with a compilation error:

```
SakuraMangasScraperTest.java:306: error: incompatible types: incompatible parameter types in lambda expression
      super("", ignored -> null, ignored -> null);
                ^
```

The `RecordingBrowserManager` test helper extends `PlaywrightBrowserManager` and calls the package-private constructor:

```java
PlaywrightBrowserManager(String, PlaywrightFactory, BrowserLauncher)
```

The two lambdas `ignored -> null` are ambiguous because Java cannot infer which functional interface each targets. Both `PlaywrightFactory` and `BrowserLauncher` accept one argument and return an object, so the compiler needs an explicit type hint.

## Fix

**File:** `backend/src/test/java/com/mangaTracker/backend/scraper/SakuraMangasScraperTest.java`
**Line:** 306

Change:

```java
super("", ignored -> null, ignored -> null);
```

To:

```java
super("", (PlaywrightFactory) ignored -> null, (BrowserLauncher) ignored -> null);
```

This requires importing (or using the already-visible package-private) `PlaywrightBrowserManager.PlaywrightFactory` and `PlaywrightBrowserManager.BrowserLauncher`. Since `RecordingBrowserManager` is in the same package, both interfaces are accessible without additional imports.

## Verification

```bash
cd backend
./gradlew spotlessApply --no-daemon
./gradlew test jacocoTestReport --no-daemon
```

Both must pass. No other files need changes.

## Constraints
- Do not modify any other file
- Do not change the `PlaywrightBrowserManager` constructor signatures or interface definitions
- The 14 original `SakuraMangasScraperTest` tests must still pass unchanged

## Implementer Review

**Verdict:** ready

**Findings:**
- No remaining code-level findings after the supervisor-requested fix.

**What I reviewed:**
- Confirmed the ambiguous lambda in `RecordingBrowserManager` was resolved by binding the first argument as a zero-arg `PlaywrightFactory` lambda and the second as an explicitly typed `BrowserLauncher` lambda.
- Confirmed the change stayed within the fix-doc scope and did not alter any production constructor signatures or interface definitions.
- Ran `git diff --check -- backend/src/test/java/com/mangaTracker/backend/scraper/SakuraMangasScraperTest.java` successfully.

**Verification status:**
- Code-level self-review complete.
- `./gradlew` verification remains blocked in this session because the environment still cannot download the Gradle wrapper/dependencies.

## Agreement

- **Implementer Review:** ready
- **Independent Review:** ready
- **Agreement:** agree-pass
