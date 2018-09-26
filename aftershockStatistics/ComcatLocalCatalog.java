package scratch.aftershockStatistics;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Comparator;

import java.io.IOException;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;

import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.impl.StringParameter;

import scratch.aftershockStatistics.util.SimpleUtils;
import scratch.aftershockStatistics.util.SphRegionWorld;
import scratch.aftershockStatistics.util.SphLatLon;
//import scratch.aftershockStatistics.util.SphRegion;
import scratch.aftershockStatistics.util.SphRegionCircle;

/**
 * A local earthquake catalog.
 * Author: Michael Barall 09/24/2018.
 *
 * Holds a collection of earthquakes that can be queried in the same way as Comcat.
 */
public class ComcatLocalCatalog {

	//----- Binning -----

	// The number of latitude bins.

	protected int n_lat_bins;

	// Default number of latitude bins.

	public static final int DEF_N_LAT_BINS = 200;

	// Given a latitude, return the scaled latitude.
	// The latitude ranges from -90 to +90.
	// The scaled latitude ranges from 0.0 to 1.0.
	// Note: Accumulation of rounding errors can conceivably result
	// in a scaled value slightly outside the given range.

	protected double get_sc_lat (double lat) {
		return (lat + 90.0) / 180.0;
	}

	// Given a longitude, return the scaled longitude.
	// The longitude ranges from -360 to +360.
	// The scaled longitude ranges from 0.0 to 2.0.
	// Note: Accumulation of rounding errors can conceivably result
	// in a scaled value slightly outside the given range.

	protected double get_sc_lon (double lon) {
		return (lon + 360.0) / 360.0;
	}

	// Given a scaled latitude, return the bin number.
	// The scaled latitude can range from 0.0 to 1.0.
	// The return value ranges from 0 to n_lat_bins - 1.

	protected int get_lat_bin (double sc_lat) {
		int bin = (int)(sc_lat * ((double)n_lat_bins));
		if (bin >= n_lat_bins) {
			bin = n_lat_bins - 1;
		}
		return bin;
	}

	// Given a scaled longitude, return the bin number.
	// The scaled longitude can range from 0.0 to 2.0.
	// The return value ranges from 0 to 2*n_lon_bins - 1.

	protected int get_lon_bin (double sc_lon, int n_lon_bins) {
		int bin = (int)(sc_lon * ((double)n_lon_bins));
		if (bin >= 2 * n_lon_bins) {
			bin = 2 * n_lon_bins - 1;
		}
		return bin;
	}

	// Calculate the number of longitude bins for a given latitude bin.
	// The latitude bin ranges from 0 to n_lat_bins-1.

	protected int calc_n_lon_bins (int lat_bin) {
		double r_lat_bin = ((double)lat_bin);
		double r_n_lat_bins = ((double)n_lat_bins);
		double r_bins = 2.0 * r_n_lat_bins * Math.sin(((r_lat_bin + 0.5) / r_n_lat_bins) * Math.PI);
		int bins = (int)(Math.round(r_bins));
		if (bins < 1) {
			bins = 1;
		}
		return bins;
	}




	//----- Data structures -----

	// Map of event ids to local catalog entries.

	protected HashMap<String, ComcatLocalCatalogEntry> event_map;

	// Bins of events.
	// The first index is the latitude bin, and ranges from 0 to n_lat_bins-1.
	// The second index is the latitude bin, and ranges from 0 to n_lon_bins-1,
	//  where n_lon_bins varies depending on the latitude bin.
	// The third index is time.  Time is not binned, but events are sorted in
	//  order of increasing time, allowing a binary search.

	protected ComcatLocalCatalogEntry[][][] event_bins;




	//----- Catalog statistics -----

	// Total number of bins.

	protected int stat_total_bins;

	// Total number of events.

	protected int stat_total_events;

	// Maximum size of a bin.

	protected int stat_max_bin_size;

	// Minimum and maximum time.

	protected long stat_min_time;
	protected long stat_max_time;

	// Minimum and maximum depth.

	protected double stat_min_depth;
	protected double stat_max_depth;

	// Minimum and maximum magnitude.

	protected double stat_min_mag;
	protected double stat_max_mag;

