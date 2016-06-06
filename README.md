## CloudFoundry Membership Scheme for Carbon

Please note that this code is still under development and testing

Supports running a clustered deployment of WSO2 Carbon Servers on CloudFoundry. 

#### How to use

* Create an account for yourself in [Pivotal Web Services] (https://login.run.pivotal.io/login)
* Take a clone of the cloud foundry membershp scheme [repository] (https://github.com/isurulucky/cloudfoundry-membership-scheme-for-carbon.git)
* Use mvn clean install to build the code
* Locate the jar `cloudfoundry-membership-scheme-<VERSION>.jar` in the target directory
* Copy the jar file to the repository/components/dropins directory of the relevant carbon server.
* Provide the following details in the startup script (bin/wso2server.sh) of the carbon server:
    `-DCLOUD_FOUNDRY_TOKEN_API_URL=https://login.run.pivotal.io \
    -DCLOUD_FOUNDRY_API_URL=https://api.run.pivotal.io/v2 \
    -DCLOUD_FOUNDRY_API_USERNAME=xxxxx \
    -DCLOUD_FOUNDRY_API_PASSWORD=yyyyy \
    -DCLOUD_FOUNDRY_APPLICATION_NAMES=<NAME(S)_OF_CF_APPS_TO_FORM_A_CLUSTER> \` 
* Make the following changes in the axis2.xml (repository/conf/axis2/axis2.xml) of the carbon server:
    1. Enable clustering by making 'enable=true' in clustering element
    2. Add the following line under the clustering element:
        `<parameter name="membershipSchemeClassName">org.wso2.carbon.membership.scheme.cloudfoundry.CloudFoundryMembershipScheme</parameter>`
    3. Change the membershipScheme to 'cloudfoundry'
* Refer [https://medium.com/@imesh/how-to-deploy-wso2-middleware-on-cloud-foundry-3b50291734e2#.2wh53xbto] (https://medium.com/@imesh/how-to-deploy-wso2-middleware-on-cloud-foundry-3b50291734e2#.2wh53xbto) by Imesh (imesh@apache.org) for details on running WSO2 products on Cloud Foundry. 
       

License: Apache2
