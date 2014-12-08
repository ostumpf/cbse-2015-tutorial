package cz.cuni.mff.d3s.roadtrain.demo.ensembles;

import java.util.Map;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;

import cz.cuni.mff.d3s.deeco.annotations.Ensemble;
import cz.cuni.mff.d3s.deeco.annotations.In;
import cz.cuni.mff.d3s.deeco.annotations.InOut;
import cz.cuni.mff.d3s.deeco.annotations.KnowledgeExchange;
import cz.cuni.mff.d3s.deeco.annotations.Membership;
import cz.cuni.mff.d3s.deeco.annotations.PartitionedBy;
import cz.cuni.mff.d3s.deeco.annotations.PeriodicScheduling;
import cz.cuni.mff.d3s.deeco.scheduler.CurrentTimeProvider;
import cz.cuni.mff.d3s.deeco.task.ParamHolder;
import cz.cuni.mff.d3s.roadtrain.demo.utils.VehicleInfo;

@Ensemble
@PeriodicScheduling(period = 1000)
@PartitionedBy("dstCity")
public class SharedDestination {
	@Membership
	public static boolean membership(
			@In("coord.id") String coordId,
			@In("coord.dstCity") String coordDstCity,
			@In("coord.trainId") String coordTrainId,
			@In("member.id") String memberId,
			@In("member.dstCity") String memberDstCity,
			@In("member.trainId") String memberTrainId) {
		// Same destination, not the same vehicle, not part of the train
		return coordDstCity.equals(memberDstCity) && !coordId.equals(memberId) && memberTrainId.equals(memberId)
				&& coordTrainId.equals(coordId);
	}

	@KnowledgeExchange
	public static void exchange(
			@In("member.id") String memberId,
			@In("member.position") Coord memberPosition,
			@In("member.currentLink") Id memberLink,
			@InOut("coord.destGroup") ParamHolder<Map<String, VehicleInfo>> coordGroup,
			@In("coord.clock") CurrentTimeProvider clock) {
		// Exchange information about the group sharing the same destination
		coordGroup.value.put(memberId, new VehicleInfo(memberId, memberPosition, memberLink, clock.getCurrentMilliseconds()));
	}
}