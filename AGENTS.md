# AGENTS.md

## Project
Spring Boot 4.1.2 / Java 21 image-steganography service (`com.unish:Steganography`)
with a React + Vite frontend in `frontend/`. Core LSB encode/decode, AES-GCM encrypted
payloads, and the REST API are implemented. Base package: `com.unish.steganography`.

## Commands (Windows)
- Run app: `.\mvnw.cmd spring-boot:run`
- All tests: `.\mvnw.cmd test`
- Single test: `.\mvnw.cmd test -Dtest=SteganographyApplicationTests#contextLoads`
- Package: `.\mvnw.cmd package`

Use the wrapper (Maven 3.10.0); Java 21 is required. No system Maven needed.

## Frontend (React + Vite, JavaScript)
- Location: `frontend/`. Run from that directory with npm (Node 20.19+).
- Install: `npm install`
- Dev server: `npm run dev` (defaults to http://localhost:5173)
- Build: `npm run build` (outputs to `frontend/dist/`)
- Preview build: `npm run preview`
- Native `fetch()` only; no Axios and no UI framework. The Vite dev proxy forwards `/api`
  to `http://localhost:8080`, so run the Spring Boot app alongside the dev server.
- Optional `VITE_API_BASE_URL` overrides the API origin for non-proxy deployments; leave
  empty to use same-origin/relative requests.
- Frontend validation mirrors backend limits: PNG/BMP magic bytes, 5MB, 8192 px per side,
  16,777,216 total pixels, password 8–1024 chars. Never log passwords or decoded messages.

## Gotchas
- Boot 4 starter names differ from Boot 3: `spring-boot-starter-webmvc`,
  `spring-boot-starter-webmvc-test`, `spring-boot-starter-validation-test`.
  Match existing `pom.xml` before adding dependencies.
- Read `.opencode/skills/steganography-development/SKILL.md` before
  implementing features; it defines layer separation, LSB requirements,
  security rules, and test expectations.
- No CI, lint, or formatter config; verification is `.\mvnw.cmd test`.
- `HELP.md` is gitignored.
- Boot 4 `@WebMvcTest`/`@AutoConfigureMockMvc` live in
  `org.springframework.boot.webmvc.test.autoconfigure`; `MockMvc` and
  `MockMultipartFile` remain in `spring-test`.
- Spring 7 renamed `HttpStatus.UNPROCESSABLE_ENTITY`/`PAYLOAD_TOO_LARGE` to
  `UNPROCESSABLE_CONTENT`/`CONTENT_TOO_LARGE`; use the new constants.

## REST API
- `POST /api/steganography/encode`: multipart `image` + `message` -> `image/png` attachment.
- `POST /api/steganography/decode`: multipart `image` -> `{"message":"..."}`.
- `POST /api/steganography/encrypted/encode`: multipart `image` + `message` + `password`
  -> `image/png` attachment (`stego-encrypted.png`).
- `POST /api/steganography/encrypted/decode`: multipart `image` + `password`
  -> `{"message":"..."}`.
- Multipart limits: 5MB file / 6MB request (`spring.servlet.multipart.*`); the service
  also rejects uploads over 5MB before decoding.
- Image limits (`util/ImageIoUtils`): max 8192 px per side and 16,777,216 total pixels,
  checked from the image header before pixel decoding or key derivation.
- Errors: 400 invalid image/missing params/invalid password, 422 invalid payload or
  malformed/undecryptable encrypted envelope, 413 capacity/size.
- Wrong password, authentication failures, and tampered ciphertext all return the same
  422 message: `Unable to decrypt payload: incorrect password or corrupted data`.

## Encrypted payload format (`crypto/EncryptedEnvelope`, version 1)
- AES-256-GCM, 12-byte random nonce, 128-bit tag; PBKDF2WithHmacSHA256 key derivation
  with a fresh 16-byte random salt and 600,000 default iterations.
- Envelope: `"SGCE"` magic, version byte, 4-byte iterations, salt length + salt,
  nonce length + nonce, tag length, 4-byte ciphertext length, ciphertext || GCM tag.
- Iterations are range-checked (100k–2M) and all length fields are validated before
  allocation. Password policy: 8–1024 chars, not blank; never hardcoded.
- Envelopes are embedded via `LsbEncoder.encodeBytes`/`LsbDecoder.decodeBytes`, which
  reuse the existing LSB format. String-based LSB methods remain for backward compatibility.

## Core LSB format
- `algorithm/LsbFormat` uses an 8-byte header: `STEG` magic + 4-byte big-endian payload length.
- One bit per RGB channel (alpha untouched), row-major pixels, MSB-first per byte.
- `algorithm/LsbEncoder`/`LsbDecoder` expose byte-oriented methods used by encrypted flows.
- `util/ImageIoUtils` accepts PNG/BMP only; `algorithm/LsbFileCodec` refuses to overwrite the source.

## Repo layout
- This project lives inside the larger college monorepo at `E:\College\BCA`
  (git root). Scope git add/commit to `Sem6/Project II/Steganography/`; the
  parent repo contains many unrelated changed files.
