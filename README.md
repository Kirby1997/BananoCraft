# BananoCraft


This plugin is use as is. It's in a working but unfinished state. 
This plugin requires a Banano Node (https://github.com/BananoCoin/banano/wiki/Building-a-Bananode-from-sources).

Each player that joins the server receives a Banano address. All transactions are done on the blockchain.
In order for users to buy or sell to the server, a master wallet needs set up. Think of this as a central reserve.  
Run your server once with the plugin installed and shut down the server again.


Database Setup:  
Set up a free database from mongodb.com (or host your own)  
Create a database called "BananoCraft"  
Create a collection in the database called "users"   
Find your database connection URI and put it in the BananoEconomy/config.yml file.  

Master wallet setup:  
Set up a Banano Node or use the public API (https://nanoo.tools/bananode-api)
Paste the IP in BananoEconomy/config.yml where it asks for an IP.
Get a Banano wallet seed from vault.banano.cc or Kalium and paste it in BananoEconomy/config.yml where it asks for a seed
  

   
Send donations to: ban_1kirby19w89i35yenyesnz7zqdyguzdb3e819dxrhdegdnsaphzeug39ntxj  
For help, DM Kirby #8061 on Discord