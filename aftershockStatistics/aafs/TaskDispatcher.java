package scratch.aftershockStatistics.aafs;

import java.util.List;

import java.io.StringWriter;
import java.io.PrintWriter;

import scratch.aftershockStatistics.aafs.entity.PendingTask;
import scratch.aftershockStatistics.aafs.entity.LogEntry;
import scratch.aftershockStatistics.aafs.entity.CatalogSnapshot;
import scratch.aftershockStatistics.aafs.entity.TimelineEntry;

import scratch.aftershockStatistics.util.MarshalImpArray;
import scratch.aftershockStatistics.util.MarshalReader;
import scratch.aftershockStatistics.util.MarshalWriter;

/**
 * Task dispatcher for AAFS server.
 * Author: Michael Barall 03/18/2018.
 *
 * This class pulls tasks off the pending task queue and executes them.
 * It can run within its own thread.
 */
public class TaskDispatcher implements Runnable {

	//----- Dispatcher state variables -----

	// The time the task dispatcher was started.
	// Note: This is true time.

	private long start_time = 0L;

	// get_start_time - Get the time the task dispatcher was started.

	public long get_start_time () {
		return start_time;
	}




	// The last time that the task dispatcher was active.
	// Note: This is true time.

	private volatile long active_time = 0L;

	// get_active_time - Return the last time the dispatcher was active, or a special value.
	// Note: Can be called from any thread.

	public long get_active_time () {
		return active_time;
	}




	// The time the task dispatcher was last restarted, or 0L if never restarted.
	// Note: This is true time.

	private long restart_time = 0L;




	// Current dispatcher state, one of the special values below.

	private int dispatcher_state = 0;

	public static final int STATE_INITIAL         = 0;	// Initial state, dispatcher not started yet.
	public static final int STATE_FIRST_CONNECT   = 1;	// Connecting to MongoDB for the first time.
	public static final int STATE_RECONNECTING    = 2;	// Re-connecting to MongoDB.
	public static final int STATE_PROCESSING      = 3;	// Processing task.
	public static final int STATE_WAITING         = 4;	// Waiting for next task.
	public static final int STATE_POLLING         = 5;	// Polling queue.
	public static final int STATE_SHUTDOWN        = 6;	// Normal shutdown.

	// get_dispatcher_state - Get the task dispatcher state.
	// Note: This should only be called after the dispatcher exits.
	// The final state should be STATE_SHUTDOWN for normal exit.
	// Any other final state identifies when the failure occurred.

	public int get_dispatcher_state () {
		return dispatcher_state;
	}




	// Effective time at which the current task began to execute.

	private long dispatcher_time = 0L;

	// Get the effective time at which the current task began to execute.

	public long get_dispatcher_time () {
		return dispatcher_time;
	}

	// True time at which the current task began to execute.

	private long dispatcher_true_time = 0L;

	// Get the true time at which the current task began to execute.

	public long get_dispatcher_true_time () {
		return dispatcher_true_time;
	}

	// Action configuration parameters for the current task.

	private ActionConfig dispatcher_action_config = null;

	// Get the action configuration parameters for the current task.

	public ActionConfig get_dispatcher_action_config () {
		return dispatcher_action_config;
	}




	// Time to insert in log entry for current task.
	// Defaults to dispatcher_time.

	private long taskres_log_time = 0L;

	// Remark to insert in log entry for current task.
	// Defaults to "".

	private String taskres_log_remark = "";

	// Execution time to use when staging current task.

	private long taskres_exec_time = 0L;

	// Stage to use when staging current task.

	private int taskres_stage = 0;




	//----- Dispatcher parameters -----

	// The polling delay, in milliseconds.

	private long polling_delay = 30000L;			// 30 seconds

	// The minimum delay before restarting after failure, in milliseconds.

	private long restart_delay_min = 20000L;		// 20 seconds

	// The maximum delay before restarting after failure, in milliseconds.

	private long restart_delay_max = 300000L;		// 5 minutes




	// Opcodes.

	public static final int OPCODE_MIN = 1;					// Minimum allowed opcode
	public static final int OPCODE_NO_OP = 1;				// No operation
	public static final int OPCODE_SHUTDOWN = 2;			// Shut down the server
	public static final int OPCODE_CON_MESSAGE = 3;			// Write message to console
	public static final int OPCODE_GEN_FORECAST = 4;		// Generate forecast
	public static final int OPCODE_GEN_PDL_REPORT = 5;		// Generate delayed forecast report to PDL
	public static final int OPCODE_GEN_EXPIRE = 6;			// Stop generation, mark the timeline expired
	public static final int OPCODE_MAX = 6;					// Maximum allowed opcode

	// Return a string describing an opcode.

	public String get_opcode_as_string (int x) {
		switch (x) {
		case OPCODE_NO_OP: return "OPCODE_NO_OP";
		case OPCODE_SHUTDOWN: return "OPCODE_SHUTDOWN";
		case OPCODE_CON_MESSAGE: return "OPCODE_CON_MESSAGE";
		case OPCODE_GEN_FORECAST: return "OPCODE_GEN_FORECAST";
		case OPCODE_GEN_PDL_REPORT: return "OPCODE_GEN_PDL_REPORT";
		case OPCODE_GEN_EXPIRE: return "OPCODE_GEN_EXPIRE";
		}
		return "OPCODE_INVALID(" + x + ")";
	}




	// Special execution times.

	public static final long EXEC_TIME_ACTIVE = 0L;			// Task is active
	public static final long EXEC_TIME_MIN_WAITING = 1L;	// Minimum execution time for waiting tasks
	public static final long EXEC_TIME_SHUTDOWN = 2L;		// Execution time for shutdown task
	public static final long EXEC_TIME_FAR_FUTURE = 1000000000000000L;	// 10^15 ~ 30,000 years




