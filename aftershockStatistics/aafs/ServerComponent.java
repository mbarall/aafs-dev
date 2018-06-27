package scratch.aftershockStatistics.aafs;

/**
 * Common base class for server components.
 * Author: Michael Barall 06/23/2018.
 */
public class ServerComponent {


	//----- Constant definitions -----
	//
	// These constants are known to every server component.




	// Opcodes.

	public static final int OPCODE_MIN = 1;					// Minimum allowed opcode
	public static final int OPCODE_NO_OP = 1;				// No operation
	public static final int OPCODE_SHUTDOWN = 2;			// Shut down the server
	public static final int OPCODE_CON_MESSAGE = 3;			// Write message to console
	public static final int OPCODE_GEN_FORECAST = 4;		// Generate forecast
	public static final int OPCODE_GEN_PDL_REPORT = 5;		// Generate delayed forecast report to PDL
	public static final int OPCODE_GEN_EXPIRE = 6;			// Stop generation, mark the timeline expired
	public static final int OPCODE_INTAKE_SYNC = 7;			// Intake an event, from sync
	public static final int OPCODE_INTAKE_PDL = 8;			// Intake an event, from PDL
	public static final int OPCODE_ANALYST_INTERVENE = 9;	// Analyst intervention
	public static final int OPCODE_UNKNOWN = 10;			// Unknown operation
	public static final int OPCODE_MAX = 10;				// Maximum allowed opcode

	// Return a string describing an opcode.

	public String get_opcode_as_string (int x) {
		switch (x) {
		case OPCODE_NO_OP: return "OPCODE_NO_OP";
		case OPCODE_SHUTDOWN: return "OPCODE_SHUTDOWN";
		case OPCODE_CON_MESSAGE: return "OPCODE_CON_MESSAGE";
		case OPCODE_GEN_FORECAST: return "OPCODE_GEN_FORECAST";
		case OPCODE_GEN_PDL_REPORT: return "OPCODE_GEN_PDL_REPORT";
		case OPCODE_GEN_EXPIRE: return "OPCODE_GEN_EXPIRE";
		case OPCODE_INTAKE_SYNC: return "OPCODE_INTAKE_SYNC";
		case OPCODE_INTAKE_PDL: return "OPCODE_INTAKE_PDL";
		case OPCODE_ANALYST_INTERVENE: return "OPCODE_ANALYST_INTERVENE";
		case OPCODE_UNKNOWN: return "OPCODE_UNKNOWN";
		}
		return "OPCODE_INVALID(" + x + ")";
	}




	// Special execution times.

	public static final long EXEC_TIME_ACTIVE = 0L;						// Task is active
	public static final long EXEC_TIME_MIN_WAITING = 1L;				// Minimum execution time for waiting tasks
	public static final long EXEC_TIME_MIN_PROMPT = 10000000L;			// Minimum execution time for prompt tasks, 10^7 ~ 2.8 hours
	public static final long EXEC_TIME_MAX_PROMPT = 19000000L;			// Maximum execution time for prompt tasks
	public static final long EXEC_TIME_SHUTDOWN = 20000000L;			// Execution time for shutdown task, 2*10^7 ~ 5.6 hours
	public static final long EXEC_TIME_FAR_FUTURE = 1000000000000000L;	// 10^15 ~ 30,000 years




	// Result codes.

