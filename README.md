<img title="Eversity Logo" src="./assets/eversity_logo.png" alt="Eversity Logo" width="367" data-align="center">

# Eversity Server (Eversity Core)

Core of Eversity project. Developed for self-hosting at school site. Uses "Schools.by" as authentication service and data source, so it is required that your school is connected to "Schools.by" system (maybe, enrollie will develop it's own version of "Schools.by", who knows ;) )

# Installation

### Requirements:

- Windows 7 or later (or any Windows, that supports JRE (Java) 11 or later), or any Linux distribution, that supports JRE 11 (developer uses Ubuntu Server 21.04 for testing)

- At least 1.5 GB of RAM on server

- JRE 11 (Java 11)

- School, connected to "Schools.by" system

- An Internet connection (because of "Schools.by" integration)

- School-wide Local Area Network

*Author of README did all of those steps on Ubuntu Server 21.04 machine*

1. ##### Install Java Runtime.
   Refer to the Internet on how to install a Java Runtime. Remember, you need JRE 11 or later.

2. ##### Install PostgreSQL
   
   Please, refer to the Internet on how to install PostgreSQL on your OS. Then create user (in example - "eversity"). As we are in simple guide, let's create role named "eversity" with password "very-strong-password" *(please, **do not** use this kind of passwords in production)*. Then create database with any name (for convenience you can name it "eversity") That's all we have to do with Postgres.

3. ##### Download Eversity Server
   
   If you are reading this on GitHub, there is "Packages" section on your right (or left, if you are Arabian) with "by.enrollie.eversity..." in it. Click it, then download "eversity-server-...-uberJar.jar" (it usually comes first). Download it to secure directory, as in the same directory database access data will be stored. (tips on security: do not set guessable passwords, do not let any unauthorized persons to interact with those folders, do not share these folders). Rename downloaded file to "**Eversity-Server.jar**" (for convenience in step 5) 

4. ##### Setup Eversity Server
   
   Go to repository root folder and download file named "**school_naming.properties.template**" to same directory, where Eversity Server JAR is contained. Then go to repo's directory "src/main/resources" and download file named "**application.conf**" to same directory, where Eversity Server JAR is contained.
   
   Now,  rename "school_naming.properties.template" to "**school_naming.properties**" (remove ".template"). Open it and fill in all of school names. Now, open "application.conf" and fill in all of data here (don't worry, it's all commented).
   
    _Note about Telegram bot token: please, search for internet on how-to create it_
   
   _Note about school website: it is also used for Telegram bot, but, since most likely your server is behind NAT, you can fill it with any website (preferably, related to your school)_

5. ##### Run Eversity Server
   
   Create file "run.bat" (or similar) in same directory, as Eversity Server JAR. Put in the following text: 
   
   ```bash
   java -jar Eversity-Server.jar -config=application.conf
   ```
   
   Now, run file "run.bat". If last output line says something like "Responding at http://0.0.0.0:8080" then congratulations! You've just set up Eversity Server!

6. ##### Use it!
   
   Set up any client you want (refer to one's Installation Guides) and use Eversity. Official client is Eversity Client (at the time of writing, still under development)

# Building

Clone repository using

```bash
git clone 'https://github.com/enrollie/Eversity-Server.git'
```

then run 

```bash
./gradlew build
```

If you want to build uber jar (which is main way of distribution), run 

```bash
./gradlew shadowJar
```

# License
```
Eversity Server is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Eversity Server is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with Eversity Server.  If not, see <https://www.gnu.org/licenses/>
```
# Authors

Pavel Matusevich