	// Result codes.

	public static final int RESCODE_MIN = 1;					// Minimum known result code
	public static final int RESCODE_SUCCESS = 1;				// Task completes successfully
	public static final int RESCODE_TASK_CORRUPT = 2;			// Task entry or payload was corrupted, task discarded
	public static final int RESCODE_TIMELINE_CORRUPT = 3;		// Timeline entry or payload was corrupted, task discarded
	public static final int RESCODE_TIMELINE_NOT_FOUND = 4;		// Timeline entry not found, task discarded
	public static final int RESCODE_TIMELINE_NOT_ACTIVE = 5;	// Timeline entry not active, task discarded
	public static final int RESCODE_TIMELINE_LAG_MISMATCH = 6;	// Timeline entry has lag values that do not match the forecast task
	public static final int RESCODE_TIMELINE_COMCAT_FAIL = 7;	// Timeline stopped due to ComCat failure
	public static final int RESCODE_TIMELINE_WITHDRAW = 8;		// Timeline stopped due to withdrawal of event at first forecast
	public static final int RESCODE_TIMELINE_FORESHOCK = 9;		// Timeline stopped because event was found to be a foreshock
	public static final int RESCODE_MAX = 9;					// Maximum known result code

	public static final int RESCODE_DELETE = -1;				// Special result code: delete current task (without logging it)
	public static final int RESCODE_STAGE = -2;					// Special result code: stage current task (execute it again)

	// Return a string describing an result code.

	public String get_rescode_as_string (int x) {
		switch (x) {
		case RESCODE_SUCCESS: return "RESCODE_SUCCESS";
		case RESCODE_TASK_CORRUPT: return "RESCODE_TASK_CORRUPT";
		case RESCODE_TIMELINE_CORRUPT: return "RESCODE_TIMELINE_CORRUPT";
		case RESCODE_TIMELINE_NOT_FOUND: return "RESCODE_TIMELINE_NOT_FOUND";
		case RESCODE_TIMELINE_NOT_ACTIVE: return "RESCODE_TIMELINE_NOT_ACTIVE";
		case RESCODE_TIMELINE_LAG_MISMATCH: return "RESCODE_TIMELINE_LAG_MISMATCH";
		case RESCODE_TIMELINE_COMCAT_FAIL: return "RESCODE_TIMELINE_COMCAT_FAIL";
		case RESCODE_TIMELINE_WITHDRAW: return "RESCODE_TIMELINE_WITHDRAW";
		case RESCODE_TIMELINE_FORESHOCK: return "RESCODE_TIMELINE_FORESHOCK";

		case RESCODE_DELETE: return "RESCODE_DELETE";
		case RESCODE_STAGE: return "RESCODE_STAGE";
		}
		return "RESCODE_INVALID(" + x + ")";
	}




	// Special event ids

	public static final String EVID_SHUTDOWN = "===shutdown===";	// Shutdown task




	// Special submit ids

	public static final String SUBID_AAFS = "AAFS";		// Automatic system




	//----- Task posting functions -----




	/**
	 * post_task - Post a task.
	 * @param event_id = Event associated with this task, or "" if none. Cannot be null.
	 * @param sched_time = Time at which task should execute, in milliseconds
	 *                     since the epoch. Must be positive.
	 * @param submit_time = Time at which the task is submitted, in milliseconds
	 *                      since the epoch. Must be positive.
	 * @param submit_id = Person or entity submitting this task. Cannot be empty or null.
	 * @param opcode = Operation code used to dispatch the task.
	 * @param stage = Stage number, user-defined, effectively an extension of the opcode.
	 * @param details = Further details of this task. Can be null if there are none.
	 * @return
	 * Returns true if task is successfully posted, false if error.
	 * This function connects to MongoDB, puts the task on the queue, and then
	 * disconnects from MongoDB.
	 * If already connected to MongoDB, then use PendingTask.submit_task instead.
	 */
	public static boolean post_task (String event_id, long sched_time, long submit_time,
								String submit_id, int opcode, int stage, MarshalWriter details) {

		boolean result = true;

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Post the command

			PendingTask.submit_task (event_id, sched_time, submit_time,
									submit_id, opcode, stage, details);

			// Normal return

			result = true;

		// Abnormal return

        } catch (Exception e) {
			result = false;
            e.printStackTrace();
        } catch (Throwable e) {
			result = false;
            e.printStackTrace();
        }

