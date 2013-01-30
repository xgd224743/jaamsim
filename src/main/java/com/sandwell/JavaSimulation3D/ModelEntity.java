/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2002-2011 Ausenco Engineering Canada Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.sandwell.JavaSimulation3D;

import static com.sandwell.JavaSimulation.Util.formatNumber;

import java.util.ArrayList;
import java.util.HashMap;

import com.jaamsim.input.InputAgent;
import com.jaamsim.math.Color4d;
import com.sandwell.JavaSimulation.BooleanInput;
import com.sandwell.JavaSimulation.BooleanListInput;
import com.sandwell.JavaSimulation.BooleanVector;
import com.sandwell.JavaSimulation.ColourInput;
import com.sandwell.JavaSimulation.DoubleInput;
import com.sandwell.JavaSimulation.DoubleListInput;
import com.sandwell.JavaSimulation.DoubleVector;
import com.sandwell.JavaSimulation.EntityInput;
import com.sandwell.JavaSimulation.EntityListInput;
import com.sandwell.JavaSimulation.ErrorException;
import com.sandwell.JavaSimulation.FileEntity;
import com.sandwell.JavaSimulation.Input;
import com.sandwell.JavaSimulation.InputErrorException;
import com.sandwell.JavaSimulation.IntegerVector;
import com.sandwell.JavaSimulation.Keyword;
import com.sandwell.JavaSimulation.ProbabilityDistribution;
import com.sandwell.JavaSimulation.Process;
import com.sandwell.JavaSimulation.Tester;
import com.sandwell.JavaSimulation.Vector;

/**
 * Class ModelEntity - JavaSimulation3D
 */
public class ModelEntity extends DisplayEntity {

	// Breakdowns

	@Keyword(desc = "Reliability is defined as:\n" +
	                " 100% - (plant breakdown time / total operation time)\n " +
	                "or\n " +
	                "(Operational Time)/(Breakdown + Operational Time)",
	         example = "Object1 Reliability { 0.95 }")
	private final DoubleInput availability;
	protected double hoursForNextFailure;    // The number of working hours required before the next breakdown
	protected double iATFailure;             // inter arrival time between failures
	protected boolean breakdownPending;          // true when a breakdown is to occur
	protected boolean brokendown;                // true => entity is presently broken down
	protected boolean maintenance;               // true => entity is presently in maintenance
	protected boolean associatedBreakdown;       // true => entity is presently in Associated Breakdown
	protected boolean associatedMaintenance;     // true => entity is presently in Associated Maintenance
	protected double breakdownStartTime;         // Start time of the most recent breakdown
	protected double breakdownEndTime;           // End time of the most recent breakdown

	// Breakdown Probability Distributions
	@Keyword(desc = "A ProbabilityDistribution object that governs the duration of breakdowns (in hours).",
	         example = "Object1  DowntimeDurationDistribution { BreakdownProbDist1 }")
	private final EntityInput<ProbabilityDistribution> downtimeDurationDistribution;

	@Keyword(desc = "A ProbabilityDistribution object that governs when breakdowns occur (in hours).",
	         example = "Object1  DowntimeIATDistribution { BreakdownProbDist1 }")
	private final EntityInput<ProbabilityDistribution> downtimeIATDistribution;

	// Maintenance

	@Keyword(desc = "The simulation time for the start of the first maintenance for each maintenance cycle.",
	         example = "Object1 FirstMaintenanceTime { 24 h }")
	protected DoubleListInput firstMaintenanceTimes;

	@Keyword(desc = "The time between maintenance activities for each maintenance cycle",
	         example = "Object1 MaintenanceInterval { 168 h }")
	protected DoubleListInput maintenanceIntervals;

	@Keyword(desc = "The durations of a single maintenance event for each maintenance cycle.",
	         example = "Object1 MaintenanceDuration { 336 h }")
	protected DoubleListInput maintenanceDurations;
	protected IntegerVector maintenancePendings;  // Number of maintenance periods that are due

	@Keyword(desc = "A Boolean value. Allows scheduled maintenances to be skipped if it overlaps " +
	                "with another planned maintenance event.",
	         example = "Object1 SkipMaintenanceIfOverlap { TRUE }")
	protected BooleanListInput skipMaintenanceIfOverlap;

	@Keyword(desc = "A list of objects that share the maintenance schedule with this object. " +
	                "In order for the maintenance to start, all objects on this list must be available." +
	                "This keyword is for Handlers and Signal Blocks only.",
	         example = "Block1 SharedMaintenance { Block2 Block2 }")
	private final EntityListInput<ModelEntity> sharedMaintenanceList;
	protected ModelEntity masterMaintenanceEntity;  // The entity that has maintenance information

	protected boolean performMaintenanceAfterShipDelayPending;			// maintenance needs to be done after shipDelay

	// Maintenance based on hours of operations

	@Keyword(desc = "Working time for the start of the first maintenance for each maintenance cycle",
	         example = "Object1 FirstMaintenanceOperatingHours { 1000 2500 h }")
	private final DoubleListInput firstMaintenanceOperatingHours;

	@Keyword(desc = "Working time between one maintenance event and the next for each maintenance cycle",
	         example = "Object1 MaintenanceOperatingHoursIntervals { 2000 5000 h }")
	private final DoubleListInput maintenanceOperatingHoursIntervals;

	@Keyword(desc = "Duration of maintenance events based on working hours for each maintenance cycle",
	         example = "Ship1 MaintenanceOperatingHoursDurations { 24 48 h }")
	private final DoubleListInput maintenanceOperatingHoursDurations;
	protected IntegerVector maintenanceOperatingHoursPendings;  // Number of maintenance periods that are due
	protected DoubleVector hoursForNextMaintenanceOperatingHours;

	protected double maintenanceStartTime; // Start time of the most recent maintenance
	protected double maintenanceEndTime; // End time of the most recent maintenance
	protected DoubleVector nextMaintenanceTimes; // next start time for each maintenance
	protected double nextMaintenanceDuration; // duration for next maintenance
	protected DoubleVector lastScheduledMaintenanceTimes;

	@Keyword(desc = "If maintenance has been deferred by the DeferMaintenanceLookAhead keyword " +
	                "for longer than this time, the maintenance will start even if " +
	                "there is an object within the lookahead. There must be one entry for each " +
	                "defined maintenance schedule if DeferMaintenanceLookAhead is used.  This" +
	                "keyword is only used for signal blocks.",
	         example = "Object1 DeferMaintenanceLimit { 50 50 h }")
	private final DoubleListInput deferMaintenanceLimit;


	@Keyword(desc = "If the duration of the downtime is longer than this time, equipment will be released",
	         example = "Object1 DowntimeToReleaseEquipment { 1.0 h }")
	protected final DoubleInput downtimeToReleaseEquipment;

