package com.muksihs.farhorizons;

public class CreateMapShellScript {
	public static final String script="#!/bin/bash\n" + 
			"FH_DIR=\"$(dirname \"$0\")\"\n" +
			"echo \"GAME DIR: $FH_DIR\"\n" +
			"BIN_DIR=$FH_DIR/bin\n" + 
			"echo \"BIN DIR: $BIN_DIR\"\n" +
			"LISTGALAXY=$BIN_DIR/ListGalaxy\n" + 
			"STARMAP=$BIN_DIR/PrintMap\n" + 
			"PS2PDF=/usr/bin/ps2pdf\n" + 
			"#############3\n" + 
			"cd \"$(dirname \"$0\")\"\n" + 
			"$LISTGALAXY -p | awk 'BEGIN { OFS = \", \"} {print $3,$6,$9,\"unnamed\",$13\".\"}' > /tmp/fh.map.raw.$$\n" + 
			"num=`wc -l /tmp/fh.map.raw.$$ | awk '{print $1}'` \n" + 
			"head -n $(($num-4)) /tmp/fh.map.raw.$$ > /tmp/fh.map.$$\n" + 
			"cd /tmp\n" + 
			"\n" + 
			"#3d map\n" + 
			"$STARMAP -t /tmp/fh.map.$$\n" + 
			"$PS2PDF -dAutoRotatePages=/None /tmp/fh.map.$$.ps $FH_DIR/galaxy_map_3d.pdf\n" + 
			"\n" + 
			"\n" + 
			"$STARMAP /tmp/fh.map.$$\n" + 
			"$PS2PDF -dAutoRotatePages=/None /tmp/fh.map.$$.ps $FH_DIR/galaxy_map.pdf\n" + 
			"\n" + 
			"rm /tmp/fh.map.*\n" + 
			"\n" + 
			"cd \"$(dirname \"$0\")\"\n" + 
			"mkdir maps\n" +
			"gm convert -rotate 90 -density 144 galaxy_map.pdf[0] maps/galaxy_map-1.png\n" +
			"gm convert -rotate 90 -density 144 galaxy_map.pdf[1] maps/galaxy_map-2.png\n" +
			"gm convert -rotate 90 -density 144 galaxy_map_3d.pdf[0] maps/galaxy_map_3d-1.png\n" +
			"gm convert -rotate 90 -density 144 galaxy_map_3d.pdf[1] maps/galaxy_map_3d-2.png\n" +
			"\n";
}
