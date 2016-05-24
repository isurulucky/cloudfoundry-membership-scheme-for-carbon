/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.membership.scheme.cloudfoundry.api;

import feign.Headers;
import feign.Param;
import feign.QueryMap;
import feign.RequestLine;
import org.wso2.carbon.membership.scheme.cloudfoundry.api.bean.Application;
import org.wso2.carbon.membership.scheme.cloudfoundry.api.bean.Instances;

import java.util.Map;

public interface ApplicationApiClient {

    @RequestLine("GET /apps")
    @Headers("authorization: bearer {token}")
    Application getApplication(@Param("token") String token, @QueryMap Map<String, Object> queryMap);

    @RequestLine("GET /apps/{appUuid}/stats")
    @Headers("authorization: bearer {token}")
    Instances getApplicationInstances(@Param("token") String token, @Param("appUuid") String appUuid);
}