	// Bin size histogram, length is stat_max_bin_size + 1.

	protected int[] stat_bin_size_histogram;




	//----- Construction -----

	// Load the catalog from a scanner.
	// Throws an exception if the load fails.

	public void load_catalog (Scanner scanner, int the_n_lat_bins) {

		// Set the number of latitude bins

		n_lat_bins = the_n_lat_bins;

		// Initialize counters

		clear_stat();

		// Create the map of event ids

		event_map = new HashMap<String, ComcatLocalCatalogEntry>();

		// Create a temporary array of variable-size bins

		ArrayList<ArrayList<ArrayList<ComcatLocalCatalogEntry>>> var_bins = new ArrayList<ArrayList<ArrayList<ComcatLocalCatalogEntry>>>();

		// Loop over latitude bins

		for (int lat_bin = 0; lat_bin < n_lat_bins; ++lat_bin) {
			int n_lon_bins = calc_n_lon_bins (lat_bin);
			stat_total_bins += n_lon_bins;

			// Create the longitude bins

			var_bins.add (new ArrayList<ArrayList<ComcatLocalCatalogEntry>>());

			// Loop over longitude bins, and create the bin

			for (int lon_bin = 0; lon_bin < n_lon_bins; ++lon_bin) {
				var_bins.get(lat_bin).add (new ArrayList<ComcatLocalCatalogEntry>());
			}
		}

		// Loop over catalog entries

		while (scanner.hasNext()) {

			// Read the next entry from the scanner

			ComcatLocalCatalogEntry entry = new ComcatLocalCatalogEntry();
			entry.parse_line (scanner);

			// Add to our map of event ids

			String dup_id = entry.add_ids_to_map (event_map);

			// If it's not a duplicate ...

			if (dup_id == null) {

				// Accumulate its statistics

				accum_stat (entry);

				// Find its bin

				int lat_bin = get_lat_bin(get_sc_lat(entry.rup_lat));
				ArrayList<ArrayList<ComcatLocalCatalogEntry>> lon_bins = var_bins.get(lat_bin);
				int n_lon_bins = lon_bins.size();
				int lon_bin = get_lon_bin(get_sc_lon(entry.rup_lon), n_lon_bins);
				ArrayList<ComcatLocalCatalogEntry> bin = lon_bins.get(lon_bin % n_lon_bins);

				// Add to the bin

				bin.add (entry);
				int bin_size = bin.size();
				if (stat_max_bin_size < bin_size) {
					stat_max_bin_size = bin_size;
				}
			}
		}

		// Initialize histogram

		stat_bin_size_histogram = new int[stat_max_bin_size + 1];
		for (int h = 0; h <= stat_max_bin_size; ++h) {
			stat_bin_size_histogram[h] = 0;
		}

		// Create the array of bins

		event_bins = new ComcatLocalCatalogEntry[n_lat_bins][][];

		// Loop over latitude bins

		for (int lat_bin = 0; lat_bin < n_lat_bins; ++lat_bin) {
			int n_lon_bins = var_bins.get(lat_bin).size();

			// Create the longitude bins

			event_bins[lat_bin] = new ComcatLocalCatalogEntry[n_lon_bins][];

			// Loop over longitude bins

			for (int lon_bin = 0; lon_bin < n_lon_bins; ++lon_bin) {

				// Get the variable-size bin

				ArrayList<ComcatLocalCatalogEntry> bin = var_bins.get(lat_bin).get(lon_bin);
				int bin_size = bin.size();
				stat_bin_size_histogram[bin_size] = stat_bin_size_histogram[bin_size] + 1;

				// Sort the list in order of increasing time

				if (bin_size > 1) {
					bin.sort (new Comparator<ComcatLocalCatalogEntry>(){
						@Override
						public int compare (ComcatLocalCatalogEntry entry1, ComcatLocalCatalogEntry entry2) {
							return Long.compare (entry1.rup_time, entry2.rup_time);
						}
					});
				}

				// Convert to an array, and save

				event_bins[lat_bin][lon_bin] = bin.toArray (new ComcatLocalCatalogEntry[bin_size]);
			}
		}

		return;
	}




	// Load the catalog from a file.
	// Throws an exception if the load fails.

