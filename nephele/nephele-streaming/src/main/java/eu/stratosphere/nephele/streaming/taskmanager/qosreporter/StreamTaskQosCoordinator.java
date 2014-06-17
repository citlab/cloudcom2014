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
package eu.stratosphere.nephele.streaming.taskmanager.qosreporter;

import java.util.ArrayList;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import eu.stratosphere.nephele.io.channels.ChannelID;
import eu.stratosphere.nephele.streaming.message.action.EdgeQosReporterConfig;
import eu.stratosphere.nephele.streaming.message.action.LimitBufferSizeAction;
import eu.stratosphere.nephele.streaming.message.action.VertexQosReporterConfig;
import eu.stratosphere.nephele.streaming.message.qosreport.DummyVertexReporterActivity;
import eu.stratosphere.nephele.streaming.taskmanager.qosmodel.QosReporterID;
import eu.stratosphere.nephele.streaming.taskmanager.qosreporter.listener.QosReportingListenerHelper;
import eu.stratosphere.nephele.streaming.taskmanager.runtime.StreamTaskEnvironment;
import eu.stratosphere.nephele.streaming.taskmanager.runtime.io.StreamInputGate;
import eu.stratosphere.nephele.streaming.taskmanager.runtime.io.StreamOutputGate;
import eu.stratosphere.nephele.taskmanager.runtime.RuntimeTask;
import eu.stratosphere.nephele.types.Record;

/**
 * An instance of this class implements Qos data reporting for a specific vertex
 * and its ingoing/outgoing edges on a task manager while the vertex actually
 * runs.
 * 
 * This class is thread-safe.
 * 
 * @author Bjoern Lohrmann
 * 
 */
public class StreamTaskQosCoordinator implements QosReporterConfigListener {

	private static final Log LOG = LogFactory
			.getLog(StreamTaskQosCoordinator.class);

	private final RuntimeTask task;

	private final StreamTaskEnvironment taskEnvironment;

	private final QosReportForwarderThread reporterThread;
	
	private final QosReporterConfigCenter reporterConfigCenter;

	/**
	 * For each input gate of the task for whose channels latency reporting is
	 * required, this list contains a InputGateReporterManager. A
	 * InputGateReporterManager keeps track of and reports on the latencies for
	 * all of the input gate's channels. This is a sparse list (may contain
	 * nulls), indexed by the runtime gate's own indices.
	 */
	private ArrayList<InputGateReporterManager> inputGateReporters;

	/**
	 * For each output gate of the task for whose output channels QoS statistics
	 * are required (throughput, output buffer lifetime, ...), this list
	 * contains a OutputGateReporterManager. Each OutputGateReporterManager
	 * keeps track of and reports on Qos statistics all of the output gate's
	 * channels and also attaches tags to records sent via its channels. This is
	 * a sparse list (may contain nulls), indexed by the runtime gate's own
	 * indices.
	 */
	private ArrayList<OutputGateReporterManager> outputGateReporters;

	/**
	 * For each input/output gate combination for which Qos reports are
	 * required, this VertexLatencyReportManager creates the reports.
	 */
	private VertexLatencyReportManager vertexLatencyManager;

	private boolean isShutdown;

	public StreamTaskQosCoordinator(RuntimeTask task,
			StreamTaskEnvironment taskEnvironment,
			QosReportForwarderThread reportForwarder) {

		this.task = task;
		this.taskEnvironment = taskEnvironment;
		this.reporterThread = reportForwarder;
		this.reporterConfigCenter = reportForwarder.getConfigCenter();

		this.vertexLatencyManager = new VertexLatencyReportManager(
				this.reporterThread,
				this.taskEnvironment.getNumberOfInputGates(),
				this.taskEnvironment.getNumberOfOutputGates());
		this.inputGateReporters = new ArrayList<InputGateReporterManager>();
		this.outputGateReporters = new ArrayList<OutputGateReporterManager>();
		this.isShutdown = false;

		this.prepareQosReporting();
	}

	private void prepareQosReporting() {
		this.installVertexLatencyReporters();
		this.installInputGateListeners();
		this.installOutputGateListeners();
	}

	private void installVertexLatencyReporters() {
		Set<VertexQosReporterConfig> vertexReporterConfigs = this.reporterConfigCenter
				.getVertexQosReporters(this.task.getVertexID());

		if (vertexReporterConfigs.isEmpty()) {
			this.reporterConfigCenter.setQosReporterConfigListener(
					this.task.getVertexID(), this);
		} else {
			for (VertexQosReporterConfig reporterConfig : vertexReporterConfigs) {
				if (reporterConfig.isDummy()) {
					this.announceDummyReporter(reporterConfig.getReporterID());
				} else {
					this.installVertexLatencyReporter(reporterConfig);
				}
			}
		}
	}

	private void announceDummyReporter(QosReporterID.Vertex reporterID) {
		this.reporterThread.addToNextReport(new DummyVertexReporterActivity(
				reporterID));
	}

