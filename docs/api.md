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
    "notificationsEnabled": true,
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

Updates the reading progress or notification preference for a manga. All fields are optional — only supplied fields are changed.

**Path parameter:** `id` — UUID of the manga.

**Request body**

```json
{
  "currentChapter": 1095,
  "notificationsEnabled": false
}
```

**Validation rules**

- `currentChapter` must be `>= 0` and `<= latestChapter`

**Response `200 OK`** — the updated manga object.

**Error responses**

| Status | Condition                                               |
|--------|---------------------------------------------------------|
| 400    | `currentChapter` is negative or exceeds `latestChapter` |
| 404    | Manga with the given `id` not found                     |

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

## Settings — `/api/settings`

### GET /api/settings

Returns the current application settings.

**Response `200 OK`**

```json
{
  "id": 1,
  "emailNotificationsEnabled": true,
  "notificationEmail": "user@localhost",
  "pollIntervalMinutes": 30
}
```

---

### PUT /api/settings

Replaces the application settings. All fields are optional — only supplied fields are updated.

**Request body**

```json
{
  "emailNotificationsEnabled": true,
  "notificationEmail": "you@example.com",
  "pollIntervalMinutes": 60
}
```

**Validation rules**

- `notificationEmail` must be a non-blank string
- `pollIntervalMinutes` must be a positive integer

**Response `200 OK`** — the updated settings object.

**Error responses**

| Status | Condition                   |
|--------|-----------------------------|
| 400    | Validation failure (see above) |

---

## Schemas

### Manga

| Field                 | Type      | Notes                                      |
|-----------------------|-----------|--------------------------------------------|
| `id`                  | UUID      | Assigned on creation                       |
| `title`               | string    | Scraped from source page                   |
| `sourceUrl`           | string    | Must be unique; determines which scraper is used |
| `currentChapter`      | integer   | Chapter the user has read up to; default 0 |
| `latestChapter`       | integer   | Latest chapter found by scraper            |
| `notificationsEnabled`| boolean   | Per-manga notification switch; default true |
| `lastCheckedAt`       | datetime  | Timestamp of last scrape attempt; nullable |
| `createdAt`           | datetime  | Set on creation                            |
| `updatedAt`           | datetime  | Updated on any change                      |

### AppSettings

| Field                      | Type    | Notes                              |
|----------------------------|---------|------------------------------------|
| `id`                       | integer | Always `1` (single-row table)      |
| `emailNotificationsEnabled`| boolean | Global email notifications switch  |
| `notificationEmail`        | string  | Address where notifications are sent |
| `pollIntervalMinutes`      | integer | How often the scraping job runs    |

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
