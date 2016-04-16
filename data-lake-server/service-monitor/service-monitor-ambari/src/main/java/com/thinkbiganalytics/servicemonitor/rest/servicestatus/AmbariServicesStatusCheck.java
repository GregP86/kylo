package com.thinkbiganalytics.servicemonitor.rest.servicestatus;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.thinkbiganalytics.servicemonitor.check.*;
import com.thinkbiganalytics.servicemonitor.model.*;
import com.thinkbiganalytics.servicemonitor.rest.client.ambari.AmbariClient;
import com.thinkbiganalytics.servicemonitor.rest.model.ambari.*;
import com.thinkbiganalytics.servicemonitor.support.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClientException;

import java.util.*;

/**
 * Ambari Service bean autowired in via the ServiceStatusManager looking for this ServicesStatusCheck interface
 *
 */
public class AmbariServicesStatusCheck implements ServicesStatusCheck {
    private static final Logger LOG = LoggerFactory.getLogger(AmbariServicesStatusCheck.class);
    @Value("${ambari.services.status:#{null}}")
    private String services;

     /*
    https://github.com/apache/ambari/blob/trunk/ambari-server/docs/api/v1/host-component-resources.md

    State 	Description
INIT 	The initial clean state after the component is first created.
INSTALLING 	In the process of installing the component.
INSTALL_FAILED 	The component install failed.
INSTALLED 	The component has been installed successfully but is not currently running.
STARTING 	In the process of starting the component.
STARTED 	The component has been installed and started.
STOPPING 	In the process of stopping the component.
UNINSTALLING 	In the process of uninstalling the component.
UNINSTALLED 	The component has been successfully uninstalled.
WIPING_OUT 	In the process of wiping out the installed component.
UPGRADING 	In the process of upgrading the component.
MAINTENANCE 	The component has been marked for maintenance.
UNKNOWN 	The component state can not be determined.
     */

    @Autowired
    private AmbariClient ambariClient;

    @Override
    public List<ServiceStatusResponse> healthCheck() {

        List<ServiceStatusResponse> serviceStatusResponseList = new ArrayList<>();

        //Get the Map of Services and optional Components we are tracking
        Map<String, List<String>> definedServiceComponentMap = ServiceMonitorCheckUtil.getMapOfServiceAndComponents(services);
        //LOG.info("Check Ambari "+definedServiceComponentMap);

        if (definedServiceComponentMap != null && !definedServiceComponentMap.isEmpty()) {

            try {
                AmbariClient client = ambariClient;
                //get the Clusers from ambari
                List<String> clusterNames = client.getAmbariClusterNames();
                //get the Service Status from Ambari
                ServiceComponentInfoSummary response = client.getServiceComponentInfo(clusterNames, services);
                //get alert info for these services as well
                AlertSummary alertSummary = client.getAlerts(clusterNames, services);
                //Convert the Ambari Alerts to the Pipeline Controller Alert
                List<ServiceAlert> serviceAlerts = transformAmbariAlert(alertSummary);
                //Convert the Ambari ServiceComponentInfo objects to Pipeline Controller ServiceComponents
                serviceStatusResponseList = transformAmbariServiceComponents(response, serviceAlerts, definedServiceComponentMap);
            } catch (RestClientException e) {
                Throwable cause;
                if (e.getCause() != null) {
                    cause = e.getCause();
                } else {
                    cause = e;
                }
                ServiceComponent ambariServiceComponent = new DefaultServiceComponent.Builder("Unknown", "Ambari", "Ambari REST_CLIENT", ServiceComponent.STATE.DOWN).exception(cause).build();
                List<ServiceComponent> ambariComponents = new ArrayList<>();
                ambariComponents.add(ambariServiceComponent);
                ServiceStatusResponse serviceStatusResponse = new DefaultServiceStatusResponse(ambariServiceComponent.getServiceName(), ambariComponents);
                serviceStatusResponseList.add(serviceStatusResponse);
                //add the other services as being Warnings
                addAmbariServiceErrors(cause.getMessage(), serviceStatusResponseList, definedServiceComponentMap);

            }
        }
        return serviceStatusResponseList;
    }