	private void installVertexLatencyReporter(
			VertexQosReporterConfig reporterConfig) {

		QosReporterID.Vertex reporterID = reporterConfig.getReporterID();

		if (this.vertexLatencyManager.containsReporter(reporterID)) {
			return;
		}

		StreamInputGate<? extends Record> inputGate = this.taskEnvironment
				.getInputGate(reporterConfig.getInputGateID());

		StreamOutputGate<? extends Record> outputGate = this.taskEnvironment
				.getOutputGate(reporterConfig.getOutputGateID());

		this.vertexLatencyManager.addReporter(inputGate.getIndex(),
				outputGate.getIndex(), reporterID);

		QosReportingListenerHelper.listenToVertexLatencyOnInputGate(inputGate,
				this.vertexLatencyManager);
		QosReportingListenerHelper.listenToVertexLatencyOnOutputGate(
				outputGate, this.vertexLatencyManager);
	}

	private void installInputGateListeners() {
		for (int i = 0; i < this.taskEnvironment.getNumberOfInputGates(); i++) {
			StreamInputGate<? extends Record> inputGate = this.taskEnvironment
					.getInputGate(i);

			// as constraints are defined on job graph level, it is safe to only
			// test one channel
			boolean mustReportQosForGate = this.reporterConfigCenter
					.getEdgeQosReporter(inputGate.getInputChannel(0)
							.getConnectedChannelID()) != null;

			if (!mustReportQosForGate) {
				this.inputGateReporters.add(null);
				this.reporterConfigCenter.setQosReporterConfigListener(
						inputGate.getGateID(), this);
				break;
			}

			InputGateReporterManager reporter = new InputGateReporterManager(
					this.reporterThread, inputGate.getNumberOfInputChannels());
			this.inputGateReporters.add(reporter);

			for (int j = 0; j < inputGate.getNumberOfInputChannels(); j++) {
				int runtimeChannelIndex = inputGate.getInputChannel(j)
						.getChannelIndex();
				ChannelID sourceChannelID = inputGate.getInputChannel(j)
						.getConnectedChannelID();

				EdgeQosReporterConfig edgeReporter = this.reporterConfigCenter
						.getEdgeQosReporter(sourceChannelID);
				QosReporterID.Edge reporterID = (QosReporterID.Edge) edgeReporter
						.getReporterID();
				reporter.addEdgeQosReporterConfig(runtimeChannelIndex,
						reporterID);
			}

			QosReportingListenerHelper.listenToChannelLatenciesOnInputGate(
					inputGate, reporter);
		}
	}

	private void installOutputGateListeners() {
		for (int i = 0; i < this.taskEnvironment.getNumberOfOutputGates(); i++) {
			StreamOutputGate<? extends Record> outputGate = this.taskEnvironment
					.getOutputGate(i);

			// as constraints are defined on job graph level, it is safe to only
			// test one channel
			boolean mustReportQosForGate = this.reporterConfigCenter
					.getEdgeQosReporter(outputGate.getOutputChannel(0).getID()) != null;

			if (!mustReportQosForGate) {
				this.outputGateReporters.add(null);
				this.reporterConfigCenter.setQosReporterConfigListener(
						outputGate.getGateID(), this);
				break;
			}

			OutputGateReporterManager gateReporterManager = new OutputGateReporterManager(
					this.reporterThread, outputGate.getNumberOfOutputChannels());

			this.outputGateReporters.add(gateReporterManager);

			for (int j = 0; j < outputGate.getNumberOfOutputChannels(); j++) {
				int runtimeChannelIndex = outputGate.getOutputChannel(j)
						.getChannelIndex();
				ChannelID sourceChannelID = outputGate.getOutputChannel(j)
						.getID();

				EdgeQosReporterConfig edgeReporter = this.reporterConfigCenter
						.getEdgeQosReporter(sourceChannelID);
				QosReporterID.Edge reporterID = (QosReporterID.Edge) edgeReporter
						.getReporterID();
				gateReporterManager.addEdgeQosReporterConfig(
						runtimeChannelIndex, reporterID);
			}

			QosReportingListenerHelper
					.listenToOutputChannelStatisticsOnOutputGate(outputGate,
							gateReporterManager);
		}
	}

