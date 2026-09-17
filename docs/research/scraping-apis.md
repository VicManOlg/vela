# Metadata / artwork scraping APIs — research for provider adapters

Date: 2026-09-17. Scope: an open-source Android (Kotlin) emulation frontend that needs game metadata and artwork behind a common `MetadataProvider` interface. This document is research only; no code.

Confidence markers used throughout:

- **[verified]** — checked live during this research (HTTP probe, downloaded file, or read from the provider's own spec/source).
- **[source]** — taken from an official doc page or a well-maintained open-source implementation (linked in "Sources").
- **[uncertain]** — from forum posts, search snippets, or memory; verify before relying on it.

---

## 0. One-page comparison

| Provider | Auth needed by app | Auth needed by user | Match method | Media | Offline-capable | Cost / limits | License / ToS gotchas |
|---|---|---|---|---|---|---|---|
| ScreenScraper.fr | Dev credentials (`devid`, `devpassword`, `softname`) — granted per application | Optional but effectively required (`ssid`, `sspassword`) | CRC32/MD5/SHA1 of ROM, or filename+system (arcade), or text search | Richest: box 2D/3D, wheel/logo, screenshots, title, fanart, video, mix images, marquee, manuals, bezels | No | Free; threads/day quota scale with user level; 1 thread for anonymous/new accounts | CC BY-NC-SA 4.0 data; API only for fully free applications |
| IGDB (Twitch) | Client ID + Client Secret (Twitch app) | No | Name (+ platform id) via `search`/`where` | Cover, screenshots, artworks, YouTube video ids, logos | No | Free (non-commercial and commercial); 4 req/s, 8 concurrent | Attribution requested; secret must not ship in client; official advice is a backend proxy |
| SteamGridDB | No | Yes (user API key from profile) | Name via `/search/autocomplete`, or Steam/GOG/EGS ids | Grids (covers), heroes (banners), logos, icons | No | Free; page size max 50; rate limit not documented | Terms not published in spec; community art |
| TheGamesDB | Public or private API key (requested on forum) | No | Name (+ platform filter), game id, also serial (`ByGameUniqueID`) and hash (`ByGameHash`) | Boxart front/back, fanart, banner, screenshot, clearlogo, titlescreen, video | No | Monthly allowance per key (public key ~1000/month/IP [uncertain]) | GPL-3.0 server code; data terms unspecified |
| libretro-thumbnails | No | No | Exact No-Intro/Redump name (after character sanitising) | Boxart, snap, title, (logos for some systems) | Yes (git repos / GitHub zip archives) | Free, static files | Community images, no explicit license in README [uncertain] |
| libretro-database (.dat/.rdb) | No | No | CRC32 (cartridge) / serial (disc) → canonical name + metadata | None (metadata only) | Yes | Free | CC-BY-SA-4.0 [source] |
| OpenVGDB | No | No | CRC32/MD5/SHA1/serial → title + cover URL | Cover URLs (hotlinks to GameFAQs etc.) | Yes (SQLite, 42 MB) | Free; last release 2021 | Cover URLs point at third-party hosts |
| Hasheous | No for lookups; client key for IGDB proxy | No | Hash (CRC/MD5/SHA1/SHA256) → IGDB/TheGamesDB/RA ids | Proxied IGDB images | No | Free, rate-limited by profile | Terms = GitHub repo |
| GameTDB | No | No | Disc/title id (serial) | Wii/GC/WiiU/DS/3DS/Switch/PS3 covers by id | Partially (TDB xml + cover packs) | Free | "Do not use without permission ... on a website"; homebrew-friendly |
| LaunchBox GamesDB | No | No | Name (offline XML) | Box, fanart, clear logo, screenshots (image URLs) | Yes (Metadata.zip, 107 MB) | Free download | No API, no published license for third parties |
| MobyGames | Paid key | — | Name | Covers, screenshots | No | Paid since Sep 2024 (Hobbyist ~$9.99/mo, 0.2 req/s) | Non-commercial tier only |
| RAWG | API key | — | Name | Screenshots, background image | No | 20k req/month free personal; link back required | Commercial contact for large projects |

---

## 1. ScreenScraper.fr — API v2

Official API page: <https://www.screenscraper.fr/webapi2.php> (French; the per-function pages require a dev login). Base URLs used in the wild: `https://api.screenscraper.fr/api2/` (ES-DE, Batocera ES, RomM) and `https://www.screenscraper.fr/api2/` (Skyscraper) **[source]**.

### 1.1 Authentication model

Every call carries two credential pairs:

| Param | Who it identifies | Required | Notes |
|---|---|---|---|
| `devid` | The *application* (developer account) | Yes | Issued per software after presenting the project on the ScreenScraper forum ("WebAPI" section, `forumsujets.php?frub=12`) **[source]** |
| `devpassword` | The application | Yes | Same |
| `softname` | Application name + version, e.g. `ES-DE 3.1.0 Android` | Yes | Used for statistics and for blacklisting (HTTP 426). Spaces in `softname` end up in the media URLs returned, so URL-encode them (ES-DE/Batocera comments) **[source]** |
| `ssid` | The *end user's* ScreenScraper account | Optional | Without it you are an anonymous/"guest" user (256 guest thread slots globally, often closed) |
| `sspassword` | The end user | Optional | Sent in the query string (plain, over HTTPS) |

Why a dev account is required: ScreenScraper meters and blacklists per application, and states that "L'API ScreenScraper ne peut être intégré que dans les applications entièrement gratuites et distribuées, ou, dans le cas contraire, avec l'autorisation préalable" (only fully free distributed applications may integrate the API, otherwise prior authorisation is needed) **[source: webapi2.php]**. Developers must "présenter votre logiciel" in the forum's development section to get credentials. ScreenScraper staff on a 2025 GitHub thread: "It's up to the [app] developer to integrate these IDs into their app — not up to users to request a key every time" **[source: JellyEmu issue #219]**.

How open-source frontends ship the dev credentials (all of these are recoverable by anyone reading the source; obfuscation is a courtesy to the ScreenScraper rule that credentials should not be posted in the clear):

| Project | Handling | Reference |
|---|---|---|
| ES-DE | Constants `API_DEV_U`, `API_DEV_P`, `API_DEV_KEY` are `std::string` byte arrays in `ScreenScraper.h`, decoded at runtime with `Utils::String::scramble(value, key)` (XOR-style). `softname = "ES-DE " + version + " " + platform`. | es-app/src/scrapers/ScreenScraper.h/.cpp **[verified]** |
| Skyscraper (Gemba fork / muldjord) | `devid=muldjord` in plaintext; `devpassword` via `StrTools::unMagic("204;198;236;...")` (custom byte obfuscation) in `src/screenscraper.cpp`. | **[verified]** |
| Batocera EmulationStation | Credentials are a compile-time define `SCREENSCRAPER_DEV_LOGIN` passed by the build system; the whole scraper is compiled out with `#if defined(SCREENSCRAPER_DEV_LOGIN)`. Credentials are *not* in the public repo. | es-app/src/scrapers/ScreenScraper.h **[verified]** |
| RomM (self-hosted server) | Env vars `SCREENSCRAPER_DEV_ID` / `SCREENSCRAPER_DEV_PASSWORD` (plus `SCREENSCRAPER_USER`/`_PASSWORD`) — each server operator supplies their own dev credentials. | backend/config/__init__.py **[verified]** |
| Skraper | Closed source; official ScreenScraper partner (linked from screenscraper.fr header). Credentials embedded, not disclosed. | **[source]** |
| Pegasus Frontend | Has no ScreenScraper client of its own; it consumes Skraper/Skyscraper output (`[game dir]/skraper`, `media/`). | pegasus-frontend.org docs **[source]** |
| RetroPie | Ships Skyscraper; users only enter their own `ssid:sspassword` in `config.ini` (`userCreds`). | RetroPie-Setup skyscraper.sh **[source]** |

Practical conclusion for us: request our own `devid` on the forum (free app requirement satisfied), embed it obfuscated in the APK (same approach as ES-DE; treat it as "recoverable"), never reuse another project's credentials (they can blacklist by `softname`/`devid`), and make the user's `ssid`/`sspassword` a first-class settings entry because anonymous access is frequently closed (HTTP 401 "API fermé pour les non membres") and limited to 1 thread.

### 1.2 Endpoint catalogue

All endpoints are `GET` with query parameters; `output=json|xml|ini` selects the format (`xml` is the default) **[source: webapi2.php]**.

| Endpoint | Purpose | Key params (besides credentials) |
|---|---|---|
| `ssinfraInfos.php` | Server load / infrastructure status | — |
| `ssuserInfos.php` | User level, thread allowance, quotas; **does not consume the daily quota** (RomM comment) | `ssid`, `sspassword` |
| `userlevelsListe.php` | List of user levels | — |
| `systemesListe.php` | All systems with ids, names, extensions, company, and system media | — |
| `mediasJeuListe.php` / `mediasSystemeListe.php` | Enumerates game/system media types | — |
| `regionsListe.php`, `languesListe.php`, `genresListe.php`, `classificationsListe.php`, `nbJoueursListe.php`, `supportTypesListe.php`, `romTypesListe.php`, `infosJeuListe.php`, `infosRomListe.php`, `famillesListe.php` | Reference lists | — |
| `jeuInfos.php` | **Main lookup**: identify one game from ROM hash/filename/size and return full metadata + media URLs | `crc`, `md5`, `sha1`, `systemeid`, `romtype`, `romnom`, `romtaille`, `serialnum`, `gameid` |
| `jeuRecherche.php` | Text search (max 30 results) | `recherche`, `systemeid` |
| `mediaJeu.php` | Download one game media with server-side resize/format | `systemeid`, `jeuid`, `media`, `mediaformat`, `maxwidth`, `maxheight`, `outputformat`, `crc`/`md5`/`sha1` |
| `mediaSysteme.php`, `mediaGroup.php`, `mediaCompagnie.php` | System / genre / company media | similar |
| `mediaVideoJeu.php` | Game video download | `systemeid`, `jeuid`, `media=video|video-normalized` |
| `botNote.php`, `botProposition.php` | Submit ratings / proposals (not needed) | — |

### 1.3 `jeuInfos.php` — parameters and identification rules

Example (from ES-DE/Skyscraper URL construction, credentials elided) **[source]**:

```
https://api.screenscraper.fr/api2/jeuInfos.php?devid=XXX&devpassword=YYY&softname=MyApp%201.0
  &output=json&ssid=USER&sspassword=PASS
  &crc=B19ED489&md5=CDD3C8C37322978CA8669B34BC89C804&sha1=...
  &systemeid=4&romtype=rom&romnom=Super%20Mario%20World%20(USA).sfc&romtaille=524288
```

| Param | Required | Meaning / rules |
|---|---|---|
| `crc` / `md5` / `sha1` | At least one identifier | Hex hash of the ROM file. Skyscraper sends all three uppercase; Batocera uppercases MD5/CRC. |
| `romnom` (+ `systemeid`) | Alternative identifier | File name including extension, percent-encoded. Filename + system is a valid identifier on its own (this is how arcade/MAME sets are matched — the DB knows MAME short names). |
| `romtaille` | Recommended | File size in bytes. For zips the server expects the size of the file you hashed (Skyscraper sends `info.size()` of the archive when it hashed the archive). Documentation says it is needed "for zip archives" **[source: webapi2 summary]**. |
| `systemeid` | Recommended | Restricts the search; ScreenScraper returns 400 for malformed `romnom` without it in some cases. |
| `romtype` | Optional | `rom`, `iso`, `dossier` (folder) **[source]**. Batocera always sends `romtype=rom`. |
| `serialnum` | Optional | Disc serial (e.g. `SLUS-00594`) **[uncertain: present in the official param list, not seen used by open-source scrapers]** |
| `gameid` | Optional | Direct lookup by ScreenScraper game id (useful for re-scrape / user pick). |

Rules observed in implementations:

- **Hash first, name second.** ES-DE sends MD5 only (plus `romtaille`), Skyscraper sends CRC+MD5+SHA1+`romnom`+`romtaille`, Batocera sends MD5 (+CRC if known). Any one hash that ScreenScraper knows yields an exact match; ScreenScraper also learns associations ("romasso") from users, so hashes of *zipped* files often match popular sets even though the canonical DB hash is that of the uncompressed ROM **[uncertain]**.
- **Size limits before hashing.** ES-DE: skip hashing if file > `ScraperSearchFileHashMaxSize` (default **384 MiB**) **[verified]**. Batocera: compute MD5 only if size ≤ **128 MB**, optionally extracting the inner file from archives (`shouldExtractHashesFromArchives`) **[verified]**. Skyscraper: with `unpack` on, extracts zip/7z < ~78 MB (`81920000` bytes) and hashes the inner file; otherwise hashes the archive itself **[verified]**.
- **Arcade / MAME**: match by `romnom` (the `.zip` short name) + `systemeid=75` and do **not** run the wide text search (ES-DE `isArcadeSystem -> singleSearch`). Skyscraper additionally prefers the `flyer` media type as "cover" for arcade platforms **[verified]**.
- **Text search quirks** (ES-DE comments): `jeuRecherche` errors on search strings shorter than 4 characters, strips the word "the" and trailing `+`; ES-DE falls back to `jeuInfos` with `romnom` for such names **[verified]**.
- ScreenScraper strips region tags itself when matching on `romnom`; still send the exact filename.

### 1.4 Response structure (`output=json`)

Top level: `{"header": {...}, "response": {...}}` **[source: screech Go types, RomM handler]**.

- `header`: `APIversion`, `dateTime`, `commandRequested`, `success`, `error`.
- `response.serveurs`: per-server `cpu_usage`, `request_rate`, `response_time`.
- `response.ssuser` (only when `ssid` is valid; also returned by `ssuserInfos.php`): `id`, `niveau` (level), `contribution`, `uploadsysteme`, `uploadinfos`, `romasso`, `uploadmedia`, **`maxthreads`**, **`maxdownloadspeed`** (KB/s), **`requeststoday`**, **`requestskotoday`**, **`maxrequestsperday`**, **`maxrequestskoperday`**, **`maxrequestspermin`**, `visites`, `datedernierevisite`, `favregion` **[source]**. ES-DE checks `niveau` to detect wrong username/password.
- `response.jeu`:
  - `id`, `romid`, `notgame` (true for BIOS/non-game entries; also names like `ZZZ(NOTGAME)` — filter these) **[source: ES-DE, Skyscraper]**
  - `noms[]`: `{region, text}` — title per region
  - `cloneof`, `systeme {id, text}`, `editeur {id, text}`, `developpeur {id, text}`, `joueurs {text}`, `note {text}` (rating /20), `topstaff`, `rotation`
  - `synopsis[]`: `{langue, text}` — description per language
  - `classifications[]`: `{type (ESRB/PEGI/CERO...), text}`
  - `dates[]`: `{region, text}` — `YYYY-MM-DD` or `YYYY`
  - `genres[]`: `{id, nomcourt, principale, parentid, noms[{langue, text}]}`; `familles[]` similar; `modes[]` similar
  - `medias[]`: `{type, parent ("jeu" for game media), url, region, crc, md5, sha1, size, format (png/jpg/mp4/pdf), posx, posy, posw, posh, id, subparent}`
  - `roms[]` / `rom`: `{id, romsize, romfilename, romnumsupport, romtotalsupport, romcloneof, romcrc, rommd5, romsha1, beta, demo, proto, trad, hack, unl, alt, best, netplay, romregions, romlangues}`

Media `url` values already include the dev/user credentials as query params — strip them before logging or persisting (RomM `strip_sensitive_query_params`) **[source]**.

### 1.5 Media types

Type names as they appear in `medias[].type` (compiled from ES-DE, Batocera, Skyscraper, RomM and community lists) **[source]**:

| Type | What it is | Typical use |
|---|---|---|
| `box-2D` | Front box art (2D scan) | Cover |
| `box-2D-back`, `box-2D-side` | Back / spine | Back cover |
| `box-3D` | Rendered 3D box | 3D cover |
| `box-texture` | Flat texture used to build 3D boxes | — |
| `support-2D`, `support-texture` | Cartridge / disc image ("physical media") | Cart/disc |
| `wheel`, `wheel-hd`, `wheel-carbon`, `wheel-steel` | Game logo (transparent PNG; "wheel" in HyperSpin terms) | Logo / marquee |
| `screenmarquee`, `screenmarqueesmall` | Arcade-style wide marquee | Marquee |
| `marquee` | Arcade cabinet marquee (arcade systems) | Marquee |
| `flyer` | Arcade flyer | Cover for arcade |
| `sstitle` | Title screen | Title screenshot |
| `ss` | In-game screenshot | Screenshot |
| `fanart` | Wide background artwork | Background |
| `mixrbv1`, `mixrbv2` | "Mix" composites (Recalbox-style: screenshot + wheel + box) | Single tile |
| `video`, `video-normalized` | Gameplay video; `-normalized` is re-encoded, smaller (Skyscraper default `videoPreferNormalized=true`) | Video |
| `manuel` | PDF manual | Manual |
| `bezel-16-9`, `bezel-4-3` | Bezel overlays (Batocera) | Bezels |
| `maps`, `steamgrid` | Maps; Steam-style grid | — |

Fallback chains used by Batocera (good defaults): `wheel -> wheel-hd -> wheel-steel -> wheel-carbon -> screenmarqueesmall -> screenmarquee`; `box-2D -> box-3D`; `mixrbv1 -> mixrbv2` **[verified]**.

`mediaJeu.php` can resize/convert server-side (`maxwidth`, `maxheight`, `mediaformat`) and supports cache validation: pass `crc`/`md5`/`sha1` of the file you already have and the server answers `CRCOK`/`MD5OK`/`SHA1OK` instead of bytes; `NOMEDIA` when the media does not exist **[source: webapi2 summary]**. Batocera builds video URLs directly as `https://www.screenscraper.fr/medias/{systemeid}/{gameid}/{media}.mp4` **[source]**. Downloads are throttled to `maxdownloadspeed` per user (free accounts about 128 kbit/s per the quoted forum figures) **[uncertain]**.

### 1.6 Region and language handling

- Region codes (lower-case) seen in `noms[].region`, `dates[].region`, `medias[].region`: `wor` (world), `us`, `eu`, `jp`, `ss` (ScreenScraper's own — used for homebrew/OpenBOR/PICO-8), `cus` (custom), `uk`, `fr`, `de`, `es` (`sp` in Skyscraper's list), `it`, `nl`, `pt`, `br`, `au`, `nz`, `ca`, `kr`, `cn`, `tw`, `asi`, `ame`, `ru`, `pl`, `se`, `no`, `dk`, `gr`, `fi` **[source]**.
- Language codes in `synopsis[].langue`, `genres[].noms[].langue`: `en`, `fr`, `de`, `es`, `it`, `pt`, ... **[source]**.
- Fallback orders used by implementations:
  - ES-DE: names/dates/media `{userRegion, "wor", "us", "ss", "eu", "jp"}` (+ `"cus"` and "first region in result" if `ScraperRegionFallback`); text `{userLanguage, "en", "wor"}`; default region `eu`, language `en` **[verified]**.
  - Batocera: `{language, region, "wor", "us", "eu", "jp", "ss", "cus", ""}` **[verified]**.
  - RomM: region tags parsed from the filename first, then `["us","wor","ss","eu","jp","cus"]` **[verified]**.
  - Skyscraper default `regionPrios`: `eu, us, ss, uk, wor, jp, au, ame, de, cus, cn, kr, asi, br, sp, fr, gr, it, no, dk, nz, nl, pl, ru, se, tw, ca`; `langPrios`: `en, de, fr, es` **[source]**.
- "ScreenScraper will return results according to your currently set region, and not the game's region" (RetroHub docs) — i.e. the server returns *all* regions and the client picks; nothing is filtered server-side.

### 1.7 Quotas, threads, error codes

| Level | Threads (`maxthreads`) | Daily requests | Notes |
|---|---|---|---|
| Anonymous (no `ssid`) | 1 (global pool of 256 guest threads; closed when CPU > 60 %) | ~10 000 [uncertain] | HTTP 401 when the API is closed to non-members |
| New free account | 1 at ~128 kbit/s download | 20 000 | **[source: RomM issue #3978]** |
| Contributors | up to 8 (grows with contributions) at ~40 Mbit/s | up to 100 000 | |
| One-off €10 donation (Tipeee/Patreon) | +5 threads for life | | |

- Per-minute allowance: the FAQ formula is `threads × 50`, but RomM's maintainers confirmed with ScreenScraper that the real figure is whatever `maxrequestspermin` reports (currently about `1024 × (threads + 1)`) — always pace against the returned value **[source, uncertain formula]**.
- Unknown-ROM ("KO") requests have a separate, roughly 10× smaller daily quota (`maxrequestskoperday`); each miss counts against it, so avoid re-querying hashes you already know are unknown **[source]**.
- Quotas reset at midnight CET (RomM) **[source]**.
- Skyscraper deliberately spaces requests 1.2 s apart per thread ("as requested by the good folks at ScreenScraper. Don't change!") **[verified]**.

HTTP status codes **[source: webapi2.php + RomM mapping]**:

| Code | Meaning | Client action |
|---|---|---|
| 400 | Missing/invalid required field or malformed `romnom` | Fix request |
| 401 | Bad user credentials **or** API closed to non-members / inactive members because server saturated (CPU > 60 %) | Retry later; suggest login |
| 403 | Developer authentication failed (`devid`/`devpassword`) | Disable provider, report bug |
| 404 | Game/ROM not found (also body starting with `Erreur : Rom/Iso/Dossier non trouvée`) | Fall back to name search |
| 423 | API in critical state / offline | Back off |
| 426 | This `softname`/version is blacklisted or too old | Force app update |
| 429 | Too many requests: thread or per-minute limit hit (transient) | Wait, reduce concurrency |
| 430 | Daily scrape quota exhausted | Stop until midnight CET |
| 431 | Too many unrecognised ROM submissions today (KO quota) | Stop hash lookups for unknown files today |

### 1.8 System ids for our platforms

Cross-checked between ES-DE `screenscraper_platformid_map`, Skyscraper `platforms_idmap.csv` and RomM `ss_handler.py` (all agree) **[verified]**:

| Platform | `systemeid` | Platform | `systemeid` |
|---|---|---|---|
| NES / Famicom | 3 | PlayStation | 57 |
| SNES / Super Famicom | 4 | PlayStation 2 | 58 |
| Nintendo 64 | 14 | PSP | 61 |
| GameCube | 13 | PS Vita | 62 |
| Wii | 16 | Dreamcast | 23 |
| Wii U | 18 | Arcade (MAME/FBNeo) | 75 |
| Game Boy | 9 | Sega Naomi | 56 |
| Game Boy Color | 10 | Mega Drive / Genesis | 1 |
| Game Boy Advance | 12 | Master System | 2 |
| Nintendo DS | 15 | Saturn | 22 |
| Nintendo 3DS | 17 | Neo Geo (AES) | 142 |
| Switch | 225 | Neo Geo MVS | 68 (RomM) |
| Virtual Boy | 11 | Neo Geo CD | 70 |
| Famicom Disk System | 106 | PC Engine / TurboGrafx-16 | 31 |
| Sega CD | 20 | PC Engine CD | 114 |
| 32X | 19 | SuperGrafx | 105 |
| Game Gear | 21 | Atari 2600 | 26 |
| N64DD | 122 | Atari 7800 / 5200 / Lynx / Jaguar | 41 / 40 / 28 / 27 |
| Android | 63 | Windows / Steam | 138 |
| DOS / PC | 135 | ScummVM | 123 |

Fetch `systemesListe.php` once (cache it) to get names, extensions and system artwork.

### 1.9 Licensing notes

The site footer states the data is under **Creative Commons Attribution-NonCommercial-ShareAlike 4.0** **[source]**. Combined with the "free applications only" API rule, ScreenScraper is fine for an open-source, non-monetised frontend; it would not be fine for a paid or ad-supported build without written permission.

---

## 2. IGDB (Twitch) — API v4

Docs: <https://api-docs.igdb.com/> (Slate single page; the site blocks non-browser user agents, so fetch with a browser UA) **[verified]**.

### 2.1 Authentication (Twitch OAuth client credentials)

1. Create a Twitch developer application at dev.twitch.tv; **Client Type must be "Confidential"** to obtain a Client Secret; 2FA required on the Twitch account **[source]**.
2. `POST https://id.twitch.tv/oauth2/token?client_id=...&client_secret=...&grant_type=client_credentials` → `{"access_token": "...", "expires_in": 5587808, "token_type": "bearer"}` (roughly 60 days) **[source]**.
3. Every API call: `POST https://api.igdb.com/v4/{endpoint}` with headers `Client-ID: <client id>` and `Authorization: Bearer <access_token>`, body = Apicalypse text (`Content-Type: text/plain`) **[source]**.
4. Limits: **4 requests/second** (429 on excess) and **8 open requests** at a time; **25 active tokens per application** (older ones get invalidated); tokens live ~60 days **[source]**.

Twitch user credentials cannot be used — only application credentials **[source: FAQ]**.

### 2.2 Apicalypse query language

Statements are semicolon-terminated and sent in the POST body **[source]**:

| Clause | Example | Notes |
|---|---|---|
| `fields` | `fields name,summary,cover.image_id;` | `*` for all; dot-notation **expands** referenced entities (`cover.*`, `genres.name`, `involved_companies.company.name`) |
| `exclude` | `fields *; exclude alternative_names;` | |
| `where` | `where platforms = (19) & first_release_date > 700000000;` | `=`, `!=`, `>`, `<`, `>=`, `<=`, `~` (case-insensitive), `(a,b)` any-of, `[a,b]` all-of, `{a,b}` exactly, `& |` |
| `search` | `search "Chrono Trigger";` | Fuzzy; cannot be combined with `sort` |
| `sort` | `sort first_release_date desc;` | |
| `limit` / `offset` | `limit 50; offset 50;` | default 10, **max 500** |
| count | `POST /v4/games/count` with the same `where` | returns `{count}` |
| multiquery | `POST /v4/multiquery` body `query games "main" { fields name; where id = 1942; }; query covers "c" {...};` | max **10** sub-queries |

Examples for our use cases:

```
POST /v4/games
fields name,slug,summary,storyline,first_release_date,total_rating,total_rating_count,
       cover.image_id,cover.width,cover.height,screenshots.image_id,artworks.image_id,
       videos.video_id,videos.name,genres.name,themes.name,game_modes.name,
       involved_companies.company.name,involved_companies.developer,involved_companies.publisher,
       alternative_names.name,platforms,release_dates.date,release_dates.platform,release_dates.region,
       game_type,version_parent,parent_game;
search "Super Mario World"; where platforms = (19) & version_parent = null; limit 10;
```

```
POST /v4/search
fields game.name,game.slug,game.cover.image_id,game.platforms,alternative_name,name;
search "castlevania"; where game != null; limit 50;
```

```
POST /v4/alternative_names
fields name,game.name,game.platforms; where name ~ *"Rockman"* & game.platforms = (18);
```

`/search` returns hits across games, characters, platforms etc. with a `game` reference; Skyscraper filters with `where game != null` and excludes editions via `game.version_parent = null` **[verified]**.

Endpoints relevant to us (all POST): `/games`, `/search`, `/covers`, `/screenshots`, `/artworks`, `/game_videos` (`fields checksum,game,name,video_id` — YouTube id), `/involved_companies` (`company, developer, publisher, porting, supporting`), `/companies`, `/genres`, `/themes`, `/game_modes`, `/platforms`, `/alternative_names`, `/release_dates`, `/game_localizations` (regional titles + covers), `/external_games` (Steam/GOG ids), `/age_ratings` **[source]**. Note the 2025 migration: enum fields such as `category` became tables (`game_type`, `game_status`, `artwork_type`, `image_type`) — use the new names **[source: docs "Migration Enums to Tables"]**.

### 2.3 Images

URL template: `https://images.igdb.com/igdb/image/upload/t_{size}/{image_id}.jpg` (`.png` also works). API responses return `url` in `t_thumb` form; replace the size token. Append `_2x` for retina (e.g. `t_cover_big_2x`) **[verified live: `t_cover_big/co1wyy.jpg` 21.8 KB, `t_cover_big_2x` 72 KB]**.

| Size token | Pixels | Fit |
|---|---|---|
| `micro` | 35×35 | thumb, centre |
| `thumb` | 90×90 | thumb, centre |
| `cover_small` | 90×128 | fit |
| `cover_big` | 264×374 | fit |
| `logo_med` | 284×160 | fit |
| `screenshot_med` | 569×320 | lfill, centre |
| `screenshot_big` | 889×500 | lfill, centre |
| `screenshot_huge` | 1280×720 | lfill, centre |
| `720p` | 1280×720 | fit, centre |
| `1080p` | 1920×1080 | fit, centre |

RomM and Skyscraper both request covers at `t_1080p` and screenshots at `t_720p` **[verified]**.

### 2.4 Platform ids (from RomM's IGDB platform table, cross-checked with slugs) **[verified]**

| Platform | id | slug | Platform | id | slug |
|---|---|---|---|---|---|
| NES | 18 | `nes` | PlayStation | 7 | `ps` |
| Famicom | 99 | `famicom` | PlayStation 2 | 8 | `ps2` |
| SNES | 19 | `snes` | PSP | 38 | `psp` |
| Super Famicom | 58 | `sfam` | PS Vita | 46 | `psvita` |
| Nintendo 64 | 4 | `n64` | Dreamcast | 23 | `dc` |
| GameCube | 21 | `ngc` | Arcade | 52 | `arcade` |
| Wii | 5 | `wii` | Mega Drive/Genesis | 29 | `genesis-slash-megadrive` |
| Game Boy | 33 | `gb` | Master System | 64 | `sms` |
| Game Boy Color | 22 | `gbc` | Saturn | 32 | `saturn` |
| Game Boy Advance | 24 | `gba` | Neo Geo AES | 80 | `neogeoaes` |
| Nintendo DS | 20 | `nds` | Neo Geo MVS | 79 | `neogeomvs` |
| Nintendo 3DS | 37 | `3ds` | PC Engine/TG-16 | 86 | `turbografx16--1` |
| Switch | 130 | `switch` | Atari 2600 | 59 | `atari2600` |

RomM notes that IGDB catalogues regional twins as separate platforms (NES/Famicom, SNES/Super Famicom), so search both ids **[verified]**.

### 2.5 Matching by name + platform

1. Clean the filename (see §7), then `POST /v4/games` with `search "<name>"; where platforms = (<id>[, twin]) & version_parent = null; limit 10;` plus `fields ... alternative_names.name`.
2. If nothing, retry without the platform filter, then via `/v4/search` (broader fuzziness) and `/v4/alternative_names` (Japanese/EU titles).
3. Rank locally (normalised Levenshtein/Jaro-Winkler on `name` and `alternative_names`, boost if `platforms` contains ours and release year matches the region tag).
4. Skyscraper's IGDB module is filename/ID based only; there is no hash lookup in IGDB — use Hasheous (§6) for hash → IGDB id.

### 2.6 Do we need a proxy?

Yes, if we ship IGDB access out of the box. Official positions **[source: docs FAQ / CORS section]**:

- "The IGDB API does not support browser requests, CORS, for security reasons. This is because the request would leak your access token! We suggest that you create a backend proxy which authenticates and queries the API directly."
- The docs list proxy reasons: keep OAuth tokens server-side, cache, log, enable CORS. Every Twitch-app token you mint counts against the 25-active-token cap and the 4 req/s limit is **per Client-ID** — a Client-Secret embedded in an APK would be shared by every install and exhausted immediately.

Options: (a) BYO credentials — user pastes their own Client ID/Secret (Skyscraper's `userCreds="CLIENTID:SECRETKEY"` model, honest and zero infra); (b) our own tiny proxy (Cloudflare Worker-style) that holds the secret and enforces per-device throttling; (c) use **Hasheous** as a free public IGDB proxy (`/api/v1/MetadataProxy/IGDB/Game?Id=...`, needs a free client key) — see §6.

### 2.7 Terms of service

- "The IGDB.com API is free for non-commercial usage under the terms of the Twitch Developer Service Agreement." Business FAQ: "The API is free for both non-commercial and commercial projects"; commercial projects should sign a partnership (`partner@igdb.com`) and provide "user facing attribution to IGDB.com ... visible to your users and located in a static location (e.g. not in a change log)" **[source]**.
- Caching: "Am I allowed to store/cache the data locally? Yes. In fact, we prefer if you store and serve the data to your end users." Data may be kept after partnership termination **[source]**.
- The Twitch Developer Services Agreement additionally requires "a clear path to the source from displays of Program Materials" (section VII.C) and prohibits re-syndication/redistribution of API data and using it to target users with off-Twitch commercial offers **[uncertain: second-hand summary; read the DSA before shipping]**.
- Daily CSV data dumps exist but are for Data Partners only **[source]**.

---

## 3. SteamGridDB — API v2

Docs are a ReDoc page at <https://www.steamgriddb.com/api/v2>; the underlying spec is <https://www.steamgriddb.com/static/openapi.yml> (OpenAPI 3.1, version 2.10.0) **[verified]**. Base URL `https://www.steamgriddb.com/api/v2`.

### 3.1 Auth

Header `Authorization: Bearer <api key>`; the key is generated by the *user* at <https://www.steamgriddb.com/profile/preferences> ("API" tab). Unauthenticated calls return **401** (`/search/autocomplete/zelda` → 401 JSON) **[verified]**. There is no application-level credential, so the app should ask the user for a key; the node client README and Steam ROM Manager both follow this model.

### 3.2 Endpoints (from the spec) **[verified]**

| Method / path | Purpose | Notes |
|---|---|---|
| `GET /games/id/{gameId}` | Game record by SGDB id | returns `{id, name, types[], verified, release_date?}` |
| `GET /games/{platform}/{platformId}` | Game by external store id | `platform` ∈ `steam, origin, egs, bnet, uplay, flashpoint, eshop` (GOG appears as a `types[]` value; not in the path enum) |
| `GET /grids/game/{gameId}` | Grids for a game (comma-delimited ids allowed → "multiple responses") | filters: `styles, dimensions, mimes, types, nsfw, humor, epilepsy, oneoftag, limit, page` |
| `GET /grids/{platform}/{id*}` | Grids by store id (e.g. `/grids/steam/220`) | same filters |
| `GET /heroes/game/{gameId}`, `GET /heroes/{platform}/{id*}` | Hero banners | `styles` (hero enum), `dimensions` (hero enum), `mimes`, `types`, tags |
| `GET /logos/game/{gameId}`, `GET /logos/{platform}/{id*}` | Logos | `styles` (logo enum), `mimes` (png/webp), `types`, tags — **no dimensions** |
| `GET /icons/game/{gameId}`, `GET /icons/{platform}/{id*}` | Icons | `styles` (icon enum), `dimensions` (icon sizes), `mimes` (png / `image/vnd.microsoft.icon`) |
| `GET /search/autocomplete/{term}` | Name search | returns `data: [{id, name, types[], verified}]` — this is how non-Steam / retro games are found |
| `POST /grids`, `DELETE /grids/{gridIds}` (and heroes/logos/icons) | Upload/delete own assets | not needed |

### 3.3 Filter enums **[verified from spec]**

| Param | Values |
|---|---|
| Grid `styles` | `alternate, blurred, white_logo, material, no_logo` |
| Grid `dimensions` | `460x215, 920x430` (horizontal "Steam capsule"), `600x900` (vertical, the de-facto cover), `342x482, 660x930` (GOG Galaxy tiles), `512x512, 1024x1024` (square) |
| Hero `styles` | `alternate, blurred, material` |
| Hero `dimensions` | `1920x620, 3840x1240, 1600x650` |
| Logo `styles` | `official, white, black, custom` |
| Icon `styles` | `official, custom` |
| Icon `dimensions` | `8,10,14,16,20,24,28,32,35,40,48,54,56,57,60,64,72,76,80,96,114,120,128,144,152,160,180,192,194,256,512,768,1024` (ico files match if they contain the size) |
| `mimes` | grids/heroes: `image/png, image/jpeg, image/webp`; logos: `image/png, image/webp`; icons: `image/png, image/vnd.microsoft.icon` |
| `types` | `static` (default), `animated` (APNG/WebP) |
| `nsfw`, `humor`, `epilepsy` | `false` (default, filtered out), `true` (only), `any` |
| `oneoftag` | `humor, nsfw, epilepsy` — combine with `...=any` to emulate "untagged" |
| `limit` | default 50, values > 50 ignored |
| `page` | default 0 |

Response shape: `{"success": true, "page": 0, "total": 123, "limit": 50, "data": [{id, score, style, width, height, nsfw, humor, epilepsy, lock, url, thumb, tags[], language, mime, notes, author{name, steam64, avatar}}]}`. `thumb` is a reduced preview (spec says 380×178 for grids); `url` is the full asset on S3/CDN **[verified spec + RomM types]**.

Example: `GET /grids/game/2254?dimensions=600x900&styles=alternate,material&types=static&nsfw=false&limit=50` with `Authorization: Bearer ...`.

### 3.4 Rate limits and terms

No rate limit is documented in the spec or client READMEs **[verified absence]**; clients paginate at 50 and tolerate 429 generically. Treat as "be polite" (≤ 1–2 req/s per key) **[uncertain]**. There is no published API ToS beyond the site terms; assets are community uploads with unclear rights — fine for local caching in a frontend, not for redistribution.

### 3.5 Non-Steam games

SGDB has entries for many console games (types include `steam`, `gog`, `egs`, `origin`, `uplay`, `bnet`, `flashpoint`, `eshop` and plain community entries with `types: []`). Discovery is by `/search/autocomplete/{term}`; there is no platform filter, so rank results by name similarity and prefer `verified: true`. Best fit: Android/PC/Steam games and modern titles (Switch via `eshop` ids), and as a *logo* (`/logos`) and *hero* source for any game where ScreenScraper has no wheel/fanart.

---

## 4. TheGamesDB (api.thegamesdb.net)

Swagger UI at <https://api.thegamesdb.net/>; spec at <https://api.thegamesdb.net/spec.yaml> (Swagger 2.0, "TheGamesDB API 2.0.0", GPL-3.0 server code) **[verified]**. Base path `https://api.thegamesdb.net`.

### 4.1 Key model

- Every call requires `apikey` (query). Keys are requested by posting on the TGDB forum ("API Key Request" thread). Two kinds: a **public key** intended to be shipped in an app (its allowance is metered per *end-user IP*, "thats the whole point of the public key") and a **private key** for servers/scrapers **[uncertain: forum thread t=60/t=2430 require login; figures from search snippets]**.
- Allowance figures seen: "1000 requests per IP per month" for public keys (Skyscraper docs) and 3000/month for private keys **[uncertain]**. Every response carries `remaining_monthly_allowance`, `extra_allowance` and `allowance_refresh_timer` (seconds; example 2592000 = 30 days) **[verified in spec]**; `GET /v1/API/Limit?apikey=` returns them without spending allowance **[verified]**. ES-DE embeds an obfuscated key and logs the allowance; Skyscraper lets users set a 64-char private key via `userCreds`.

### 4.2 Endpoints **[verified from spec]**

| Path | Params | Notes |
|---|---|---|
| `GET /v1/Games/ByGameID` | `id` (comma list), `fields`, `include`, `page` | |
| `GET /v1/Games/ByGameName` | `name`, `fields`, `filter[platform]`, `filter[region]`, `filter[country]`, `include`, `page` | v1 quirk: any `mode` value triggers natural-language search |
| `GET /v1.1/Games/ByGameName` | same + `mode=natural` | `include.platform` wrapped in `data` unlike v1 — **prefer v1.1** |
| `GET /v1/Games/ByPlatformID` | `id` | bulk listing per platform |
| `GET /v1/Games/ByGameUniqueID` | `uid`, `filter[platform]` | **serial / external id lookup** |
| `GET /v1/Games/ByGameHash` | `hash`, `filter[type]=md5|crc`, `filter[platform]` | **hash lookup** (new; coverage unknown [uncertain]) |
| `GET /v1/Games/Images` | `games_id` (comma list), `filter[type]`, `page` | types: `fanart, banner, boxart, screenshot, clearlogo, titlescreen` |
| `GET /v1/Games/Videos` | `games_id` | "Prepend base_url to each filename" |
| `GET /v1/Games/Updates` | `last_edit_id` / time | incremental sync |
| `GET /v1/Platforms`, `/v1/Platforms/ByPlatformID`, `/ByPlatformName`, `/v1/Platforms/Images` | `fields=icon,console,controller,developer,manufacturer,media,cpu,memory,graphics,sound,maxcontrollers,display,overview,youtube`, `include=boxart` | |
| `GET /v1/Genres`, `/v1/Developers`, `/v1/Publishers`, `/v1/Regions`, `/v1/Countries` | | reference data |

`fields` for games: `players, publishers, genres, overview, last_updated, rating, platform, coop, youtube, os, processor, ram, hdd, video, sound, alternates`; `include`: `boxart, platform`.

Response envelope: `{code, status, remaining_monthly_allowance, extra_allowance, allowance_refresh_timer, data: {count, games: [...]}, include: {boxart: {base_url: {...}, data: {"<gameId>": [{id, type, side, filename, resolution}]}}}, pages: {previous, current, next}}` **[verified]**.

### 4.3 Images

`base_url` sizes: `original` `https://cdn.thegamesdb.net/images/original/`, `small` `/images/small/`, `thumb` `/images/thumb/`, `cropped_center_thumb` `/images/cropped_center_thumb/`, `medium` `/images/medium/`, `large` `/images/large/` **[verified]**. Filenames look like `boxart/front/53-1.jpg`, `boxart/back/53-1.jpg`, `screenshots/53-1.jpg`, `fanart/189-1.jpg`, `clearlogo/…`; videos `https://cdn.thegamesdb.net/videos/{id}/{id}-1.mp4` (Skyscraper) **[source]**. Live check: `https://cdn.thegamesdb.net/images/original/boxart/front/1-1.jpg` and `/images/thumb/boxart/front/1-1.jpg` both 200 **[verified]**.

### 4.4 Platform ids (Skyscraper `platforms_idmap.csv`, `tgdb_id` column) **[verified]**

NES 7, SNES 6, N64 3, GameCube 2, Wii 9, Wii U 38, GB 4, GBC 41, GBA 5, NDS 8, 3DS 4912, Switch 4971, PS1 10, PS2 11, PSP 13, Vita 39, Dreamcast 16, Arcade 23, Mega Drive 36 (Genesis 18), Master System 35, Saturn 17, Neo Geo 24, Neo Geo CD 4956, PC Engine 34, PC Engine CD 4955, Atari 2600 22. Fetch `/v1/Platforms` once to confirm.

---

## 5. libretro-thumbnails and libretro-database (offline-friendly, no auth)

### 5.1 libretro-thumbnails

- Repo: <https://github.com/libretro-thumbnails/libretro-thumbnails> — a superproject with **124 git submodules**, one per system, named exactly like RetroArch playlists/databases (`Nintendo - Super Nintendo Entertainment System`, `Sony - PlayStation`, `Sega - Mega Drive - Genesis`, `MAME`, `FBNeo - Arcade Games`, ...). Submodule repo names replace spaces with underscores (`libretro-thumbnails/Nintendo_-_Super_Nintendo_Entertainment_System`) **[verified from .gitmodules]**.
- Directory layout per system: `Named_Boxarts/`, `Named_Snaps/` (in-game), `Named_Titles/` (title screen), and for some systems `Named_Logos/` (RetroArch ≥ 1.19.1, XMB only) **[source]**.
- **File name = database (No-Intro/Redump) game name + `.png`** with the characters `` &*/:`<>?\|" `` replaced by `_` (e.g. `Q*bert's Qubes (USA)` → `Q_bert's Qubes (USA).png`). Images are PNG, downscaled to 512 px width **[source: repo README, docs.libretro.com]**.
- Serving:
  - Single file: `https://thumbnails.libretro.com/{System}/{Named_Boxarts|Named_Snaps|Named_Titles|Named_Logos}/{Name}.png` (URL-encode spaces as `%20`). Live: `.../Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/Super%20Mario%20World%20(USA).png` → 200 `image/png`; same for `Named_Snaps`, `Named_Titles`, `Named_Logos` **[verified]**. The server root lists systems as directories.
  - Same file via GitHub raw: `https://raw.githubusercontent.com/libretro-thumbnails/{Repo}/master/Named_Boxarts/{Name}.png` → 200 **[verified]**.
  - Whole-system pack: `https://github.com/libretro-thumbnails/{Repo}/archive/refs/heads/master.zip` (302 → codeload) **[verified]**. `thumbnails.libretro.com/{System}.zip` and `/Named_Boxarts.zip` do **not** exist (404) **[verified]**; RetroArch's in-app pack downloads come from its own buildbot paths that were not reachable in this test [uncertain].
- RetroArch's own matching (≥ 1.17): (1) ROM filename → PNG, (2) playlist label (DB name) → PNG, (3) label truncated before the first `(`. Mirror this in our resolver.
- Coverage is best for No-Intro cartridge systems and Redump disc systems; arcade uses MAME short names (`MAME/Named_Snaps/sf2.png`). License: none stated in the README **[uncertain]**; images are user-contributed scans.

### 5.2 libretro-database (.dat / .rdb)

Repo: <https://github.com/libretro/libretro-database>, license **CC-BY-SA-4.0** **[source]**.

- `metadat/<category>/<System>.dat` — clrmamepro text DATs. Categories present **[verified]**: `no-intro`, `redump`, `tosec`, `mame`, `mame-split`, `mame-nonmerged`, `mame-member`, `fbneo-split`, `fbneo-member`, `developer`, `publisher`, `releaseyear`, `releasemonth`, `genre`, `franchise`, `maxusers`, `serial`, `origin`, `esrb`, `elspa`, `pegi`, `bbfc`, `enhancement_hw`, `rumble`, `analog`, `headered`, `homebrew`, `hacks`, `magazine`, `libretro-dats`, `lost-level-archive`.
- DAT entry format **[verified sample]**:

  ```
  game (
      name "Super Mario World (USA)"
      region "USA"
      rom ( name "Super Mario World (USA).sfc" size 524288 crc B19ED489 md5 CDD3C8C3... sha1 6B47BB75... )
  )
  ```

  Metadata DATs join on the key field: `game ( comment "2020 Super Baseball (Japan)" releaseyear "1993" rom ( crc E95A3DD7 ) )`. Disc DATs carry `serial "SLPS-01204"` both at game and rom level.
- **Key field**: CRC32 for cartridge-era systems; **serial** (read from the disc image / cue) for disc systems to avoid hashing large files. `rom.name`/`size` help disambiguate **[source: README]**.
- `rdb/<System>.rdb` — binary RetroArch DB compiled from the DATs (msgpack-like; `libretrodb_tool` can `list`, `find '{"crc":b"31B965DB"}'`, `get-names`, `create-index`). Fields: `name, description, rom_name, size, crc, md5, sha1, serial, developer, publisher, releaseyear, releasemonth, genre, users, franchise, region, esrb_rating, ...` **[source]**. Download all RDBs: `https://buildbot.libretro.com/assets/frontend/database-rdb.zip` (200, zip) or single files via `https://raw.githubusercontent.com/libretro/libretro-database/master/rdb/{System}.rdb` (200) **[verified]**.
- For a Kotlin app the text DATs are easier: parse once per system into Room/SQLite (`crc`, `md5`, `sha1`, `serial`, `size` → canonical `name`, plus developer/publisher/year/genre/users from the metadata DATs). Precedence: `dat/` overrides `metadat/`, No-Intro > TOSEC.
- The canonical `name` is exactly the libretro-thumbnails filename (after character sanitising), which is what makes the pair "libretro-database + libretro-thumbnails" a zero-auth offline pipeline.

---

## 6. Other sources (brief)

### 6.1 LaunchBox Games Database (Metadata.xml)

- Bulk dump: `https://gamesdb.launchbox-app.com/Metadata.zip` — 107,746,889 bytes, regenerated daily (Last-Modified 2026-09-17) **[verified HEAD]**. Contains `Metadata.xml` (+ `Mame.xml`, `Platforms.xml` in recent versions [uncertain]).
- Structure (from LaunchBox staff/community and RomM's importer) **[uncertain on exact tag list]**: `<Game>` with `Name, ReleaseYear, ReleaseDate, Overview, MaxPlayers, ReleaseType, Cooperative, VideoURL, WikipediaURL, DatabaseID, CommunityRating, CommunityRatingCount, Platform, ESRB, Genres, Developer, Publisher`; `<GameImage>` with `DatabaseID, FileName, Type ("Box - Front", "Box - Back", "Box - 3D", "Fanart - Background", "Clear Logo", "Screenshot - Gameplay", "Screenshot - Game Title", "Cart - Front", "Disc", "Banner", "Arcade - Marquee"...), Region, CRC`; `<GameAlternateName>`, `<Platform>`, `<PlatformAlternateName>`, `<MameFile>`.
- Images are served from `https://images.launchbox-app.com/<FileName>` (Jason Carr: "It includes the image file names, which can be easily used to construct a URL") **[uncertain exact path]**; the root returns 404 **[verified]**.
- No public API (feature request "Public API Access" is open), and **no license statement** for third-party use was found on the site, help centre or forums **[verified absence]**. RomM ships a LaunchBox importer behind `LAUNCHBOX_API_ENABLED`. Treat as "ask Unbroken Software before shipping"; the XML is useful offline for name → overview/genre/year, alternate names and clear-logo discovery.

### 6.2 MobyGames API

- Became paid in September 2024 (previously free keys); documentation removed the free-key path **[source: obsidian-media-db issue #164]**. Current non-commercial tier: **"Hobbyist API" $9.99/month, 0.2 requests/s (720/hour)**, bundle with MobyPlus $12.99/month; higher tiers unlock release dates/ratings/videos; commercial tiers separate **[uncertain: from search snippets of mobygames.com/api/subscribe, site blocks bots]**. Skyscraper's MobyGames module notes the Hobbyist limitation of "first release date worldwide" and 1 req / 5 s.
- Endpoints (v1): `https://api.mobygames.com/v1/games?title=&platform=&format=normal|brief|id&api_key=`, `/games/{id}/platforms/{pid}/covers`, `/screenshots`, `/platforms`, `/genres` **[uncertain]**. Platform ids are in Skyscraper's `platforms_idmap.csv` (`mobygames_id`).
- Verdict: skip for an open-source frontend, or support as "bring your own paid key".

### 6.3 RAWG

- Docs: <https://rawg.io/apidocs>. Base `https://api.rawg.io/api`, `key` query param; endpoints `/games?search=&platforms=&search_exact=&search_precise=&page_size=`, `/games/{id}`, `/games/{id}/screenshots`, `/platforms` **[source]**.
- Terms: "free for personal use as long as you attribute RAWG as the source ... active hyperlink"; 20,000 requests/month; commercial projects under 100k MAU / 500k page views are free, larger must contact RAWG; **no data redistribution or competing services** **[source]**. Coverage is modern-PC-centric; retro metadata is thin. Low priority.

### 6.4 OpenVGDB (SQLite)

- Latest release **v29.0 (2021-11-11)**, `openvgdb.zip` 9.1 MB → `openvgdb.sqlite` 42 MB; 51,742 ROMs, 53,871 releases **[verified]**. Effectively unmaintained since.
- Schema **[verified]**:
  - `ROMs(romID, systemID, regionID, romHashCRC, romHashMD5, romHashSHA1, romSize, romFileName, romExtensionlessFileName, romParent, romSerial, romHeader, romLanguage, TEMPromRegion, romDumpSource)`
  - `RELEASES(releaseID, romID, releaseTitleName, regionLocalizedID, TEMPregionLocalizedName, TEMPsystemShortName, TEMPsystemName, releaseCoverFront, releaseCoverBack, releaseCoverCart, releaseCoverDisc, releaseDescription, releaseDeveloper, releasePublisher, releaseGenre, releaseDate, releaseReferenceURL, releaseReferenceImageURL)`
  - `SYSTEMS(systemID, systemName, systemShortName, systemHeaderSizeBytes, systemHashless, systemHeader, systemSerial, systemOEID)` — 43 systems incl. NES, SNES, N64, GC, Wii, GB/GBC/GBA, NDS, PSX, PSP, Genesis, SMS, Saturn, PCE, 2600 (no PS2/3DS/Switch/Vita/Dreamcast).
  - `REGIONS(regionID, regionName)` — 39 entries (`USA`, `Europe`, `Japan`, `World`, combos like `USA, Europe`).
- Sample: CRC `B19ED489` → `Super Mario World (USA).sfc`, title "Super Mario World", cover `https://gamefaqs.gamespot.com/a/box/6/2/5/14625_front.jpg`, developer Nintendo, date "Aug 13, 1991" **[verified]**. PSX rows carry `romSerial` (`SLES-03355`).
- Caveat: cover URLs hotlink GameFAQs/others (rights and link-rot issues). Useful for offline hash → title/description/year/developer, as a complement to libretro-database (which has no descriptions).

### 6.5 Hasheous (hasheous.org)

- Open source (gaseous-project/hasheous). Matches TOSEC, No-Intro, Redump, MAME Arcade/MESS, FBNeo(?) signatures to IGDB (and TheGamesDB, RetroAchievements, GiantBomb, Steam, GOG, Wikipedia ids) and **proxies/caches IGDB** so clients need no Twitch credentials **[verified README + swagger]**.
- Endpoints **[verified from /swagger/v1/swagger.json]**:
  - `GET /api/v1/Lookup/ByHash/{crc|md5|sha1|sha256}/{hash}` — no auth. Live: `/Lookup/ByHash/crc/38d167fd` → `{id, name, platform{name, metadata[{source: IGDB|TheGamesDb|RetroAchievements|GiantBomb|Steam|GOG, id, immutableId, status: Mapped|NotMapped, link}]}, publisher, signature{...}, signatures, metadata[...], attributes[...]}` **[verified]**.
  - `POST /api/v1/Lookup/ByHash` — body one or many `{crc, md5, sha1, sha256}` objects; query `returnAllSources`, `returnFields`, `returnSources` (TOSEC, MAMEArcade, ...).
  - `GET /api/v1/Lookup/Platforms` (paged).
  - `GET /api/v1/MetadataProxy/IGDB/Game?Id=|slug=&expandColumns=`, `/MetadataProxy/IGDB/Cover?Id=`, `/Artwork`, `/Screenshot`, `/GameVideo`, `/InvolvedCompany`, `/Platform`, ... — require header **`X-Client-API-Key`** (free client key from a Hasheous account) **[verified]**; `GET /api/v1/MetadataProxy/IGDB/Image/{ImageId}.jpg?redirect=` — no auth.
  - `GET /api/v1/Dumps/MetadataMap.zip` and `/Dumps/platforms/{platformname}.zip` — offline dumps of the hash → metadata-id map **[verified path; not downloaded]**.
- Rate limiting is profile-based (roles/origin/UA) and hot-reloaded; no public numbers **[source]**. RomM integrates it (`HASHEOUS_API_ENABLED`). Terms of service = the GitHub project page. Good candidate for "hash → IGDB id" and as the default IGDB proxy when the user has not supplied their own Twitch credentials.

### 6.6 GameTDB (Wii/GC/WiiU/DS/3DS/Switch/PS3)

- Databases: `https://www.gametdb.com/wiitdb.zip?LANG=EN` (also `dstdb.zip`, `3dstdb.zip`, `switchtdb.zip`, `wiiutdb.zip`, `ps3tdb.zip`; `LANG` = `EN, JA, FR, DE, ES, IT, NL, PT, ZHTW, ZHCN, KO`; Wii also accepts `WIIWARE=1`, `GAMECUBE=1` [uncertain]) **[verified downloads]**. `wiitdb.xml` = `<datafile><WiiTDB version filter games/><companies><company code name/></companies><game name="..."><id>RSPE01</id><type/><region>NTSC-U</region><languages>EN,FR</languages><locale lang="EN"><title/><synopsis/></locale><developer/><publisher/><date year month day/><genre/><rating type="ESRB" value=""/><wi-fi players/><input players/><rom version name size crc md5 sha1/></game>...` **[verified]**. Switch ids are 5-char title codes like `A224B` (not 16-hex title ids) **[verified]**.
- Cover URL pattern: `https://art.gametdb.com/{platform}/{type}/{REGION}/{ID}.{png|jpg}` where `REGION` must match the game's region folder (`US`, `EN`, `JA`, `FR`, `DE`, `ES`, `IT`, `NL`, `KO`, `ZH`, `AU`, `RU`, ...). Verified 200s: `wii/cover/US/RSPE01.png`, `wii/cover3D/US/RSPE01.png`, `wii/coverfull/US/GALE01.png`, `wii/coverfullHQ/US/RSPE01.png`, `wii/disc/US/RSPE01.png`, `wii/cover/EN/RSPP01.png` (GameCube shares the `wii` folder), `ds/coverS/US/ASME.png`, `ds/box/US/ASME.png`, `3ds/box/JA/A22J.png`, `3ds/box/EN/AREP.png`, `switch/coverHQ/JA/A224B.jpg`, `wiiu/coverHQ/US/ARDE01.jpg`, `ps3/cover/US/BLUS30443.jpg` **[verified]**. Not found: `3ds/coverHQ`, `switch/cover`, `ds/coverM`, `wii/discM` for the tested ids — treat type names per platform as: Wii/GC `cover, cover3D, coverfull, coverfullHQ, disc`; DS `coverS, box`; 3DS `box` (+ `coverHQ` for some); Switch/WiiU/PS3 `coverHQ` (jpg).
- Weekly cover packs exist (`download.php?FTP=GameTDB-wii_cover-EN-2026-09-16.zip` etc.) **[verified links]**.
- Terms: "collaborative database ... for anyone to contribute and anyone to use in any Wii-related project" but "Do not use without permission if you wish to use this file on a website" (XML header and FAQ) **[verified]**. Homebrew/frontend use with local caching is the intended use; avoid bulk hammering (use packs).
- Value for us: serial-based, region-exact covers for GC/Wii/DS/3DS/Switch where hash matching is impractical (multi-GB images). Serial extraction: Wii/GC id at offset 0 of ISO/GCM (6 ASCII chars; RVZ/WBFS/CISO need header parsing), DS at offset 0x0C (4 chars), 3DS product code in NCSD/NCCH header, Switch title id from NSP/XCI control NACP (needs keys — use filename `[0100...]` tags instead).

---

## 7. Recommended provider strategy and matching pipeline

### 7.1 Provider tiers

1. **Tier 0 — offline identity (no network, no auth):**
   - Parse **libretro-database** DATs (No-Intro/Redump + metadata) into a local SQLite: `hash/serial → canonical name, region, year, developer, publisher, genre, players`.
   - Optionally bundle/ download **OpenVGDB** for descriptions and as a second hash table (older, but adds `releaseDescription`).
   - **GameTDB** TDB XML for GC/Wii/DS/3DS/Switch titles by serial (synopsis, genre, players, ratings).
   - Result: every ROM with a known hash/serial gets a canonical display name before any artwork provider is contacted.
2. **Tier 1 — zero-config artwork:** **libretro-thumbnails** by canonical name (boxart/snap/title/logo), **GameTDB** covers by serial for Nintendo disc/cart systems, **Hasheous** hash lookup for IGDB/TGDB ids (and its IGDB proxy for cover/screenshots without user credentials).
3. **Tier 2 — full media, user opts in:** **ScreenScraper** with the user's `ssid/sspassword` (our own dev credentials embedded, obfuscated). Provides wheel/logo, 3D box, mix images, videos, marquee, manuals, fanart and the best hash coverage. Respect `maxthreads`/`maxrequestspermin` from `ssuserInfos.php`, stop on 430/431, and offer "scrape only missing media".
4. **Tier 3 — modern metadata:** **IGDB** via (a) Hasheous proxy by default, (b) user-supplied Twitch Client ID/Secret, or (c) our own proxy if we ever run infrastructure. Use for summaries, ratings, genres/themes, YouTube video ids, artworks; platform ids in §2.4.
5. **Tier 4 — PC/Android/Steam-like artwork:** **SteamGridDB** with a user API key: `600x900` grids as covers, `1920x620` heroes as backgrounds, `official/white` logos, icons. Also a good logo fallback for consoles.
6. **Optional / BYO key:** TheGamesDB (private key), MobyGames (paid), RAWG. LaunchBox Metadata.xml only after clarifying licensing.

Every adapter implements the same contract: `identify(rom) -> List<Candidate(score, providerId, name, platform)>`, `fetchMetadata(candidate) -> GameMetadata`, `listMedia(candidate) -> List<MediaRef(type, url, region, lang, w, h, format, hash?)>`, plus `capabilities` (hash lookup? name search? media types? needs user key? offline?) and `limits` (concurrency, rps, daily budget, reset time) so a scheduler can pace all providers uniformly.

### 7.2 Matching pipeline

1. **Normalise the filename**
   - Strip extension(s) (`.zip`, `.7z`, `.chd`, `.cue/.bin`, `.m3u`); for archives with a single ROM inside use the inner filename.
   - Extract and remember tags: region `(USA)`, `(Europe)`, `(Japan)`, `(World)`, `(En,Fr,De)`, GoodTools `[!]`, `[b]`, `[h]`, `[t]`, `(Rev 1)`, `(v1.1)`, `(Beta)`, `(Proto)`, `(Demo)`, `(Unl)`, `(Disc 1)`, `(Track 1)`, `(Virtual Console)`, bracketed serials `[SLUS-00594]`, Switch `[0100000000010000][v0]`.
   - Build a `searchName`: remove all `(...)`/`[...]` groups, collapse `, The` → `The ...` (No-Intro article inversion), replace `_` with space, drop trailing `-`/`:` fragments only for the second attempt, collapse whitespace, NFKC-fold, lower-case for comparison.
   - Map region tags to provider region codes (`USA`→`us`, `Europe`→`eu`, `Japan`→`jp`, `World`→`wor`; ScreenScraper `favregion`, IGDB `release_dates.region`).
2. **Hash / serial identification**
   - Cartridge systems (NES, SNES, N64, GB/GBC/GBA, NDS, MD/SMS/GG, PCE, 2600, Lynx, WonderSwan, NGP): compute **CRC32** (and MD5/SHA1 in the same pass) of the *decompressed* ROM; for `.zip` read the entry stream, for `.7z` decompress (cap at e.g. 64 MB, Skyscraper uses ~78 MB, Batocera 128 MB, ES-DE 384 MB — we should pick 64 MB for phones and hash the archive itself above that). Handle headered formats: strip 512-byte SMC headers for SNES and the 16-byte iNES header for NES *only* for DAT lookups that expect headerless hashes (No-Intro NES DATs are headered; libretro `metadat/headered` covers this) **[uncertain: verify per system]**. Send both raw-file and inner-file hashes to ScreenScraper when cheap.
   - Disc systems (PS1/PS2/PSP/Saturn/Dreamcast/GC/Wii/3DS/Switch): **skip hashing** (files are 100 MB–4 GB). Read the serial from the image (PS1/PS2 `SYSTEM.CNF` `BOOT2 = cdrom0:\SLUS_123.45`, PSP `PARAM.SFO` `DISC_ID`, Saturn/Dreamcast IP.BIN header at sector 0, GC/Wii header id, DS header id) or from `[SLUS-00594]` filename tags; match on `serial` in libretro-database, GameTDB, TGDB `ByGameUniqueID`, ScreenScraper `serialnum`/`romnom`.
   - Arcade (MAME/FBNeo): never hash the zip; the **short name** is the identity (`sf2.zip` → `sf2`). Use ScreenScraper `romnom`+`systemeid=75`, libretro `metadat/mame` DATs for the long title, then look up thumbnails under `MAME/`.
   - ROM > 64 MB and no serial: fall back to name matching only.
3. **Name matching / fuzzy ranking** (when hash/serial fails or for name-only providers)
   - Query with `searchName` restricted to the platform id; retry without platform; retry with alternate-name endpoints; final retry with the first 4+ significant tokens.
   - Score = weighted combination of token-set ratio / Jaro-Winkler between `searchName` and each candidate `name` + `alternative_names` (accent- and punctuation-folded, roman/arabic numerals normalised, `&`↔`and`), + platform match bonus, + release-year vs region-tag plausibility, − penalty for editions when `version_parent != null`. Accept automatically above ~0.85 (Skyscraper `minMatch` default 65 %, ES-DE asks the user below its threshold); otherwise present top-N for the user to pick and remember the choice (`providerId` pinned in our DB).
4. **Media selection**: for each desired slot (cover, logo, screenshot, title, fanart, video, marquee, 3D box) walk a provider priority list and, within a provider, a type fallback chain (§1.5) and the region fallback chain (`romRegion → userRegion → wor → us → eu → jp → ss → cus → any`).
5. **Cache policy**
   - Persist per game: `providerId`, `matchMethod` (hash/serial/name/manual), `matchScore`, `fetchedAt`, and the *provider ids* of chosen media (ScreenScraper `medias[].id`+`md5`, IGDB `image_id`, SGDB `id`), so re-scrapes can skip downloads using ScreenScraper's `md5` check (`MD5OK`) or by comparing ids.
   - Media files stored under app storage by `{platform}/{gameKey}/{slot}.{ext}`; images resized on device (cover ≤ 1024 px height, screenshots ≤ 1280 px) to bound storage; videos only on Wi-Fi and opt-in.
   - Metadata TTL: none for hash-identified ScreenScraper/libretro records (immutable), 30 days for IGDB ratings/summaries (IGDB explicitly prefers local caching), 7 days for negative results (remember "unknown to provider X" to protect ScreenScraper's KO quota), refresh reference lists (`systemesListe`, IGDB `/platforms`, TGDB `/Platforms`) monthly.
   - Global scheduler: one queue per provider with its own concurrency (`ScreenScraper.maxthreads`, IGDB 4 rps / 8 in flight, SGDB ~1 rps, TGDB budget from `remaining_monthly_allowance`), exponential backoff on 429/5xx, circuit-breaker on 430/431/426/403, resume on app restart, Wi-Fi-only default for media downloads.
   - Attribution screen listing ScreenScraper (CC BY-NC-SA), IGDB, SteamGridDB, TheGamesDB, libretro, GameTDB, OpenVGDB, Hasheous with links (IGDB requires a static, visible credit).

### 7.3 Open questions / to verify before implementation

- Obtain our own ScreenScraper `devid` (forum post with app description, repo link, free/open-source statement); confirm whether an Android app storing user passwords in the query string is acceptable (it is what ES-DE Android does).
- Exact current ScreenScraper per-minute formula and anonymous daily quota (read from `ssuserInfos.php` at runtime rather than hard-coding).
- TheGamesDB public-key allowance and whether a public key may be embedded; coverage of the new `ByGameHash` endpoint.
- Whether Hasheous' IGDB proxy is happy with the request volume of a consumer app (ask on their Discord; consider donating).
- LaunchBox Metadata.zip licensing for third-party apps.
- libretro-thumbnails image licensing (none stated).

---

## Sources

Official docs / specs
- ScreenScraper API v2 overview: https://www.screenscraper.fr/webapi2.php ; WebAPI forum: https://www.screenscraper.fr/forumsujets.php?frub=12&numpage=0
- IGDB API docs (Getting started, Apicalypse, Images, Multi-Query, CORS/Proxy, Business & Technical FAQ, Partnership): https://api-docs.igdb.com/
- IGDB commercial use thread (Twitch forums): https://discuss.dev.twitch.com/t/commercial-use-of-igdb-api/23567 ; Twitch Developer Services Agreement: https://legal.twitch.com/legal/developer-agreement/
- SteamGridDB API v2 ReDoc: https://www.steamgriddb.com/api/v2 ; OpenAPI spec: https://www.steamgriddb.com/static/openapi.yml ; node client: https://github.com/SteamGridDB/node-steamgriddb
- TheGamesDB Swagger UI: https://api.thegamesdb.net/ ; spec: https://api.thegamesdb.net/spec.yaml ; server source: https://github.com/TheGamesDB/TheGamesDBv2 ; allowance thread: https://forums.thegamesdb.net/viewtopic.php?t=60 ; key request: https://forums.thegamesdb.net/viewtopic.php?t=2430
- libretro-thumbnails: https://github.com/libretro-thumbnails/libretro-thumbnails ; server: https://thumbnails.libretro.com/ ; RetroArch thumbnail docs: https://docs.libretro.com/guides/roms-playlists-thumbnails/
- libretro-database: https://github.com/libretro/libretro-database ; RDB format: https://github.com/libretro/RetroArch/blob/master/libretro-db/README.md ; RDB bundle: https://buildbot.libretro.com/assets/frontend/database-rdb.zip
- OpenVGDB releases: https://github.com/OpenVGDB/OpenVGDB/releases (v29.0)
- Hasheous: https://hasheous.org/ ; swagger: https://hasheous.org/swagger/v1/swagger.json ; source: https://github.com/gaseous-project/hasheous
- GameTDB: https://www.gametdb.com/ ; Wii downloads: https://www.gametdb.com/Wii/Downloads ; FAQ: https://www.gametdb.com/Main/FAQ
- LaunchBox Games Database: https://gamesdb.launchbox-app.com/ ; Metadata.zip: https://gamesdb.launchbox-app.com/Metadata.zip ; API request: https://feedback.launchbox-app.com/p/public-api-access-for-the-launchbox-games-database-globewithmeridianswrench ; forum on image access: https://forums.launchbox-app.com/topic/54163-is-there-a-public-way-to-get-images-from-the-launchbox-games-database/
- MobyGames API: https://www.mobygames.com/info/api/ ; subscriptions: https://www.mobygames.com/api/subscribe/ ; paid-API discussion: https://github.com/mProjectsCode/obsidian-media-db-plugin/issues/164
- RAWG API docs: https://rawg.io/apidocs

Open-source implementations consulted
- ES-DE: `es-app/src/scrapers/ScreenScraper.{h,cpp}`, `GamesDBJSONScraper.cpp`, `es-core/src/Settings.cpp` — https://gitlab.com/es-de/emulationstation-de
- Skyscraper (Gemba fork): `src/screenscraper.cpp`, `src/igdb.cpp`, `src/thegamesdb.cpp`, `platforms_idmap.csv`, `docs/SCRAPINGMODULES.md`, `docs/CONFIGINI.md` — https://github.com/Gemba/skyscraper
- Batocera EmulationStation: `es-app/src/scrapers/ScreenScraper.{h,cpp}` — https://github.com/batocera-linux/batocera-emulationstation
- RomM: `backend/handler/metadata/{ss,igdb,sgdb,hasheous}_handler.py`, `backend/adapters/services/{screenscraper,igdb,steamgriddb,steamgriddb_types}.py`, `backend/config/__init__.py` — https://github.com/rommapp/romm ; quota issue: https://github.com/rommapp/romm/issues/3978
- RetroPie Skyscraper module: https://github.com/RetroPie/RetroPie-Setup/blob/master/scriptmodules/supplementary/skyscraper.sh
- RetroHub scraping docs: https://retrohub.readthedocs.io/en/latest/user_guide/scraping/index.html
- Pegasus asset docs: https://pegasus-frontend.org/docs/user-guide/meta-assets/ ; Skraper: https://www.skraper.net/
- JellyEmu ScreenScraper credentials issue: https://github.com/Jellyfin-PG/JellyEmu/issues/219
- sselph/scraper OpenVGDB downloader (schema query): https://github.com/sselph/scraper/blob/master/ovgdb-dl/ovgdb-dl.go
