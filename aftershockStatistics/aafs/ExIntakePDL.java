package scratch.aftershockStatistics.aafs;

import java.util.List;

import scratch.aftershockStatistics.aafs.entity.PendingTask;
import scratch.aftershockStatistics.aafs.entity.LogEntry;
import scratch.aftershockStatistics.aafs.entity.CatalogSnapshot;
import scratch.aftershockStatistics.aafs.entity.TimelineEntry;
import scratch.aftershockStatistics.aafs.entity.AliasFamily;

import scratch.aftershockStatistics.util.MarshalReader;
import scratch.aftershockStatistics.util.MarshalWriter;
import scratch.aftershockStatistics.util.SimpleUtils;

import scratch.aftershockStatistics.ComcatException;
import scratch.aftershockStatistics.CompactEqkRupList;

/**
 * Execute task: Shutdown.
 * Author: Michael Barall 06/25/2018.
 */
public class ExIntakePDL extends ServerExecTask {


	//----- Task execution -----


	// Execute the task, called from the task dispatcher.
	// The parameter is the task to execute.
	// The return value is a result code.
	// Support functions, task context, and result functions are available through the server group.

	@Override
	public int exec_task (PendingTask task) {
		return exec_intake_pdl (task);
	}




	// Intake an event for PDL.

	private int exec_intake_pdl (PendingTask task) {

		// Convert event ID to timeline ID if needed

		//int etres = sg.timeline_sup.intake_event_id_to_timeline_id (task);
		int etres = intake_pdl_event_id_to_timeline_id (task);
		if (etres != RESCODE_SUCCESS) {
			return etres;
		}

		//--- Get payload and timeline status

		OpIntakePDL payload = new OpIntakePDL();
		TimelineStatus tstatus = new TimelineStatus();

		int rescode = sg.timeline_sup.open_timeline (task, tstatus, payload);

		switch (rescode) {

		case RESCODE_TIMELINE_EXISTS:
			return RESCODE_DELETE;		// Just delete, so that log is not flooded with PDL notifications

		case RESCODE_TIMELINE_NOT_FOUND:
			break;

		default:
			return rescode;
		}

		//--- Mainshock data

		// Get mainshock parameters

		ForecastMainshock fcmain = new ForecastMainshock();

		try {
			sg.alias_sup.get_mainshock_for_timeline_id_ex (task.get_event_id(), fcmain);
		}

		// An exception here triggers a ComCat retry

		catch (ComcatException e) {
			return sg.timeline_sup.intake_setup_comcat_retry (task, e);
		}

		//--- Intake check

		// Search intake regions, using the minimum magnitude criterion

		IntakeSphRegion intake_region = sg.task_disp.get_action_config().get_pdl_intake_region_for_min_mag (
			fcmain.mainshock_lat, fcmain.mainshock_lon, fcmain.mainshock_mag);

		if (intake_region == null) {

			// Didn't pass, check the original PDL values using the minimum magnitude criterion

			intake_region = sg.task_disp.get_action_config().get_pdl_intake_region_for_min_mag (
				payload.mainshock_lat, payload.mainshock_lon, payload.mainshock_mag);

			// If none, then drop the event

			if (intake_region == null) {
				return RESCODE_DELETE;		// Just delete, so that log is not flooded with PDL notifications
			}

			// Now search intake regions, using the intake magnitude criterion

			intake_region = sg.task_disp.get_action_config().get_pdl_intake_region_for_intake_mag (
				fcmain.mainshock_lat, fcmain.mainshock_lon, fcmain.mainshock_mag);

			// If none, then drop the event

			if (intake_region == null) {
				return RESCODE_DELETE;		// Just delete, so that log is not flooded with PDL notifications
			}
		}

		//--- Final steps

		// Set track state
			
		tstatus.set_state_track (
			sg.task_disp.get_time(),
			sg.task_disp.get_action_config(),
			task.get_event_id(),
			fcmain,
			TimelineStatus.FCORIG_PDL,
			TimelineStatus.FCSTAT_ACTIVE_INTAKE);

		// If the command contains analyst data, save it

		if (payload.analyst_options != null) {
			tstatus.set_analyst_data (payload.analyst_options);
		}

		// Write the new timeline entry

		sg.timeline_sup.append_timeline (task, tstatus);

		// Log the task

		return RESCODE_SUCCESS;
	}




