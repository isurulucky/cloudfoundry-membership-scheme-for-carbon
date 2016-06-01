/*
* Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.wso2.carbon.membership.scheme.cloudfoundry;

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.*;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.clustering.ClusteringMessage;
import org.apache.axis2.description.Parameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.core.clustering.hazelcast.HazelcastCarbonClusterImpl;
import org.wso2.carbon.core.clustering.hazelcast.HazelcastMembershipScheme;
import org.wso2.carbon.core.clustering.hazelcast.HazelcastUtil;
import org.wso2.carbon.membership.scheme.cloudfoundry.api.ApplicationApiClient;
import org.wso2.carbon.membership.scheme.cloudfoundry.api.TokenApiClient;
import org.wso2.carbon.membership.scheme.cloudfoundry.api.bean.Application;
import org.wso2.carbon.membership.scheme.cloudfoundry.api.bean.InstanceInfo;
import org.wso2.carbon.membership.scheme.cloudfoundry.api.bean.Instances;
import org.wso2.carbon.membership.scheme.cloudfoundry.api.bean.Token;
import org.wso2.carbon.membership.scheme.cloudfoundry.exceptions.CloudFoundryErrorDecoder;
import org.wso2.carbon.membership.scheme.cloudfoundry.exceptions.CloudFoundryMembershipSchemeException;
import org.wso2.carbon.utils.xml.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cloud Foundry membership scheme provides carbon cluster discovery on Cloud Foundry.
 */
public class CloudFoundryMembershipScheme implements HazelcastMembershipScheme {

    private static final Log log = LogFactory.getLog(CloudFoundryMembershipScheme.class);

    private final Map<String, Parameter> parameters;
    protected final NetworkConfig nwConfig;
    private final List<ClusteringMessage> messageBuffer;
    private HazelcastInstance primaryHazelcastInstance;
    private HazelcastCarbonClusterImpl carbonCluster;
    private boolean skipSSLVerification;

    public CloudFoundryMembershipScheme(Map<String, Parameter> parameters,
                                        String primaryDomain,
                                        Config config,
                                        HazelcastInstance primaryHazelcastInstance,
                                        List<ClusteringMessage> messageBuffer) {
        this.parameters = parameters;
        this.primaryHazelcastInstance = primaryHazelcastInstance;
        this.messageBuffer = messageBuffer;
        this.nwConfig = config.getNetworkConfig();
    }

    @Override
    public void setPrimaryHazelcastInstance(HazelcastInstance primaryHazelcastInstance) {
        this.primaryHazelcastInstance = primaryHazelcastInstance;
    }

    @Override
    public void setLocalMember(Member localMember) {
    }

    @Override
    public void setCarbonCluster(HazelcastCarbonClusterImpl hazelcastCarbonCluster) {
        this.carbonCluster = hazelcastCarbonCluster;
    }

    @Override
    public void init() throws ClusteringFault {
        try {
            log.info("Initializing kubernetes membership scheme...");

            nwConfig.getJoin().getMulticastConfig().setEnabled(false);
            nwConfig.getJoin().getAwsConfig().setEnabled(false);
            TcpIpConfig tcpIpConfig = nwConfig.getJoin().getTcpIpConfig();
            tcpIpConfig.setEnabled(true);

            // Try to read parameters from env variables
            String cfTokenApiUrl = System.getenv(Constants.PARAMETER_NAME_CLOUD_FOUNDRY_TOKEN_API_URL);
            String cfApiUrl = System.getenv(Constants.PARAMETER_NAME_CLOUD_FOUNDRY_API_URL);
            String cfApplicationNames = System.getenv(Constants.PARAMETER_NAME_CLOUD_FOUNDRY_APPLICATION_NAMES);
            String cfApiUsername = System.getenv(Constants.PARAMETER_NAME_CLOUD_FOUNDRY_API_USERNAME);
            String cfApiPassword = System.getenv(Constants.PARAMETER_NAME_CLOUD_FOUNDRY_API_PASSWORD);
            String skipCfApiSslVerification = System.getenv(Constants.PARAMETER_NAME_CLOUD_FOUNDRY_API_SKIP_SSL_VERIFICATION);

            // If not available read from clustering configuration
            if(StringUtils.isEmpty(cfTokenApiUrl)) {
                cfTokenApiUrl = getParameterValue(Constants.PARAMETER_NAME_CLOUD_FOUNDRY_TOKEN_API_URL, null);
            }

            if(StringUtils.isEmpty(cfApiUrl)) {
                cfApiUrl = getParameterValue(Constants.PARAMETER_NAME_CLOUD_FOUNDRY_API_URL, null);
            }

            if(StringUtils.isEmpty(cfApplicationNames)) {
                cfApplicationNames = getParameterValue(Constants.PARAMETER_NAME_CLOUD_FOUNDRY_APPLICATION_NAMES, null);
            }

            if(StringUtils.isEmpty(cfApiUsername)) {
                cfApiUsername = getParameterValue(Constants.PARAMETER_NAME_CLOUD_FOUNDRY_API_USERNAME, null);
            }

            if(StringUtils.isEmpty(cfApiPassword)) {
                cfApiPassword = getParameterValue(Constants.PARAMETER_NAME_CLOUD_FOUNDRY_API_PASSWORD, null);
            }

            if (StringUtils.isEmpty(skipCfApiSslVerification)){
                skipCfApiSslVerification = getParameterValue(Constants.PARAMETER_NAME_CLOUD_FOUNDRY_API_SKIP_SSL_VERIFICATION, "false");
            }

            skipSSLVerification = Boolean.parseBoolean(skipCfApiSslVerification);

            log.info(String.format("Cloud Foundry memberhsip scheme configuration: [api-url] %s [application-names] %s " +
                    "[skip-api-ssl-verification] %s", cfApiUrl, cfApplicationNames, skipSSLVerification));

            String[] cfAppNames = cfApplicationNames.split(",");
            for (String cfAppName : cfAppNames) {
                List<String> containerIPs = findContainerIPs(cfTokenApiUrl, cfApiUrl, cfAppName, cfApiUsername, cfApiPassword);
                for(String containerIP : containerIPs) {
                    tcpIpConfig.addMember(containerIP);
                    log.info("Member added to cluster configuration: [container-ip] " + containerIP);
                }
            }
            log.info("Cloud Foundry membership scheme initialized successfully");
        } catch (Exception e) {
            log.error(e);
            throw new ClusteringFault("Kubernetes membership initialization failed", e);
        }
    }