	@Keyword(desc = "A list of Boolean values corresponding to the maintenance cycles. If a value is TRUE, " +
	                "then routes/tasks are released before performing the maintenance in the cycle.",
	         example = "Object1 ReleaseEquipment { TRUE FALSE FALSE }")
	protected final BooleanListInput releaseEquipment;

	@Keyword(desc = "A list of Boolean values corresponding to the maintenance cycles. If a value is " +
	                "TRUE, then maintenance in the cycle can start even if the equipment is presently " +
	                "working.",
	         example = "Object1 ForceMaintenance { TRUE FALSE FALSE }")
	protected final BooleanListInput forceMaintenance;

	// Statistics

	@Keyword(desc = "If TRUE, then statistics for this object are " +
	                "included in the main output report.",
	         example = "Object1 PrintToReport { TRUE }")
	private final BooleanInput printToReport;

	// States
	protected static Vector stateList = new Vector( 11, 1 ); // List of valid states
	protected HashMap<String, Integer> stateMap; // map of states and state list index
	protected double workingHours;                    // Accumulated working time spent in working states
	protected DoubleVector hoursPerState;             // Elapsed time to date for each state
	protected double lastHistogramUpdateTime;   // Last time at which a histogram was updated for this entity
	protected double secondToLastHistogramUpdateTime;   // Second to last time at which a histogram was updated for this entity
	protected DoubleVector lastStartTimePerState;     // Last time at which the state changed from some other state to each state
	protected DoubleVector secondToLastStartTimePerState;     // The second to last time at which the state changed from some other state to each state
	protected String presentState;                    // The present state of the entity
	protected int presentStateIndex;				  // The index of present state in the statelist
	protected double timeOfLastStateChange;           // Time at which the state was last changed
	protected FileEntity stateReportFile;        // The file to store the state information
	private String finalLastState = "";        // The final state of the entity (in a sequence of transitional states)
	private double timeOfLastPrintedState = 0; // The time that the last state printed in the trace state file

	// Graphics
	protected final static Color4d breakdownColor = ColourInput.DARK_RED; // Color of the entity in breaking down
	protected final static Color4d maintenanceColor = ColourInput.RED; // Color of the entity in maintenance

	static {
		stateList.addElement( "Idle" );
		stateList.addElement( "Working" );
		stateList.addElement( "Breakdown" );
		stateList.addElement( "Maintenance" );
	}

	{
		maintenanceDurations = new DoubleListInput("MaintenanceDurations", "Maintenance", new DoubleVector());
		maintenanceDurations.setValidRange(0.0d, Double.POSITIVE_INFINITY);
		maintenanceDurations.setUnits("h");
		this.addInput(maintenanceDurations, true, "MaintenanceDuration");

		maintenanceIntervals = new DoubleListInput("MaintenanceIntervals", "Maintenance", new DoubleVector());
		maintenanceIntervals.setValidRange(0.0d, Double.POSITIVE_INFINITY);
		maintenanceIntervals.setUnits("h");
		this.addInput(maintenanceIntervals, true, "MaintenanceInterval");

		firstMaintenanceTimes = new DoubleListInput("FirstMaintenanceTimes", "Maintenance", new DoubleVector());
		firstMaintenanceTimes.setValidRange(0.0d, Double.POSITIVE_INFINITY);
		firstMaintenanceTimes.setUnits("h");
		this.addInput(firstMaintenanceTimes, true, "FirstMaintenanceTime");

		forceMaintenance = new BooleanListInput("ForceMaintenance", "Maintenance", null);
		this.addInput(forceMaintenance, true);

		releaseEquipment = new BooleanListInput("ReleaseEquipment", "Maintenance", null);
		this.addInput(releaseEquipment, true);

		availability = new DoubleInput("Reliability", "Breakdowns", 1.0d, 0.0d, 1.0d);
		this.addInput(availability, true);

		downtimeIATDistribution = new EntityInput<ProbabilityDistribution>(ProbabilityDistribution.class, "DowntimeIATDistribution", "Breakdowns", null);
		this.addInput(downtimeIATDistribution, true);

		downtimeDurationDistribution = new EntityInput<ProbabilityDistribution>(ProbabilityDistribution.class, "DowntimeDurationDistribution", "Breakdowns", null);
		this.addInput(downtimeDurationDistribution, true);

		downtimeToReleaseEquipment = new DoubleInput("DowntimeToReleaseEquipment", "Breakdowns", 0.0d, 0.0d, Double.POSITIVE_INFINITY);
		this.addInput(downtimeToReleaseEquipment, true);

		skipMaintenanceIfOverlap = new BooleanListInput("SkipMaintenanceIfOverlap", "Maintenance", new BooleanVector());
		this.addInput(skipMaintenanceIfOverlap, true);

		deferMaintenanceLimit = new DoubleListInput("DeferMaintenanceLimit", "Maintenance", null);
		deferMaintenanceLimit.setValidRange(0.0d, Double.POSITIVE_INFINITY);
		deferMaintenanceLimit.setUnits("h");
		this.addInput(deferMaintenanceLimit, true);

		sharedMaintenanceList = new EntityListInput<ModelEntity>(ModelEntity.class, "SharedMaintenance", "Maintenance", new ArrayList<ModelEntity>(0));
		this.addInput(sharedMaintenanceList, true);

		firstMaintenanceOperatingHours = new DoubleListInput("FirstMaintenanceOperatingHours", "Maintenance", new DoubleVector());
		firstMaintenanceOperatingHours.setValidRange(0.0d, Double.POSITIVE_INFINITY);
		firstMaintenanceOperatingHours.setUnits("h");
		this.addInput(firstMaintenanceOperatingHours, true);

		maintenanceOperatingHoursDurations = new DoubleListInput("MaintenanceOperatingHoursDurations", "Maintenance", new DoubleVector());
		maintenanceOperatingHoursDurations.setValidRange(1e-15, Double.POSITIVE_INFINITY);
		maintenanceOperatingHoursDurations.setUnits("h");
		this.addInput(maintenanceOperatingHoursDurations, true);

		maintenanceOperatingHoursIntervals = new DoubleListInput("MaintenanceOperatingHoursIntervals", "Maintenance", new DoubleVector());
		maintenanceOperatingHoursIntervals.setValidRange(1e-15, Double.POSITIVE_INFINITY);
		maintenanceOperatingHoursIntervals.setUnits("h");
		this.addInput(maintenanceOperatingHoursIntervals, true);

		printToReport = new BooleanInput("PrintToReport", "Report", true);
		this.addInput(printToReport, true);
	}

