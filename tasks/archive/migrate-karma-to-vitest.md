# Migrate Karma/Jasmine to Vitest

## Context

The Angular 18 frontend uses Karma + Jasmine for unit tests. There is currently **1 spec file** (`src/app/app.component.spec.ts`) using `TestBed`. We are migrating to Vitest for faster tests, modern DX, and simpler config.

### Decisions made

- **Explicit Vitest imports** (`import { describe, it, expect } from 'vitest'`) — no globals
- **Drop TestBed** — use `@testing-library/angular` for component tests instead
- **Use `@analogjs/vitest-plugin`** for Angular template compilation support
- **Coverage via v8 provider** with `text` + `lcov` reporters (CI/SonarQube compatible)

---

## Phase 1: Remove Karma + Jasmine (with Phase 2 & 3 in one commit)

### 1a. Uninstall packages

```bash
cd frontend
npm uninstall karma karma-chrome-launcher karma-coverage karma-jasmine karma-jasmine-html-reporter jasmine-core @types/jasmine
```

### 1b. Install Vitest stack

```bash
npm install -D vitest @analogjs/vitest-plugin @testing-library/angular jsdom @vitest/coverage-v8
```

### 1c. Create `frontend/vitest.config.ts`

```ts
/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vitest-plugin';

export default defineConfig({
  plugins: [angular()],
  test: {
    globals: false,
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
    setupFiles: [],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      reportsDirectory: 'coverage',
    },
  },
});
```

### 1d. Update `frontend/tsconfig.spec.json`

Change:
```json
"types": ["jasmine"]
```
To:
```json
"types": ["vitest/globals"]
```

### 1e. Update `frontend/angular.json`

Remove the entire `"test"` architect target (lines 94-117). Tests now run via `vitest` directly, not `ng test`.

### 1f. Update `frontend/package.json` scripts

Replace:
```json
"test": "ng test"
```
With:
```json
"test": "vitest run",
"test:watch": "vitest",
"test:coverage": "vitest run --coverage"
```

---

## Phase 2: Rewrite spec file (second commit)

### Rewrite `src/app/app.component.spec.ts`

From (Karma/Jasmine + TestBed):
```ts
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});
```

To (Vitest + @testing-library/angular):
```ts
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/angular';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  it('should create the app', async () => {
    const { fixture } = await render(AppComponent, {
      providers: [provideHttpClient(), provideRouter([])],
    });
    expect(fixture.componentInstance).toBeTruthy();
  });
});
```

---

## Phase 3: CI + Cleanup (third commit)

### 3a. Update GitHub Actions workflows

Find any workflow that runs `ng test` or `npm test` and replace with:
```yaml
- run: npm run test:coverage
  working-directory: frontend
```

### 3b. Update `CLAUDE.md` quality gate commands

Replace:
```
cd frontend && ng test
```
With:
```
cd frontend && npm test
```

### 3c. Verify

```bash
cd frontend
npm test                # should pass
npm run test:coverage   # should generate coverage/lcov.info
```

---

## Verification

**Success commands:**
```bash
cd frontend
npm test                # unit tests pass
npm run test:coverage   # coverage report generates
npm run lint            # no lint errors
```

**Quality gates:** frontend (CLAUDE.md commands)

**Max fix attempts:** 5 (dependency/config task — version mismatches are likely)

**Watch targets:**
- `frontend/package.json` — all `@analogjs/*`, `vitest`, `@vitest/*` versions must be mutually compatible
- `frontend/vitest.config.*` — must reference working setup file, `globals: false`
- `frontend/angular.json` — karma test target must be removed entirely
- `frontend/src/app/app.component.spec.ts` — must use explicit vitest imports, no TestBed

**Validation checklist:**
- [ ] `npm test` passes with no Karma/Jasmine references
- [ ] `npm run test:coverage` generates `coverage/` directory with lcov output
- [ ] CI workflow runs the new test command successfully
- [ ] No Karma/Jasmine packages remain in `package.json`
- [ ] `angular.json` has no `karma` builder reference
- [ ] `CLAUDE.md` quality gate commands are updated
