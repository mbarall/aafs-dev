package scratch.aftershockStatistics.aafs;

import java.util.List;

import scratch.aftershockStatistics.aafs.entity.PendingTask;
import scratch.aftershockStatistics.aafs.entity.LogEntry;
import scratch.aftershockStatistics.aafs.entity.CatalogSnapshot;
import scratch.aftershockStatistics.aafs.entity.TimelineEntry;

import scratch.aftershockStatistics.util.MarshalReader;
import scratch.aftershockStatistics.util.MarshalWriter;
import scratch.aftershockStatistics.util.SimpleUtils;

import scratch.aftershockStatistics.CompactEqkRupList;

/**
 * Program version information.
 * Author: Michael Barall 08/16/2018.
 */
public class VersionInfo  {

	// Program name.

	public static final String program_name = "USGS Aftershock Forecasting System";

	// Program version.

	public static final String program_version = "Version 0.01.1004 Alpha (08/28/2018)";

	// Program sponsor.

	public static final String program_sponsor = "U.S. Geological Survey, Earthquake Science Center";

	// Major version.

	public static final int major_version = 0;

	// Minor version.

	public static final int minor_version = 1;

	// Build.

	public static final int build = 1004;




	// Get the title, as multiple lines but no final newline.

	public static String get_title () {
		return program_name + "\n"
				+ program_version + "\n"
				+ program_sponsor;
	}


	// Get a one-line name and version

	public static String get_one_line_version () {
		return program_name + ", " + program_version;
	}

}