	public ModelEntity() {
		presentState = "Idle";
		presentStateIndex = 0;
		hoursPerState = new DoubleVector();
		lastHistogramUpdateTime = 0.0;
		secondToLastHistogramUpdateTime = 0.0;
		lastStartTimePerState = new DoubleVector();
		secondToLastStartTimePerState = new DoubleVector();
		presentState = "";
		timeOfLastStateChange = 0.0;
		hoursForNextFailure = 0.0;
		iATFailure = 0.0;

		maintenancePendings = new IntegerVector( 1, 1 );

		maintenanceOperatingHoursPendings = new IntegerVector( 1, 1 );
		hoursForNextMaintenanceOperatingHours = new DoubleVector( 1, 1 );

		performMaintenanceAfterShipDelayPending = false;
		lastScheduledMaintenanceTimes = new DoubleVector();

		breakdownStartTime = 0.0;
		breakdownEndTime = Double.POSITIVE_INFINITY;
		breakdownPending = false;
		brokendown = false;
		associatedBreakdown = false;
		maintenanceStartTime = 0.0;
		maintenanceEndTime = Double.POSITIVE_INFINITY;
		maintenance = false;
		associatedMaintenance = false;
		workingHours = 0.0;
		stateMap = new HashMap<String, Integer>();
		timeOfLastStateChange = 0.0;

		// Populate the hash map for the states and state list indices
		for( int i = 0; i < getStateList().size(); i++ ) {
			stateMap.put( ((String)getStateList().get(i)).toLowerCase(), Integer.valueOf(i));
		}
	}

	/**
	 * Clear internal properties
	 */
	public void clearInternalProperties() {
		presentStateIndex = 0;
		hoursPerState.clear();
		presentState = "";
		timeOfLastStateChange = 0.0;
		hoursForNextFailure = 0.0;
		performMaintenanceAfterShipDelayPending = false;
		breakdownPending = false;
		brokendown = false;
		associatedBreakdown = false;
		maintenance = false;
		associatedMaintenance = false;
		workingHours = 0.0;
	}

	public void validate()
	throws InputErrorException {
		super.validate();
		this.validateMaintenance();
		Input.validateIndexedLists(firstMaintenanceOperatingHours.getValue(), maintenanceOperatingHoursIntervals.getValue(), "FirstMaintenanceOperatingHours", "MaintenanceOperatingHoursIntervals");
		Input.validateIndexedLists(firstMaintenanceOperatingHours.getValue(), maintenanceOperatingHoursDurations.getValue(), "FirstMaintenanceOperatingHours", "MaintenanceOperatingHoursDurations");

		if( getAvailability() < 1.0 ) {
			if( getDowntimeDurationDistribution() == null ) {
				throw new InputErrorException("When availability is less than one you must define downtimeDurationDistribution in your input file!");
			}
		}

		if( downtimeIATDistribution.getValue() != null ) {
			if( getDowntimeDurationDistribution() == null ) {
				throw new InputErrorException("When DowntimeIATDistribution is set, DowntimeDurationDistribution must also be set.");
			}
		}

		if( skipMaintenanceIfOverlap.getValue().size() > 0 )
			Input.validateIndexedLists(firstMaintenanceTimes.getValue(), skipMaintenanceIfOverlap.getValue(), "FirstMaintenanceTimes", "SkipMaintenanceIfOverlap");

		if( releaseEquipment.getValue() != null )
			Input.validateIndexedLists(firstMaintenanceTimes.getValue(), releaseEquipment.getValue(), "FirstMaintenanceTimes", "ReleaseEquipment");

		if( forceMaintenance.getValue() != null ) {
			Input.validateIndexedLists(firstMaintenanceTimes.getValue(), forceMaintenance.getValue(), "FirstMaintenanceTimes", "ForceMaintenance");
		}

		if(downtimeDurationDistribution.getValue() != null &&
		   downtimeDurationDistribution.getValue().getMinimumValue() < 0)
			throw new InputErrorException("DowntimeDurationDistribution cannot allow negative values");

		if(downtimeIATDistribution.getValue() != null &&
		   downtimeIATDistribution.getValue().getMinimumValue() < 0)
			throw new InputErrorException("DowntimeIATDistribution cannot allow negative values");
	}

	public void earlyInit() {
		super.earlyInit();

		if( downtimeDurationDistribution.getValue() != null ) {
			downtimeDurationDistribution.getValue().initialize();
		}

		if( downtimeIATDistribution.getValue() != null ) {
			downtimeIATDistribution.getValue().initialize();
		}
	}

	// ******************************************************************************************************
	// INPUT
	// ******************************************************************************************************

	public void validateMaintenance() {
		Input.validateIndexedLists(firstMaintenanceTimes.getValue(), maintenanceIntervals.getValue(), "FirstMaintenanceTimes", "MaintenanceIntervals");
		Input.validateIndexedLists(firstMaintenanceTimes.getValue(), maintenanceDurations.getValue(), "FirstMaintenanceTimes", "MaintenanceDurations");

		for( int i = 0; i < maintenanceIntervals.getValue().size(); i++ ) {
			if( maintenanceIntervals.getValue().get( i ) < maintenanceDurations.getValue().get( i ) ) {
				throw new InputErrorException("MaintenanceInterval should be greater than MaintenanceDuration  (%f) <= (%f)",
											  maintenanceIntervals.getValue().get(i), maintenanceDurations.getValue().get(i));
			}
		}
	}

	// ******************************************************************************************************
	// INITIALIZATION METHODS
	// ******************************************************************************************************

	public void clearStatistics() {

		for( int i = 0; i < getMaintenanceOperatingHoursIntervals().size(); i++ ) {
			hoursForNextMaintenanceOperatingHours.set( i, hoursForNextMaintenanceOperatingHours.get( i ) - this.getWorkingHours() );
		}

		if( getStateList().size() != 0 ) {
			hoursPerState.fillWithEntriesOf( getStateList().size(), 0.0 );
		}
		else {
			hoursPerState.clear();
		}
		timeOfLastStateChange = getCurrentTime();

		// Determine the time for the first breakdown event
		/*if ( downtimeIATDistribution == null ) {
			if( breakdownSeed != 0 ) {
				breakdownRandGen.initialiseWith( breakdownSeed );
				hoursForNextFailure = breakdownRandGen.getUniformFrom_To( 0.5*iATFailure, 1.5*iATFailure );
			} else {
				hoursForNextFailure = getNextBreakdownIAT();
			}
		}
		 else {
			hoursForNextFailure = getNextBreakdownIAT();
		}*/
	}

	public void initializeStatistics() {
		hoursPerState.fillWithEntriesOf( getStateList().size(), 0.0 );
		timeOfLastStateChange = getCurrentTime();
	}

