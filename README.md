## CloudFoundry Membership Scheme for Carbon

Supports running a clustered deployment of WSO2 Carbon Servers on CloudFoundry. 

### How to use

* Take a clone of the cloud foundry membershp scheme [repositpory] (https://github.com/isurulucky/cloudfoundry-membership-scheme-for-carbon.git)
* Use mvn clean install to build it
* Locate the jar cloudfoundry-membership-scheme-<VERSION>.jar in the target directory
* Copy the jar file to the repository/components/dropins directory of the relevant carbon server.
* Provide the following details in the startup script (bin/wso2server.sh) of the carbon server:
    -DCLOUD_FOUNDRY_TOKEN_API_URL=https://login.run.pivotal.io \
    -DCLOUD_FOUNDRY_API_URL=https://api.run.pivotal.io/v2 \
    -DCLOUD_FOUNDRY_API_USERNAME=xxxxx \
    -DCLOUD_FOUNDRY_API_PASSWORD=yyyyy \
    -DCLOUD_FOUNDRY_APPLICATION_NAMES=<name_of_cloud_foundry_application> \ 
* Make the following changes in the axis2.xml (repository/conf/axis2/axis2.xml) of the carbon server:
    1. Enable clustering by making 'enable=true' in clustering element
    2. Add the following line under the clustering element:
        <parameter name="membershipSchemeClassName">org.wso2.carbon.membership.scheme.cloudfoundry.CloudFoundryMembershipScheme</parameter>
    3. Change the membershipScheme to 'cloudfoundry'
       

License: Apache2
