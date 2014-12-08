package cz.cuni.mff.d3s.roadtrain.demo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.matsim.api.core.v01.network.Link;
import org.matsim.core.basic.v01.IdImpl;

import cz.cuni.mff.d3s.deeco.annotations.processor.AnnotationProcessor;
import cz.cuni.mff.d3s.deeco.annotations.processor.AnnotationProcessorException;
import cz.cuni.mff.d3s.deeco.knowledge.CloningKnowledgeManagerFactory;
import cz.cuni.mff.d3s.deeco.knowledge.KnowledgeManagerFactory;
import cz.cuni.mff.d3s.deeco.model.runtime.api.EnsembleDefinition;
import cz.cuni.mff.d3s.deeco.model.runtime.api.RuntimeMetadata;
import cz.cuni.mff.d3s.deeco.model.runtime.custom.RuntimeMetadataFactoryExt;
import cz.cuni.mff.d3s.deeco.network.DataSender;
import cz.cuni.mff.d3s.deeco.network.DefaultKnowledgeDataManager;
import cz.cuni.mff.d3s.deeco.network.DirectGossipStrategy;
import cz.cuni.mff.d3s.deeco.network.DirectRecipientSelector;
import cz.cuni.mff.d3s.deeco.network.IPGossipStrategy;
import cz.cuni.mff.d3s.deeco.network.KnowledgeDataManager;
import cz.cuni.mff.d3s.deeco.network.RandomIPGossip;
import cz.cuni.mff.d3s.deeco.network.connector.ConnectorComponent;
import cz.cuni.mff.d3s.deeco.network.connector.ConnectorEnsemble;
import cz.cuni.mff.d3s.deeco.network.connector.IPGossipConnectorStrategy;
import cz.cuni.mff.d3s.deeco.network.ip.IPControllerImpl;
import cz.cuni.mff.d3s.deeco.network.ip.IPDataSender;
import cz.cuni.mff.d3s.deeco.network.ip.IPGossipClientStrategy;
import cz.cuni.mff.d3s.deeco.runtime.RuntimeFramework;
import cz.cuni.mff.d3s.deeco.simulation.DirectSimulationHost;
import cz.cuni.mff.d3s.deeco.simulation.NetworkDataHandler;
import cz.cuni.mff.d3s.deeco.simulation.SimulationRuntimeBuilder;
import cz.cuni.mff.d3s.deeco.simulation.matsim.DefaultMATSimExtractor;
import cz.cuni.mff.d3s.deeco.simulation.matsim.DefaultMATSimUpdater;
import cz.cuni.mff.d3s.deeco.simulation.matsim.JDEECoAgent;
import cz.cuni.mff.d3s.deeco.simulation.matsim.JDEECoAgentSource;
import cz.cuni.mff.d3s.deeco.simulation.matsim.MATSimRouter;
import cz.cuni.mff.d3s.deeco.simulation.matsim.MATSimSimulation;
import cz.cuni.mff.d3s.roadtrain.demo.components.AgentSourceBasedPosition;
import cz.cuni.mff.d3s.roadtrain.demo.components.Vehicle;
import cz.cuni.mff.d3s.roadtrain.demo.custom.KnowledgeProvider;
import cz.cuni.mff.d3s.roadtrain.demo.custom.RealisticKnowledgeDataHandler;
import cz.cuni.mff.d3s.roadtrain.demo.ensembles.LeaderFollower;
import cz.cuni.mff.d3s.roadtrain.demo.ensembles.SharedDestination;
import cz.cuni.mff.d3s.roadtrain.demo.ensembles.Train;
import cz.cuni.mff.d3s.roadtrain.demo.ensembles.TrainLeaderFollower;
import cz.cuni.mff.d3s.roadtrain.demo.environment.MATSimDataProviderReceiver;
import cz.cuni.mff.d3s.roadtrain.demo.utils.Navigator;