	/**
	 * *!*!*!*! OVERLOAD !*!*!*!*
	 * Initialize statistics
	 */
	public void initialize() {

		brokendown = false;
		maintenance = false;
		associatedBreakdown = false;
		associatedMaintenance = false;

		// Create state trace file if required
		if (testFlag(FLAG_TRACESTATE)) {
			String fileName = InputAgent.getReportDirectory() +  InputAgent.getRunName() + "-" + this.getName() + ".trc";
			stateReportFile = new FileEntity( fileName, FileEntity.FILE_WRITE, false );
		}

		hoursPerState.fillWithEntriesOf( getStateList().size(), 0.0 );
		lastStartTimePerState.fillWithEntriesOf( getStateList().size(), 0.0 );
		secondToLastStartTimePerState.fillWithEntriesOf( getStateList().size(), 0.0 );
		timeOfLastStateChange = getCurrentTime();
		workingHours = 0.0;

		//  Calculate the average downtime duration if distributions are used
		double average = 0.0;
		if(getDowntimeDurationDistribution() != null)
			average = getDowntimeDurationDistribution().getExpectedValue();

		//  Calculate the average downtime inter-arrival time
		if( (getAvailability() == 1.0 || average == 0.0) ) {
			iATFailure = 10.0E10;
		}
		else {
			if( getDowntimeIATDistribution() != null ) {
				iATFailure = getDowntimeIATDistribution().getExpectedValue();

				// Adjust the downtime inter-arrival time to get the specified availability
				if( ! Tester.equalCheckTolerance( iATFailure, ( (average / (1.0 - getAvailability())) - average ) ) ) {
					getDowntimeIATDistribution().setValueFactor_For( ( (average / (1.0 - getAvailability())) - average) / iATFailure, this  );
					iATFailure = getDowntimeIATDistribution().getExpectedValue();
				}
			}
			else {
				iATFailure = ( (average / (1.0 - getAvailability())) - average );
			}
		}

		// Determine the time for the first breakdown event
		hoursForNextFailure = getNextBreakdownIAT();

		int ind = this.indexOfState( "Idle" );
		if( ind != -1 ) {
			this.setPresentState( "Idle" );
		}
		brokendown = false;

		//  Start the maintenance network
		if( firstMaintenanceTimes.getValue().size() != 0 ) {
			maintenancePendings.fillWithEntriesOf( firstMaintenanceTimes.getValue().size(), 0 );
			lastScheduledMaintenanceTimes.fillWithEntriesOf( firstMaintenanceTimes.getValue().size(), Double.POSITIVE_INFINITY );

			this.doMaintenanceNetwork();
		}

		// calculate hours for first operating hours breakdown
		for ( int i = 0; i < getMaintenanceOperatingHoursIntervals().size(); i++ ) {
			hoursForNextMaintenanceOperatingHours.add( firstMaintenanceOperatingHours.getValue().get( i ) );
			maintenanceOperatingHoursPendings.add( 0 );
		}
	}

	// ******************************************************************************************************
	// ACCESSOR METHODS
	// ******************************************************************************************************

	/**
	 * Return the time at which the most recent maintenance is scheduled to end
	 */
	public double getMaintenanceEndTime() {
		return maintenanceEndTime;
	}

	/**
	 * Return the time at which a the most recent breakdown is scheduled to end
	 */
	public double getBreakdownEndTime() {
		return breakdownEndTime;
	}

	public double getTimeOfLastStateChange() {
		return timeOfLastStateChange;
	}

	/**
	 * Returns the availability proportion.
	 */
	public double getAvailability() {
		return availability.getValue();
	}

	public DoubleListInput getFirstMaintenanceTimes() {
		return firstMaintenanceTimes;
	}

	public boolean getPrintToReport() {
		return printToReport.getValue();
	}

	public boolean isBrokendown() {
		return brokendown;
	}

	public boolean isBreakdownPending() {
		return breakdownPending;
	}

	public boolean isInAssociatedBreakdown() {
		return associatedBreakdown;
	}

	public boolean isInMaintenance() {
		return maintenance;
	}

	public boolean isInAssociatedMaintenance() {
		return associatedMaintenance;
	}

	public boolean isInService() {
		return ( brokendown || maintenance || associatedBreakdown || associatedMaintenance );
	}

	public void setBrokendown( boolean bool ) {
		brokendown = bool;
		this.setPresentState();
	}

	public void setMaintenance( boolean bool ) {
		maintenance = bool;
		this.setPresentState();
	}

	public void setAssociatedBreakdown( boolean bool ) {
		associatedBreakdown = bool;
	}

	public void setAssociatedMaintenance( boolean bool ) {
		associatedMaintenance = bool;
	}

	public ProbabilityDistribution getDowntimeDurationDistribution() {
		return downtimeDurationDistribution.getValue();
	}

	public double getDowntimeToReleaseEquipment() {
		return downtimeToReleaseEquipment.getValue();
	}

	public boolean hasServiceDefined() {
		return( maintenanceDurations.getValue().size() > 0 || getDowntimeDurationDistribution() != null );
	}

	// ******************************************************************************************************
	// HOURS AND STATES
	// ******************************************************************************************************

	/**
	 * Updates the statistics for the present status.
	 */
	public void updateHours() {

		if( presentState.length() == 0 ) {
			timeOfLastStateChange = getCurrentTime();
			return;
		}

		int index = this.indexOfState( presentState );
		if( index == -1 ) {
			throw new ErrorException( "Present state not found in StateList." );
		}
		if( presentStateIndex != index ) {
			throw new ErrorException( "presentStateIndex should be: " + index + ", but it is: " + presentStateIndex );
		}

		double dur = getCurrentTime() - timeOfLastStateChange;

		if( dur > 0.0 ) {
			/*int index = getStateList().indexOfString( presentState );
			if( index == -1 ) {
				throw new ErrorException( " Present state not found in StateList." );
			}

			hoursPerState.addAt( dur, index );*/
			if( !getStateList().get( presentStateIndex ).equals( presentState ) ) {
				throw new ErrorException( this + " Present state index ( " + presentStateIndex + " ) does not match present state ( " + presentState + " )." );
			}
			if( hoursPerState.size() <= presentStateIndex  ) {
				throw new ErrorException( this + " Present state index ( " + presentStateIndex + " ) does not exist in hoursPerState." );
			}

			hoursPerState.addAt( dur, presentStateIndex );
			timeOfLastStateChange = getCurrentTime();

			// Update working hours, if required
			//if( getWorkingStateList().contains( presentState ) ) {
			if( this.isWorking() ) {
				workingHours += dur;
			}
		}
	}

	/**
	 * Return true if the entity is working
	 */
	public boolean isWorking() {
		return false;
	}

	/**
	 * Returns the present status.
	 */
	public String getPresentState() {
		return presentState;
	}

	public void setPresentState() {}

