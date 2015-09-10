self-hosted-gps-tracker
=======================

This small Android app sends your GPS coordinates to *your* server. It's your data, do what *you* want with it.

This is how it works :

![how it works.png](how-it-works.png)

Compiled app is available on https://play.google.com/store/apps/details?id=fr.herverenault.selfhostedgpstracker and on https://f-droid.org/repository/browse/?fdid=fr.herverenault.selfhostedgpstracker

You need a self-hosted web server on which you're allowed to install this kind of script which records GPS coordinates (latitude, longitude) : https://github.com/herverenault/Self-Hosted-GPS-Tracker/blob/master/server-side/gps.php

For example, you can put these PHP scripts on your server in order to show your family or friend where you are on a Google map : https://github.com/herverenault/Self-Hosted-GPS-Tracker/tree/master/server-side
It may be useful, for instance, if you're alone doing sport in an isolated area : in case of emergency (injury or failure), your family may locate you easily on the map. Provided you have GPS signal and data connectivity (4G, H+, 3G, EDGE...).

This app is NOT meant to be a stealth tracker, or to be forced upon a user. So, please don't ask me how to hide it from the user, or how to prevent the user from closing the app.

I put a demo on http://herverenault.fr/self-hosted-gps-tracker/demo/i-am-here
To see your position on this page, enter http://herverenault.fr/gps in the app on your phone (or tablet). It's an anonymous URL, so if you're not the only one doing it, you may see the location of another anonymous user.

Side-note: There are plenty of apps which do the same, for example Open GPS Tracker. But I wanted an app
* which sends my position to my server, and only my server
* which does not download any map on my phone and thus does not go over my 3G plan
* the most simple ever : just enter URL, enable 3G and GPS and go !

And then, I wanted to do it just for fun. This is not a product, I don't sell it, there's no ad. It's only code sharing. Enjoy.
