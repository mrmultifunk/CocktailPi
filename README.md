# CocktailMaker

The “Cocktail-Maker” is not only a piece of software. 
It’s a cocktail-mixing-machine, that works with a Raspberry-Pi, 
that controls multiple pumps, which have different ingredients assigned. 
The Cocktail-Maker provides a UI, that can be accessed via web browser. 
Admins can create other users and assign them to multiple roles with 
different permissions. Users can create own cocktails and if the 
Cocktail-Maker has all the needed ingredients, they can order them. 
Cocktails can be categorized and shared with other users.

New recipes can be created in the UI. The user can add ingredients to 
different production steps. Ingredients that are in the same 
production step get bottled at the same time. 
The order in which ingredients get bottled can be changed via drag & 
drop.

# Demo
A demo can be found here: https://cocktailmaker-demo.liggesmeyer.net/
User: Admin  
Password: 123456  

# Build the hardware

In order to use this machine in a useful way, you need to build your own hardware.
In simple words produces the Cocktail-Maker-Software different recipes by 
controlling a relay board, that opens or closes the electronic circuit 
for multiple pumps, which pump the different liquids into the glass.
The user can add new pumps in the webinterface, where he has to specify a GPIO-Pin
for every pump and how long that pumps needs to pump exactly one centiliter in 
milliseconds. You can build your machine as you like. The only important thing
is that you need to be able to apply that concept to your setup.
### This is an example setup:
![Blueprint](./documentation/img/blueprint.png "Blueprint")
### ...or with voltage director relays, that allow reverse pumping:
![Blueprint with voltage direcor](./documentation/img/blueprint-vd.png "Blueprint with voltage direcor")

### This is the hardware that I've used for my machine:
You can find the list and an installation tutorial on my website: https://alexander.liggesmeyer.net/portfolio/cocktailmaker/

# Installation

An installation tutorial can be found here: https://github.com/alex9849/pi-cocktail-maker/wiki/Installation