	/**
	 * Updates the statistics, then sets the present status to be the specified value.
	 */
	public void setPresentState( String state ) {
		if( traceFlag ) this.trace("setState( "+state+" )");
		if( traceFlag ) this.traceLine(" Old State = "+presentState );

		if( ! presentState.equals( state ) ) {
			if (testFlag(FLAG_TRACESTATE)) this.printStateTrace(state);

			int ind = this.indexOfState( state );
			if( ind != -1 ) {
				this.updateHours();
				presentState = state;
				presentStateIndex = ind;
				if( lastStartTimePerState.size() > 0 ) {
					if( secondToLastStartTimePerState.size() > 0 ) {
						secondToLastStartTimePerState.set( ind, lastStartTimePerState.get( ind ) );
					}
					lastStartTimePerState.set( ind, getCurrentTime() );
				}
			}
			else {
				throw new ErrorException( this + " Specified state: " + state + " was not found in the StateList: " + this.getStateList() );
			}
		}
	}

	/**
	 * Print that state information on the trace state log file
	 */
	public void printStateTrace( String state ) {

		// First state ever
		if( finalLastState.equals("") ) {
			finalLastState = state;
			stateReportFile.putString(String.format("%.5f  %s.setState( \"%s\" ) dt = %s\n",
									  0.0d, this.getName(), presentState, formatNumber(getCurrentTime())));
			stateReportFile.flush();
			timeOfLastPrintedState = getCurrentTime();
		}
		else {

			// The final state in a sequence from the previous state change (one step behind)
			if ( ! Tester.equalCheckTimeStep( timeOfLastPrintedState, getCurrentTime() ) ) {
				stateReportFile.putString(String.format("%.5f  %s.setState( \"%s\" ) dt = %s\n",
										  timeOfLastPrintedState, this.getName(), finalLastState, formatNumber(getCurrentTime() - timeOfLastPrintedState)));
//				for( int i = 0; i < stateTraceRelatedModelEntities.size(); i++ ) {
//					ModelEntitiy each = (ModelEntitiy) stateTraceRelatedModelEntities.get( i );
//					putString( )
//				}
				stateReportFile.flush();
				timeOfLastPrintedState = getCurrentTime();
			}
			finalLastState = state;
		}
	}

	/**
	 * Returns the amount of time spent in the specified status.
	 */
	public double getHoursForState( String state ) {

		int index = this.indexOfState( state );
		if( index != -1 ) {
			this.updateHours();
			return hoursPerState.get( index );
		}
		else {
			throw new ErrorException( "Specified state: " + state + " was not found in the StateList." );
		}
	}

	/**
	 * Set the last time a histogram was updated for this entity
	 */
	public void setLastHistogramUpdateTime( double time ) {
		secondToLastHistogramUpdateTime = lastHistogramUpdateTime;
		lastHistogramUpdateTime = time;
	}

	/**
	 * Returns the time from the start of the start state to the start of the end state
	 */
	public double getTimeFromStartState_ToEndState( String startState, String endState) {

		// Determine the index of the start state
		int startIndex = this.indexOfState( startState );
		if( startIndex == -1 ) {
			throw new ErrorException( "Specified state: " + startState + " was not found in the StateList." );
		}

		// Determine the index of the end state
		int endIndex = this.indexOfState( endState );
		if( endIndex == -1 ) {
			throw new ErrorException( "Specified state: " + endState + " was not found in the StateList." );
		}

		// Is the start time of the end state greater or equal to the start time of the start state?
		if( lastStartTimePerState.get( endIndex ) >= lastStartTimePerState.get( startIndex ) ) {

			// If either time was not in the present cycle, return NaN
			if( lastStartTimePerState.get( endIndex ) <= lastHistogramUpdateTime ||
				lastStartTimePerState.get( startIndex ) <= lastHistogramUpdateTime ) {
				return Double.NaN;
			}
			// Return the time from the last start time of the start state to the last start time of the end state
			return lastStartTimePerState.get( endIndex ) - lastStartTimePerState.get( startIndex );
		}
		else {
			// If either time was not in the present cycle, return NaN
			if( lastStartTimePerState.get( endIndex ) <= lastHistogramUpdateTime ||
				secondToLastStartTimePerState.get( startIndex ) <= secondToLastHistogramUpdateTime ) {
				return Double.NaN;
			}
			// Return the time from the second to last start time of the start date to the last start time of the end state
			return lastStartTimePerState.get( endIndex ) - secondToLastStartTimePerState.get( startIndex );
		}
	}

	/**
	 * Return the commitment
	 */
	public double getCommitment() {
		return 1.0 - this.getFractionOfTimeForState( "Idle" );
	}

	/**
	 * Return the fraction of time for the given status
	 */
	public double getFractionOfTimeForState( String aState ) {
		if( hoursPerState.sum() > 0.0 ) {
			return ((this.getHoursForState( aState ) / hoursPerState.sum()) );
		}
		else {
			return 0.0;
		}
	}

	/**
	 * Return the percentage of time for the given status
	 */
	public double getPercentageOfTimeForState( String aState ) {
		if( hoursPerState.sum() > 0.0 ) {
			return ((this.getHoursForState( aState ) / hoursPerState.sum()) * 100.0);
		}
		else {
			return 0.0;
		}
	}

	/**
	 * Returns the number of hours the entity is in use.
	 * *!*!*!*! OVERLOAD !*!*!*!*
	 */
	public double getWorkingHours() {
		this.updateHours();
		return workingHours;
	}

	public DoubleVector getHours() {
		this.updateHours();
		return hoursPerState;
	}

	public Vector getStateList() {
		return stateList;
	}

	public int indexOfState( String state ) {
		Integer i = stateMap.get( state.toLowerCase() );
		if( i != null ) {
			return  i.intValue();
		}
		else {
			return -1;
		}
	}

	public DoubleVector getHoursPerState() {
		return hoursPerState;
	}

	// *******************************************************************************************************
	// MAINTENANCE METHODS
	// *******************************************************************************************************

	/**
	 * Perform tasks required before a maintenance period
	 */
	public void doPreMaintenance() {
	//@debug@ cr 'Entity should be overloaded' print
	}

	/**
	 * Start working again following a breakdown or maintenance period
	 */
	public void restart() {
	//@debug@ cr 'Entity should be overloaded' print
	}

	/**
	 * Disconnect routes, release truck assignments, etc. when performing maintenance or breakdown
	 */
	public void releaseEquipment() {}

	public boolean releaseEquipmentForMaintenanceSchedule( int index ) {

		if( releaseEquipment.getValue() == null )
			return true;

		return releaseEquipment.getValue().get( index );
	}

	public boolean forceMaintenanceSchedule( int index ) {

		if( forceMaintenance.getValue() == null )
			return false;

		return forceMaintenance.getValue().get( index );
	}

