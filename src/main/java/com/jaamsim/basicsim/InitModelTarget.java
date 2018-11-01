/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2014 Ausenco Engineering Canada Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jaamsim.basicsim;

import com.jaamsim.events.EventManager;
import com.jaamsim.events.ProcessTarget;

public class InitModelTarget extends ProcessTarget {
	public InitModelTarget() {}

	@Override
	public String getDescription() {
		return "SimulationInit";
	}

	@Override
	public void process() {

		// Initialise each entity
		for (Entity each : Entity.getClonesOfIterator(Entity.class)) {
			each.earlyInit();
		}

		// Initialise each entity a second time
		for (Entity each : Entity.getClonesOfIterator(Entity.class)) {
			each.lateInit();
		}

		// Start each entity
		double startTime = Simulation.getStartTime();
		for (Entity each : Entity.getClonesOfIterator(Entity.class)) {
			if (!each.isActive())
				continue;
			EventManager.scheduleSeconds(startTime, 0, true, new StartUpTarget(each), null);
		}

		// Schedule the initialisation period
		if (Simulation.getInitializationTime() > 0.0) {
			double clearTime = startTime + Simulation.getInitializationTime();
			EventManager.scheduleSeconds(clearTime, 5, false, new ClearStatisticsTarget(), null);
		}

		// Schedule the end of the simulation run
		double endTime = Simulation.getEndTime();
		EventManager.scheduleSeconds(endTime, 5, false, new EndModelTarget(), null);

		// Start checking the pause condition
		Simulation.getInstance().doPauseCondition();
	}
}