	// Convert event ID to timeline ID for an intake PDL command.
	// Returns values:
	//  RESCODE_SUCCESS = The task already contains a timeline ID.
	//  RESCODE_STAGE = The task is being staged for retry, either to start over with
	//    a timeline ID in place of an event ID, or to retry a failed Comcat operation.
	//  RESCODE_INTAKE_COMCAT_FAIL = Comcat retries exhausted, the command has failed.
	//  RESCODE_DELETE = The task should be deleted without being logged.
	//  RESCODE_TASK_CORRUPT = The task payload is corrupted.
	// Note: This function is the same as TimelineSupport.intake_event_id_to_timeline_id,
	// except that it checks the intake filter.

	private int intake_pdl_event_id_to_timeline_id (PendingTask task) {

		// If the task already contains a timeline ID, just return

		if (sg.alias_sup.is_timeline_id (task.get_event_id())) {
			return RESCODE_SUCCESS;
		}

		//--- Get payload

		OpIntakePDL payload = new OpIntakePDL();

		try {
			payload.unmarshal_task (task);
		}

		// Invalid task

		catch (Exception e) {

			// Display the error and log the task

			sg.task_sup.display_invalid_task (task, e);
			return RESCODE_TASK_CORRUPT;
		}

		//--- Mainshock data

		// Get mainshock parameters for an event ID

		ForecastMainshock fcmain = new ForecastMainshock();

		int retval;

		try {
			retval = sg.alias_sup.get_mainshock_for_event_id (task.get_event_id(), fcmain);

			if (retval == RESCODE_ALIAS_NOT_IN_COMCAT) {
				throw new ComcatException ("ExIntakePDL.intake_pdl_event_id_to_timeline_id: Comcat does not recognize event ID: " + task.get_event_id());
			}
		}

		// Handle Comcat exception, which includes event ID not found

		catch (ComcatException e) {
			return sg.timeline_sup.intake_setup_comcat_retry (task, e);
		}

		//--- Intake check

		// Search intake regions, using the minimum magnitude criterion

		IntakeSphRegion intake_region = sg.task_disp.get_action_config().get_pdl_intake_region_for_min_mag (
			fcmain.mainshock_lat, fcmain.mainshock_lon, fcmain.mainshock_mag);

		if (intake_region == null) {

			// Didn't pass, check the original PDL values using the minimum magnitude criterion

			intake_region = sg.task_disp.get_action_config().get_pdl_intake_region_for_min_mag (
				payload.mainshock_lat, payload.mainshock_lon, payload.mainshock_mag);

			// If none, then drop the event

			if (intake_region == null) {
				return RESCODE_DELETE;		// Just delete, so that log is not flooded with PDL notifications
			}

			// Now search intake regions, using the intake magnitude criterion

			intake_region = sg.task_disp.get_action_config().get_pdl_intake_region_for_intake_mag (
				fcmain.mainshock_lat, fcmain.mainshock_lon, fcmain.mainshock_mag);

			// If none, then drop the event

			if (intake_region == null) {
				return RESCODE_DELETE;		// Just delete, so that log is not flooded with PDL notifications
			}
		}

		//--- Final steps

		// If the event ID has not been seen before, create the alias timeline

		if (retval == RESCODE_ALIAS_NEW_EVENT) {
			sg.alias_sup.write_mainshock_to_new_timeline (fcmain);
		}

		// Stage the task, using the timeline ID in place of the event ID, for immediate execution

		sg.task_disp.set_taskres_stage (sg.task_sup.get_prompt_exec_time(),		// could use EXEC_TIME_MIN_WAITING
										task.get_stage(),
										fcmain.timeline_id);
		return RESCODE_STAGE;
	}




	//----- Construction -----


	// Default constructor.

	public ExIntakePDL () {}

}
