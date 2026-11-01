# BananoCraft
# Mojang is currently in the process of updating their TOS. Use of this plugin may be against Minecraft's TOS in the future: https://www.minecraft.net/en-us/article/minecraft-and-nfts

This plugin is use as is. It's in a working but unfinished state. 
This plugin requires a Banano Node (https://github.com/BananoCoin/banano/wiki/Building-a-Bananode-from-sources).

Each player that joins the server receives a Banano address. All transactions are done on the blockchain.
In order for users to buy or sell to the server, a master wallet needs set up. Think of this as a central reserve.  
Run your server once with the plugin installed and shut down the server again.


## Database Setup:  

## NoDB

If you don't have access to an external MongoDB or MySQL database server, the `config.yml`
file can exclude both MongoDB and MySQL settings and JSON files will be used and stored
within the plugin's data folder.

## MongoDB
Set up a free database from mongodb.com ([or host your own](https://docs.mongodb.com/manual/tutorial/install-mongodb-on-ubuntu/))

* Find your [database connection URI](https://docs.mongodb.com/manual/reference/connection-string/) and put it in the BananoEconomy/config.yml file as `mongoURI`.
* The plugin uses a database called `BananoCraft` and creates its collections
  (`users`, `offlinepayments`, `banks`, `bank_members`) automatically on first use, so
  nothing needs to be created by hand. The database user needs read/write access to it.
* If MongoDB cannot be reached at startup (5 second timeout), the plugin logs a single
  SEVERE error and **disables itself** rather than running without its player records.
  Fix the connection and restart the server.

## MySQL
Set up a new MySQL database with a database user that can be used by the plugin.

* Create a database called "BananoCraft".

* The plugin creates all of its tables automatically on startup (`CREATE TABLE IF NOT EXISTS`),
  so the database user needs the `CREATE` privilege on the schema in addition to
  `SELECT`, `INSERT`, `UPDATE` and `DELETE` (and `ALTER` for the `walletIndex` column and the unique
  indexes, see **Several servers and the database**). If you prefer not to grant `CREATE`, run the
  following SQL in the BananoCraft schema yourself before the first start:

```sql
CREATE TABLE IF NOT EXISTS users (
    playerUUID  VARCHAR(75)  NOT NULL UNIQUE,
    name        VARCHAR(50)  NOT NULL,
    wallet      VARCHAR(100) NOT NULL,
    frozen      BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (playerUUID)
) ENGINE=INNODB;

CREATE TABLE IF NOT EXISTS offlinepayments (
    id             INT          NOT NULL AUTO_INCREMENT,
    playerUUID     VARCHAR(75)  NOT NULL,
    fromplayername VARCHAR(50)  NOT NULL,
    amount         DOUBLE       NOT NULL,
    blockhash      VARCHAR(250) NOT NULL,
    message        VARCHAR(250) NOT NULL,
    transdate      TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
) ENGINE=INNODB;

CREATE TABLE IF NOT EXISTS banks (
    bank_name   VARCHAR(100) NOT NULL,
    address     VARCHAR(100) NOT NULL,
    owner_uuid  VARCHAR(75)  NOT NULL,
    created_at  BIGINT       NOT NULL,
    PRIMARY KEY (bank_name)
) ENGINE=INNODB;

CREATE TABLE IF NOT EXISTS bank_members (
    bank_name   VARCHAR(100) NOT NULL,
    player_uuid VARCHAR(75)  NOT NULL,
    PRIMARY KEY (bank_name, player_uuid)
) ENGINE=INNODB;
```

* Add the MySQL connection details to the config.yml file:
  - `mysqlServerName: "dbservername"`
  - `mysqlPort: <mysql port number used>`
  - `mysqlDatabaseName: "BananoCraft"`
  - `mysqlUsername: "dbusername"`
  - `mysqlPassword: "dbpassword"`


## Master wallet setup:  
* Set up **your own** Banano Node with `enable_control = true`. Do **not** point the plugin at a
  public or third-party RPC API: if the configured wallet is missing, the plugin sends the wallet
  seed to the node (`wallet_create`), and that seed controls every player's funds. The seed is only
  ever sent to an `https://` node, or to an `http://` node on loopback or a private network.
* Get a Banano wallet seed from vault.banano.cc or Kalium


## Installation steps:
* Install [Spigot MC](https://www.spigotmc.org/)
* Install [Vault](https://www.spigotmc.org/resources/vault.34315/) to your Spigot Server.
* Build plugin with Maven Package Build
* Copy `BananoCraft.jar` into your Minecraft server's /plugins folder  
* Run the server once and then shut it down again to generate the plugin config file.

## Configuration steps:

* In the new BananoEconomy folder (created by the previous step) open `config.yml`  
* Add your node IP *with* protocol (e.g.: http://127.0.0.1:7072). To run without your own node, see
  **Light wallet mode** below.
* Add your wallet seed (generated in **Master Wallet Setup** step)
* Add your MongoDB URI (from **Database Setup** step)  
* Ignore the rest unless you know what you are doing. Empty fields will be auto filled.

## Optional (Basic users, ignore me.)
This is necessary when restoring backups etc.
You may set `walletId` to the ID of the wallet created by your `wallet_create` RPC call to specify specific wallet on the node.
Doing so allows you to use previously created wallet.
Of course, the specified node should contain the wallet ID and the wallet has to contain the specified master account.

If `walletSeed` is empty but `walletID` is set, the plugin will **not** generate a new seed
(it would not match the existing node wallet); set `walletSeed` to that wallet's seed instead.
A new seed is only generated on a fresh install where both are unset. The generated seed is
written to `seed-backup.txt` (readable only by the server's OS user) — copy it somewhere safe
offline and delete the file. Keep `secret.key` together with `config.yml` in your backups: the
seed in `config.yml` is encrypted with it.

## Light wallet mode (no node required)

By default (`walletMode: node`) BananoCraft imports your wallet seed into **your own** Banano node and
asks the node to sign every transaction. With `walletMode: light` no node wallet is involved at all:

* **How it works.** The plugin derives the account keys from your seed on the Minecraft server, builds
  and signs each block there, generates the proof-of-work there (CPU, low priority, on background
  threads - but see **Proof-of-work and the main thread** below), and only publishes the finished,
  signed block with the standard `process` RPC. Balances, history, receivable blocks and representatives are read with ordinary read-only RPC calls.
* **The seed never leaves the server.** Nothing in light mode ever sends the seed (or any private key)
  over the network. `IP` can therefore be **any** Banano RPC endpoint, including a public one: it only
  ever sees public information and signed blocks. It does not need `enable_control`, and the plugin
  never calls `wallet_create` in light mode. Pick an endpoint that allows `account_info`, `receivable`,
  `account_history`, `representatives_online` and `process`.
* **No balance cache.** Every send, receive and representative change reads the account's current state
  (`account_info`) under a per-account lock immediately before building the block, and never builds two
  blocks on the same frontier. If the network answer to `process` is lost, the plugin re-checks the
  account: if the block landed it counts as success, if it did not it is re-sent once, and if the outcome
  still cannot be established the player is told *"Transaction status unknown - check /history before
  retrying"* (it is deliberately not reported as a plain failure, because the funds may have moved).
  The unsettled block is also saved in `plugins/BananoEconomy/pending-blocks/` (`player-<index>.json`,
  `bank-<index>.json`, owner-only, written atomically) and republished first, even after a restart, before
  anything else is built on that account, so it can never fork.
* **Unknown outcomes and other plugins.** For **Vault** callers (shops etc.), bank accounts and the
  `BananoWalletService` API an unknown outcome is *not* reported as a failure - a shop that sees a failure
  would retry or refund and could move the funds twice. Vault and the bank layer answer `SUCCESS` with the
  message *"outcome unknown - reconcile with /history"*; `BananoWalletService.SendResult` has
  `success() == true` and `unknown() == true` (with a note in `userError()`, and no block hash). The server
  log gets a `SEVERE` line with player, amount and direction so an operator can reconcile it.
* **No automatic receive on the node.** In node mode your node pockets incoming transfers itself; in light
  mode nothing else does, so the plugin receives for you: right after a payment it pockets the transfer,
  and when a player joins (on a background thread, best-effort, at most 5 blocks per join) it pockets
  whatever is pending for them. Deposits sent from outside while the player stays online are pocketed by
  `/receive` (or at their next join); the websocket only notifies.
* **One server per seed.** Light mode supports **exactly one server per wallet seed**. Two servers signing
  for the same accounts would each build their own next block on the same frontier and fork the account
  (and the per-account lock that prevents this only exists inside one process). Several servers may share
  one database **only in node mode**, where the node serialises the signing.

### Config keys

```yaml
walletMode: light           # node (default) | light
workThreads: 2              # light mode: CPU threads for proof-of-work (default 2)
defaultRepresentative: ""   # light mode: representative for new accounts; falls back to the first
                            # `representatives` entry, then to the master wallet itself
walletIndexScanLimit: 10000 # both modes: highest index tried when matching old accounts (see below)
IP: "https://your-rpc-endpoint"
walletSeed: ...             # same seed, same encryption-at-rest as before
```

### Proof-of-work cost

Every block needs a proof-of-work. Measured in the development container (Banano difficulty, plain
CPU, one block each):

| `workThreads` | average per block | median | worst case seen |
|---|---|---|---|
| 1 | 3.7 s | - | - |
| 2 | 1.7 s | 1.2 s | ~6 s |

A tip is **two** blocks: the sender's send and the recipient's receive, so expect several seconds of CPU
work per tip (the recipient's receive happens in the background) and plan CPU accordingly (work is done by
background daemon threads at minimum priority).
To hide most of that latency the plugin **pre-computes work** for an account's next block right after each
successful block, for the master wallet at startup and for a player's account when they join; the cached
work is thrown away if the account's frontier moves before it is used. A busy server should raise
`workThreads` (up to the number of spare cores).

### Proof-of-work and the main thread

Proof-of-work is always computed on background threads, never on a plugin thread you control - but Vault's
economy API is **synchronous**: when another plugin (a shop, a quest plugin) calls `depositPlayer` /
`withdrawPlayer` on the server's main thread, the call has to return the result, so in light mode **a
payment can block the main thread for the proof-of-work time whenever no pre-computed work is ready**
(typically the first payment from an account, or two payments in quick succession). Expect roughly the
numbers above: with `workThreads: 2` an average of 1.7 s (median 1.2 s, worst case about 6 s); with one
thread an average of 3.7 s. The slow, non-essential part is moved off the main thread (the recipient's
receive is always scheduled in the background there), and commands (`/tip`, `/withdraw`, ...) always run
off the main thread.

* Use `workThreads` of **2 or more** (up to the number of spare cores).
* For servers with **heavy Vault shop traffic, use node mode** (`walletMode: node`), where the node
  generates the work and signing is quick.

### Account indices and the startup backfill

Every player account is the seed's account at some index. From this version on, new accounts (in **both**
modes) are created at an explicit index, `highest stored index + 1` (index 0 is the master wallet; the
first player gets 1), allocated under a lock so two simultaneous joins can never share an index, and the
index is stored with the player (`walletIndex`: a field in the JSON player file and the MongoDB document,
and a nullable `walletIndex INT` column that is added automatically to the MySQL `users` and `banks`
tables). Banks work the same way against the bank seed.

Accounts created by older versions have no stored index. At startup (both modes, on a background thread)
the plugin derives the addresses at indices `0..walletIndexScanLimit` from your seed(s), matches them to
those accounts and stores the index. Anything it cannot match is listed in a single `SEVERE` log line. In
light mode such an account **cannot send, receive or change representative** (the player gets a clear
error, and is never silently given a new wallet) until it is matched; raise `walletIndexScanLimit` and
restart. The scan runs in the background right after startup, so a legacy player who spends in the very
first seconds after a restart may briefly get the same error. Until the scan has finished the plugin does
**not** hand out new indices (a new index could otherwise collide with a not-yet-matched legacy account):
a brand-new player (or a new bank) joining in that window is told that wallet setup is in progress and to
rejoin shortly, and existing players are not affected. When many addresses are already taken (for example
by unmatched legacy accounts) the plugin keeps looking for a free index, up to `walletIndexScanLimit`.

### Several servers and the database

* The MySQL `users.wallet`, `users.walletIndex`, `banks.address` and `banks.walletIndex` columns and the
  MongoDB `users.wallet` / `users.walletIndex` / `banks.address` / `banks.walletIndex` fields get
  **UNIQUE indexes** (`uq_users_wallet`, `uq_users_walletIndex`, `uq_banks_address`, `uq_banks_walletIndex`;
  `walletIndex` may stay empty for legacy accounts) created idempotently at startup, so two servers (or a
  bug) can never store the same address or index twice. If a record cannot be inserted because the index or
  address is already taken, the plugin tries the next index instead of overwriting anything. If an index
  cannot be created because the data already contains duplicates, the duplicates are listed in a `SEVERE`
  log line and the plugin keeps running without that constraint - fix the duplicates and restart. The
  database user needs the `ALTER`/`INDEX` privilege for this on MySQL. JSON storage is single-process and
  has no such constraint.
* Light mode: **one server per seed** (see above). Multiple servers may share a database only in node mode.


### Switching modes

* **node to light:** set `walletMode: light` (and `IP` if you want a public RPC) and restart. Your seed,
  master wallet and every player's address are unchanged, so no funds move. Leave `walletID` /
  `bankWalletID` as they are; light mode ignores them and does not overwrite them. Check the log for the
  backfill summary.
* **light to node:** set `walletMode: node` and restart. The plugin uses the stored `walletID`; if the
  node does not have that wallet it imports your seed into **your own** node as before (never over a
  public plain-http connection).
* If light mode starts without a usable seed (missing, undecryptable `ENC:` value, or not 64 hex
  characters) it logs one `SEVERE` message and disables itself rather than touching any wallet.

## Troubleshooting

* **Wallet / send RPC calls fail with "RPC control is disabled"** (issue #8): BananoCraft uses
  wallet RPC actions, so the node must have `enable_control = true` in the `[rpc]` section of
  its `config-rpc.toml` (or `rpc.enable_control: true` in older `config.json` files). Only
  expose such a node's RPC port to the Minecraft server, never to the internet.
* **Moving to a new node** (issue #7): re-create the wallet on the new node from your seed
  (`wallet_create` with the hex seed) and update `walletID` (see the seed backup file for
  steps). Player accounts are derived from the seed, so they come back too. If a player's stored
  wallet address is not valid on the new node's wallet, a new wallet is created for that player
  the next time they join and their record is updated — any balance on the old address must be
  recovered from the old seed/node.
* **MongoDB driver spams the console**: the driver's own logging is lowered to WARN at startup.
  If it is still noisy on your server, set the `org.mongodb.driver` logger to `WARN` in your
  server's Log4j2 configuration.

## Representatives

By default, `/representative set random` (and any internal automatic representative change,
e.g. from a plugin verifying wallet ownership) picks from whatever the node currently reports
as online. To pin a specific set of known-good representatives instead — recommended, since this
affects Nano/Banano network decentralization — list them in `config.yml`:

```yaml
representatives:
  - "ban_1representativeaddress..."
  - "ban_1anotherrepresentativeaddress..."
```

Leave the list empty (the default) to always fall back to the node's online list.

## Cross-Plugin API (for plugin developers)

BananoCraft exposes a small, dependency-free `BananoWalletService` API via Bukkit's
`ServicesManager`, so other plugins can look up a player's wallet address/balance, send BAN on
their behalf, and manage their representative — without needing an online `Player` object or
BananoCraft's full dependency tree. Depend on the slim `api` classifier artifact rather than the
full plugin jar (mirrors how this project itself depends on `VaultAPI` rather than all of Vault):

```xml
<dependency>
    <groupId>Banano.BananoMinecraft</groupId>
    <artifactId>BananoEconomy</artifactId>
    <version>1.2.6-SNAPSHOT</version>
    <classifier>api</classifier>
    <scope>provided</scope>
</dependency>
```

```java
RegisteredServiceProvider<BananoWalletService> registration =
        Bukkit.getServicesManager().getRegistration(BananoWalletService.class);

if (registration != null) {
    BananoWalletService wallet = registration.getProvider();
    String address = wallet.getAddress(playerId);
    // ...
}
```

Demo video:  https://www.youtube.com/watch?v=KR-cTu4XxLY
  
## Player Commands

`/balance`

Provides the player with their in-game wallet balance.

`/deposit`

Provides the player with their in-game wallet address that can be
copied to the clipboard, or opened in the account explorer.

`/deposit server`

Provides the player with the server wallet address.

`/tip [{amount}|all] [playername]`

Enables the player to send a specified amount, or their whole wallet 
balance to another player.

`/withdraw [{amount}|all] [ban_walletaddress]`

Enables the player to withdraw a specified amount, or their whole wallet
balance to another wallet address.

`/representative set [ban_repaddress|random]` (alias `/rep set`)

Sets the player's in-game wallet representative to a specific address, or
to one chosen randomly from the pool configured in `representatives` in
`config.yml` (or, if that list is empty, from whatever the node currently
reports as online).

`/representative get` (alias `/rep get`)

Shows the player's currently set representative.

## Admin Commands

`/bc setnode [node address]`

Enables setting the node address without restarting the server.

* The new node address must follow the format `http://[url/ip address]:[port]/`
* **Server console only.** If the master wallet is missing on the new node, the wallet seed is sent
  to it, so this cannot be run by in-game players, even ops.

`/bc account [account|block] [set|view] [set:address]`

When using the set parameter with a URL parameter following this 
command enables setting the Account/Block explorer URLs. The view
parameter will return the currently set URL.

`/bc [freeze|unfreeze] [playername1] [playername2] [etc]`

Enables freezing/unfreezing player's wallets. The player will not be 
able to make transactions using their in-game wallet when frozen.

`/bc serverwallet balance`

Returns the balance of the server wallet.

`/bc serverwallet deposit`

Returns a clickable/copyable format of the server wallet address for 
ease of topping up the server wallet.

`/bc serverwallet tip [{amount}|all] [playername]`

Enables tipping a player the specified amount (or whole server balance) 
from the server wallet.

`/bc serverwallet withdraw [{amount}|all] [ban_walletaddress]`

Enables withdrawing the specified amount (or whole server balance) to the 
specified wallet address.

## Donations:   
* ban_1kirby19w89i35yenyesnz7zqdyguzdb3e819dxrhdegdnsaphzeug39ntxj

* nano_3cejfd9g6x7fbxusojd43mp9ctb6d4s1w67hqph3udtb4kqupxrx5areqswr  

For help, contact Kirby #8061 on Discord. You can find me on the Banano server https://chat.banano.cc
