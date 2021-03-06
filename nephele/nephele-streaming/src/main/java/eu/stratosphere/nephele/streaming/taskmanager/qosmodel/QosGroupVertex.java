package eu.stratosphere.nephele.streaming.taskmanager.qosmodel;

import java.util.ArrayList;
import java.util.HashSet;

import eu.stratosphere.nephele.instance.InstanceConnectionInfo;
import eu.stratosphere.nephele.jobgraph.JobVertexID;
import eu.stratosphere.nephele.streaming.util.SparseDelegateIterable;

/**
 * This class models a Qos group vertex as part of a Qos graph. It is equivalent
 * to an {@link eu.stratosphere.nephele.executiongraph.ExecutionGroupVertex},
 * which in turn is equivalent to a
 * {@link eu.stratosphere.nephele.jobgraph.AbstractJobVertex}.
 * 
 * Some structures inside the group vertex are sparse. This applies to the lists
 * of forward and backward group edges as well as the member vertex list.
 * Sparseness means that, for example if the group vertex has an forward edge
 * with index 1 it may not have a forward edge with index 0. This stems from the
 * fact, that the Qos graph itself only contains those group vertices and group
 * edges from the executiong graph, that are covered by a constraint.
 * 
 * @author Bjoern Lohrmann
 * 
 */
public class QosGroupVertex {

	private final String name;

	/**
	 * The ID of the job vertex which is represented by this group vertex.
	 */
	private final JobVertexID jobVertexID;

	/**
	 * The list of {@link QosVertex} contained in this group vertex. This is a
	 * sparse list, meaning that entries in this list may be null.
	 */
	private ArrayList<QosVertex> groupMembers;

	/**
	 * The number of non-null members in this group vertex ({@see #groupMembers}
	 * )
	 */
	private int noOfMembers;

	/**
	 * The list of group edges which originate from this group vertex. This is a
	 * sparse list, meaning that entries in this list may be null.
	 */
	private ArrayList<QosGroupEdge> forwardEdges;

	/**
	 * The number of output gates that the member vertices have. This is equal
	 * to the number of non-null entries in forwardEdges.
	 */
	private int noOfOutputGates;

	/**
	 * The a group edge which arrives at this group vertex.
	 */
	private ArrayList<QosGroupEdge> backwardEdges;

	/**
	 * The number of input gates that the member vertices have. This is equal to
	 * the number of non-null entries in backwardEdges.
	 */
	private int noOfInputGates;

	private int noOfExecutingInstances;

	public QosGroupVertex(JobVertexID jobVertexID, String name) {
		this.name = name;
		this.jobVertexID = jobVertexID;
		this.noOfExecutingInstances = -1;
		this.noOfMembers = 0;
		this.groupMembers = new ArrayList<QosVertex>();
		this.forwardEdges = new ArrayList<QosGroupEdge>();
		this.backwardEdges = new ArrayList<QosGroupEdge>();
	}

	public QosGroupEdge getForwardEdge(int outputGateIndex) {
		try {
			return this.forwardEdges.get(outputGateIndex);
		} catch (IndexOutOfBoundsException e) {
			return null;
		}
	}

	public void setForwardEdge(QosGroupEdge forwardEdge) {
		int outputGate = forwardEdge.getOutputGateIndex();
		if (outputGate >= this.forwardEdges.size()) {
			this.fillWithNulls(this.forwardEdges, outputGate + 1);
		}

		if (this.forwardEdges.get(outputGate) == null) {
			this.noOfOutputGates++;
		}
		this.forwardEdges.set(outputGate, forwardEdge);
	}

	private <T> void fillWithNulls(ArrayList<T> list, int targetSize) {
		int toAdd = targetSize - list.size();

		for (int i = 0; i < toAdd; i++) {
			list.add(null);
		}
	}

	public QosGroupEdge getBackwardEdge(int inputGateIndex) {
		try {
			return this.backwardEdges.get(inputGateIndex);
		} catch (IndexOutOfBoundsException e) {
			return null;
		}
	}

	public void setBackwardEdge(QosGroupEdge backwardEdge) {
		int inputGate = backwardEdge.getInputGateIndex();
		if (inputGate >= this.backwardEdges.size()) {
			this.fillWithNulls(this.backwardEdges, inputGate + 1);
		}

		if (this.backwardEdges.get(inputGate) == null) {
			this.noOfInputGates++;
		}

		this.backwardEdges.set(inputGate, backwardEdge);
	}

	public String getName() {
		return this.name;
	}

	public JobVertexID getJobVertexID() {
		return this.jobVertexID;
	}