    /**
     * Convert Ambari ServiceComponentInfo into PipelineController ServiceComponent
     *
     * @param ambariServiceComponents
     * @param definedServiceComponentMap
     * @return
     */
    private List<ServiceStatusResponse> transformAmbariServiceComponents(ServiceComponentInfoSummary ambariServiceComponents, List<ServiceAlert> serviceAlerts, Map<String, List<String>> definedServiceComponentMap) {
        List<ServiceStatusResponse> list = new ArrayList<>();
        if (ambariServiceComponents != null) {
            Map<String, List<ServiceComponent>> serviceComponentMap = new HashMap<String, List<ServiceComponent>>();

            for (ServiceComponentInfoItem item : ambariServiceComponents.getItems()) {
                ServiceComponent.STATE state = getServiceComponentState(item);
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(item.getServiceComponentInfo().getState());
                String message = stringBuilder.toString();

                String name = item.getServiceComponentInfo().getComponentName();
                String serviceName = item.getServiceComponentInfo().getServiceName();
                String clusterName = item.getServiceComponentInfo().getClusterName();
                ServiceComponent component = new DefaultServiceComponent.Builder(clusterName, serviceName, name, state).alerts(alertsForComponent(serviceAlerts, item.getServiceComponentInfo().getComponentName())).message(message).build();

                if (!serviceComponentMap.containsKey(component.getServiceName())) {
                    serviceComponentMap.put(component.getServiceName(), new ArrayList<ServiceComponent>());
                }

                if (definedServiceComponentMap.get(component.getServiceName()).contains(ServiceMonitorCheckUtil.ALL_COMPONENTS) || definedServiceComponentMap.get(component.getServiceName()).contains(component.getName())) {
                    serviceComponentMap.get(component.getServiceName()).add(component);
                }
            }
            //build the response
            for (Map.Entry<String, List<ServiceComponent>> entry : serviceComponentMap.entrySet()) {


                List<ServiceAlert> alertsForService = alertsForService(serviceAlerts, entry.getKey());
                ServiceStatusResponse serviceStatusResponse = new DefaultServiceStatusResponse(entry.getKey(), entry.getValue(), alertsForService);
                list.add(serviceStatusResponse);
            }

        }
        return list;
    }

    /**
     * @param list
     * @param definedServiceComponentMap
     */
    private void addAmbariServiceErrors(String exceptionMessage, List<ServiceStatusResponse> list, Map<String, List<String>> definedServiceComponentMap) {
        if (definedServiceComponentMap != null && !definedServiceComponentMap.isEmpty()) {
            String message = "Status Unknown. Unable to check service.  Ambari connection error: " + exceptionMessage;
            for (Map.Entry<String, List<String>> entry : definedServiceComponentMap.entrySet()) {
                String serviceName = entry.getKey();
                List<String> componentNames = entry.getValue();
                List<ServiceComponent> components = new ArrayList<>();
                if (componentNames != null && !componentNames.isEmpty()) {
                    for (String componentName : componentNames) {
                        if (ServiceMonitorCheckUtil.ALL_COMPONENTS.equals(componentName)) {
                            componentName = serviceName;
                        }
                        ServiceComponent serviceComponent = new DefaultServiceComponent.Builder("Unknown", serviceName, componentName, ServiceComponent.STATE.UNKNOWN).message(message).build();
                        components.add(serviceComponent);
                    }
                } else {
                    //add the component based uppon the Service Name
                    ServiceComponent serviceComponent = new DefaultServiceComponent.Builder("Unknown", serviceName, serviceName, ServiceComponent.STATE.UNKNOWN).message(message).build();
                    components.add(serviceComponent);
                }
                ServiceStatusResponse serviceStatusResponse = new DefaultServiceStatusResponse(serviceName, components);
                list.add(serviceStatusResponse);

            }
        }
    }

