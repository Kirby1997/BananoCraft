# Release Notes #

## Unreleased ##

- **Light wallet mode** (`walletMode: light`): no Banano node wallet is needed. Keys are derived, blocks built and signed, and proof-of-work generated on the Minecraft server (`workThreads`), then published with `process`, so `IP` may be a public RPC and the seed never leaves the server. Measured proof-of-work cost: 1 CPU thread ~5.7 s per block, 4 threads ~1.5 s (max 3.2 s); a tip is a send plus the recipient's receive; work for the next block is pre-computed. The default stays `walletMode: node`. See "Light wallet mode" in the README.
- Wallet actions in `RPC` (seed import, account creation, send, receive, representative change) now go through a `WalletSigner` (`NodeWalletSigner` = the previous node-wallet code, `LightWalletSigner` = local signing). Public `RPC` methods are unchanged.
- New config keys `walletMode`, `workThreads`, `defaultRepresentative`, `walletIndexScanLimit`.
- New accounts (players and banks, both modes) are created at an explicit derivation index (highest stored + 1, allocated under a lock) which is stored as `walletIndex` (JSON, MongoDB, and an automatically added nullable `walletIndex` column on the MySQL `users` and `banks` tables). Existing records are matched to their index at startup (scan limit `walletIndexScanLimit`); unmatched records are logged at SEVERE and, in light mode, cannot spend.
- An unknown transaction outcome in light mode is reported as "Transaction status unknown - check /history before retrying" rather than as a plain failure.
- Unknown outcomes (light mode) are no longer a failure for other plugins: Vault `depositPlayer`/`withdrawPlayer` and the Vault bank methods answer `SUCCESS` with the message "outcome unknown - reconcile with /history" (a shop that saw a failure would retry or refund and could move the funds twice), and `BananoWalletService.SendResult` gained an `unknown` flag (`success()` is also true, no block hash; existing factories and the 3-argument constructor are unchanged). Each case is logged at `SEVERE` with player, amount and direction. `/tip` to an offline player still saves the offline payment, marked with the unknown note, so the recipient is informed.
- Signed blocks whose outcome is unknown are now persisted in `plugins/BananoEconomy/pending-blocks/` (`player-<index>.json`, `bank-<index>.json`; owner-only, atomic writes) and republished first after a restart.
- Light mode and the main thread: the (slow) auto-receive after a Vault/bank payment is never run synchronously on the primary thread; proof-of-work is precomputed for the master wallet at startup and for a player's account on join. README now states plainly that a Vault payment can still block the main thread for the proof-of-work time when no precomputed work is ready (2 threads: avg 1.7 s, median 1.2 s, max ~6 s; 1 thread: avg 3.7 s) and recommends `workThreads` of 2 or more and node mode for heavy Vault shop traffic.
- Light mode has no node auto-receive, so on join the plugin now pockets a bounded number (5) of the player's pending blocks in the background; comments and README describe the receive behaviour of both modes accurately.
- Multi-server safety: UNIQUE indexes on wallet address and (where set) `walletIndex` for MySQL (`users`, `banks`) and MongoDB (`users`, `banks`), created idempotently at startup (duplicates are listed at `SEVERE` and the plugin continues without that constraint); a duplicate-key error on insert means "index taken" and the next index is tried instead of overwriting anything. README: light mode supports exactly one server per seed; several servers may share a database only in node mode.
- New accounts wait for the startup wallet-index backfill: a new player or bank created before it finishes is told that wallet setup is in progress and to rejoin shortly (new message `join.wallet_setup_pending`) instead of risking an index collision, and skipping already-assigned addresses now continues up to `walletIndexScanLimit` instead of giving up after 10.

- JSON storage: a missing player file no longer creates an empty file, and empty/corrupt player files are treated as "no record" instead of breaking joins.
- JSON storage: all files are written atomically (temp file + rename) and writes to the same file are serialised, so a crash can no longer truncate data.
- JSON storage: offline payments are saved on every change instead of only every 15 minutes; a corrupt offlinepayments.json is moved aside rather than overwritten.
- JSON storage: the duplicate-wallet index is built synchronously at startup, ignores non-player files and is kept up to date on insert/update.
- New `updatePlayerWallet` storage operation (MongoDB, MySQL, JSON) used to replace invalid player wallets after a node migration (#7).
- MongoDB (#13): 5s connect/server-selection timeouts and a startup ping; if MongoDB is unreachable a single clear error is logged and the plugin disables itself instead of spamming the console or issuing new wallets without its records. Read failures are logged instead of thrown; missing `frozen` fields no longer cause errors; saving an unchanged player record is no longer reported as a failure.
- MongoDB driver console spam reduced (#12): the driver logger is set to WARN and the plugin no longer bundles logback (an SLF4J binding) — unused slf4j-api and Apache httpclient dependencies were removed.
- Security: MySQL driver moved from `mysql:mysql-connector-java` 8.0.30 to `com.mysql:mysql-connector-j` 9.7.0.
- Security: `seed-backup.txt` and `secret.key` are created readable by the owner only; the seed is never written to the log. An existing seed backup is never overwritten.
- Security: a `secret.key` of the wrong length is renamed to `secret.key.bad-<timestamp>` instead of being silently replaced, and a seed is never saved to config.yml unencrypted.
- A new wallet seed is only generated when both `walletSeed` and `walletID` are unset.
- Credentials in the node URL are hidden in the startup log.
- Transaction history no longer fails on a missing or short block hash.
- README: documents all four MySQL tables, the `CREATE` privilege needed for auto-creation, and a Troubleshooting section (`enable_control`, node migration).

## 1.1.1-SNAPSHOT ##

- bugfix for unhandled exceptions when calling sendPost() that caused the plugin to crash servers for transactions with a delayed response.