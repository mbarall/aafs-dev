package scratch.aftershockStatistics.aafs;

import scratch.aftershockStatistics.util.MarshalReader;
import scratch.aftershockStatistics.util.MarshalWriter;
import scratch.aftershockStatistics.util.MarshalException;
import scratch.aftershockStatistics.util.MarshalImpArray;
import scratch.aftershockStatistics.util.MarshalImpJsonReader;
import scratch.aftershockStatistics.util.MarshalImpJsonWriter;
import scratch.aftershockStatistics.util.SphLatLon;
import scratch.aftershockStatistics.util.SphRegion;

import scratch.aftershockStatistics.AftershockStatsCalc;
import scratch.aftershockStatistics.ComcatAccessor;
import scratch.aftershockStatistics.GenericRJ_Parameters;
import scratch.aftershockStatistics.GenericRJ_ParametersFetch;
import scratch.aftershockStatistics.MagCompPage_Parameters;
import scratch.aftershockStatistics.MagCompPage_ParametersFetch;
import scratch.aftershockStatistics.OAFTectonicRegime;
import scratch.aftershockStatistics.SeqSpecRJ_Parameters;

import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.commons.geo.Location;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;

/**
 * Parameters for constructing a forecast.
 * Author: Michael Barall 04/21/2018.
 *
 * All fields are public, since there is little benefit to having lots of getters and setters.
 */
public class ForecastParameters {

	//----- Constants -----

	// Parameter status codes.

	public static final int PSTAT_MIN = 0;			// Minimum value
	public static final int PSTAT_OMITTED = 0;		// Parameters omitted or not available
	public static final int PSTAT_AUTO = 1;			// Parameters determined by automatic system
	public static final int PSTAT_ANALYST = 2;		// Parameters selected by analyst
	public static final int PSTAT_MAX = 2;			// Maximum value


	//----- Root parameters -----

	// Mainshock event id.

	public String event_id = "";

	// Time of forecast, in milliseconds since the epoch.
	// Restriction: Must be greater than the value in the previous forecast.

	public long forecast_time = 0L;


	//----- Mainshock parameters -----

	// Mainshock parameter status code.

	public int mainshock_pstat = PSTAT_OMITTED;

	// Mainshock time, in milliseconds since the epoch.

	public long mainshock_time = 0L;

	// Mainshock magnitude.

	public double mainshock_mag = 0.0;

	// Mainshock latitude, in degrees, from -90 to +90.

	public double mainshock_lat = 0.0;

	// Mainshock longitude, in degrees, from -180 to +180.

	public double mainshock_lon = 0.0;

	// Mainshock depth, in kilometers, positive underground.

	public double mainshock_depth = 0.0;

	// Set mainshock parameters from rupture information.

	public void set_eqk_rupture (ObsEqkRupture rup) {
		mainshock_time = rup.getOriginTime();
		mainshock_mag = rup.getMag();
		Location hypo = rup.getHypocenterLocation();
		mainshock_lat = hypo.getLatitude();
		mainshock_lon = hypo.getLongitude();
		mainshock_depth = hypo.getDepth();

		if (mainshock_lon > 180.0) {
			mainshock_lon -= 360.0;
		}
		if (mainshock_lon < -180.0) {
			mainshock_lon = 180.0;
		}

		if (mainshock_lat > 90.0) {
			mainshock_lat = 90.0;
		}
		else if (mainshock_lat < -90.0) {
			mainshock_lat = -90.0;
		}

		return;
	}

	// Get location from mainshock parameters.

	public Location get_eqk_location () {
		return new Location (mainshock_lat, mainshock_lon, mainshock_depth);
	}

	// Get spherical location from mainshock parameters.

	public SphLatLon get_sph_eqk_location () {
		return new SphLatLon (mainshock_lat, mainshock_lon);
	}

	// Get rupture information from mainshock parameters.

	public ObsEqkRupture get_eqk_rupture () {
		return new ObsEqkRupture (event_id, mainshock_time, get_eqk_location(), mainshock_mag);
	}

	// Fetch mainshock parameters.
	// Note: Must set event_id first.

