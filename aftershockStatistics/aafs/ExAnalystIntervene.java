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
public class ExAnalystIntervene extends ServerExecTask {


	//----- Task execution -----


	// Execute the task, called from the task dispatcher.
	// The parameter is the task to execute.
	// The return value is a result code.
	// Support functions, task context, and result functions are available through the server group.

	@Override
	public int exec_task (PendingTask task) {
		return exec_analyst_intervene (task);
	}




	// Analyst intervention.

	private int exec_analyst_intervene (PendingTask task) {

		//--- Get payload and timeline status

		OpAnalystIntervene payload = new OpAnalystIntervene();
		TimelineStatus tstatus = new TimelineStatus();

		int rescode = sg.timeline_sup.open_timeline (task, tstatus, payload);

		switch (rescode) {

		case RESCODE_TIMELINE_EXISTS:

			// If request to start generating forecasts ...

			if (payload.state_change == OpAnalystIntervene.ASREQ_START && tstatus.can_analyst_start()) {

				// Analyst intervention

				tstatus.set_state_analyst_intervention (sg.task_disp.get_time());

				// Update the state
			
				tstatus.set_fc_status (TimelineStatus.FCSTAT_ACTIVE_NORMAL);

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

			// If request to stop generating forecasts ...

			if (payload.state_change == OpAnalystIntervene.ASREQ_STOP && tstatus.can_analyst_stop()) {

				// Analyst intervention

				tstatus.set_state_analyst_intervention (sg.task_disp.get_time());

				// Update the state
			
				tstatus.set_fc_status (TimelineStatus.FCSTAT_STOP_ANALYST);

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

			// If request to set analyst data with no change in state

			if (payload.f_has_analyst && tstatus.can_analyst_update()) {

				// Analyst intervention
			
				tstatus.set_state_analyst_intervention (sg.task_disp.get_time());

				// Save analyst data

				tstatus.set_analyst_data  (
					payload.analyst_id,
					payload.analyst_remark,
					payload.analyst_time,
					payload.analyst_params,
					payload.extra_forecast_lag);

				// Write the new timeline entry

				sg.timeline_sup.append_timeline (task, tstatus);

				// Log the task

				return RESCODE_TIMELINE_ANALYST_SET;
			}

			if (payload.f_has_analyst) {
				return RESCODE_TIMELINE_ANALYST_FAIL;
			}

			return RESCODE_TIMELINE_ANALYST_NONE;

		case RESCODE_TIMELINE_NOT_FOUND:
			break;

		default:
			return rescode;
		}

		//--- Mainshock data

		// If not requesting timeline creation, just return

		if (!( payload.f_create_timeline )) {
			return RESCODE_TIMELINE_ANALYST_NONE;
		}

		// Fetch parameters, part 1 (control and mainshock parameters)

		ForecastParameters forecast_params = new ForecastParameters();

		try {
			forecast_params.fetch_all_1 (task.get_event_id(), payload.get_eff_analyst_params());
		}

		// An exception here triggers a ComCat retry

		catch (Exception e) {

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
		
			sg.task_disp.set_display_taskres_log ("TASK-ERR: Analyst intervention failed due to ComCat failure:\n"
				+ "event_id = " + task.get_event_id() + "\n"
				+ "Stack trace:\n" + SimpleUtils.getStackTraceAsString(e));

			return RESCODE_INTAKE_COMCAT_FAIL;
		}

		//--- Final steps

		// Set track state
			
		tstatus.set_state_track (
			sg.task_disp.get_time(),
			sg.task_disp.get_action_config(),
			task.get_event_id(),
			forecast_params.mainshock_time,
			TimelineStatus.FCORIG_ANALYST,
			TimelineStatus.FCSTAT_ACTIVE_NORMAL);

		// If request to stop sending forecasts, create timeline in the stopped state

		if (payload.state_change == OpAnalystIntervene.ASREQ_STOP) {
			tstatus.set_fc_status (TimelineStatus.FCSTAT_STOP_ANALYST);
		}

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

	public ExAnalystIntervene () {}

}
