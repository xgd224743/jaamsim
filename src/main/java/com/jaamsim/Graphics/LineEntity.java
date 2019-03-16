/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2018-2019 JaamSim Software Inc.
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
package com.jaamsim.Graphics;

import java.util.ArrayList;
import java.util.Collections;

import com.jaamsim.basicsim.JaamSimModel;
import com.jaamsim.input.ColourInput;
import com.jaamsim.math.Color4d;
import com.jaamsim.ui.GUIFrame;

public interface LineEntity {
	public JaamSimModel getJaamSimModel();
	public boolean isOutlined();
	public int getLineWidth();
	public Color4d getLineColour();

	public static ArrayList<Color4d> getLineColoursInUse() {
		ArrayList<Color4d> ret = new ArrayList<>();
		JaamSimModel simModel = GUIFrame.getJaamSimModel();
		for (DisplayEntity ent : simModel.getClonesOfIterator(DisplayEntity.class, LineEntity.class)) {
			LineEntity lineEnt = (LineEntity) ent;
			if (ret.contains(lineEnt.getLineColour()))
				continue;
			ret.add(lineEnt.getLineColour());
		}
		Collections.sort(ret, ColourInput.colourComparator);
		return ret;
	}

}