	public void fetch_mainshock_params (ForecastParameters prior_params) {

		// If the prior parameters have analyst-supplied values, copy them

		if (prior_params != null) {
			if (prior_params.mainshock_pstat == PSTAT_ANALYST) {
				mainshock_pstat = prior_params.mainshock_pstat;
				mainshock_time = prior_params.mainshock_time;
				mainshock_mag = prior_params.mainshock_mag;
				mainshock_lat = prior_params.mainshock_lat;
				mainshock_lon = prior_params.mainshock_lon;
				mainshock_depth = prior_params.mainshock_depth;
				return;
			}
		}

		// Fetch parameters from Comcat

		ObsEqkRupture rup;

		try {
			ComcatAccessor accessor = new ComcatAccessor();
			rup = accessor.fetchEvent(event_id, false, false);
		} catch (Exception e) {
			throw new RuntimeException("ForecastParameters.fetch_mainshock_params: Comcat exception", e);
		}

		if (rup == null) {
			throw new RuntimeException("ForecastParameters.fetch_mainshock_params: Comcat returned nothing");
		}

		mainshock_pstat = PSTAT_AUTO;
		set_eqk_rupture (rup);
		return;
	}


	//----- R&J generic parameters -----

	// Generic parameter status code.

	public int generic_pstat = PSTAT_OMITTED;

	// Tectonic regime (null iff omitted).
	// (For analyst-supplied values, cannot be null, but can be an empty string,
	// and need not be the name of a known tectonic regime.)

	public String generic_regime = null;

	// Generic parameters (null iff omitted).

	public GenericRJ_Parameters generic_params = null;

	// Fetch generic parameters.
	// Note: Mainshock parameters must be fetched first.

	public void fetch_generic_params (ForecastParameters prior_params) {

		// If the prior parameters have analyst-supplied values, copy them

		if (prior_params != null) {
			if (prior_params.generic_pstat == PSTAT_ANALYST) {
				generic_pstat = prior_params.generic_pstat;
				generic_regime = prior_params.generic_regime;
				generic_params = prior_params.generic_params;
				return;
			}
		}

		// If we don't have mainshock parameters, then we can't fetch generic parameters

		if (mainshock_pstat == PSTAT_OMITTED) {
			generic_pstat = PSTAT_OMITTED;
			generic_regime = null;
			generic_params = null;
			return;
		}

		// Fetch parameters based on mainshock location
		
		GenericRJ_ParametersFetch fetch = new GenericRJ_ParametersFetch();
		OAFTectonicRegime regime = fetch.getRegion(new Location (mainshock_lat, mainshock_lon, mainshock_depth));

		generic_pstat = PSTAT_AUTO;
		generic_regime = regime.toString();
		generic_params = fetch.get(regime);

		return;
	}


	//----- R&J magnitude of completeness parameters -----

	// Magnitude of completeness parameter status code.

	public int mag_comp_pstat = PSTAT_OMITTED;

	// Tectonic regime (null iff omitted).
	// (For analyst-supplied values, cannot be null, but can be an empty string,
	// and need not be the name of a known tectonic regime.)

	public String mag_comp_regime = null;

	// Magnitude of completeness parameters (null iff omitted).

	public MagCompPage_Parameters mag_comp_params = null;

	// Fetch magnitude of completeness parameters.
	// Note: Mainshock parameters must be fetched first.

	public void fetch_mag_comp_params (ForecastParameters prior_params) {

		// If the prior parameters have analyst-supplied values, copy them

		if (prior_params != null) {
			if (prior_params.mag_comp_pstat == PSTAT_ANALYST) {
				mag_comp_pstat = prior_params.mag_comp_pstat;
				mag_comp_regime = prior_params.mag_comp_regime;
				mag_comp_params = prior_params.mag_comp_params;
				return;
			}
		}

		// If we don't have mainshock parameters, then we can't fetch magnitude of completeness parameters

		if (mainshock_pstat == PSTAT_OMITTED) {
			mag_comp_pstat = PSTAT_OMITTED;
			mag_comp_regime = null;
			mag_comp_params = null;
			return;
		}

		// Fetch parameters based on mainshock location
		
		MagCompPage_ParametersFetch fetch = new MagCompPage_ParametersFetch();
		OAFTectonicRegime regime = fetch.getRegion (get_eqk_location());

		mag_comp_pstat = PSTAT_AUTO;
		mag_comp_regime = regime.toString();
		mag_comp_params = fetch.get(regime);

		return;
	}


	//----- R&J sequence specific parameters -----

	// Sequence specific parameter status code.

	public int seq_spec_pstat = PSTAT_OMITTED;

	// Sequence specific parameters (null iff omitted).

