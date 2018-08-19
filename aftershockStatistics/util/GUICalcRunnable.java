package scratch.aftershockStatistics.util;

import java.awt.Component;
import java.awt.Dialog.ModalityType;

import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;

import org.opensha.sha.gui.infoTools.CalcProgressBar;


/**
 * Runs a sequence of operations on a worker thhread, with a progress monitor.
 * Author: Michael Barall 08/18/2018.
 */
public class GUICalcRunnable implements Runnable {

	// The owner of the progress bar.

	private Component owner;

	// The calculation steps to perform.

	private GUICalcStep[] steps;

	// An exception that occurred, or null if none.
		
	private volatile Throwable exception;	// written from multiple threads

	// Setting this flag true forces all calculation steps to occur in the event dispatch thread.

	private boolean forceEDT = true;

	// To construct, specify the owner of the progress monitor window, and the calculation steps.
		
	public GUICalcRunnable(Component owner, GUICalcStep... calcSteps) {
		this.owner = owner;
		this.steps = calcSteps;
	}

	// This function runs in an application thread.

	@Override
	public void run() {

		// Create and initialize the progress manager

		GUIProgressManager progress_manager = new GUIProgressManager (owner);
		progress_manager.req_init();

		// No exception so far

		exception = null;
		String curTitle = "No calculation";

		// For each calculation step ...

		for (final GUICalcStep step : steps) {

			// Update the progress manager

			progress_manager.req_update (step.get_title(), step.get_progressMessage());

			// Save the title of the current calculation step

			curTitle = step.get_title();

			// If we want to run in the event dispatch thread ...

			if (forceEDT || step.get_runInEDT()) {
				try {
					SwingUtilities.invokeAndWait(new Runnable() {
						@Override
						public void run() {
							try {
								step.get_run().run();
							} catch (Throwable e) {
								exception = e;
							}
						}
					});
				} catch (Exception e) {
					exception = e;
				}
			}

			// Otherwise, we want to run in the application thread (this thread)

			else {
				try {
					step.get_run().run();
				} catch (Throwable e) {
					exception = e;
				}
			}

			// If any exception occurred, stop the sequence of operations

			if (exception != null) {
				break;
			}
		}

		// Dispose of the progress manager

		progress_manager.req_dispose();

		// If an exception occurred, report it to the user

		if (exception != null) {
			final String title = "Error " + curTitle;
			exception.printStackTrace();
			final String message = exception.getMessage();
			try {
				SwingUtilities.invokeAndWait(new Runnable() {
					@Override
					public void run() {
						JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
					}
				});
			} catch (Exception e) {
				System.err.println("Error displaying error message!");
				e.printStackTrace();
			}
		}

		return;
	}

}