	public synchronized void handleLimitBufferSizeAction(
			LimitBufferSizeAction limitBufferSizeAction) {

		if (this.isShutdown) {
			return;
		}

		StreamOutputGate<?> outputGate = this.taskEnvironment
				.getOutputGate(limitBufferSizeAction.getOutputGateID());

		if (outputGate != null) {
			ChannelID sourceChannelID = limitBufferSizeAction
					.getSourceChannelID();
			EdgeQosReporterConfig edgeReporter = this.reporterThread
					.getConfigCenter().getEdgeQosReporter(sourceChannelID);

			if (edgeReporter != null) {
				LOG.info(String
						.format("Setting buffer size output channel %s (%s) to %d bytes",
								sourceChannelID, edgeReporter.getName(),
								limitBufferSizeAction.getBufferSize()));
				outputGate.enqueueQosAction(limitBufferSizeAction);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see eu.stratosphere.nephele.streaming.taskmanager.qosreporter.
	 * QosReporterConfigListener
	 * #newVertexQosReporter(eu.stratosphere.nephele.streaming
	 * .message.action.VertexQosReporterConfig)
	 */
	@Override
	public synchronized void newVertexQosReporter(
			VertexQosReporterConfig reporterConfig) {
		if (this.isShutdown) {
			return;
		}

		if (reporterConfig.isDummy()) {
			this.announceDummyReporter(reporterConfig.getReporterID());
		} else {
			this.installVertexLatencyReporter(reporterConfig);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see eu.stratosphere.nephele.streaming.taskmanager.qosreporter.
	 * QosReporterConfigListener
	 * #newEdgeQosReporter(eu.stratosphere.nephele.streaming
	 * .message.action.EdgeQosReporterConfig)
	 */
	@Override
	public synchronized void newEdgeQosReporter(
			EdgeQosReporterConfig edgeReporter) {
		if (this.isShutdown) {
			return;
		}

		QosReporterID.Edge reporterID = (QosReporterID.Edge) edgeReporter
				.getReporterID();

		StreamInputGate<? extends Record> inputGate = this.taskEnvironment
				.getInputGate(edgeReporter.getInputGateID());

		if (inputGate != null) {
			int runtimeGateIndex = inputGate.getIndex();
			int runtimeChannelIndex = inputGate.getInputChannel(
					edgeReporter.getTargetChannelID()).getChannelIndex();

			if (this.inputGateReporters.get(runtimeGateIndex) == null) {
				this.createAndRegisterGateReporterManager(inputGate);
			}

			this.inputGateReporters.get(runtimeGateIndex)
					.addEdgeQosReporterConfig(runtimeChannelIndex, reporterID);
		} else {
			StreamOutputGate<? extends Record> outputGate = this.taskEnvironment
					.getOutputGate(edgeReporter.getOutputGateID());

			int runtimeGateIndex = outputGate.getIndex();
			int runtimeChannelIndex = outputGate.getOutputChannel(
					edgeReporter.getSourceChannelID()).getChannelIndex();

			if (this.outputGateReporters.get(runtimeGateIndex) == null) {
				this.createAndRegisterGateReporterManager(outputGate);
			}

			this.outputGateReporters.get(runtimeGateIndex)
					.addEdgeQosReporterConfig(runtimeChannelIndex, reporterID);
		}
	}

	private void createAndRegisterGateReporterManager(
			StreamInputGate<? extends Record> inputGate) {

		int runtimeGateIndex = inputGate.getIndex();
		InputGateReporterManager gateReporterManager = new InputGateReporterManager(
				this.reporterThread, inputGate.getNumberOfInputChannels());

		this.inputGateReporters.set(runtimeGateIndex, gateReporterManager);

		QosReportingListenerHelper.listenToChannelLatenciesOnInputGate(
				inputGate, gateReporterManager);
	}

	private void createAndRegisterGateReporterManager(
			StreamOutputGate<? extends Record> outputGate) {

		int runtimeGateIndex = outputGate.getIndex();

		OutputGateReporterManager gateReporterManager = new OutputGateReporterManager(
				this.reporterThread, outputGate.getNumberOfOutputChannels());

		this.outputGateReporters.set(runtimeGateIndex, gateReporterManager);

		QosReportingListenerHelper.listenToOutputChannelStatisticsOnOutputGate(
				outputGate, gateReporterManager);
	}

	public synchronized void shutdownReporting() {
		this.isShutdown = true;
		shutdownInputGateReporters();
		shutdownOutputGateReporters();
		this.vertexLatencyManager = null;
		this.reporterConfigCenter.unsetQosReporterConfigListener(this.task.getVertexID());
	}

	private void shutdownOutputGateReporters() {
		for (int i = 0; i < this.taskEnvironment.getNumberOfOutputGates(); i++) {
			StreamOutputGate<? extends Record> outputGate = this.taskEnvironment
					.getOutputGate(i);
			outputGate.setQosReportingListener(null);
			this.reporterConfigCenter.unsetQosReporterConfigListener(outputGate.getGateID());
		}
		this.outputGateReporters.clear();
	}

	private void shutdownInputGateReporters() {
		for (int i = 0; i < this.taskEnvironment.getNumberOfInputGates(); i++) {
			StreamInputGate<? extends Record> inputGate = this.taskEnvironment
					.getInputGate(i);
			inputGate.setQosReportingListener(null);
			this.reporterConfigCenter.unsetQosReporterConfigListener(inputGate.getGateID());
		}
		this.inputGateReporters.clear();
	}
}