    protected String getParameterValue(String parameterName, String defaultValue) throws ClusteringFault {
        Parameter cfConfigParam = getParameter(parameterName);
        if (cfConfigParam == null) {
            if (defaultValue == null) {
                throw new ClusteringFault(parameterName + " parameter not found");
            } else {
                return defaultValue;
            }
        }
        return (String) cfConfigParam.getValue();
    }

    protected List<String> findContainerIPs(String cfTokenApiUrl, String cfApiUrl, String applicationName, String username, String password)
            throws CloudFoundryMembershipSchemeException {

        TokenApiClient tokenAPIClient = Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .errorDecoder(new CloudFoundryErrorDecoder())
                .target(TokenApiClient.class, cfTokenApiUrl);

        // retrieve Token
        // TODO: send username and password in the request body
        Map<String, Object> queryMap = new HashMap<String, Object>();
        queryMap.put(Constants.GRANT_TYPE, Constants.GRANT_TYPE_PASSWORD);
        queryMap.put(Constants.QUERY_PARAM_USERNAME, username);
        queryMap.put(Constants.QUERY_PARAM_PASSWORD, password);
        Token token = tokenAPIClient.getToken(queryMap);

        checkIfNullAndHandle(token, "OAuth token null for user " + username);
        checkIfNullAndHandle(token.getAccess_token(), "OAuth token null for user " + username);

        // retrieve Application
        ApplicationApiClient applicationAPIClient = Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .errorDecoder(new CloudFoundryErrorDecoder())
                .target(ApplicationApiClient.class, cfApiUrl);

        // clear previously used query params
        queryMap.clear();
        queryMap.put("q", "name:" + applicationName);
        Application application = applicationAPIClient.getApplication(token.getAccess_token(),
                queryMap);

        checkIfNullAndHandle(application, "Unable to retrieve application model for app name " + applicationName);

        // clear previously used query params
        queryMap.clear();

        // assumption: application name is unique across all applications used
        if (application.getResources().size() > 1) {
            log.warn("More than one applications found for app name " + applicationName + ", only the first one will be considered");
        }
        // retrieve application instances and get ips and ports
        Instances instances = applicationAPIClient.getApplicationInstances(token.getAccess_token(), application
                .getResources().get(0).getMetadata().getGuid());

        checkIfNullAndHandle(instances, "No instances found for app name " + applicationName);

        List<String> ipAndPortTuples = new ArrayList<String>();
        for (InstanceInfo instanceInfo : instances.getInstanceInfoMap().values()) {
            // TODO: check port mapping stuff in CF, and append the port if necessary as below:
            // ipAndPortTuples.add(instanceInfo.getStats().getHost() + ":" + instanceInfo.getStats().getPort());
            ipAndPortTuples.add(instanceInfo.getStats().getHost());
        }

        return ipAndPortTuples;
    }

    private void checkIfNullAndHandle (Object obj, String errorMsg) throws CloudFoundryMembershipSchemeException {
        if (obj == null) {
            throw new CloudFoundryMembershipSchemeException(errorMsg);
        }
    }

    @Override
    public void joinGroup() throws ClusteringFault {
        primaryHazelcastInstance.getCluster().addMembershipListener(new CloudFoundryMembershipListener());
    }

    private Parameter getParameter(String name) {
        return parameters.get(name);
    }

    /**
     * CloudFoundryMembershipListener membership scheme listener
     */
    private class CloudFoundryMembershipListener implements MembershipListener {

        @Override
        public void memberAdded(MembershipEvent membershipEvent) {
            Member member = membershipEvent.getMember();

            // Send all cluster messages
            carbonCluster.memberAdded(member);
            log.info("Member joined [" + member.getUuid() + "]: " + member.getSocketAddress().toString());
            // Wait for sometime for the member to completely join before replaying messages
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
            HazelcastUtil.sendMessagesToMember(messageBuffer, member, carbonCluster);
        }

        @Override
        public void memberRemoved(MembershipEvent membershipEvent) {
            Member member = membershipEvent.getMember();
            carbonCluster.memberRemoved(member);
            log.info("Member left [" + member.getUuid() + "]: " + member.getSocketAddress().toString());
        }

        @Override
        public void memberAttributeChanged(MemberAttributeEvent memberAttributeEvent) {
            if (log.isDebugEnabled()) {
                log.debug("Member attribute changed: [" + memberAttributeEvent.getKey() + "] " +
                        memberAttributeEvent.getValue());
            }
        }
    }
}