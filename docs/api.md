# API Reference

Base URL: `http://localhost:8080` (direct) or `http://localhost:4200` (via nginx proxy).

All request and response bodies use `Content-Type: application/json`.

---

## Manga — `/api/manga`

### GET /api/manga

Returns all tracked manga sorted by most recently updated first.

**Response `200 OK`**

```json
[
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "title": "One Piece",
    "sourceUrl": "https://sakuramangas.org/manga/one-piece/",
    "currentChapter": 1095,
    "latestChapter": 1110,
    "coverImageUrl": "https://sakuramangas.org/img/one-piece.jpg",
    "notificationsEnabled": true,
    "latestChapterAt": "2024-01-15T10:30:00",
    "lastCheckedAt": "2024-01-15T10:30:00",
    "createdAt": "2024-01-01T09:00:00",
    "updatedAt": "2024-01-15T10:30:00"
  }
]
```

---

### POST /api/manga

Adds a new manga by source URL. The backend scrapes the page to extract the title and latest chapter number.

**Request body**

```json
{ "sourceUrl": "https://sakuramangas.org/manga/one-piece/" }
```

**Response `201 Created`** — the created manga object (see schema above).

**Error responses**

| Status | Condition                                       |
|--------|-------------------------------------------------|
| 400    | URL is blank, malformed, or from an unsupported source |
| 409    | The URL is already tracked                      |
| 422    | The scraper could not extract title or chapter  |

---

### PATCH /api/manga/{id}

Updates the notification preference for a manga.

**Path parameter:** `id` — UUID of the manga.

**Request body**

```json
{ "notificationsEnabled": false }
```

**Response `200 OK`** — the updated manga object.

**Error responses**

| Status | Condition                           |
|--------|-------------------------------------|
| 404    | Manga with the given `id` not found |

---

### POST /api/manga/{id}/read

Marks the manga as fully read — sets `currentChapter` to its `latestChapter`. The push
notification's tap target uses this so opening a manga clears its unread state.

**Path parameter:** `id` — UUID of the manga.

**Response `200 OK`** — the updated manga object (now caught up).

**Error responses**

| Status | Condition                           |
|--------|-------------------------------------|
| 404    | Manga with the given `id` not found |

---

### POST /api/manga/{id}/unread

Marks the manga as unread — resets `currentChapter` below `latestChapter` so the card shows
the "New" badge again. Inverse of `/read`; used by the dashboard's read/unread toggle.

**Path parameter:** `id` — UUID of the manga.

**Response `200 OK`** — the updated manga object.

**Error responses**

| Status | Condition                           |
|--------|-------------------------------------|
| 404    | Manga with the given `id` not found |

---

### GET /api/manga/{id}/cover

Streams the manga's cover image bytes. Covers are stored inline as `data:` URLs (base64, fetched
past Cloudflare at scrape time), which exceed the ~4KB Web Push payload limit. This endpoint decodes
the stored `data:` URL and serves it with the correct `Content-Type`, giving push notifications a
fetchable URL for the icon and large image.

**Path parameter:** `id` — UUID of the manga.

**Response `200 OK`** — raw image bytes; `Content-Type` matches the stored media type (e.g. `image/jpeg`).

**Error responses**

| Status | Condition                                              |
|--------|--------------------------------------------------------|
| 404    | Manga not found, or it has no decodable `data:` cover  |

---

### POST /api/manga/{id}/test-push

Sends a test Web Push notification for this manga to all subscribed browsers, so you can
verify push works on a device. The payload uses the manga's latest chapter, title, and cover.

**Path parameter:** `id` — UUID of the manga.

**Response `200 OK`** — empty body.

**Error responses**

| Status | Condition                           |
|--------|-------------------------------------|
| 404    | Manga with the given `id` not found |

---

### DELETE /api/manga/{id}

Removes a manga from the reading list. Associated notification log entries are deleted automatically (cascaded).

**Path parameter:** `id` — UUID of the manga.

**Response `204 No Content`**

**Error responses**

| Status | Condition                           |
|--------|-------------------------------------|
| 404    | Manga with the given `id` not found |

---

## Push notifications — `/api/push`

New chapters are delivered as Web Push notifications to subscribed browsers (installable PWA).
There is no email channel and no configurable poll interval — the scraper runs once a day at
08:00 (`America/Sao_Paulo`).

### GET /api/push/public-key

Returns the VAPID public key the browser needs to create a push subscription.

**Response `200 OK`**

```json
{ "publicKey": "BBV4dRDQ8s3tlGeJ..." }
```

---

### POST /api/push/subscribe

Registers a browser push subscription (idempotent by `endpoint`). Body matches the browser's
`PushSubscription.toJSON()` shape.

**Request body**

```json
{
  "endpoint": "https://fcm.googleapis.com/fcm/send/abc123",
  "keys": { "p256dh": "BN...", "auth": "k9..." }
}
```

**Response `201 Created`**

---

### POST /api/push/unsubscribe

Removes a subscription by endpoint.

**Request body**

```json
{ "endpoint": "https://fcm.googleapis.com/fcm/send/abc123" }
```

**Response `204 No Content`**

---

## Schemas

### Manga

| Field                 | Type      | Notes                                      |
|-----------------------|-----------|--------------------------------------------|
| `id`                  | UUID      | Assigned on creation                       |
| `title`               | string    | Scraped from source page                   |
| `sourceUrl`           | string    | Must be unique; determines which scraper is used |
| `currentChapter`      | integer   | Chapter read up to; 0 until marked read, set to `latestChapter` by `POST /read` |
| `latestChapter`       | integer   | Latest chapter found by scraper            |
| `coverImageUrl`       | string    | Cover scraped from the page's `og:image`; nullable |
| `notificationsEnabled`| boolean   | Per-manga notification switch; default true |
| `lastCheckedAt`       | datetime  | Timestamp of last scrape attempt; nullable |
| `createdAt`           | datetime  | Set on creation                            |
| `updatedAt`           | datetime  | Updated on any change                      |

### PushSubscription

| Field        | Type   | Notes                                           |
|--------------|--------|-------------------------------------------------|
| `id`         | UUID   | Assigned on creation                            |
| `endpoint`   | string | Push service endpoint; unique                   |
| `p256dh`     | string | Subscription public key (browser-provided)      |
| `auth`       | string | Subscription auth secret (browser-provided)     |
| `createdAt`  | datetime | Set on creation                               |

---

## Error Format

All error responses use a consistent body:

```json
{ "error": "Human-readable error message" }
```

| HTTP Status | Meaning                              |
|-------------|--------------------------------------|
| 400         | Bad request / validation failure     |
| 404         | Resource not found                   |
| 409         | Conflict (e.g. duplicate URL)        |
| 422         | Unprocessable entity (scraping error)|