	public SeqSpecRJ_Parameters seq_spec_params = null;

	// Fetch sequence specific parameters.
	// Note: Generic parameters must be fetched first.

	public void fetch_seq_spec_params (ForecastParameters prior_params) {

		// If the prior parameters have analyst-supplied values, copy them

		if (prior_params != null) {
			if (prior_params.seq_spec_pstat == PSTAT_ANALYST) {
				seq_spec_pstat = prior_params.seq_spec_pstat;
				seq_spec_params = prior_params.seq_spec_params;
				return;
			}
		}

		// If we don't have generic parameters, then we can't make sequence specific parameters

		if (generic_pstat == PSTAT_OMITTED) {
			seq_spec_pstat = PSTAT_OMITTED;
			seq_spec_params = null;
			return;
		}

		// Fetch parameters based on generic parameters

		seq_spec_pstat = PSTAT_AUTO;
		seq_spec_params = new SeqSpecRJ_Parameters(generic_params);

		return;
	}


	//----- Aftershock search parameters -----

	// Aftershock search parameter status code.

	public int aftershock_search_pstat = PSTAT_OMITTED;

	// Aftershock search region (null iff omitted).

	public SphRegion aftershock_search_region = null;

	// Minimum search time, in days after the mainshock.

	public double min_days = 0.0;

	// Maximum search time, in days after the mainshock.
	// Note: This get set to the current time, even if there are analyst-supplied values.

	public double max_days = 0.0;

	// Minimum search depth, in kilometers.

	public double min_depth = 0.0;

	// Maximum search depth, in kilometers.
	// (Comcat has 1000 km maximum, OpenSHA has 700 km maximum.)

	public double max_depth = 700.0;

	// Fetch aftershock search region.
	// Note: Mainshock parameters must be fetched first.

	public void fetch_aftershock_search_region (ForecastParameters prior_params) {

		// Set maximum search time to now

		//max_days = (System.currentTimeMillis() - mainshock_time)/ComcatAccessor.day_millis;
		max_days = (forecast_time - mainshock_time)/ComcatAccessor.day_millis;

		// If the prior parameters have analyst-supplied values, copy them

		if (prior_params != null) {
			if (prior_params.aftershock_search_pstat == PSTAT_ANALYST) {
				aftershock_search_pstat = prior_params.aftershock_search_pstat;
				aftershock_search_region = prior_params.aftershock_search_region;
				min_days = prior_params.min_days;
				min_depth = prior_params.min_depth;
				max_depth = prior_params.max_depth;
				return;
			}
		}

		// If we don't have mainshock parameters, then we can't fetch search region

		if (mainshock_pstat == PSTAT_OMITTED) {
			aftershock_search_pstat = PSTAT_OMITTED;
			aftershock_search_region = null;
			min_days = 0.0;
			max_days = 0.0;
			min_depth = 0.0;
			max_depth = 700.0;
			return;
		}

		// Get initial search radius from Wells and Coppersmith

		WC1994_MagLengthRelationship wcMagLen = new WC1994_MagLengthRelationship();
		double radius = wcMagLen.getMedianLength(mainshock_mag);

		// The initial region is a circle centered at the epicenter

		SphRegion initial_region = SphRegion.makeCircle (get_sph_eqk_location(), radius);

		// Time range used for sampling aftershocks, in days since the mainshock

		min_days = 0.0;

		// Depth range used for sampling aftershocks, in kilometers

		min_depth = 0.0;
		max_depth = 700.0;

		// Retrieve list of aftershocks in the initial region

		ObsEqkRupList aftershocks;

		try {
			ComcatAccessor accessor = new ComcatAccessor();
			aftershocks = accessor.fetchAftershocks(get_eqk_rupture(), min_days, max_days, min_depth, max_depth, initial_region, initial_region.getPlotWrap());
		} catch (Exception e) {
			throw new RuntimeException("ForecastParameters.fetch_aftershock_search_region: Comcat exception", e);
		}

		// Center of search region

		Location centroid;

		// If no aftershocks, use the hypocenter location

		if (aftershocks.isEmpty()) {
			centroid = get_eqk_location();
		}

		// Otherwise, use the centroid of the aftershocks

		else {
			centroid = AftershockStatsCalc.getSphCentroid(get_eqk_rupture(), aftershocks);
		}

		// Search region is a circle centered at the centroid (or hypocenter if no aftershocks)
			
		aftershock_search_region = SphRegion.makeCircle (new SphLatLon(centroid), radius);

		aftershock_search_pstat = PSTAT_AUTO;
		return;
	}


