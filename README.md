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

* Create a database called "BananoCraft" 

`use BananoCraft`

* Create a collection in the database called "users"

`db.createCollection("users")`

* Find your [database connection URI](https://docs.mongodb.com/manual/reference/connection-string/) and put it in the BananoEconomy/config.yml file.

## MySQL
Set up a new MySQL database with a database user that can be used by the plugin.

* Create a database called "BananoCraft"

* Run the following SQL in the BananoCraft schema to create the users table:
 `CREATE TABLE IF NOT EXISTS users (
      playerUUID     VARCHAR(75) NOT NULL UNIQUE,
      name           VARCHAR(50) NOT NULL,
      wallet         VARCHAR(100) NOT NULL,
      frozen         BOOLEAN NOT NULL DEFAULT FALSE,
      PRIMARY KEY (playerUUID)
  )  ENGINE=INNODB;`

* Add the MySQL connection details to the config.yml file:
  - `mysqlServerName: "dbservername"`
  - `mysqlPort: <mysql port number used>`
  - `mysqlDatabaseName: "BananoCraft"`
  - `mysqlUsername: "dbusername"`
  - `mysqlPassword: "dbpassword"`


## Master wallet setup:  
* Set up a Banano Node or use the public API (https://nanoo.tools/bananode-api)
* Get a Banano wallet seed from vault.banano.cc or Kalium


## Installation steps:
* Install [Spigot MC](https://www.spigotmc.org/)
* Install [Vault](https://www.spigotmc.org/resources/vault.34315/) to your Spigot Server.
* Build plugin with Maven Package Build
* Copy `BananoCraft.jar` into your Minecraft server's /plugins folder  
* Run the server once and then shut it down again to generate the plugin config file.

## Configuration steps:

* In the new BananoEconomy folder (created by the previous step) open `config.yml`  
* Add your node IP *with* protocol (e.g.: http://127.0.0.1:7072)
* Add your wallet seed (generated in **Master Wallet Setup** step)
* Add your MongoDB URI (from **Database Setup** step)  
* Ignore the rest unless you know what you are doing. Empty fields will be auto filled.

## Optional (Basic users, ignore me.)
This is necessary when restoring backups etc.
You may set `walletId` to the ID of the wallet created by your `wallet_create` RPC call to specify specific wallet on the node.
Doing so allows you to use previously created wallet.
Of course, the specified node should contain the wallet ID and the wallet has to contain the specified master account.

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
* Required permission: OP or `BananoEconomy.admin`

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
