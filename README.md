# BananoCraft
# Mojang is currently in the process of updating their TOS. Use of this plugin may be against Minecraft's TOS in the future: https://www.minecraft.net/en-us/article/minecraft-and-nfts

This plugin is use as is. It's in a working but unfinished state. 
This plugin requires a Banano Node (https://github.com/BananoCoin/banano/wiki/Building-a-Bananode-from-sources).

Each player that joins the server receives a Banano address. All transactions are done on the blockchain.
In order for users to buy or sell to the server, a master wallet needs set up. Think of this as a central reserve.  
Run your server once with the plugin installed and shut down the server again.


## Database Setup:  
Set up a free database from mongodb.com ([or host your own](https://docs.mongodb.com/manual/tutorial/install-mongodb-on-ubuntu/))

* Create a database called "BananoCraft" 

`use BananoCraft`

* Create a collection in the database called "users"

`db.createCollection("users")`

* Find your [database connection URI](https://docs.mongodb.com/manual/reference/connection-string/) and put it in the BananoEconomy/config.yml file.


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

Demo video:  https://www.youtube.com/watch?v=KR-cTu4XxLY
  

## Donations:   
* ban_1kirby19w89i35yenyesnz7zqdyguzdb3e819dxrhdegdnsaphzeug39ntxj

* nano_3cejfd9g6x7fbxusojd43mp9ctb6d4s1w67hqph3udtb4kqupxrx5areqswr  

For help, contact Kirby #8061 on Discord. You can find me on the Banano server https://chat.banano.cc
