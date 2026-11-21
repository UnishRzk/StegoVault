
---
name: steganography-development
description: Implement, debug, and test image steganography features in this Java Spring Boot project, including LSB encoding, decoding, image validation, capacity checks, and API integration.
---

# Steganography Development Workflow

## Before making changes
- Read AGENTS.md and inspect the existing project structure.
- Understand the current implementation before modifying files.
- Identify the relevant controller, service, algorithm, utility, and test classes.
- Avoid duplicating existing functionality.

## Architecture
- Keep HTTP handling in controllers.
- Keep application logic in services.
- Keep LSB encoding and decoding in dedicated algorithm classes.
- Keep cryptography (key derivation, envelope format, AEAD) in the `crypto` package.
- Keep image processing and bit manipulation in utility classes.
- Use dedicated DTOs for API requests and responses.
- Use custom exceptions for expected failure cases.
- Embed encrypted envelopes through the byte-oriented LSB API
  (`LsbEncoder.encodeBytes` / `LsbDecoder.decodeBytes`); never reimplement LSB.

## Algorithm requirements
- Use lossless image formats for embedding and extraction.
- Validate the uploaded image and its capacity before embedding.
- Ensure encoding and decoding use the same bit ordering and traversal rules.
- Define an unambiguous message format, including message length or a reliable end marker.
- Never silently truncate secret messages.
- Handle empty messages, corrupted data, and insufficient capacity.
- Test that extracted messages exactly match the originals.

## Security
- Do not claim LSB steganography is encryption.
- If confidentiality is required, encrypt the message before embedding it.
- Never hardcode encryption keys, passwords, or salts; derive keys with
  PBKDF2WithHmacSHA256 and a fresh random salt per encryption.
- Use AES-GCM with a fresh 12-byte nonce and a 128-bit tag; never reuse a nonce with the
  same key.
- Keep a versioned envelope with explicit magic, version, iteration count, and validated
  length fields; reject malformed envelopes before allocating or deriving keys.
- Validate image upload size and dimensions from headers before pixel decoding or PBKDF2.
- Return the same client-facing 422 error for wrong passwords, authentication failures,
  and tampered ciphertext. Keep malformed envelopes, missing LSB payloads, and valid empty
  plaintext distinct.
- Validate uploaded files and impose reasonable upload-size limits.
- Return meaningful errors without exposing internal stack traces or cryptographic details.

## Testing
- Write unit tests for the algorithm, crypto, and utility classes.
- Test normal messages, empty messages, Unicode, capacity limits, and invalid images.
- Test encode/decode round trips, including encrypted round trips.
- Test wrong passwords, tampered ciphertext/nonce/salt, malformed envelopes, and
  insufficient image capacity.
- Test that auth failures share one generic error and that empty plaintext, missing LSB
  payloads, and malformed envelopes remain distinguishable.
- Run the relevant Maven tests after changes.
- Report any failing tests instead of claiming success.

## Implementation rules
- Prefer simple, readable Java code.
- Keep changes focused on the requested feature.
- Follow existing project conventions.
- Explain significant design decisions and list the files changed.
- Do not add dependencies without a clear reason.
