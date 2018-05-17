package scratch.aftershockStatistics.aafs;

import java.util.List;

import scratch.aftershockStatistics.aafs.MongoDBUtil;

import scratch.aftershockStatistics.aafs.entity.PendingTask;
import scratch.aftershockStatistics.aafs.entity.LogEntry;
import scratch.aftershockStatistics.aafs.entity.CatalogSnapshot;
import scratch.aftershockStatistics.aafs.entity.TimelineEntry;

import scratch.aftershockStatistics.AftershockStatsCalc;
import scratch.aftershockStatistics.CompactEqkRupList;
import scratch.aftershockStatistics.RJ_AftershockModel_SequenceSpecific;

import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;

import scratch.aftershockStatistics.util.MarshalImpArray;
import scratch.aftershockStatistics.util.MarshalReader;
import scratch.aftershockStatistics.util.MarshalWriter;


/**
 * Holds a set of tests for the AAFS server code.
 * Author: Michael Barall 03/16/2018.
 */
public class ServerTest {




	// Test #1 - Add a few elements to the task pending queue.

	public static void test1(String[] args) {

		// No additional arguments

		if (args.length != 1) {
			System.err.println ("ServerTest : Invalid 'test1' subcommand");
			return;
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){
		
			String event_id;
			long sched_time;
			long submit_time;
			String submit_id;
			int opcode;
			int stage;
			MarshalWriter details;
		
			event_id = "Event_2";
			sched_time = 20100L;
			submit_time = 20000L;
			submit_id = "Submitter_2";
			opcode = 102;
			stage = 2;
			details = PendingTask.begin_details();
			details.marshalArrayBegin (null, 5);
			details.marshalString (null, "Details_2");
			details.marshalLong (null, 21010L);
			details.marshalLong (null, 21020L);
			details.marshalDouble (null, 21030.0);
			details.marshalDouble (null, 21040.0);
			details.marshalArrayEnd ();
			PendingTask.submit_task (event_id, sched_time, submit_time,
				submit_id, opcode, stage, details);
		
			event_id = "Event_4";
			sched_time = 40100L;
			submit_time = 40000L;
			submit_id = "Submitter_4_no_details";
			opcode = 104;
			stage = 4;
			details = null;
			PendingTask.submit_task (event_id, sched_time, submit_time,
				submit_id, opcode, stage, details);
		
			event_id = "Event_1";
			sched_time = 10100L;
			submit_time = 10000L;
			submit_id = "Submitter_1";
			opcode = 101;
			stage = 1;
			details = PendingTask.begin_details();
			details.marshalArrayBegin (null, 5);
			details.marshalString (null, "Details_1");
			details.marshalLong (null, 11010L);
			details.marshalLong (null, 11020L);
			details.marshalDouble (null, 11030.0);
			details.marshalDouble (null, 11040.0);
			details.marshalArrayEnd ();
			PendingTask.submit_task (event_id, sched_time, submit_time,
				submit_id, opcode, stage, details);
		
			event_id = "Event_5";
			sched_time = 50100L;
			submit_time = 50000L;
			submit_id = "Submitter_5";
			opcode = 105;
			stage = 5;
			details = PendingTask.begin_details();
			details.marshalArrayBegin (null, 5);
			details.marshalString (null, "Details_5");
			details.marshalLong (null, 51010L);
			details.marshalLong (null, 51020L);
			details.marshalDouble (null, 51030.0);
			details.marshalDouble (null, 51040.0);
			details.marshalArrayEnd ();
			PendingTask.submit_task (event_id, sched_time, submit_time,
				submit_id, opcode, stage, details);
		
			event_id = "Event_3";
			sched_time = 30100L;
			submit_time = 30000L;
			submit_id = "Submitter_3";
			opcode = 103;
			stage = 3;
			details = PendingTask.begin_details();
			details.marshalArrayBegin (null, 5);
			details.marshalString (null, "Details_3");
			details.marshalLong (null, 31010L);
			details.marshalLong (null, 31020L);
			details.marshalDouble (null, 31030.0);
			details.marshalDouble (null, 31040.0);
			details.marshalArrayEnd ();
			PendingTask.submit_task (event_id, sched_time, submit_time,
				submit_id, opcode, stage, details);

		}

		return;
	}




	// Test #2 - Display the pending task queue, unsorted.

	public static void test2(String[] args) {

		// No additional arguments

		if (args.length != 1) {
			System.err.println ("ServerTest : Invalid 'test2' subcommand");
			return;
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the list of pending tasks

			List<PendingTask> tasks = PendingTask.get_all_tasks_unsorted();

			// Display them

			for (PendingTask task : tasks) {
				System.out.println (task.toString());
			}

		}

		return;
	}




