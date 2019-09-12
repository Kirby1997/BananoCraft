# BananoCraft


This plugin is use as is. It's in a working but unfinished state. 
This plugin requires a Banano Node.


Database Setup:  
Set up a free database from mongodb.com (or host your own)  
Create a database called "BananoCraft" (or change the "getDatabase parameter on line 25, 43 and 55 in DB.java")  
Create a collection in the database called "users" (or change the getCollection parameter on line 25, 43 and 55 in DB.java")  
Find your database connection URI and paste it in line 16 in DB.java  

Master wallet setup:  
Set up a Banano Node
Create a wallet on your Banano node and copy the ID.
Put the wallet ID into line 132 in RPC.java  
Put the IP of your node in line 14 of nodeinfo.java  
Create an account in the banano wallet and paste the address into line 86 of EconomyFuncs.java  
  
 Now compile the plugin, place it in your server's "plugin folder", run your server and then shut it down again.  
 Go into the newly created "HelloWorld" folder and edit the config.yml.
 Extraction of parameters was in process. Put the following lines in the file and replace the text were indicated.  
 IP: "INSERT NODE IP HERE"  
 walletID: "INSERT WALLET ID HERE"
 mongoURI: "INSERT MONGODB URI HERE"