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

package eu.stratosphere.nephele.streaming.taskmanager.runtime;

import eu.stratosphere.nephele.execution.RuntimeEnvironment;
import eu.stratosphere.nephele.template.AbstractGenericInputTask;
import eu.stratosphere.nephele.template.AbstractInvokable;

/**
 * This class provides a wrapper for Nephele tasks of the type
 * {@link AbstractGenericInputTask}.
 * <p>
 * This class is thread-safe.
 * 
 * @author warneke, Bjoern Lohrmann
 */
public final class StreamInputTaskWrapper extends AbstractGenericInputTask {

	private volatile AbstractInvokable wrappedInvokable = null;

	private volatile StreamTaskEnvironment wrappedEnvironment = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void registerInputOutput() {
		// registerInputOutput() is called by the RuntimeEnvironment at the time
		// of its
		// instantiation. Before registerInputOutput() is called the
		// RuntimeEnvironment has set
		// itself using setEnvironment(). Here we replace the RuntimeEnvironment
		// with its
		// wrapped instance.
		this.wrappedEnvironment = WrapperUtils
				.getWrappedEnvironment((RuntimeEnvironment) this
						.getEnvironment());
		this.setEnvironment(this.wrappedEnvironment);

		this.wrappedInvokable = WrapperUtils
				.getWrappedInvokable(this.wrappedEnvironment);
		this.wrappedInvokable.registerInputOutput();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void invoke() throws Exception {
		this.wrappedInvokable.invoke();
	}
}
