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
import com.devicehive.dao.NetworkDao;
import com.devicehive.exceptions.ActionNotAllowedException;
import com.devicehive.exceptions.HiveException;
import com.devicehive.exceptions.IllegalParametersException;
import com.devicehive.model.enums.SortOrder;
import com.devicehive.model.rpc.ListNetworkRequest;
import com.devicehive.model.updates.NetworkUpdate;
import com.devicehive.util.HiveValidator;
import com.devicehive.vo.DeviceVO;
import com.devicehive.vo.NetworkVO;
import com.devicehive.vo.NetworkWithUsersAndDevicesVO;
import com.devicehive.vo.UserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.devicehive.configuration.Messages.NETWORKS_NOT_FOUND;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static org.springframework.util.CollectionUtils.isEmpty;

@Component
public class NetworkService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkService.class);

    private final HiveValidator hiveValidator;
    private final NetworkDao networkDao;
    
    private BaseUserService baseUserService;

    @Autowired
    public NetworkService(HiveValidator hiveValidator,
                          NetworkDao networkDao) {
        this.hiveValidator = hiveValidator;
        this.networkDao = networkDao;
    }

    @Autowired
    public void setBaseUserService(BaseUserService baseUserService) {
        this.baseUserService = baseUserService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Set<String> getDeviceIdsForNetworks(Set<Long> networkIds, HivePrincipal principal) {
        Set<Long> forbiddenNetworkIds = new HashSet<>();
        Set<String> deviceIds = networkIds.stream()
                .map(networkId -> {
                    NetworkWithUsersAndDevicesVO network = getWithDevices(networkId);
                    if (network == null) forbiddenNetworkIds.add(networkId);
                    return network;
                })
                .filter(Objects::nonNull)
                .map(NetworkWithUsersAndDevicesVO::getDevices)
                .flatMap(Collection::stream)
                .map(DeviceVO::getDeviceId)
                .collect(Collectors.toSet());
        
        if (!isEmpty(forbiddenNetworkIds)) {
            throw new HiveException(String.format(NETWORKS_NOT_FOUND, forbiddenNetworkIds), SC_FORBIDDEN);
        }
        
        return deviceIds;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public NetworkWithUsersAndDevicesVO getWithDevices(@NotNull Long networkId) {
        HivePrincipal principal = (HivePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Set<Long> permittedNetworks = principal.getNetworkIds();

        Optional<NetworkWithUsersAndDevicesVO> result = of(principal)
                .flatMap(pr -> {
                    if (pr.getUser() != null) {
                        return of(pr.getUser());
                    } else {
                        return empty();
                    }
                }).flatMap(user -> {
                    Long idForFiltering = user.isAdmin() ? null : user.getId();
                    List<NetworkWithUsersAndDevicesVO> found = networkDao.getNetworksByIdsAndUsers(idForFiltering,
                            Collections.singleton(networkId), permittedNetworks);
                    return found.stream().findFirst();
                });

        return result.orElse(null);
    }

    @Transactional
    public boolean delete(long id) {
        logger.trace("About to execute named query \"Network.deleteById\" for ");
        int result = networkDao.deleteById(id);
        logger.debug("Deleted {} rows from Network table", result);
        return result > 0;
    }

    @Transactional
    public NetworkVO create(NetworkVO newNetwork) {
        hiveValidator.validate(newNetwork);
        logger.debug("Creating network {}", newNetwork);
        if (newNetwork.getId() != null) {
            logger.error("Can't create network entity with id={} specified", newNetwork.getId());
            throw new IllegalParametersException(Messages.ID_NOT_ALLOWED);
        }
        List<NetworkVO> existing = networkDao.findByName(newNetwork.getName());
        if (!existing.isEmpty()) {
            logger.error("Network with name {} already exists", newNetwork.getName());
            throw new ActionNotAllowedException(Messages.DUPLICATE_NETWORK);
        }
        networkDao.persist(newNetwork);
        logger.info("Entity {} created successfully", newNetwork);
        return newNetwork;
    }

    @Transactional
    public NetworkVO update(@NotNull Long networkId, NetworkUpdate networkUpdate) {
        NetworkVO existing = networkDao.find(networkId);
        if (existing == null) {
            throw new NoSuchElementException(String.format(Messages.NETWORK_NOT_FOUND, networkId));
        }
        if (networkUpdate.getName().isPresent()){
            existing.setName(networkUpdate.getName().get());
        }
        if (networkUpdate.getDescription().isPresent()){
            existing.setDescription(networkUpdate.getDescription().get());
        }
        hiveValidator.validate(existing);

        return networkDao.merge(existing);
    }

    public List<NetworkVO> list(String name,
            String namePattern,
            String sortField,
            String sortOrder,
            Integer take,
            Integer skip,
            HivePrincipal principal) {
        Optional<HivePrincipal> principalOpt = ofNullable(principal);

        return networkDao.list(name, namePattern, sortField, SortOrder.parse(sortOrder), take, skip, principalOpt);
    }

    public List<NetworkVO> list(ListNetworkRequest request) {
        return list(request.getName(), request.getNamePattern(), request.getSortField(), request.getSortOrder(),
                request.getTake(), request.getSkip(), request.getPrincipal().orElse(null));
    }

    @Transactional
    public NetworkVO verifyNetwork(Optional<NetworkVO> networkNullable) {
        //case network is not defined
        if (networkNullable == null || networkNullable.orElse(null) == null) {
            return null;
        }
        NetworkVO network = networkNullable.get();

        Optional<NetworkVO> storedOpt = findNetworkByIdOrName(network);
        if (storedOpt.isPresent()) {
            return storedOpt.get();
        }

        throw new NoSuchElementException(String.format(Messages.NETWORK_NOT_FOUND, network.getId()));
    }

    @Transactional
    public NetworkVO createOrUpdateNetworkByUser(Optional<NetworkVO> networkNullable, UserVO user) {
        //case network is not defined
        if (networkNullable == null || networkNullable.orElse(null) == null) {
            return null;
        }

        NetworkVO network = networkNullable.orElse(null);

        Optional<NetworkVO> storedOpt = findNetworkByIdOrName(network);
        if (storedOpt.isPresent()) {
            return storedOpt.get();
        } else {
            if (network.getId() != null) {
                throw new IllegalParametersException(Messages.INVALID_REQUEST_PARAMETERS);
            }
            if (user.isAdmin()) {
                NetworkWithUsersAndDevicesVO newNetwork = new NetworkWithUsersAndDevicesVO(network);
                networkDao.persist(newNetwork);
                network.setId(newNetwork.getId());
                baseUserService.assignNetwork(user.getId(), network.getId()); // Assign created network to user
            } else {
                throw new ActionNotAllowedException(Messages.NETWORK_CREATION_NOT_ALLOWED);
            }
            return network;
        }
    }

    @Transactional
    public Long findDefaultNetworkByUserId(Long userId) {
    	return networkDao.findDefaultByUser(userId)
    			.map(nvo -> nvo.getId())
    			.orElseThrow(() -> new ActionNotAllowedException(Messages.NO_ACCESS_TO_NETWORK));
    }

    @Transactional
    public NetworkVO createOrUpdateNetworkByUser(UserVO user) {
        NetworkVO networkVO = new NetworkVO();
        networkVO.setName(user.getLogin());
        networkVO.setDescription(String.format("User %s default network", user.getLogin()));
        return createOrUpdateNetworkByUser(Optional.ofNullable(networkVO), user);
    }

    public boolean isNetworkExists(Long networkId) {
    	return ofNullable(networkId)
        	.map(id -> networkDao.find(id) != null)
        	.orElse(false);
    }

    private Optional<NetworkVO> findNetworkByIdOrName(NetworkVO network) {
        return ofNullable(network.getId())
                .map(id -> ofNullable(networkDao.find(id)))
                .orElseGet(() -> networkDao.findFirstByName(network.getName()));
    }
}
