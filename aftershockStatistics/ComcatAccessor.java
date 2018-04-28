package scratch.aftershockStatistics;

import gov.usgs.earthquake.event.EventQuery;
import gov.usgs.earthquake.event.EventWebService;
import gov.usgs.earthquake.event.Format;
import gov.usgs.earthquake.event.JsonEvent;
import scratch.aftershockStatistics.util.ObsEqkRupEventIdComparator;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.FileNotFoundException;

import scratch.aftershockStatistics.util.SphLatLon;
import scratch.aftershockStatistics.util.SphRegion;

public class ComcatAccessor {
	
	private static final boolean D = true;
	
	protected EventWebService service;
	
	public ComcatAccessor() {
		try {
			service = new EventWebService(new URL("https://earthquake.usgs.gov/fdsnws/event/1/"));
		} catch (MalformedURLException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
	}
	



	/**
	 * [DEPRECATED]
	 * Fetches an event with the given ID, e.g. "ci37166079"
	 * @param eventID
	 * @return
	 * Note: The longitude is coerced to lie between -90 and +270.
	 * This is so it is possible to draw a region surrounding the mainshock,
	 * and have the entire region lie in the valid range -180 <= lon <= +360.
	 * Note: If in the future, the Region class is replaced by something that
	 * does spherical geometry, then the coercion here and below could be removed,
	 * and all longitudes could be between -180 and 180.
	 */
	public ObsEqkRupture fetchEvent(String eventID) {
		EventQuery query = new EventQuery();
		query.setEventId(eventID);
		List<JsonEvent> events;
		try {
			events = service.getEvents(query);
		} catch (FileNotFoundException e) {
			// If ComCat does not recognize the eventID, ComCat returns HTTP error 404, which appears here as FileNotFoundException.
			return null;
		} catch (IOException e) {
			// If the eventID has been deleted from ComCat, ComCat returns HTTP error 409, which appears here as IOException.
			return null;
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		if (events.isEmpty())
			return null;
		Preconditions.checkState(events.size() == 1, "More that 1 match? "+events.size());
		
		JsonEvent event = events.get(0);
//		printJSON(event);
		
		return eventToObsRup(event);
	}
	



	/**
	 * Fetches an event with the given ID, e.g. "ci37166079"
	 * @param eventID = Earthquake event id.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @return
	 * The return value can be null if the event could not be obtained.
	 * A null return may indicate a temporary condition (e.g., Comcat not responding) or a
	 * permanent condition (e.g., event id not recognized).
	 * Note: This entry point always returns extended information.
	 */
	public ObsEqkRupture fetchEvent(String eventID, boolean wrapLon) {
		return fetchEvent(eventID, wrapLon, true);
	}
	



	/**
	 * Fetches an event with the given ID, e.g. "ci37166079"
	 * @param eventID = Earthquake event id.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @param extendedInfo = True to return extended information, see eventToObsRup below.
	 * @return
	 * The return value can be null if the event could not be obtained.
	 * A null return may indicate a temporary condition (e.g., Comcat not responding) or a
	 * permanent condition (e.g., event id not recognized).
	 */
	public ObsEqkRupture fetchEvent(String eventID, boolean wrapLon, boolean extendedInfo) {
		EventQuery query = new EventQuery();
		query.setEventId(eventID);
		List<JsonEvent> events;
		try {
			events = service.getEvents(query);
		} catch (FileNotFoundException e) {
			// If ComCat does not recognize the eventID, ComCat returns HTTP error 404, which appears here as FileNotFoundException.
			return null;
		} catch (IOException e) {
			// If the eventID has been deleted from ComCat, ComCat returns HTTP error 409, which appears here as IOException.
			return null;
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		if (events.isEmpty())
			return null;
		Preconditions.checkState(events.size() == 1, "More that 1 match? "+events.size());
		
		JsonEvent event = events.get(0);
//		printJSON(event);
		
		return eventToObsRup(event, wrapLon, extendedInfo);
	}
	



	// Print a JSONObject.  Apparently for debugging.

	public static void printJSON(JSONObject json) {
		printJSON(json, "");
	}
	private static void printJSON(JSONObject json, String prefix) {
		for (Object key : json.keySet()) {
			Object val = json.get(key);
			if (val != null && val.toString().startsWith("[{")) {
				String str = val.toString();
				try {
					val = new JSONParser().parse(str.substring(1, str.length()-1));
				} catch (ParseException e) {
//					e.printStackTrace();
				}
			}
			if (val != null && val instanceof JSONObject) {
				System.out.println(prefix+key+":");
				String prefix2 = prefix;
				if (prefix2 == null)
					prefix2 = "";
				prefix2 += "\t";
				printJSON((JSONObject)val, prefix2);
			} else {
				System.out.println(prefix+key+": "+val);
			}
		}
	}
	
	public static final double day_millis = 24d*60d*60d*1000d;



	
	/**
	 * [DEPRECATED]
	 * Fetch all aftershocks of the given event. Returned list will not contain the mainshock
	 * even if it matches the query.
	 * @param mainshock
	 * @param minDays
	 * @param maxDays
	 * @param minDepth
	 * @param maxDepth
	 * @param region
	 * @return
	 * Note: The mainshock parameter must be a return value from fetchEvent() above.
	 * Note: If the mainshock longitude is between -90 and +90, then the aftershock
	 * longitudes lie between -180 and +180.  If the mainshock longitude is between
	 * +90 and +270, then the aftershock longitudes are coerced to lie between
	 * 0 and +360.  This makes is possible to easily test if an aftershock lies
	 * within a region surrounding the mainshock.
	 */
	public ObsEqkRupList fetchAftershocks(ObsEqkRupture mainshock, double minDays, double maxDays,
			double minDepth, double maxDepth, Region region) {
		EventQuery query = new EventQuery();
		
		Preconditions.checkState(minDepth < maxDepth, "Min depth must be less than max depth");
		query.setMinDepth(new BigDecimal(String.format("%.3f", minDepth)));
		query.setMaxDepth(new BigDecimal(String.format("%.3f", maxDepth)));
		
		Preconditions.checkState(minDays < maxDays, "Min days must be less than max days");
		// time zones shouldn't be an issue since we're just adding to the original catalog time, whatever
		// time zone that is in.
		long eventTime = mainshock.getOriginTime();
		long startTime = eventTime + (long)(minDays*day_millis);
		long endTime = eventTime + (long)(maxDays*day_millis);
		query.setStartTime(new Date(startTime));
		if(endTime==startTime)
			endTime=Instant.now().toEpochMilli();
		query.setEndTime(new Date(endTime));
		
		Preconditions.checkState(startTime < System.currentTimeMillis(), "Aftershock fetch start time is after now!");
		
		// need to set this threshold at 90 (not 180) so that mainshocks located just
		// west of the date line are handled correctly
		boolean mainshockLonWrapped = mainshock.getHypocenterLocation().getLongitude() > 90;
		
		query.setMinLatitude(new BigDecimal(String.format("%.5f", region.getMinLat())));
		query.setMaxLatitude(new BigDecimal(String.format("%.5f", region.getMaxLat())));
		query.setMinLongitude(new BigDecimal(String.format("%.5f", region.getMinLon())));
		query.setMaxLongitude(new BigDecimal(String.format("%.5f", region.getMaxLon())));
		query.setLimit(20000);
		List<JsonEvent> events;
		int count=20000;
		ObsEqkRupList rups = new ObsEqkRupList();
		Date latest=new Date(endTime);
		Date endTimeStamp;
		do{
			endTimeStamp=latest;
			query.setEndTime(latest);
			if (D)
				try {
					System.out.println(service.getUrl(query, Format.GEOJSON));
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}

			try {
				events = service.getEvents(query);
				count = events.size();
			} catch (FileNotFoundException e) {
				events = null;
				count = 0;
			} catch (IOException e) {
				events = null;
				count = 0;
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}

			System.out.println(count);

			if (count > 0) {
				for (JsonEvent event : events) {
					boolean wrap = mainshockLonWrapped && event.getLongitude().doubleValue() < 0;
					ObsEqkRupture rup = eventToObsRup(event, wrap, false);
					if (rup !=null)
						rups.add(rup);
				}
			}
			rups.sortByOriginTime();
			if(count==0)
				break;
			latest=rups.get(0).getOriginTimeCal().getTime();
		}while(count==20000 && endTimeStamp.compareTo(latest)!=0);
		Collections.sort(rups, new ObsEqkRupEventIdComparator());
		ObsEqkRupList delrups=new ObsEqkRupList();
		ObsEqkRupture previous =null;
		for (ObsEqkRupture rup : rups) {
			if (rup.getEventId().equals(mainshock.getEventId()) || (previous!=null && rup.getEventId().equals(previous.getEventId()))) {
				//if (D) System.out.println("Removing mainshock (M="+rup.getMag()+") from aftershock list");
				delrups.add(rup);
			}
		}
		rups.removeAll(delrups);
		if (!region.isRectangular()) {
			if (D) System.out.println("Fetched "+rups.size()+" events before region filtering");
			for (int i=rups.size(); --i>=0;)
				if (!region.contains(rups.get(i).getHypocenterLocation()))
					rups.remove(i);
		}
		
		if (D) System.out.println("Returning "+rups.size()+" aftershocks");
		
		return rups;
	}



	
	/**
	 * Fetch all aftershocks of the given event. Returned list will not contain the mainshock
	 * even if it matches the query.
	 * @param mainshock = Mainshock.
	 * @param minDays = Start of time interval, in days after the mainshock.
	 * @param maxDays = End of time interval, in days after the mainshock.
	 * @param minDepth = Minimum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param maxDepth = Minimum depth, in km.  Comcat requires a value from -100 to +1000.
	 * @param region = Region to search.  Events not in this region are filtered out.
	 * @param wrapLon = Desired longitude range: false = -180 to 180; true = 0 to 360.
	 * @return
	 * Note: The mainshock parameter must be a return value from fetchEvent() above.
	 * Note: As a special case, if maxDays == minDays, then the end time is the current time.
	 */
	public ObsEqkRupList fetchAftershocks(ObsEqkRupture mainshock, double minDays, double maxDays,
			double minDepth, double maxDepth, SphRegion region, boolean wrapLon) {
		EventQuery query = new EventQuery();
		
		Preconditions.checkState(minDepth < maxDepth, "Min depth must be less than max depth");
		query.setMinDepth(new BigDecimal(String.format("%.3f", minDepth)));
		query.setMaxDepth(new BigDecimal(String.format("%.3f", maxDepth)));
		
		Preconditions.checkState(minDays <= maxDays, "Min days must be less than max days");
		// time zones shouldn't be an issue since we're just adding to the original catalog time, whatever
		// time zone that is in.
		long eventTime = mainshock.getOriginTime();
		long startTime = eventTime + (long)(minDays*day_millis);
		long endTime = eventTime + (long)(maxDays*day_millis);
		query.setStartTime(new Date(startTime));
		if(endTime==startTime)
			endTime=Instant.now().toEpochMilli();
		query.setEndTime(new Date(endTime));
		
		Preconditions.checkState(startTime < System.currentTimeMillis(), "Aftershock fetch start time is after now!");
		
		// If the region is a circle, use Comcat's circle query

		if (region.isCircular()) {
			query.setLatitude(new BigDecimal(String.format("%.5f", region.getCircleCenter().get_lat())));
			query.setLongitude(new BigDecimal(String.format("%.5f", region.getCircleCenter().get_lon())));
			query.setMaxRadius(new BigDecimal(String.format("%.5f", region.getCircleRadiusDeg())));
		}

		// Otherwise, use Comcat's rectangle query to search the bounding box of the region

		else {
			query.setMinLatitude(new BigDecimal(String.format("%.5f", region.getMinLat())));
			query.setMaxLatitude(new BigDecimal(String.format("%.5f", region.getMaxLat())));
			query.setMinLongitude(new BigDecimal(String.format("%.5f", region.getMinLon())));
			query.setMaxLongitude(new BigDecimal(String.format("%.5f", region.getMaxLon())));
		}

		query.setLimit(20000);
		List<JsonEvent> events;
		int count=20000;
		ObsEqkRupList rups = new ObsEqkRupList();
		Date latest=new Date(endTime);
		Date endTimeStamp;
		do{
			endTimeStamp=latest;
			query.setEndTime(latest);
			if (D)
				try {
					System.out.println(service.getUrl(query, Format.GEOJSON));
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}

			try {
				events = service.getEvents(query);
				count = events.size();
			} catch (FileNotFoundException e) {
				events = null;
				count = 0;
			} catch (IOException e) {
				events = null;
				count = 0;
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}

			System.out.println(count);

			if (count > 0) {
				for (JsonEvent event : events) {
					ObsEqkRupture rup = eventToObsRup(event, wrapLon, false);
					if (rup !=null)
						rups.add(rup);
				}
			}
			rups.sortByOriginTime();
			if(count==0)
				break;
			latest=rups.get(0).getOriginTimeCal().getTime();
		}while(count==20000 && endTimeStamp.compareTo(latest)!=0);

		// Sort by event id, and then scan the list to remove duplicates

		Collections.sort(rups, new ObsEqkRupEventIdComparator());
		ObsEqkRupList delrups=new ObsEqkRupList();
		ObsEqkRupture previous =null;
		for (ObsEqkRupture rup : rups) {
			if (rup.getEventId().equals(mainshock.getEventId()) || (previous!=null && rup.getEventId().equals(previous.getEventId()))) {
				//if (D) System.out.println("Removing mainshock (M="+rup.getMag()+") from aftershock list");
				delrups.add(rup);
			}
		}
		rups.removeAll(delrups);

		// If region is neither rectangular or circular, scan list and remove ruptures not in our region
		// TODO: Using rups.remove(i) is very inefficient for a large list

		if (!( region.isRectangular() || region.isCircular() )) {
			if (D) System.out.println("Fetched "+rups.size()+" events before region filtering");
			for (int i=rups.size(); --i>=0;)
				if (!region.contains(rups.get(i).getHypocenterLocation()))
					rups.remove(i);
		}
		
		if (D) System.out.println("Returning "+rups.size()+" aftershocks");
		
		return rups;
	}




	// [DEPRECATED]
	// This function should be private.  It is public only to avoid breaking class
	// scratch.kevin.ucerf3.LaHabraProbCalc.  New code should NOT call this function.
	public static ObsEqkRupture eventToObsRup(JsonEvent event) {
		// default to moving anything with lon < -90 to the positive domain
		// then we'll apply this consistently to all aftershocks
		// without this fix (and corresponding check in fetchEvent), events such as usp000fuse will fail
		return eventToObsRup(event, event.getLongitude().doubleValue() < -90, true);
	}




	// Convert a JsonEvent into an ObsEqkRupture.
	// If wrapLon is false, longitudes range from -180 to +180.
	// If wrapLon is true, longitudes range from 0 to +360.
	// If extendedInfo is true, extended information is added to the ObsEqkRupture,
	//  which presently is the place description.
	// The return value can be null if the conversion could not be performed.
	
	private static ObsEqkRupture eventToObsRup(JsonEvent event, boolean wrapLon, boolean extendedInfo) {
		double lat = event.getLatitude().doubleValue();
		double lon = event.getLongitude().doubleValue();
		GeoTools.validateLon(lon);
		if (wrapLon && lon < 0.0) {
			lon += 360.0;
			GeoTools.validateLon(lon);
		}
		double dep = event.getDepth().doubleValue();
		if (dep < 0.0) {
			// some regional networks can report negative depths, but the definition of what they're relative to can vary between
			// networks (see http://earthquake.usgs.gov/data/comcat/data-eventterms.php#depth) so we decided to just discard any
			// negative depths rather than try to correct with a DEM (which may be inconsistant with the networks). More discussion
			// in e-mail thread 2/8-9/17 entitled "ComCat depths in OAF app"
			dep = 0.0;
		}
		Location hypo = new Location(lat, lon, dep);
		double mag=0.0;
		try{
			mag = event.getMag().doubleValue();
		}catch(Exception e){
			System.out.println(event.toString());
			return null;
		}
		ObsEqkRupture rup = new ObsEqkRupture(event.getEventId().toString(),
				event.getTime().getTime(), hypo, mag);
		
		if (extendedInfo) {
			// adds the place description ("10km from wherever"). Needed for ETAS_AftershockStatistics forecast document -NVDE 
			rup.addParameter(new StringParameter("description", event.getPlace()));
		}
		
		return rup;
	}

}