		return result;
	}




	/**
	 * post_shutdown - Post a shutdown task to the queue.
	 * @param submit_id = Person or entity submitting this task. Cannot be empty or null.
	 * @return
	 * Returns true if shutdown is successfully posted, false if error.
	 */
	public static boolean post_shutdown (String submit_id) {

		boolean result = post_task (EVID_SHUTDOWN, EXEC_TIME_SHUTDOWN, ServerClock.get_true_time(),
								submit_id, OPCODE_SHUTDOWN, 0, null);

		return result;
	}




	//----- Task dispatching functions -----




	// Run the task dispatcher.

	@Override
	public void run() {

		// State = first connection

		dispatcher_state = STATE_FIRST_CONNECT;

		// Set up timers

		start_time = ServerClock.get_true_time();
		active_time = start_time;
		restart_time = start_time - restart_delay_max;

		// Restart loop, continue until shutdown or failure

		for (;;) {

			// Active task, null if none

			PendingTask task = null;

			// Connect to MongoDB

			try (
				MongoDBUtil mongo_instance = new MongoDBUtil();
			){

				// If first connection ...

				if (dispatcher_state == STATE_FIRST_CONNECT) {
				
					// Remove any shutdown commands from the task queue

					List<PendingTask> shutdown_tasks = PendingTask.get_task_entry_range (0L, 0L, EVID_SHUTDOWN);

					for (PendingTask shutdown_task : shutdown_tasks) {
						if (shutdown_task.get_opcode() == OPCODE_SHUTDOWN) {
							PendingTask.delete_task (shutdown_task);
						}
					}
				}

				// Polling loop, continue until shutdown or exception

				while (dispatcher_state != STATE_SHUTDOWN) {

					// State = polling

					dispatcher_state = STATE_POLLING;

					// Get task time and configuration

					dispatcher_time = ServerClock.get_time();
					dispatcher_true_time = ServerClock.get_true_time();
					dispatcher_action_config = new ActionConfig();

					// Record the dispatcher active time

					active_time = dispatcher_true_time;

					// Get the next task on the pending queue, that's ready to execute, and activate it

					long cutoff_time = dispatcher_time;
					task = PendingTask.activate_first_ready_task (cutoff_time);

					// Check if there is a task

					if (task == null) {

						// State = waiting

						dispatcher_state = STATE_WAITING;

						// If none, wait for the polling delay

						try {
							Thread.sleep(polling_delay);
						} catch (InterruptedException e) {
						}

					} else {

						// State = processing

						dispatcher_state = STATE_PROCESSING;

						// Dispatch on opcode

						dispatch_task (task);

						// No active task

						task = null;
					}
				}

			// Operation failed with exception

			} catch (Exception e) {
				e.printStackTrace();
				if (task != null) {
					System.err.println ("Failing task: " + task.toString());
				}
			} catch (Throwable e) {
				e.printStackTrace();
				if (task != null) {
					System.err.println ("Failing task: " + task.toString());
				}
			}

			// If normal shutdown, exit the restart loop

			if (dispatcher_state == STATE_SHUTDOWN) {
				break;
			}

			// If first connection failed, exit the restart loop
			// (because this might be a configuration error)

			if (dispatcher_state == STATE_FIRST_CONNECT) {
				break;
			}

			// Calculate restart delay:
			// restart time is restart_delay_min after the current time,
			// or restart_delay_max after the last restart, whichever is later

			long restart_delay = Math.max (restart_delay_min,
						restart_delay_max + restart_time - ServerClock.get_true_time());

			// Wait until time for restart

			try {
				Thread.sleep(restart_delay);
			} catch (InterruptedException e) {
			}

			// New restart time

			restart_time = ServerClock.get_true_time();

			// State = reconnecting
		
			dispatcher_state = STATE_RECONNECTING;

		}

		return;
	}




	/**
	 * run_next_task - Run the next task.
	 * @param f_verbose = True to display the task to System.out.
	 * @return
	 * Returns true if task is executed, false if task queue is empty.
	 * This function connects to MongoDB, executes the next task on the queue, and then
	 * disconnects from MongoDB.
	 * This function also sets the clock to the task execution time.
	 */
	public boolean run_next_task (boolean f_verbose) {

		boolean result = true;

		// Active task, null if none

		PendingTask task = null;

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get task time and configuration

			dispatcher_true_time = ServerClock.get_true_time();
			dispatcher_action_config = new ActionConfig();

			// Get the next task on the pending queue, and activate it

			long cutoff_time = EXEC_TIME_FAR_FUTURE;
			task = PendingTask.activate_first_ready_task (cutoff_time);

			// Check if there is a task

			if (task == null) {

				// No task

				result = false;

				// If verbose, write message

				if (f_verbose) {
					System.out.println ("No task available");
				}

			} else {

				// Got a task

				result = true;

				// Adjust the time

				ServerClock.advance_frozen_time (task.get_apparent_time());
				dispatcher_time = ServerClock.get_time();

				// If verbose, write message

				if (f_verbose) {
					System.out.println ("Executing task: " + task.toString());
				}

				// Dispatch on opcode

				dispatch_task (task);

				// No active task

				task = null;
			}

		// Abnormal return

        } catch (Exception e) {
            e.printStackTrace();
			if (task != null) {
				System.err.println ("Failing task: " + task.toString());
			}
        } catch (Throwable e) {
            e.printStackTrace();
			if (task != null) {
				System.err.println ("Failing task: " + task.toString());
			}
        }

		return result;
	}




	// Dispatch task based on its opcode.
	//
	// Each task must have an execution function that takes the task as its argument.
	// On entry to the execution function, the following are set:
	//   dispatcher_time = Effective time at which task is beginning to execute.
	//   dispatcher_true_time = True clock time at which task is beginning to execute.
	//   dispatcher_action_config = Configuration parameters.
	//   taskres_log_time = dispatcher_time.
	//   taskres_log_remark = "".
	// The execution function must return a result code (RESCODE_XXXXX).
	//   RESCODE_DELETE deletes the current task, with no other action.
	//   RESCODE_STAGE stages the current task, using taskres_exec_time and taskres_stage.
	//   Any other return writes a log entry with the given result code, using
	//     taskres_log_time and taskres_log_remark; and then deletes the current task.
	// If the task is being restarted and a log entry has already been written,
	//   then the task is deleted and the execution function is not called.

	private void dispatch_task (PendingTask task) {

		// If restarting ...

		if (task.is_restarted()) {
		
			// If we wrote a log entry ...

			if (LogEntry.get_log_entry_for_key (task.get_record_key()) != null) {
			
				// Just remove the task from the queue

				PendingTask.delete_task (task);
				return;
			}
		}

		// Establish task context
		// (Note that dispatcher_time, dispatcher_true_time, and dispatcher_action_config
		// are established by our caller)

		taskres_log_time = dispatcher_time;
		taskres_log_remark = "";

		// Invoke the execution function, depending on the opcode

		int rescode;

		switch (task.get_opcode()) {

		default: rescode = exec_unknown (task); break;
				
		case OPCODE_NO_OP: rescode = exec_no_op (task); break;

		case OPCODE_SHUTDOWN: rescode = exec_shutdown (task); break;

		case OPCODE_CON_MESSAGE: rescode = exec_con_message (task); break;

		case OPCODE_GEN_FORECAST: rescode = exec_gen_forecast (task); break;

		}

		// Handle task disposition, depending on the result code

		switch (rescode) {

		default:

			// Log the task

			LogEntry.submit_log_entry (task, taskres_log_time, rescode, taskres_log_remark);

			// Remove the task from the queue

			PendingTask.delete_task (task);

			break;

		case RESCODE_DELETE:

			// Remove the task from the queue

			PendingTask.delete_task (task);

			break;

		case RESCODE_STAGE:

			// Stage the task, so it will execute again

			PendingTask.stage_task (task, taskres_exec_time, taskres_stage);

			break;
		}

		return;
	}




	//----- Task execution subroutines : General task management -----




	// Set the remark to be used in a log entry, during task disposition.

	public void set_taskres_log (String log_remark) {
		taskres_log_remark = log_remark;
		return;
	}




	// Set and display the remark to be used in a log entry, during task disposition.

	public void set_display_taskres_log (String log_remark) {
		taskres_log_remark = log_remark;
		System.err.println (log_remark);
		return;
	}




	// Set the time and remark to be used in a log entry, during task disposition.

	public void set_taskres_log (long log_time, String log_remark) {
		taskres_log_time = log_time;
		taskres_log_remark = log_remark;
		return;
	}




	// Set the execution time and stage to be used when staging a task, during task disposition.

	public void set_taskres_stage (long exec_time, int stage) {
		taskres_exec_time = exec_time;
		taskres_stage = stage;
		return;
	}




	// Get a stack trace as a string.

	public static String getStackTraceAsString (Throwable e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw, true);
		e.printStackTrace(pw);
		return sw.getBuffer().toString();
	}




	// Display an exception caused by an invalid task, and set the log remark.

	public void display_invalid_task (PendingTask task, Exception e) {

		// Display messages

		e.printStackTrace();
		System.err.println ("Invalid task: " + task.toString() + "\nDump:\n" + task.dump_details());

		// Insert the stack trace into the log remark

		set_taskres_log (getStackTraceAsString(e));
		return;
	}




	// Delete all waiting tasks with the given event id and any of the given opcodes.
	// Note: The currently active task is not deleted, even if it would match.

	public void delete_all_waiting_tasks (String event_id, int... opcodes) {

		// Get a list of all waiting tasks for the given event

		List<PendingTask> tasks = PendingTask.get_task_entry_range (EXEC_TIME_MIN_WAITING, 0L, event_id);

		// Delete tasks with the given opcode

		for (PendingTask task : tasks) {
			for (int opcode : opcodes) {
				if (task.get_opcode() == opcode) {
					PendingTask.delete_task (task);
					break;
				}
			}
		}
	
		return;
	}




	// Display informational message, during task execution.

	public void display_taskinfo (String info) {
		System.out.println (info);
		return;
	}




	//----- Task execution subroutines : Timeline operations -----




