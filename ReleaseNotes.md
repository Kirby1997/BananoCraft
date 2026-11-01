# Release Notes #

## Unreleased ##

- **Light wallet mode** (`walletMode: light`): no Banano node wallet is needed. Keys are derived, blocks built and signed, and proof-of-work generated on the Minecraft server (`workThreads`), then published with `process`, so `IP` may be a public RPC and the seed never leaves the server. Measured proof-of-work cost: 1 CPU thread ~5.7 s per block, 4 threads ~1.5 s (max 3.2 s); a tip is a send plus the recipient's receive; work for the next block is pre-computed. The default stays `walletMode: node`. See "Light wallet mode" in the README.
- Wallet actions in `RPC` (seed import, account creation, send, receive, representative change) now go through a `WalletSigner` (`NodeWalletSigner` = the previous node-wallet code, `LightWalletSigner` = local signing). Public `RPC` methods are unchanged.
- New config keys `walletMode`, `workThreads`, `defaultRepresentative`, `walletIndexScanLimit`.
- New accounts (players and banks, both modes) are created at an explicit derivation index (highest stored + 1, allocated under a lock) which is stored as `walletIndex` (JSON, MongoDB, and an automatically added nullable `walletIndex` column on the MySQL `users` and `banks` tables). Existing records are matched to their index at startup (scan limit `walletIndexScanLimit`); unmatched records are logged at SEVERE and, in light mode, cannot spend.
- An unknown transaction outcome in light mode is reported as "Transaction status unknown - check /history before retrying" rather than as a plain failure.

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