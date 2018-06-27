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

		// Fetch parameters, part 1 (control and mainshock parameters)

		ForecastParameters forecast_params = new ForecastParameters();

		try {
			forecast_params.fetch_all_1 (task.get_event_id(), payload.get_eff_analyst_params());
		}

		// An exception here triggers a ComCat retry

		catch (Exception e) {

			// Delete any other PDL intake commands for this event, so we don't have multiple retries going on

			sg.task_sup.delete_all_waiting_tasks (task.get_event_id(), OPCODE_INTAKE_PDL);

			// Get the next ComCat retry lag

			long next_comcat_intake_lag = sg.task_disp.get_action_config().get_next_comcat_intake_lag (
											sg.task_disp.get_action_config().int_to_lag (task.get_stage()) + 1L );

			// If there is another retry, stage the task

			if (next_comcat_intake_lag >= 0L) {
				sg.task_disp.set_taskres_stage (task.get_sched_time() + next_comcat_intake_lag,
									sg.task_disp.get_action_config().lag_to_int (next_comcat_intake_lag));
				return RESCODE_STAGE;
			}

			// Retries exhausted, display the error and log the task
		
			sg.task_disp.set_display_taskres_log ("TASK-ERR: Event intake failed due to ComCat failure:\n"
				+ "event_id = " + task.get_event_id() + "\n"
				+ "Stack trace:\n" + SimpleUtils.getStackTraceAsString(e));

			return RESCODE_INTAKE_COMCAT_FAIL;
		}

		//--- Intake check

		// Search intake regions, using the minimum magnitude criterion

		IntakeSphRegion intake_region = sg.task_disp.get_action_config().get_pdl_intake_region_for_min_mag (
			forecast_params.mainshock_lat, forecast_params.mainshock_lon, forecast_params.mainshock_mag);

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
				forecast_params.mainshock_lat, forecast_params.mainshock_lon, forecast_params.mainshock_mag);

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
			forecast_params.mainshock_time,
			TimelineStatus.FCORIG_PDL,
			TimelineStatus.FCSTAT_ACTIVE_INTAKE);

		// If the command contains analyst data, save it

		if (payload.f_has_analyst) {
			tstatus.set_analyst_data  (
				payload.analyst_id,
				payload.analyst_remark,
				payload.analyst_time,
				payload.analyst_params,
				payload.extra_forecast_lag);
		}

		// Write the new timeline entry

		sg.timeline_sup.append_timeline (task, tstatus);

		// Log the task

		return RESCODE_SUCCESS;
	}




	//----- Construction -----


	// Default constructor.

	public ExIntakePDL () {}

}
