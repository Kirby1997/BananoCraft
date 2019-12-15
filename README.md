# BananoCraft

Join our server and test it out, address: `banano.party` Both Minecraft Java & Bedrock edition

This plugin works perfectly for implementing [Banano](https://banano.cc) to your Spigot Minecraft 1.15 server.

Requires [Vault](https://www.spigotmc.org/resources/vault.34315/) & it's recommended hosting a [Banano Node](https://github.com/BananoCoin/banano/wiki/Building-a-Bananode-from-sources)

Each player that joins the server receives a Banano address & all transactions are done on the blockchain.

In order for users to buy or sell to the server a master wallet needs to be set up, think of this as a central reserve.  
Run your server once with the plugin installed then shut down the server & finish configuration.

# Installation

## Database Setup  
Fire up a free database at [mongodb.com](https://mongodb.com) (or host your own)  

Create a database called `BananoCraft`  

Create a collection in the database called `users`   

Find your database connection URI and put it in the `BananoEconomy/config.yml` file.  

## Master wallet setup  
Set up a [Banano Node](https://github.com/BananoCoin/banano/wiki/Building-a-Bananode-from-sources) or use [the public API](https://nanoo.tools/bananode-api)

Paste the IP in `BananoEconomy/config.yml` where it asks for an IP. `http://[::1]:7076/` if you're hosting the node on the same server as the Minecraft server or `https://api-beta.banano.cc:443/` if you're using the public API

Get a Banano wallet seed from [vault.banano.cc](https://vault.banano.cc) and paste it in `BananoEconomy/config.yml` where it asks for a seed
 
## Donations & Support
Send Banano donations to Kirby: `ban_1kirby19w89i35yenyesnz7zqdyguzdb3e819dxrhdegdnsaphzeug39ntxj`  

Send Litcoin donations to ez: `lit_3rsye7goqc9rqzz4igi4t6bdahoznye9dctjkuprobixnhkntxx387d4999z`

For support, spam `Kirby#8061` on Discord