	/**
	 * Perform all maintenance schedules that are due
	 */
	public void doMaintenance() {

		// scheduled maintenance
		for( int index = 0; index < maintenancePendings.size(); index++ ) {
			if( this.getMaintenancePendings().get( index ) > 0 ) {
				if( traceFlag ) this.trace( "Starting Maintenance Schedule: " + index );
				this.doMaintenance(index);
			}
		}

		// Operating hours maintenance
		for( int index = 0; index < maintenanceOperatingHoursPendings.size(); index++ ) {
			if( this.getWorkingHours() > hoursForNextMaintenanceOperatingHours.get( index ) ) {
				hoursForNextMaintenanceOperatingHours.set(index, this.getWorkingHours() + getMaintenanceOperatingHoursIntervals().get( index ));
				maintenanceOperatingHoursPendings.addAt( 1, index );
				this.doMaintenanceOperatingHours(index);
			}
		}
	}

	/**
	 * Perform all the planned maintenance that is due for the given schedule
	 */
	public void doMaintenance( int index ) {

		double wait;
		if( masterMaintenanceEntity != null ) {
			wait = masterMaintenanceEntity.getMaintenanceDurations().getValue().get( index );
		}
		else {
			wait = this.getMaintenanceDurations().getValue().get( index );
		}

		if( wait > 0.0 && maintenancePendings.get( index ) != 0 ) {

			if( traceFlag ) this.trace( "ModelEntity.doMaintenance_Wait() -- start of maintenance" );

			// Keep track of the start and end of maintenance times
			maintenanceStartTime = getCurrentTime();

			if( masterMaintenanceEntity != null ) {
				maintenanceEndTime = maintenanceStartTime + ( maintenancePendings.get( index ) * masterMaintenanceEntity.getMaintenanceDurations().getValue().get( index ) );
			}
			else {
				maintenanceEndTime = maintenanceStartTime + ( maintenancePendings.get( index ) * maintenanceDurations.getValue().get( index ) );
			}

			this.setPresentState( "Maintenance" );
			maintenance = true;
			this.doPreMaintenance();

			// Release equipment if necessary
			if( this.releaseEquipmentForMaintenanceSchedule( index ) ) {
				this.releaseEquipment();
			}

			while( maintenancePendings.get( index ) != 0 ) {
				maintenancePendings.subAt( 1, index );
				scheduleWait( wait );

				// If maintenance pending goes negative, something is wrong
				if( maintenancePendings.get( index ) < 0 ) {
					this.error( "ModelEntity.doMaintenance_Wait()", "Maintenace pending should not be negative", "maintenacePending = "+maintenancePendings.get( index ) );
				}
			}
			if( traceFlag ) this.trace( "ModelEntity.doMaintenance_Wait() -- end of maintenance" );

			//  The maintenance is over
			this.setPresentState( "Idle" );
			maintenance = false;
			this.restart();
		}
	}

	/**
	 * Perform all the planned maintenance that is due
	 */
	public void doMaintenanceOperatingHours( int index ) {
		if(maintenanceOperatingHoursPendings.get( index ) == 0 )
			return;

		if( traceFlag ) this.trace( "ModelEntity.doMaintenance_Wait() -- start of maintenance" );

		// Keep track of the start and end of maintenance times
		maintenanceStartTime = getCurrentTime();
		maintenanceEndTime = maintenanceStartTime +
		   (maintenanceOperatingHoursPendings.get( index ) * getMaintenanceOperatingHoursDurationFor(index));

		this.setPresentState( "Maintenance" );
		maintenance = true;
		this.doPreMaintenance();

		while( maintenanceOperatingHoursPendings.get( index ) != 0 ) {
			//scheduleWait( maintenanceDurations.get( index ) );
			scheduleWait( maintenanceEndTime - maintenanceStartTime );
			maintenanceOperatingHoursPendings.subAt( 1, index );

			// If maintenance pending goes negative, something is wrong
			if( maintenanceOperatingHoursPendings.get( index ) < 0 ) {
				this.error( "ModelEntity.doMaintenance_Wait()", "Maintenace pending should not be negative", "maintenacePending = "+maintenanceOperatingHoursPendings.get( index ) );
			}

		}
		if( traceFlag ) this.trace( "ModelEntity.doMaintenance_Wait() -- end of maintenance" );

		//  The maintenance is over
		maintenance = false;
		this.setPresentState( "Idle" );
		this.restart();
	}