//	// Display a message for timeline not found.  (No neeed to set the log remark.)
//
//	public void display_timeline_not_found (PendingTask task) {
//
//		// Display message
//
//		System.err.println ("Timeline entry not found: event_id = " + task.get_event_id());
//
//		return;
//	}




	// Delete all waiting tasks with the given event id that are delayed timeline actions.
	// Note: The currently active task is not deleted, even if it would match.

	public void delete_delayed_timeline_tasks (String event_id) {
		delete_all_waiting_tasks (event_id, OPCODE_GEN_FORECAST, OPCODE_GEN_PDL_REPORT, OPCODE_GEN_EXPIRE);
		return;
	}




	// Display an exception caused by a corrupt timeline entry, and set the log remark.
	// Also, if the timeline entry is not an error entry, then write an error entry.

	public void display_timeline_corrupt (TimelineEntry tentry, PendingTask task, Exception e) {

		// Display messages
		
		set_display_taskres_log ("TASK-ERR: Timeline entry is corrupt:\n"
			+ "event_id = " + task.get_event_id() + "\n"
			+ "Timeline entry synopsis:\n" + tentry.toString() + "\n"
			+ "Timeline entry details dump:\n" + tentry.dump_details() + "\n"
			+ "Stack trace:\n" + getStackTraceAsString(e));

		// If the timeline entry is not marked as error, then add a new timeline entry

		if (tentry.get_actcode() != TimelineStatus.ACTCODE_ERROR) {
			TimelineEntry.submit_timeline_entry (
				task.get_record_key(),										// key
				Math.max(dispatcher_time, tentry.get_action_time() + 1L),	// action_time
				tentry.get_event_id(),										// event_id
				TimelineStatus.ACTCODE_ERROR,								// actcode
				null);														// details
		}

		return;
	}




	// Display an exception caused by a ComCat failure, and set the log remark.
	// Also, the timeline entry is set to the ComCat fail state, and written to the database.

	public void display_timeline_comcat_fail (PendingTask task, TimelineStatus tstatus, Exception e) {

		// Display messages
		
		set_display_taskres_log ("TASK-ERR: Timeline stopped due to ComCat failure:\n"
			+ "event_id = " + task.get_event_id() + "\n"
			+ "Stack trace:\n" + getStackTraceAsString(e));

		// Set to ComCat fail state

		tstatus.set_state_comcat_fail (dispatcher_time);

		// Write timeline entry

		append_timeline (task, tstatus);
		return;
	}




	// Append an entry to the timeline.
	// The entry is tagged with the key from the current task.

	public void append_timeline (PendingTask task, TimelineStatus tstatus) {

		// Write timeline entry

		TimelineEntry.submit_timeline_entry (
			task.get_record_key(),				// key
			tstatus.action_time,				// action_time
			tstatus.event_id,					// event_id
			tstatus.actcode,					// actcode
			tstatus.marshal_timeline());		// details

		// Display message
		
		display_taskinfo ("TASK-INFO: Timeline appended:\n"
			+ "event_id = " + tstatus.event_id + "\n"
			+ "actcode = " + tstatus.get_actcode_as_string () + "\n"
			+ "action_time = " + tstatus.action_time + "\n"
			+ "fc_status = " + tstatus.get_fc_status_as_string ());

		return;
	}