	public void load_catalog (String filename, int the_n_lat_bins) throws IOException {
		try (
			Scanner scanner = new Scanner (new BufferedReader (new FileReader (filename)));
		){
			load_catalog (scanner, the_n_lat_bins);
		}
		return;
	}




	// Clear all the statistics variables.

	public void clear_stat () {
		stat_total_bins = 0;
		stat_total_events = 0;
		stat_max_bin_size = 0;
		stat_min_time = 0L;
		stat_max_time = 0L;
		stat_min_depth = 0.0;
		stat_max_depth = 0.0;
		stat_min_mag = 0.0;
		stat_max_mag = 0.0;
		stat_bin_size_histogram = null;
		return;
	}




	// Accumulate statistics for an event.

	public void accum_stat (ComcatLocalCatalogEntry entry) {

		// Initialize for first event

		if (stat_total_events == 0) {
			stat_min_time = entry.rup_time;
			stat_max_time = entry.rup_time;
			stat_min_depth = entry.rup_depth;
			stat_max_depth = entry.rup_depth;
			stat_min_mag = entry.rup_mag;
			stat_max_mag = entry.rup_mag;
		}

		// Accumulate statistics

		++stat_total_events;

		if (stat_min_time > entry.rup_time) {
			stat_min_time = entry.rup_time;
		}
		if (stat_max_time < entry.rup_time) {
			stat_max_time = entry.rup_time;
		}

		if (stat_min_depth > entry.rup_depth) {
			stat_min_depth = entry.rup_depth;
		}
		if (stat_max_depth < entry.rup_depth) {
			stat_max_depth = entry.rup_depth;
		}

		if (stat_min_mag > entry.rup_mag) {
			stat_min_mag = entry.rup_mag;
		}
		if (stat_max_mag < entry.rup_mag) {
			stat_max_mag = entry.rup_mag;
		}
		
		return;
	}




	// Default constructor.

	public ComcatLocalCatalog () {
		n_lat_bins = 0;
		event_map = null;
		event_bins = null;

		clear_stat();
	}




	// Display our contents.

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();

		result.append ("ComcatLocalCatalog:" + "\n");
		result.append ("n_lat_bins = " + n_lat_bins + "\n");
		result.append ("stat_total_bins = " + stat_total_bins + "\n");
		result.append ("stat_total_events = " + stat_total_events + "\n");
		result.append ("stat_max_bin_size = " + stat_max_bin_size + "\n");
		result.append ("stat_min_time = " + SimpleUtils.time_raw_and_string(stat_min_time) + "\n");
		result.append ("stat_max_time = " + SimpleUtils.time_raw_and_string(stat_max_time) + "\n");
		result.append ("stat_min_depth = " + stat_min_depth + "\n");
		result.append ("stat_max_depth = " + stat_max_depth + "\n");
		result.append ("stat_min_mag = " + stat_min_mag + "\n");
		result.append ("stat_max_mag = " + stat_max_mag + "\n");
		for (int h = 0; h <= stat_max_bin_size; ++h) {
			result.append ("stat_bin_size_histogram[" + h + "] = " + stat_bin_size_histogram[h] + "\n");
		}