	public static final int RESCODE_MIN = 1;					// Minimum known result code
	public static final int RESCODE_SUCCESS = 1;				// Task completed successfully
	public static final int RESCODE_TASK_CORRUPT = 2;			// Task entry or payload was corrupted, task discarded
	public static final int RESCODE_TIMELINE_CORRUPT = 3;		// Timeline entry or payload was corrupted, task discarded
	public static final int RESCODE_TIMELINE_NOT_FOUND = 4;		// Timeline entry not found, task discarded
	public static final int RESCODE_TIMELINE_NOT_ACTIVE = 5;	// Timeline entry not active, task discarded
	public static final int RESCODE_TIMELINE_TASK_MISMATCH = 6;	// Timeline entry has lag values that do not match the forecast task
	public static final int RESCODE_TIMELINE_COMCAT_FAIL = 7;	// Timeline stopped due to ComCat failure
	public static final int RESCODE_TIMELINE_WITHDRAW = 8;		// Timeline stopped due to withdrawal of event at first forecast
	public static final int RESCODE_TIMELINE_FORESHOCK = 9;		// Timeline stopped because event was found to be a foreshock
	public static final int RESCODE_TIMELINE_NOT_PDL_PEND = 10;	// Timeline entry does not have a PDL report pending, task discarded
	public static final int RESCODE_TIMELINE_PDL_FAIL = 11;		// Timeline attempt to send PDL report failed, sending abandoned
	public static final int RESCODE_TIMELINE_EXISTS = 12;		// Timeline already exists, task discarded
	public static final int RESCODE_TASK_RETRY_SUCCESS = 13;	// Task completed on task dispatcher retry
	public static final int RESCODE_TIMELINE_STATE_UPDATE = 14;	// Timeline state was updated
	public static final int RESCODE_INTAKE_COMCAT_FAIL = 15;	// Event intake failed due to ComCat failure
	public static final int RESCODE_TIMELINE_ANALYST_SET = 16;	// Timeline analyst data was set
	public static final int RESCODE_TIMELINE_ANALYST_FAIL = 17;	// Timeline analyst intervention failed due to bad state
	public static final int RESCODE_TIMELINE_ANALYST_NONE = 18;	// Timeline analyst intervention not done
	public static final int RESCODE_MAX = 18;					// Maximum known result code

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
		case RESCODE_TIMELINE_TASK_MISMATCH: return "RESCODE_TIMELINE_TASK_MISMATCH";
		case RESCODE_TIMELINE_COMCAT_FAIL: return "RESCODE_TIMELINE_COMCAT_FAIL";
		case RESCODE_TIMELINE_WITHDRAW: return "RESCODE_TIMELINE_WITHDRAW";
		case RESCODE_TIMELINE_FORESHOCK: return "RESCODE_TIMELINE_FORESHOCK";
		case RESCODE_TIMELINE_NOT_PDL_PEND: return "RESCODE_TIMELINE_NOT_PDL_PEND";
		case RESCODE_TIMELINE_PDL_FAIL: return "RESCODE_TIMELINE_PDL_FAIL";
		case RESCODE_TIMELINE_EXISTS: return "RESCODE_TIMELINE_EXISTS";
		case RESCODE_TASK_RETRY_SUCCESS: return "RESCODE_TASK_RETRY_SUCCESS";
		case RESCODE_TIMELINE_STATE_UPDATE: return "RESCODE_TIMELINE_STATE_UPDATE";
		case RESCODE_INTAKE_COMCAT_FAIL: return "RESCODE_INTAKE_COMCAT_FAIL";
		case RESCODE_TIMELINE_ANALYST_SET: return "RESCODE_TIMELINE_ANALYST_SET";
		case RESCODE_TIMELINE_ANALYST_FAIL: return "RESCODE_TIMELINE_ANALYST_FAIL";
		case RESCODE_TIMELINE_ANALYST_NONE: return "RESCODE_TIMELINE_ANALYST_NONE";

		case RESCODE_DELETE: return "RESCODE_DELETE";
		case RESCODE_STAGE: return "RESCODE_STAGE";
		}
		return "RESCODE_INVALID(" + x + ")";
	}




	// Special event ids

	public static final String EVID_SHUTDOWN = "===shutdown===";	// Shutdown task




	// Special submit ids

	public static final String SUBID_AAFS = "AAFS";		// Automatic system




	//----- Component access -----


	// Server group.
	// This provides access to other server components.

	protected ServerGroup sg;




	//----- Construction -----


	// Default constructor.

	public ServerComponent () {
		this.sg = null;
	}


	// Set up this component by linking to the server group.
	// A subclass may override this to perform additional setup operations.

	public void setup (ServerGroup the_sg) {
		this.sg = the_sg;
		return;
	}

}