//	// Display a message for timeline not active.  (No neeed to set the log remark.)
//
//	public void display_timeline_not_active (PendingTask task, TimelineStatus tstatus) {
//
//		// Display message
//
//		System.err.println ("Timeline entry not active: event_id = " + task.get_event_id() + ", fc_status = " + tstatus.fc_status);
//
//		return;
//	}




	// Determine the next forecast lag for a timeline entry.
	// Parameters:
	//  tstatus = Timeline status.
	// Returns -1L if there is no next forecast lag.
	// If there is a next forecast lag, the return value is
	// positive and a multiple of the lag unit.
	// Note: Forecast lag is relative to the mainshock origin time.
	// Note: This does not consider the possibility of a PDL report.

	public long get_next_forecast_lag (TimelineStatus tstatus) {

		long next_forecast_lag = -1L;

		// If the timeline state requests a forecast ...

		if (tstatus.is_forecast_state()) {

			long min_lag;
			long min_extra_lag;

			// Minimum lag is 0L if this is the first forecast,
			// otherwise it is a minimum spacing after the last lag

			if (tstatus.last_forecast_lag < 0L) {
				min_lag = 0L;
				min_extra_lag = 0L;
			} else {
				min_lag = tstatus.last_forecast_lag + dispatcher_action_config.get_forecast_min_gap();
				min_extra_lag = tstatus.last_forecast_lag;
			}

			// Get next forecast lag from configured schedule

			next_forecast_lag = dispatcher_action_config.get_next_forecast_lag (min_lag);

			// If no next forecast lag on the configured schedule ...

			if (next_forecast_lag < 0L) {
			
				// Use the requested extra forecast lag, if any

				if (tstatus.extra_forecast_lag >= 0L) {

					// Make sure the value is a multiple of the lag unit, and greater than the last lag

					next_forecast_lag = 
						dispatcher_action_config.floor_unit_lag (tstatus.extra_forecast_lag, min_extra_lag);
				} else {
					next_forecast_lag = -1L;
				}
			}

			// Otherwise, we have a forecast lag from the schedule ...

			else {
			
				// If there is a requested extra forecast lag ...

				if (tstatus.extra_forecast_lag >= 0L) {

					// Use the smaller of the scheduled and extra lags

					next_forecast_lag = Math.min (next_forecast_lag,
						dispatcher_action_config.floor_unit_lag (tstatus.extra_forecast_lag, min_extra_lag));
				}
			}
		}

		// Return next lag

		return next_forecast_lag;
	}




	// Determine the next PDL report stage for a timeline entry.
	// Parameters:
	//  tstatus = Timeline status.
	//  next_forecast_lag = Scheduled time of the next forecast, or -1L if none.
	//  last_pdl_lag = Lag for the last PDL report attempt, or -1L if none.
	//	base_pdl_time = Start time for the PDL report sequence.
	// Returns -1L if there is no next PDL report lag.
	// If there is a next PDL report lag, the return value is
	// non-negative, a multiple of the lag unit, and greather than last_pdl_lag.
	// Note: PDL report lag is relative to the start time of the PDL report sequence.
	// Note: If last_pdl_lag == -1L and there is a next PDL report lag, then the return value is 0L.

	public long get_next_pdl_lag (TimelineStatus tstatus, long next_forecast_lag, long last_pdl_lag, long base_pdl_time) {

		long next_pdl_lag = -1L;

		// If the timeline state requests a PDL retry ...

		if (tstatus.is_pdl_retry_state()) {

			// Get next PDL lag from the schedule, must be > last_pdl_lag

			if (last_pdl_lag >= 0L) {
				next_pdl_lag = dispatcher_action_config.get_next_pdl_report_retry_lag (last_pdl_lag + 1L);
			} else {
				next_pdl_lag = 0L;
			}

			// If there is another lag in the schedule ...

			if (next_pdl_lag >= 0L) {

				// This will be the PDL time ceiling (the retry must occur before this time)

				long pdl_time_ceiling = Long.MAX_VALUE;

				// If there is a next forecast, limit to a time shortly before the forecast

				if (next_forecast_lag >= 0L) {
					pdl_time_ceiling = Math.min (pdl_time_ceiling,
						tstatus.last_mainshock_time + next_forecast_lag - dispatcher_action_config.get_forecast_min_gap());
				}

				// If there is a previous forecast (should always be), limit to a maximum time after it

				if (tstatus.last_forecast_lag >= 0L) {
					pdl_time_ceiling = Math.min (pdl_time_ceiling,
						tstatus.last_mainshock_time + tstatus.last_forecast_lag + dispatcher_action_config.get_forecast_max_delay());
				}

				// Kill the PDL retry if it would not occur before the time ceiling
				// (The max below is the projected execution time of the PDL retry)

				if (Math.max (base_pdl_time + next_pdl_lag, dispatcher_time) >= pdl_time_ceiling) {
					next_pdl_lag = -1L;
				}

			} else {
				next_pdl_lag = -1L;
			}
		}

		// Return next stage

		return next_pdl_lag;
	}




	// Post the next automatic database command for a timeline entry.
	// Returns true if a task was posted.
	// This form is used when staging the current task is not considered.

	public boolean next_auto_timeline (TimelineStatus tstatus) {

		// Get the next forecast lag, or -1 if none

		long next_forecast_lag = get_next_forecast_lag (tstatus);

		// Get the next PDL report lag, or -1 if none, assuming the report begins now

		long next_pdl_lag = get_next_pdl_lag (tstatus, next_forecast_lag, -1L, dispatcher_time);

		return next_auto_timeline (tstatus, next_forecast_lag, next_pdl_lag);
	}


	public boolean next_auto_timeline (TimelineStatus tstatus, long next_forecast_lag, long next_pdl_lag) {

		// If a PDL report is desired, submit the task

		if (next_pdl_lag >= 0L) {
		
			OpGeneratePDLReport pdl_payload = new OpGeneratePDLReport();
			pdl_payload.setup (tstatus.last_forecast_lag, dispatcher_time);

			PendingTask.submit_task (
				tstatus.event_id,										// event id
				dispatcher_time + next_pdl_lag,							// sched_time
				dispatcher_time,										// submit_time
				SUBID_AAFS,												// submit_id
				OPCODE_GEN_PDL_REPORT,									// opcode
				dispatcher_action_config.lag_to_int (next_pdl_lag),		// stage
				pdl_payload.marshal_task());							// details

			return true;
		}

		// If a forecast is desired, submit the task

		if (next_forecast_lag >= 0L) {
		
			OpGenerateForecast forecast_payload = new OpGenerateForecast();
			forecast_payload.setup (tstatus.last_forecast_lag, next_forecast_lag);

			PendingTask.submit_task (
				tstatus.event_id,										// event id
				tstatus.last_mainshock_time + next_forecast_lag 
				+ dispatcher_action_config.get_comcat_clock_skew()
				+ dispatcher_action_config.get_comcat_origin_skew(),	// sched_time
				dispatcher_time,										// submit_time
				SUBID_AAFS,												// submit_id
				OPCODE_GEN_FORECAST,									// opcode
				0,														// stage
				forecast_payload.marshal_task());						// details

			return true;
		}

		// If timeline state requests an action, submit an expire command

		if (tstatus.is_forecast_state() || tstatus.is_pdl_retry_state()) {
		
			OpGenerateExpire expire_payload = new OpGenerateExpire();
			expire_payload.setup (tstatus.last_forecast_lag);

			PendingTask.submit_task (
				tstatus.event_id,										// event id
				dispatcher_time,										// sched_time
				dispatcher_time,										// submit_time
				SUBID_AAFS,												// submit_id
				OPCODE_GEN_EXPIRE,										// opcode
				0,														// stage
				expire_payload.marshal_task());							// details

			return true;
		}

		// No command required

		return false;
	}




	// Send a report to PDL.
	// Throw an exception if the report failed.

	public void send_pdl_report (TimelineStatus tstatus) {

		// For now, just assume success

		return;
	}




	// Return true if this machine is primary for sending reports to PDL, false if secondary

	public boolean is_pdl_primary () {

		// For now, just assume primary

		return true;
	}




	//----- Task execution functions -----




	// Execute unknown opcode.

	private int exec_unknown (PendingTask task) {

		// Remove the task from the queue

		PendingTask.delete_task (task);

		// Throw exception

		throw new RuntimeException("TaskDispatcher: Invalid opcode\n" + task.toString());

		//return RESCODE_DELETE;	// would be unreachable
	}




	// Execute no operation.

	private int exec_no_op (PendingTask task) {

		// Remove the task from the queue

		return RESCODE_DELETE;
	}




	// Execute shutdown.

	private int exec_shutdown (PendingTask task) {

		// Signal polling loop to exit

		dispatcher_state = STATE_SHUTDOWN;

		// Remove the task from the queue

		return RESCODE_DELETE;
	}




	// Execute console message.
	// For testing, this message supports the following stages:
	//  0 = Write message normally.
	//  1 = If not restarting, throw exception before writing message.
	//  2 = If not restarting, throw exception after writing message.

	private int exec_con_message (PendingTask task) {

		// Get payload and check for valid task

		OpConsoleMessage payload = new OpConsoleMessage();

		try {
			payload.unmarshal_task (task);

			if (task.get_stage() < 0 || task.get_stage() > 2) {
				throw new DBCorruptException("Invalid stage for console message task, stage = " + task.get_stage());
			}
		}

		// Invalid task

		catch (Exception e) {

			// Display the error and log the task

			display_invalid_task (task, e);
			return RESCODE_TASK_CORRUPT;
		}

		// If stage 1 and not restarting, throw exception

		if (task.get_stage() == 1 && !task.is_restarted()) {
			throw new RuntimeException("TaskDispatcher.exec_con_message: Pre-message exception");
		}

		// Write message

		System.out.println (payload.message);

		// Log the task
		// (We write this explicitly so we can throw an exception afterward)

		LogEntry.submit_log_entry (task, taskres_log_time, RESCODE_SUCCESS, taskres_log_remark);

		// If stage 2 and not restarting, throw exception

		if (task.get_stage() == 2 && !task.is_restarted()) {
			throw new RuntimeException("TaskDispatcher.exec_con_message: Post-message exception");
		}

		// Remove the task from the queue

		return RESCODE_DELETE;
	}




	// Generate forecast.

	private int exec_gen_forecast (PendingTask task) {

		//--- Get payload

		// Get payload and check for valid task

		OpGenerateForecast payload = new OpGenerateForecast();

		try {
			payload.unmarshal_task (task);
		}

		// Invalid task

		catch (Exception e) {

			// Display the error and log the task

			display_invalid_task (task, e);
			return RESCODE_TASK_CORRUPT;
		}

		//--- Check restart

		// If restarting ...

		TimelineEntry tentry = null;
		TimelineStatus tstatus = null;

		if (task.is_restarted()) {

			// If we wrote a timeline entry ...

			tentry = TimelineEntry.get_timeline_entry_for_key (task.get_record_key());
			if (tentry != null) {

				// Log the task

				return RESCODE_SUCCESS;
			}

			// Delete any delayed tasks for this timeline that might have been written last time

			delete_delayed_timeline_tasks (task.get_event_id());

			// Delete any catalog snapshot written for this command

			CatalogSnapshot catsnap = CatalogSnapshot.get_catalog_shapshot_for_key (task.get_record_key());
			if (catsnap != null) {
				CatalogSnapshot.delete_catalog_snapshot (catsnap);
			}

			// Now continue, starting the operation over from the beginning ...
		}

		//--- Retrieve timeline

		// Get the most recent timeline entry for this event

		tentry = TimelineEntry.get_recent_timeline_entry (0L, 0L, task.get_event_id());

		if (tentry == null) {
			//display_timeline_not_found (task);
		
			set_display_taskres_log ("TASK-ERR: Timeline entry not found:\n"
				+ "event_id = " + task.get_event_id());

			return RESCODE_TIMELINE_NOT_FOUND;
		}

		// Get the status for this timeline entry

		tstatus = new TimelineStatus();

		try {
			tstatus.unmarshal_timeline (tentry);
		}

		// Invalid timeline entry

		catch (Exception e) {

			// Display the error and log the task

			display_timeline_corrupt (tentry, task, e);
			return RESCODE_TIMELINE_CORRUPT;
		}

		//--- Timeline state check

		// Get the expected forecast lag

		long next_forecast_lag = get_next_forecast_lag (tstatus);

		// Check that timeline is active

		if (next_forecast_lag < 0L) {
			//display_timeline_not_active (task, tstatus);
		
			set_display_taskres_log ("TASK-ERR: Timeline entry is not active:\n"
				+ "event_id = " + task.get_event_id() + "\n"
				+ "tstatus.fc_status = " + tstatus.get_fc_status_as_string());

			next_auto_timeline (tstatus);
			return RESCODE_TIMELINE_NOT_ACTIVE;
		}

		// Check lag matches the command

		if (!( payload.next_forecast_lag == next_forecast_lag
			&& payload.last_forecast_lag == tstatus.last_forecast_lag )) {
		
			set_display_taskres_log ("TASK-ERR: Timeline entry lags do not match task:\n"
				+ "event_id = " + task.get_event_id() + "\n"
				+ "payload.next_forecast_lag = " + payload.next_forecast_lag + "\n"
				+ "next_forecast_lag = " + next_forecast_lag + "\n"
				+ "payload.last_forecast_lag = " + payload.last_forecast_lag + "\n"
				+ "tstatus.last_forecast_lag = " + tstatus.last_forecast_lag);

			next_auto_timeline (tstatus);
			return RESCODE_TIMELINE_LAG_MISMATCH;
		}

		//--- Forecast

		// Fetch parameters, part 1 (control and mainshock parameters)

		ForecastParameters forecast_params = new ForecastParameters();

		try {
			forecast_params.fetch_all_1 (task.get_event_id(), tstatus.analyst_params);
		}

		// An exception here triggers a ComCat retry

		catch (Exception e) {

			// Get the next ComCat retry lag

			long next_comcat_retry_lag = dispatcher_action_config.get_next_comcat_retry_lag (
											dispatcher_action_config.int_to_lag (task.get_stage()) + 1L );

			// If there is another retry, stage the task

			if (next_comcat_retry_lag >= 0L) {
				set_taskres_stage (task.get_sched_time() + next_comcat_retry_lag,
									dispatcher_action_config.lag_to_int (next_comcat_retry_lag));
				return RESCODE_STAGE;
			}

			// Retries exhausted, display the error and log the task

			display_timeline_comcat_fail (task, tstatus, e);
			return RESCODE_TIMELINE_COMCAT_FAIL;
		}

		// Check if it's too soon to do this forecast (might happen if mainshock origin time has changed)

		if (forecast_params.mainshock_time + next_forecast_lag
			+ dispatcher_action_config.get_comcat_clock_skew() > dispatcher_time) {
		
			// Stage the task

			set_taskres_stage (forecast_params.mainshock_time + next_forecast_lag
								+ dispatcher_action_config.get_comcat_clock_skew()
								+ dispatcher_action_config.get_comcat_origin_skew(),
								task.get_stage());
			return RESCODE_STAGE;
		}

		// If intake need to be re-checked ...

		if (tstatus.is_intake_state()) {

			// Search intake regions

			IntakeSphRegion intake_region = dispatcher_action_config.get_pdl_intake_region_for_min_mag (
				forecast_params.mainshock_lat, forecast_params.mainshock_lon, forecast_params.mainshock_mag);

			// If none, then withdraw the timeline
		
			set_display_taskres_log ("TASK-INFO: Timeline entry withdrawn:\n"
				+ "event_id = " + task.get_event_id() + "\n"
				+ "forecast_params.mainshock_lat = " + forecast_params.mainshock_lat + "\n"
				+ "forecast_params.mainshock_lon = " + forecast_params.mainshock_lon + "\n"
				+ "forecast_params.mainshock_mag = " + forecast_params.mainshock_mag);

			tstatus.set_state_withdrawn (dispatcher_time, forecast_params.mainshock_time);
			append_timeline (task, tstatus);

			return RESCODE_TIMELINE_WITHDRAW;
		}

		// Fetch parameters, part 2 (model and search parameters), and calculate results

		ForecastResults forecast_results = new ForecastResults();

		try {
			forecast_params.fetch_all_2 (next_forecast_lag, tstatus.analyst_params);

			forecast_results.calc_all (
				forecast_params.mainshock_time + next_forecast_lag,
				forecast_params,
				next_forecast_lag >= dispatcher_action_config.get_seq_spec_min_lag());
		}

		// An exception here triggers a ComCat retry

		catch (Exception e) {

			// Get the next ComCat retry lag

			long next_comcat_retry_lag = dispatcher_action_config.get_next_comcat_retry_lag (
											dispatcher_action_config.int_to_lag (task.get_stage()) + 1L );

			// If there is another retry, stage the task

			if (next_comcat_retry_lag >= 0L) {
				set_taskres_stage (task.get_sched_time() + next_comcat_retry_lag,
									dispatcher_action_config.lag_to_int (next_comcat_retry_lag));
				return RESCODE_STAGE;
			}

			// Retries exhausted, display the error and log the task

			display_timeline_comcat_fail (task, tstatus, e);
			return RESCODE_TIMELINE_COMCAT_FAIL;
		}

		// Select report for PDL, if any

		forecast_results.pick_pdl_model();

		// If we have an earthquake catalog ...

		if (forecast_results.catalog_result_avail) {

			// If there is an aftershock with larger magnitude than the mainshock ...

			if (forecast_results.catalog_eqk_count > 0 && forecast_results.catalog_max_mag > forecast_params.mainshock_mag) {
			
				// Set timeline to foreshock state

				tstatus.set_state_foreshock (dispatcher_time, forecast_results.catalog_max_event_id);

				// Write the timeline entry

				append_timeline (task, tstatus);

				// Log the task

				set_display_taskres_log ("TASK-INFO: Foreshock detected:\n"
					+ "event_id = " + tstatus.event_id + "\n"
					+ "mainshock_mag = " + forecast_params.mainshock_mag + "\n"
					+ "catalog_max_event_id = " + forecast_results.catalog_max_event_id + "\n"
					+ "catalog_max_mag = " + forecast_results.catalog_max_mag);

				return RESCODE_TIMELINE_FORESHOCK;
			}

			// Write catalog to database

			CatalogSnapshot.submit_catalog_shapshot (
				task.get_record_key(),					// key
				tstatus.event_id,						// event_id
				forecast_results.catalog_start_time,	// start_time
				forecast_results.catalog_end_time,		// end_time
				forecast_results.catalog_aftershocks);	// rupture_list

			// Display message
		
			display_taskinfo ("TASK-INFO: Catalog snapshot saved:\n"
				+ "event_id = " + tstatus.event_id + "\n"
				+ "catalog_eqk_count = " + forecast_results.catalog_eqk_count);

		}

		// Insert forecast into timeline status

		tstatus.set_state_forecast (dispatcher_time, dispatcher_action_config, forecast_params, forecast_results);

		// Get the next forecast lag, or -1 if none

		long new_next_forecast_lag = get_next_forecast_lag (tstatus);

		// If no next forecast, mark the timeline expired

		if (new_next_forecast_lag < 0L) {
			tstatus.set_fc_status (TimelineStatus.FCSTAT_STOP_EXPIRED);
		}

		//--- PDL report

		// Get the next PDL report lag, or -1 if none, assuming the report begins now

		long new_next_pdl_lag = get_next_pdl_lag (tstatus, new_next_forecast_lag, -1L, dispatcher_time);

		// If a PDL report is requested based on state ...

		if (tstatus.is_pdl_retry_state()) {

			// Check for secondary status

			if (!( is_pdl_primary() )) {
				tstatus.set_pdl_status (TimelineStatus.PDLSTAT_SECONDARY);
				new_next_pdl_lag = -1L;
			}

			// Otherwise, if no PDL report is requested based on timing, mark it bypassed

			else if (new_next_pdl_lag < 0L) {
				tstatus.set_pdl_status (TimelineStatus.PDLSTAT_BYPASSED);
			}

			// Otherwise, we need to send a PDL report ...

			else {
			
				// Attempt to send the report

				try {
					send_pdl_report (tstatus);
					tstatus.set_pdl_status (TimelineStatus.PDLSTAT_SUCCESS);
				}

				// Exception here means PDL report did not succeed

				catch (Exception e) {

					// Get time of PDL retry

					new_next_pdl_lag = get_next_pdl_lag (tstatus, new_next_forecast_lag, new_next_pdl_lag, dispatcher_time);

					// If no retry, report the failure now

					if (new_next_pdl_lag < 0L) {
						tstatus.set_pdl_status (TimelineStatus.PDLSTAT_FAILURE);
		
						display_taskinfo ("TASK-ERR: Unable to send forecast report to PDL:\n"
							+ "event_id = " + tstatus.event_id + "\n"
							+ "last_forecast_lag = " + tstatus.last_forecast_lag);
					}
				}
			}
		}

		//--- Final steps

		// Submit the next delayed command for this timeline

		next_auto_timeline (tstatus, new_next_forecast_lag, new_next_pdl_lag);

		// Write the new timeline entry

		// Log the task

		return RESCODE_SUCCESS;
	}







}