	public Iterable<QosVertex> getMembers() {
		return new SparseDelegateIterable<QosVertex>(
				this.groupMembers.iterator());
	}

	public void setGroupMember(QosVertex groupMember) {
		this.setGroupMember(groupMember.getMemberIndex(), groupMember);
	}

	public void setGroupMember(int memberIndex, QosVertex groupMember) {
		if (this.groupMembers.size() <= memberIndex) {
			this.fillWithNulls(this.groupMembers, memberIndex + 1);
		}

		if (groupMember == null) {
			if (this.groupMembers.get(memberIndex) != null) {
				this.noOfMembers--;
			}
			this.groupMembers.set(memberIndex, null);
		} else {
			if (this.groupMembers.get(memberIndex) == null) {
				this.noOfMembers++;
			}
			groupMember.setGroupVertex(this);
			this.groupMembers.set(groupMember.getMemberIndex(), groupMember);
		}
		this.noOfExecutingInstances = -1;
	}

	public int getNumberOfExecutingInstances() {
		if (this.noOfExecutingInstances == -1) {
			this.countExecutingInstances();
		}

		return this.noOfExecutingInstances;
	}

	private void countExecutingInstances() {
		HashSet<InstanceConnectionInfo> instances = new HashSet<InstanceConnectionInfo>();
		for (QosVertex memberVertex : this.getMembers()) {
			instances.add(memberVertex.getExecutingInstance());
		}
		this.noOfExecutingInstances = instances.size();
	}

	@Override
	public String toString() {
		return this.name;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return this.jobVertexID.hashCode();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (this.getClass() != obj.getClass()) {
			return false;
		}
		QosGroupVertex other = (QosGroupVertex) obj;
		return this.jobVertexID.equals(other.jobVertexID);
	}

	public int getNumberOfOutputGates() {
		return this.noOfOutputGates;
	}

	public Iterable<QosGroupEdge> getForwardEdges() {
		return new SparseDelegateIterable<QosGroupEdge>(
				this.forwardEdges.iterator());
	}

	public Iterable<QosGroupEdge> getBackwardEdges() {
		return new SparseDelegateIterable<QosGroupEdge>(
				this.backwardEdges.iterator());
	}

	public int getNumberOfInputGates() {
		return this.noOfInputGates;
	}

	public boolean hasOutputGate(int outputGateIndex) {
		return this.getForwardEdge(outputGateIndex) != null;
	}

	public boolean hasInputGate(int inputGateIndex) {
		return this.getBackwardEdge(inputGateIndex) != null;
	}

	/**
	 * @return The member with the given index, or null if there is no member at
	 *         the given index.
	 */
	public QosVertex getMember(int memberIndex) {
		QosVertex toReturn = null;

		if (this.groupMembers.size() > memberIndex) {
			toReturn = this.groupMembers.get(memberIndex);
		}

		return toReturn;
	}

	/**
	 * @return The number of actual (non-null) members in this group vertex.
	 */
	public int getNumberOfMembers() {
		return this.noOfMembers;
	}

	/**
	 * Clones this group vertex including members but excluding any group edges
	 * or member gates.
	 * 
	 * @return The cloned object
	 */
	public QosGroupVertex cloneWithoutEdges() {
		QosGroupVertex clone = new QosGroupVertex(this.jobVertexID, this.name);
		clone.noOfExecutingInstances = this.noOfExecutingInstances;
		clone.groupMembers = new ArrayList<QosVertex>();
		for (QosVertex member : this.groupMembers) {
			if (member != null) {
				clone.setGroupMember(member.cloneWithoutGates());
			}
		}
		return clone;
	}

	/**
	 * Clones this group vertex without forward/backward group edges and without
	 * members.
	 * 
	 * @return The cloned object
	 */
	public QosGroupVertex cloneWithoutMembersOrEdges() {
		QosGroupVertex clone = new QosGroupVertex(this.jobVertexID, this.name);
		return clone;
	}

	public QosGroupEdge wireTo(QosGroupVertex target, QosGroupEdge templateEdge) {
		// already invokes the setForward/BackwardEdge methods
		return new QosGroupEdge(templateEdge.getDistributionPattern(), this,
				target, templateEdge.getOutputGateIndex(),
				templateEdge.getInputGateIndex());

	}

	public QosGroupEdge wireFrom(QosGroupVertex source,
			QosGroupEdge templateEdge) {
		// already invokes the setForward/BackwardEdge methods
		return new QosGroupEdge(templateEdge.getDistributionPattern(), source,
				this, templateEdge.getOutputGateIndex(),
				templateEdge.getInputGateIndex());

	}
}