	//----- Construction -----

	// Default constructor.

	public ForecastParameters () {}

	// Fetch all parameters.

	public void fetch_all (String the_event_id, long the_forecast_time, ForecastParameters prior_params) {
		event_id = the_event_id;
		forecast_time = the_forecast_time;
		fetch_mainshock_params (prior_params);
		fetch_generic_params (prior_params);
		fetch_mag_comp_params (prior_params);
		fetch_seq_spec_params (prior_params);
		fetch_aftershock_search_region (prior_params);
		return;
	}

	// Display our contents

	@Override
	public String toString() {
		return "ForecastParameters:" + "\n"
		+ "event_id = " + event_id + "\n"
		+ "forecast_time = " + forecast_time + "\n"

		+ "mainshock_pstat = " + mainshock_pstat + "\n"
		+ ((mainshock_pstat == PSTAT_OMITTED) ? "" : (
			"mainshock_time = " + mainshock_time + "\n"
			+ "mainshock_mag = " + mainshock_mag + "\n"
			+ "mainshock_lat = " + mainshock_lat + "\n"
			+ "mainshock_lon = " + mainshock_lon + "\n"
			+ "mainshock_depth = " + mainshock_depth + "\n"
		))

		+ "generic_pstat = " + generic_pstat + "\n"
		+ ((generic_pstat == PSTAT_OMITTED) ? "" : (
			"generic_regime = " + generic_regime + "\n"
			+ "generic_params = " + generic_params.toString() + "\n"
		))

		+ "mag_comp_pstat = " + mag_comp_pstat + "\n"
		+ ((mag_comp_pstat == PSTAT_OMITTED) ? "" : (
			"mag_comp_regime = " + mag_comp_regime + "\n"
			+ "mag_comp_params = " + mag_comp_params.toString() + "\n"
		))

		+ "seq_spec_pstat = " + seq_spec_pstat + "\n"
		+ ((seq_spec_pstat == PSTAT_OMITTED) ? "" : (
			"seq_spec_params = " + seq_spec_params.toString() + "\n"
		))

		+ "aftershock_search_pstat = " + aftershock_search_pstat + "\n"
		+ ((aftershock_search_pstat == PSTAT_OMITTED) ? "" : (
			"aftershock_search_region = " + aftershock_search_region.toString() + "\n"
			+ "min_days = " + min_days + "\n"
			+ "max_days = " + max_days + "\n"
			+ "min_depth = " + min_depth + "\n"
			+ "max_depth = " + max_depth + "\n"
		));
	}




	//----- Marshaling -----

	// Marshal version number.

	private static final int MARSHAL_VER_1 = 22001;

	private static final String M_VERSION_NAME = "ForecastParameters";

	// Marshal type code.

	protected static final int MARSHAL_NULL = 22000;
	protected static final int MARSHAL_FCAST_PARAM = 22001;

	protected static final String M_TYPE_NAME = "ClassType";

	// Get the type code.

	protected int get_marshal_type () {
		return MARSHAL_FCAST_PARAM;
	}

	// Marshal object, internal.

	protected void do_marshal (MarshalWriter writer) {

		// Version

		writer.marshalInt (M_VERSION_NAME, MARSHAL_VER_1);

		// Contents

		writer.marshalString ("event_id"       , event_id       );
		writer.marshalLong   ("forecast_time"  , forecast_time  );

		writer.marshalInt ("mainshock_pstat", mainshock_pstat);
		if (mainshock_pstat != PSTAT_OMITTED) {
			writer.marshalLong   ("mainshock_time" , mainshock_time );
			writer.marshalDouble ("mainshock_mag"  , mainshock_mag  );
			writer.marshalDouble ("mainshock_lat"  , mainshock_lat  );
			writer.marshalDouble ("mainshock_lon"  , mainshock_lon  );
			writer.marshalDouble ("mainshock_depth", mainshock_depth);
		}

		writer.marshalInt ("generic_pstat", generic_pstat);
		if (generic_pstat != PSTAT_OMITTED) {
			writer.marshalString ("generic_regime", generic_regime);
			generic_params.marshal (writer, "generic_params");
		}

		writer.marshalInt ("mag_comp_pstat", mag_comp_pstat);
		if (mag_comp_pstat != PSTAT_OMITTED) {
			writer.marshalString ("mag_comp_regime", mag_comp_regime);
			mag_comp_params.marshal (writer, "mag_comp_params");
		}

		writer.marshalInt ("seq_spec_pstat", seq_spec_pstat);
		if (seq_spec_pstat != PSTAT_OMITTED) {
			seq_spec_params.marshal (writer, "seq_spec_params");
		}

		writer.marshalInt ("aftershock_search_pstat", aftershock_search_pstat);
		if (aftershock_search_pstat != PSTAT_OMITTED) {
			SphRegion.marshal_poly (writer, "aftershock_search_region", aftershock_search_region);
			writer.marshalDouble ("min_days" , min_days );
			writer.marshalDouble ("max_days" , max_days );
			writer.marshalDouble ("min_depth", min_depth);
			writer.marshalDouble ("max_depth", max_depth);
		}
	
		return;
	}

