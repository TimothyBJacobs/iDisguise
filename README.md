# iDisguise
Plugin for CraftBukkit and Spigot

## Basic information
This plugin allows you to turn into almost every entity that exists in Minecraft.  
More information can be found here: https://www.spigotmc.org/resources/idisguise.5509/

## Maven repository
````
<repository>
  <id>robingrether-repo</id>
  <url>http://repo.robingrether.de/</url>
</repository>
````

If you need the core plugin (API and so):
````
<dependency>
  <groupId>de.robingrether.idisguise</groupId>
  <artifactId>idisguise-core</artifactId>
  <version>5.6.1</version>
</dependency>
````

If you need everything:
````
<dependency>
  <groupId>de.robingrether.idisguise</groupId>
  <artifactId>idisguise-full</artifactId>
  <version>5.6.1</version>
</dependency>
````

## Compiling
In order to compile the whole plugin you have to clone/download this repository and build the project _idisguise-full_ using Maven.  
Run _mvn build package_ and you will find the final jar file under _/idisguise-full/target/idisguise-full-&lt;VERSION&gt;-jar-with-dependencies.jar_.
