/*
 * Copyright (c) 2018 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.weblayer.api;

import javax.validation.Valid;

import org.mybatis.dynamic.sql.SqlTable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import ch.ethz.seb.sebserver.WebSecurityConfig;
import ch.ethz.seb.sebserver.gbl.api.API;
import ch.ethz.seb.sebserver.gbl.api.APIMessage;
import ch.ethz.seb.sebserver.gbl.api.APIMessage.APIMessageException;
import ch.ethz.seb.sebserver.gbl.api.EntityType;
import ch.ethz.seb.sebserver.gbl.api.POSTMapper;
import ch.ethz.seb.sebserver.gbl.model.EntityKey;
import ch.ethz.seb.sebserver.gbl.model.user.PasswordChange;
import ch.ethz.seb.sebserver.gbl.model.user.UserInfo;
import ch.ethz.seb.sebserver.gbl.model.user.UserMod;
import ch.ethz.seb.sebserver.gbl.profile.WebServiceProfile;
import ch.ethz.seb.sebserver.gbl.util.Result;
import ch.ethz.seb.sebserver.webservice.datalayer.batis.mapper.UserRecordDynamicSqlSupport;
import ch.ethz.seb.sebserver.webservice.servicelayer.PaginationService;
import ch.ethz.seb.sebserver.webservice.servicelayer.authorization.AuthorizationService;
import ch.ethz.seb.sebserver.webservice.servicelayer.authorization.SEBServerUser;
import ch.ethz.seb.sebserver.webservice.servicelayer.bulkaction.BulkActionService;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.UserActivityLogDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.UserActivityLogDAO.ActivityType;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.UserDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.validation.BeanValidationService;
import ch.ethz.seb.sebserver.webservice.weblayer.oauth.RevokeTokenEndpoint;

@WebServiceProfile
@RestController
@RequestMapping("${sebserver.webservice.api.admin.endpoint}" + API.USER_ACCOUNT_ENDPOINT)
public class UserAccountController extends ActivatableEntityController<UserInfo, UserMod> {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final UserDAO userDAO;
    private final PasswordEncoder userPasswordEncoder;

    public UserAccountController(
            final UserDAO userDAO,
            final AuthorizationService authorization,
            final UserActivityLogDAO userActivityLogDAO,
            final PaginationService paginationService,
            final BulkActionService bulkActionService,
            final ApplicationEventPublisher applicationEventPublisher,
            final BeanValidationService beanValidationService,
            @Qualifier(WebSecurityConfig.USER_PASSWORD_ENCODER_BEAN_NAME) final PasswordEncoder userPasswordEncoder) {

        super(authorization,
                bulkActionService,
                userDAO,
                userActivityLogDAO,
                paginationService,
                beanValidationService);
        this.applicationEventPublisher = applicationEventPublisher;
        this.userDAO = userDAO;
        this.userPasswordEncoder = userPasswordEncoder;
    }

    @RequestMapping(path = "/me", method = RequestMethod.GET)
    public UserInfo loggedInUser() {
        return this.authorization
                .getUserService()
                .getCurrentUser()
                .getUserInfo();
    }

    @Override
    protected Class<UserMod> modifiedDataType() {
        return UserMod.class;
    }

    @Override
    protected SqlTable getSQLTableOfEntity() {
        return UserRecordDynamicSqlSupport.userRecord;
    }

    @Override
    protected UserMod createNew(final POSTMapper postParams) {
        return new UserMod(null, postParams);
    }

    @Override
    protected Result<UserInfo> validForSave(final UserInfo userInfo) {
        return Result.tryCatch(() -> {
            // check of institution of UserInfo is active. Otherwise save is not valid
            if (!this.beanValidationService.isActive(new EntityKey(userInfo.institutionId, EntityType.INSTITUTION))) {
                throw new IllegalAPIArgumentException(
                        "User within an inactive institution cannot be created nor modified");
            }
            return userInfo;
        });
    }

    @RequestMapping(
            path = API.PASSWORD_PATH_SEGMENT,
            method = RequestMethod.PUT,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public UserInfo changePassword(@Valid @RequestBody final PasswordChange passwordChange) {

        final String modelId = passwordChange.getModelId();
        return this.userDAO.byModelId(modelId)
                .flatMap(this.authorization::checkWrite)
                .map(ui -> checkPasswordChange(ui, passwordChange))
                .flatMap(e -> this.userDAO.changePassword(modelId, passwordChange.getNewPassword()))
                .flatMap(this::revokeAccessToken)
                .flatMap(e -> this.userActivityLogDAO.log(ActivityType.PASSWORD_CHANGE, e))
                .getOrThrow();
    }

    private UserInfo checkPasswordChange(final UserInfo info, final PasswordChange passwordChange) {
        final SEBServerUser authUser = this.userDAO.sebServerUserByUsername(info.username)
                .getOrThrow();

        if (!this.userPasswordEncoder.matches(passwordChange.getOldPassword(), authUser.getPassword())) {
            throw new APIMessageException(APIMessage.fieldValidationError(
                    new FieldError(
                            "passwordChange",
                            PasswordChange.ATTR_NAME_OLD_PASSWORD,
                            "old password is wrong")));
        }

        if (!passwordChange.newPasswordMatch()) {

            throw new APIMessageException(APIMessage.fieldValidationError(
                    new FieldError(
                            "passwordChange",
                            PasswordChange.ATTR_NAME_RETYPED_NEW_PASSWORD,
                            "old password is wrong")));
        }

        return info;

    }

    private Result<UserInfo> revokeAccessToken(final UserInfo userInfo) {
        return Result.tryCatch(() -> {
            this.applicationEventPublisher.publishEvent(
                    new RevokeTokenEndpoint.RevokeTokenEvent(userInfo, userInfo.username));
            return userInfo;
        });
    }

}