	/**
	 * Check if a maintenance is due.  if so, try to perform the maintenance
	 */
	public boolean checkMaintenance() {
		if( traceFlag ) this.trace( "checkMaintenance()" );
		if( checkOperatingHoursMaintenance() ) {
			return true;
		}

		// List of all entities going to maintenance
		ArrayList<ModelEntity> sharedMaintenanceEntities;

		// This is not a master maintenance entity
		if( masterMaintenanceEntity != null ) {
			sharedMaintenanceEntities = masterMaintenanceEntity.getSharedMaintenanceList();
		}

		// This is a master maintenance entity
		else {
			sharedMaintenanceEntities = getSharedMaintenanceList();
		}

		// If this entity is in shared maintenance relation with a group of entities
		if( sharedMaintenanceEntities.size() > 0 || masterMaintenanceEntity != null ) {

			// Are all entities in the group ready for maintenance
			if( this.areAllEntitiesAvailable() ) {

				// For every entity in the shared maintenance list plus the master maintenance entity
				for( int i=0; i <= sharedMaintenanceEntities.size(); i++ ) {
					ModelEntity aModel;

					// Locate master maintenance entity( after all entity in shared maintenance list have been taken care of )
					if( i == sharedMaintenanceEntities.size() ) {

						// This entity is manster maintenance entity
						if( masterMaintenanceEntity == null ) {
							aModel = this;
						}

						// This entity is on the shared maintenannce list of the master maintenance entity
						else {
							aModel = masterMaintenanceEntity;
						}
					}

					// Next entity in the shared maintenance list
					else {
						aModel = sharedMaintenanceEntities.get( i );
					}

					// Check for aModel maintenances
					for( int index = 0; index < maintenancePendings.size(); index++ ) {
						if( aModel.getMaintenancePendings().get( index ) > 0 ) {
							if( traceFlag ) this.trace( "Starting Maintenance Schedule: " + index );
							aModel.startProcess("doMaintenance", index);
						}
					}
				}
				return true;
			}
			else {
				return false;
			}
		}

		// This block is maintained indipendently
		else {

			//  Check for maintenances
			for( int i = 0; i < maintenancePendings.size(); i++ ) {
				if( maintenancePendings.get( i ) > 0 ) {
					if( this.canStartMaintenance( i ) ) {
						if( traceFlag ) this.trace( "Starting Maintenance Schedule: " + i );
						this.startProcess("doMaintenance", i);
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Determine how many hours of maintenance is scheduled between startTime and endTime
	 */
	public double getScheduledMaintenanceHoursForPeriod( double startTime, double endTime ) {
		if( traceFlag ) this.trace("Handler.getScheduledMaintenanceHoursForPeriod( "+startTime+", "+endTime+" )" );

		double totalHours = 0.0;
		double firstTime = 0.0;

		// Add on hours for all pending maintenance
		for( int i=0; i < maintenancePendings.size(); i++ ) {
			totalHours += maintenancePendings.get( i ) * maintenanceDurations.getValue().get( i );
		}

		if( traceFlag ) this.traceLine( "Hours of pending maintenances="+totalHours );

		// Add on hours for all maintenance scheduled to occur in the given period from startTime to endTime
		for( int i=0; i < maintenancePendings.size(); i++ ) {

			// Find the first time that maintenance is scheduled after startTime
			firstTime = firstMaintenanceTimes.getValue().get( i );
			while( firstTime < startTime ) {
				firstTime += maintenanceIntervals.getValue().get( i );
			}
			if( traceFlag ) this.traceLine(" first time maintenance "+i+" is scheduled after startTime= "+firstTime );

			// Now have the first maintenance start time after startTime
			// Add all maintenances that lie in the given interval
			while( firstTime < endTime ) {
				if( traceFlag ) this.traceLine(" Checking for maintenances for period:"+firstTime+" to "+endTime );
				// Add the maintenance
				totalHours += maintenanceDurations.getValue().get( i );

				// Update the search period
				endTime += maintenanceDurations.getValue().get( i );

				// Look for next maintenance in new interval
				firstTime += maintenanceIntervals.getValue().get( i );
				if( traceFlag ) this.traceLine(" Adding Maintenance duration = "+maintenanceDurations.getValue().get( i ) );
			}
		}

		// Return the total hours of maintenance scheduled from startTime to endTime
		if( traceFlag ) this.traceLine( "Maintenance hours to add= "+totalHours );
		return totalHours;
	}

	public boolean checkOperatingHoursMaintenance() {
		if( traceFlag ) this.trace("checkOperatingHoursMaintenance()");

		//  Check for maintenances
		for( int i = 0; i < getMaintenanceOperatingHoursIntervals().size(); i++ ) {

			// If the entity is not available, maintenance cannot start
			if( ! this.canStartMaintenance( i ) )
				continue;

			if( this.getWorkingHours() > hoursForNextMaintenanceOperatingHours.get( i ) ) {
				hoursForNextMaintenanceOperatingHours.set(i, (this.getWorkingHours() + getMaintenanceOperatingHoursIntervals().get( i )));
				maintenanceOperatingHoursPendings.addAt( 1, i );

				if( traceFlag ) this.trace( "Starting Maintenance Operating Hours Schedule : " + i );
				this.startProcess("doMaintenanceOperatingHours", i);
				return true;
			}
		}
		return false;
	}

	/**
	 * Wrapper method for doMaintenance_Wait.
	 */
	public void doMaintenanceNetwork() {
		this.startProcess("doMaintenanceNetwork_Wait");
	}

	/**
	 * Network for planned maintenance.
	 * This method should be called in the initialize method of the specific entity.
	 */
	public void doMaintenanceNetwork_Wait() {

		// Initialize schedules
		for( int i=0; i < maintenancePendings.size(); i++ ) {
			maintenancePendings.set( i, 0 );
		}
		nextMaintenanceTimes = new DoubleVector(firstMaintenanceTimes.getValue());
		nextMaintenanceDuration = 0;

		// Find the next maintenance event
		int index = 0;
		double earliestTime = Double.POSITIVE_INFINITY;
		for( int i=0; i < nextMaintenanceTimes.size(); i++ ) {
			double time = nextMaintenanceTimes.get( i );
			if( Tester.lessCheckTolerance( time, earliestTime ) ) {
				earliestTime = time;
				index = i;
				nextMaintenanceDuration = maintenanceDurations.getValue().get( i );
			}
		}

		// Make sure that maintenance for entities on the shared list are being called after those entities have been initialize (AT TIME ZERO)
		scheduleLastLIFO();
		while( true ) {

			double dt = earliestTime - getCurrentTime();

			// Wait for the maintenance check time
			if( dt > Process.getEventTolerance() ) {
				scheduleWait( dt );
			}

			// Increment the number of maintenances due for the entity
			maintenancePendings.addAt( 1, index );

			// If this is a master maintenance entity
			if (getSharedMaintenanceList().size() > 0) {

				// If all the entities on the shared list are ready for maintenance
				if( this.areAllEntitiesAvailable() ) {

					// Put this entity to maintenance
					if( traceFlag ) this.trace( "Starting Maintenance Schedule: " + index );
					this.startProcess("doMaintenance", index);
				}
			}

			// If this entity is maintained independently
			else {

				// Do maintenance if possible
				if( ! this.isInService() && this.canStartMaintenance( index ) ) {
					// if( traceFlag ) this.trace( "doMaintenanceNetwork_Wait: Starting Maintenance.  PresentState = "+presentState+" IsAvailable? = "+this.isAvailable() );
					if( traceFlag ) this.trace( "Starting Maintenance Schedule: " + index );
					this.startProcess("doMaintenance", index);
				}
				// Keep track of the time the maintenance was attempted
				else {
					lastScheduledMaintenanceTimes.set( index, getCurrentTime() );

					// If skipMaintenance was defined, cancel the maintenance
					if( this.shouldSkipMaintenance( index ) ) {

						// if a different maintenance is due, cancel this maintenance
						boolean cancelMaintenance = false;
						for( int i=0; i < maintenancePendings.size(); i++ ) {
							if( i != index ) {
								if( maintenancePendings.get( i ) > 0 ) {
									cancelMaintenance = true;
									break;
								}
							}
						}

						if( cancelMaintenance || this.isInMaintenance() ) {
							maintenancePendings.subAt( 1, index );
						}
					}

					// Do a check after the limit has expired
					if( this.getDeferMaintenanceLimit( index ) > 0.0 ) {
						this.startProcess( "scheduleCheckMaintenance", this.getDeferMaintenanceLimit( index ) );
					}
				}
			}

			// Determine the next maintenance time
			nextMaintenanceTimes.addAt( maintenanceIntervals.getValue().get( index ), index );

			// Find the next maintenance event
			index = 0;
			earliestTime = Double.POSITIVE_INFINITY;
			for( int i=0; i < nextMaintenanceTimes.size(); i++ ) {
				double time = nextMaintenanceTimes.get( i );
				if( Tester.lessCheckTolerance( time, earliestTime ) ) {
					earliestTime = time;
					index = i;
					nextMaintenanceDuration = maintenanceDurations.getValue().get( i );
				}
			}
		}
	}

	public double getDeferMaintenanceLimit( int index ) {

		if( deferMaintenanceLimit.getValue() == null )
			return 0.0d;

		return deferMaintenanceLimit.getValue().get( index );
	}

	public void scheduleCheckMaintenance( double wait ) {
		scheduleWait( wait );
		this.checkMaintenance();
	}

	public boolean shouldSkipMaintenance( int index ) {

		if( skipMaintenanceIfOverlap.getValue().size() == 0 )
			return false;

		return skipMaintenanceIfOverlap.getValue().get( index );
	}

	/**
	 * Return TRUE if there is a pending maintenance for any schedule
	 */
	public boolean isMaintenancePending() {
		for( int i = 0; i < maintenancePendings.size(); i++ ) {
			if( maintenancePendings.get( i ) > 0 ) {
				return true;
			}
		}

		for( int i = 0; i < hoursForNextMaintenanceOperatingHours.size(); i++ ) {
			if( this.getWorkingHours() > hoursForNextMaintenanceOperatingHours.get( i ) ) {
				return true;
			}
		}

		return false;
	}

	public boolean isForcedMaintenancePending() {

		if( forceMaintenance.getValue() == null )
			return false;

		for( int i = 0; i < maintenancePendings.size(); i++ ) {
			if( maintenancePendings.get( i ) > 0 && forceMaintenance.getValue().get(i) ) {
				return true;
			}
		}
		return false;
	}

	public ArrayList<ModelEntity> getSharedMaintenanceList () {
		return sharedMaintenanceList.getValue();
	}

	public IntegerVector getMaintenancePendings () {
		return maintenancePendings;
	}

	public DoubleListInput getMaintenanceDurations() {
		return maintenanceDurations;
	}


	/**
	 * Return the start of the next scheduled maintenance time if not in maintenance,
	 * or the start of the current scheduled maintenance time if in maintenance
	 */
	public double getNextMaintenanceStartTime() {
		if( nextMaintenanceTimes == null )
			return Double.POSITIVE_INFINITY;
		else
			return nextMaintenanceTimes.getMin();
	}

	/**
	 * Return the duration of the next maintenance event (assuming only one pending)
	 */
	public double getNextMaintenanceDuration() {
		return nextMaintenanceDuration;
	}

	// Shows if an Entity would ever go on service
	public boolean hasServiceScheduled() {
		if( firstMaintenanceTimes.getValue().size() != 0 || masterMaintenanceEntity != null ) {
			return true;
		}
		return false;
	}
	public void setMasterMaintenanceBlock( ModelEntity aModel ) {
		masterMaintenanceEntity =  aModel;
	}

	// *******************************************************************************************************
	// BREAKDOWN METHODS
	// *******************************************************************************************************

	/**
	 * No Comments Given.
	 */
	public void calculateTimeOfNextFailure() {
		hoursForNextFailure = (this.getWorkingHours() + this.getNextBreakdownIAT());
	}

	/**
	 * Activity Network for Breakdowns.
	 */
	public void doBreakdown() {
	}

	/**
	 * Prints the header for the entity's state list.
	 * @return bottomLine contains format for each column of the bottom line of the group report
	 */
	public IntegerVector printUtilizationHeaderOn( FileEntity anOut ) {

		IntegerVector bottomLine = new IntegerVector();

		if( getStateList().size() != 0 ) {
			anOut.putStringTabs( "Name", 1 );
			bottomLine.add( ReportAgent.BLANK );

			int doLoop = getStateList().size();
			for( int x = 0; x < doLoop; x++ ) {
				String state = (String)getStateList().get( x );
				anOut.putStringTabs( state, 1 );
				bottomLine.add( ReportAgent.AVERAGE_PCT_ONE_DEC );
			}
			anOut.newLine();
		}
		return bottomLine;
	}

	/**
	 * Print the entity's name and percentage of hours spent in each state.
	 * @return columnValues are the values for each column in the group report (0 if the value is a String)
	 */
	public DoubleVector printUtilizationOn( FileEntity anOut ) {

		double total;
		DoubleVector columnValues = new DoubleVector();

		this.updateHours();
		if( hoursPerState.size() != 0 ) {
			total = hoursPerState.sum();
			if( !(total == 0.0) ) {
				this.updateHours();
				anOut.putStringTabs( getName(), 1 );
				columnValues.add( 0.0 );

				for( int i = 0; i < hoursPerState.size(); i++ ) {
					double value = hoursPerState.get( i ) / total;
					anOut.putDoublePercentWithDecimals( value, 1 );
					anOut.putTabs( 1 );
					columnValues.add( value );
				}
				anOut.newLine();
			}
		}
		return columnValues;
	}

	/**
	 * This method must be overridden in any subclass of ModelEntity.
	 */
	public boolean isAvailable() {
		throw new ErrorException( "Must override isAvailable in any subclass of ModelEntity." );
	}

	/**
	 * This method must be overridden in any subclass of ModelEntity.
	 */
	public boolean canStartMaintenance( int index ) {
		return isAvailable();
	}

	/**
	 * This method must be overridden in any subclass of ModelEntity.
	 */
	public boolean canStartForcedMaintenance() {
		return isAvailable();
	}

	/**
	 * This method must be overridden in any subclass of ModelEntity.
	 */
	public boolean areAllEntitiesAvailable() {
		throw new ErrorException( "Must override areAllEntitiesAvailable in any subclass of ModelEntity." );
	}

	/**
	 * Return the time of the next breakdown duration
	 */
	public double getBreakdownDuration() {
		// if( traceFlag ) this.trace( "getBreakdownDuration()" );

		//  If a distribution was specified, then select a duration randomly from the distribution
		if ( getDowntimeDurationDistribution() != null ) {
			return getDowntimeDurationDistribution().nextValue();
		}
		else {
			return 0.0;
		}
	}

	/**
	 * Return the time of the next breakdown IAT
	 */
	public double getNextBreakdownIAT() {
		if( getDowntimeIATDistribution() != null ) {
			return getDowntimeIATDistribution().nextValue();
		}
		else {
			return iATFailure;
		}
	}

	public double getHoursForNextFailure() {
		return hoursForNextFailure;
	}

	public void setHoursForNextFailure( double hours ) {
		hoursForNextFailure = hours;
	}

	/**
	 Returns a vector of strings describing the ModelEntity.
	 Override to add details
	 @return Vector - tab delimited strings describing the DisplayEntity
	 **/
	public Vector getInfo() {
		Vector info = super.getInfo();
		if( presentState == null || presentState.equals( "" ) )
			info.addElement( "Present State\t<no state>" );
		else
			info.addElement( "Present State" + "\t" + presentState );
		return info;
	}

	protected DoubleVector getMaintenanceOperatingHoursIntervals() {
		return maintenanceOperatingHoursIntervals.getValue();
	}

	protected double getMaintenanceOperatingHoursDurationFor(int index) {
		return maintenanceOperatingHoursDurations.getValue().get(index);
	}

	protected ProbabilityDistribution getDowntimeIATDistribution() {
		return downtimeIATDistribution.getValue();
	}
}