    /**
     * State 	Description
     * INIT 	Initial/Clean state.
     * INSTALLING 	In the process of installing.
     * INSTALL_FAILED 	Install failed.
     * INSTALLED 	State when install completed successfully.
     * STARTING 	In the process of starting.
     * STARTED 	State when start completed successfully.
     * STOPPING 	In the process of stopping.
     * UNINSTALLING 	In the process of uninstalling.
     * UNINSTALLED 	State when uninstall completed successfully.
     * WIPING_OUT 	In the process of wiping out the install.
     * UPGRADING 	In the process of upgrading the deployed bits.
     * DISABLED 	Disabled master’s backup state.
     * UNKNOWN 	State could not be determined.
     *
     * @param serviceComponentInfoItem
     * @return
     */
    private ServiceComponent.STATE getServiceComponentState(ServiceComponentInfoItem serviceComponentInfoItem) {
        ServiceComponent.STATE state = ServiceComponent.STATE.DOWN;
        serviceComponentInfoItem.updateServiceComponentInfoState();
        String ambariState = serviceComponentInfoItem.getServiceComponentInfo().getState();
        //check for category
        boolean isClient = serviceComponentInfoItem.getServiceComponentInfo().getCategory().equalsIgnoreCase("CLIENT");
        if (isClient && "INSTALLED".equals(ambariState)) {
            state = ServiceComponent.STATE.UP;
        } else {
            if ("STARTING".equalsIgnoreCase(ambariState)) {
                state = ServiceComponent.STATE.STARTING;
            } else if ("STARTED".equalsIgnoreCase(ambariState)) {
                state = ServiceComponent.STATE.UP;
            } else if ("UNKNOWN".equalsIgnoreCase(ambariState)) {
                state = ServiceComponent.STATE.UNKNOWN;
            }
        }
        return state;
    }

    /**
     * return a matching List of ServiceAlerts based upon the incoming component name
     *
     * @param alerts
     * @param component
     * @return
     */
    private List<ServiceAlert> alertsForComponent(List<ServiceAlert> alerts, final String component) {
        Predicate<ServiceAlert> predicate = new Predicate<ServiceAlert>() {
            @Override
            public boolean apply(ServiceAlert alert) {
                return alert.getComponentName() != null && alert.getComponentName().equals(component);
            }
        };
        Collection<ServiceAlert> matchingAlerts = Collections2.filter(alerts, predicate);
        if (matchingAlerts != null && !matchingAlerts.isEmpty()) {
            return new ArrayList<ServiceAlert>(matchingAlerts);
        }
        return null;
    }

    private List<ServiceAlert> alertsForService(List<ServiceAlert> alerts, final String service) {
        Predicate<ServiceAlert> predicate = new Predicate<ServiceAlert>() {
            @Override
            public boolean apply(ServiceAlert alert) {
                return alert.getServiceName() != null && alert.getServiceName().equals(service);
            }
        };
        Collection<ServiceAlert> matchingAlerts = Collections2.filter(alerts, predicate);
        if (matchingAlerts != null && !matchingAlerts.isEmpty()) {
            return new ArrayList<ServiceAlert>(matchingAlerts);
        }
        return null;
    }


    public List<ServiceAlert> transformAmbariAlert(AlertSummary alertSummary) {
        List<ServiceAlert> serviceAlerts = new ArrayList<>();
        List<AlertItem> alertItems = alertSummary.getItems();
        if (alertItems != null) {
            for (AlertItem alertItem : alertItems) {
                Alert alert = alertItem.getAlert();
                ServiceAlert serviceAlert = new DefaultServiceAlert();
                serviceAlert.setServiceName(alertItem.getAlert().getServiceName());
                serviceAlert.setComponentName(alert.getComponentName());
                serviceAlert.setFirstTimestamp(new Date(alert.getOriginalTimestamp()));
                serviceAlert.setLatestTimestamp(new Date(alert.getLatestTimestamp()));
                serviceAlert.setLabel(alert.getLabel());
                serviceAlert.setMessage(alert.getText());
                if (StringUtils.isNotBlank(alert.getState())) {
                    try {
                        serviceAlert.setState(ServiceAlert.STATE.valueOf(alert.getState()));
                    } catch (IllegalArgumentException e) {
                        serviceAlert.setState(ServiceAlert.STATE.UNKNOWN);
                    }
                } else {
                    serviceAlert.setState(ServiceAlert.STATE.UNKNOWN);
                }

                serviceAlerts.add(serviceAlert);
            }
        }
        return serviceAlerts;
    }

    protected void setAmbariClient(AmbariClient ambariClient) {
        this.ambariClient = ambariClient;
    }

    protected void setServices(String services) {
        this.services = services;
    }
}
