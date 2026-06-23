# Task Plan — Web Push notifications + daily 8AM check

## Goal
Let the user track manga, get a **phone push notification** when a tracked manga has a new
chapter, and **tap the notification to open that manga's page** — with **no native app**
(Web Push via an installable PWA). Keep the existing 30-min poll AND add a daily 08:00 check.

## Context — most of this already exists
- Add/track manga: `AddMangaFormComponent` + dashboard. ✅
- Read vs latest chapter + unread highlight/badge: `dashboard.component.html`. ✅
- New-chapter detection + notification trigger: `NotificationService` (email via `JavaMailSender`),
  called from `ScrapingJob`. ✅ (currently email→Mailhog dev SMTP only)
- Scheduling: `ScrapingJob` `@Scheduled(fixedDelayString = poll-interval-minutes*60000)` every 30 min. ✅
- Scraper for sakuramangas.org works (stealth Playwright + DOM scrape, PR #12).

## Scope
### In scope
1. **Web Push delivery** to the user's phone, payload includes the manga URL; tapping opens it.
2. **PWA setup** so the Angular site is installable + can show notifications when closed.
3. **Subscription management** — UI to enable notifications (browser permission → save push subscription).
4. **Daily 08:00 check** in addition to the existing 30-min poll.

### Out of scope
- Native iOS/Android app.
- Reading chapters in-app.
- Auth/multi-user (single-user app; subscriptions are global).

## Decisions (from user)
- Channel: **Web Push (PWA)**, no native app. (Fallback option documented: ntfy.sh.)
- Schedule: **keep 30-min poll AND add 08:00 daily cron.**
- Cron timezone: **`America/Sao_Paulo`**.
- Email: **drop it** — remove the Mailhog/email notification path; push is the only channel.
- Target device: **Android** (Chrome full web-push support). iOS is non-goal, so the
  add-to-home-screen/Safari-16.4 caveat can be ignored for now.

## Implementation plan

### Backend
1. **Migration** `Vn__create_push_subscription.sql`: table `push_subscription`
   (id UUID, endpoint TEXT UNIQUE, p256dh TEXT, auth TEXT, created_at TIMESTAMP).
   Remember: `ddl-auto=validate` — migration required, never edit old ones.
2. **Dependency**: `nl.martijndwars:web-push` (+ bouncycastle) in `build.gradle`.
3. **VAPID keys**: generate once; inject via env/config (`app.push.vapid.public/private/subject`).
   Never commit the private key.
4. `PushSubscription` entity + repository (jakarta.persistence, Lombok pattern as existing entities).
5. `PushSubscriptionController`: `GET /api/push/public-key`, `POST /api/push/subscribe`,
   `POST /api/push/unsubscribe`. Follow existing controller + `GlobalExceptionHandler` patterns.
6. `PushNotificationService`: send a Web Push to all subscriptions with payload
   `{ title, body, url }` where `url` = manga `sourceUrl`. Prune `410 Gone` subscriptions.
7. Wire into the existing new-chapter path: replace the email send in `NotificationService` with
   `PushNotificationService` (and **remove the email/Mailhog path** — spring-mail dep,
   `JavaMailSender` usage, mail config, Mailhog from docker-compose). Reuse the existing dedup
   (`NotificationLog`) so the user isn't notified twice for the same chapter.
8. **Daily cron**: add a second `@Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")`
   method alongside the existing 30-min `fixedDelay`. Both call the same `pollAllManga()`.

### Frontend (Angular)
9. Add `@angular/service-worker`; `ng add @angular/pwa` or manual `ngsw-config.json` + manifest
   (name, icons, `start_url`, display=standalone) so it's installable.
10. Custom service worker handler for `push` (show notification) and `notificationclick`
    (`clients.openWindow(payload.url)`).
11. `PushService` (Angular, `inject()` per ESLint rule): request permission, subscribe via
    `PushManager` using the VAPID public key from the API, POST subscription to backend.
12. Settings/dashboard toggle: "Enable phone notifications" → calls `PushService`.
13. Per-chapter "New" label: confirm current unread badge is enough or add explicit "New" text.

### Verify
- Backend: `./gradlew spotlessApply test jacocoTestReport` (mock the push sender in unit tests).
- Frontend: `npm run lint && npm test && npm run e2e`.
- Manual: install PWA on an Android phone, enable notifications, force a new-chapter scrape,
  confirm push arrives and tapping opens the manga. Document iOS (add-to-home-screen, 16.4+).
- `docker compose up` still works (service worker served over HTTPS — note: Web Push needs HTTPS
  in prod; localhost is exempt for dev).

## Risks / notes
- **HTTPS required** in production for service workers / Web Push (localhost exempt). Deploy implication.
- VAPID private key is a secret — env only, never committed.
- Single-user assumption: subscriptions are global, no per-user routing.
- Dropping email touches `NotificationService`, `build.gradle` (spring-boot-starter-mail),
  application.properties mail config, docker-compose (mailhog service), and any mail tests —
  remove cleanly, keep `NotificationLog` dedup.

## Recommendation
Implement in a **fresh Claude Code session** (this one is near its context limit). Start from this
file. Suggested order: backend subscription + send + cron first (unit-testable), then the Angular
PWA + subscribe UI, then manual phone verification.