		return result.toString();
	}




	//----- Query -----
	



	/**
	 * Fetches an event with the given ID, e.g. "ci37166079"
	 * @param eventID = Earthquake event id.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @param extendedInfo = True to return extended information, see eventToObsRup below.
	 * @return
	 * The return value can be null if the event could not be obtained.
	 * A null return means the event is either not found or deleted in Comcat.
	 * A ComcatException means that there was an error accessing Comcat.
	 */
	public ObsEqkRupture fetchEvent (String eventID, boolean wrapLon, boolean extendedInfo) {
		ObsEqkRupture rup = null;

		// Retrieve the entry

		ComcatLocalCatalogEntry entry = event_map.get (eventID);

		// If found, convert the entry

		if (entry != null) {
			rup = entry.get_eqk_rupture (wrapLon, extendedInfo);
		}
		
		return rup;
	}



	
	/**
	 * Fetch a list of events satisfying the given conditions.
	 * @param exclude_id = An event id to exclude from the results, or null if none.
	 * @param startTime = Start of time interval, in milliseconds after the epoch.
	 * @param endTime = End of time interval, in milliseconds after the epoch.
	 * @param minDepth = Minimum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param maxDepth = Maximum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param region = Region to search.  Events not in this region are filtered out.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @param extendedInfo = True to return extended information, see eventToObsRup below.
	 * @param minMag = Minimum magnitude, or -10.0 for no minimum.
	 * @return
	 * Note: As a special case, if endTime == startTime, then the end time is the current time.
	 */
	public ObsEqkRupList fetchEventList (String exclude_id, long startTime, long endTime,
			double minDepth, double maxDepth, ComcatRegion region, boolean wrapLon, boolean extendedInfo,
			double minMag) {

		// Check depth range

		if (!( minDepth < maxDepth )) {
			throw new IllegalArgumentException ("ComcatLocalCatalog: Min depth must be less than max depth: minDepth = " + minDepth + ", maxDepth = " + maxDepth);
		}

		// Check time range and adjust end time

		long timeNow = System.currentTimeMillis();

		if (!( startTime < timeNow )) {
			throw new IllegalArgumentException ("ComcatLocalCatalog: Start time must be less than time now: startTime = " + startTime + ", timeNow = " + timeNow);
		}

		if (!( startTime <= endTime )) {
			throw new IllegalArgumentException ("ComcatLocalCatalog: Start time must be less than end time: startTime = " + startTime + ", endTime = " + endTime);
		}

		if (endTime == startTime) {
			endTime = timeNow;
		}

		// Set up the event id filter, to remove duplicate events and our excluded event

		HashSet<String> event_filter = new HashSet<String>();

		if (exclude_id != null) {
			event_filter.add (exclude_id);
		}

		// The list of ruptures we are going to build

		ObsEqkRupList rups = new ObsEqkRupList();

		// Scaled latitude range

		double min_sc_lat = get_sc_lat (region.getMinLat());
		double max_sc_lat = get_sc_lat (region.getMaxLat());

		// Scaled longitude range

		double min_sc_lon = get_sc_lon (region.getMinLon());
		double max_sc_lon = get_sc_lon (region.getMaxLon());

		// Latitude bin range

		int min_lat_bin = get_lat_bin (min_sc_lat);
		int max_lat_bin = get_lat_bin (max_sc_lat);

		// Loop over latitude bins

		for (int lat_bin = min_lat_bin; lat_bin <= max_lat_bin; ++lat_bin) {

			// Get the array of longitude bins
		
			ComcatLocalCatalogEntry[][] lon_bins = event_bins[lat_bin];
			int n_lon_bins = lon_bins.length;

			// Longitude bin range

			int min_lon_bin = get_lon_bin (min_sc_lon, n_lon_bins);
			int max_lon_bin = get_lon_bin (max_sc_lon, n_lon_bins);

			if (max_lon_bin > min_lon_bin + n_lon_bins - 1) {
				max_lon_bin = min_lon_bin + n_lon_bins - 1;
			}

			// Loop over longitude bins

			for (int lon_bin = min_lon_bin; lon_bin <= max_lon_bin; ++lon_bin) {

				// Get the time-sorted array

				ComcatLocalCatalogEntry[] time_arr = lon_bins[lon_bin % n_lon_bins];
				int n_entry = time_arr.length;

				// Binary search to find the first entry >= the start time

				int lo = -1;
				int hi = n_entry;
				while (hi - lo > 1) {
					int mid = (hi + lo) / 2;
					if (time_arr[mid].rup_time >= startTime) {
						hi = mid;
					} else {
						lo = mid;
					}
				}

				// Loop over time range

				for (int tix = hi; tix < n_entry; ++tix) {
				
					// Get the entry

					ComcatLocalCatalogEntry entry = time_arr[tix];

					// If end of time interval, exit the Loop

					if (entry.rup_time > endTime) {
						break;
					}

					// If entry passes filters ...

					if (   entry.rup_mag >= minMag
						&& region.contains (entry.rup_lat, entry.rup_lon)
						&& entry.rup_depth >= minDepth
						&& entry.rup_depth <= maxDepth
						&& !(event_filter.contains (entry.rup_id_list[0])) ) {

						// Convert the entry

						ObsEqkRupture rup = entry.get_eqk_rupture (wrapLon, extendedInfo);

						// Add to the list

						rups.add(rup);
					}
				}
			}
		}
		
		return rups;
	}




	//----- Testing and commands -----

	public static void main(String[] args) {

		// There needs to be at least one argument, which is the subcommand

		if (args.length < 1) {
			System.err.println ("ComcatLocalCatalog : Missing subcommand");
			return;
		}




		// Subcommand : Download catalog to a local file.
		// Command format:
		//  download  filename  start_time  end_time  min_mag
		// Download a world-wide catalog, and write to the given file.
		// Times are ISO-8601 format, for example 2011-12-03T10:15:30Z.

		if (args[0].equalsIgnoreCase ("download")) {

			// Four additional arguments

			if (args.length != 5) {
				System.err.println ("ComcatLocalCatalogEntry : Invalid 'download' subcommand");
				return;
			}

			try {

				String filename = args[1];
				long startTime = SimpleUtils.string_to_time (args[2]);
				long endTime = SimpleUtils.string_to_time (args[3]);
				double min_mag = Double.parseDouble (args[4]);

				// Say hello

				System.out.println ("Downloading catalog: " + filename);
				System.out.println ("Start time: " + SimpleUtils.time_to_string(startTime));
				System.out.println ("End time: " + SimpleUtils.time_to_string(endTime));
				System.out.println ("Minimum magnitude: " + min_mag);
				System.out.println ("");

				// Create the accessor

				ComcatAccessor accessor = new ComcatAccessor();

				// Construct the Region

				SphRegionWorld region = new SphRegionWorld ();

				// Call Comcat

				String rup_event_id = null;
				double minDepth = ComcatAccessor.DEFAULT_MIN_DEPTH;
				double maxDepth = ComcatAccessor.DEFAULT_MAX_DEPTH;
				boolean wrapLon = false;
				boolean extendedInfo = true;

				ObsEqkRupList rup_list = accessor.fetchEventList (rup_event_id, startTime, endTime,
						minDepth, maxDepth, region, wrapLon, extendedInfo,
						min_mag);

				// Display the information

				System.out.println ("Events returned by fetchEventList = " + rup_list.size());

				// Open the output file

				int entries_written = 0;

				try (
					Writer writer = new BufferedWriter (new FileWriter (filename));
				){
					// Loop over ruptures

					for (ObsEqkRupture rup : rup_list) {

						// Convert to catalog entry

						ComcatLocalCatalogEntry entry = null;

						try {
							entry = new ComcatLocalCatalogEntry();
							entry.set_eqk_rupture (rup);
						} catch (Exception e) {
							entry = null;
						}

						// If we got an entry, write to the file

						if (entry != null) {
							writer.write (entry.format_line() + "\n");
							++entries_written;
						}
					}
				}

				// Display the result

				System.out.println ("Events written to local catalog = " + entries_written);

            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}




		// Subcommand : Load catalog from a local file and display statistics.
		// Command format:
		//  statistics  filename

		if (args[0].equalsIgnoreCase ("statistics")) {

			// One additional argument

			if (args.length != 2) {
				System.err.println ("ComcatLocalCatalogEntry : Invalid 'statistics' subcommand");
				return;
			}

			try {

				String filename = args[1];

				// Say hello

				System.out.println ("Loading catalog: " + filename);

				// Load the catalog

				ComcatLocalCatalog local_catalog = new ComcatLocalCatalog();
				local_catalog.load_catalog (filename, ComcatLocalCatalog.DEF_N_LAT_BINS);

				// Display statistics

				System.out.println (local_catalog.toString());

            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}




		// Subcommand : Test #1
		// Command format:
		//  test1  filename  event_id
		// Fetch information for an event, and display it.
		// Same as ComcatAccessor test #1, except reading from a local catalog.

		if (args[0].equalsIgnoreCase ("test1")) {

			// Two additional arguments

			if (args.length != 3) {
				System.err.println ("ComcatLocalCatalogEntry : Invalid 'test1' subcommand");
				return;
			}

			try {

				String filename = args[1];
				String event_id = args[2];

				// Load the catalog

				System.out.println ("Loading catalog: " + filename);
				ComcatLocalCatalog local_catalog = new ComcatLocalCatalog();
				local_catalog.load_catalog (filename, ComcatLocalCatalog.DEF_N_LAT_BINS);

				// Say hello

				System.out.println ("Fetching event: " + event_id);

				// Get the rupture

				ObsEqkRupture rup = local_catalog.fetchEvent (event_id, false, true);

				// Display its information

				if (rup == null) {
					System.out.println ("Null return from fetchEvent");
					return;
				}

				System.out.println (ComcatAccessor.rupToString (rup));

				String rup_event_id = rup.getEventId();

				Map<String, String> eimap = ComcatAccessor.extendedInfoToMap (rup, ComcatAccessor.EITMOPT_NULL_TO_EMPTY);

				for (String key : eimap.keySet()) {
					System.out.println ("EI Map: " + key + " = " + eimap.get(key));
				}

				List<String> idlist = ComcatAccessor.idsToList (eimap.get (ComcatAccessor.PARAM_NAME_IDLIST), rup_event_id);

				for (String id : idlist) {
					System.out.println ("ID List: " + id);
				}

            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}




		// Subcommand : Test #2
		// Command format:
		//  test2  filename  event_id  min_days  max_days  radius_km  min_mag
		// Fetch information for an event, and display it.
		// Then fetch the event list for a circle surrounding the hypocenter,
		// for the specified interval in days after the origin time,
		// excluding the event itself.
		// Same as ComcatAccessor test #2, except reading from a local catalog.

		if (args[0].equalsIgnoreCase ("test2")) {

			// Six additional arguments

			if (args.length != 7) {
				System.err.println ("ComcatLocalCatalogEntry : Invalid 'test2' subcommand");
				return;
			}

			try {

				String filename = args[1];
				String event_id = args[2];
				double min_days = Double.parseDouble (args[3]);
				double max_days = Double.parseDouble (args[4]);
				double radius_km = Double.parseDouble (args[5]);
				double min_mag = Double.parseDouble (args[6]);

				// Load the catalog

				System.out.println ("Loading catalog: " + filename);
				ComcatLocalCatalog local_catalog = new ComcatLocalCatalog();
				local_catalog.load_catalog (filename, ComcatLocalCatalog.DEF_N_LAT_BINS);

				// Say hello

				System.out.println ("Fetching event: " + event_id);

				// Get the rupture

				ObsEqkRupture rup = local_catalog.fetchEvent (event_id, false, true);

				// Display its information

				if (rup == null) {
					System.out.println ("Null return from fetchEvent");
					return;
				}

				System.out.println (ComcatAccessor.rupToString (rup));

				String rup_event_id = rup.getEventId();
				long rup_time = rup.getOriginTime();
				Location hypo = rup.getHypocenterLocation();

				// Say hello

				System.out.println ("Fetching event list");
				System.out.println ("min_days = " + min_days);
				System.out.println ("max_days = " + max_days);
				System.out.println ("radius_km = " + radius_km);
				System.out.println ("min_mag = " + min_mag);

				// Construct the Region

				SphRegionCircle region = new SphRegionCircle (new SphLatLon(hypo), radius_km);

				// Calculate the times

				long startTime = rup_time + (long)(min_days*ComcatAccessor.day_millis);
				long endTime = rup_time + (long)(max_days*ComcatAccessor.day_millis);

				// Call Comcat

				double minDepth = ComcatAccessor.DEFAULT_MIN_DEPTH;
				double maxDepth = ComcatAccessor.DEFAULT_MAX_DEPTH;
				boolean wrapLon = false;
				boolean extendedInfo = false;

				ObsEqkRupList rup_list = local_catalog.fetchEventList (rup_event_id, startTime, endTime,
						minDepth, maxDepth, region, wrapLon, extendedInfo,
						min_mag);

				// Display the information

				System.out.println ("Events returned by fetchEventList = " + rup_list.size());

            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}




		// Unrecognized subcommand.

		System.err.println ("ComcatLocalCatalog : Unrecognized subcommand : " + args[0]);
		return;

	}

}
