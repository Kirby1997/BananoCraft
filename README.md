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
Get a Banano wallet seed from vault.banano.cc or Kalium


Installation steps:  
Build plugin with Maven Package Build
Copy BananoCraft.jar into your Minecraft server's /plugins folder  
Run the server once and then shut it down again to generate the plugin config file.  
In the new BananoEconomy folder open config.yml  
Add your node IP  
Add your wallet seed
Add your MongoDB URI  
Ignore the rest unless you know what you are doing. Empty fields will be auto filled.  

Demo video:  https://www.youtube.com/watch?v=KR-cTu4XxLY
  

   
Donations:   ban_1kirby19w89i35yenyesnz7zqdyguzdb3e819dxrhdegdnsaphzeug39ntxj  
nano_3cejfd9g6x7fbxusojd43mp9ctb6d4s1w67hqph3udtb4kqupxrx5areqswr  
For help, DM Kirby #8061 on Discord
