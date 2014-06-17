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

package eu.stratosphere.nephele.streaming;

import eu.stratosphere.nephele.plugins.AbstractPluginLoader;
import eu.stratosphere.nephele.plugins.JobManagerPlugin;
import eu.stratosphere.nephele.plugins.PluginID;
import eu.stratosphere.nephele.plugins.PluginLookupService;
import eu.stratosphere.nephele.plugins.TaskManagerPlugin;
import eu.stratosphere.nephele.streaming.jobmanager.StreamJobManagerPlugin;
import eu.stratosphere.nephele.streaming.taskmanager.StreamTaskManagerPlugin;

/**
 * This class implements the loader functionality for the Nephele streaming
 * plugin.
 * <p>
 * This class is thread-safe.
 * 
 * @author warneke
 */
public final class StreamingPluginLoader extends AbstractPluginLoader {

	/**
	 * The job manager component of this plugin.
	 */
	private StreamJobManagerPlugin jobManagerPlugin = null;

	/**
	 * The task manager component of this plugin.
	 */
	private StreamTaskManagerPlugin taskManagerPlugin = null;

	/**
	 * The ID of this plugin.
	 */
	public static final PluginID STREAMING_PLUGIN_ID = PluginID
			.fromByteArray(new byte[] { 0x3c, 0x00, 0x00, -0x1b, 0x38, 0x4a,
					0x60, -0x61, -0x25, 0x00, 0x00, 0x16, 0x00, 0x18, 0x7f,
					0x01 });

	/**
	 * Constructs a loader for the Nephele streaming plugin.
	 * 
	 * @param pluginName
	 *            the name of the plugin as specified in the plugin
	 *            configuration file
	 * @param pluginConfiguration
	 *            the configuration of this plugin
	 * @param pluginLookupService
	 *            the lookup service to locate the remote components of this
	 *            plugin
	 */
	public StreamingPluginLoader(final String pluginName,
			final PluginLookupService pluginLookupService) {
		super(pluginName, pluginLookupService);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized JobManagerPlugin getJobManagerPlugin() {

		if (this.jobManagerPlugin == null) {
			this.jobManagerPlugin = new StreamJobManagerPlugin(STREAMING_PLUGIN_ID);
		}

		return this.jobManagerPlugin;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized TaskManagerPlugin getTaskManagerPlugin() {

		if (this.taskManagerPlugin == null) {
			this.taskManagerPlugin = new StreamTaskManagerPlugin();
		}

		return this.taskManagerPlugin;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PluginID getPluginID() {
		return STREAMING_PLUGIN_ID;
	}
}
