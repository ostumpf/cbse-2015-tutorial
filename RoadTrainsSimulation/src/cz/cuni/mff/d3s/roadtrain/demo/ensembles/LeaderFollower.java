package cz.cuni.mff.d3s.roadtrain.demo.ensembles;

import org.matsim.api.core.v01.Id;

import cz.cuni.mff.d3s.deeco.annotations.Ensemble;
import cz.cuni.mff.d3s.deeco.annotations.In;
import cz.cuni.mff.d3s.deeco.annotations.InOut;
import cz.cuni.mff.d3s.deeco.annotations.KnowledgeExchange;
import cz.cuni.mff.d3s.deeco.annotations.Membership;
import cz.cuni.mff.d3s.deeco.annotations.PartitionedBy;
import cz.cuni.mff.d3s.deeco.annotations.PeriodicScheduling;
import cz.cuni.mff.d3s.deeco.task.ParamHolder;
import cz.cuni.mff.d3s.roadtrain.demo.Settings;
import cz.cuni.mff.d3s.roadtrain.demo.utils.Navigator;
import cz.cuni.mff.d3s.roadtrain.demo.utils.VehicleLink;
import cz.cuni.mff.d3s.roadtrain.demo.utils.VehicleState;

@Ensemble
@PeriodicScheduling(period = 1000)
@PartitionedBy("dstPlace")
public class LeaderFollower {
	@Membership
	public static boolean membership(
			@In("coord.id") String coordId,
			@In("coord.state") VehicleState coordState,
			@In("member.state") VehicleState memberState,
			@In("member.leader") VehicleLink memberLeader,
			@In("member.trainId") String memberTrainId) {
		// Member is following coordinator and they are not part of the road train
		return memberLeader != null && memberLeader.id.equals(coordId) && coordState.canLead() && memberState.canFollow();
	}

	@KnowledgeExchange
	public static void exchange(
			@In("coord.id") String coordId,
			@In("coord.currentLink") Id coordLink,
			@In("coord.trainId") String coordTrainId,
			@In("coord.curTime") long time,
			@InOut("coord.nearestFollower") ParamHolder<Double> nearestFollower,
			@In("member.id") String memberId,
			@In("member.currentLink") Id memberLink,
			@InOut("member.trainId") ParamHolder<String> memeberTrainId,
			@InOut("member.leader") ParamHolder<VehicleLink> leader) {
		double distance = Navigator.getLinkLinkDist(coordLink, memberLink);
				
		// Leader - follower distance		
		if (nearestFollower.value == null || nearestFollower.value > distance) {
			nearestFollower.value = distance;
		}
		
		leader.value = new VehicleLink(coordId, coordLink, distance, time);
		
		// Assign vehicle to train
		if(distance < Settings.TRAIN_FORM_DISTANCE) {
			memeberTrainId.value = coordTrainId;
		}
	}
}
