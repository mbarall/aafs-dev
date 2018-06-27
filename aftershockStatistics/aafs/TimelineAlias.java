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
 * Alias functions for timelines.
 * Author: Michael Barall 06/26/2018.
 *
 * This class contains functions for maintaining the mapping from Comcat IDs to timeline IDs.
 */
public class TimelineAlias extends ServerComponent {




	//----- Timeline ID -----
	//
	// Each timeline is assigned an ID that is distinct from any possible Comcat ID.
	// That ID is used as the "event ID" for the timeline.
	// This allows a timeline to retain a constant ID even if the Comcat IDs change.








	//----- Construction -----


	// Default constructor.

	public TimelineAlias () {}

}