public class LauncherWithGroupers implements Launcher, VehicleDeployer {
	private JDEECoAgentSource agentSource;
	private MATSimSimulation sim;
	private MATSimRouter router;
	private MATSimDataProviderReceiver matSimProviderReceiver;
	private SimulationRuntimeBuilder builder;	
	private AgentSourceBasedPosition positionProvider;
	private CloningKnowledgeManagerFactory kmFactory;
	private Set<String> destinations = new HashSet<String>();
	
	// For dummy gossip
	private final DummyRecipientSelector dummyRecipientSelector = new DummyRecipientSelector();
	private final DirectGossipStrategy directGossipStrategy = new DirectGossipStrategy() {
		public Collection<String> filterRecipients(Collection<String> recipients) {
			return recipients;
		}
	};
		
	public void run(DemoDeployer demoDeployer) throws AnnotationProcessorException, IOException {
		// Setup simulation
		System.out.println("Preparing simulation");
		agentSource = new JDEECoAgentSource();
		positionProvider = new AgentSourceBasedPosition(agentSource);
		NetworkDataHandler networkHandler = new RealisticKnowledgeDataHandler(positionProvider);
		matSimProviderReceiver = new MATSimDataProviderReceiver(new LinkedList<String>());
		sim = new MATSimSimulation(matSimProviderReceiver,
				matSimProviderReceiver, new DefaultMATSimUpdater(),
				new DefaultMATSimExtractor(), networkHandler,
				Arrays.asList(agentSource), Settings.MATSIM_CONFIG);
		router = new MATSimRouter(sim.getControler(), sim.getTravelTime(), Settings.ROUTE_CALCULATION_OFFSET);
		positionProvider.setRouter(router);
		builder = new SimulationRuntimeBuilder();
		kmFactory = new CloningKnowledgeManagerFactory();
		
		// Setup navigator
		Navigator.init(router);
	
		// Deploy components
		System.out.println("Deploying components");
		//deployCarGroups();
	/*	deployAccidentSites(
				Settings.CRASH_SITES,
				Settings.POLICE_PER_CRASH,
				Settings.AMBULANCE_PER_CRASH,
				Settings.FIRE_PER_CRASH);*/
		demoDeployer.deploy();
		
		
		if(Settings.useGroupers) {
			// Deploy groupers
			List<Set<Object>> cDom = new ArrayList<Set<Object> >();
			cDom.add(new HashSet<Object>());
			cDom.add(new HashSet<Object>());
			cDom.add(new HashSet<Object>());
			
			int cnt = 0;
			for(String dest: destinations) {
				cDom.get(cnt++ % cDom.size()).add(dest);
			}
	
			deployConnector("G1", cDom.get(0));
			deployConnector("G2", cDom.get(1));
			deployConnector("G3", cDom.get(2));
		}
		
		
		// Run the simulation
		System.out.println("Running simulation");
		long startTime = System.currentTimeMillis();
		sim.run();
		long diffTime = System.currentTimeMillis() - startTime;
		System.out.println(String.format("Simulation Finished in: %s.%ss", diffTime / 1000, diffTime % 1000));
		System.out.println(MessageProbe.report());
	}
	
	
	public IPGossipStrategy getStrategy(Vehicle component, Object commGroup, RuntimeMetadata model, DirectSimulationHost host) {
		if(Settings.useGroupers) {
			IPControllerImpl controller = new IPControllerImpl();
			
			// TODO: default IP should be added automatically based on current ensemble definition
			controller.getRegister(commGroup).add("G1");
			host.addDataReceiver(controller);
			
			Set<String> partitions = new HashSet<String>();
			for (EnsembleDefinition ens : model.getEnsembleDefinitions())
				partitions.add(ens.getPartitionedBy());
			
			return new IPGossipClientStrategy(partitions, controller);
		} else {
			dummyRecipientSelector.add(component.id);
			return new RandomIPGossip(Arrays.asList((DirectRecipientSelector)dummyRecipientSelector), directGossipStrategy);
		}
	}
	
	
	public void deployVehicle(Vehicle component) throws AnnotationProcessorException {
		agentSource.addAgent(new JDEECoAgent(new IdImpl(component.id), component.currentLink));
				
		RuntimeMetadata model = RuntimeMetadataFactoryExt.eINSTANCE.createRuntimeMetadata();
		AnnotationProcessor processor = new AnnotationProcessor(RuntimeMetadataFactoryExt.eINSTANCE, model,
				kmFactory, new PartitionedByProcessor());
		processor.process(
				component,
				SharedDestination.class,
				LeaderFollower.class,
				Train.class,
				TrainLeaderFollower.class);

		DirectSimulationHost host = sim.getHost(component.id);
		IPGossipStrategy strategy = getStrategy(component, component.dstPlace, model, host);
		KnowledgeDataManager kdm = new DefaultKnowledgeDataManager(model.getEnsembleDefinitions(), strategy);
		
		RuntimeFramework runtime = builder.build(host, sim, null, model, kdm, new CloningKnowledgeManagerFactory());
		runtime.start();
	}
	
	
	
