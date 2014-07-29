package eu.stratosphere.nephele.streaming.taskmanager.qosmanager;

import java.util.List;

import eu.stratosphere.nephele.jobgraph.JobVertexID;
import eu.stratosphere.nephele.streaming.JobGraphSequence;
import eu.stratosphere.nephele.streaming.SequenceElement;
import eu.stratosphere.nephele.streaming.taskmanager.qosmodel.EdgeQosData;
import eu.stratosphere.nephele.streaming.taskmanager.qosmodel.QosEdge;
import eu.stratosphere.nephele.streaming.taskmanager.qosmodel.QosGraphMember;
import eu.stratosphere.nephele.streaming.taskmanager.qosmodel.QosVertex;
import eu.stratosphere.nephele.streaming.taskmanager.qosmodel.VertexQosData;
import eu.stratosphere.nephele.streaming.taskmanager.qosmodel.VertexQosData.GateCombinationStatistics;

public class QosSequenceSummary {
	
	private int[][] inputOutputGateCombinations;
	
	private int noOfEdges;
	
	private int noOfVertices;
	
	private double memberStats[][];
	
	private double sequenceLatency;
	
	private double outputBufferLatencySum;
	
	private boolean isMemberQosDataFresh;

	public QosSequenceSummary(JobGraphSequence jobGraphSequence) {
		this.inputOutputGateCombinations = new int[jobGraphSequence.size()][];
		this.noOfEdges = 0;
		this.noOfVertices = 0;
		
		this.memberStats = new double[jobGraphSequence.size()][];
		for (SequenceElement<JobVertexID> sequenceElement : jobGraphSequence) {
			int index = sequenceElement.getIndexInSequence();

			this.inputOutputGateCombinations[index] = new int[2];
			if (sequenceElement.isVertex()) {
				this.noOfVertices++;
				this.memberStats[index] = new double[3];
				this.inputOutputGateCombinations[index][0] = sequenceElement
						.getInputGateIndex();
				this.inputOutputGateCombinations[index][1] = sequenceElement
						.getOutputGateIndex();
			} else {
				this.noOfEdges++;
				this.memberStats[index] = new double[2];
				this.inputOutputGateCombinations[index][0] = sequenceElement
						.getOutputGateIndex();
				this.inputOutputGateCombinations[index][1] = sequenceElement
						.getInputGateIndex();
			}
		}
	}
	
	public void update(List<QosGraphMember> sequenceMembers) {
		this.sequenceLatency = 0;
		this.outputBufferLatencySum = 0;
		this.isMemberQosDataFresh = true;
		
		int index = 0;
		for (QosGraphMember member : sequenceMembers) {
			if (member.isVertex()) {
				VertexQosData vertexQos = ((QosVertex) member).getQosData();

				int inputGateIndex = this.inputOutputGateCombinations[index][0];
				int outputGateIndex = this.inputOutputGateCombinations[index][1];

				GateCombinationStatistics stats = vertexQos.getGateCombinationStatistics(inputGateIndex, outputGateIndex);
				this.memberStats[index][0] = stats.getLatencyInMillis();
				this.memberStats[index][1] = stats.getRecordsConsumedPerSec();
				this.memberStats[index][2] = stats.getRecordsEmittedPerSec();
				sequenceLatency += this.memberStats[index][0];
			} else {
				EdgeQosData edgeQos = ((QosEdge) member).getQosData();
				double channelLatency = edgeQos.getChannelLatencyInMillis();
				double outputBufferLatency = Math.min(channelLatency, edgeQos.getOutputBufferLifetimeInMillis() / 2);
				
				this.memberStats[index][0] = outputBufferLatency;
				this.memberStats[index][1] = channelLatency - outputBufferLatency;
				sequenceLatency += channelLatency;
				outputBufferLatencySum += outputBufferLatency;
				this.isMemberQosDataFresh = this.isMemberQosDataFresh && hasFreshValues(edgeQos);
			}

			index++;
		}
	}
	
	private boolean hasFreshValues(EdgeQosData qosData) {
		long freshnessThreshold = qosData.getBufferSizeHistory().getLastEntry().getTimestamp();

		return qosData.isChannelLatencyFresherThan(freshnessThreshold)
				&& qosData.isOutputBufferLifetimeFresherThan(freshnessThreshold);
	}

	public double[][] getMemberStatistics() {
		return memberStats;
	}

	public double getSequenceLatency() {
		return sequenceLatency;
	}

	public double getOutputBufferLatencySum() {
		return outputBufferLatencySum;
	}
	
	public boolean isMemberQosDataFresh() {
		return this.isMemberQosDataFresh;
	}

	public int getNoOfEdges() {
		return noOfEdges;
	}

	public int getNoOfVertices() {
		return noOfVertices;
	}
	
	public double getNonOutputBufferLatency() {
		return this.sequenceLatency - this.outputBufferLatencySum;
	}
}