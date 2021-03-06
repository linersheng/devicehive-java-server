package com.devicehive.service;

/*
 * #%L
 * DeviceHive Java Server Common business logic
 * %%
 * Copyright (C) 2016 DataArt
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.devicehive.auth.HivePrincipal;
import com.devicehive.configuration.Messages;
import com.devicehive.dao.DeviceDao;
import com.devicehive.exceptions.HiveException;
import com.devicehive.vo.DeviceVO;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.springframework.util.CollectionUtils.isEmpty;

@Component
public class BaseDeviceService {
    private static final Logger logger = LoggerFactory.getLogger(BaseDeviceService.class);

    protected final DeviceDao deviceDao;
    protected final NetworkService networkService;

    @Autowired
    public BaseDeviceService(DeviceDao deviceDao,
                             NetworkService networkService) {
        this.deviceDao = deviceDao;
        this.networkService = networkService;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public DeviceVO findByIdWithPermissionsCheck(String deviceId, HivePrincipal principal) {
        List<DeviceVO> result = findByIdWithPermissionsCheck(Collections.singletonList(deviceId), principal);
        return result.isEmpty() ? null : result.get(0);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<DeviceVO> findByIdWithPermissionsCheck(Collection<String> deviceIds, HivePrincipal principal) {
        return getDeviceList(new ArrayList<>(deviceIds), principal);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<DeviceVO> list(Long networkId) {
        return deviceDao.list(null, null, networkId, null,
                null, false, null, null, null);
    }

    private List<DeviceVO> getDeviceList(List<String> deviceIds, HivePrincipal principal) {
        return deviceDao.getDeviceList(deviceIds, principal);
    }
}