	// Test #3 - Display the pending task queue, sorted by execution time.

	public static void test3(String[] args) {

		// No additional arguments

		if (args.length != 1) {
			System.err.println ("ServerTest : Invalid 'test3' subcommand");
			return;
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the list of pending tasks

			List<PendingTask> tasks = PendingTask.get_all_tasks();

			// Display them

			for (PendingTask task : tasks) {
				System.out.println (task.toString());
			}

		}

		return;
	}




	// Test #4 - Display the first task in the pending task queue, according to execution time.

	public static void test4(String[] args) {

		// No additional arguments

		if (args.length != 1) {
			System.err.println ("ServerTest : Invalid 'test4' subcommand");
			return;
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the task

			PendingTask task = PendingTask.get_first_task();

			// Display it

			if (task == null) {
				System.out.println ("null");
			} else {
				System.out.println (task.toString());
			}

		}

		return;
	}




	// Test #5 - Display the first task in the pending task queue, before cutoff time, according to execution time.

	public static void test5(String[] args) {

		// One additional argument

		if (args.length != 2) {
			System.err.println ("ServerTest : Invalid 'test5' subcommand");
			return;
		}

		long cutoff_time = Long.parseLong(args[1]);

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the task

			PendingTask task = PendingTask.get_first_ready_task (cutoff_time);

			// Display it

			if (task == null) {
				System.out.println ("null");
			} else {
				System.out.println (task.toString());
			}

		}

