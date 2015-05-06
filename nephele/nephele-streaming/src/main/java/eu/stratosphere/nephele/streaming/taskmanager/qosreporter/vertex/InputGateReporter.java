package eu.stratosphere.nephele.streaming.taskmanager.qosreporter.vertex;

import eu.stratosphere.nephele.streaming.message.qosreport.EdgeLatency;
import eu.stratosphere.nephele.streaming.taskmanager.qosmodel.QosReporterID;
import eu.stratosphere.nephele.streaming.taskmanager.qosreporter.QosReportForwarderThread;
import eu.stratosphere.nephele.streaming.taskmanager.qosreporter.QosReporterConfigCenter;
import eu.stratosphere.nephele.streaming.taskmanager.qosreporter.TimestampTag;
import eu.stratosphere.nephele.streaming.util.StreamPluginConfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A instance of this class keeps track of and reports on the latencies of an
 * input gate's input channels.
 *
 * An {@link EdgeLatency} record per input channel will be handed to the
 * provided {@link QosReportForwarderThread} approximately once per aggregation
 * interval (see {@link QosReporterConfigCenter}). "Approximately" because if no
 * records have been received/emitted, nothing will be reported.
 *
 *
 * This class is thread-safe.
 *
 * @author Bjoern Lohrmann
 *
 */
public class InputGateReporter extends CountingGateReporter {
	/**
	 * No need for a thread-safe set because it is only accessed in synchronized
	 * methods.
	 */
	private HashSet<QosReporterID> reporters;

	/**
	 * Maps from an input channels index in the runtime gate to the latency
	 * reporter. This needs to be threadsafe because statistics collection may
	 * already be running while EdgeLatencyReporters are being added.
	 */
	private CopyOnWriteArrayList<EdgeLatencyReporter> reportersByChannelIndexInRuntimeGate;

	private QosReportForwarderThread reportForwarder;

	private ReportTimer reportTimer;

	public void initReporter(QosReportForwarderThread qosReporter, int noOfInputChannels) {
		this.reportForwarder = qosReporter;
		this.reportersByChannelIndexInRuntimeGate = new CopyOnWriteArrayList<EdgeLatencyReporter>();
		Collections.addAll(this.reportersByChannelIndexInRuntimeGate, new EdgeLatencyReporter[noOfInputChannels]);
		this.reporters = new HashSet<QosReporterID>();
		this.reportTimer = new ReportTimer(StreamPluginConfig.getAggregationIntervalMillis());
		setReporter(true);
	}

	private class EdgeLatencyReporter {

		public QosReporterID.Edge reporterID;
		long accumulatedLatency;
		int tagsReceived;

		public void sendReportIfDue() {
			if (this.reportIsDue()) {
				this.sendReport();
				this.reset();
			}
		}

		private void sendReport() {
			EdgeLatency channelLatency = new EdgeLatency(this.reporterID,
					this.accumulatedLatency / this.tagsReceived);
			InputGateReporter.this.reportForwarder
					.addToNextReport(channelLatency);
		}

		public boolean reportIsDue() {
			return this.tagsReceived > 0;
		}

		public void reset() {
			this.accumulatedLatency = 0;
			this.tagsReceived = 0;
		}

		public void update(TimestampTag tag, long now) {
			// need to take max() because timestamp diffs can be below zero
			// due to clockdrift
			this.accumulatedLatency += Math.max(0, now - tag.getTimestamp());
			this.tagsReceived++;
		}
	}

	public void reportLatenciesIfNecessary(int channelIndex, TimestampTag timestampTag) {

		EdgeLatencyReporter info = this.reportersByChannelIndexInRuntimeGate.get(channelIndex);

		long now = System.currentTimeMillis();
		if (info != null) {
			info.update(timestampTag, now);
		}
		sendReportsIfDue(now);
	}

	private void sendReportsIfDue(long now) {
		if (reportTimer.reportIsDue()) {
			for (EdgeLatencyReporter reporter : reportersByChannelIndexInRuntimeGate) {
				if (reporter != null) {
					reporter.sendReportIfDue();
				}
			}
		}
		reportTimer.reset(now);
	}

	public void addEdgeQosReporterConfig(int channelIndex, QosReporterID.Edge reporterID) {
		if (this.reporters.contains(reporterID)) {
			return;
		}

		EdgeLatencyReporter info = new EdgeLatencyReporter();
		info.reporterID = reporterID;
		info.accumulatedLatency = 0;
		info.tagsReceived = 0;
		this.reportersByChannelIndexInRuntimeGate.set(channelIndex, info);
		this.reporters.add(reporterID);
	}
}