	// Unmarshal object, internal.

	protected void do_umarshal (MarshalReader reader) {
	
		// Version

		int ver = reader.unmarshalInt (M_VERSION_NAME, MARSHAL_VER_1, MARSHAL_VER_1);

		// Contents

		event_id        = reader.unmarshalString ("event_id"       );
		forecast_time   = reader.unmarshalLong   ("forecast_time"  );

		mainshock_pstat = reader.unmarshalInt ("mainshock_pstat", PSTAT_MIN, PSTAT_MAX);
		if (mainshock_pstat != PSTAT_OMITTED) {
			mainshock_time  = reader.unmarshalLong   ("mainshock_time" );
			mainshock_mag   = reader.unmarshalDouble ("mainshock_mag"  );
			mainshock_lat   = reader.unmarshalDouble ("mainshock_lat"  );
			mainshock_lon   = reader.unmarshalDouble ("mainshock_lon"  );
			mainshock_depth = reader.unmarshalDouble ("mainshock_depth");
		} else {
			mainshock_time  = 0L;
			mainshock_mag   = 0.0;
			mainshock_lat   = 0.0;
			mainshock_lon   = 0.0;
			mainshock_depth = 0.0;
		}

		generic_pstat = reader.unmarshalInt ("generic_pstat", PSTAT_MIN, PSTAT_MAX);
		if (generic_pstat != PSTAT_OMITTED) {
			generic_regime = reader.unmarshalString ("generic_regime");
			generic_params = (new GenericRJ_Parameters()).unmarshal (reader, "generic_params");
		} else {
			generic_regime = null;
			generic_params = null;
		}

		mag_comp_pstat = reader.unmarshalInt ("mag_comp_pstat", PSTAT_MIN, PSTAT_MAX);
		if (mag_comp_pstat != PSTAT_OMITTED) {
			mag_comp_regime = reader.unmarshalString ("mag_comp_regime");
			mag_comp_params = (new MagCompPage_Parameters()).unmarshal (reader, "mag_comp_params");
		} else {
			mag_comp_regime = null;
			mag_comp_params = null;
		}

		seq_spec_pstat = reader.unmarshalInt ("seq_spec_pstat", PSTAT_MIN, PSTAT_MAX);
		if (seq_spec_pstat != PSTAT_OMITTED) {
			seq_spec_params = (new SeqSpecRJ_Parameters()).unmarshal (reader, "seq_spec_params");
		} else {
			seq_spec_params = null;
		}

		aftershock_search_pstat = reader.unmarshalInt ("aftershock_search_pstat", PSTAT_MIN, PSTAT_MAX);
		if (aftershock_search_pstat != PSTAT_OMITTED) {
			aftershock_search_region = SphRegion.unmarshal_poly (reader, "aftershock_search_region");
			if (aftershock_search_region == null) {
				throw new MarshalException ("Aftershock search region is null");
			}
			min_days  = reader.unmarshalDouble ("min_days" );
			max_days  = reader.unmarshalDouble ("max_days" );
			min_depth = reader.unmarshalDouble ("min_depth");
			max_depth = reader.unmarshalDouble ("max_depth");
		} else {
			aftershock_search_region = null;
			min_days  = 0.0;
			max_days  = 0.0;
			min_depth = 0.0;
			max_depth = 700.0;
		}

		return;
	}

	// Marshal object.