		return;
	}




	// Test #6 - Activate the first document before the cutoff time, and display the retrieved document.

	public static void test6(String[] args) {

		// One additional argument

		if (args.length != 2) {
			System.err.println ("ServerTest : Invalid 'test6' subcommand");
			return;
		}

		long cutoff_time = Long.parseLong(args[1]);

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the task

			PendingTask task = PendingTask.activate_first_ready_task (cutoff_time);

			// Display it

			if (task == null) {
				System.out.println ("null");
			} else {
				System.out.println (task.toString());
			}

		}

		return;
	}




	// Test #7 - Activate the first document before the cutoff time, and stage it.

	public static void test7(String[] args) {

		// Three additional arguments

		if (args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test7' subcommand");
			return;
		}

		long cutoff_time = Long.parseLong(args[1]);
		long exec_time = Long.parseLong(args[2]);
		int stage = Integer.parseInt(args[3]);

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the task

			PendingTask task = PendingTask.activate_first_ready_task (cutoff_time);

			// Stage it

			if (task != null) {
				PendingTask.stage_task (task, exec_time, stage);
			}

			// Display it

			if (task == null) {
				System.out.println ("null");
			} else {
				System.out.println (task.toString());
			}

		}

		return;
	}




	// Test #8 - Activate the first document before the cutoff time, and delete it.

	public static void test8(String[] args) {

		// One additional argument

		if (args.length != 2) {
			System.err.println ("ServerTest : Invalid 'test8' subcommand");
			return;
		}

		long cutoff_time = Long.parseLong(args[1]);

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the task

			PendingTask task = PendingTask.activate_first_ready_task (cutoff_time);

			// Delete it

			if (task != null) {
				PendingTask.delete_task (task);
			}

			// Display it

			if (task == null) {
				System.out.println ("null");
			} else {
				System.out.println (task.toString());
			}

		}

		return;
	}




	// Test #9 - Run task dispatcher.

	public static void test9(String[] args) {

		// No additional arguments

		if (args.length != 1) {
			System.err.println ("ServerTest : Invalid 'test9' subcommand");
			return;
		}

		// Get a task dispatcher

		TaskDispatcher dispatcher = new TaskDispatcher();

		// Run it

		dispatcher.run();

		// Display final status

		System.out.println ("Dispatcher final state: " + dispatcher.get_dispatcher_state());

		return;
	}




	// Test #10 - Post a shutdown task.

	public static void test10(String[] args) {

		// No additional arguments

		if (args.length != 1) {
			System.err.println ("ServerTest : Invalid 'test10' subcommand");
			return;
		}

		// Get a task dispatcher

		TaskDispatcher dispatcher = new TaskDispatcher();

		// Post the shutdown task

		boolean result = dispatcher.post_shutdown ("ServerTest");

		// Display result

		System.out.println ("Post shutdown result: " + result);

		return;
	}




	// Test #11 - Scan the pending task queue, sorted, and write a log entry for each.

	public static void test11(String[] args) {

		// No additional arguments

		if (args.length != 1) {
			System.err.println ("ServerTest : Invalid 'test11' subcommand");
			return;
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the list of pending tasks

			List<PendingTask> tasks = PendingTask.get_all_tasks();

			// Write the log entries

			for (PendingTask task : tasks) {
				LogEntry.submit_log_entry (task, task.get_sched_time() + 100L, task.get_opcode() + 1000, "Result_for_" + task.get_opcode());
			}

		}

		return;
	}




	// Test #12 - Scan the pending task queue, sorted, and search the log for each.

	public static void test12(String[] args) {

		// No additional arguments

		if (args.length != 1) {
			System.err.println ("ServerTest : Invalid 'test12' subcommand");
			return;
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the list of pending tasks

			List<PendingTask> tasks = PendingTask.get_all_tasks();

			// Search for the log entries, display task and matching log entry

			for (PendingTask task : tasks) {
				System.out.println (task.toString());
				LogEntry entry = LogEntry.get_log_entry_for_key (task.get_record_key());
				if (entry == null) {
					System.out.println ("LogEntry: null");
				} else {
					System.out.println (entry.toString());
				}
			}

		}

		return;
	}




	// Test #13 - Search the log for log time and/or event id.

	public static void test13(String[] args) {

		// Two or three additional arguments

		if (args.length != 3 && args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test13' subcommand");
			return;
		}

		long log_time_lo = Long.parseLong(args[1]);
		long log_time_hi = Long.parseLong(args[2]);
		String event_id = null;
		if (args.length == 4) {
			event_id = args[3];
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the list of matching log entries

			List<LogEntry> entries = LogEntry.get_log_entry_range (log_time_lo, log_time_hi, event_id);

			// Display them

			for (LogEntry entry : entries) {
				System.out.println (entry.toString());
			}

		}

		return;
	}




	// Test #14 - Search the log for log time and/or event id, and delete the entries.

	public static void test14(String[] args) {

		// Two or three additional arguments

		if (args.length != 3 && args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test14' subcommand");
			return;
		}

		long log_time_lo = Long.parseLong(args[1]);
		long log_time_hi = Long.parseLong(args[2]);
		String event_id = null;
		if (args.length == 4) {
			event_id = args[3];
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the list of matching log entries

			List<LogEntry> entries = LogEntry.get_log_entry_range (log_time_lo, log_time_hi, event_id);

			// Display them, and delete

			for (LogEntry entry : entries) {
				System.out.println (entry.toString());
				LogEntry.delete_log_entry (entry);
			}

		}

		return;
	}




	// Test #15 - Post a task with given event id, opcode, stage, and details.

	public static void test15(String[] args) {

		// Three or four additional arguments

		if (args.length != 4 && args.length != 5) {
			System.err.println ("ServerTest : Invalid 'test15' subcommand");
			return;
		}

		String event_id = args[1];
		if (event_id.equalsIgnoreCase ("-")) {
			event_id = "";
		}
		int opcode = Integer.parseInt(args[2]);
		int stage = Integer.parseInt(args[3]);
		MarshalWriter details = PendingTask.begin_details();
		if (args.length == 5) {
			details.marshalMapBegin (null);
			details.marshalString ("value", args[4]);
			details.marshalMapEnd ();
		}

		// Post the task

		long the_time = ServerClock.get_time();

		TaskDispatcher.post_task (event_id, the_time, the_time, "ServerTest", opcode, stage, details);

		return;
	}




	// Test #16 - Execute the next task.

	public static void test16(String[] args) {

		// No additional arguments

		if (args.length != 1) {
			System.err.println ("ServerTest : Invalid 'test16' subcommand");
			return;
		}

		// Get a task dispatcher

		TaskDispatcher dispatcher = new TaskDispatcher();

		// Run one task

		boolean f_verbose = true;

		dispatcher.run_next_task (f_verbose);

		return;
	}




	// Test #17 - Write a catalog snapshot.

	public static void test17(String[] args) {

		// Tthree additional arguments

		if (args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test17' subcommand");
			return;
		}

		double start_time_days = Double.parseDouble (args[1]);
		double end_time_days   = Double.parseDouble (args[2]);
		String event_id = args[3];

		long start_time = Math.round(start_time_days * 86400000L);
		long end_time   = Math.round(end_time_days   * 86400000L);

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Create a simulated aftershock sequence, using the method of RJ_AftershockModel_SequenceSpecific
			// (Start and end times should be in the range of 0 to 30 days)
			
			double a = -1.67;
			double b = 0.91;
			double c = 0.05;
			double p = 1.08;
			double magMain = 7.5;
			double magCat = 2.5;
			double capG = 1.25;
			double capH = 0.75;

			ObsEqkRupList aftershockList = AftershockStatsCalc.simAftershockSequence(a, b, magMain, magCat, capG, capH, p, c, start_time_days, end_time_days);

			CompactEqkRupList rupture_list = new CompactEqkRupList (aftershockList);

			// Write the rupture sequence

			CatalogSnapshot entry_in = CatalogSnapshot.submit_catalog_shapshot (null, event_id, start_time, end_time, rupture_list);

			System.out.println (entry_in.toString());

			// Search for it

			CatalogSnapshot entry_out = CatalogSnapshot.get_catalog_shapshot_for_key (entry_in.get_record_key());

			System.out.println (entry_out.toString());

			// Use the retrieved rupture sequence to make a sequence-specific model
		
			double min_a = -2.0;
			double max_a = -1.0;
			int num_a = 101;

			double min_p = 0.9; 
			double max_p = 1.2; 
			int num_p = 31;
		
			double min_c=0.05;
			double max_c=0.05;
			int num_c=1;

			ObsEqkRupture mainShock = new ObsEqkRupture("0", 0L, null, magMain);

			CompactEqkRupList rupture_list_out = entry_out.get_rupture_list();

			// Make the model, it will output some information

			RJ_AftershockModel_SequenceSpecific seqModel =
				new RJ_AftershockModel_SequenceSpecific(mainShock, rupture_list_out,
			 								magCat, capG, capH,
											b, start_time_days, end_time_days,
											min_a, max_a, num_a, 
											min_p, max_p, num_p, 
											min_c, max_c, num_c);

		}

		return;
	}




	// Test #18 - Search the catalog snapshots for end time and/or event id.

	public static void test18(String[] args) {

		// Two or three additional arguments

		if (args.length != 3 && args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test18' subcommand");
			return;
		}

		double end_time_lo_days = Double.parseDouble (args[1]);
		double end_time_hi_days = Double.parseDouble (args[2]);
		String event_id = null;
		if (args.length == 4) {
			event_id = args[3];
		}

		long end_time_lo = Math.round(end_time_lo_days * 86400000L);
		long end_time_hi = Math.round(end_time_hi_days * 86400000L);

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the list of matching catalog snapshots

			List<CatalogSnapshot> entries = CatalogSnapshot.get_catalog_snapshot_range (end_time_lo, end_time_hi, event_id);

			// Display them

			for (CatalogSnapshot entry : entries) {
				System.out.println (entry.toString());
			}

		}

		return;
	}




	// Test #19 - Search the catalog snapshots for end time and/or event id, and delete the matching entries.

	public static void test19(String[] args) {

		// Two or three additional arguments

		if (args.length != 3 && args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test19' subcommand");
			return;
		}

		double end_time_lo_days = Double.parseDouble (args[1]);
		double end_time_hi_days = Double.parseDouble (args[2]);
		String event_id = null;
		if (args.length == 4) {
			event_id = args[3];
		}

		long end_time_lo = Math.round(end_time_lo_days * 86400000L);
		long end_time_hi = Math.round(end_time_hi_days * 86400000L);

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the list of matching catalog snapshots

			List<CatalogSnapshot> entries = CatalogSnapshot.get_catalog_snapshot_range (end_time_lo, end_time_hi, event_id);

			// Display them, and delete

			for (CatalogSnapshot entry : entries) {
				System.out.println (entry.toString());
				CatalogSnapshot.delete_catalog_snapshot (entry);
			}

		}

		return;
	}




	// Test #20 - Add a few elements to the timeline.

	public static void test20(String[] args) {

		// No additional arguments

		if (args.length != 1) {
			System.err.println ("ServerTest : Invalid 'test20' subcommand");
			return;
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){
		
			String event_id;
			long action_time;
			int actcode;
			MarshalWriter details;
		
			event_id = "Event_2";
			action_time = 20100L;
			actcode = 102;
			details = TimelineEntry.begin_details();
			details.marshalArrayBegin (null, 5);
			details.marshalString (null, "Details_2");
			details.marshalLong (null, 21010L);
			details.marshalLong (null, 21020L);
			details.marshalDouble (null, 21030.0);
			details.marshalDouble (null, 21040.0);
			details.marshalArrayEnd ();
			TimelineEntry.submit_timeline_entry (null, action_time, event_id,
				actcode, details);
		
			event_id = "Event_4";
			action_time = 40100L;
			actcode = 104;
			details = null;
			TimelineEntry.submit_timeline_entry (null, action_time, event_id,
				actcode, details);
		
			event_id = "Event_1";
			action_time = 10100L;
			actcode = 101;
			details = TimelineEntry.begin_details();
			details.marshalArrayBegin (null, 5);
			details.marshalString (null, "Details_1");
			details.marshalLong (null, 11010L);
			details.marshalLong (null, 11020L);
			details.marshalDouble (null, 11030.0);
			details.marshalDouble (null, 11040.0);
			details.marshalArrayEnd ();
			TimelineEntry.submit_timeline_entry (null, action_time, event_id,
				actcode, details);
		
			event_id = "Event_5";
			action_time = 50100L;
			actcode = 105;
			details = TimelineEntry.begin_details();
			details.marshalArrayBegin (null, 5);
			details.marshalString (null, "Details_5");
			details.marshalLong (null, 51010L);
			details.marshalLong (null, 51020L);
			details.marshalDouble (null, 51030.0);
			details.marshalDouble (null, 51040.0);
			details.marshalArrayEnd ();
			TimelineEntry.submit_timeline_entry (null, action_time, event_id,
				actcode, details);
		
			event_id = "Event_3";
			action_time = 30100L;
			actcode = 103;
			details = TimelineEntry.begin_details();
			details.marshalArrayBegin (null, 5);
			details.marshalString (null, "Details_3");
			details.marshalLong (null, 31010L);
			details.marshalLong (null, 31020L);
			details.marshalDouble (null, 31030.0);
			details.marshalDouble (null, 31040.0);
			details.marshalArrayEnd ();
			TimelineEntry.submit_timeline_entry (null, action_time, event_id,
				actcode, details);

		}

		return;
	}




	// Test #21 - Search the timeline for action time and/or event id; using list.

	public static void test21(String[] args) {

		// Two or three additional arguments

		if (args.length != 3 && args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test21' subcommand");
			return;
		}

		long action_time_lo = Long.parseLong(args[1]);
		long action_time_hi = Long.parseLong(args[2]);
		String event_id = null;
		if (args.length == 4) {
			event_id = args[3];
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the list of matching timeline entries

			List<TimelineEntry> entries = TimelineEntry.get_timeline_entry_range (action_time_lo, action_time_hi, event_id);

			// Display them

			for (TimelineEntry entry : entries) {
				System.out.println (entry.toString());
			}

		}

		return;
	}




	// Test #22 - Search the timeline for action time and/or event id; using iterator.

	public static void test22(String[] args) {

		// Two or three additional arguments

		if (args.length != 3 && args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test22' subcommand");
			return;
		}

		long action_time_lo = Long.parseLong(args[1]);
		long action_time_hi = Long.parseLong(args[2]);
		String event_id = null;
		if (args.length == 4) {
			event_id = args[3];
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){
			try (

				// Get an iterator over matching timeline entries

				RecordIterator<TimelineEntry> entries = TimelineEntry.fetch_timeline_entry_range (action_time_lo, action_time_hi, event_id);
			){

				// Display them

				for (TimelineEntry entry : entries) {
					System.out.println (entry.toString());
				}
			}
		}

		return;
	}




	// Test #23 - Search the timeline for action time and/or event id; and re-fetch the entries.

	public static void test23(String[] args) {

		// Two or three additional arguments

		if (args.length != 3 && args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test23' subcommand");
			return;
		}

		long action_time_lo = Long.parseLong(args[1]);
		long action_time_hi = Long.parseLong(args[2]);
		String event_id = null;
		if (args.length == 4) {
			event_id = args[3];
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the list of matching timeline entries

			List<TimelineEntry> entries = TimelineEntry.get_timeline_entry_range (action_time_lo, action_time_hi, event_id);

			// Display them, and re-fetch

			for (TimelineEntry entry : entries) {
				System.out.println (entry.toString());
				TimelineEntry refetch = TimelineEntry.get_timeline_entry_for_key (entry.get_record_key());
				System.out.println (refetch.toString());
			}
		}

		return;
	}




	// Test #24 - Search the timeline for action time and/or event id; and delete the entries.

	public static void test24(String[] args) {

		// Two or three additional arguments

		if (args.length != 3 && args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test24' subcommand");
			return;
		}

		long action_time_lo = Long.parseLong(args[1]);
		long action_time_hi = Long.parseLong(args[2]);
		String event_id = null;
		if (args.length == 4) {
			event_id = args[3];
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the list of matching timeline entries

			List<TimelineEntry> entries = TimelineEntry.get_timeline_entry_range (action_time_lo, action_time_hi, event_id);

			// Display them, and delete

			for (TimelineEntry entry : entries) {
				System.out.println (entry.toString());
				TimelineEntry.delete_timeline_entry (entry);
			}
		}

		return;
	}




	// Test #25 - Display the pending task queue, sorted by execution time, using iterator.

	public static void test25(String[] args) {

		// No additional arguments

		if (args.length != 1) {
			System.err.println ("ServerTest : Invalid 'test25' subcommand");
			return;
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){
			try (

				// Get an iterator over pending tasks

				RecordIterator<PendingTask> tasks = PendingTask.fetch_all_tasks();
			){

				// Display them

				for (PendingTask task : tasks) {
					System.out.println (task.toString());
				}
			}
		}

		return;
	}




	// Test #26 - Search the log for log time and/or event id, using iterator.

	public static void test26(String[] args) {

		// Two or three additional arguments

		if (args.length != 3 && args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test26' subcommand");
			return;
		}

		long log_time_lo = Long.parseLong(args[1]);
		long log_time_hi = Long.parseLong(args[2]);
		String event_id = null;
		if (args.length == 4) {
			event_id = args[3];
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){
			try (

				// Get an iterator over matching log entries

				RecordIterator<LogEntry> entries = LogEntry.fetch_log_entry_range (log_time_lo, log_time_hi, event_id);
			){

				// Display them

				for (LogEntry entry : entries) {
					System.out.println (entry.toString());
				}
			}
		}

		return;
	}




	// Test #27 - Search the catalog snapshots for end time and/or event id, using iterator.

	public static void test27(String[] args) {

		// Two or three additional arguments

		if (args.length != 3 && args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test27' subcommand");
			return;
		}

		double end_time_lo_days = Double.parseDouble (args[1]);
		double end_time_hi_days = Double.parseDouble (args[2]);
		String event_id = null;
		if (args.length == 4) {
			event_id = args[3];
		}

		long end_time_lo = Math.round(end_time_lo_days * 86400000L);
		long end_time_hi = Math.round(end_time_hi_days * 86400000L);

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){
			try (

				// Get an iterator over matching catalog snapshots

				RecordIterator<CatalogSnapshot> entries = CatalogSnapshot.fetch_catalog_snapshot_range (end_time_lo, end_time_hi, event_id);
			){

				// Display them

				for (CatalogSnapshot entry : entries) {
					System.out.println (entry.toString());
				}
			}
		}

		return;
	}




	// Test #28 - Post a console message task with given stage and message.

	public static void test28(String[] args) {

		// Two additional arguments

		if (args.length != 3) {
			System.err.println ("ServerTest : Invalid 'test28' subcommand");
			return;
		}

		int stage = Integer.parseInt(args[1]);

		OpConsoleMessage payload = new OpConsoleMessage();
		payload.message = args[2];

		String event_id = "";
		int opcode = TaskDispatcher.OPCODE_CON_MESSAGE;

		// Post the task

		long the_time = ServerClock.get_time();

		TaskDispatcher.post_task (event_id, the_time, the_time, "ServerTest", opcode, stage, payload.marshal_task());

		return;
	}




	// Test #29 - Search the task queue for execution time and/or event id.

	public static void test29(String[] args) {

		// Two or three additional arguments

		if (args.length != 3 && args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test29' subcommand");
			return;
		}

		long exec_time_lo = Long.parseLong(args[1]);
		long exec_time_hi = Long.parseLong(args[2]);
		String event_id = null;
		if (args.length == 4) {
			event_id = args[3];
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the list of matching tasks

			List<PendingTask> tasks = PendingTask.get_task_entry_range (exec_time_lo, exec_time_hi, event_id);

			// Display them

			for (PendingTask task : tasks) {
				System.out.println (task.toString());
			}

		}

		return;
	}




	// Test #30 - Search the task queue for execution time and/or event id, using iterator.

	public static void test30(String[] args) {

		// Two or three additional arguments

		if (args.length != 3 && args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test30' subcommand");
			return;
		}

		long exec_time_lo = Long.parseLong(args[1]);
		long exec_time_hi = Long.parseLong(args[2]);
		String event_id = null;
		if (args.length == 4) {
			event_id = args[3];
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){
			try (

				// Get an iterator over matching tasks

				RecordIterator<PendingTask> tasks = PendingTask.fetch_task_entry_range (exec_time_lo, exec_time_hi, event_id);
			){

				// Display them

				for (PendingTask task : tasks) {
					System.out.println (task.toString());
				}
			}
		}

		return;
	}




	// Test #31 - Search the timeline for action time and/or event id; get most recent.

	public static void test31(String[] args) {

		// Two or three additional arguments

		if (args.length != 3 && args.length != 4) {
			System.err.println ("ServerTest : Invalid 'test31' subcommand");
			return;
		}

		long action_time_lo = Long.parseLong(args[1]);
		long action_time_hi = Long.parseLong(args[2]);
		String event_id = null;
		if (args.length == 4) {
			event_id = args[3];
		}

		// Connect to MongoDB

		try (
			MongoDBUtil mongo_instance = new MongoDBUtil();
		){

			// Get the most recent matching timeline entry

			TimelineEntry entry = TimelineEntry.get_recent_timeline_entry (action_time_lo, action_time_hi, event_id);

			// Display it

			if (entry == null) {
				System.out.println ("null");
			} else {
				System.out.println (entry.toString());
			}

		}

		return;
	}




	// Test dispatcher.
	
	public static void main(String[] args) {

		// There needs to be at least one argument, which is the subcommand

		if (args.length < 1) {
			System.err.println ("ServerTest : Missing subcommand");
			return;
		}

		// Subcommand : Test #1
		// Command format:
		//  test1
		// Add a few items to the pending task queue.

		if (args[0].equalsIgnoreCase ("test1")) {

			try {
				test1(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #2
		// Command format:
		//  test2
		// Display the pending task queue, unsorted.

		if (args[0].equalsIgnoreCase ("test2")) {

			try {
				test2(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #3
		// Command format:
		//  test3
		// Display the pending task queue, sorted by execution time.

		if (args[0].equalsIgnoreCase ("test3")) {

			try {
				test3(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #4
		// Command format:
		//  test4
		// Display the first task in the pending task queue, according to execution time.

		if (args[0].equalsIgnoreCase ("test4")) {

			try {
				test4(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #5
		// Command format:
		//  test5  cutoff_time
		// Display the first task in the pending task queue, before cutoff time, according to execution time.

		if (args[0].equalsIgnoreCase ("test5")) {

			try {
				test5(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #6
		// Command format:
		//  test6  cutoff_time
		// Activate the first document before the cutoff time, and display the retrieved document.

		if (args[0].equalsIgnoreCase ("test6")) {

			try {
				test6(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #7
		// Command format:
		//  test7  cutoff_time  exec_time  stage
		// Activate the first document before the cutoff time, and stage it.

		if (args[0].equalsIgnoreCase ("test7")) {

			try {
				test7(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #8
		// Command format:
		//  test6  cutoff_time
		// Activate the first document before the cutoff time, and delete it.

		if (args[0].equalsIgnoreCase ("test8")) {

			try {
				test8(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #9
		// Command format:
		//  test9
		// Run task dispatcher.

		if (args[0].equalsIgnoreCase ("test9")) {

			try {
				test9(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #10
		// Command format:
		//  test10
		// Post a shutdown task.

		if (args[0].equalsIgnoreCase ("test10")) {

			try {
				test10(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #11
		// Command format:
		//  test11
		// Scan the pending task queue, sorted, and write a log entry for each.

		if (args[0].equalsIgnoreCase ("test11")) {

			try {
				test11(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #12
		// Command format:
		//  test12
		// Scan the pending task queue, sorted, and search the log for each.

		if (args[0].equalsIgnoreCase ("test12")) {

			try {
				test12(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #13
		// Command format:
		//  test13  log_time_lo  log_time_hi  [event_id]
		// Search the log for log time and/or event id.
		// Log times can be 0 for no bound, event id can be omitted for no restriction.

		if (args[0].equalsIgnoreCase ("test13")) {

			try {
				test13(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #14
		// Command format:
		//  test14  log_time_lo  log_time_hi  [event_id]
		// Search the log for log time and/or event id, and delete the matching entries.
		// Log times can be 0 for no bound, event id can be omitted for no restriction.

		if (args[0].equalsIgnoreCase ("test14")) {

			try {
				test14(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #15
		// Command format:
		//  test15  event_id  opcode  stage  [details]
		// Post a task with given event id, opcode, stage, and details.
		// Event id can be "-" for an empty string.

		if (args[0].equalsIgnoreCase ("test15")) {

			try {
				test15(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #16
		// Command format:
		//  test16
		// Execute the next task.

		if (args[0].equalsIgnoreCase ("test16")) {

			try {
				test16(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #17
		// Command format:
		//  test17  start_time_days  end_time_days  event_id
		// Write a catalog snapshot.

		if (args[0].equalsIgnoreCase ("test17")) {

			try {
				test17(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #18
		// Command format:
		//  test18  end_time_lo_days  end_time_hi_days  [event_id]
		// Search the catalog snapshots for end time and/or event id.
		// Times can be 0 for no bound, event id can be omitted for no restriction.

		if (args[0].equalsIgnoreCase ("test18")) {

			try {
				test18(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #19
		// Command format:
		//  test19  end_time_lo_days  end_time_hi_days  [event_id]
		// Search the catalog snapshots for end time and/or event id, and delete the matching entries.
		// Times can be 0 for no bound, event id can be omitted for no restriction.

		if (args[0].equalsIgnoreCase ("test19")) {

			try {
				test19(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #20
		// Command format:
		//  test20
		// Add a few elements to the timeline.

		if (args[0].equalsIgnoreCase ("test20")) {

			try {
				test20(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #21
		// Command format:
		//  test21  action_time_lo  action_time_hi  [event_id]
		// Search the timeline for action time and/or event id; using list.
		// Times can be 0 for no bound, event id can be omitted for no restriction.

		if (args[0].equalsIgnoreCase ("test21")) {

			try {
				test21(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #22
		// Command format:
		//  test22  action_time_lo  action_time_hi  [event_id]
		// Search the timeline for action time and/or event id; using iterator.
		// Times can be 0 for no bound, event id can be omitted for no restriction.

		if (args[0].equalsIgnoreCase ("test22")) {

			try {
				test22(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #23
		// Command format:
		//  test23  action_time_lo  action_time_hi  [event_id]
		// Search  the timeline for action time and/or event id; and re-fetch the entries.
		// Times can be 0 for no bound, event id can be omitted for no restriction.

		if (args[0].equalsIgnoreCase ("test23")) {

			try {
				test23(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #24
		// Command format:
		//  test24  action_time_lo  action_time_hi  [event_id]
		// Search  the timeline for action time and/or event id; and delete the entries.
		// Times can be 0 for no bound, event id can be omitted for no restriction.

		if (args[0].equalsIgnoreCase ("test24")) {

			try {
				test24(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #25
		// Command format:
		//  test25
		// Display the pending task queue, sorted by execution time, using iterator.

		if (args[0].equalsIgnoreCase ("test25")) {

			try {
				test25(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #26
		// Command format:
		//  test26  log_time_lo  log_time_hi  [event_id]
		// Search the log for log time and/or event id, using iterator.
		// Log times can be 0 for no bound, event id can be omitted for no restriction.

		if (args[0].equalsIgnoreCase ("test26")) {

			try {
				test26(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #27
		// Command format:
		//  test27  end_time_lo_days  end_time_hi_days  [event_id]
		// Search the catalog snapshots for end time and/or event id, using iterator.
		// Times can be 0 for no bound, event id can be omitted for no restriction.

		if (args[0].equalsIgnoreCase ("test27")) {

			try {
				test27(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #28
		// Command format:
		//  test28  stage  message
		// Post a console message task with given stage and message.

		if (args[0].equalsIgnoreCase ("test28")) {

			try {
				test28(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #29
		// Command format:
		//  test29  exec_time_lo  exec_time_hi  [event_id]
		// Search the task queue for execution time and/or event id.
		// Execution times can be 0 for no bound, event id can be omitted for no restriction.

		if (args[0].equalsIgnoreCase ("test29")) {

			try {
				test29(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #30
		// Command format:
		//  test30  exec_time_lo  exec_time_hi  [event_id]
		// Search the task queue for execution time and/or event id, using iterator.
		// Execution times can be 0 for no bound, event id can be omitted for no restriction.

		if (args[0].equalsIgnoreCase ("test30")) {

			try {
				test30(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Subcommand : Test #31
		// Command format:
		//  test31  action_time_lo  action_time_hi  [event_id]
		// Search the timeline for action time and/or event id; get most recent.
		// Times can be 0 for no bound, event id can be omitted for no restriction.

		if (args[0].equalsIgnoreCase ("test31")) {

			try {
				test31(args);
            } catch (Exception e) {
                e.printStackTrace();
			}

			return;
		}

		// Unrecognized subcommand.

		System.err.println ("ServerTest : Unrecognized subcommand : " + args[0]);
		return;
	}
}