	public void deployConnector(String id, Collection<Object> range) throws AnnotationProcessorException {
		Link link = Navigator.getRandomLink();
		agentSource.addAgent(new JDEECoAgent(new IdImpl(id), link.getId()));
		
		/* Model */
		KnowledgeManagerFactory knowledgeManagerFactory = new CloningKnowledgeManagerFactory();
		RuntimeMetadata model = RuntimeMetadataFactoryExt.eINSTANCE.createRuntimeMetadata();
		AnnotationProcessor processor = new AnnotationProcessor(RuntimeMetadataFactoryExt.eINSTANCE, model,
				knowledgeManagerFactory, new PartitionedByProcessor());
		
		processor.process(
				SharedDestination.class,
				LeaderFollower.class,
				Train.class,
				TrainLeaderFollower.class,
				ConnectorEnsemble.class);
		
		/* Available partitions */
		Set<String> partitions = new HashSet<String>();
		for (EnsembleDefinition ens : model.getEnsembleDefinitions())
			partitions.add(ens.getPartitionedBy());
		
		DirectSimulationHost host = sim.getHost(id);
		
		/* Create IPController */
		IPControllerImpl controller = new IPControllerImpl();
		host.addDataReceiver(controller);	
		
		/* Create knowledge provider */
		KnowledgeProvider provider = new KnowledgeProvider();
		host.addDataReceiver(provider);
		
		/* Create Connector component */
		IPDataSender ipSender = new IPDataSenderWrapper(host.getDataSender());
		ConnectorComponent connector = new ConnectorComponent(id, partitions, range, 
				controller, ipSender, provider);
		processor.process(connector);
		
		// provide list of initial IPs
		controller.getRegister(connector.connector_group).add("G1"); //.add("C2", "C3");
		
		IPGossipStrategy strategy = new IPGossipConnectorStrategy(partitions, controller);
		KnowledgeDataManager kdm = new DefaultKnowledgeDataManager(model.getEnsembleDefinitions(), strategy);
		RuntimeFramework runtime = builder.build(host, sim, null, model, kdm, new CloningKnowledgeManagerFactory());
		runtime.start();
	}
	
	private class IPDataSenderWrapper implements IPDataSender {

		private final DataSender sender;
		
		public IPDataSenderWrapper(DataSender sender) {
			this.sender = sender;
		}
		
		public void sendData(Object data, String recipient) {
			sender.sendData(data, recipient);
		}
		
		public void broadcastData(Object data) {
			sender.broadcastData(data);
		}

	}

	@Override
	public MATSimSimulation getSimulation() {
		return sim;
	}


	@Override
	public MATSimRouter getRouter() {
		return router;
	}


	@Override
	public MATSimDataProviderReceiver getProviderReceiver() {
		return matSimProviderReceiver;
	}

	@Override
	public void addDestination(String destination) {
		destinations.add(destination);
	}
}
