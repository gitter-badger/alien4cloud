package alien4cloud.plugin.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.model.application.Application;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStateMonitorEvent;
import alien4cloud.paas.model.PaaSMessageMonitorEvent;
import alien4cloud.rest.utils.JsonUtil;
import alien4cloud.tosca.container.model.topology.NodeTemplate;
import alien4cloud.tosca.container.model.topology.RelationshipTemplate;
import alien4cloud.tosca.container.model.topology.ScalingPolicy;
import alien4cloud.tosca.container.model.topology.Topology;
import alien4cloud.tosca.container.model.type.PropertyConstraint;
import alien4cloud.tosca.container.model.type.PropertyDefinition;
import alien4cloud.tosca.container.model.type.ToscaType;
import alien4cloud.tosca.properties.constraints.GreaterOrEqualConstraint;
import alien4cloud.tosca.properties.constraints.PatternConstraint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;

@Slf4j
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class MockPaaSProvider extends AbstractPaaSProvider implements IConfigurablePaaSProvider<ProviderConfig> {

    public static final String PRIVATE_IP = "private_ip_address";
    public static final String PUBLIC_IP = "public_ip_address";

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    private final Map<String, PropertyDefinition> deploymentProperties;
    private final Map<String, DeploymentStatus> deploymentsMap = Maps.newConcurrentMap();

    /**
     * A little bit scary isn't it ? It's just a mock man.
     */
    private final Map<String, Map<String, Map<Integer, InstanceInformation>>> instanceInformationsMap = Maps.newConcurrentMap();

    private final List<AbstractMonitorEvent> toBeDeliveredEvents = Collections.synchronizedList(new ArrayList<AbstractMonitorEvent>());

    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDAO;

    private static final String UNKNOWN_APPLICATION_THAT_NEVER_WORKS = "UNKNOWN-APPLICATION";

    private static final String BAD_APPLICATION_THAT_NEVER_WORKS = "BAD-APPLICATION";

    private static final String WARN_APPLICATION_THAT_NEVER_WORKS = "WARN-APPLICATION";

    public MockPaaSProvider() {
        deploymentProperties = Maps.newHashMap();
        executorService.scheduleWithFixedDelay(new Runnable() {

            @Override
            public void run() {
                for (Map.Entry<String, Map<String, Map<Integer, InstanceInformation>>> topologyInfo : instanceInformationsMap.entrySet()) {
                    // Call this just to change instance state and push to client
                    doChangeInstanceInformations(topologyInfo.getKey(), topologyInfo.getValue());
                }
            }
        }, 1L, 1L, TimeUnit.SECONDS);

    }

    @PreDestroy
    public void destroy() {
        executorService.shutdown();
        try {
            executorService.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
        }
    }

    @Override
    public DeploymentStatus doGetStatus(String deploymentId) {
        if (deploymentsMap.containsKey(deploymentId)) {
            return deploymentsMap.get(deploymentId);
        } else {
            Deployment deployment = alienDAO.findById(Deployment.class, deploymentId);
            if (deployment == null) {
                return DeploymentStatus.UNDEPLOYED;
            }
            QueryBuilder matchTopologyIdQueryBuilder = QueryBuilders.termQuery("topologyId", deployment.getTopologyId());
            final Application application = alienDAO.customFind(Application.class, matchTopologyIdQueryBuilder);
            if (application != null && UNKNOWN_APPLICATION_THAT_NEVER_WORKS.equals(application.getName())) {
                return DeploymentStatus.UNKNOWN;
            } else {
                return DeploymentStatus.UNDEPLOYED;
            }
        }
    }

    private InstanceInformation newInstance(int i) {
        Map<String, String> properties = Maps.newHashMap();
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put(PRIVATE_IP, "192.168.0." + i);
        attributes.put(PUBLIC_IP, "10.52.0." + i);
        Map<String, String> runtimeProperties = Maps.newHashMap();
        runtimeProperties.put(PRIVATE_IP, "192.168.0." + i);
        runtimeProperties.put(PUBLIC_IP, "10.52.0." + i);
        return new InstanceInformation("init", InstanceStatus.PROCESSING, properties, attributes, runtimeProperties);
    }

    private ScalingPolicy getScalingPolicy(String id, Map<String, ScalingPolicy> policies, Map<String, NodeTemplate> nodeTemplates) {
        // Get the scaling of parent if not exist
        ScalingPolicy policy = policies.get(id);
        if (policy == null && nodeTemplates.get(id).getRelationships() != null) {
            for (RelationshipTemplate rel : nodeTemplates.get(id).getRelationships().values()) {
                if ("tosca.relationships.HostedOn".equals(rel.getType())) {
                    policy = getScalingPolicy(rel.getTarget(), policies, nodeTemplates);
                }
            }
        }
        return policy;
    }

    @Override
    protected synchronized void doDeploy(final String deploymentId) {
        final Deployment deployment = alienDAO.findById(Deployment.class, deploymentId);
        log.info("Deploying deployment [" + deploymentId + "]");
        changeStatus(deploymentId, DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
        if (deploymentId != null) {
            Topology topology = alienDAO.findById(Topology.class, deployment.getTopologyId());
            Map<String, ScalingPolicy> policies = topology.getScalingPolicies();
            if (policies == null) {
                policies = Maps.newHashMap();
            }
            Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();
            if (nodeTemplates == null) {
                nodeTemplates = Maps.newHashMap();
            }
            Map<String, Map<Integer, InstanceInformation>> currentInformations = Maps.newHashMap();
            for (Map.Entry<String, NodeTemplate> nodeTemplateEntry : nodeTemplates.entrySet()) {
                Map<Integer, InstanceInformation> instanceInformations = Maps.newHashMap();
                currentInformations.put(nodeTemplateEntry.getKey(), instanceInformations);
                ScalingPolicy policy = getScalingPolicy(nodeTemplateEntry.getKey(), policies, nodeTemplates);
                int initialInstances = policy != null ? policy.getInitialInstances() : 1;
                for (int i = 1; i <= initialInstances; i++) {
                    InstanceInformation newInstanceInformation = newInstance(i);
                    instanceInformations.put(i, newInstanceInformation);
                    notifyInstanceStateChanged(deploymentId, nodeTemplateEntry.getKey(), i, newInstanceInformation, 1);
                }
            }
            instanceInformationsMap.put(deploymentId, currentInformations);
        }
        executorService.schedule(new Runnable() {

            @Override
            public void run() {
                Application application = alienDAO.findById(Application.class, deployment.getSourceId());
                // To bluff the product owner, we must do as if it's deploying something, but in fact it's not
                if (application == null) {
                    changeStatus(deploymentId, DeploymentStatus.DEPLOYED);
                    return;
                }
                switch (application.getName()) {
                case BAD_APPLICATION_THAT_NEVER_WORKS:
                    changeStatus(deploymentId, DeploymentStatus.FAILURE);
                    break;
                case WARN_APPLICATION_THAT_NEVER_WORKS:
                    changeStatus(deploymentId, DeploymentStatus.WARNING);
                    break;
                default:
                    changeStatus(deploymentId, DeploymentStatus.DEPLOYED);
                }
            }

        }, 5, TimeUnit.SECONDS);
    }

    @Override
    protected synchronized void doUndeploy(final String deploymentId) {
        log.info("Undeploying deployment [" + deploymentId + "]");
        changeStatus(deploymentId, DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS);
        if (instanceInformationsMap.containsKey(deploymentId)) {
            Map<String, Map<Integer, InstanceInformation>> appInfo = instanceInformationsMap.get(deploymentId);
            for (Map.Entry<String, Map<Integer, InstanceInformation>> nodeEntry : appInfo.entrySet()) {
                for (Map.Entry<Integer, InstanceInformation> instanceEntry : nodeEntry.getValue().entrySet()) {
                    instanceEntry.getValue().setState("stopping");
                    instanceEntry.getValue().setInstanceStatus(InstanceStatus.PROCESSING);
                    notifyInstanceStateChanged(deploymentId, nodeEntry.getKey(), instanceEntry.getKey(), instanceEntry.getValue(), 1);
                }
            }
        }
        executorService.schedule(new Runnable() {

            @Override
            public void run() {
                changeStatus(deploymentId, DeploymentStatus.UNDEPLOYED);
            }
        }, 5, TimeUnit.SECONDS);
    }

    @Override
    protected synchronized DeploymentStatus doChangeStatus(final String deploymentId, final DeploymentStatus status) {
        DeploymentStatus oldDeploymentStatus = deploymentsMap.put(deploymentId, status);
        if (oldDeploymentStatus == null) {
            oldDeploymentStatus = DeploymentStatus.UNDEPLOYED;
        }
        log.info("Deployment [" + deploymentId + "] pass from status [" + oldDeploymentStatus + "] to [" + status + "]");
        executorService.schedule(new Runnable() {

            @Override
            public void run() {
                Deployment deployment = alienDAO.findById(Deployment.class, deploymentId);
                String cloudId = deployment.getCloudId();
                PaaSDeploymentStatusMonitorEvent event = new PaaSDeploymentStatusMonitorEvent();
                event.setDeploymentStatus(status);
                event.setDate((new Date()).getTime());
                event.setDeploymentId(deploymentId);
                event.setCloudId(cloudId);
                toBeDeliveredEvents.add(event);
                PaaSMessageMonitorEvent messageMonitorEvent = new PaaSMessageMonitorEvent();
                messageMonitorEvent.setDate((new Date()).getTime());
                messageMonitorEvent.setDeploymentId(deploymentId);
                messageMonitorEvent.setCloudId(cloudId);
                messageMonitorEvent.setMessage("APPLICATIONS.RUNTIME.EVENTS.MESSAGE_EVENT.STATUS_DEPLOYMENT_CHANGED");
                toBeDeliveredEvents.add(messageMonitorEvent);
            }
        }, 1, TimeUnit.SECONDS);
        return oldDeploymentStatus;
    }

    private void notifyInstanceStateChanged(final String deploymentId, final String nodeId, final Integer instanceId, final InstanceInformation information,
            long delay) {
        final InstanceInformation cloned = new InstanceInformation();
        cloned.setAttributes(information.getAttributes());
        cloned.setInstanceStatus(information.getInstanceStatus());
        cloned.setProperties(information.getProperties());
        cloned.setRuntimeProperties(information.getRuntimeProperties());
        cloned.setState(information.getState());
        executorService.schedule(new Runnable() {

            @Override
            public void run() {
                Deployment deployment = alienDAO.findById(Deployment.class, deploymentId);
                String cloudId = deployment.getCloudId();
                PaaSInstanceStateMonitorEvent event = new PaaSInstanceStateMonitorEvent();
                event.setInstanceId(instanceId.toString());
                event.setInstanceState(cloned.getState());
                event.setInstanceStatus(cloned.getInstanceStatus());
                event.setNodeTemplateId(nodeId);
                event.setDate((new Date()).getTime());
                event.setDeploymentId(deploymentId);
                event.setProperties(cloned.getProperties());
                event.setRuntimeProperties(cloned.getRuntimeProperties());
                event.setAttributes(cloned.getAttributes());
                event.setCloudId(cloudId);
                toBeDeliveredEvents.add(event);
                PaaSMessageMonitorEvent messageMonitorEvent = new PaaSMessageMonitorEvent();
                messageMonitorEvent.setDate((new Date()).getTime());
                messageMonitorEvent.setDeploymentId(deploymentId);
                messageMonitorEvent.setMessage("APPLICATIONS.RUNTIME.EVENTS.MESSAGE_EVENT.INSTANCE_STATE_CHANGED");
                messageMonitorEvent.setCloudId(cloudId);
                toBeDeliveredEvents.add(messageMonitorEvent);
            }
        }, delay, TimeUnit.SECONDS);
    }

    private void notifyInstanceRemoved(final String deploymentId, final String nodeId, final Integer instanceId, long delay) {
        executorService.schedule(new Runnable() {

            @Override
            public void run() {
                Deployment deployment = alienDAO.findById(Deployment.class, deploymentId);
                PaaSInstanceStateMonitorEvent event = new PaaSInstanceStateMonitorEvent();
                event.setInstanceId(instanceId.toString());
                event.setNodeTemplateId(nodeId);
                event.setDate((new Date()).getTime());
                event.setDeploymentId(deploymentId);
                event.setCloudId(deployment.getCloudId());
                toBeDeliveredEvents.add(event);
            }
        }, delay, TimeUnit.SECONDS);
    }

    @Override
    public Map<String, Map<Integer, InstanceInformation>> getInstancesInformation(String deploymentId, Topology topology) {
        return instanceInformationsMap.get(deploymentId);
    }

    private synchronized void doChangeInstanceInformations(String applicationId, Map<String, Map<Integer, InstanceInformation>> currentInformations) {
        Iterator<Entry<String, Map<Integer, InstanceInformation>>> appIterator = currentInformations.entrySet().iterator();
        while (appIterator.hasNext()) {
            Entry<String, Map<Integer, InstanceInformation>> iStatuses = appIterator.next();
            Iterator<Entry<Integer, InstanceInformation>> iterator = iStatuses.getValue().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, InstanceInformation> iStatus = iterator.next();
                changeInstanceState(applicationId, iStatuses.getKey(), iStatus.getKey(), iStatus.getValue(), iterator);
            }
            if (iStatuses.getValue().isEmpty()) {
                appIterator.remove();
            }
        }
    }

    private void changeInstanceState(String id, String nodeId, Integer instanceId, InstanceInformation information,
            Iterator<Entry<Integer, InstanceInformation>> iterator) {
        String currentState = information.getState();
        String nextState = getNextState(currentState);
        if (nextState != null) {
            information.setState(nextState);
            if ("started".equals(nextState)) {
                information.setInstanceStatus(InstanceStatus.SUCCESS);
            }
            if ("terminated".equals(nextState)) {
                iterator.remove();
                notifyInstanceRemoved(id, nodeId, instanceId, 2);
            } else {
                notifyInstanceStateChanged(id, nodeId, instanceId, information, 2);
            }
        }
    }

    private String getNextState(String currentState) {
        switch (currentState) {
        case "init":
            return "creating";
        case "creating":
            return "created";
        case "created":
            return "configuring";
        case "configuring":
            return "configured";
        case "configured":
            return "starting";
        case "starting":
            return "started";
        case "stopping":
            return "stopped";
        case "stopped":
            return "uninstalled";
        case "uninstalled":
            return "terminated";
        default:
            return null;
        }
    }

    private interface ScalingVisitor {
        void visit(String nodeTemplateId);
    }

    private void doScaledUpNode(ScalingVisitor scalingVisitor, String nodeTemplateId, Map<String, NodeTemplate> nodeTemplates) {
        scalingVisitor.visit(nodeTemplateId);
        for (Entry<String, NodeTemplate> nEntry : nodeTemplates.entrySet()) {
            if (nEntry.getValue().getRelationships() != null) {
                for (Entry<String, RelationshipTemplate> rt : nEntry.getValue().getRelationships().entrySet()) {
                    if (nodeTemplateId.equals(rt.getValue().getTarget()) && "tosca.relationships.HostedOn".equals(rt.getValue().getType())) {
                        doScaledUpNode(scalingVisitor, nEntry.getKey(), nodeTemplates);
                    }
                }
            }
        }
    }

    @Override
    public void scale(String deploymentId, String nodeTemplateId, final int instances) {
        Deployment deployment = alienDAO.findById(Deployment.class, deploymentId);
        Topology topology = alienDAO.findById(Topology.class, deployment.getTopologyId());
        final Map<String, Map<Integer, InstanceInformation>> existingInformations = instanceInformationsMap.get(deploymentId);
        if (existingInformations != null && existingInformations.containsKey(nodeTemplateId)) {
            ScalingVisitor scalingVisitor = new ScalingVisitor() {
                @Override
                public void visit(String nodeTemplateId) {
                    Map<Integer, InstanceInformation> nodeInformations = existingInformations.get(nodeTemplateId);
                    if (nodeInformations != null) {
                        int currentSize = nodeInformations.size();
                        if (instances > 0) {
                            for (int i = currentSize + 1; i < currentSize + instances + 1; i++) {
                                nodeInformations.put(i, newInstance(i));
                            }
                        } else {
                            for (int i = currentSize + instances + 1; i < currentSize + 1; i++) {
                                if (nodeInformations.containsKey(new Integer(i))) {
                                    nodeInformations.get(new Integer(i)).setState("stopping");
                                    nodeInformations.get(new Integer(i)).setInstanceStatus(InstanceStatus.PROCESSING);
                                }
                            }
                        }
                    }
                }
            };
            doScaledUpNode(scalingVisitor, nodeTemplateId, topology.getNodeTemplates());
        }
    }

    @Override
    public AbstractMonitorEvent[] getEventsSince(Date date, int maxEvents) {
        AbstractMonitorEvent[] events = toBeDeliveredEvents.toArray(new AbstractMonitorEvent[toBeDeliveredEvents.size()]);
        toBeDeliveredEvents.clear();
        return events;
    }

    @Override
    protected String doExecuteOperation(NodeOperationExecRequest request) {
        List<String> allowedOperation = Arrays.asList("updateWar", "updateWarFile", "addNode");
        String result = null;
        try {
            log.info("TRIGGERING OPERATION : {}", request.getOperationName());
            Thread.sleep(3000);
            log.info(" COMMAND REQUEST IS: " + JsonUtil.toString(request));
        } catch (JsonProcessingException | InterruptedException e) {
            log.error("OPERATION execution failled!", e);
            log.info("RESULT IS: KO");
            return "KO";
        }
        // only 2 operations in allowedOperation will return OK
        result = allowedOperation.contains(request.getOperationName()) ? "OK" : "KO";
        log.info("RESULT IS : {}", result);
        return result;
    }

    @Override
    public Map<String, PropertyDefinition> getDeploymentPropertyMap() {

        // Field 1 : managerUrl as string
        PropertyDefinition managerUrl = new PropertyDefinition();
        managerUrl.setType(ToscaType.STRING.toString());
        managerUrl.setRequired(true);
        managerUrl.setDescription("PaaS manager URL");
        managerUrl.setConstraints(null);
        PatternConstraint manageUrlConstraint = new PatternConstraint();
        manageUrlConstraint.setPattern("http://.+");
        managerUrl.setConstraints(Arrays.asList((PropertyConstraint) manageUrlConstraint));

        // Field 2 : number backup with constraint
        PropertyDefinition numberBackup = new PropertyDefinition();
        numberBackup.setType(ToscaType.INTEGER.toString());
        numberBackup.setRequired(true);
        numberBackup.setDescription("Number of backup");
        numberBackup.setConstraints(null);
        GreaterOrEqualConstraint greaterOrEqualConstraint = new GreaterOrEqualConstraint();
        greaterOrEqualConstraint.setGreaterOrEqual(String.valueOf("1"));
        numberBackup.setConstraints(Lists.newArrayList((PropertyConstraint) greaterOrEqualConstraint));

        // Field 3 : email manager
        PropertyDefinition managerEmail = new PropertyDefinition();
        managerEmail.setType(ToscaType.STRING.toString());
        managerEmail.setRequired(true);
        managerEmail.setDescription("PaaS manager email");
        managerEmail.setConstraints(null);
        PatternConstraint managerEmailConstraint = new PatternConstraint();
        managerEmailConstraint.setPattern(".+@.+");
        managerEmail.setConstraints(Arrays.asList((PropertyConstraint) managerEmailConstraint));

        deploymentProperties.put("managementUrl", managerUrl);
        deploymentProperties.put("numberBackup", numberBackup);
        deploymentProperties.put("managerEmail", managerEmail);

        return deploymentProperties;
    }

    @Override
    public ProviderConfig getDefaultConfiguration() {
        return null;
    }

    @Override
    public void setConfiguration(ProviderConfig configuration) {
        log.info("In the plugin configurator <" + this.getClass().getName() + ">");
        try {
            log.info("The config object Tags is: " + JsonUtil.toString(configuration.getTags()));
        } catch (JsonProcessingException e) {
            log.error("Fails to serialize configuration object as json string", e);
        }
    }
}