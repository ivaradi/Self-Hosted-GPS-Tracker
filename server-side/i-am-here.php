<!DOCTYPE html>
<html lang="en">
<head>
<meta charset=utf-8>
<title>I am here</title>

<!-- Google Maps version -->
<script type="text/javascript" src="http://maps.google.com/maps/api/js?sensor=false"></script>

<!-- OpenStreetMap + Leaflet.js version -->
<link rel="stylesheet" href="http://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.3/leaflet.css" />
<script src="http://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.3/leaflet.js"></script>

</head>
<body>

<?php
$date = $lat = $lon = '';
$date_lat_lon = rtrim(file_get_contents("/tmp/gps-position.txt"));
if ($date_lat_lon) {
	list($date, $lat, $lon) = explode("_", $date_lat_lon);
}
?>

<h1>I was here on <span id="date"><?php echo $date ? $date : "…" ?></span></h1>
<p>(last known position where I had a GPS signal, a network connection, and some battery power)</p>

<h2>Google Maps version</h2>
<div id="googlemap" style="width: 800px; height: 600px">
	<div id="interlude" style="text-align: center; line-height: 600px; font-weight: bold; border: 1px dotted grey; background-color: #eee;">
		Map currently unavailable.
	</div>
</div>

<h2>OpenStreetMap version</h2>
<div id="openstreetmap" style="width: 800px; height: 600px">
    <div id="interlude" style="text-align: center; line-height: 600px; font-weight: bold; border: 1px dotted grey; background-color: #eee;">
        Map currently unavailable.
    </div>
</div>

<script>
var gmap, gmarker;
var osmap, osmarker;

<?php if ($lat && $lon): ?>
createGMap(<?php echo $lat.",".$lon ?>);
<?php endif; ?>

function createGMap(lat, lon) {
	var latlng = new google.maps.LatLng(lat, lon);
	var myOptions = {
	    zoom: 12,
	    center: latlng,
	    mapTypeControl: false,
	    navigationControlOptions: {style: google.maps.NavigationControlStyle.SMALL},
	    mapTypeId: google.maps.MapTypeId.ROADMAP
	};
	gmap = new google.maps.Map(document.getElementById("googlemap"), myOptions);
	gmarker = new google.maps.Marker({
	      position: latlng, 
	      map: gmap, 
	      title:"I'm here"
	});
	google.maps.event.addListener(gmarker, "click", function(e) {
		alert("GPS coordinates:\nLatitude: " + gmarker.getPosition().lat() + "\nLongitude: " + gmarker.getPosition().lng());
	});
}

function updateGMap(dte, lat, lon) {
	var latlng = new google.maps.LatLng(lat, lon);
	gmarker.setPosition(latlng);
	gmap.panTo(latlng);
	document.querySelector("#date").innerHTML = dte;
}

doRefresh();
function doRefresh() {
	var xhr; 
	try {
		xhr = new XMLHttpRequest();
	} catch (e) {
		xhr = false;
	}

	xhr.onreadystatechange  = function() { 
		if (xhr.readyState  == 4) {
			if (xhr.status  == 200) {
				dte = xhr.responseText.split('_')[0];
				lat = xhr.responseText.split('_')[1];
				lon = xhr.responseText.split('_')[2];
				if (dte && lat && lon) {
					if (!gmap) {
						createGMap(lat, lon);
					} else {
						updateGMap(dte, lat, lon);
					}
				}
			}
		}
	};
	xhr.open("GET", "i-am-here-position?" + Math.random(),  true); 
	xhr.send(null);
	setTimeout('doRefresh()', 30000);
}

</script>

