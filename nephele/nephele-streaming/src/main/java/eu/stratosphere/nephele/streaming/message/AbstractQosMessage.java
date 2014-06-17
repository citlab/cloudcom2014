/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/

package eu.stratosphere.nephele.streaming.message;

import eu.stratosphere.nephele.jobgraph.JobID;

/**
 * Abstract base class to be used to for exchanging messages between the
 * different subcomponents of the nephele-streaming component.
 * 
 * @author Bjoern Lohrmann
 */
public abstract class AbstractQosMessage {

	/**
	 * The ID of the job this piece of streaming data refers to
	 */
	private JobID jobID;

	public AbstractQosMessage(JobID jobID) {
		if (jobID == null) {
			throw new IllegalArgumentException("jobID must not be null");
		}

		this.jobID = jobID;
	}

	/**
	 * Empty default constructor.
	 */
	public AbstractQosMessage() {
	}

	public JobID getJobID() {

		return this.jobID;
	}
	
	public void setJobID(JobID jobID) {
		this.jobID = jobID;
	}
}