	public void marshal (MarshalWriter writer, String name) {
		writer.marshalMapBegin (name);
		do_marshal (writer);
		writer.marshalMapEnd ();
		return;
	}

	// Unmarshal object.

	public ForecastParameters unmarshal (MarshalReader reader, String name) {
		reader.unmarshalMapBegin (name);
		do_umarshal (reader);
		reader.unmarshalMapEnd ();
		return this;
	}

	// Marshal object, polymorphic.

	public static void marshal_poly (MarshalWriter writer, String name, ForecastParameters obj) {

		writer.marshalMapBegin (name);

		if (obj == null) {
			writer.marshalInt (M_TYPE_NAME, MARSHAL_NULL);
		} else {
			writer.marshalInt (M_TYPE_NAME, obj.get_marshal_type());
			obj.do_marshal (writer);
		}

		writer.marshalMapEnd ();

		return;
	}

	// Unmarshal object, polymorphic.

	public static ForecastParameters unmarshal_poly (MarshalReader reader, String name) {
		ForecastParameters result;

		reader.unmarshalMapBegin (name);
	
		// Switch according to type

		int type = reader.unmarshalInt (M_TYPE_NAME);

		switch (type) {

		default:
			throw new MarshalException ("ForecastParameters.unmarshal_poly: Unknown class type code: type = " + type);

		case MARSHAL_NULL:
			result = null;
			break;

		case MARSHAL_FCAST_PARAM:
			result = new ForecastParameters();
			result.do_umarshal (reader);
			break;
		}

		reader.unmarshalMapEnd ();

		return result;
	}




	//----- Testing -----

	public static void main(String[] args) {

		// There needs to be at least one argument, which is the subcommand

		if (args.length < 1) {
			System.err.println ("ForecastParameters : Missing subcommand");
			return;
		}

		// Subcommand : Test #1
		// Command format:
		//  test1  event_id
		// Get parameters for the event, and display them.

		if (args[0].equalsIgnoreCase ("test1")) {

			// One additional argument

			if (args.length != 2) {
				System.err.println ("ForecastParameters : Invalid 'test1' subcommand");
				return;
			}

			String the_event_id = args[1];

			// Fetch just the mainshock info

			ForecastParameters params = new ForecastParameters();
			params.event_id = the_event_id;
			params.fetch_mainshock_params(null);

			// Set the forecast time to be 7 days after the mainshock

			long the_forecast_time = params.mainshock_time + Math.round(ComcatAccessor.day_millis * 7.0);

			// Get parameters

			params = new ForecastParameters();
			params.fetch_all (the_event_id, the_forecast_time, null);

			// Display them

			System.out.println ("");
			System.out.println (params.toString());

			return;
		}

		// Subcommand : Test #2
		// Command format:
		//  test1  event_id
		// Get parameters for the event, and display them.
		// Then marshal to JSON, and display the JSON.
		// Then unmarshal, and display the unmarshaled parameters.

		if (args[0].equalsIgnoreCase ("test2")) {

			// One additional argument

			if (args.length != 2) {
				System.err.println ("ForecastParameters : Invalid 'test2' subcommand");
				return;
			}

			String the_event_id = args[1];

			// Fetch just the mainshock info

			ForecastParameters params = new ForecastParameters();
			params.event_id = the_event_id;
			params.fetch_mainshock_params(null);

			// Set the forecast time to be 7 days after the mainshock

			long the_forecast_time = params.mainshock_time + Math.round(ComcatAccessor.day_millis * 7.0);

			// Get parameters

			params = new ForecastParameters();
			params.fetch_all (the_event_id, the_forecast_time, null);

			// Display them

			System.out.println ("");
			System.out.println (params.toString());

			// Marshal to JSON

			MarshalImpJsonWriter store = new MarshalImpJsonWriter();
			ForecastParameters.marshal_poly (store, null, params);
			store.check_write_complete ();
			String json_string = store.get_json_string();

			System.out.println ("");
			System.out.println (json_string);

			// Unmarshal from JSON
			
			params = null;

			MarshalImpJsonReader retrieve = new MarshalImpJsonReader (json_string);
			params = ForecastParameters.unmarshal_poly (retrieve, null);
			retrieve.check_read_complete ();

			System.out.println ("");
			System.out.println (params.toString());

			return;
		}

		// Unrecognized subcommand.

		System.err.println ("ForecastParameters : Unrecognized subcommand : " + args[0]);
		return;

	}

}
