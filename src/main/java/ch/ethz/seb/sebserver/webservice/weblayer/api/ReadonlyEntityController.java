/*
 * Copyright (c) 2019 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.weblayer.api;

import javax.validation.Valid;

import org.springframework.util.MultiValueMap;

import ch.ethz.seb.sebserver.gbl.api.POSTMapper;
import ch.ethz.seb.sebserver.gbl.api.authorization.PrivilegeType;
import ch.ethz.seb.sebserver.gbl.model.Entity;
import ch.ethz.seb.sebserver.gbl.model.EntityProcessingReport;
import ch.ethz.seb.sebserver.gbl.util.Result;
import ch.ethz.seb.sebserver.webservice.servicelayer.PaginationService;
import ch.ethz.seb.sebserver.webservice.servicelayer.authorization.AuthorizationService;
import ch.ethz.seb.sebserver.webservice.servicelayer.authorization.PermissionDeniedException;
import ch.ethz.seb.sebserver.webservice.servicelayer.bulkaction.BulkActionService;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.EntityDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.UserActivityLogDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.validation.BeanValidationService;

public abstract class ReadonlyEntityController<T extends Entity, M extends Entity> extends EntityController<T, M> {

    private static final String ONLY_READ_ACCESS = "Only read requests available for this entity";

    protected ReadonlyEntityController(
            final AuthorizationService authorization,
            final BulkActionService bulkActionService,
            final EntityDAO<T, M> entityDAO,
            final UserActivityLogDAO userActivityLogDAO,
            final PaginationService paginationService,
            final BeanValidationService beanValidationService) {

        super(
                authorization,
                bulkActionService,
                entityDAO,
                userActivityLogDAO,
                paginationService,
                beanValidationService);
    }

    @Override
    public T savePut(@Valid final T modifyData) {
        throw new UnsupportedOperationException(ONLY_READ_ACCESS);
    }

    @Override
    public T create(final MultiValueMap<String, String> allRequestParams, final Long institutionId) {
        throw new UnsupportedOperationException(ONLY_READ_ACCESS);
    }

    @Override
    public EntityProcessingReport hardDelete(final String modelId) {
        throw new UnsupportedOperationException(ONLY_READ_ACCESS);
    }

    @Override
    protected M createNew(final POSTMapper postParams) {
        throw new UnsupportedOperationException(ONLY_READ_ACCESS);
    }

    @Override
    protected void checkModifyPrivilege(final Long institutionId) {
        throw new PermissionDeniedException(
                getGrantEntityType(),
                PrivilegeType.MODIFY,
                this.authorization.getUserService().getCurrentUser().uuid());
    }

    @Override
    protected Result<T> checkModifyAccess(final T entity) {
        throw new PermissionDeniedException(
                getGrantEntityType(),
                PrivilegeType.MODIFY,
                this.authorization.getUserService().getCurrentUser().uuid());
    }

    @Override
    protected Result<T> checkWriteAccess(final T entity) {
        throw new PermissionDeniedException(
                getGrantEntityType(),
                PrivilegeType.WRITE,
                this.authorization.getUserService().getCurrentUser().uuid());
    }

    @Override
    protected Result<M> checkCreateAccess(final M entity) {
        throw new PermissionDeniedException(
                getGrantEntityType(),
                PrivilegeType.WRITE,
                this.authorization.getUserService().getCurrentUser().uuid());
    }

}